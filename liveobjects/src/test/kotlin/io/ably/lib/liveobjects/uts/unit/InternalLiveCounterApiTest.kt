package io.ably.lib.liveobjects.uts.unit

import io.ably.lib.liveobjects.instance.InstanceListener
import io.ably.lib.liveobjects.instance.InstanceSubscriptionEvent
import io.ably.lib.liveobjects.message.WireObjectMessage
import io.ably.lib.liveobjects.message.WireObjectOperationAction
import io.ably.lib.types.AblyException
import io.ably.lib.types.ProtocolMessage
import io.ably.lib.uts.infra.pollUntil
import io.ably.lib.uts.infra.unit.MockEvent
import io.ably.lib.uts.infra.unit.MockWebSocket
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds

/**
 * Derived from UTS spec `objects/unit/internal_live_counter_api.md` — the public counter
 * read/write surface (`RTLC5`, `RTLC11`–`RTLC13`): `value()`, `increment`/`decrement` wire
 * messages and local apply-on-ACK, amount validation, and the counter update event.
 *
 * Public-tier spec: uses only the public API plus the module-local `Helpers.kt`. The spec's
 * hand-rolled capturing mock maps to the standard `setupSyncedChannel` transport (identical
 * CONNECTED/ATTACHED/OBJECT_SYNC/ACK behaviour) plus the mock's recorded `MessageFromClient`
 * event log as the spec's `captured_messages` — see [capturedObjectMessages].
 */
class InternalLiveCounterApiTest {

    /**
     * The spec's `captured_messages`: every OBJECT ProtocolMessage the client sent, read from
     * the mock transport's event log.
     */
    private fun capturedObjectMessages(mockWs: MockWebSocket): List<ProtocolMessage> =
        mockWs.events.filterIsInstance<MockEvent.MessageFromClient>()
            .map { it.message }
            .filter { it.action == ProtocolMessage.Action.`object` }

    /**
     * @UTS objects/unit/RTLC5/value-returns-data-0
     */
    @Test
    fun `RTLC5 - value returns current counter data`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        val counter = root.get("score")
        assertEquals(100.0, counter.asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTLC12/increment-sends-counter-inc-0
     */
    @Test
    fun `RTLC12 - increment sends v6 COUNTER_INC message`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment(25).await()

        val capturedMessages = capturedObjectMessages(mockWs)
        assertEquals(1, capturedMessages.size)
        val objMsg = assertNotNull(capturedMessages[0].state)[0] as WireObjectMessage
        val op = assertNotNull(objMsg.operation)
        assertEquals(WireObjectOperationAction.CounterInc, op.action)
        assertEquals("counter:score@1000", op.objectId)
        assertEquals(25.0, assertNotNull(op.counterInc).number)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLC12/increment-applies-locally-0
     */
    @Test
    fun `RTLC12 - increment applies locally after ACK`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().increment(50).await()

        assertEquals(150.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTLC12e1/increment-non-number-0
     */
    @Test
    fun `RTLC12e1 - increment with non-number throws`() {
        // DEVIATION (see deviations.md): `increment` takes a `@NotNull Number`, so the spec's
        // increment("not_a_number") is rejected at compile time — the invalid-input contract
        // (ErrorInfo 40003) for non-Number amounts is enforced by the type system and cannot be
        // exercised at runtime. The runtime-reachable invalid amounts (NaN / ±Infinity) are
        // covered by `RTLC12e1 - Table-driven invalid increment amounts`.
    }

    /**
     * @UTS objects/unit/RTLC13/decrement-negates-0
     */
    @Test
    fun `RTLC13 - decrement delegates to increment with negated amount`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")

        root.get("score").asLiveCounter().decrement(15).await()

        val capturedMessages = capturedObjectMessages(mockWs)
        val objMsg = assertNotNull(capturedMessages[0].state)[0] as WireObjectMessage
        assertEquals(-15.0, assertNotNull(assertNotNull(objMsg.operation).counterInc).number)
        assertEquals(85.0, root.get("score").asLiveCounter().value())

        client.close()
    }

    /**
     * @UTS objects/unit/RTLC11/counter-update-on-inc-0
     */
    @Test
    fun `RTLC11 - LiveCounterUpdate emitted on increment`() = runTest {
        val (client, _, root, mockWs) = setupSyncedChannel("test")

        val updates = mutableListOf<InstanceSubscriptionEvent>()
        val instance = assertNotNull(root.get("score").instance()).asLiveCounter()
        instance.subscribe(InstanceListener { event -> updates.add(event) })

        mockWs.sendToClient(
            buildObjectMessage("test", listOf(buildCounterInc("counter:score@1000", 7, "99", "remote-site"))),
        )
        pollUntil(5.seconds) { updates.size >= 1 }

        assertEquals(7.0, assertNotNull(assertNotNull(updates[0].message).operation.counterInc).number)

        client.close()
    }

    /**
     * @UTS objects/unit/RTLC12e1/increment-invalid-amounts-table-0
     */
    @Test
    fun `RTLC12e1 - Table-driven invalid increment amounts`() = runTest {
        val (client, _, root, _) = setupSyncedChannel("test")

        // DEVIATION (see deviations.md): only the NaN / Infinity / -Infinity rows are
        // expressible against `increment(@NotNull Number)`. The string / boolean / array /
        // object rows are rejected at compile time by the Number signature, and the `null` row
        // is not passable from Kotlin (non-null parameter) — per the spec's own
        // language-applicability note, that row does not apply where null cannot be passed.
        val invalidAmounts = listOf(
            Double.NaN to "NaN",
            Double.POSITIVE_INFINITY to "Infinity",
            Double.NEGATIVE_INFINITY to "-Infinity",
        )

        for ((value, label) in invalidAmounts) {
            val error = assertFailsWith<AblyException>("expected failure for $label") {
                root.get("score").asLiveCounter().increment(value).await()
            }
            assertEquals(40003, error.errorInfo.code, "wrong error code for $label")
        }

        client.close()
    }
}
