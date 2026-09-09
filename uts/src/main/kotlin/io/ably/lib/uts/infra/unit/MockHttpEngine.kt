package io.ably.lib.uts.infra.unit

import io.ably.lib.network.FailedConnectionException
import io.ably.lib.network.HttpCall
import io.ably.lib.network.HttpEngine
import io.ably.lib.network.HttpRequest
import io.ably.lib.network.HttpResponse
import io.ably.lib.types.ProtocolMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal class MockHttpEngine(
    private val onConnect: (PendingConnection) -> Unit,
    private val onRequest: (PendingRequest) -> Unit,
) : HttpEngine {
    override fun call(request: HttpRequest): HttpCall = MockHttpCall(request, onConnect, onRequest)
    override fun isUsingProxy() = false
}

internal class MockHttpCall(
    private val request: HttpRequest,
    private val onConnect: (PendingConnection) -> Unit,
    private val onRequest: (PendingRequest) -> Unit,
) : HttpCall {
    @Volatile private var connDeferred: CompletableDeferred<Unit>? = null
    @Volatile private var respDeferred: CompletableDeferred<HttpResponse>? = null

    override fun execute(): HttpResponse = runBlocking {
        // Phase 1 — connection
        val cd = CompletableDeferred<Unit>().also { connDeferred = it }
        val url = request.url
        val tls = url.protocol == "https"
        val port = if (url.port != -1) url.port else if (tls) 443 else 80
        val queryParams = parseQueryString(url.query)
        onConnect(DefaultHttpPendingConnection(url.host, port, tls, queryParams, cd))
        cd.await()

        // Phase 2 — request
        val rd = CompletableDeferred<HttpResponse>().also { respDeferred = it }
        onRequest(io.ably.lib.uts.infra.unit.DefaultPendingRequest(request, rd))
        rd.await()
    }

    override fun cancel() {
        connDeferred?.cancel()
        respDeferred?.cancel()
    }
}

internal class DefaultHttpPendingConnection(
    override val host: String,
    override val port: Int,
    override val tls: Boolean,
    override val queryParams: Map<String, String>,
    private val deferred: CompletableDeferred<Unit>,
) : PendingConnection {
    override fun respondWithSuccess() { deferred.complete(Unit) }
    override fun respondWithSuccess(message: ProtocolMessage) = respondWithSuccess()
    override fun respondWithRefused() { deferred.completeExceptionally(
        FailedConnectionException(IOException("Connection refused to $host:$port"))) }
    override fun respondWithTimeout() { deferred.completeExceptionally(
        FailedConnectionException(SocketTimeoutException("Connection timed out to $host:$port"))) }
    override fun respondWithDnsError() { deferred.completeExceptionally(
        FailedConnectionException(UnknownHostException(host))) }
}
