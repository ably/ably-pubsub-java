package io.ably.lib.liveobjects.uts.unit

import io.ably.lib.liveobjects.Subscription
import io.ably.lib.liveobjects.instance.InstanceListener
import io.ably.lib.liveobjects.instance.InstanceSubscriptionEvent
import io.ably.lib.liveobjects.message.ObjectOperationAction
import io.ably.lib.liveobjects.message.WireCounterInc
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperation
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.uts.infra.pollUntil
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/**
 * Derived from UTS spec `objects/unit/live_object_subscribe.md` — LiveObject data
 * subscriptions (`RTLO4b` and sub-points): listener registration/deregistration through the
 * public `Instance#subscribe` (RTINS16), noop suppression, tombstone-driven deregistration,
 * and the source ObjectMessage carried on the event.
 *
 * Public-tier spec: uses only the public API plus the module-local `Helpers.kt`. The spec's
 * internal `LiveObjectUpdate` diff payload is not exposed on the public
 * `InstanceSubscriptionEvent` (only `getObject()` + `getMessage()`, `objects-mapping.md` §8);
 * these tests assert listener delivery counts and the public message fields, which is all this
 * spec requires.
 */
class LiveObjectSubscribeTest {

    /**
     * @UTS objects/unit/RTLO4b/subscribe-receives-updates-0
     */
    @Test
    fun `RTLO4b - subscribe registers listener for data updates`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val updates = mutableListOf<InstanceSubscriptionEvent>()
        val instance = assertNotNull(root.get("score").instance()).asLiveCounter()
        val sub = instance.subscribe(InstanceListener { event -> updates.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        assertIs<Subscription>(sub)
        assertEquals(1, updates.size)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLO4b7/subscribe-returns-subscription-0
     */
    @Test
    fun `RTLO4b7 - subscribe returns Subscription with unsubscribe method`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")
        val instance = assertNotNull(root.get("score").instance()).asLiveCounter()

        val sub = instance.subscribe(InstanceListener { })

        assertIs<Subscription>(sub)
        // `sub.unsubscribe IS Function`: guaranteed at compile time by the Subscription
        // interface; assert the callable reference exists.
        assertNotNull(sub::unsubscribe)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLO4b7/subscription-unsubscribe-stops-delivery-0
     */
    @Test
    fun `RTLO4b7 - Subscription unsubscribe stops delivery`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val updates = mutableListOf<InstanceSubscriptionEvent>()
        val control = mutableListOf<InstanceSubscriptionEvent>()
        val instance = assertNotNull(root.get("score").instance()).asLiveCounter()
        val sub = instance.subscribe(InstanceListener { event -> updates.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 5, "01", "remote"))),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        sub.unsubscribe()

        // Negative-assertion quiescence (helpers/standard_test_pool.md): subscribe a control
        // listener that WILL fire on the same dispatch as the message under test, then await it
        // before asserting `updates` is unchanged.
        instance.subscribe(InstanceListener { event -> control.add(event) })
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 10, "02", "remote"))),
        )
        pollUntil(5.seconds) { control.size >= 1 }

        // Control delivered, so the unsubscribed listener would also have run had it still been
        // registered.
        assertEquals(1, updates.size)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLO4b7/subscription-unsubscribe-idempotent-0
     */
    @Test
    fun `RTLO4b7 - Subscription unsubscribe is idempotent`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")
        val instance = assertNotNull(root.get("score").instance()).asLiveCounter()
        val sub = instance.subscribe(InstanceListener { })

        sub.unsubscribe()
        sub.unsubscribe()

        // No error thrown — both calls complete without error.

        client.close()
    }

    /**
     * @UTS objects/unit/RTLO4b4c1/noop-no-trigger-0
     */
    @Test
    fun `RTLO4b4c1 - noop update does not trigger listener`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val updates = mutableListOf<InstanceSubscriptionEvent>()
        val control = mutableListOf<InstanceSubscriptionEvent>()
        val instance = assertNotNull(root.get("score").instance()).asLiveCounter()
        instance.subscribe(InstanceListener { event -> updates.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 5, "01", "remote"))),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        // Serial "02" passes the newness check (RTLO4a6); an increment with no `number` is the
        // noop (RTLC9h). A raw ObjectMessage with no `number` field exercises the real
        // RTLC9h/RTLO4b4c1 noop branch (a `number: 0` would EXIST per RTLC9g and produce a
        // non-noop update with amount 0).
        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(
                    WireObjectMessage(
                        serial = "02",
                        siteCode = "remote",
                        operation = WireObjectOperation(
                            action = WireObjectOperationAction.CounterInc,
                            objectId = "counter:score@1000",
                            counterInc = WireCounterInc(number = null),
                        ),
                    ),
                ),
            ),
        )
        // Negative-assertion quiescence: drive a follow-up "03" increment and await it via a
        // SEPARATE control listener. Because "03" is dispatched after the noop "02" on the same
        // channel, once the control fires the noop has certainly been processed.
        val controlSub = instance.subscribe(InstanceListener { event -> control.add(event) })
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 3, "03", "remote"))),
        )
        pollUntil(5.seconds) { control.size >= 1 }
        controlSub.unsubscribe()

        // The noop "02" produced no update, so the original listener fired only for "01" and
        // "03". Had the noop wrongly fired, updates.size would be 3.
        assertEquals(2, updates.size)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLO4b6/subscribe-no-side-effects-0
     */
    @Test
    fun `RTLO4b6 - subscribe has no side effects`() = runTest {
        val (client, channel, root, _) = setupSyncedChannel("test")
        val stateBefore = channel.state
        val instance = assertNotNull(root.get("score").instance()).asLiveCounter()

        instance.subscribe(InstanceListener { })

        assertEquals(stateBefore, channel.state)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLO4b/subscribe-map-update-0
     */
    @Test
    fun `RTLO4b - subscribe on InternalLiveMap receives LiveMapUpdate`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val updates = mutableListOf<InstanceSubscriptionEvent>()
        val instance = assertNotNull(root.instance()).asLiveMap()
        instance.subscribe(InstanceListener { event -> updates.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        assertEquals(1, updates.size)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLO4b4c3c/tombstone-deregisters-listeners-0
     */
    @Test
    fun `RTLO4b4c3c - tombstone update deregisters all Instance subscribe listeners`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val updatesA = mutableListOf<InstanceSubscriptionEvent>()
        val updatesB = mutableListOf<InstanceSubscriptionEvent>()
        val control = mutableListOf<InstanceSubscriptionEvent>()
        val instance = assertNotNull(root.get("score").instance()).asLiveCounter()
        instance.subscribe(InstanceListener { event -> updatesA.add(event) })
        instance.subscribe(InstanceListener { event -> updatesB.add(event) })

        // Send an OBJECT_DELETE which causes a tombstone. Await ALL involved listeners on this
        // dispatch before asserting either count (negative-assertion quiescence, multi-listener
        // case).
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildObjectDelete("counter:score@1000", "50", "remote"))),
        )
        pollUntil(5.seconds) { updatesA.size >= 1 }
        pollUntil(5.seconds) { updatesB.size >= 1 }

        // Both listeners should have received the tombstone update.
        assertEquals(1, updatesA.size)
        assertEquals(ObjectOperationAction.OBJECT_DELETE, assertNotNull(updatesA[0].message).operation.action)
        assertEquals(1, updatesB.size)
        assertEquals(ObjectOperationAction.OBJECT_DELETE, assertNotNull(updatesB[0].message).operation.action)

        // Send another update to the tombstoned object — the deregistered listeners must not
        // fire. QUIESCENCE: a tombstoned object ignores further operations (RTLC7e), so use a
        // SEPARATE LIVE object as the control: an update on map:profile@1000 dispatched AFTER
        // the message under test. Messages are processed in order, so once the control fires,
        // "51" has also been processed.
        val controlInst = assertNotNull(root.get("profile").instance()).asLiveMap()
        controlInst.subscribe(InstanceListener { event -> control.add(event) })
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 3, "51", "remote"))),
        )
        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("map:profile@1000", "quiescence_probe", dataString("x"), "52", "remote")),
            ),
        )
        pollUntil(5.seconds) { control.size >= 1 }

        // Control delivered, so any still-registered original listener would also have run.
        assertEquals(1, updatesA.size)
        assertEquals(1, updatesB.size)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLO4b4c3c/tombstone-zero-value-counter-tears-down-0
     */
    @Test
    fun `RTLO4b4c3c - tombstone update on an already-zero counter still fires listeners then deregisters`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val updatesA = mutableListOf<InstanceSubscriptionEvent>()
        val updatesB = mutableListOf<InstanceSubscriptionEvent>()
        val control = mutableListOf<InstanceSubscriptionEvent>()
        val instance = assertNotNull(root.get("score").instance()).asLiveCounter()

        // Drive the counter (100 in the standard pool) down to 0 BEFORE registering the listeners
        // under test, so they observe only the tombstone. poll_until(value() == 0) is the quiescence
        // barrier that the increment has been applied before we subscribe, so the "-100" update is
        // not seen by them.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", -100, "40", "remote"))),
        )
        pollUntil(5.seconds) { root.get("score").asLiveCounter().value() == 0.0 }

        instance.subscribe(InstanceListener { event -> updatesA.add(event) })
        instance.subscribe(InstanceListener { event -> updatesB.add(event) })

        // OBJECT_DELETE tombstones the already-zero counter (zero-delta diff, RTLC14c → NOT a
        // no-op). Await ALL involved listeners on this dispatch before asserting either count
        // (negative-assertion quiescence, multi-listener case).
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildObjectDelete("counter:score@1000", "50", "remote"))),
        )
        pollUntil(5.seconds) { updatesA.size >= 1 }
        pollUntil(5.seconds) { updatesB.size >= 1 }

        // Both listeners received the tombstone update even though the counter data did not change
        // (0 → 0).
        assertEquals(1, updatesA.size)
        assertEquals(ObjectOperationAction.OBJECT_DELETE, assertNotNull(updatesA[0].message).operation.action)
        assertEquals(1, updatesB.size)
        assertEquals(ObjectOperationAction.OBJECT_DELETE, assertNotNull(updatesB[0].message).operation.action)

        // Prove deregistration. As in the populated teardown case, a tombstoned object ignores
        // further ops (RTLC7e), so use a SEPARATE LIVE object as the control: an update on
        // map:profile@1000 dispatched AFTER the message under test. Messages are processed in
        // order, so once the control fires, the follow-up "51" has also been processed.
        val controlInst = assertNotNull(root.get("profile").instance()).asLiveMap()
        controlInst.subscribe(InstanceListener { event -> control.add(event) })
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 3, "51", "remote"))),
        )
        mockWs.sendToClient(
            buildObjectMessage(
                "test",
                listOf(buildMapSet("map:profile@1000", "quiescence_probe", dataString("x"), "52", "remote")),
            ),
        )
        pollUntil(5.seconds) { control.size >= 1 }

        // Control delivered, so any still-registered original listener would also have run: the
        // tombstone deregistered them per RTLO4b4c3c.
        assertEquals(1, updatesA.size)
        assertEquals(1, updatesB.size)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLO4b4d/update-has-object-message-0
     */
    @Test
    fun `RTLO4b4d - InstanceSubscriptionEvent message is populated from source ObjectMessage`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val updates = mutableListOf<InstanceSubscriptionEvent>()
        val instance = assertNotNull(root.get("score").instance()).asLiveCounter()
        instance.subscribe(InstanceListener { event -> updates.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        assertEquals(1, updates.size)
        val message = assertNotNull(updates[0].message)
        assertEquals("99", message.serial)
        assertEquals("remote", message.siteCode)
        assertEquals(ObjectOperationAction.COUNTER_INC, message.operation.action)
        assertEquals("counter:score@1000", message.operation.objectId)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLO4b4e/tombstone-flag-true-0
     */
    @Test
    fun `RTLO4b4e - tombstone update identified by OBJECT_DELETE action`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val updates = mutableListOf<InstanceSubscriptionEvent>()
        val instance = assertNotNull(root.get("score").instance()).asLiveCounter()
        instance.subscribe(InstanceListener { event -> updates.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildObjectDelete("counter:score@1000", "50", "remote"))),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        assertEquals(1, updates.size)
        assertEquals(ObjectOperationAction.OBJECT_DELETE, assertNotNull(updates[0].message).operation.action)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLO4b4e/tombstone-flag-false-0
     */
    @Test
    fun `RTLO4b4e - normal update carries non-tombstone action`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val updates = mutableListOf<InstanceSubscriptionEvent>()
        val instance = assertNotNull(root.get("score").instance()).asLiveCounter()
        instance.subscribe(InstanceListener { event -> updates.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        assertEquals(1, updates.size)
        assertEquals(ObjectOperationAction.COUNTER_INC, assertNotNull(updates[0].message).operation.action)

        client.close()
    }
}
