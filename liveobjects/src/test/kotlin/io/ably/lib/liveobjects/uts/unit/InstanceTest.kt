package io.ably.lib.liveobjects.uts.unit

import io.ably.lib.liveobjects.Subscription
import io.ably.lib.liveobjects.ValueType
import io.ably.lib.liveobjects.instance.Instance
import io.ably.lib.liveobjects.instance.InstanceListener
import io.ably.lib.liveobjects.instance.InstanceSubscriptionEvent
import io.ably.lib.liveobjects.message.ObjectOperationAction
import io.ably.lib.liveobjects.value.LiveMapValue
import io.ably.lib.uts.infra.pollUntil
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Derived from UTS spec `objects/unit/instance.md` — the public `Instance` API
 * (`RTINS1`–`RTINS16`): identity (`id`), value/entry reads, mutations delegating to the
 * underlying live objects, and identity-based subscriptions.
 *
 * Public-tier spec: uses only the public API plus the module-local `Helpers.kt`
 * (`setupSyncedChannel`, message builders). Typed-SDK notes
 * (`objects-mapping.md` §5): the base `Instance` is partitioned into typed sub-interfaces
 * reached via throwing `as*` casts (RTTS9d), the dynamic API's polymorphic
 * `value()`/`size()`/`id()` null results and 92007 wrong-type errors are therefore expressed
 * differently — see the `// DEVIATION` comments and `deviations.md`.
 */
class InstanceTest {

    /**
     * @UTS objects/unit/RTINS3/id-returns-objectid-0
     */
    @Test
    fun `RTINS3 - id property returns objectId`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val counterInst = assertNotNull(root.get("score").instance())
        assertEquals("counter:score@1000", counterInst.asLiveCounter().id)

        val mapInst = assertNotNull(root.get("profile").instance())
        assertEquals("map:profile@1000", mapInst.asLiveMap().id)

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS4/value-counter-0
     */
    @Test
    fun `RTINS4 - value returns counter number or primitive`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val counterInst = assertNotNull(root.get("score").instance())
        assertEquals(100.0, counterInst.asLiveCounter().value())

        // DEVIATION (typed-SDK partition, see deviations.md): the base Instance has no value()
        // and Instance casts throw on mismatch (RTTS9d), so `map_inst.value() == null` is not
        // expressible; assert the wrapped type instead — a LIVE_MAP instance carries no value
        // accessor by construction (RTINS4d / RTTS7c).
        val mapInst = assertNotNull(root.instance())
        assertEquals(ValueType.LIVE_MAP, mapInst.type)

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS5/get-wraps-entry-0
     */
    @Test
    fun `RTINS5 - get returns Instance wrapping entry value`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")
        val rootInst = assertNotNull(root.instance()).asLiveMap()

        val nameInst = rootInst.get("name")
        assertIs<Instance>(nameInst)
        assertEquals("Alice", nameInst.asString().value())

        val scoreInst = assertNotNull(rootInst.get("score"))
        assertEquals("counter:score@1000", scoreInst.asLiveCounter().id)

        val nullInst = rootInst.get("nonexistent")
        assertNull(nullInst)

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS6/entries-yields-instances-0
     */
    @Test
    fun `RTINS6 - entries returns array of key Instance pairs`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")
        val rootInst = assertNotNull(root.instance()).asLiveMap()

        val entries = mutableMapOf<String, Instance>()
        for ((key, inst) in rootInst.entries()) {
            entries[key] = inst
        }

        assertEquals(7, entries.size)
        assertIs<Instance>(entries["name"])
        assertEquals("Alice", entries.getValue("name").asString().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS9/size-0
     */
    @Test
    fun `RTINS9 - size returns non-tombstoned count`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val rootInst = assertNotNull(root.instance()).asLiveMap()
        assertEquals(7L, rootInst.size())

        // DEVIATION (typed-SDK partition, see deviations.md): LiveCounterInstance has no size()
        // and the Instance cast to LiveMapInstance throws (RTTS9d), so
        // `counter_inst.size() == null` is not expressible; assert the wrapped type instead —
        // a LIVE_COUNTER instance carries no size accessor by construction (RTINS9c / RTTS7c).
        val counterInst = assertNotNull(root.get("score").instance())
        assertEquals(ValueType.LIVE_COUNTER, counterInst.type)

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS10/compact-0
     */
    @Test
    fun `RTINS10 - compact recursively compacts`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")
        val rootInst = assertNotNull(root.instance()).asLiveMap()

        // DEVIATION (RTTS7d, see deviations.md): compact() is not implemented in ably-java;
        // compactJson() is the typed-SDK equivalent and behaves identically for this tree.
        val result = rootInst.compactJson()

        assertEquals("Alice", result.get("name").asString)
        assertEquals(100.0, result.get("score").asDouble)
        assertEquals("alice@example.com", result.getAsJsonObject("profile").get("email").asString)

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS12/set-delegates-0
     */
    @Test
    fun `RTINS12 - set delegates to InternalLiveMap set`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")
        val rootInst = assertNotNull(root.instance()).asLiveMap()

        rootInst.set("name", LiveMapValue.of("Bob")).await()

        assertEquals("Bob", root.get("name").asString().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS12d/set-non-map-throws-0
     */
    @Test
    fun `RTINS12d - set on non-map throws`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")
        val counterInst = assertNotNull(root.get("score").instance())

        // DEVIATION (typed-SDK, see deviations.md): set() does not exist on the wrong-typed
        // Instance view; the equivalent failure is the throwing asLiveMap() cast (RTTS9d),
        // which raises IllegalStateException rather than an ErrorInfo with code 92007.
        assertFailsWith<IllegalStateException> {
            counterInst.asLiveMap().set("key", LiveMapValue.of("value")).await()
        }

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS13/remove-delegates-0
     */
    @Test
    fun `RTINS13 - remove delegates to InternalLiveMap remove`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")
        val rootInst = assertNotNull(root.instance()).asLiveMap()

        rootInst.remove("name").await()

        assertNull(root.get("name").asString().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS14/increment-delegates-0
     */
    @Test
    fun `RTINS14 - increment delegates to InternalLiveCounter increment`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")
        val counterInst = assertNotNull(root.get("score").instance())

        counterInst.asLiveCounter().increment(25).await()

        assertEquals(125.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS14d/increment-non-counter-throws-0
     */
    @Test
    fun `RTINS14d - increment on non-counter throws`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")
        val mapInst = assertNotNull(root.instance())

        // DEVIATION (typed-SDK, see deviations.md): increment() does not exist on the
        // wrong-typed Instance view; the equivalent failure is the throwing asLiveCounter()
        // cast (RTTS9d) — IllegalStateException, not ErrorInfo 92007.
        assertFailsWith<IllegalStateException> {
            mapInst.asLiveCounter().increment(5).await()
        }

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS15/decrement-delegates-0
     */
    @Test
    fun `RTINS15 - decrement delegates to InternalLiveCounter decrement`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")
        val counterInst = assertNotNull(root.get("score").instance())

        counterInst.asLiveCounter().decrement(10).await()

        assertEquals(90.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS14a/increment-default-0
     */
    @Test
    fun `RTINS14a - increment defaults to 1`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")
        val counterInst = assertNotNull(root.get("score").instance())

        counterInst.asLiveCounter().increment().await()

        assertEquals(101.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS15a/decrement-default-0
     */
    @Test
    fun `RTINS15a - decrement defaults to 1`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")
        val counterInst = assertNotNull(root.get("score").instance())

        counterInst.asLiveCounter().decrement().await()

        assertEquals(99.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS16/subscribe-receives-events-0
     */
    @Test
    fun `RTINS16 - subscribe receives InstanceSubscriptionEvent`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val counterInst = assertNotNull(root.get("score").instance()).asLiveCounter()
        val events = mutableListOf<InstanceSubscriptionEvent>()
        val sub = counterInst.subscribe(InstanceListener { event -> events.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        assertIs<Subscription>(sub)
        assertEquals(1, events.size)
        assertIs<Instance>(events[0].getObject())
        assertEquals("counter:score@1000", events[0].getObject().asLiveCounter().id)

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS16c/subscribe-primitive-throws-0
     */
    @Test
    fun `RTINS16c - subscribe on primitive throws`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val nameInst = assertNotNull(assertNotNull(root.instance()).asLiveMap().get("name"))
        // DEVIATION (typed-SDK, see deviations.md): subscribe() does not exist on primitive
        // Instance sub-types (RTTS7b/RTTS7c); the equivalent failure is the throwing cast to a
        // subscribable view (RTTS9d) — IllegalStateException, not ErrorInfo 92007.
        assertFailsWith<IllegalStateException> {
            nameInst.asLiveMap().subscribe(InstanceListener { })
        }

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS16e2/subscription-event-message-0
     */
    @Test
    fun `RTINS16e2 - InstanceSubscriptionEvent contains public ObjectMessage`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val rootInst = assertNotNull(root.instance()).asLiveMap()
        val events = mutableListOf<InstanceSubscriptionEvent>()
        rootInst.subscribe(InstanceListener { event -> events.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        assertIs<Instance>(events[0].getObject())
        assertEquals("root", events[0].getObject().asLiveMap().id)
        val message = assertNotNull(events[0].message)
        assertEquals("test", message.channel)
        assertEquals(ObjectOperationAction.MAP_SET, message.operation.action)
        assertEquals("root", message.operation.objectId)
        assertEquals("name", assertNotNull(message.operation.mapSet).key)

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS16f/subscribe-returns-subscription-0
     */
    @Test
    fun `RTINS16f - subscribe returns Subscription for deregistration`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val counterInst = assertNotNull(root.get("score").instance()).asLiveCounter()
        val events = mutableListOf<InstanceSubscriptionEvent>()
        val sub = counterInst.subscribe(InstanceListener { event -> events.add(event) })
        sub.unsubscribe()

        // Quiescence control: a second, still-subscribed listener on the same counter instance
        // that WILL fire on the same dispatch as the send below
        // (helpers/standard_test_pool.md "Negative-assertion quiescence").
        val controlEvents = mutableListOf<InstanceSubscriptionEvent>()
        counterInst.subscribe(InstanceListener { event -> controlEvents.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )

        // Await the control listener; once it has fired, the unsubscribed listener would also
        // have fired had it remained subscribed — then assert its count is unchanged.
        pollUntil(5.seconds) { controlEvents.size >= 1 }
        assertEquals(0, events.size)

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS16g/subscription-follows-identity-0
     */
    @Test
    fun `RTINS16g - Instance subscription follows identity not path`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val counterInst = assertNotNull(root.get("score").instance()).asLiveCounter()
        val events = mutableListOf<InstanceSubscriptionEvent>()
        counterInst.subscribe(InstanceListener { event -> events.add(event) })

        // Repoint root's "score" key to a different object, then increment the ORIGINAL counter.
        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("root", "score", dataObjectId("counter:new@2000"), remoteSerial(0), "remote")),
            ),
        )
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 10, "100", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        assertTrue(events.size >= 1)
        // RTINS16e1: assert against the DELIVERED EVENT's object id (not the pre-existing
        // counter_inst handle) — the listener followed the identity, not the "score" path.
        assertIs<Instance>(events[0].getObject())
        assertEquals("counter:score@1000", events[0].getObject().asLiveCounter().id)

        client.close()
    }

    /**
     * @UTS objects/unit/RTINS16h/subscribe-no-side-effects-0
     */
    @Test
    fun `RTINS16h - subscribe has no side effects`() = runTest {
        val (client, channel, root, _) = setupSyncedChannel("test")
        val counterInst = assertNotNull(root.get("score").instance()).asLiveCounter()
        val channelStateBefore = channel.state

        counterInst.subscribe(InstanceListener { })

        assertEquals(channelStateBefore, channel.state)

        client.close()
    }
}
