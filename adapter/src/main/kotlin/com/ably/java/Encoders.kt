package com.ably.java

import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.realtime.ConnectionStateListener.ConnectionStateChange
import io.ably.lib.rest.Auth.*
import io.ably.lib.types.*
import kotlinx.serialization.json.*

fun ErrorInfo.toJsonElement(): JsonElement = buildJsonObject {
    put("code", code)
    put("statusCode", statusCode)
    put("message", message)
    put("href", href)
}


fun HttpPaginatedResponse.toJsonElement(): JsonElement = JsonArray(items().map {
    it.toKotlin()
})

fun TokenDetails.toJsonElement(): JsonElement = buildJsonObject {
    put("token", token)
    put("expires", expires)
    put("issued", issued)
    put("capability", capability)
    put("clientId", clientId)
}
fun TokenRequest.toJsonElement(): JsonElement = buildJsonObject {
    put("keyName", keyName)
    put("nonce", nonce)
    put("mac", mac)
    put("timestamp", timestamp)
    if (ttl != 0L) put("ttl", ttl)
    if (capability != null) put("capability", capability)
}

fun PresenceMessage.toJsonElement(): JsonElement = buildJsonObject {
    put("action", action.value)
    put("id", id)
    put("clientId", clientId)
    put("connectionId", connectionId)
    put("encoding", encoding)
    put("timestamp", timestamp)
}

fun Message.toJsonElement(): JsonElement = buildJsonObject {
    put("id", id)
    put("name", name)
    put("data", data.toString())
    put("connectionKey", connectionKey)
    put("clientId", clientId)
    put("connectionId", connectionId)
    put("encoding", encoding)
    put("timestamp", timestamp)
}

@JvmName("fromPresenceMessageToJsonElement")
fun PaginatedResult<PresenceMessage>.toJsonElement(): JsonElement = JsonArray(items().map { it.toJsonElement() })
@JvmName("fromMessageToJsonElement")
fun PaginatedResult<Message>.toJsonElement(): JsonElement = JsonArray(items().map { it.toJsonElement() })
fun Array<PresenceMessage>.toJsonElement(): JsonElement = JsonArray(map { it.toJsonElement() })

fun Map<String, String>.toJsonElement(): JsonElement = JsonObject(mapValues { JsonPrimitive(it.value) })
fun Array<ChannelMode>.toJsonElement() = JsonArray(map { JsonPrimitive(it.name) })
fun ChannelState.toJsonElement() = JsonPrimitive(this.name)
fun ConnectionState.toJsonElement() = JsonPrimitive(this.name)
fun ConnectionStateChange.toJsonElement(): JsonElement = buildJsonObject {
    put("previous", previous.name)
    put("current", current.name)
    reason?.let { put("reason", it.toJsonElement()) }
    put("retryIn", retryIn)
}

fun TokenParams.toJsonElement(): JsonElement = buildJsonObject {
    if (ttl != 0L) put("ttl", ttl)
    if (timestamp != 0L) put("timestamp", timestamp)
    capability?.let { put("capability", it) }
    clientId?.let { put("clientId", it) }
}
