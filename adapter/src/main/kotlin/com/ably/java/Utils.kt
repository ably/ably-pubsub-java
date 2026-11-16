package com.ably.java

import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.ChannelState
import io.ably.lib.realtime.CompletionListener
import io.ably.lib.types.AblyException
import io.ably.lib.types.ErrorInfo
import kotlinx.serialization.json.*
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

fun generateId() = UUID.randomUUID().toString().replace("-", "")

fun JsonElement.isJsonRpc() = jsonObject.keys.contains("jsonrpc")
fun JsonElement.hasMethod() = jsonObject.keys.contains("method")
val JsonElement.methodName get() = jsonObject["method"]?.jsonPrimitive?.content


fun JsonElement.getParams() = jsonObject["params"]?.jsonObject!!
fun JsonElement.getRefId() = getParams() ["refId"]?.jsonPrimitive?.content!!
fun JsonElement.getCallbackId() = getParams()["callbackId"]?.jsonPrimitive?.content!!
fun JsonElement.getArg(name: String) = getParams()["args"]!!.jsonObject[name]!!
fun JsonElement.getOptionalArg(name: String) = getParams()["args"]?.jsonObject?.get(name)?.takeUnless { it is JsonNull }
fun JsonElement.get(name: String) = jsonObject[name].takeUnless { it is JsonNull }
fun JsonElement.asString() = jsonPrimitive.content
fun JsonElement.asInt() = jsonPrimitive.int
fun JsonElement.asLong() = jsonPrimitive.long
fun JsonElement.asBoolean() = jsonPrimitive.boolean
fun JsonElement.asRequestHeaders() = asRequestParams()
fun JsonElement.asPresenceParams() = asRequestParams()
fun JsonElement.asRestPresenceParams() = asRequestParams()
fun JsonElement.asArrayOfStings() = jsonArray.map { it.asString() }.toTypedArray()

fun notImplementedResponse(rpcRequest: JsonElement) = buildJsonObject {
    put("jsonrpc", JsonPrimitive("2.0"))
    put("id", rpcRequest.jsonObject["id"]!!)
    put("error", buildJsonObject {
        put("data", buildJsonObject {
            put("ablyError", false)
        })
        put("message", "Not implemented")
    })
}.toString()

fun unhandledExceptionResponse(rpcRequest: JsonElement, exception: Exception) = buildJsonObject {
    put("jsonrpc", JsonPrimitive("2.0"))
    put("id", rpcRequest.jsonObject["id"]!!)
    put("error", buildJsonObject {
        put("data", buildJsonObject {
            put("ablyError", false)
        })
        put("message", exception.message)
    })
}.toString()

fun ablyExceptionResponse(rpcRequest: JsonElement, exception: AblyException) = buildJsonObject {
    put("jsonrpc", JsonPrimitive("2.0"))
    put("id", rpcRequest.jsonObject["id"]!!)
    put("error", buildJsonObject {
        put("data", buildJsonObject {
            put("ablyError", true)
            put("errorInfo", exception.errorInfo.toJsonElement())
        })
        put("message", exception.message)
    })
}.toString()

fun jsonRpcResponse(rpcRequest: JsonElement, result: JsonElement): String = buildJsonObject {
    put("jsonrpc", JsonPrimitive("2.0"))
    put("id", rpcRequest.jsonObject["id"]!!)
    put("result", result)
}.toString()

/**
 * From Gson to Kotlinx
 */
fun com.google.gson.JsonElement.toKotlin(): JsonElement {
    return when {
        isJsonArray -> JsonArray(asJsonArray.map { it.toKotlin() })
        isJsonObject -> buildJsonObject {
            val obj = asJsonObject
            obj.keySet().forEach { key -> put(key, obj[key].toKotlin()) }
        }
        isJsonNull -> JsonNull
        isJsonPrimitive -> {
            val primitive = asJsonPrimitive
            when {
                primitive.isString -> JsonPrimitive(asString)
                primitive.isNumber -> JsonPrimitive(asInt)
                primitive.isBoolean -> JsonPrimitive(asBoolean)
                else -> error("Unknown type")
            }
        }
        else -> error("Unknown type")
    }
}


suspend fun RealtimeChannel.attachAsync() =
    suspendCoroutine { cont ->
        val callback = object : CompletionListener {
            override fun onSuccess() {
                cont.resume(Unit)
            }
            override fun onError(errorInfo: ErrorInfo) {
                cont.resumeWithException(AblyException.fromErrorInfo(errorInfo))
            }
        }
        attach(callback)
    }

suspend fun RealtimeChannel.detachAsync() =
    suspendCoroutine { cont ->
        val callback = object : CompletionListener {
            override fun onSuccess() {
                cont.resume(Unit)
            }
            override fun onError(errorInfo: ErrorInfo) {
                cont.resumeWithException(AblyException.fromErrorInfo(errorInfo))
            }
        }
        detach(callback)
    }
