package io.ably.lib.liveobjects.uts.unit

import com.google.gson.JsonParser
import io.ably.lib.liveobjects.ValueType
import io.ably.lib.liveobjects.instance.Instance
import io.ably.lib.uts.infra.pollUntil
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/**
 * Derived from UTS spec `objects/unit/path_object.md` — `PathObject` read operations
 * (`RTPO1`–`RTPO14`): path string representation and dot-escaping, navigation (`get`/`at`),
 * value/instance/entries/keys/values/size reads, compaction, and path-resolution failure
 * behaviour.
 *
 * Public-tier spec: uses only the public API plus the module-local `Helpers.kt`. Typed-SDK
 * notes (`objects-mapping.md` §4): the dynamic `value()` splits into per-type `as*().value()`
 * reads (never-throwing casts, RTTS5d); `compact()` is not implemented — `compactJson()` is
 * the supported equivalent (RTTS3f) — see the `// DEVIATION` comments and `deviations.md`.
 */
class PathObjectTest {

    /**
     * @UTS objects/unit/RTPO4/path-string-representation-0
     */
    @Test
    fun `RTPO4 - path returns dot-delimited string`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        assertEquals("", root.path())
        assertEquals("profile", root.get("profile").path())
        assertEquals("profile.email", root.get("profile").asLiveMap().get("email").path())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO4b/path-escapes-dots-0
     */
    @Test
    fun `RTPO4b - path escapes dots in segments`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val po = root.get("a.b").asLiveMap().get("c")

        // The segment "a.b" contains a literal dot, escaped as `a\.b` in the path string.
        assertEquals("a\\.b.c", po.path())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO5/get-appends-key-0
     */
    @Test
    fun `RTPO5 - get returns new PathObject with appended key`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val child = root.get("profile")
        val grandchild = child.asLiveMap().get("email")

        assertEquals("profile", child.path())
        assertEquals("profile.email", grandchild.path())
        assertNotSame<Any>(root, child)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO5b/get-non-string-throws-0
     */
    @Test
    fun `RTPO5b - get throws on non-string key`() {
        // DEVIATION (see deviations.md): `get` takes a `@NotNull String` key, so the spec's
        // `root.get(123)` is rejected at compile time — the invalid-input contract (ErrorInfo
        // 40003) for non-String keys is enforced by the type system and cannot be exercised at
        // runtime.
    }

    /**
     * @UTS objects/unit/RTPO6/at-parses-path-0
     */
    @Test
    fun `RTPO6 - at parses dot-delimited path`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val po = root.at("profile.email")

        assertEquals("profile.email", po.path())
        assertEquals("alice@example.com", po.asString().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO6/at-escaped-dots-0
     */
    @Test
    fun `RTPO6 - at respects escaped dots`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        // `a\.b.c` parses to segments ["a.b", "c"] — the escaped dot is a literal.
        val po = root.at("a\\.b.c")

        assertEquals("a\\.b.c", po.path())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO7/value-counter-0
     */
    @Test
    fun `RTPO7 - value returns counter numeric value`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        assertEquals(100.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO7/value-primitive-0
     */
    @Test
    fun `RTPO7 - value returns primitive value`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        assertEquals("Alice", root.get("name").asString().value())
        assertEquals(30.0, root.get("age").asNumber().value()?.toDouble())
        assertEquals(true, root.get("active").asBoolean().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO7d/value-livemap-null-0
     */
    @Test
    fun `RTPO7d - value returns null for InternalLiveMap`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        // The dynamic value() returns null for a LiveMap; in the typed SDK the equivalent read
        // is the counter-typed value() on the never-throwing cast (RTTS5d/RTTS6g).
        assertNull(root.get("profile").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO7e/value-unresolvable-null-0
     */
    @Test
    fun `RTPO7e - value returns null on resolution failure`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        assertNull(root.get("nonexistent").asLiveMap().get("deep").asString().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO8/instance-live-object-0
     */
    @Test
    fun `RTPO8 - instance returns Instance for LiveObject`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val counterInst = root.get("score").instance()
        assertIs<Instance>(counterInst)
        assertEquals("counter:score@1000", counterInst.asLiveCounter().id)

        val mapInst = root.get("profile").instance()
        assertIs<Instance>(mapInst)
        assertEquals("map:profile@1000", mapInst.asLiveMap().id)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO8f/instance-primitive-wrapped-0
     */
    @Test
    fun `RTPO8f - instance returns Instance for primitive`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val nameInst = root.get("name").instance()
        assertIs<Instance>(nameInst)
        // DEVIATION (typed-SDK partition, see deviations.md): primitive Instance sub-types
        // expose no id member at all (RTINS3b / RTTS7c), so `name_inst.id() == null` is not
        // expressible; assert the wrapped type instead — a STRING instance carries no id by
        // construction.
        assertEquals(ValueType.STRING, nameInst.type)
        assertEquals("Alice", nameInst.asString().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO9/entries-yields-pairs-0
     */
    @Test
    fun `RTPO9 - entries returns array of key PathObject pairs`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val entries = mutableMapOf<String, String>()
        for ((key, pathObj) in root.entries()) {
            entries[key] = pathObj.path()
        }

        assertEquals("name", entries["name"])
        assertEquals("profile", entries["profile"])
        assertEquals(7, entries.size)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO9d/entries-non-map-empty-0
     */
    @Test
    fun `RTPO9d - entries returns empty array for non-map`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val entries = root.get("score").asLiveMap().entries()

        assertEquals(0, entries.count())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO10/keys-returns-array-0
     */
    @Test
    fun `RTPO10 - keys returns array of key strings`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val keys = root.keys().toList()

        // `keys IS Array`: the Iterable materialises to a list.
        assertIs<List<*>>(keys)
        assertEquals(7, keys.size)
        assertContains(keys, "name")
        assertContains(keys, "profile")
        assertContains(keys, "score")

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO10d/keys-non-map-empty-0
     */
    @Test
    fun `RTPO10d - keys returns empty array for non-map`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val keys = root.get("score").asLiveMap().keys().toList()

        assertIs<List<*>>(keys)
        assertEquals(0, keys.size)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO11/values-returns-array-0
     */
    @Test
    fun `RTPO11 - values returns array of PathObjects`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val vals = root.values().toList()

        assertIs<List<*>>(vals)
        assertEquals(7, vals.size)
        // Each element is a PathObject whose path is the key.
        val paths = vals.map { it.path() }.toSet()
        assertContains(paths, "name")
        assertContains(paths, "profile")
        assertContains(paths, "score")

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO11d/values-non-map-empty-0
     */
    @Test
    fun `RTPO11d - values returns empty array for non-map`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val vals = root.get("score").asLiveMap().values().toList()

        assertIs<List<*>>(vals)
        assertEquals(0, vals.size)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO12/size-count-0
     */
    @Test
    fun `RTPO12 - size returns non-tombstoned count`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        assertEquals(7L, root.size())
        assertEquals(3L, root.get("profile").asLiveMap().size())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO12c/size-non-map-null-0
     */
    @Test
    fun `RTPO12c - size returns null for non-map`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        assertNull(root.get("score").asLiveMap().size())
        assertNull(root.get("name").asLiveMap().size())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO13/compact-recursive-0
     */
    @Test
    fun `RTPO13 - compact recursively compacts InternalLiveMap tree`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        // DEVIATION (RTTS3f, see deviations.md): compact() is not implemented in ably-java;
        // compactJson() is the typed-SDK equivalent. Binary values come back base64-encoded
        // (RTPO14b1) instead of raw bytes.
        val result = assertNotNull(root.compactJson()).asJsonObject

        assertEquals("Alice", result.get("name").asString)
        assertEquals(30.0, result.get("age").asDouble)
        assertEquals(true, result.get("active").asBoolean)
        assertEquals(100.0, result.get("score").asDouble)
        assertEquals(JsonParser.parseString("""{"tags": ["a", "b"]}"""), result.get("data"))
        // spec: result["avatar"] IS bytes [1, 2, 3] — compactJson base64-encodes binary values.
        assertEquals("AQID", result.get("avatar").asString)
        val profile = result.getAsJsonObject("profile")
        assertEquals("alice@example.com", profile.get("email").asString)
        assertEquals(5.0, profile.get("nested_counter").asDouble)
        assertEquals("dark", profile.getAsJsonObject("prefs").get("theme").asString)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO13c5/compact-cycle-detection-0
     */
    @Test
    fun `RTPO13c5 - compact handles cycles via shared reference`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")

        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("map:prefs@1000", "back_ref", dataObjectId("map:profile@1000"), "99", "remote")),
            ),
        )
        // Mock delivery is applied asynchronously in ably-java — await the applied op before
        // reading (does not weaken any assertion).
        pollUntil(5.seconds) { root.at("profile.prefs.back_ref").exists() }

        // DEVIATION (RTTS3f, see deviations.md): compact()'s in-memory identity reuse
        // (`result["prefs"]["back_ref"] IS result`) is not expressible over compactJson();
        // the cyclic reference is represented as an { "objectId": ... } marker per RTPO14b2.
        val result = assertNotNull(root.get("profile").compactJson()).asJsonObject

        assertEquals(
            JsonParser.parseString("""{"objectId": "map:profile@1000"}"""),
            result.getAsJsonObject("prefs").get("back_ref"),
        )

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO13c/compact-counter-0
     */
    @Test
    fun `RTPO13c - compact returns number for InternalLiveCounter`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        // DEVIATION (RTTS3f, see deviations.md): compact() -> compactJson(); a counter
        // compacts to its numeric value either way.
        assertEquals(100.0, assertNotNull(root.get("score").compactJson()).asDouble)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO14/compact-json-0
     */
    @Test
    fun `RTPO14 - compactJson encodes binary as base64 and cycles as objectId`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")

        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("map:prefs@1000", "back_ref", dataObjectId("map:profile@1000"), "99", "remote")),
            ),
        )
        // Mock delivery is applied asynchronously in ably-java — await the applied op before
        // reading (does not weaken any assertion).
        pollUntil(5.seconds) { root.at("profile.prefs.back_ref").exists() }

        val result = assertNotNull(root.get("profile").compactJson()).asJsonObject

        assertEquals(
            JsonParser.parseString("""{"objectId": "map:profile@1000"}"""),
            result.getAsJsonObject("prefs").get("back_ref"),
        )

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO3/path-resolution-walk-0
     */
    @Test
    fun `RTPO3 - path resolution walks through InternalLiveMaps`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        // The empty path resolves to the root LiveMap, whose dynamic value() is null; typed
        // equivalent per RTTS6g — the counter-typed read on the never-throwing cast.
        assertNull(root.asLiveCounter().value())
        assertEquals(
            "dark",
            root.get("profile").asLiveMap().get("prefs").asLiveMap().get("theme").asString().value(),
        )

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO3a1/intermediate-not-map-0
     */
    @Test
    fun `RTPO3a1 - resolution fails if intermediate is not InternalLiveMap`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        assertNull(root.get("score").asLiveMap().get("something").asString().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO3c1/read-null-on-failure-0
     */
    @Test
    fun `RTPO3c1 - read operation returns null on resolution failure`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        assertNull(root.get("nonexistent").asString().value())
        assertNull(root.get("nonexistent").instance())
        assertNull(root.get("nonexistent").asLiveMap().size())
        // spec: compact() == null — compactJson() is the typed-SDK equivalent (RTTS3f).
        assertNull(root.get("nonexistent").compactJson())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO6b/at-non-string-throws-0
     */
    @Test
    fun `RTPO6b - at throws for non-string input`() {
        // DEVIATION (see deviations.md): `at` takes a `@NotNull String` path, so the spec's
        // `root.at(123)` is rejected at compile time — the invalid-input contract (ErrorInfo
        // 40003) for non-String paths is enforced by the type system and cannot be exercised at
        // runtime.
    }

    /**
     * @UTS objects/unit/RTPO7/value-bytes-0
     */
    @Test
    fun `RTPO7 - value returns bytes for binary entry`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        assertContentEquals(byteArrayOf(1, 2, 3), root.get("avatar").asBinary().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO14/compact-json-bytes-0
     */
    @Test
    fun `RTPO14 - compactJson encodes bytes as base64 string`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val result = assertNotNull(root.compactJson()).asJsonObject

        assertEquals("AQID", result.get("avatar").asString)

        client.close()
    }
}
