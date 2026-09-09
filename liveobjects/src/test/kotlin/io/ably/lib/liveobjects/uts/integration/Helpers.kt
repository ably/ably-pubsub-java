package io.ably.lib.liveobjects.uts.integration

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.ably.lib.http.HttpUtils
import io.ably.lib.rest.AblyRest
import io.ably.lib.types.ClientOptions
import io.ably.lib.uts.infra.integration.SandboxApp

/**
 * LiveObjects **integration** test helpers — the ably-java translation of the UTS
 * `objects/helpers/standard_test_pool.md` "## REST Fixture Provisioning" section (`provision_objects_via_rest`),
 * used by integration specs that need pre-existing object state before the realtime client connects
 * (currently `objects/integration/RTPO15`).
 *
 * ### Payload format: V2 (per the OpenAPI), NOT the UTS pseudocode
 *
 * The source of truth for the request shape is the published OpenAPI
 * (`ably-docs/static/open-specs/liveobjects.yaml`, "Update LiveObjects REST API docs for **V2 format**",
 * 2026-01-22), **not** the UTS pseudocode — the UTS spec helper describes a legacy/pre-V2 shape that is out
 * of sync (see [provisionObjectsViaRest]). The V2 contract:
 *
 *  - Endpoint: `POST /channels/{channel}/object` (**singular** `object`).
 *  - Body: a single operation object, **or** a bare JSON array of them — there is **no** `{ "messages": [...] }`
 *    wrapper.
 *  - An operation is identified by its **payload key** (`mapSet` / `mapRemove` / `mapCreate` / `counterInc` /
 *    `counterCreate`) plus a sibling target (`objectId` *or* `path`) — there is **no** `operation: "MAP_SET"`
 *    string and **no** `data` wrapper.
 *  - Values are `{ string }` / `{ number }` / `{ boolean }` / `{ bytes }`(base64) / `{ objectId }`.
 *  - `mapCreate.semantics` is the integer `0` (LWW); its `entries` wrap each value as `{ "data": <value> }`.
 *
 * Compiles against `:java` only (`AblyRest` + `HttpUtils`), like the unit `Helpers.kt`.
 */

// ---------------------------------------------------------------------------
// Value builders — a V2 `PrimitiveValue` (the `value` of a mapSet / a mapCreate entry's `data`)
// ---------------------------------------------------------------------------

fun valueString(value: String, encoding: String? = null): JsonObject =
    JsonObject().apply { addProperty("string", value); encoding?.let { addProperty("encoding", it) } }
fun valueNumber(value: Number): JsonObject = JsonObject().apply { addProperty("number", value) }
fun valueBoolean(value: Boolean): JsonObject = JsonObject().apply { addProperty("boolean", value) }
fun valueBytes(base64: String, encoding: String? = null): JsonObject =
    JsonObject().apply { addProperty("bytes", base64); encoding?.let { addProperty("encoding", it) } }
fun valueObjectId(objectId: String): JsonObject = JsonObject().apply { addProperty("objectId", objectId) }

// ---------------------------------------------------------------------------
// Operation builders — a single V2 operation; target is `objectId` OR `path`
// ---------------------------------------------------------------------------

private fun operation(objectId: String?, path: String?, id: String?, build: JsonObject.() -> Unit): JsonObject =
    JsonObject().apply {
        id?.let { addProperty("id", it) } // OperationBase.id — idempotency key
        objectId?.let { addProperty("objectId", it) }
        path?.let { addProperty("path", it) }
        build()
    }

/** `{ mapSet: { key, value }, objectId|path }` */
fun mapSetOp(key: String, value: JsonObject, objectId: String? = null, path: String? = null, id: String? = null): JsonObject =
    operation(objectId, path, id) {
        add("mapSet", JsonObject().apply { addProperty("key", key); add("value", value) })
    }

/** `{ mapRemove: { key }, objectId|path }` */
fun mapRemoveOp(key: String, objectId: String? = null, path: String? = null, id: String? = null): JsonObject =
    operation(objectId, path, id) {
        add("mapRemove", JsonObject().apply { addProperty("key", key) })
    }

/** `{ mapCreate: { semantics, entries: { k: { data: value } } }, objectId?|path? }` */
fun mapCreateOp(
    entries: Map<String, JsonObject> = emptyMap(),
    semantics: Int = 0, // 0 == LWW
    objectId: String? = null,
    path: String? = null,
    id: String? = null,
): JsonObject = operation(objectId, path, id) {
    add(
        "mapCreate",
        JsonObject().apply {
            addProperty("semantics", semantics)
            add(
                "entries",
                JsonObject().apply {
                    entries.forEach { (k, v) -> add(k, JsonObject().apply { add("data", v) }) }
                },
            )
        },
    )
}

/** `{ counterCreate: { count }, objectId?|path? }` */
fun counterCreateOp(count: Number, objectId: String? = null, path: String? = null, id: String? = null): JsonObject =
    operation(objectId, path, id) {
        add("counterCreate", JsonObject().apply { addProperty("count", count) })
    }

/** `{ counterInc: { number }, objectId|path }` (negative `number` = decrement) */
fun counterIncOp(number: Number, objectId: String? = null, path: String? = null, id: String? = null): JsonObject =
    operation(objectId, path, id) {
        add("counterInc", JsonObject().apply { addProperty("number", number) })
    }

// ---------------------------------------------------------------------------
// provision_objects_via_rest
// ---------------------------------------------------------------------------

/**
 * `provision_objects_via_rest(api_key, channel_name, operations)` — seeds object state on a channel via the
 * REST API before any realtime client connects.
 *
 * Translated to the **V2** OpenAPI contract (see file header), which diverges from the UTS pseudocode in
 * `standard_test_pool.md` (legacy `POST …/objects` + `{ messages: [ { operation: { action, … } } ] }`). A
 * single operation is posted as one object; multiple operations are posted as a JSON array (`BatchOperation`).
 *
 * @return the `objectIds` reported by the API for the created/updated objects.
 */
fun provisionObjectsViaRest(apiKey: String, channelName: String, operations: List<JsonObject>): List<String> {
    require(operations.isNotEmpty()) { "operations must not be empty" }

    val rest = AblyRest(
        ClientOptions().apply {
            key = apiKey
            // Target the same nonprod sandbox host that SandboxApp and the realtime clients use
            // (sandbox.realtime.ably-nonprod.net) — NOT environment="sandbox", which resolves to the
            // legacy prod-sandbox host sandbox-rest.ably.io (Hosts.java also forbids setting both
            // environment and restHost). Matches standard_test_pool.md (ably/specification#497).
            restHost = SandboxApp.sandboxHost
            useBinaryProtocol = false
        },
    )

    val path = "/channels/$channelName/object" // V2: singular `object`
    val body: JsonElement =
        if (operations.size == 1) operations[0] else JsonArray().apply { operations.forEach { add(it) } }

    val response = rest.request(
        "POST",
        path,
        null,
        HttpUtils.requestBodyFromGson(body, rest.options.useBinaryProtocol),
        null,
    )
    check(response.success) {
        "REST objects provisioning failed: HTTP ${response.statusCode} ${response.errorMessage}"
    }

    return response.items().flatMap { item ->
        item.asJsonObject.get("objectIds")?.asJsonArray?.map { it.asString } ?: emptyList()
    }
}
