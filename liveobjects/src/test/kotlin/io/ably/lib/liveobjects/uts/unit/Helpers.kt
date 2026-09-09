package io.ably.lib.liveobjects.uts.unit

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.ably.lib.liveobjects.message.ObjectMessage
import io.ably.lib.liveobjects.message.WireCounterCreate
import io.ably.lib.liveobjects.message.WireCounterInc
import io.ably.lib.liveobjects.message.WireMapCreate
import io.ably.lib.liveobjects.message.WireMapRemove
import io.ably.lib.liveobjects.message.WireMapSet
import io.ably.lib.liveobjects.message.WireMapClear
import io.ably.lib.liveobjects.message.WireObjectData
import io.ably.lib.liveobjects.message.WireObjectDelete
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperation
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.liveobjects.message.WireObjectState
import io.ably.lib.liveobjects.message.WireObjectsCounter
import io.ably.lib.liveobjects.message.WireObjectsMap
import io.ably.lib.liveobjects.message.WireObjectsMapEntry
import io.ably.lib.liveobjects.message.WireObjectsMapSemantics
import io.ably.lib.liveobjects.message.toPublicMessage
import io.ably.lib.liveobjects.path.types.LiveMapPathObject
import io.ably.lib.realtime.AblyRealtime
import io.ably.lib.realtime.Channel
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.types.PublishResult
import io.ably.lib.uts.infra.unit.ConnectionDetails
import io.ably.lib.uts.infra.unit.MockEvent
import io.ably.lib.uts.infra.unit.MockHttpClient
import io.ably.lib.uts.infra.unit.MockWebSocket
import io.ably.lib.uts.infra.unit.TestRealtimeClient
import kotlinx.coroutines.future.await

/**
 * LiveObjects unit-test helpers — the ably-java translation of the UTS
 * `objects/helpers/standard_test_pool.md` (standard test pool, protocol-message /
 * object-message builders, and the synced-channel setup) used by every objects
 * unit spec (see `.claude/skills/uts-to-kotlin/references/objects-mapping.md` §13/§17).
 *
 * This file lives in `:liveobjects`'s own test source set, so the builders construct the
 * **typed internal wire DTOs** (`Wire*`) directly — no wire JSON, no reflection. The mock
 * transport serializes [ProtocolMessage.state] through the SDK's own `ObjectJsonSerializer`,
 * so the typed messages are the single source of truth for the wire form.
 *
 * The transport bootstrap uses the shared `:uts` fixtures (`io.ably.lib.uts.infra.unit.*`).
 */

// ---------------------------------------------------------------------------
// Canonical constants (standard_test_pool.md "Canonical Constants")
// ---------------------------------------------------------------------------

/** The harness `ConnectionDetails` siteCode delivered on CONNECT. */
const val SITE_CODE: String = "test-site"

/**
 * The fixed apply-on-ACK serial scheme: `ack_serial(msgSerial, i) == "t:${msgSerial + 1}:$i"`, so the
 * first publish's first op is `t:1:0`. The value must sort AFTER the standard pool's `t:0` entry
 * timeserials under string LWW comparison (RTLM9) — otherwise locally applied MAP_SETs on existing pool
 * entries would be rejected as stale. Replay tests that reuse the apply-on-ACK serial MUST reference this.
 */
fun ackSerial(msgSerial: Long?, i: Int): String = "t:${(msgSerial ?: 0) + 1}:$i"

/**
 * Baseline timeserial every standard-pool entry/object is seeded with; every synthetic serial is chosen
 * relative to this under lexicographic string LWW comparison (RTLM9e).
 */
const val POOL_SERIAL: String = "t:0"

/**
 * A REMOTE inbound MAP_SET / MAP_REMOVE serial on an EXISTING pool entry: it sorts after [POOL_SERIAL], so it
 * wins the per-entry LWW comparison (RTLM9e). A bare number like `"99"` sorts BEFORE `"t:0"` and would be
 * rejected as stale. 0-based → `remoteSerial(0) == "t:1"`, `remoteSerial(1) == "t:2"`. (Counter increments and
 * other object-level ops from a fresh siteCode compare per-site, not per-entry, so they apply regardless of
 * serial value and need NOT use this.)
 */
fun remoteSerial(i: Int): String = "t:${i + 1}"

/**
 * A serial that is NOT an [ackSerial] (so it escapes the RTO9a3 apply-on-ACK echo dedup) yet sorts BELOW the
 * first ackSerial (`ackSerial(0, 0) == "t:1:0"`), while still after [POOL_SERIAL]. Used by RTO20f to prove a
 * LOCAL apply-on-ACK left siteTimeserials untouched (RTLC7c): had it wrongly recorded
 * `siteTimeserials[SITE_CODE] = "t:1:0"`, this lower serial would be rejected by the per-site newness check.
 * 0-based → `belowAckSerial(9) == "t:0:9"`.
 */
fun belowAckSerial(i: Int): String = "t:0:$i"

// ---------------------------------------------------------------------------
// ObjectData (leaf value) builders — the `data` of a map entry / mapSet value
// ---------------------------------------------------------------------------

internal fun dataString(value: String): WireObjectData = WireObjectData(string = value)
internal fun dataNumber(value: Number): WireObjectData = WireObjectData(number = value.toDouble())
internal fun dataBoolean(value: Boolean): WireObjectData = WireObjectData(boolean = value)
internal fun dataObjectId(objectId: String): WireObjectData = WireObjectData(objectId = objectId)
internal fun dataBytes(base64: String): WireObjectData = WireObjectData(bytes = base64)
// The typed DTO carries the JsonElement; the SDK's WireObjectDataJsonSerializer stringifies it on the
// wire (OD4c5) and parses it back — no manual encoding needed here.
internal fun dataJson(element: JsonElement): WireObjectData = WireObjectData(json = element)

// ---------------------------------------------------------------------------
// map / counter state + createOp fragments
// ---------------------------------------------------------------------------

internal fun mapEntry(
    data: WireObjectData,
    timeserial: String = POOL_SERIAL,
    tombstone: Boolean? = null,
    serialTimestamp: Long? = null,
): WireObjectsMapEntry =
    WireObjectsMapEntry(tombstone = tombstone, timeserial = timeserial, serialTimestamp = serialTimestamp, data = data)

/** A tombstoned map entry (`data` absent on the wire), e.g. RTLM6c1 / RTLM23a2 fixtures. */
internal fun tombstonedMapEntry(
    timeserial: String = POOL_SERIAL,
    serialTimestamp: Long? = null,
): WireObjectsMapEntry =
    WireObjectsMapEntry(tombstone = true, timeserial = timeserial, serialTimestamp = serialTimestamp, data = null)

internal fun mapState(
    entries: Map<String, WireObjectsMapEntry>,
    semantics: WireObjectsMapSemantics = WireObjectsMapSemantics.LWW,
    clearTimeserial: String? = null,
): WireObjectsMap = WireObjectsMap(semantics = semantics, entries = entries, clearTimeserial = clearTimeserial)

internal fun counterState(count: Number): WireObjectsCounter = WireObjectsCounter(count = count.toDouble())

/**
 * A `createOp` fragment for [buildObjectState]. The operation's `objectId` is stamped by
 * [buildObjectState] (it must equal the enclosing object's id — `validate`/`validateObjectId`),
 * so it is intentionally blank here.
 */
internal fun mapCreateOp(
    semantics: WireObjectsMapSemantics = WireObjectsMapSemantics.LWW,
    entries: Map<String, WireObjectsMapEntry> = emptyMap(),
): WireObjectOperation =
    WireObjectOperation(action = WireObjectOperationAction.MapCreate, objectId = "", mapCreate = WireMapCreate(semantics, entries))

internal fun counterCreateOp(count: Number): WireObjectOperation =
    WireObjectOperation(
        action = WireObjectOperationAction.CounterCreate,
        objectId = "",
        counterCreate = WireCounterCreate(count = count.toDouble()),
    )

// ---------------------------------------------------------------------------
// ObjectMessage builders — STATE (for OBJECT_SYNC) and OPERATIONS (for OBJECT)
// ---------------------------------------------------------------------------

/** `build_object_state` — an ObjectMessage wrapping an ObjectState in its (wire) `object` field. */
internal fun buildObjectState(
    objectId: String,
    siteTimeserials: Map<String, String>,
    map: WireObjectsMap? = null,
    counter: WireObjectsCounter? = null,
    tombstone: Boolean = false,
    createOp: WireObjectOperation? = null,
): WireObjectMessage = WireObjectMessage(
    objectState = WireObjectState(
        objectId = objectId,
        siteTimeserials = siteTimeserials,
        tombstone = tombstone,
        // the createOp's objectId must equal the object's id (LiveMapManager.validate / validateObjectId)
        createOp = createOp?.copy(objectId = objectId),
        map = map,
        counter = counter,
    ),
)

/** `build_object_message_with_state` — wraps an already-built ObjectState in an ObjectMessage. */
internal fun buildObjectMessageWithState(objectState: WireObjectState): WireObjectMessage =
    WireObjectMessage(objectState = objectState)

private fun operationMessage(
    serial: String?,
    siteCode: String?,
    serialTimestamp: Long? = null,
    operation: WireObjectOperation,
): WireObjectMessage =
    WireObjectMessage(serial = serial, siteCode = siteCode, serialTimestamp = serialTimestamp, operation = operation)

internal fun buildCounterInc(objectId: String, number: Number, serial: String? = null, siteCode: String? = null): WireObjectMessage =
    operationMessage(serial, siteCode, operation = WireObjectOperation(
        action = WireObjectOperationAction.CounterInc, objectId = objectId, counterInc = WireCounterInc(number = number.toDouble()),
    ))

internal fun buildMapSet(objectId: String, key: String, value: WireObjectData, serial: String? = null, siteCode: String? = null): WireObjectMessage =
    operationMessage(serial, siteCode, operation = WireObjectOperation(
        action = WireObjectOperationAction.MapSet, objectId = objectId, mapSet = WireMapSet(key = key, value = value),
    ))

internal fun buildMapRemove(objectId: String, key: String, serial: String? = null, siteCode: String? = null, serialTimestamp: Long? = null): WireObjectMessage =
    operationMessage(serial, siteCode, serialTimestamp, operation = WireObjectOperation(
        action = WireObjectOperationAction.MapRemove, objectId = objectId, mapRemove = WireMapRemove(key = key),
    ))

internal fun buildMapClear(objectId: String, serial: String? = null, siteCode: String? = null): WireObjectMessage =
    operationMessage(serial, siteCode, operation = WireObjectOperation(
        action = WireObjectOperationAction.MapClear, objectId = objectId, mapClear = WireMapClear,
    ))

internal fun buildObjectDelete(objectId: String, serial: String? = null, siteCode: String? = null, serialTimestamp: Long? = null): WireObjectMessage =
    operationMessage(serial, siteCode, serialTimestamp, operation = WireObjectOperation(
        action = WireObjectOperationAction.ObjectDelete, objectId = objectId, objectDelete = WireObjectDelete,
    ))

internal fun buildCounterCreate(objectId: String, counterCreate: WireCounterCreate, serial: String? = null, siteCode: String? = null): WireObjectMessage =
    operationMessage(serial, siteCode, operation = WireObjectOperation(
        action = WireObjectOperationAction.CounterCreate, objectId = objectId, counterCreate = counterCreate,
    ))

internal fun buildMapCreate(objectId: String, mapCreate: WireMapCreate, serial: String? = null, siteCode: String? = null): WireObjectMessage =
    operationMessage(serial, siteCode, operation = WireObjectOperation(
        action = WireObjectOperationAction.MapCreate, objectId = objectId, mapCreate = mapCreate,
    ))

// ---------------------------------------------------------------------------
// ProtocolMessage builders
// ---------------------------------------------------------------------------

internal fun buildObjectSyncMessage(channel: String, channelSerial: String, objectMessages: List<WireObjectMessage>): ProtocolMessage =
    ProtocolMessage(ProtocolMessage.Action.object_sync).apply {
        this.channel = channel
        this.channelSerial = channelSerial
        state = objectMessages.toTypedArray()
    }

internal fun buildObjectMessage(channel: String, objectMessages: List<WireObjectMessage>): ProtocolMessage =
    ProtocolMessage(ProtocolMessage.Action.`object`).apply {
        this.channel = channel
        state = objectMessages.toTypedArray()
    }

fun buildAckMessage(msgSerial: Long?, serials: List<String>): ProtocolMessage =
    ProtocolMessage(ProtocolMessage.Action.ack).apply {
        this.msgSerial = msgSerial
        // `count` is the number of protocol messages acknowledged starting at msgSerial (one OBJECT
        // publish per ACK here). ConnectionManager.PendingMessageQueue.ack acks `subList(0, count)`, so
        // an unset count (0) would acknowledge nothing and the publish future would hang. The single
        // acked message's PublishResult (res[0]) carries the per-object serials.
        count = 1
        res = arrayOf(PublishResult(serials.toTypedArray()))
    }

/**
 * `build_public_object_message` — constructs a public [ObjectMessage] (PAOM3) from a wire object message
 * (as produced by the operation builders above) and a channel name, via the SDK's own mapping.
 */
internal fun buildPublicObjectMessage(objectMessage: WireObjectMessage, channelName: String): ObjectMessage =
    objectMessage.toPublicMessage(channelName)

// `provision_objects_via_rest(...)` is intentionally not here — it's REST fixture provisioning for
// *integration* tests and lives with this module's integration tier
// (io.ably.lib.liveobjects.uts.integration.Helpers).

// ---------------------------------------------------------------------------
// STANDARD_POOL_OBJECTS — the fixed tree shared by all objects unit specs
// ---------------------------------------------------------------------------

private val SITE = mapOf("aaa" to POOL_SERIAL)

internal val STANDARD_POOL_OBJECTS: List<WireObjectMessage> = listOf(
    buildObjectState(
        "root", SITE,
        map = mapState(
            linkedMapOf(
                "name" to mapEntry(dataString("Alice")),
                "age" to mapEntry(dataNumber(30)),
                "active" to mapEntry(dataBoolean(true)),
                "score" to mapEntry(dataObjectId("counter:score@1000")),
                "profile" to mapEntry(dataObjectId("map:profile@1000")),
                "data" to mapEntry(dataJson(JsonParser.parseString("""{"tags":["a","b"]}"""))),
                "avatar" to mapEntry(dataBytes("AQID")),
            ),
        ),
        createOp = mapCreateOp(),
    ),
    // Matches standard_test_pool.md: this counter's object-state carries the *post-create residual*
    // count (0), with the initial value on the createOp. Counter sync is additive (RTLC6c+RTLC6d/RTLC16
    // → data = count + createOp.count; the spec's own RTLC6/replace-data-with-create-op asserts 100+50=150),
    // so 0 + 100 = 100 with createOperationIsMerged==true, as every consumer asserts.
    // (Was UTS spec issue SI-1: the spec previously declared count:100 AND createOp:100, materialising 200;
    // fixed upstream in standard_test_pool.md to count:0 — see uts/SPEC_ISSUES.md.)
    buildObjectState("counter:score@1000", SITE, counter = counterState(0), createOp = counterCreateOp(100)),
    buildObjectState(
        "map:profile@1000", SITE,
        map = mapState(
            linkedMapOf(
                "email" to mapEntry(dataString("alice@example.com")),
                "nested_counter" to mapEntry(dataObjectId("counter:nested@1000")),
                "prefs" to mapEntry(dataObjectId("map:prefs@1000")),
            ),
        ),
        createOp = mapCreateOp(),
    ),
    // Matches standard_test_pool.md: residual count 0, the initial 5 carried by the createOp
    // (0 + 5 = 5, merged=true). (Was SI-1: spec previously had count:5 + createOp:5 → 10; fixed
    // upstream to count:0. See uts/SPEC_ISSUES.md.)
    buildObjectState("counter:nested@1000", SITE, counter = counterState(0), createOp = counterCreateOp(5)),
    buildObjectState(
        "map:prefs@1000", SITE,
        map = mapState(linkedMapOf("theme" to mapEntry(dataString("dark")))),
        createOp = mapCreateOp(),
    ),
)

// ---------------------------------------------------------------------------
// synced-channel setup
// ---------------------------------------------------------------------------

/** Result of [setupSyncedChannel] — the spec's `{ client, channel, root, mock_ws }`. */
data class SyncedChannel(
    val client: AblyRealtime,
    val channel: Channel,
    val root: LiveMapPathObject,
    val mockWs: MockWebSocket,
)

/**
 * The spec's `captured_messages` for OBJECT publishes: every [ProtocolMessage] the client sent to
 * the mock with action `object`, in send order (RTLC12/RTLC13/RTLM20/RTLM21/RTO15 wire assertions).
 */
internal fun MockWebSocket.capturedObjectMessages(): List<ProtocolMessage> =
    events.filterIsInstance<MockEvent.MessageFromClient>()
        .map { it.message }
        .filter { it.action == ProtocolMessage.Action.`object` }

/** `setup_synced_channel` — connected client + channel synced with [STANDARD_POOL_OBJECTS]; auto-ACKs OBJECT publishes. */
internal suspend fun setupSyncedChannel(channelName: String): SyncedChannel = setup(channelName, autoAck = true)

/** `setup_synced_channel_no_ack` — as above but does not ACK OBJECT publishes (for tests that control ACK timing). */
internal suspend fun setupSyncedChannelNoAck(channelName: String): SyncedChannel = setup(channelName, autoAck = false)

private suspend fun setup(channelName: String, autoAck: Boolean): SyncedChannel {
    lateinit var mockWs: MockWebSocket
    mockWs = MockWebSocket {
        onConnectionAttempt = { conn ->
            conn.respondWithSuccess(
                ProtocolMessage(ProtocolMessage.Action.connected).apply {
                    connectionId = "conn-1"
                    connectionDetails = ConnectionDetails {
                        connectionKey = "conn-key-1"
                        siteCode = SITE_CODE
                        objectsGCGracePeriod = 86_400_000L
                        // Without an explicit maxMessageSize the field defaults to 0, which makes the
                        // SDK's RTO15d size check reject every OBJECT publish ("size N exceeds 0 bytes").
                        maxMessageSize = 65_536
                    }
                },
            )
        }
        onMessageFromClient = { msg ->
            when (msg.action) {
                ProtocolMessage.Action.attach -> {
                    mockWs.sendToClient(
                        ProtocolMessage(ProtocolMessage.Action.attached).apply {
                            channel = msg.channel
                            channelSerial = "sync1:"
                            setFlag(ProtocolMessage.Flag.has_objects)
                        },
                    )
                    mockWs.sendToClient(buildObjectSyncMessage(msg.channel, "sync1:", STANDARD_POOL_OBJECTS))
                }
                ProtocolMessage.Action.`object` -> if (autoAck) {
                    val serials = (msg.state?.indices ?: IntRange.EMPTY).map { ackSerial(msg.msgSerial, it) }
                    mockWs.sendToClient(buildAckMessage(msg.msgSerial, serials))
                }
                // standard_test_pool.md: an outbound DETACH is answered with DETACHED (both the
                // ack and no-ack variants have this branch). Lets a solicited channel.detach()
                // settle to DETACHED without an RTL13a re-attach.
                ProtocolMessage.Action.detach -> mockWs.sendToClient(
                    ProtocolMessage(ProtocolMessage.Action.detached).apply { channel = msg.channel },
                )
                else -> Unit
            }
        }
    }

    // Hermetic REST: *_CREATE publishes derive object ids from server time (RTO14/RTO16 —
    // ServerTime.getCurrentTime -> adapter.time -> GET /time). Answer it locally so no test
    // depends on a live rest.ably.io round-trip. (ServerTime caches the offset per JVM, so
    // only the first create in a test JVM even reaches this handler.)
    val mockHttp = MockHttpClient {
        // the mock's HTTP call is two-phase: the connection attempt must succeed before the
        // request is delivered to onRequest (MockHttpCall.execute)
        onConnectionAttempt = { conn -> conn.respondWithSuccess() }
        onRequest = { req ->
            if (req.url.path.endsWith("/time")) {
                req.respondWith(200, "[${System.currentTimeMillis()}]", mapOf("Content-Type" to "application/json"))
            } else {
                req.respondWith(404, "unexpected request in unit test: ${req.method} ${req.url}")
            }
        }
    }

    val client = TestRealtimeClient {
        key = "fake:key"
        install(mockWs)
        install(mockHttp)
    }
    val channel = client.channels.get(
        channelName,
        ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish) },
    )
    val root = channel.`object`.get().await()
    return SyncedChannel(client, channel, root, mockWs)
}
