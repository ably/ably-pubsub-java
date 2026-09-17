/**
 * The LiveObjects bridge layer for the example app: Compose observers plus coroutine
 * wrappers over the path-based API (`io.ably.pubsub.liveobjects`).
 *
 * The app works exclusively with [io.ably.pubsub.liveobjects.path.PathObject]s — references
 * to a *location* in the channel's objects graph, not to a particular object. A PathObject
 * resolves its path lazily on every call, so a stored reference stays valid even when the
 * object at that location is replaced (e.g. "Reset all" swaps in brand-new counters), and
 * a path subscription automatically observes whatever object currently lives there. The
 * identity-bound alternative, [io.ably.pubsub.liveobjects.instance.Instance] (obtained via
 * `pathObject.instance()`), tracks one specific object wherever it moves — not needed here,
 * since every screen wants location semantics.
 *
 * ably-java's typed flavour of the API: the base PathObject exposes only type-agnostic
 * methods; type-specific reads/writes live on sub-type views reached via `as*` casts
 * (`asLiveCounter()`, `asLiveMap()`, `asString()`, ...). The casts never throw — a
 * wrong-typed view degrades gracefully (reads return null/empty, writes fail with an
 * AblyException). The root obtained from `channel.object.get()` is already a
 * [LiveMapPathObject], so no cast is needed there.
 */
package com.ably.example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.ably.pubsub.liveobjects.ValueType
import io.ably.pubsub.liveobjects.path.types.LiveCounterPathObject
import io.ably.pubsub.liveobjects.path.types.LiveMapPathObject
import io.ably.pubsub.liveobjects.state.ObjectStateEvent
import io.ably.pubsub.liveobjects.value.LiveCounter
import io.ably.pubsub.liveobjects.value.LiveMap
import io.ably.pubsub.liveobjects.value.LiveMapValue
import io.ably.pubsub.realtime.RealtimeClient
import io.ably.pubsub.realtime.Channel
import io.ably.pubsub.realtime.ChannelState
import io.ably.pubsub.realtime.ChannelStateListener
import io.ably.pubsub.types.ChannelMode
import io.ably.pubsub.types.ChannelOptions
import kotlinx.coroutines.future.await

/**
 * Returns the counter path object at [key] under [root], creating and linking a fresh
 * zero-value counter when the path is missing or holds a non-counter value. Path objects
 * re-resolve on every call, so the returned reference stays valid even if another client
 * replaces the counter object at this key.
 *
 * The check-then-set is not atomic: another client can create the same key in between.
 * That is fine here - the colliding operations converge deterministically via
 * last-write-wins, and both sides end up bound to whichever counter won.
 */
suspend fun getOrCreateCounter(root: LiveMapPathObject, key: String): LiveCounterPathObject {
  val path = root.get(key)
  if (path.type != ValueType.LIVE_COUNTER) {
    // Creates the counter and links it under root in a single published operation
    root.set(key, LiveMapValue.of(LiveCounter.create())).await()
  }
  return path.asLiveCounter()
}

/**
 * Returns the map path object at [key] under [root], creating and linking a fresh empty
 * map when the path is missing or holds a non-map value. Same last-write-wins caveat as
 * [getOrCreateCounter].
 */
suspend fun getOrCreateMap(root: LiveMapPathObject, key: String): LiveMapPathObject {
  val path = root.get(key)
  if (path.type != ValueType.LIVE_MAP) {
    root.set(key, LiveMapValue.of(LiveMap.create())).await()
  }
  return path.asLiveMap()
}

@Composable
fun observeCounter(root: LiveMapPathObject?, key: String): CounterState {
  var counter by remember { mutableStateOf<LiveCounterPathObject?>(null) }
  var counterValue by remember { mutableStateOf<Int?>(null) }

  LaunchedEffect(root, key) {
    root?.let {
      supressCoroutineExceptions {
        counter = getOrCreateCounter(it, key)
      }
    }
  }

  DisposableEffect(counter) {
    counterValue = counter?.value()?.toInt()

    // The path subscription fires both for increments on the counter and for the key
    // being replaced with a new counter (e.g. "Reset all" on another device); the path
    // re-resolves on read, so no explicit rebinding is needed.
    val subscription = counter?.subscribe {
      counterValue = counter?.value()?.toInt()
    }

    onDispose {
      subscription?.unsubscribe()
    }
  }

  return CounterState(counterValue, counter) {
    // Reset by replacing the object at this key with a brand-new zero-value counter;
    // fire-and-forget - the path subscription refreshes the displayed value on ack
    root?.set(key, LiveMapValue.of(LiveCounter.create()))
  }
}

data class CounterState(val value: Int?, val counter: LiveCounterPathObject?, val reset: () -> Unit)

@Composable
fun observeChannelState(channel: Channel): ChannelState {
  var channelState by remember { mutableStateOf(channel.state) }

  DisposableEffect(channel) {
    val listener: (ChannelStateListener.ChannelStateChange) -> Unit = {
      channelState = it.current
    }

    channel.on(listener)

    onDispose {
      channel.off(listener)
    }
  }

  return channelState
}

@Composable
fun observeMap(root: LiveMapPathObject?, key: String): Pair<Map<String, String>, LiveMapPathObject?> {
  var map by remember { mutableStateOf<LiveMapPathObject?>(null) }
  var mapValue by remember { mutableStateOf<Map<String, String>>(mapOf()) }

  fun readEntries(liveMap: LiveMapPathObject?): Map<String, String> =
    liveMap?.entries()
      ?.mapNotNull { (entryKey, valuePath) -> valuePath.asString().value()?.let { entryKey to it } }
      ?.toMap()
      ?: mapOf()

  LaunchedEffect(root, key) {
    root?.let {
      supressCoroutineExceptions {
        map = getOrCreateMap(it, key)
      }
    }
  }

  DisposableEffect(map) {
    mapValue = readEntries(map)

    // Fires for every entry change on the map at this path (default subscription depth
    // covers nested updates), after which entries are re-read from the resolved map.
    val subscription = map?.subscribe {
      mapValue = readEntries(map)
    }

    onDispose {
      subscription?.unsubscribe()
    }
  }

  return mapValue to map
}

/**
 * Observes the channel's objects synchronization state via `channel.object.on(event)`.
 * Returns the most recent [ObjectStateEvent], or null before any event has been received
 * (events fire on transitions only, so an already-completed sync emits nothing).
 */
@Composable
fun observeObjectsSyncState(channel: Channel): ObjectStateEvent? {
  var syncState by remember { mutableStateOf<ObjectStateEvent?>(null) }

  DisposableEffect(channel) {
    // There is no wildcard subscription - register one listener per event
    val subscriptions = listOf(ObjectStateEvent.SYNCING, ObjectStateEvent.SYNCED).map { event ->
      channel.`object`.on(event) { stateEvent -> syncState = stateEvent }
    }

    onDispose {
      subscriptions.forEach { it.unsubscribe() }
    }
  }

  return syncState
}

@Composable
fun observeRootObject(channel: Channel): LiveMapPathObject? {
  val channelState = observeChannelState(channel)
  var root: LiveMapPathObject? by remember { mutableStateOf(null) }

  LaunchedEffect(channelState) {
    if (channelState == ChannelState.attached) {
      supressCoroutineExceptions {
        // Completes once the objects synchronization state has reached SYNCED
        root = channel.`object`.get().await()
      }
    }
  }

  return root
}

@Composable
fun getRealtimeChannel(realtimeClient: RealtimeClient, channelName: String): Channel {
  val channel = realtimeClient.channels.get(channelName)

  DisposableEffect(channel) {
    channel.setOptions(ChannelOptions().apply {
      attachOnSubscribe = false
      modes = arrayOf(ChannelMode.object_publish, ChannelMode.object_subscribe)
    })

    channel.attach()

    onDispose {
      channel.detach()
    }
  }

  return channel
}

suspend fun supressCoroutineExceptions(block: suspend () -> Unit) {
  try {
    block()
  } catch (_: Exception) {
  }
}
