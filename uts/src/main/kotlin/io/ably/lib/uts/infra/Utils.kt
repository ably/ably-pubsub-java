package io.ably.lib.uts.infra

import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ChannelStateListener
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.realtime.ConnectionStateListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// tryResume/completeResume (the atomic single-winner resume) are @InternalCoroutinesApi.
@OptIn(InternalCoroutinesApi::class)
suspend fun awaitState(
  client: AblyRealtime,
  target: ConnectionState,
  timeout: Duration = 5.seconds
) {
  // withContext uses a real-thread dispatcher so withTimeout measures wall-clock time,
  // not virtual (kotlinx.coroutines.test) time.
  // Listener is registered BEFORE the state check to avoid the race where the target
  // state fires on the ActionHandler thread between the check and the registration.
  withContext(Dispatchers.Default.limitedParallelism(1)) {
    withTimeout(timeout) {
      suspendCancellableCoroutine { cont ->
        lateinit var listener: ConnectionStateListener
        listener = ConnectionStateListener { change ->
          if (change.current == target) {
            client.connection.off(listener)
            // single-winner resume: the listener and the immediate check race on different threads
            cont.tryResume(Unit)?.let(cont::completeResume)
          }
        }
        client.connection.on(listener)
        if (client.connection.state == target) {
          client.connection.off(listener)
          cont.tryResume(Unit)?.let(cont::completeResume)
        }
        cont.invokeOnCancellation { client.connection.off(listener) }
      }
    }
  }
}

/**
 * Suspends until [condition] returns `true`, polling every [interval], or fails with a
 * [kotlinx.coroutines.TimeoutCancellationException] once [timeout] elapses.
 *
 * Runs on a real-thread dispatcher so [timeout] measures wall-clock time (not virtual
 * `kotlinx.coroutines.test` time) — use this for integration tests that wait on real network or
 * proxy state, e.g. `pollUntil { authCallbackCount.get() > original }`.
 */
suspend fun pollUntil(
  timeout: Duration = 15.seconds,
  interval: Duration = 100.milliseconds,
  condition: suspend () -> Boolean,
) {
  withContext(Dispatchers.Default.limitedParallelism(1)) {
    withTimeout(timeout) {
      while (!condition()) delay(interval)
    }
  }
}

/**
 * Runs [block] under a wall-clock [timeout] on a real-thread dispatcher.
 *
 * Inside `runTest`, a bare `withTimeout` measures virtual (kotlinx.coroutines.test) time, which
 * fast-forwards while the test coroutine idles — so a timeout wrapping a real network operation
 * fires immediately. Use this instead for integration tests awaiting real backend work, e.g.
 * `withRealTimeout(15.seconds) { channel.`object`.get().await() }`.
 */
suspend fun <T> withRealTimeout(timeout: Duration, block: suspend () -> T): T =
  withContext(Dispatchers.Default.limitedParallelism(1)) {
    withTimeout(timeout) { block() }
  }

@OptIn(InternalCoroutinesApi::class)
suspend fun awaitChannelState(
  channel: Channel,
  target: ChannelState,
  timeout: Duration = 5.seconds
) {
  withContext(Dispatchers.Default.limitedParallelism(1)) {
    withTimeout(timeout) {
      suspendCancellableCoroutine { cont ->
        lateinit var listener: ChannelStateListener
        listener = ChannelStateListener { change ->
          if (change.current == target) {
            channel.off(listener)
            // single-winner resume: the listener and the immediate check race on different threads
            cont.tryResume(Unit)?.let(cont::completeResume)
          }
        }
        channel.on(listener)
        if (channel.state == target) {
          channel.off(listener)
          cont.tryResume(Unit)?.let(cont::completeResume)
        }
        cont.invokeOnCancellation { channel.off(listener) }
      }
    }
  }
}
