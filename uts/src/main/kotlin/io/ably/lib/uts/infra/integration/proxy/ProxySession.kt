package io.ably.lib.uts.infra.integration.proxy

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import io.ably.lib.uts.infra.integration.SandboxApp
import io.ably.lib.uts.infra.unit.ClientOptionsBuilder
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType

// Finite timeouts so a stalled local proxy/control endpoint fails fast instead of hanging teardown.
private val client = HttpClient(CIO) {
    install(HttpTimeout) {
        requestTimeoutMillis = 15_000
        connectTimeoutMillis = 5_000
        socketTimeoutMillis = 15_000
    }
}

// ── Rule type alias ────────────────────────────────────────────────────────────

/**
 * A proxy rule: a `Map<String, Any>` with at minimum `"match"` and `"action"` keys.
 *
 * Use the factory helpers ([wsConnectRule], [wsFrameToClientRule], [wsFrameToServerRule],
 * [httpRequestRule]) to construct rules without hard-coding map literals everywhere.
 *
 * Rules are evaluated in order; the first matching rule wins. Unmatched traffic passes through.
 * When `"times"` is set the rule auto-removes after that many firings.
 */
typealias ProxyRule = Map<String, Any>

// ── Event ───────────────────────────────────────────────────────────────────────

/**
 * A single event recorded in a [ProxySession]'s log, returned by [ProxySession.getLog].
 *
 * Mirrors the proxy's `Event` struct. Fields that are absent from a given event are `null`
 * (Go's `omitempty` tags), so most properties are nullable.
 */
data class Event(
    /** RFC3339 timestamp, e.g. `2026-06-22T21:43:56.747996Z`. */
    val timestamp: String? = null,
    /** `ws_connect`, `ws_frame`, `ws_disconnect`, `http_request`, `http_response`, or `action`. */
    val type: String? = null,
    /** `client_to_server` or `server_to_client`. */
    val direction: String? = null,
    val url: String? = null,
    val queryParams: Map<String, String>? = null,
    /**
     * The raw protocol message (proxy `json.RawMessage`), parsed into a [JsonObject].
     * Introspect via `message?.get("action")?.asInt` etc.
     */
    val message: JsonObject? = null,
    val method: String? = null,
    val path: String? = null,
    val status: Int? = null,
    /** `client`, `server`, or `proxy`. */
    val initiator: String? = null,
    val closeCode: Int? = null,
    val ruleMatched: String? = null,
    val headers: Map<String, String>? = null,
)

// ── Rule factory helpers ───────────────────────────────────────────────────────

/**
 * Builds a rule that matches WebSocket connection attempts.
 *
 * @param action      The action to take (e.g. `mapOf("type" to "refuse_connection")`).
 * @param count       1-based occurrence index; `2` matches only the 2nd connection attempt.
 * @param queryContains Match only if the WS URL query params contain these key/value pairs.
 *                    Use `"*"` as a wildcard value (matches any non-null value).
 * @param times       Auto-remove the rule after this many firings.
 */
fun wsConnectRule(
    action: Map<String, Any>,
    count: Int? = null,
    queryContains: Map<String, String>? = null,
    times: Int? = null,
): ProxyRule = buildMap {
    put("match", buildMap<String, Any> {
        put("type", "ws_connect")
        if (count != null) put("count", count)
        if (queryContains != null) put("queryContains", queryContains)
    })
    put("action", action)
    if (times != null) put("times", times)
}

/**
 * Builds a rule that matches WebSocket frames travelling **server → client**.
 *
 * @param action        The action to take (e.g. `mapOf("type" to "suppress")`).
 * @param messageAction The Ably protocol message action number to match (see the action table
 *                      in proxy.md; e.g. `4` = CONNECTED, `11` = ATTACHED).
 * @param channel       If set, additionally match only frames for this channel name.
 * @param times         Auto-remove the rule after this many firings.
 */
fun wsFrameToClientRule(
    action: Map<String, Any>,
    messageAction: Int? = null,
    channel: String? = null,
    times: Int? = null,
): ProxyRule = buildMap {
    put("match", buildMap<String, Any> {
        put("type", "ws_frame_to_client")
        // The proxy's MatchConfig.Action is a Go string (action name or numeric string).
        if (messageAction != null) put("action", messageAction.toString())
        if (channel != null) put("channel", channel)
    })
    put("action", action)
    if (times != null) put("times", times)
}

/**
 * Builds a rule that matches WebSocket frames travelling **client → server**.
 *
 * @param action        The action to take.
 * @param messageAction The Ably protocol message action number to match
 *                      (e.g. `10` = ATTACH, `17` = AUTH).
 * @param channel       If set, additionally match only frames for this channel name.
 * @param times         Auto-remove the rule after this many firings.
 */
fun wsFrameToServerRule(
    action: Map<String, Any>,
    messageAction: Int? = null,
    channel: String? = null,
    times: Int? = null,
): ProxyRule = buildMap {
    put("match", buildMap<String, Any> {
        put("type", "ws_frame_to_server")
        // The proxy's MatchConfig.Action is a Go string (action name or numeric string).
        if (messageAction != null) put("action", messageAction.toString())
        if (channel != null) put("channel", channel)
    })
    put("action", action)
    if (times != null) put("times", times)
}

/**
 * Builds a rule that matches HTTP requests passing through the proxy.
 *
 * @param action       The action to take (e.g. `mapOf("type" to "http_respond", "status" to 401)`).
 * @param pathContains Match only requests whose path contains this substring.
 * @param method       Match only requests with this HTTP method (e.g. `"GET"`, `"POST"`).
 * @param times        Auto-remove the rule after this many firings.
 */
fun httpRequestRule(
    action: Map<String, Any>,
    pathContains: String? = null,
    method: String? = null,
    times: Int? = null,
): ProxyRule = buildMap {
    put("match", buildMap<String, Any> {
        put("type", "http_request")
        if (pathContains != null) put("pathContains", pathContains)
        if (method != null) put("method", method)
    })
    put("action", action)
    if (times != null) put("times", times)
}

// ── ProxySession ──────────────────────────────────────────────────────────────

/**
 * A single proxy session wrapping the `uts-proxy` control REST API.
 *
 * Each test should create one session, run its scenario, and call [close] in a `finally` block.
 *
 * ```kotlin
 * val session = ProxySession.create(rules = listOf(
 *     wsConnectRule(action = mapOf("type" to "refuse_connection"), count = 2)
 * ))
 * try {
 *     val client = TestRealtimeClient {
 *         key = sandboxKey
 *         connectThroughProxy(session)
 *     }
 *     // … test scenario …
 * } finally {
 *     session.close()
 * }
 * ```
 *
 * All methods are `suspend` functions backed by a Ktor client.
 *
 * > **Note:** [getLog] returns a typed `List<`[Event]`>`. The raw protocol message is exposed as
 * > [Event.message] (a `JsonObject`); introspect it via `message?.get("action")`.
 */
class ProxySession private constructor(
    /** Opaque session identifier assigned by the proxy. */
    val sessionId: String,
    /** The port on `localhost` that the proxy is listening on for this session. */
    val proxyPort: Int,
    /** Always `"localhost"`. Exposed for use by [connectThroughProxy]. */
    val proxyHost: String = "localhost",
) {

    companion object {
        private val gson = Gson()

        /**
         * Creates a new proxy session pointing at the Ably sandbox.
         *
         * @param rules       Initial rule set applied to all traffic through this session.
         * @param port        Specific port to listen on; `0` (default) lets the proxy choose.
         * @param timeoutMs   Session idle-timeout in ms; `null` uses the proxy default (30 000 ms).
         * @param realtimeHost Upstream Ably realtime host (defaults to sandbox).
         * @param restHost     Upstream Ably REST host (defaults to sandbox).
         */
        suspend fun create(
            rules: List<ProxyRule> = emptyList(),
            port: Int = 0,
            timeoutMs: Long? = null,
            realtimeHost: String = SandboxApp.sandboxHost,
            restHost: String = SandboxApp.sandboxHost,
        ): ProxySession {
            val body = JsonObject().apply {
                add("target", JsonObject().apply {
                    addProperty("realtimeHost", realtimeHost)
                    addProperty("restHost", restHost)
                })
                add("rules", gson.toJsonTree(rules))
                if (port != 0) addProperty("port", port)
                if (timeoutMs != null) addProperty("timeoutMs", timeoutMs)
            }

            val responseBody = controlPost("/sessions", body.toString())
            val data = gson.fromJson(responseBody, JsonObject::class.java)
            val sessionId = data["sessionId"].asString
            val proxyPort = data.getAsJsonObject("proxy")["port"].asInt

            return ProxySession(sessionId = sessionId, proxyPort = proxyPort)
        }

        // ── HTTP helpers (shared by companion + instance methods) ──────────────

        private fun controlUrl(path: String) = "http://localhost:${ProxyManager.CONTROL_PORT}$path"

        internal suspend fun controlPost(path: String, body: String): String {
            val response = client.post(controlUrl(path)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            val text = response.bodyAsText()
            check(response.status.value in 200..299) {
                "Proxy control API returned ${response.status.value} for POST $path: $text"
            }
            return text
        }

        internal suspend fun controlGet(path: String): String {
            val response = client.get(controlUrl(path))
            val text = response.bodyAsText()
            check(response.status.value in 200..299) {
                "Proxy control API returned ${response.status.value} for GET $path: $text"
            }
            return text
        }

        internal suspend fun controlDelete(path: String) {
            val response = client.delete(controlUrl(path))
            if (response.status.value !in 200..299) {
                // Teardown should never throw, but a failed delete leaks a session — make it visible.
                System.err.println("Proxy control API returned ${response.status.value} for DELETE $path")
            }
        }
    }

    // ── Session instance API ──────────────────────────────────────────────────

    /**
     * Appends or prepends [rules] to this session's active rule list.
     *
     * @param rules    Rules to add.
     * @param position `"append"` (default) or `"prepend"`.
     */
    suspend fun addRules(rules: List<ProxyRule>, position: String = "append") {
        val body = JsonObject().apply {
            add("rules", gson.toJsonTree(rules))
            addProperty("position", position)
        }
        controlPost("/sessions/$sessionId/rules", body.toString())
    }

    /**
     * Triggers an imperative action on the current active WebSocket connection.
     *
     * Common actions:
     * ```kotlin
     * session.triggerAction(mapOf("type" to "disconnect"))
     * session.triggerAction(mapOf("type" to "close", "closeCode" to 1000))
     * session.triggerAction(mapOf("type" to "inject_to_client", "message" to mapOf("action" to 6)))
     * ```
     */
    suspend fun triggerAction(action: Map<String, Any>) {
        controlPost("/sessions/$sessionId/actions", gson.toJson(action))
    }

    /**
     * Returns the ordered event log recorded by the proxy for this session as typed [Event]s.
     *
     * Common [Event.type] values: `ws_connect`, `ws_frame`, `ws_disconnect`, `http_request`,
     * `http_response`, `action`. The raw protocol message is available via [Event.message].
     */
    suspend fun getLog(): List<Event> {
        val body = controlGet("/sessions/$sessionId/log")
        val data = gson.fromJson(body, JsonObject::class.java)
        val eventsEl = data["events"] ?: return emptyList()
        val listType = object : TypeToken<List<Event>>() {}.type
        return gson.fromJson(eventsEl, listType)
    }

    /**
     * Closes this session and stops its proxy listener.
     * Should always be called in a `finally` block after a test completes.
     * Cleanup errors are silently ignored.
     */
    suspend fun close() {
        runCatching { controlDelete("/sessions/$sessionId") }
    }
}

// ── Client wiring ───────────────────────────────────────────────────────────────

/**
 * Routes a [TestRealtimeClient] / [TestRestClient] through the given proxy [session].
 *
 * Call this inside the client builder block to point both the realtime and REST hosts at the
 * proxy listening on `localhost`:
 *
 * ```kotlin
 * val client = TestRealtimeClient {
 *     key = sandboxKey
 *     connectThroughProxy(session)
 *     autoConnect = false
 * }
 * ```
 *
 * Sets `realtimeHost` and `restHost` to the proxy host, `port` to the session's assigned port,
 * and `tls = false` (the proxy serves plain HTTP/WS; TLS is only used upstream to the sandbox).
 * `useBinaryProtocol` is already `false` by default in [ClientOptionsBuilder].
 *
 * Setting explicit hosts disables fallback hosts automatically, so no `fallbackHosts` is needed.
 */
fun ClientOptionsBuilder.connectThroughProxy(session: ProxySession) {
    realtimeHost = session.proxyHost
    restHost = session.proxyHost
    port = session.proxyPort
    tls = false
}
