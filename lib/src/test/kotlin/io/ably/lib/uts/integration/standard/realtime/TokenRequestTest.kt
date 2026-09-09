package io.ably.lib.uts.integration.standard.realtime

import io.ably.lib.realtime.ConnectionState
import io.ably.lib.rest.Auth
import io.ably.lib.uts.infra.awaitState
import io.ably.lib.uts.infra.integration.SandboxApp
import io.ably.lib.uts.infra.unit.TestRealtimeClient
import io.ably.lib.uts.infra.unit.TestRestClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/**
 * Direct-sandbox integration test against the Ably Sandbox
 * (`sandbox.realtime.ably-nonprod.net`, via [SandboxApp.sandboxHost]) — no proxy, no
 * fault injection. Provisions one throwaway [SandboxApp] for the suite and connects a real
 * realtime client straight to the sandbox.
 *
 * End-to-end verification that `Auth#createTokenRequest` produces a signed `TokenRequest` that the
 * Ably service accepts — proving the HMAC signature computation (RSA9g) is compatible with the
 * server. A REST client signs the TokenRequest; a separate realtime client exchanges it (through
 * its `authCallback`) for a token and connects, proving the server accepted it.
 *
 * Spec points: RSA9, RSA9a, RSA9g. Source spec: `realtime/integration/auth/token_request_test.md`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TokenRequestTest {

    private lateinit var app: SandboxApp

    @BeforeAll
    fun setUpAll() = runBlocking {
        app = SandboxApp.create()
    }

    @AfterAll
    fun tearDownAll() = runBlocking {
        if (::app.isInitialized) app.delete()
    }

    /**
     * @UTS realtime/integration/RSA9a/token-request-server-accepted-0
     * @UTS realtime/integration/RSA9g/token-request-server-accepted-0
     */
    @Test
    fun `RSA9a, RSA9g - createTokenRequest produces server-accepted token`() = runTest {
        // Client A signs TokenRequests locally with the API key (no network).
        val creator = TestRestClient {
            key = app.defaultKey
            restHost = SandboxApp.sandboxHost
        }

        // Client B connects using a TokenRequest produced by client A.
        val client = TestRealtimeClient {
            authCallback = Auth.TokenCallback { params -> creator.auth.createTokenRequest(params, null) }
            realtimeHost = SandboxApp.sandboxHost
            restHost = SandboxApp.sandboxHost
            useBinaryProtocol = false
            autoConnect = false
        }

        try {
            client.connect()
            awaitState(client, ConnectionState.connected, 15.seconds)

            assertEquals(ConnectionState.connected, client.connection.state)
            assertNotNull(client.connection.id)
            assertNull(client.connection.reason)
        } finally {
            client.close()
            runCatching { creator.close() }
        }
    }

    /**
     * @UTS realtime/integration/RSA9/token-request-with-clientid-0
     */
    @Test
    fun `RSA9 - createTokenRequest with clientId`() = runTest {
        val testClientId = "token-request-client-" + UUID.randomUUID()

        val creator = TestRestClient {
            key = app.defaultKey
            restHost = SandboxApp.sandboxHost
        }

        // The TokenRequest is signed with the specific clientId, producing a token that
        // authenticates the client with that identity.
        val client = TestRealtimeClient {
            authCallback = Auth.TokenCallback { params ->
                params.clientId = testClientId
                creator.auth.createTokenRequest(params, null)
            }
            clientId = testClientId
            realtimeHost = SandboxApp.sandboxHost
            restHost = SandboxApp.sandboxHost
            useBinaryProtocol = false
            autoConnect = false
        }

        try {
            client.connect()
            awaitState(client, ConnectionState.connected, 15.seconds)

            assertEquals(ConnectionState.connected, client.connection.state)
            assertEquals(testClientId, client.auth.clientId)
        } finally {
            client.close()
            runCatching { creator.close() }
        }
    }
}
