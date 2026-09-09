package io.ably.lib.liveobjects.uts.unit

import io.ably.lib.liveobjects.Subscription
import io.ably.lib.liveobjects.message.ObjectOperationAction
import io.ably.lib.liveobjects.path.PathObject
import io.ably.lib.liveobjects.path.PathObjectListener
import io.ably.lib.liveobjects.path.PathObjectSubscriptionEvent
import io.ably.lib.liveobjects.path.PathObjectSubscriptionOptions
import io.ably.lib.realtime.ChannelState
import io.ably.lib.types.AblyException
import io.ably.lib.types.ChannelMode
import io.ably.lib.types.ChannelOptions
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.uts.infra.awaitChannelState
import io.ably.lib.uts.infra.pollUntil
import io.ably.lib.uts.infra.unit.ConnectionDetails
import io.ably.lib.uts.infra.unit.MockWebSocket
import io.ably.lib.uts.infra.unit.TestRealtimeClient
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
 * Derived from UTS spec `objects/unit/path_object_subscribe.md` — PathObject subscriptions
 * (`RTPO19`, `RTO24`, `RTO25`): Subscription return, event object/message payloads, depth
 * filtering (RTO24c1/RTO24c2), candidate-path construction (RTO24b2a), multi-path dispatch
 * via getFullPaths (RTO24b1), exactly-once delivery per dispatch (RTO24b2b), and the RTO25
 * access preconditions.
 *
 * Public-tier spec: uses only the public API plus the module-local `Helpers.kt`.
 */
class PathObjectSubscribeTest {

    /**
     * @UTS objects/unit/RTPO19/subscribe-receives-events-0
     */
    @Test
    fun `RTPO19 - subscribe returns Subscription and receives events`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        val sub = root.get("score").subscribe(PathObjectListener { event -> events.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        assertIs<Subscription>(sub)
        assertEquals(1, events.size)
        assertIs<PathObject>(events[0].getObject())
        assertEquals("score", events[0].getObject().path())
        val message = assertNotNull(events[0].message)
        assertEquals("99", message.serial)
        assertEquals("remote", message.siteCode)
        assertNotNull(message.operation)
        assertEquals(ObjectOperationAction.COUNTER_INC, message.operation.action)
        assertEquals("test", message.channel)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO19b/subscribe-precondition-detached-0
     */
    @Test
    fun `RTPO19b - subscribe on DETACHED channel throws 90001`() = runTest {
        lateinit var mockWs: MockWebSocket
        mockWs = MockWebSocket {
            onConnectionAttempt = { conn ->
                conn.respondWithSuccess(
                    ProtocolMessage(ProtocolMessage.Action.connected).apply {
                        connectionId = "conn-1"
                        connectionDetails = ConnectionDetails {
                            connectionKey = "conn-key-1"
                            siteCode = "test-site"
                            objectsGCGracePeriod = 86_400_000L
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
                    ProtocolMessage.Action.detach -> {
                        mockWs.sendToClient(
                            ProtocolMessage(ProtocolMessage.Action.detached).apply { channel = msg.channel },
                        )
                    }
                    else -> Unit
                }
            }
        }

        val client = TestRealtimeClient {
            key = "fake:key"
            install(mockWs)
        }
        val channel = client.channels.get(
            "test",
            ChannelOptions().apply { modes = arrayOf(ChannelMode.object_subscribe, ChannelMode.object_publish) },
        )
        val root = channel.`object`.get().await()

        channel.detach()
        awaitChannelState(channel, ChannelState.detached)

        val error = assertFailsWith<AblyException> {
            root.subscribe(PathObjectListener { })
        }

        assertEquals(90001, error.errorInfo.code)
        assertEquals(400, error.errorInfo.statusCode)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO19c1a/subscribe-non-positive-depth-throws-0
     */
    @Test
    fun `RTPO19c1a - subscribe with non-positive depth throws 40003`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val error = assertFailsWith<AblyException> {
            root.subscribe(PathObjectListener { }, PathObjectSubscriptionOptions(0))
        }

        assertEquals(40003, error.errorInfo.code)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO19c1a/subscribe-negative-depth-throws-0
     */
    @Test
    fun `RTPO19c1a - subscribe with negative depth throws 40003`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val error = assertFailsWith<AblyException> {
            root.subscribe(PathObjectListener { }, PathObjectSubscriptionOptions(-1))
        }

        assertEquals(40003, error.errorInfo.code)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO19c1/subscribe-depth-1-self-only-0
     */
    @Test
    fun `RTPO19c1 - subscribe with depth 1 only receives self events`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { event -> events.add(event) }, PathObjectSubscriptionOptions(1))
        // Quiescence control: an unlimited-depth root listener that DOES cover the out-of-scope
        // child path, so it fires on the send below and gives us a delivery to await
        // (Negative-assertion quiescence, helpers/standard_test_pool.md).
        val control = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { event -> control.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        val controlBefore = control.size
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "100", "remote"))),
        )
        // Negative-assertion quiescence: the unlimited-depth control covers ["score"], so await
        // its delivery for this dispatch, THEN assert the depth-1 listener did NOT fire.
        pollUntil(5.seconds) { control.size > controlBefore }

        assertEquals(1, events.size)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO19c1/subscribe-depth-2-children-0
     */
    @Test
    fun `RTPO19c1 - subscribe with depth 2 receives self and children`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { event -> events.add(event) }, PathObjectSubscriptionOptions(2))
        // Quiescence control: an unlimited-depth root listener that covers the out-of-scope
        // grandchild path, so it fires on the send below.
        val control = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { event -> control.add(event) })

        // Self event (root map update) — candidate [] is covered at depth 2.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        // Child event (root["score"] counter) — candidate ["score"], relativeDepth 1-0+1 = 2 <= 2, covered.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "100", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 2 }

        // Grandchild event (root["profile"]["nested_counter"] counter) — candidate
        // ["profile","nested_counter"], relativeDepth 2-0+1 = 3 > 2, NOT covered. A COUNTER_INC
        // yields ONLY this single candidate (no key candidate), unlike a MAP_SET on a child map
        // which would also emit the covered parent-map path (RTO24b2a1).
        val controlBefore = control.size
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:nested@1000", 1, "101", "remote"))),
        )
        // Negative-assertion quiescence: the unlimited-depth control covers
        // ["profile","nested_counter"], so await its delivery for this dispatch, THEN assert the
        // depth-2 listener did NOT fire on the grandchild update.
        pollUntil(5.seconds) { control.size > controlBefore }

        assertEquals(2, events.size)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO19c1/subscribe-unlimited-depth-0
     */
    @Test
    fun `RTPO19c1 - subscribe with no depth receives all descendants`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { event -> events.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "100", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 2 }

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("map:prefs@1000", "theme", dataString("light"), remoteSerial(1), "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 3 }

        assertTrue(events.size >= 3)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO19d/subscribe-returns-subscription-0
     */
    @Test
    fun `RTPO19d - subscribe returns Subscription with unsubscribe`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        val sub = root.get("score").subscribe(PathObjectListener { event -> events.add(event) })
        // Quiescence control: a separate, still-subscribed listener on the same (live) object
        // that WILL fire on the send below, giving a delivery to await.
        val control = mutableListOf<PathObjectSubscriptionEvent>()
        root.get("score").subscribe(PathObjectListener { event -> control.add(event) })

        assertIs<Subscription>(sub)
        sub.unsubscribe()

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )
        // Negative-assertion quiescence: the separate control listener (still subscribed) fires
        // on this dispatch; await it, THEN assert the unsubscribed listener did not fire.
        pollUntil(5.seconds) { control.size >= 1 }

        assertEquals(0, events.size)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO19e1/event-path-object-correct-0
     */
    @Test
    fun `RTPO19e1 - subscribe event provides correct PathObject`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { event -> events.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        assertIs<PathObject>(events[0].getObject())
        assertEquals("score", events[0].getObject().path())
        assertEquals(107.0, events[0].getObject().asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO19e2/event-message-delivery-0
     */
    @Test
    fun `RTPO19e2 - subscribe event delivers PublicAPI ObjectMessage for operations`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.get("score").subscribe(PathObjectListener { event -> events.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 42, "serial-1", "site-a"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        val message = assertNotNull(events[0].message)
        assertEquals("test", message.channel)
        assertEquals("serial-1", message.serial)
        assertEquals("site-a", message.siteCode)
        assertNotNull(message.operation)
        assertEquals(ObjectOperationAction.COUNTER_INC, message.operation.action)
        assertEquals("counter:score@1000", message.operation.objectId)
        assertEquals(42.0, assertNotNull(message.operation.counterInc).number)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO19e2/event-message-omitted-no-operation-0
     */
    @Test
    fun `RTPO19e2 - subscribe event omits message when objectMessage has no operation`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { event -> events.add(event) })

        // Send an OBJECT_SYNC that changes counter:score@1000's state (100 -> 200) via
        // replaceData (RTLC6) — a sync-triggered update, so its objectMessage has no `operation`
        // field. The sync intentionally omits `root`: per RTO5c2a the root object must never be
        // removed from the pool (RTO3b), so root is retained and still references "score".
        mockWs.sendToClient(
            buildObjectSyncMessage(
                "test",
                "sync2:",
                listOf(
                    buildObjectState(
                        "counter:score@1000",
                        mapOf("aaa" to "t:1"),
                        counter = counterState(0),
                        createOp = counterCreateOp(200),
                    ),
                ),
            ),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        // Events from sync-triggered updates should have no message.
        for (event in events) {
            assertNull(event.message)
        }

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO19f/subscribe-follows-path-0
     */
    @Test
    fun `RTPO19f - subscribe follows path not identity`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.get("score").subscribe(PathObjectListener { event -> events.add(event) })

        // Replace the counter at "score" with a new counter.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "score", dataObjectId("counter:new@2000"), remoteSerial(0), "remote"))),
        )

        // Increment the NEW counter at "score".
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:new@2000", 10, "100", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        // Should receive event for the new counter, since subscription follows path.
        assertTrue(events.any { it.getObject().path() == "score" })

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO19g/subscribe-no-side-effects-0
     */
    @Test
    fun `RTPO19g - subscribe has no side effects`() = runTest {
        val (client, channel, root, _) = setupSyncedChannel("test")
        val stateBefore = channel.state

        root.get("score").subscribe(PathObjectListener { })

        assertEquals(stateBefore, channel.state)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO19/subscribe-primitive-path-0
     */
    @Test
    fun `RTPO19 - subscribe on primitive path receives change events`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.get("name").subscribe(PathObjectListener { event -> events.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        assertEquals(1, events.size)
        assertEquals("name", events[0].getObject().path())

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO19/map-clear-triggers-child-events-0
     */
    @Test
    fun `RTPO19 - MAP_CLEAR triggers subscription events on child paths`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { event -> events.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapClear("root", "99", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        assertTrue(events.size >= 1)

        client.close()
    }

    /**
     * @UTS objects/unit/RTPO19/child-events-bubble-0
     */
    @Test
    fun `RTPO19 - child events bubble up to parent subscription`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.get("profile").subscribe(PathObjectListener { event -> events.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("map:profile@1000", "email", dataString("bob@example.com"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:nested@1000", 3, "100", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 2 }

        assertTrue(events.size >= 2)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO24c1/depth-filtering-formula-0
     */
    @Test
    fun `RTO24c1 - depth filtering formula`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        // Seed a grandchild OBJECT under profile.prefs (path ["profile","prefs","deep"]) so the
        // grandchild stimulus below can be a COUNTER_INC yielding ONLY that single depth-3
        // candidate. Sent BEFORE subscribing, so it does not fire the listener under test.
        // (RTO6 zero-value-creates counter:deep@3000.)
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("map:prefs@1000", "deep", dataObjectId("counter:deep@3000"), "50", "remote"))),
        )
        // The mock delivers messages asynchronously, so wait for the seed to be applied before
        // subscribing — the spec's precondition is that this send is processed BEFORE the
        // listener under test is registered ("Sent BEFORE subscribing, so it does not fire the
        // listener under test"). Without this wait the seed's MAP_SET dispatch races the
        // subscribe and can fire the depth-2 listener via its covered ["profile","prefs"]
        // candidate, producing a spurious third event.
        pollUntil(5.seconds) { root.at("profile.prefs.deep").asLiveCounter().value() != null }
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        // Subscribe at "profile" with depth 2:
        // self (profile)          -> eventPath=["profile"],                  1 - 1 + 1 = 1 <= 2  yes
        // child (profile.nested)  -> eventPath=["profile","nested_counter"], 2 - 1 + 1 = 2 <= 2  yes
        // grandchild (prefs.deep) -> eventPath=["profile","prefs","deep"],   3 - 1 + 1 = 3 > 2   no
        root.get("profile").subscribe(PathObjectListener { event -> events.add(event) }, PathObjectSubscriptionOptions(2))
        // Quiescence control: an unlimited-depth root listener that covers the out-of-scope
        // grandchild path, so it fires on the grandchild send below.
        val control = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { event -> control.add(event) })

        // Self event (profile map update) — first covered candidate is ["profile"].
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("map:profile@1000", "email", dataString("bob@example.com"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        // Child event (nested counter at ["profile","nested_counter"], relativeDepth 2) — covered.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:nested@1000", 3, "100", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 2 }

        // Grandchild event (counter:deep at ["profile","prefs","deep"], relativeDepth 3) — should
        // NOT be received. A COUNTER_INC yields ONLY this single depth-3 candidate.
        val controlBefore = control.size
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:deep@3000", 1, "101", "remote"))),
        )
        // Negative-assertion quiescence: the unlimited-depth control covers
        // ["profile","prefs","deep"], so await its delivery, THEN assert the depth-2 listener
        // did NOT fire on the grandchild.
        pollUntil(5.seconds) { control.size > controlBefore }

        assertEquals(2, events.size)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO24c1/prefix-mismatch-0
     */
    @Test
    fun `RTO24c1 - prefix mismatch does not trigger subscription`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val profileEvents = mutableListOf<PathObjectSubscriptionEvent>()
        root.get("profile").subscribe(PathObjectListener { event -> profileEvents.add(event) })
        // Control listener at root: fires on both out-of-scope sends below, providing a delivery
        // to await on the same dispatch before asserting profileEvents is unchanged.
        val controlEvents = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { event -> controlEvents.add(event) })

        // Change at "score" — "profile" is not a prefix of "score".
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote"))),
        )

        // Change at "name" — "profile" is not a prefix of "name".
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(0), "remote"))),
        )
        // QUIESCENCE: await the control listener (fires for both sends) so that any
        // profileEvents callback would also have run before we assert it is unchanged.
        pollUntil(5.seconds) { controlEvents.size >= 2 }

        assertEquals(0, profileEvents.size)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO24b2a/candidate-paths-map-keys-0
     */
    @Test
    fun `RTO24b2a - candidate path construction includes map update keys`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val scoreEvents = mutableListOf<PathObjectSubscriptionEvent>()
        val rootEvents = mutableListOf<PathObjectSubscriptionEvent>()
        // Subscribe at the child path "score" (pathToThis=[""] + key "score" = ["score"]).
        root.get("score").subscribe(PathObjectListener { event -> scoreEvents.add(event) })
        // Subscribe at root path (pathToThis=[""]).
        root.subscribe(PathObjectListener { event -> rootEvents.add(event) })

        // MAP_SET on root with key "score" — generates candidates:
        //   1. pathToThis = [] (root itself)
        //   2. [] + "score" = ["score"] (from the map update key)
        // Both subscriptions should fire.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "score", dataObjectId("counter:new@2000"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { scoreEvents.size >= 1 }
        pollUntil(5.seconds) { rootEvents.size >= 1 }

        assertEquals(1, scoreEvents.size)
        assertEquals("score", scoreEvents[0].getObject().path())
        assertEquals(1, rootEvents.size)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO24b2c/listener-exception-caught-0
     */
    @Test
    fun `RTO24b2c - listener exception does not affect other listeners`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        root.subscribe(PathObjectListener { throw RuntimeException("boom") })
        root.subscribe(PathObjectListener { event -> events.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "name", dataString("Bob"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        assertEquals(1, events.size)

        client.close()
    }

    /**
     * @UTS objects/unit/RTO24b1/multi-path-dispatch-0
     */
    @Test
    fun `RTO24b1 - dispatch via getFullPaths for multi-path objects`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val eventsScore = mutableListOf<PathObjectSubscriptionEvent>()
        val eventsAlias = mutableListOf<PathObjectSubscriptionEvent>()

        // "score" already points to counter:score@1000.
        // Add a second reference "alias" -> counter:score@1000 so it has two paths.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "alias", dataObjectId("counter:score@1000"), "98", "remote"))),
        )
        // Await the seed's observable effect before subscribing — the SDK applies inbound messages
        // asynchronously, so without this the MAP_SET dispatch races the subscribes below and the
        // alias listener can also see the seed event (2 instead of 1). Same pattern as the depth
        // tests in this file.
        pollUntil(5.seconds) { root.get("alias").exists() }

        root.get("score").subscribe(PathObjectListener { event -> eventsScore.add(event) })
        root.get("alias").subscribe(PathObjectListener { event -> eventsAlias.add(event) })

        // Increment counter:score@1000 — getFullPaths returns ["score"] and ["alias"].
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 5, "99", "remote"))),
        )
        pollUntil(5.seconds) { eventsScore.size >= 1 }
        pollUntil(5.seconds) { eventsAlias.size >= 1 }

        assertEquals(1, eventsScore.size)
        assertEquals("score", eventsScore[0].getObject().path())
        assertEquals(1, eventsAlias.size)
        assertEquals("alias", eventsAlias[0].getObject().path())

        client.close()
    }

    /**
     * @UTS objects/unit/RTO24b2b/fires-once-per-dispatch-0
     */
    @Test
    fun `RTO24b2b - subscription fires exactly once per dispatch`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")
        val events = mutableListOf<PathObjectSubscriptionEvent>()
        // Subscribe at root (unlimited depth) — covers both [] and ["score"].
        root.subscribe(PathObjectListener { event -> events.add(event) })

        // MAP_SET on root with key "score" — candidates are [] and ["score"]. Root subscription
        // covers both, but should fire exactly once with the first candidate (pathToThis = []).
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildMapSet("root", "score", dataObjectId("counter:new@2000"), remoteSerial(0), "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 1 }

        // QUIESCENCE: a second, single-candidate dispatch acts as the control delivery. Awaiting
        // it guarantees any spurious second callback from the first (multi-candidate) dispatch
        // would already have run, so events.size == 2 confirms the first dispatch fired exactly
        // once.
        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:new@2000", 1, "100", "remote"))),
        )
        pollUntil(5.seconds) { events.size >= 2 }

        // Exactly one event per dispatch, even though multiple candidates match:
        // one from the multi-candidate MAP_SET + one from the control increment.
        assertEquals(2, events.size)

        client.close()
    }
}
