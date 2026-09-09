package io.ably.lib.uts.infra.integration

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.util.*
import kotlinx.coroutines.CancellationException

private val client = HttpClient(CIO) {
  install(HttpRequestRetry) {
    maxRetries = 5
    // Only retry idempotent reads (the shared app-setup fetch). Retrying POST /apps risks
    // provisioning duplicate sandbox apps if the write succeeds but the response is lost.
    retryIf { request, response -> request.method == HttpMethod.Get && !response.status.isSuccess() }
    retryOnExceptionIf { request, cause ->
      request.method == HttpMethod.Get && (
        cause is ConnectTimeoutException ||
          cause is HttpRequestTimeoutException ||
          cause is SocketTimeoutException
        )
    }
    exponentialDelay()
  }
  install(HttpTimeout) {
    requestTimeoutMillis = 30_000
    connectTimeoutMillis = 10_000
    socketTimeoutMillis = 30_000
  }
}

/**
 * A test app provisioned in the Ably sandbox (`sandbox.realtime.ably-nonprod.net`).
 *
 * Proxy integration tests provision one app in suite setup ([create]) and tear it down in
 * suite teardown ([delete]). The app is created against the sandbox directly (not through the
 * proxy) so provisioning is independent of any fault rules under test.
 *
 * ```kotlin
 * val app = SandboxApp.create()
 * try {
 *     val key = app.defaultKey   // "appId.keyId:keySecret"
 *     // … tests …
 * } finally {
 *     app.delete()
 * }
 * ```
 */
class SandboxApp private constructor(
  /** The provisioned app's id. */
  val appId: String,
  /**
   * A full-capability API key string in `appId.keyId:keySecret` form.
   */
  val defaultKey: String,
  /**
   * A list of API keys with different capabilities in `appId.keyId:keySecret` form. The first key is the default key.
   *
   * @see https://raw.githubusercontent.com/ably/ably-common/refs/heads/main/test-resources/test-app-setup.json
   */
  val keys: List<String>,
) {

  companion object {
    /**
     * The Ably **nonprod sandbox** host — the `nonprod:sandbox` endpoint (used uniformly across the
     * realtime/objects/rest integration specs), resolved to a hostname. Realtime and REST share this
     * single host, so point both transports at it: set `realtimeHost` and/or `restHost` from here.
     */
    const val sandboxHost = "sandbox.realtime.ably-nonprod.net"

    private const val sandboxBaseUrl = "https://$sandboxHost"

    /** The canonical app spec shared across all Ably SDK test suites. */
    private const val APP_SETUP_URL =
      "https://raw.githubusercontent.com/ably/ably-common/refs/heads/main/test-resources/test-app-setup.json"

    /** Fetches the `post_apps` body from the shared `test-app-setup.json` in ably-common. */
    private suspend fun loadAppCreationJson(): JsonElement =
      JsonParser.parseString(
        client.get(APP_SETUP_URL) {
          contentType(ContentType.Application.Json)
        }.bodyAsText(),
      ).asJsonObject.get("post_apps")

    /** Provisions a fresh sandbox app and returns its id and first key. */
    suspend fun create(): SandboxApp {
      val response: HttpResponse = client.post("$sandboxBaseUrl/apps") {
        contentType(ContentType.Application.Json)
        setBody(loadAppCreationJson().toString())
      }
      val responseBody = response.bodyAsText()
      check(response.status.isSuccess()) {
        "sandbox app provisioning failed: HTTP ${response.status} — $responseBody"
      }
      val body = JsonParser.parseString(responseBody).asJsonObject
      val keys = body["keys"].asJsonArray.map { it.asJsonObject["keyStr"].asString }
      return SandboxApp(
        appId = body["appId"].asString,
        defaultKey = keys.first(),
        keys = keys,
      )
    }
  }

  /**
   * Deletes the provisioned app. Errors are ignored so teardown never masks a test failure;
   * [CancellationException] is rethrown so cooperative cancellation is preserved.
   */
  suspend fun delete() {
    runCatching {
      val basic = Base64.getEncoder().encodeToString(defaultKey.toByteArray())
      client.delete("$sandboxBaseUrl/apps/$appId") {
        header(HttpHeaders.Authorization, "Basic $basic")
      }
    }.onFailure { if (it is CancellationException) throw it }
  }
}
