package io.ably.lib.uts.infra.unit

import io.ably.lib.network.EngineType
import io.ably.lib.network.WebSocketClient
import io.ably.lib.network.WebSocketEngine
import io.ably.lib.network.WebSocketEngineConfig
import io.ably.lib.network.WebSocketEngineFactory
import io.ably.lib.network.WebSocketListener
import java.net.URI

internal class MockWebSocketEngineFactory(
    private val onConnect: (PendingConnection) -> Unit,
    private val onConnected: (WebSocketListener) -> Unit = {},
    private val onTextFrame: (String) -> Unit = {},
    private val onBinaryFrame: (ByteArray) -> Unit = {},
    private val onClientClose: (Int, String) -> Unit = { _, _ -> },
) : WebSocketEngineFactory {
    override fun create(config: WebSocketEngineConfig): WebSocketEngine =
        MockWebSocketEngine(onConnect, onConnected, onTextFrame, onBinaryFrame, onClientClose)

    override fun getEngineType(): EngineType = EngineType.DEFAULT
}

internal class MockWebSocketEngine(
    private val onConnect: (PendingConnection) -> Unit,
    private val onConnected: (WebSocketListener) -> Unit,
    private val onTextFrame: (String) -> Unit,
    private val onBinaryFrame: (ByteArray) -> Unit,
    private val onClientClose: (Int, String) -> Unit,
) : WebSocketEngine {
    override fun create(url: String, listener: WebSocketListener): WebSocketClient =
        MockWebSocketClient(url, listener, onConnect, onConnected, onTextFrame, onBinaryFrame, onClientClose)

    override fun isPingListenerSupported() = false
}

internal class MockWebSocketClient(
    private val url: String,
    private val listener: WebSocketListener,
    private val onConnect: (PendingConnection) -> Unit,
    private val onConnected: (WebSocketListener) -> Unit,
    private val onTextFrame: (String) -> Unit,
    private val onBinaryFrame: (ByteArray) -> Unit,
    private val onClientClose: (Int, String) -> Unit,
) : WebSocketClient {
    override fun connect() {
        val uri = URI(url)
        val tls = uri.scheme == "wss"
        val port = if (uri.port == -1) (if (tls) 443 else 80) else uri.port
        onConnect(DefaultPendingConnection(uri.host, port, tls, parseQueryString(uri.rawQuery), listener, onConnected))
    }

    override fun close() = close(1000, "Normal closure")
    override fun close(code: Int, reason: String) {
      onClientClose(code, reason)
      listener.onClose(code, reason)   // drive the SDK
    }
    override fun cancel(code: Int, reason: String) {
      onClientClose(code, reason)
      listener.onClose(code, reason)   // drive the SDK
    }
    override fun send(message: ByteArray) { onBinaryFrame(message) }
    override fun send(message: String) { onTextFrame(message) }
}
