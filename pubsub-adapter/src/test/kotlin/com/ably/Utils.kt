package com.ably

import io.ably.pubsub.realtime.RealtimeClient
import io.ably.pubsub.realtime.RealtimeClientFactory
import io.ably.pubsub.types.ClientOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

suspend fun waitFor(timeoutInMs: Long = 10_000, block: suspend () -> Boolean) {
  withContext(Dispatchers.Default) {
    withTimeout(timeoutInMs) {
      do {
        val success = block()
        delay(100)
      } while (!success)
    }
  }
}

fun createCoreRealtimeClient(port: Int): RealtimeClient {
  val options = ClientOptions("xxxxx:yyyyyyy").apply {
    this.port = port
    useBinaryProtocol = false
    realtimeHost = "localhost"
    restHost = "localhost"
    tls = false
    autoConnect = false
  }

  return RealtimeClientFactory.create(options)
}
