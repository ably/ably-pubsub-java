package io.ably.pubsub.liveobjects

import io.ably.pubsub.liveobjects.adapter.AblyClientAdapter
import io.ably.pubsub.liveobjects.message.WireObjectMessage
import io.ably.pubsub.liveobjects.message.size
import io.ably.pubsub.realtime.ChannelState
import io.ably.pubsub.realtime.CompletionListener
import io.ably.pubsub.realtime.ConnectionEvent
import io.ably.pubsub.realtime.ConnectionStateListener
import io.ably.pubsub.types.*
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps [onUnsubscribe] in a [Subscription] that runs the cleanup at most once; further
 * calls are no-ops. Use it wherever a [Subscription] is returned: `EventEmitter.off` is
 * `synchronized`, so this avoids re-acquiring that lock (and re-running teardown) on
 * repeated unsubscribe calls. Thread-safe.
 *
 * Spec: SUB2a, SUB2b
 */
internal fun onceSubscription(onUnsubscribe: () -> Unit): Subscription {
  val unsubscribed = AtomicBoolean(false)
  return Subscription {
    if (unsubscribed.compareAndSet(false, true)) {
      onUnsubscribe()
    }
  }
}

/**
 * Validates that the channel is configured for the access (read/subscribe) API: it must be
 * attachable (not detached/failed) and have the `object_subscribe` mode. Copied from the
 * legacy `io.ably.pubsub.objects` helpers so this package has no dependency on that package.
 *
 * Spec: RTO25
 */
internal fun AblyClientAdapter.throwIfInvalidAccessApiConfiguration(channelName: String) {
  throwIfInChannelState(channelName, arrayOf(ChannelState.detached, ChannelState.failed))
  throwIfMissingChannelMode(channelName, ChannelMode.object_subscribe)
}

/**
 * Validates only the `object_subscribe` channel mode (no channel-state check), for
 * `RealtimeObject.get()` (RTO23a). Unlike the access methods, `get()` delegates channel-state handling to
 * ensure-active-channel ([ensureAttached], RTO23e / RTL33), which re-attaches a DETACHED channel and
 * rejects only FAILED — so `get()` must not pre-empt that with a state gate.
 *
 * Spec: RTO23a
 */
internal fun AblyClientAdapter.throwIfMissingObjectSubscribeMode(channelName: String) {
  throwIfMissingChannelMode(channelName, ChannelMode.object_subscribe)
}

/**
 * Validates that the channel is configured for the write (mutation) API: message echo must be
 * enabled, the channel must be usable (not detached/failed/suspended) and have the
 * `object_publish` mode.
 *
 * Spec: RTO26
 */
internal fun AblyClientAdapter.throwIfInvalidWriteApiConfiguration(channelName: String) {
  throwIfEchoMessagesDisabled()
  throwIfInChannelState(channelName, arrayOf(ChannelState.detached, ChannelState.failed, ChannelState.suspended))
  throwIfMissingChannelMode(channelName, ChannelMode.object_publish)
}

/**
 * Resolves the effective channel modes: the attached `modes` if present, otherwise the
 * user-provided channel options as a best effort.
 *
 * Spec: RTO2a, RTO2b
 */
internal fun AblyClientAdapter.getChannelModes(channelName: String): Array<ChannelMode>? {
  val channel = getChannel(channelName)
  channel.modes?.let { modes -> if (modes.isNotEmpty()) return modes } // RTO2a
  channel.options?.let { options -> if (options.hasModes()) return options.modes } // RTO2b
  return null
}

// Spec: RTO2a2, RTO2b2
private fun AblyClientAdapter.throwIfMissingChannelMode(channelName: String, channelMode: ChannelMode) {
  val channelModes = getChannelModes(channelName)
  if (channelModes == null || !channelModes.contains(channelMode)) {
    throw objectException(
      "\"${channelMode.name}\" channel mode must be set for this operation",
      ObjectErrorCode.ChannelModeRequired,
    )
  }
}

private fun AblyClientAdapter.throwIfInChannelState(channelName: String, channelStates: Array<ChannelState>) {
  val currentState = getChannel(channelName).state
  if (currentState == null || channelStates.contains(currentState)) {
    throw objectException("Channel is in invalid state: $currentState", ObjectErrorCode.ChannelStateError)
  }
}

private fun AblyClientAdapter.throwIfEchoMessagesDisabled() {
  if (!clientOptions.echoMessages) {
    throw objectException(
      "\"echoMessages\" client option must be enabled for this operation",
      ObjectErrorCode.BadRequest,
    )
  }
}

internal fun AblyClientAdapter.throwIfUnpublishableState(channelName: String) {
  if (!connectionManager.isActive) {
    throw ablyException(connectionManager.stateErrorInfo)
  }
  throwIfInChannelState(channelName, arrayOf(ChannelState.failed, ChannelState.suspended))
}

internal val AblyClientAdapter.connectionManager get() = connection.connectionManager

internal fun AblyClientAdapter.onGCGracePeriodUpdated(block : (Long?) -> Unit) : Subscription {
  connectionManager.objectsGCGracePeriod?.let { block(it) }
  // Return new objectsGCGracePeriod whenever connection state changes to connected
  val listener: (_: ConnectionStateListener.ConnectionStateChange) -> Unit = {
    block(connectionManager.objectsGCGracePeriod)
  }
  connection.on(ConnectionEvent.connected, listener)
  return onceSubscription { connection.off(listener) }
}

/**
 * Spec: RTO15g
 */
internal suspend fun AblyClientAdapter.sendAsync(message: ProtocolMessage): PublishResult = suspendCancellableCoroutine { continuation ->
  try {
    connectionManager.send(message, clientOptions.queueMessages, object : Callback<PublishResult> {
      override fun onSuccess(result: PublishResult) {
        continuation.resume(result)
      }

      override fun onError(reason: ErrorInfo) {
        continuation.resumeWithException(ablyException(reason))
      }
    })
  } catch (e: Exception) {
    continuation.resumeWithException(e)
  }
}

internal suspend fun AblyClientAdapter.attachAsync(channelName: String) = suspendCancellableCoroutine { continuation ->
  try {
    getChannel(channelName).attach(object : CompletionListener {
      override fun onSuccess() {
        continuation.resume(Unit)
      }

      override fun onError(reason: ErrorInfo) {
        continuation.resumeWithException(ablyException(reason))
      }
    })
  } catch (e: Exception) {
    continuation.resumeWithException(e)
  }
}

/**
 * Spec: RTO15d
 */
internal fun AblyClientAdapter.ensureMessageSizeWithinLimit(wireObjectMessages: Array<WireObjectMessage>) {
  val maximumAllowedSize = connectionManager.maxMessageSize
  val objectsTotalMessageSize = wireObjectMessages.sumOf { it.size() }
  if (objectsTotalMessageSize > maximumAllowedSize) {
    throw ablyException("ObjectMessages size $objectsTotalMessageSize exceeds maximum allowed size of $maximumAllowedSize bytes",
      ObjectErrorCode.MaxMessageSizeExceeded)
  }
}

internal fun AblyClientAdapter.setChannelSerial(channelName: String, protocolMessage: ProtocolMessage) {
  if (protocolMessage.action != ProtocolMessage.Action.`object`) return
  val channelSerial = protocolMessage.channelSerial
  if (channelSerial.isNullOrEmpty()) return
  getChannel(channelName).properties.channelSerial = channelSerial
}

/**
 * *Ensure-active-channel* procedure: makes the channel usable before an objects operation.
 *
 * Spec: RTL33
 */
internal suspend fun AblyClientAdapter.ensureAttached(channelName: String) {
  val channel = getChannel(channelName)
  when (val currentChannelStatus = channel.state) {
    // RTL33a - already usable, no attach performed
    ChannelState.attached, ChannelState.suspended -> return
    // RTL33b - implicit attach per RTL4 and wait for completion. attachAsync propagates the
    // ErrorInfo that caused the attach to fail (RTL33b1). Channel#attach already resolves the
    // ATTACHING/DETACHING in-flight cases per RTL4h, so a single call covers all four states.
    ChannelState.initialized,
    ChannelState.detached,
    ChannelState.detaching,
    ChannelState.attaching -> attachAsync(channelName)
    // RTL33c - failed (and any other/unknown state): code 90001, statusCode 400
    else ->
      throw objectException("Channel $channelName is in invalid state: $currentChannelStatus", ObjectErrorCode.ChannelStateError)
  }
}
