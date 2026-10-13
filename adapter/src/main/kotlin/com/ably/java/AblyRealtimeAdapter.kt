package com.ably.java

import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.ChannelBase.MessageListener
import io.ably.lib.realtime.Connection
import io.ably.lib.realtime.Presence
import io.ably.lib.rest.AblyRest
import io.ably.lib.rest.Auth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

typealias RealtimeChannels = AblyRealtime.Channels
typealias RealtimeChannel = io.ably.lib.realtime.Channel
typealias RestChannels = io.ably.lib.rest.AblyBase.Channels
typealias RestChannel = io.ably.lib.rest.Channel
typealias RestPresence = io.ably.lib.rest.ChannelBase.Presence
typealias RealtimePresence = Presence

class AblyRealtimeAdapter(
    val sendMessage: MutableSharedFlow<String>,
    val authCallbackHandler: AuthCallbackHandler,
) {
    private val idToAblyRest: MutableMap<String, AblyRest> = mutableMapOf()
    private val idToAblyRealtime: MutableMap<String, AblyRealtime> = mutableMapOf()
    private val idToRestChannels: MutableMap<String, RestChannels> = mutableMapOf()
    private val idToRestChannel: MutableMap<String, RestChannel> = mutableMapOf()
    private val idToRealtimeChannels: MutableMap<String, RealtimeChannels> = mutableMapOf()
    private val idToRealtimeChannel: MutableMap<String, RealtimeChannel> = mutableMapOf()
    private val idToAuth: MutableMap<String, Auth> = mutableMapOf()
    private val idToConnection: MutableMap<String, Connection> = mutableMapOf()
    private val idToRestPresence: MutableMap<String, RestPresence> = mutableMapOf()
    private val idToRealtimePresence: MutableMap<String, RealtimePresence> = mutableMapOf()
    private val idToSubscribeCallback: MutableMap<String, MessageListener> = mutableMapOf()

    suspend fun handleRpcCall(rpcRequest: JsonElement): String? {
        if (!rpcRequest.isJsonRpc() || !rpcRequest.hasMethod()) return null

        return when (rpcRequest.methodName) {
            "AblyRest" -> {
                val options = rpcRequest.getArg("options").asClientOptions(authCallbackHandler)
                val refId = generateId()
                idToAblyRest[refId] = AblyRest(options)
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", refId)
                })
            }

            "AblyRest#auth" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToAblyRest[refId]!!
                val field = instance.auth
                val fieldRefId = generateId()
                idToAuth[fieldRefId] = field
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", fieldRefId)
                })
            }

            "AblyRest#channels" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToAblyRest[refId]!!
                val field = instance.channels
                val fieldRefId = generateId()
                idToRestChannels[fieldRefId] = field
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", fieldRefId)
                })
            }

            "AblyRest.request" -> {
                val method = rpcRequest.getArg("method").asString()
                val path = rpcRequest.getArg("path").asString()
                val params = rpcRequest.getArg("params").asRequestParams()
                val body = rpcRequest.getArg("body").asRequestBody()
                val headers = rpcRequest.getArg("headers").asRequestHeaders()
                val refId = rpcRequest.getRefId()
                val instance = idToAblyRest[refId]!!
                val result = instance.request(method, path, params, body, headers)
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                    put("response", result.toJsonElement())
                })
            }

            "AblyRest.time" -> {

                val refId = rpcRequest.getRefId()
                val instance = idToAblyRest[refId]!!
                val result = instance.time()
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                    put("response", result)
                })
            }

            "AblyRest.close" -> {

                val refId = rpcRequest.getRefId()
                val instance = idToAblyRest[refId]!!
                instance.close()
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                })
            }

            "AblyRealtime" -> {
                val options = rpcRequest.getArg("options").asClientOptions(authCallbackHandler)
                val refId = generateId()
                idToAblyRealtime[refId] = AblyRealtime(options)
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", refId)
                })
            }

            "AblyRealtime#auth" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToAblyRealtime[refId]!!
                val field = instance.auth
                val fieldRefId = generateId()
                idToAuth[fieldRefId] = field
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", fieldRefId)
                })
            }

            "AblyRealtime#channels" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToAblyRealtime[refId]!!
                val field = instance.channels
                val fieldRefId = generateId()
                idToRealtimeChannels[fieldRefId] = field
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", fieldRefId)
                })
            }

            "AblyRealtime#connection" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToAblyRealtime[refId]!!
                val field = instance.connection
                val fieldRefId = generateId()
                idToConnection[fieldRefId] = field
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", fieldRefId)
                })
            }

            "AblyRealtime.request" -> {
                val method = rpcRequest.getArg("method").asString()
                val path = rpcRequest.getArg("path").asString()
                val params = rpcRequest.getArg("params").asRequestParams()
                val body = rpcRequest.getArg("body").asRequestBody()
                val headers = rpcRequest.getArg("headers").asRequestHeaders()
                val refId = rpcRequest.getRefId()
                val instance = idToAblyRealtime[refId]!!
                val result = instance.request(method, path, params, body, headers)
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                    put("response", result.toJsonElement())
                })
            }

            "AblyRealtime.time" -> {

                val refId = rpcRequest.getRefId()
                val instance = idToAblyRealtime[refId]!!
                val result = instance.time()
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                    put("response", result)
                })
            }

            "AblyRealtime.close" -> {

                val refId = rpcRequest.getRefId()
                val instance = idToAblyRealtime[refId]!!
                instance.close()
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                })
            }

            "Auth#clientId" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToAuth[refId]!!
                val field = instance.clientId
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("response", field)
                })
            }

            "Auth.authorize" -> {
                val tokenParams = rpcRequest.getOptionalArg("tokenParams")?.asTokenParams()
                val authOptions = rpcRequest.getOptionalArg("authOptions")?.asAuthOptions()
                val refId = rpcRequest.getRefId()
                val instance = idToAuth[refId]!!
                val result = instance.authorize(tokenParams, authOptions)
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                    put("response", result.toJsonElement())
                })
            }

            "Auth.createTokenRequest" -> {
                val tokenParams = rpcRequest.getOptionalArg("tokenParams")?.asTokenParams()
                val authOptions = rpcRequest.getOptionalArg("authOptions")?.asAuthOptions()
                val refId = rpcRequest.getRefId()
                val instance = idToAuth[refId]!!
                val result = instance.createTokenRequest(tokenParams, authOptions)
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                    put("response", result.toJsonElement())
                })
            }

            "Auth.requestToken" -> {
                val tokenParams = rpcRequest.getOptionalArg("tokenParams")?.asTokenParams()
                val authOptions = rpcRequest.getOptionalArg("authOptions")?.asAuthOptions()
                val refId = rpcRequest.getRefId()
                val instance = idToAuth[refId]!!
                val result = instance.requestToken(tokenParams, authOptions)
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                    put("response", result.toJsonElement())
                })
            }

            "RestChannels.get" -> {
                val name = rpcRequest.getArg("name").asString()
                val channelOptions = rpcRequest.getOptionalArg("channelOptions")?.asChannelOptions()
                val refId = rpcRequest.getRefId()
                val instance = idToRestChannels[refId]!!
                val result = channelOptions?.let { instance.get(name, channelOptions) } ?: instance.get(name)
                val resultRefId = generateId()
                idToRestChannel[resultRefId] = result
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)

                })
            }

            "RestChannels.release" -> {
                val name = rpcRequest.getArg("name").asString()
                val refId = rpcRequest.getRefId()
                val instance = idToRestChannels[refId]!!
                instance.release(name)
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)

                })
            }

            "RestChannel#name" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToRestChannel[refId]!!
                val field = instance.name
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("response", field)
                })
            }

            "RestChannel.publish" -> {
                val messages = rpcRequest.getOptionalArg("messages")?.asArrayOfMessage()
                val name = rpcRequest.getOptionalArg("name")?.asString()
                val data = rpcRequest.getOptionalArg("data")?.asMessageData()
                val refId = rpcRequest.getRefId()
                val instance = idToRestChannel[refId]!!
                if (messages != null) {
                    instance.publish(messages)
                } else {
                    instance.publish(name, data)
                }

                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                })
            }

            "RestChannel.history" -> {
                val params = rpcRequest.getArg("params").asRequestParams()
                val refId = rpcRequest.getRefId()
                val instance = idToRestChannel[refId]!!
                val result = instance.history(params)
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                    put("response", result.toJsonElement())
                })
            }

            "RealtimeChannels.get" -> {
                val name = rpcRequest.getArg("name").asString()
                val channelOptions = rpcRequest.getOptionalArg("channelOptions")?.asChannelOptions()
                val refId = rpcRequest.getRefId()
                val instance = idToRealtimeChannels[refId]!!
                val result = instance.get(name, channelOptions)
                val resultRefId = generateId()
                idToRealtimeChannel[resultRefId] = result
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)

                })
            }

            "RealtimeChannels.release" -> {
                val name = rpcRequest.getArg("name").asString()
                val refId = rpcRequest.getRefId()
                val instance = idToRealtimeChannels[refId]!!
                instance.release(name)
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)

                })
            }

            "RealtimeChannel#name" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToRealtimeChannel[refId]!!
                val field = instance.name
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("response", field)
                })
            }

            "RealtimeChannel#state" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToRealtimeChannel[refId]!!
                val field = instance.state
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("response", field.toJsonElement())
                })
            }

            "RealtimeChannel#presence" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToRealtimeChannel[refId]!!
                val field = instance.presence
                val fieldRefId = generateId()
                idToRealtimePresence[fieldRefId] = field
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", fieldRefId)
                })
            }

            "RealtimeChannel#errorReason" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToRealtimeChannel[refId]!!
                val field = instance.reason
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("response", field.toJsonElement())
                })
            }

            "RealtimeChannel#params" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToRealtimeChannel[refId]!!
                val field = instance.params
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("response", field.toJsonElement())
                })
            }

            "RealtimeChannel#modes" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToRealtimeChannel[refId]!!
                val field = instance.modes
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("response", field.toJsonElement())
                })
            }

            "RealtimeChannel.publish_0" -> {
                val messages = rpcRequest.getArg("messages").asArrayOfMessage()
                val refId = rpcRequest.getRefId()
                val instance = idToRealtimeChannel[refId]!!
                instance.publish(messages)
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)

                })
            }

            "RealtimeChannel.publish_1" -> {
                val name = rpcRequest.getArg("name").asString()
                val data = rpcRequest.getArg("data").asMessageData()
                val refId = rpcRequest.getRefId()
                val instance = idToRealtimeChannel[refId]!!
                instance.publish(name, data)
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)

                })
            }

            "RealtimeChannel.subscribe_0" -> {
                val refId = rpcRequest.getRefId()
                val callbackId = rpcRequest.getCallbackId()
                val instance = idToRealtimeChannel[refId]!!
                val messageListener = MessageListener { message ->
                    emitCallback(callbackId, buildJsonObject {
                        put("type", "message")
                        put("message", message.toJsonElement())
                    })
                }
                idToSubscribeCallback[callbackId] = messageListener
                instance.subscribe(messageListener)
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", refId)
                })
            }

            "RealtimeChannel.subscribe_1" -> {
                val events = rpcRequest.getArg("events").asArrayOfStings()
                val refId = rpcRequest.getRefId()
                val callbackId = rpcRequest.getCallbackId()
                val instance = idToRealtimeChannel[refId]!!
                val messageListener = MessageListener { message ->
                    emitCallback(callbackId, buildJsonObject {
                        put("type", "message")
                        put("message", message.toJsonElement())
                    })
                }
                idToSubscribeCallback[callbackId] = messageListener
                instance.subscribe(events, messageListener)
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", refId)
                })
            }

            "RealtimeChannel.unsubscribe_0" -> {
                val name = rpcRequest.getArg("name").asString()
                val refId = rpcRequest.getRefId()
                val callbackId = rpcRequest.getCallbackId()
                val instance = idToRealtimeChannel[refId]!!
                instance.unsubscribe(name, idToSubscribeCallback[callbackId]!!)
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", refId)
                })
            }

            "RealtimeChannel.unsubscribe_1" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToRealtimeChannel[refId]!!
                instance.unsubscribe()
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", refId)
                })
            }

            "RealtimeChannel.attach" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToRealtimeChannel[refId]!!
                instance.attachAsync()
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", refId)
                })
            }

            "RealtimeChannel.detach" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToRealtimeChannel[refId]!!
                instance.detachAsync()
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", refId)
                })
            }

            "RealtimeChannel.history" -> {
                val params = rpcRequest.getArg("params").asRequestParams()
                val refId = rpcRequest.getRefId()
                val instance = idToRealtimeChannel[refId]!!
                val result = instance.history(params)
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                    put("response", result.toJsonElement())
                })
            }

            "Connection#errorReason" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToConnection[refId]!!
                val field = instance.reason
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("response", field.toJsonElement())
                })
            }

            "Connection#id" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToConnection[refId]!!
                val field = instance.id
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("response", field)
                })
            }

            "Connection#key" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToConnection[refId]!!
                val field = instance.key
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("response", field)
                })
            }

            "Connection#state" -> {
                val refId = rpcRequest.getRefId()
                val instance = idToConnection[refId]!!
                val field = instance.state
                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("response", field.toJsonElement())
                })
            }

            "Connection.createRecoveryKey" -> {

                val refId = rpcRequest.getRefId()
                val instance = idToConnection[refId]!!
                val result = instance.createRecoveryKey()
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                    put("response", result)
                })
            }

            "Connection.close" -> {

                val refId = rpcRequest.getRefId()
                val instance = idToConnection[refId]!!
                instance.close()
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)

                })
            }

            "Connection.connect" -> {

                val refId = rpcRequest.getRefId()
                val instance = idToConnection[refId]!!
                instance.connect()
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                })
            }

            "Connection.on" -> {
                val refId = rpcRequest.getRefId()
                val callbackId = rpcRequest.getCallbackId()
                val events = rpcRequest.getOptionalArg("events")?.jsonArray?.map { it.asConnectionState() }
                val instance = idToConnection[refId]!!
                if (events != null) {
                    events.forEach {
                        instance.on(it.connectionEvent, { stateChange ->
                            emitCallback(callbackId, buildJsonObject {
                                put("type", "stateChange")
                                put("stateChange", stateChange.toJsonElement())
                            })
                        })
                    }
                } else {
                    instance.on({ stateChange ->
                        emitCallback(callbackId, buildJsonObject {
                            put("type", "stateChange")
                            put("stateChange", stateChange.toJsonElement())
                        })
                    })
                }

                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                })
            }

            "Connection.once" -> {
                val refId = rpcRequest.getRefId()
                val callbackId = rpcRequest.getCallbackId()
                val event = rpcRequest.getOptionalArg("event")?.asConnectionState()
                val instance = idToConnection[refId]!!
                if (event != null) {
                    instance.once(event.connectionEvent, { stateChange ->
                        emitCallback(callbackId, buildJsonObject {
                            put("type", "stateChange")
                            put("stateChange", stateChange.toJsonElement())
                        })
                    })
                } else {
                    instance.once({ stateChange ->
                        emitCallback(callbackId, buildJsonObject {
                            put("type", "stateChange")
                            put("stateChange", stateChange.toJsonElement())
                        })
                    })
                }

                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                })
            }

            "RestPresence.get" -> {
                val params = rpcRequest.getArg("params").asRestPresenceParams()
                val refId = rpcRequest.getRefId()
                val instance = idToRestPresence[refId]!!
                val result = instance.get(params)
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                    put("response", result.toJsonElement())
                })
            }

            "RestPresence.history" -> {
                val params = rpcRequest.getArg("params").asRestPresenceParams()
                val refId = rpcRequest.getRefId()
                val instance = idToRestPresence[refId]!!
                val result = instance.history(params)
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                    put("response", result.toJsonElement())
                })
            }

            "RealtimePresence.get" -> {
                val params = rpcRequest.getArg("params").asPresenceParams()
                val refId = rpcRequest.getRefId()
                val instance = idToRealtimePresence[refId]!!
                val result = instance.get(*params)
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                    put("response", result.toJsonElement())
                })
            }

            "RealtimePresence.history" -> {
                val params = rpcRequest.getArg("params").asRestPresenceParams()
                val refId = rpcRequest.getRefId()
                val instance = idToRealtimePresence[refId]!!
                val result = instance.history(params)
                val resultRefId = generateId()

                jsonRpcResponse(rpcRequest, buildJsonObject {
                    put("refId", resultRefId)
                    put("response", result.toJsonElement())
                })
            }


            else -> null
        }
    }

    fun emitCallback(callbackId: String, payload: JsonElement? = null) {
        val callbackJson = buildJsonObject {
            put("jsonrpc", JsonPrimitive("2.0"))
            put("id", generateId())
            put("method", "callback")
            put("params", buildJsonObject {
                put("callbackId", callbackId)
                if (payload != null) {
                    put("payload", payload)
                }
            })
        }

        runBlocking {
            sendMessage.emit(callbackJson.toString())
        }
    }
}
