package io.ably.lib.uts.integration.proxy

import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.rest.AblyRest
import io.ably.lib.rest.Auth
import io.ably.lib.uts.infra.awaitChannelState
import io.ably.lib.uts.infra.awaitState
import io.ably.lib.uts.infra.integration.SandboxApp
import io.ably.lib.uts.infra.integration.proxy.ProxyManager
import io.ably.lib.uts.infra.integration.proxy.ProxySession
import io.ably.lib.uts.infra.integration.proxy.connectThroughProxy
import io.ably.lib.uts.infra.integration.proxy.wsFrameToClientRule
import io.ably.lib.uts.infra.pollUntil
import io.ably.lib.uts.infra.unit.TestRealtimeClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Acceptance test for the **full proxy integration infrastructure itself** ([ProxyManager],
 * [ProxySession], [SandboxApp] + client wiring through the proxy) — NOT derived from a UTS spec, so
 * it carries no `@UTS` marker. It is the permanent teaching example for `uts/README.md` §11 and the
 * reference shape a future proxy-tier UTS test should take.
 *
 * It proves the full chain end-to-end: binary download/launch → sandbox provisioning → a real client
 * connecting through the proxy with token auth → typed proxy-log assertions → both fault-injection
 * styles (a declarative `ws_frame` rule and a late imperative `triggerAction`) → recovery → teardown.
 *
 * Needs outbound network (GitHub releases on first run, then the Ably sandbox) and spawns the local
 * `uts-proxy` process. Run with:
 * ```
 * ./gradlew :uts:runUtsIntegrationTests
 * ```
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProxyInfraSmokeTest {

    private lateinit var app: SandboxApp

    @BeforeAll
    fun setUpAll() = runBlocking {
        ProxyManager.ensureProxy()
        app = SandboxApp.create()
    }

    @AfterAll
    fun tearDownAll() = runBlocking {
        if (::app.isInitialized) app.delete()
    }

    /**
     * Pass-through session: proves the happy path and the typed proxy log, then injects a fault the
     * *late imperative* way — [ProxySession.triggerAction] on the live connection.
     */
    @Test
    fun `proxy pass-through proves the log and a late imperative disconnect`() = runTest {
        // Assert provisioning (cocoa parity).
        assertTrue(app.defaultKey.startsWith(app.appId + "."))

        val session = ProxySession.create(rules = emptyList())
        assertTrue(session.proxyPort > 0)

        val tokenSigner = AblyRest(app.defaultKey)
        val authCallbackCount = AtomicInteger(0)
        val client = TestRealtimeClient {
            // Basic key auth is TLS-only, so authenticate through the proxy with a locally-signed
            // TokenRequest (README §11 teaching point).
            authCallback = Auth.TokenCallback { params ->
                authCallbackCount.incrementAndGet()
                tokenSigner.auth.createTokenRequest(params, null)
            }
            connectThroughProxy(session)
            autoConnect = false
        }

        try {
            client.connect()
            awaitState(client, ConnectionState.connected, 20.seconds)
            assertNotNull(client.connection.id)
            assertTrue(authCallbackCount.get() >= 1)

            // Typed proxy-log assertions: the handshake recorded a ws_connect and a server→client
            // CONNECTED frame (protocol action 4).
            val log = session.getLog()
            assertTrue(log.any { it.type == "ws_connect" })
            assertTrue(
                log.any {
                    it.type == "ws_frame" &&
                        it.direction == "server_to_client" &&
                        it.message?.get("action")?.asInt == 4
                },
            )

            // Late imperative fault: disconnect the live connection, observe DISCONNECTED, recover.
            val states = Collections.synchronizedList(mutableListOf<ConnectionState>())
            client.connection.on { states.add(it.current) }
            session.triggerAction(mapOf("type" to "disconnect"))
            pollUntil(20.seconds) { states.contains(ConnectionState.disconnected) }
            awaitState(client, ConnectionState.connected, 20.seconds)

            // The proxy recorded the imperative action.
            assertTrue(session.getLog().any { it.type == "action" || it.ruleMatched != null })
        } finally {
            try {
                client.close()
            } finally {
                session.close()
                runCatching { tokenSigner.close() }
            }
        }
    }

    /**
     * Declarative-rule session: a `ws_frame_to_client` rule replaces the first ATTACHED (action 11)
     * with a disconnect, so the client observes a DISCONNECTED transition when the rule fires, then
     * recovers (reconnects and the channel re-attaches once the one-shot rule is spent).
     */
    @Test
    fun `proxy declarative rule injects a disconnect on ATTACHED then recovers`() = runTest {
        val session = ProxySession.create(
            rules = listOf(
                wsFrameToClientRule(action = mapOf("type" to "disconnect"), messageAction = 11, times = 1),
            ),
        )

        val tokenSigner = AblyRest(app.defaultKey)
        val client = TestRealtimeClient {
            authCallback = Auth.TokenCallback { params ->
                tokenSigner.auth.createTokenRequest(params, null)
            }
            connectThroughProxy(session)
            autoConnect = false
        }

        try {
            val states = Collections.synchronizedList(mutableListOf<ConnectionState>())
            client.connection.on { states.add(it.current) }

            client.connect()
            awaitState(client, ConnectionState.connected, 20.seconds)

            val channel = client.channels.get("smoke-proxy-rule-${UUID.randomUUID()}")
            channel.attach()

            // The rule fires on the ATTACHED frame and disconnects the transport.
            pollUntil(20.seconds) { states.contains(ConnectionState.disconnected) }

            // Recovery: the connection re-establishes and the channel re-attaches (rule is spent).
            awaitState(client, ConnectionState.connected, 20.seconds)
            awaitChannelState(channel, ChannelState.attached, 20.seconds)
        } finally {
            try {
                client.close()
            } finally {
                session.close()
                runCatching { tokenSigner.close() }
            }
        }
    }
}
