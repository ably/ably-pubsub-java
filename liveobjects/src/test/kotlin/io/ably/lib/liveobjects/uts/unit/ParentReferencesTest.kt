package io.ably.lib.liveobjects.uts.unit

import io.ably.lib.liveobjects.DefaultRealtimeObject
import io.ably.lib.liveobjects.ObjectsState
import io.ably.lib.liveobjects.ROOT_OBJECT_ID
import io.ably.lib.liveobjects.assertWaiter
import io.ably.lib.liveobjects.unit.getMockAblyClientAdapter
import io.ably.lib.liveobjects.value.livecounter.InternalLiveCounter
import io.ably.lib.liveobjects.value.livemap.InternalLiveMap
import io.ably.lib.realtime.ChannelState
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Derived from UTS spec `objects/unit/parent_references.md` — `parentReferences` tracking on
 * LiveObject (RTLO3f), `addParentReference`/`removeParentReference` (RTLO4g/RTLO4h), the
 * `getFullPaths` graph traversal (RTLO4f), and the post-sync rebuild (RTO5c10).
 *
 * Internal-graph spec: asserts on the internal CRDT graph (`InternalLiveCounter`,
 * `InternalLiveMap`, `ObjectsPool`), so it lives in `:liveobjects`'s own test source set —
 * symbol map in `.claude/skills/uts-to-kotlin/references/objects-mapping.md` §17
 * (instantiation §17.1, parent references §17.8, pool/sync §17.6).
 */
class ParentReferencesTest {

    private lateinit var ro: DefaultRealtimeObject

    @BeforeTest
    fun setUp() {
        // §17.1 - internal classes have no public constructors; build them against a
        // DefaultRealtimeObject backed by the mocked adapter. The pool ("root" auto-created
        // per RTO3b) is the spec's `pool = ObjectsPool()`.
        ro = DefaultRealtimeObject("test", getMockAblyClientAdapter())
    }

    @AfterTest
    fun tearDown() {
        // DEVIATION S-4 (see deviations.md): ObjectsPool.init starts a real GC coroutine +
        // adapter subscription - dispose it; unmockkAll clears the mockkStatic global state.
        ro.objectsPool.dispose()
        unmockkAll()
    }

    // -----------------------------------------------------------------------
    // RTLO3f2 - initialization
    // -----------------------------------------------------------------------

    /**
     * @UTS objects/unit/RTLO3f2/init-empty-counter-0
     */
    @Test
    fun `RTLO3f2 - parentReferences initialized to empty map on InternalLiveCounter`() {
        val counter = InternalLiveCounter.zeroValue("counter:abc@1000", ro)

        assertEquals(emptyMap(), counter.parentReferences)
    }

    /**
     * @UTS objects/unit/RTLO3f2/init-empty-map-0
     */
    @Test
    fun `RTLO3f2 - parentReferences initialized to empty map on InternalLiveMap`() {
        val map = InternalLiveMap.zeroValue("map:abc@1000", ro)

        assertEquals(emptyMap(), map.parentReferences)
    }

    // -----------------------------------------------------------------------
    // RTLO4g - addParentReference
    // -----------------------------------------------------------------------

    /**
     * @UTS objects/unit/RTLO4g2/first-reference-new-entry-0
     */
    @Test
    fun `RTLO4g2 - addParentReference creates new entry for first reference`() {
        val child = InternalLiveCounter.zeroValue("counter:child@1000", ro)
        val parent = InternalLiveMap.zeroValue("map:parent@1000", ro)

        child.addParentReference(parent, "score")

        assertContains(child.parentReferences, "map:parent@1000")
        assertEquals<Set<String>?>(setOf("score"), child.parentReferences["map:parent@1000"])
    }

    /**
     * @UTS objects/unit/RTLO4g1/second-key-same-parent-0
     */
    @Test
    fun `RTLO4g1 - addParentReference adds key to existing entry for same parent`() {
        val child = InternalLiveCounter.zeroValue("counter:child@1000", ro)
        val parent = InternalLiveMap.zeroValue("map:parent@1000", ro)
        child.parentReferences["map:parent@1000"] = mutableSetOf("score")

        child.addParentReference(parent, "points")

        assertEquals<Set<String>?>(setOf("score", "points"), child.parentReferences["map:parent@1000"])
    }

    /**
     * @UTS objects/unit/RTLO4g/different-parent-separate-entry-0
     */
    @Test
    fun `RTLO4g - addParentReference with different parent creates separate entry`() {
        val child = InternalLiveCounter.zeroValue("counter:child@1000", ro)
        val parentA = InternalLiveMap.zeroValue("map:a@1000", ro)
        val parentB = InternalLiveMap.zeroValue("map:b@1000", ro)

        child.addParentReference(parentA, "x")
        child.addParentReference(parentB, "y")

        assertEquals<Set<String>?>(setOf("x"), child.parentReferences["map:a@1000"])
        assertEquals<Set<String>?>(setOf("y"), child.parentReferences["map:b@1000"])
    }

    /**
     * @UTS objects/unit/RTLO4g/multiple-parents-multiple-keys-0
     */
    @Test
    fun `RTLO4g - addParentReference with multiple parents and multiple keys`() {
        val child = InternalLiveCounter.zeroValue("counter:child@1000", ro)
        val parentA = InternalLiveMap.zeroValue("map:a@1000", ro)
        val parentB = InternalLiveMap.zeroValue("map:b@1000", ro)

        child.addParentReference(parentA, "x")
        child.addParentReference(parentA, "y")
        child.addParentReference(parentB, "p")
        child.addParentReference(parentB, "q")

        assertEquals<Set<String>?>(setOf("x", "y"), child.parentReferences["map:a@1000"])
        assertEquals<Set<String>?>(setOf("p", "q"), child.parentReferences["map:b@1000"])
    }

    // -----------------------------------------------------------------------
    // RTLO4h - removeParentReference
    // -----------------------------------------------------------------------

    /**
     * @UTS objects/unit/RTLO4h1/nonexistent-parent-noop-0
     */
    @Test
    fun `RTLO4h1 - removeParentReference no-op for non-existent parent`() {
        val child = InternalLiveCounter.zeroValue("counter:child@1000", ro)
        val parent = InternalLiveMap.zeroValue("map:parent@1000", ro)

        child.removeParentReference(parent, "score")

        assertEquals(emptyMap(), child.parentReferences)
    }

    /**
     * @UTS objects/unit/RTLO4h2/remove-key-leaves-others-0
     */
    @Test
    fun `RTLO4h2 - removeParentReference removes key but leaves other keys`() {
        val child = InternalLiveCounter.zeroValue("counter:child@1000", ro)
        val parent = InternalLiveMap.zeroValue("map:parent@1000", ro)
        child.parentReferences["map:parent@1000"] = mutableSetOf("score", "points")

        child.removeParentReference(parent, "score")

        assertEquals<Set<String>?>(setOf("points"), child.parentReferences["map:parent@1000"])
    }

    /**
     * @UTS objects/unit/RTLO4h3/remove-last-key-removes-entry-0
     */
    @Test
    fun `RTLO4h3 - removeParentReference removes entry when set becomes empty`() {
        val child = InternalLiveCounter.zeroValue("counter:child@1000", ro)
        val parent = InternalLiveMap.zeroValue("map:parent@1000", ro)
        child.parentReferences["map:parent@1000"] = mutableSetOf("score")

        child.removeParentReference(parent, "score")

        assertFalse("map:parent@1000" in child.parentReferences)
        assertEquals(emptyMap(), child.parentReferences)
    }

    /**
     * @UTS objects/unit/RTLO4h/remove-nonexistent-key-0
     */
    @Test
    fun `RTLO4h - removeParentReference for non-existent key in existing parent`() {
        val child = InternalLiveCounter.zeroValue("counter:child@1000", ro)
        val parent = InternalLiveMap.zeroValue("map:parent@1000", ro)
        child.parentReferences["map:parent@1000"] = mutableSetOf("score")

        child.removeParentReference(parent, "nonexistent")

        assertEquals<Set<String>?>(setOf("score"), child.parentReferences["map:parent@1000"])
    }

    // -----------------------------------------------------------------------
    // RTLO4f - getFullPaths
    // -----------------------------------------------------------------------

    /**
     * @UTS objects/unit/RTLO4f2/root-returns-empty-path-0
     */
    @Test
    fun `RTLO4f2 - getFullPaths for root returns empty key-path`() {
        val root = ro.objectsPool.get(ROOT_OBJECT_ID)!!

        val paths = root.getFullPaths()
        assertEquals(1, paths.size)
        assertContains(paths, emptyList())
    }

    /**
     * @UTS objects/unit/RTLO4f/direct-child-single-path-0
     */
    @Test
    fun `RTLO4f - getFullPaths for direct child of root`() {
        val counter = InternalLiveCounter.zeroValue("counter:score@1000", ro)
        ro.objectsPool.set("counter:score@1000", counter)

        val root = ro.objectsPool.get(ROOT_OBJECT_ID) as InternalLiveMap
        counter.addParentReference(root, "score")

        val paths = counter.getFullPaths()
        assertEquals(1, paths.size)
        assertContains(paths, listOf("score"))
    }

    /**
     * @UTS objects/unit/RTLO4f/deep-nesting-0
     */
    @Test
    fun `RTLO4f - getFullPaths for deeply nested object`() {
        val root = ro.objectsPool.get(ROOT_OBJECT_ID) as InternalLiveMap

        val profile = InternalLiveMap.zeroValue("map:profile@1000", ro)
        ro.objectsPool.set("map:profile@1000", profile)
        profile.addParentReference(root, "profile")

        val prefs = InternalLiveMap.zeroValue("map:prefs@1000", ro)
        ro.objectsPool.set("map:prefs@1000", prefs)
        prefs.addParentReference(profile, "prefs")

        val themeCounter = InternalLiveCounter.zeroValue("counter:theme@1000", ro)
        ro.objectsPool.set("counter:theme@1000", themeCounter)
        themeCounter.addParentReference(prefs, "theme_counter")

        val paths = themeCounter.getFullPaths()
        assertEquals(1, paths.size)
        assertContains(paths, listOf("profile", "prefs", "theme_counter"))
    }

    /**
     * @UTS objects/unit/RTLO4f/diamond-graph-0
     */
    @Test
    fun `RTLO4f - getFullPaths with multiple parents diamond graph`() {
        val root = ro.objectsPool.get(ROOT_OBJECT_ID) as InternalLiveMap

        val mapA = InternalLiveMap.zeroValue("map:a@1000", ro)
        ro.objectsPool.set("map:a@1000", mapA)
        mapA.addParentReference(root, "a")

        val mapB = InternalLiveMap.zeroValue("map:b@1000", ro)
        ro.objectsPool.set("map:b@1000", mapB)
        mapB.addParentReference(root, "b")

        val leaf = InternalLiveCounter.zeroValue("counter:leaf@1000", ro)
        ro.objectsPool.set("counter:leaf@1000", leaf)
        leaf.addParentReference(mapA, "x")
        leaf.addParentReference(mapB, "y")

        val paths = leaf.getFullPaths()
        assertEquals(2, paths.size)
        assertContains(paths, listOf("a", "x"))
        assertContains(paths, listOf("b", "y"))
    }

    /**
     * @UTS objects/unit/RTLO4f/single-parent-multiple-keys-0
     */
    @Test
    fun `RTLO4f - getFullPaths with single parent referencing at multiple keys`() {
        val root = ro.objectsPool.get(ROOT_OBJECT_ID) as InternalLiveMap

        val child = InternalLiveCounter.zeroValue("counter:child@1000", ro)
        ro.objectsPool.set("counter:child@1000", child)
        child.addParentReference(root, "primary")
        child.addParentReference(root, "alias")

        val paths = child.getFullPaths()
        assertEquals(2, paths.size)
        assertContains(paths, listOf("primary"))
        assertContains(paths, listOf("alias"))
    }

    /**
     * @UTS objects/unit/RTLO4f/orphan-returns-empty-0
     */
    @Test
    fun `RTLO4f - getFullPaths for orphan returns empty list`() {
        val orphan = InternalLiveCounter.zeroValue("counter:orphan@1000", ro)
        ro.objectsPool.set("counter:orphan@1000", orphan)

        val paths = orphan.getFullPaths()
        assertEquals(0, paths.size)
    }

    /**
     * @UTS objects/unit/RTLO4f/cycle-suppression-0
     */
    @Test
    fun `RTLO4f - getFullPaths suppresses cycles`() {
        val root = ro.objectsPool.get(ROOT_OBJECT_ID) as InternalLiveMap

        val mapA = InternalLiveMap.zeroValue("map:a@1000", ro)
        ro.objectsPool.set("map:a@1000", mapA)
        mapA.addParentReference(root, "a")

        val mapB = InternalLiveMap.zeroValue("map:b@1000", ro)
        ro.objectsPool.set("map:b@1000", mapB)
        mapB.addParentReference(mapA, "b")

        // Create a cycle: map:A also has map:B as a parent
        mapA.addParentReference(mapB, "a")

        val pathsB = mapB.getFullPaths()
        assertEquals(1, pathsB.size)
        assertContains(pathsB, listOf("a", "b"))

        val pathsA = mapA.getFullPaths()
        assertEquals(1, pathsA.size)
        assertContains(pathsA, listOf("a"))
    }

    /**
     * @UTS objects/unit/RTLO4f/complex-diamond-deep-0
     */
    @Test
    fun `RTLO4f - getFullPaths with complex diamond and deep nesting`() {
        val root = ro.objectsPool.get(ROOT_OBJECT_ID) as InternalLiveMap

        val mapL = InternalLiveMap.zeroValue("map:l@1000", ro)
        ro.objectsPool.set("map:l@1000", mapL)
        mapL.addParentReference(root, "left")

        val mapR = InternalLiveMap.zeroValue("map:r@1000", ro)
        ro.objectsPool.set("map:r@1000", mapR)
        mapR.addParentReference(root, "right")

        val mapM = InternalLiveMap.zeroValue("map:m@1000", ro)
        ro.objectsPool.set("map:m@1000", mapM)
        mapM.addParentReference(mapL, "mid")

        val target = InternalLiveCounter.zeroValue("counter:t@1000", ro)
        ro.objectsPool.set("counter:t@1000", target)
        target.addParentReference(mapM, "target")
        target.addParentReference(mapR, "target")

        val paths = target.getFullPaths()
        assertEquals(2, paths.size)
        assertContains(paths, listOf("left", "mid", "target"))
        assertContains(paths, listOf("right", "target"))
    }

    // -----------------------------------------------------------------------
    // RTO5c10 - post-sync rebuild
    // -----------------------------------------------------------------------

    /**
     * The spec's `pool.processAttached(ProtocolMessage(action: ATTACHED, flags: HAS_OBJECTS))`.
     * DEVIATION S-2 (see deviations.md): maps to the async `handleStateChange` (launched on the
     * sequential scope), so await the SYNCING transition before delivering sync messages.
     */
    private suspend fun processAttachedWithObjects() {
        ro.handleStateChange(ChannelState.attached, hasObjects = true)
        assertWaiter { ro.state == ObjectsState.Syncing }
    }

    /**
     * @UTS objects/unit/RTO5c10/rebuild-from-sync-0
     */
    @Test
    fun `RTO5c10 - post-sync rebuild populates parentReferences from InternalLiveMap entries`() = runTest {
        processAttachedWithObjects()

        // pool.processObjectSync(build_object_sync_message("test", "sync1:", [...])) - the wire
        // messages + sync serial go straight to handleObjectSyncMessages (§17.6/§17.10)
        ro.objectsManager.handleObjectSyncMessages(
            listOf(
                buildObjectState(
                    "root", mapOf("aaa" to POOL_SERIAL),
                    map = mapState(
                        linkedMapOf(
                            "score" to mapEntry(dataObjectId("counter:score@1000"), timeserial = POOL_SERIAL),
                            "profile" to mapEntry(dataObjectId("map:profile@1000"), timeserial = POOL_SERIAL),
                        ),
                    ),
                    createOp = mapCreateOp(),
                ),
                buildObjectState(
                    "counter:score@1000", mapOf("aaa" to POOL_SERIAL),
                    counter = counterState(0),
                    createOp = counterCreateOp(100),
                ),
                buildObjectState(
                    "map:profile@1000", mapOf("aaa" to POOL_SERIAL),
                    map = mapState(
                        linkedMapOf(
                            "nested" to mapEntry(dataObjectId("counter:nested@1000"), timeserial = POOL_SERIAL),
                        ),
                    ),
                    createOp = mapCreateOp(),
                ),
                buildObjectState(
                    "counter:nested@1000", mapOf("aaa" to POOL_SERIAL),
                    counter = counterState(0),
                    createOp = counterCreateOp(5),
                ),
            ),
            "sync1:",
        )

        assertEquals(ObjectsState.Synced, ro.state)

        // counter:score@1000 is referenced by root at key "score"
        val score = ro.objectsPool.get("counter:score@1000")!!
        assertEquals<Set<String>?>(setOf("score"), score.parentReferences["root"])

        // map:profile@1000 is referenced by root at key "profile"
        val profile = ro.objectsPool.get("map:profile@1000")!!
        assertEquals<Set<String>?>(setOf("profile"), profile.parentReferences["root"])

        // counter:nested@1000 is referenced by map:profile@1000 at key "nested"
        val nested = ro.objectsPool.get("counter:nested@1000")!!
        assertEquals<Set<String>?>(setOf("nested"), nested.parentReferences["map:profile@1000"])

        // root has no parent references
        assertEquals(emptyMap(), ro.objectsPool.get(ROOT_OBJECT_ID)!!.parentReferences)

        // getFullPaths works correctly after rebuild
        assertContains(score.getFullPaths(), listOf("score"))
        assertContains(nested.getFullPaths(), listOf("profile", "nested"))
    }

    /**
     * @UTS objects/unit/RTO5c10a/rebuild-clears-stale-refs-0
     */
    @Test
    fun `RTO5c10a - post-sync rebuild clears stale parentReferences`() = runTest {
        // First sync: root --"score"--> counter:abc@1000
        processAttachedWithObjects()
        ro.objectsManager.handleObjectSyncMessages(
            listOf(
                buildObjectState(
                    "root", mapOf("aaa" to POOL_SERIAL),
                    map = mapState(
                        linkedMapOf(
                            "score" to mapEntry(dataObjectId("counter:abc@1000"), timeserial = POOL_SERIAL),
                        ),
                    ),
                    createOp = mapCreateOp(),
                ),
                buildObjectState(
                    "counter:abc@1000", mapOf("aaa" to POOL_SERIAL),
                    counter = counterState(0),
                    createOp = counterCreateOp(10),
                ),
            ),
            "sync1:",
        )
        assertEquals<Set<String>?>(setOf("score"), ro.objectsPool.get("counter:abc@1000")!!.parentReferences["root"])

        // Second sync: root --"points"--> counter:abc@1000 (key changed from "score" to "points")
        processAttachedWithObjects()
        ro.objectsManager.handleObjectSyncMessages(
            listOf(
                buildObjectState(
                    "root", mapOf("aaa" to remoteSerial(0)),
                    map = mapState(
                        linkedMapOf(
                            "points" to mapEntry(dataObjectId("counter:abc@1000"), timeserial = remoteSerial(0)),
                        ),
                    ),
                    createOp = mapCreateOp(),
                ),
                buildObjectState(
                    "counter:abc@1000", mapOf("aaa" to remoteSerial(0)),
                    counter = counterState(0),
                    createOp = counterCreateOp(20),
                ),
            ),
            "sync2:",
        )

        val counter = ro.objectsPool.get("counter:abc@1000")!!

        // Old "score" reference should be gone, replaced by "points"
        assertEquals<Set<String>?>(setOf("points"), counter.parentReferences["root"])
        assertContains(counter.getFullPaths(), listOf("points"))

        val paths = counter.getFullPaths()
        assertEquals(1, paths.size)
    }

    /**
     * @UTS objects/unit/RTO5c10/unreferenced-empty-refs-0
     */
    @Test
    fun `RTO5c10 - post-sync unreferenced objects have empty parentReferences`() = runTest {
        processAttachedWithObjects()

        ro.objectsManager.handleObjectSyncMessages(
            listOf(
                buildObjectState(
                    "root", mapOf("aaa" to POOL_SERIAL),
                    map = mapState(
                        linkedMapOf(
                            "name" to mapEntry(dataString("Alice"), timeserial = POOL_SERIAL),
                        ),
                    ),
                    createOp = mapCreateOp(),
                ),
                buildObjectState(
                    "counter:orphan@1000", mapOf("aaa" to POOL_SERIAL),
                    counter = counterState(0),
                    createOp = counterCreateOp(42),
                ),
            ),
            "sync1:",
        )

        assertEquals(ObjectsState.Synced, ro.state)

        // The counter exists in the pool but no InternalLiveMap entry points to it
        val orphan = ro.objectsPool.get("counter:orphan@1000")!!
        assertEquals(emptyMap(), orphan.parentReferences)

        // getFullPaths returns empty list for unreferenced object
        assertEquals(0, orphan.getFullPaths().size)
    }
}
