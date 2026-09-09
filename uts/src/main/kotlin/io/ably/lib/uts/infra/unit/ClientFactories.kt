package io.ably.lib.uts.infra.unit

import io.ably.lib.debug.DebugOptions
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.rest.AblyRest

class ClientOptionsBuilder : DebugOptions("appId.keyId:keySecret") {
    init {
        useBinaryProtocol = false
    }

    fun install(mock: MockWebSocket) = mock.installOn(this)
    fun install(mock: MockHttpClient) = mock.installOn(this)

    fun enableFakeTimers(fakeClock: FakeClock) {
        clock = fakeClock
    }
}

fun TestRealtimeClient(block: ClientOptionsBuilder.() -> Unit): AblyRealtime =
    AblyRealtime(ClientOptionsBuilder().apply(block))

fun TestRestClient(block: ClientOptionsBuilder.() -> Unit): AblyRest =
    AblyRest(ClientOptionsBuilder().apply(block))
