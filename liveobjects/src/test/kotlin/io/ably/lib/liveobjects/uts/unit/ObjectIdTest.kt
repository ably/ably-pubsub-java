package io.ably.lib.liveobjects.uts.unit

import io.ably.lib.liveobjects.ObjectId
import io.ably.lib.liveobjects.value.ObjectType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Derived from UTS spec `objects/unit/object_id.md` — ObjectId generation (RTO14).
 *
 * Pure-function tests: no mocks, no `DefaultRealtimeObject` setup required
 * (see `.claude/skills/uts-to-kotlin/references/objects-mapping.md` §17.7).
 * The spec's `generateObjectId(type, initialValue, nonce, timestamp)` maps to
 * `ObjectId.fromInitialValue(ObjectType, initialValue, nonce, timestamp).toString()`.
 */
class ObjectIdTest {

    /** The spec's `generateObjectId` helper mapped onto the internal `ObjectId` factory (§17.7). */
    private fun generateObjectId(type: ObjectType, initialValue: String, nonce: String, timestamp: Long): String =
        ObjectId.fromInitialValue(type, initialValue, nonce, timestamp).toString()

    /**
     * @UTS objects/unit/RTO14/objectid-format-counter-0
     */
    @Test
    fun `RTO14 - ObjectId format for counter type`() {
        val objectId = generateObjectId(
            type = ObjectType.Counter,
            initialValue = """{"counter":{"count":42}}""",
            nonce = "test-nonce-12345678",
            timestamp = 1_700_000_000_000L,
        )

        assertTrue(objectId.startsWith("counter:"), "objectId must start with \"counter:\", was: $objectId")
        assertTrue(objectId.contains("@1700000000000"), "objectId must contain \"@1700000000000\", was: $objectId")
        val parts = objectId.split(":")
        val typePart = parts[0]
        val rest = parts[1]
        val hashAndTs = rest.split("@")
        val hashPart = hashAndTs[0]
        val tsPart = hashAndTs[1]
        assertEquals("counter", typePart)
        assertEquals("1700000000000", tsPart)
        // RTO14b2 - hash is a valid base64url string (RFC 4648 s.5 alphabet)
        assertTrue(hashPart.matches(Regex("^[A-Za-z0-9_-]+$")), "hash must be valid base64url, was: $hashPart")
        assertFalse(hashPart.contains("+"))
        assertFalse(hashPart.contains("/"))
        assertFalse(hashPart.contains("="))
    }

    /**
     * @UTS objects/unit/RTO14/objectid-format-map-0
     */
    @Test
    fun `RTO14 - ObjectId format for map type`() {
        val objectId = generateObjectId(
            type = ObjectType.Map,
            initialValue = """{"map":{"semantics":"LWW","entries":{}}}""",
            nonce = "test-nonce-12345678",
            timestamp = 1_700_000_000_000L,
        )

        assertTrue(objectId.startsWith("map:"), "objectId must start with \"map:\", was: $objectId")
        assertTrue(objectId.contains("@1700000000000"), "objectId must contain \"@1700000000000\", was: $objectId")
    }

    /**
     * @UTS objects/unit/RTO14/deterministic-0
     */
    @Test
    fun `RTO14 - deterministic output for same inputs`() {
        val id1 = generateObjectId(
            type = ObjectType.Counter,
            initialValue = """{"counter":{"count":0}}""",
            nonce = "same-nonce-1234567",
            timestamp = 1_700_000_000_000L,
        )
        val id2 = generateObjectId(
            type = ObjectType.Counter,
            initialValue = """{"counter":{"count":0}}""",
            nonce = "same-nonce-1234567",
            timestamp = 1_700_000_000_000L,
        )

        assertEquals(id1, id2)
    }

    /**
     * @UTS objects/unit/RTO14/different-nonce-0
     */
    @Test
    fun `RTO14 - different nonce produces different objectId`() {
        val id1 = generateObjectId(
            type = ObjectType.Counter,
            initialValue = """{"counter":{"count":0}}""",
            nonce = "nonce-aaaaaaaaaaaaa",
            timestamp = 1_700_000_000_000L,
        )
        val id2 = generateObjectId(
            type = ObjectType.Counter,
            initialValue = """{"counter":{"count":0}}""",
            nonce = "nonce-bbbbbbbbbbbbb",
            timestamp = 1_700_000_000_000L,
        )

        assertNotEquals(id1, id2)
    }

    /**
     * @UTS objects/unit/RTO14b/base64url-encoding-0
     */
    @Test
    fun `RTO14b - SHA-256 hash is base64url encoded not standard base64`() {
        val objectId = generateObjectId(
            type = ObjectType.Counter,
            initialValue = """{"counter":{"count":0}}""",
            nonce = "test-nonce-12345678",
            timestamp = 1_700_000_000_000L,
        )
        val hashPart = objectId.split(":")[1].split("@")[0]

        assertFalse(hashPart.contains("+"), "base64url hash must not contain '+', was: $hashPart")
        assertFalse(hashPart.contains("/"), "base64url hash must not contain '/', was: $hashPart")
        assertFalse(hashPart.endsWith("="), "base64url hash must not end with '=', was: $hashPart")
    }
}
