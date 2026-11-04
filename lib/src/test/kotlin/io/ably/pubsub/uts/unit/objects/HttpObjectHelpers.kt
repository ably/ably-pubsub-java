package io.ably.pubsub.uts.unit.objects

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.ably.pubsub.http.Channel
import io.ably.pubsub.http.PubSubHttpClient
import io.ably.pubsub.uts.infra.unit.ClientOptionsBuilder
import io.ably.pubsub.uts.infra.unit.MockHttpClient
import io.ably.pubsub.uts.infra.unit.PendingRequest
import io.ably.pubsub.uts.infra.unit.TestHttpClient
import org.msgpack.core.MessagePack
import org.msgpack.core.MessagePacker
import org.msgpack.value.Value
import org.msgpack.value.ValueType
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Collections
import java.util.UUID

/**
 * Helpers for the `HttpObject` (spec `RestObject`) unit specs — `rest_object_get.md`,
 * `rest_object_publish.md` and `rest_object_generate_id.md` — which run against the mocked HTTP
 * client rather than the realtime harness in [Helpers.kt].
 *
 * Bodies and expectations use plain Kotlin values: maps, lists, strings, booleans, `ByteArray`,
 * and numbers as `Long` (integral) or `Double` (fractional), so spec JSON literals translate to
 * `mapOf(...)` with `Long` integers. `getCompact` returns a `JsonElement`; [fromJson] converts it
 * to this representation.
 */

/** The spec's `random_id()`. */
fun randomId(): String = UUID.randomUUID().toString().take(8)

/** The spec's `setup_rest_object(...)` result. */
class RestObjectSetup(
    val client: PubSubHttpClient,
    val channel: Channel,
    val capturedRequests: MutableList<PendingRequest>,
)

/**
 * The spec's `setup_rest_object(channel_name, ...)`: an HTTP client on a mock that records every
 * request and answers it with [respond]. `HttpObject` is built into the HTTP client, so the
 * spec's `plugins: { Objects: objects_plugin }` needs no wiring.
 */
fun setupRestObject(
    channelName: String,
    useBinaryProtocol: Boolean = false,
    clientOptions: ClientOptionsBuilder.() -> Unit = {},
    respond: (PendingRequest) -> Unit,
): RestObjectSetup {
    val captured = Collections.synchronizedList(mutableListOf<PendingRequest>())
    val mock = MockHttpClient {
        onConnectionAttempt = { it.respondWithSuccess() }
        onRequest = { req ->
            captured.add(req)
            respond(req)
        }
    }
    val client = TestHttpClient {
        this.useBinaryProtocol = useBinaryProtocol
        install(mock)
        clientOptions()
    }
    return RestObjectSetup(client, client.channels.get(channelName), captured)
}

/** Responds with [body] encoded as JSON or MessagePack, with the matching `Content-Type`. */
fun PendingRequest.respondWithBody(status: Int, body: Any?, useBinaryProtocol: Boolean) {
    if (useBinaryProtocol) {
        respondWith(status, msgpackEncode(body), mapOf("Content-Type" to "application/x-msgpack"))
    } else {
        respondWith(status, jsonEncode(body), mapOf("Content-Type" to "application/json"))
    }
}

/** `request.url.path` — the raw (still percent-encoded) path. */
val PendingRequest.path: String get() = url.path

/** `request.url.query_params` — URL-decoded query parameters. */
val PendingRequest.queryParams: Map<String, String>
    get() = url.query.orEmpty().split("&").filter { it.isNotEmpty() }.associate { pair ->
        val idx = pair.indexOf('=')
        val key = if (idx < 0) pair else pair.substring(0, idx)
        val value = if (idx < 0) "" else pair.substring(idx + 1)
        URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
    }

/** Case-insensitive single header lookup. */
fun PendingRequest.header(name: String): String? =
    headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

/**
 * The spec's `encode_uri_component` — JavaScript's `encodeURIComponent`, written independently of
 * the SDK's own encoder so the tests check it rather than restate it.
 */
fun encodeUriComponent(value: String): String =
    URLEncoder.encode(value, "UTF-8")
        .replace("+", "%20")
        .replace("%21", "!")
        .replace("%27", "'")
        .replace("%28", "(")
        .replace("%29", ")")
        .replace("%7E", "~")

//region JSON / MessagePack <-> plain values

/** The spec's `parse_json` over a request body or JSON string. */
fun parseJson(bytes: ByteArray): Any? = parseJson(String(bytes, Charsets.UTF_8))

fun parseJson(text: String): Any? = fromJson(JsonParser.parseString(text))

/** A JSON value as plain Kotlin values, e.g. for comparison with `HttpObjectData.json`. */
fun fromJson(element: JsonElement): Any? = when {
    element.isJsonNull -> null
    element.isJsonObject -> element.asJsonObject.entrySet().associateTo(LinkedHashMap()) { it.key to fromJson(it.value) }
    element.isJsonArray -> element.asJsonArray.map { fromJson(it) }
    element.asJsonPrimitive.isBoolean -> element.asBoolean
    element.asJsonPrimitive.isNumber -> element.asString.let { it.toLongOrNull() ?: it.toDouble() }
    else -> element.asString
}

fun jsonEncode(value: Any?): String = io.ably.pubsub.util.Serialisation.gson.toJson(value)

/** The spec's `msgpack_encode`. */
fun msgpackEncode(value: Any?): ByteArray =
    MessagePack.newDefaultBufferPacker().use { packer ->
        pack(packer, value)
        packer.toByteArray()
    }

/** The spec's `msgpack_decode`. */
fun msgpackDecode(bytes: ByteArray): Any? =
    MessagePack.newDefaultUnpacker(bytes).use { fromMsgpack(it.unpackValue()) }

private fun pack(packer: MessagePacker, value: Any?) {
    when (value) {
        null -> packer.packNil()
        is String -> packer.packString(value)
        is Boolean -> packer.packBoolean(value)
        is Double, is Float -> packer.packDouble((value as Number).toDouble())
        is java.math.BigInteger -> packer.packBigInteger(value)
        is Number -> packer.packLong(value.toLong())
        is ByteArray -> {
            packer.packBinaryHeader(value.size)
            packer.writePayload(value)
        }
        is Map<*, *> -> {
            packer.packMapHeader(value.size)
            value.forEach { (k, v) -> packer.packString(k.toString()); pack(packer, v) }
        }
        is List<*> -> {
            packer.packArrayHeader(value.size)
            value.forEach { pack(packer, it) }
        }
        else -> error("Unsupported value for msgpack: $value")
    }
}

private fun fromMsgpack(value: Value): Any? = when (value.valueType) {
    ValueType.NIL -> null
    ValueType.BOOLEAN -> value.asBooleanValue().boolean
    ValueType.INTEGER -> value.asIntegerValue().toLong()
    ValueType.FLOAT -> value.asFloatValue().toDouble()
    ValueType.STRING -> value.asStringValue().asString()
    ValueType.BINARY -> value.asBinaryValue().asByteArray()
    ValueType.ARRAY -> value.asArrayValue().map { fromMsgpack(it) }
    ValueType.MAP -> value.asMapValue().map().entries.associateTo(LinkedHashMap()) { (k, v) ->
        k.asStringValue().asString() to fromMsgpack(v)
    }
    else -> error("Unsupported msgpack value: $value")
}

//endregion
