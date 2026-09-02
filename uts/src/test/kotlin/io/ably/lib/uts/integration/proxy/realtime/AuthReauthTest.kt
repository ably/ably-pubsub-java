package io.ably.lib.uts.integration.proxy.realtime

import io.ably.lib.realtime.ConnectionState
import io.ably.lib.rest.AblyRest
import io.ably.lib.rest.Auth
import io.ably.lib.uts.infra.awaitState
import io.ably.lib.uts.infra.integration.SandboxApp
import io.ably.lib.uts.infra.integration.proxy.ProxyManager
import io.ably.lib.uts.infra.integration.proxy.ProxySession
import io.ably.lib.uts.infra.integration.proxy.connectThroughProxy
import io.ably.lib.uts.infra.pollUntil
import io.ably.lib.uts.infra.unit.assumeSideSupportsTokenAuth
import io.ably.lib.uts.infra.unit.TestRealtimeClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Proxy integration test against Ably Sandbox endpoint.
 *
 * Uses the programmable uts-proxy to inject transport-level faults while the
 * SDK communicates with the real Ably backend. See
 * `uts/docs/proxy.md` for proxy infrastructure details.
 *
 * Spec points: RTN22, RTC8a.
 * Unit-test counterparts: `server_initiated_reauth_test.md` (RTN22), `realtime_authorize.md` (RTC8a).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthReauthTest {

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
     * @UTS realtime/proxy/RTN22/server-initiated-reauth-0
     * @UTS realtime/proxy/RTC8a/server-initiated-reauth-0
     */
    @Test
    fun `RTN22, RTC8a - server-initiated re-authentication`() = runTest {
        assumeSideSupportsTokenAuth()
        // No proxy rules: the AUTH injection is triggered imperatively after the SDK connects.
        val session = ProxySession.create(rules = emptyList())

        // Re-authentication is observed via an authCallback. The spec generates a JWT from the
        // sandbox key parts; the idiomatic ably-java equivalent is a locally-signed TokenRequest
        // produced from the same key — no external JWT library required. The realtime client then
        // exchanges it for a token (through the proxy), satisfying RTC8a.
        val tokenSigner = AblyRest(app.defaultKey)
        val authCallbackCount = AtomicInteger(0)
        val authCallback = Auth.TokenCallback { params ->
            authCallbackCount.incrementAndGet()
            tokenSigner.auth.createTokenRequest(params, null)
        }

        // Keep the JSON protocol (ClientOptionsBuilder default): the proxy injects/inspects frames
        // as JSON, so the assertions below read `message.get("action")` from the proxy log.
        val client = TestRealtimeClient {
            this.authCallback = authCallback
            connectThroughProxy(session)
            autoConnect = false
        }

        try {
            // Connect through proxy
            client.connect()
            awaitState(client, ConnectionState.connected, 15.seconds)

            // Record identity and auth state before injection
            val originalConnectionId = client.connection.id
            val originalAuthCallbackCount = authCallbackCount.get()
            assertNotNull(originalConnectionId)
            assertTrue(originalAuthCallbackCount >= 1)

            // Record state changes from this point
            val stateChanges = Collections.synchronizedList(mutableListOf<ConnectionState>())
            client.connection.on { change -> stateChanges.add(change.current) }

            // Inject a server-initiated AUTH ProtocolMessage (action 17), simulating Ably
            // requesting re-authentication.
            session.triggerAction(
                mapOf("type" to "inject_to_client", "message" to mapOf("action" to 17)),
            )

            // Wait for the SDK to invoke authCallback again and send its AUTH response.
            // Allow time for the token request round-trip to the sandbox.
            pollUntil { stateChanges.size > 1 }

            // authCallback was called again (re-authentication triggered)
            assertEquals(originalAuthCallbackCount + 1, authCallbackCount.get())

            // Connection remains CONNECTED (re-auth does not disrupt the connection)
            assertEquals(ConnectionState.connected, client.connection.state)

            // Connection ID is unchanged (no reconnection occurred)
            assertEquals(originalConnectionId, client.connection.id)

            // No state transitions away from CONNECTED occurred
            val nonConnectedChanges = stateChanges.filter { it != ConnectionState.connected }
            assertEquals(0, nonConnectedChanges.size)

            // RTC8a: the client sends an AUTH (action 17) frame carrying the renewed auth details.
            val clientAuthFrames = session.getLog().filter {
                it.type == "ws_frame" &&
                    it.direction == "client_to_server" &&
                    it.message?.get("action")?.asInt == 17 &&
                    it.message.get("auth")?.isJsonNull == false
            }

            assertTrue(
                clientAuthFrames.isNotEmpty(),
                "Expected at least one client-to-server AUTH frame carrying auth details",
            )
        } finally {
            // Nest teardown so session/tokenSigner are always cleaned up even if close-wait times out.
            try {
                client.close()
                awaitState(client, ConnectionState.closed, 10.seconds)
            } finally {
                session.close()
                runCatching { tokenSigner.close() }
            }
        }
    }
}
