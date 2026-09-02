package io.ably.lib.uts.unit

/*
 * Harness self-test for the uts.side modes — not a UTS spec translation.
 *
 * The suite can construct its clients through the core constructors or through the server
 * door's builders (see the uts.side handling in ClientFactories.kt). The builders' one
 * observable behavior is the side-declaring Ably-Agent entry they stamp, so this file
 * asserts that the stamp matches the selected mode. It exists to fail loudly if the seam
 * silently degrades — for example if a refactor bypasses the factories and a "server" run
 * quietly constructs plain core clients, turning the server CI leg into a duplicate of the
 * core leg.
 *
 * The side entry is a versionless flag — a bare token, per ably/ably-common#361 — so the
 * assertions also fail if a `/version` form regresses. Mirrors ably-js's side_modes.test.ts.
 */

import io.ably.lib.rest.AblyBase
import io.ably.lib.uts.infra.unit.MockHttpClient
import io.ably.lib.uts.infra.unit.TestRealtimeClient
import io.ably.lib.uts.infra.unit.TestRestClient
import io.ably.lib.uts.infra.unit.utsSide
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Timeout

@Timeout(30)
class SideModesTest {

    /**
     * Builds a client, drives one HTTP request through the mock engine, and returns the
     * Ably-Agent header it carried. The response body is irrelevant — only the captured
     * request headers matter — so any parse failure in the SDK is swallowed.
     */
    private fun agentHeaderFrom(makeClient: (MockHttpClient) -> AblyBase): String {
        val captured = mutableListOf<Map<String, List<String>>>()
        val mock = MockHttpClient {
            onConnectionAttempt = { it.respondWithSuccess() }
            onRequest = { request ->
                captured += request.headers
                request.respondWith(200, "[1704067200000]")
            }
        }
        val client = makeClient(mock)
        try {
            runCatching { client.time() }
        } finally {
            runCatching { client.close() }
        }

        assertTrue(captured.isNotEmpty(), "expected the mock engine to observe a request")
        val agent = captured.first().entries
            .firstOrNull { it.key.equals("Ably-Agent", ignoreCase = true) }
            ?.value?.firstOrNull()
        assertNotNull(agent, "expected an Ably-Agent header, got headers: ${captured.first().keys}")
        return agent
    }

    /**
     * What the selected mode must stamp: nothing for `core`; the bare (versionless) server
     * flag for `server` — never the device flag, and never any `ably-pubsub-server/...` form.
     */
    private fun assertStamp(agent: String) {
        val tokens = agent.split(" ")
        when (utsSide) {
            "core" -> assertFalse(
                tokens.any { it.startsWith("ably-pubsub-") },
                "core mode must not stamp a side entry, got: $agent",
            )
            "server" -> {
                assertTrue(tokens.contains("ably-pubsub-server"), "expected the bare server side flag in: $agent")
                assertFalse(agent.contains("ably-pubsub-server/"), "the side flag must be versionless in: $agent")
                assertFalse(tokens.any { it.startsWith("ably-pubsub-device") }, "a server client must not carry the device entry: $agent")
            }
            else -> throw IllegalArgumentException("Unknown uts.side '$utsSide'")
        }
        assertTrue(agent.contains("ably-java/"), "the core base identifier must always be present in: $agent")
    }

    @Test
    fun `REST clients carry the agent stamp of the selected entry point`() {
        assertStamp(agentHeaderFrom { mock -> TestRestClient { install(mock) } })
    }

    @Test
    fun `realtime clients carry the agent stamp of the selected entry point`() {
        assertStamp(
            agentHeaderFrom { mock ->
                TestRealtimeClient {
                    autoConnect = false
                    install(mock)
                }
            },
        )
    }
}
