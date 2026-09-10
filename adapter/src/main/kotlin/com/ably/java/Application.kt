package com.ably.java

import io.ably.lib.types.AblyException
import io.ktor.client.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

fun main() {
    val client = HttpClient {
        install(WebSockets)
        install(Logging) {
            level = LogLevel.ALL
        }
    }

    val sendMessageFlow = MutableSharedFlow<String>()
    val json = Json
    val authCallbackHandler = AuthCallbackHandler()
    val ablyRealtimeAdapter = AblyRealtimeAdapter(sendMessageFlow, authCallbackHandler)

    runBlocking {
        client.webSocket(HttpMethod.Get, host = "localhost", port = 3000) {
            val messageReceivedRoutine = launch {
                try {
                    for (message in incoming) {
                        message as? Frame.Text ?: continue
                        val messageText = message.readText()
                        println("Received: $messageText")
                        val rpcRequest = json.parseToJsonElement(messageText)
                        try {
                            ablyRealtimeAdapter.handleRpcCall(rpcRequest)?.let {
                                sendMessageFlow.emit(it)
                            } ?: run {
                                if (rpcRequest.isJsonRpc() && rpcRequest.hasMethod()) {
                                    sendMessageFlow.emit(notImplementedResponse(rpcRequest))
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            println("Error while handling: $e")

                            if (e is AblyException) {
                                sendMessageFlow.emit(ablyExceptionResponse(rpcRequest, e))
                            } else {
                                sendMessageFlow.emit(unhandledExceptionResponse(rpcRequest, e))
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    println("Error while receiving: $e")
                }
            }

            val messageSendRoutine = sendMessageFlow.onEach {
                try {
                    println("Sent: $it")
                    send(it)
                } catch (e: Exception) {
                    println("Error while sending: $e")
                }
            }.launchIn(this@runBlocking)

            send("{\"role\":\"IMPLEMENTATION\"}")

            messageSendRoutine.join()
            messageReceivedRoutine.cancelAndJoin()
        }
        client.close()
    }
}
