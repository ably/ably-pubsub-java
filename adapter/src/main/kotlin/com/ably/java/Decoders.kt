package com.ably.java

import io.ably.lib.http.HttpUtils.JsonRequestBody
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.rest.Auth.*
import io.ably.lib.types.ChannelOptions
import io.ably.lib.types.ClientOptions
import io.ably.lib.types.Message
import io.ably.lib.types.Param
import kotlinx.serialization.json.*

fun JsonElement.asRequestParams(): Array<Param> {
    return when (this) {
        is JsonArray -> jsonArray.map { it.asRequestParam() }.toTypedArray()
        else -> arrayOf(asRequestParam())
    }
}

fun JsonElement.asRequestParam(): Param {
    return Param(
        jsonObject["key"]?.jsonPrimitive?.content,
        jsonObject["value"]?.jsonPrimitive?.content,
    )
}

fun JsonElement.asRequestBody() = JsonRequestBody(this.toString())

fun JsonElement.asClientOptions(authCallbackHandler: AuthCallbackHandler): ClientOptions {
    val options = ClientOptions()

    get("clientId")?.let { options.clientId = it.asString() }
    get("logLevel")?.let { options.logLevel = it.asInt() }
    get("tls")?.let { options.tls = it.asBoolean() }
    get("restHost")?.let { options.restHost = it.asString() }
    get("realtimeHost")?.let { options.realtimeHost = it.asString() }
    get("port")?.let { options.port = it.asInt() }
    get("tlsPort")?.let { options.tlsPort = it.asInt() }
    get("autoConnect")?.let { options.autoConnect = it.asBoolean() }
    get("useBinaryProtocol")?.let { options.useBinaryProtocol = it.asBoolean() }
    get("queueMessages")?.let { options.queueMessages = it.asBoolean() }
    get("echoMessages")?.let { options.echoMessages = it.asBoolean() }
    get("recover")?.let { options.recover = it.asString() }
    get("environment")?.let { options.environment = it.asString() }
    get("idempotentRestPublishing")?.let { options.idempotentRestPublishing = it.asBoolean() }
    get("httpOpenTimeout")?.let { options.httpOpenTimeout = it.asInt() }
    get("httpRequestTimeout")?.let { options.httpRequestTimeout = it.asInt() }
    get("httpMaxRetryDuration")?.let { options.httpMaxRetryDuration = it.asInt() }
    get("httpMaxRetryCount")?.let { options.httpMaxRetryCount = it.asInt() }
    get("realtimeRequestTimeout")?.let { options.realtimeRequestTimeout = it.asLong() }
    get("disconnectedRetryTimeout")?.let { options.disconnectedRetryTimeout = it.asLong() }
    get("suspendedRetryTimeout")?.let { options.suspendedRetryTimeout = it.asLong() }
    get("fallbackRetryTimeout")?.let { options.fallbackRetryTimeout = it.asLong() }
    get("defaultTokenParams")?.let { options.defaultTokenParams = it.asTokenParams() }
    get("channelRetryTimeout")?.let { options.channelRetryTimeout = it.asInt() }
    get("asyncHttpThreadpoolSize")?.let { options.asyncHttpThreadpoolSize = it.asInt() }
    get("pushFullWait")?.let { options.pushFullWait = it.asBoolean() }
    get("addRequestIds")?.let { options.addRequestIds = it.asBoolean() }
    get("authUrl")?.let { options.authUrl = it.asString() }
    get("authMethod")?.let { options.authMethod = it.asString() }
    get("key")?.let { options.key = it.asString() }
    get("token")?.let { options.token = if (it is JsonPrimitive) it.asString() else it.jsonObject["token"]?.asString() }
    get("queryTime")?.let { options.queryTime = it.asBoolean() }
    get("useTokenAuth")?.let { options.useTokenAuth = it.asBoolean() }
    get("authCallback")?.let { options.authCallback = authCallbackHandler.createTokenRequest(it.asString()) }

    return options
}

fun JsonElement.asMessageData(): String {
    return jsonPrimitive.content
}

fun JsonElement.asArrayOfMessage(): Array<Message> = jsonArray.map { it.asMessage() }.toTypedArray()

fun JsonElement.asMessage(): Message {
    val message = Message()
    get("name")?.let { message.name = it.asString() }
    get("connectionKey")?.let { message.connectionKey = it.asString() }
    get("id")?.let { message.id = it.asString() }
    get("clientId")?.let { message.clientId = it.asString() }
    get("connectionId")?.let { message.connectionId = it.asString() }
    get("encoding")?.let { message.encoding = it.asString() }
    get("timestamp")?.let { message.timestamp = it.asLong() }
    get("connectionKey")?.let { message.connectionKey = it.asString() }
    return message
}

fun JsonElement.asTokenParams(): TokenParams {
    val tokenParams = TokenParams()
    get("ttl")?.let { tokenParams.ttl = it.asLong() }
    get("capability")?.let { tokenParams.capability = if (it is JsonObject) it.toString() else it.asString() }
    get("clientId")?.let { tokenParams.clientId = it.asString() }
    get("timestamp")?.let { tokenParams.timestamp = it.asLong() }
    return tokenParams
}

fun JsonElement.asTokenDetails(): TokenDetails {
    val tokenDetails = TokenDetails()
    get("token")?.let { tokenDetails.token = it.asString() }
    get("expires")?.let { tokenDetails.expires = it.asLong() }
    get("issued")?.let { tokenDetails.issued = it.asLong() }
    get("capability")?.let { tokenDetails.capability = if (it is JsonObject) it.toString() else it.asString() }
    get("clientId")?.let { tokenDetails.clientId = it.asString() }
    return tokenDetails
}

fun JsonElement.asTokenRequest(): TokenRequest {
    val tokenRequest = TokenRequest()
    get("keyName")?.let { tokenRequest.keyName = it.asString() }
    get("nonce")?.let { tokenRequest.nonce = it.asString() }
    get("mac")?.let { tokenRequest.mac = it.asString() }
    get("timestamp")?.let { tokenRequest.timestamp = it.asLong() }
    get("ttl")?.let { tokenRequest.ttl = it.asLong() }
    get("capability")?.let { tokenRequest.capability = if (it is JsonObject) it.toString() else it.asString() }
    return tokenRequest
}

fun JsonElement.asAuthOptions(): AuthOptions {
    val authOptions = AuthOptions()
    get("authUrl")?.let { authOptions.authUrl = it.asString() }
    get("authMethod")?.let { authOptions.authMethod = it.asString() }
    get("key")?.let { authOptions.key = it.asString() }
    get("token")?.let { authOptions.token = it.asString() }
    get("authHeaders")?.let { authOptions.authHeaders = it.asRequestHeaders() }
    get("authParams")?.let { authOptions.authParams = it.asRequestParams() }
    get("queryTime")?.let { authOptions.queryTime = it.asBoolean() }
    get("useTokenAuth")?.let { authOptions.useTokenAuth = it.asBoolean() }
    return authOptions
}

fun JsonElement.asChannelOptions(): ChannelOptions {
    val channelOptions = ChannelOptions()
    get("params")?.let { channelOptions.params = it.asChannelParams() }
    get("encrypted")?.let { channelOptions.encrypted = it.asBoolean() }
    return channelOptions
}

fun JsonElement.asConnectionState(): ConnectionState = ConnectionState.valueOf(asString())

fun JsonElement.asChannelParams(): Map<String, String> = buildMap {}
