# Deviations — UTS realtime/rest suites (`io.ably.lib.uts.*`)

> **Scope:** this file lives alongside the realtime/rest UTS suites in the `:java` module
> (`lib/src/test/kotlin/io/ably/lib/uts/`) and holds deviations for the **realtime/rest tiers** it
> hosts. All **objects** tiers (unit, integration and proxy) moved to `:liveobjects`'s own test source
> set alongside the tests they document; their deviations (the typed-SDK / language adaptations, the
> intentional RTO18d entry, and any objects integration/proxy entries) are in
> `liveobjects/src/test/kotlin/io/ably/lib/liveobjects/uts/deviations.md`. For the shared UTS infra these
> suites consume, the tier smoke examples, and the `RUN_DEVIATIONS` mechanism, see `uts/README.md`.
>
> Records every place a generated test deviates from its UTS spec, using the manual's **Recording
> deviations** entry format: each entry records the spec point, what the spec requires, what the SDK
> actually does, and which test carries the deviation gate. Every deviation recorded here is a
> **genuine SDK bug** (open; ably-js is compliant — fix the SDK), stated per entry under **Status**; the
> other actionability groups of the shared taxonomy (shared cross-SDK gap, typed-SDK / language
> adaptations, intentional design divergence) only have entries in the objects file.

> **Recently fixed and removed from this file:** RTO23e (`get()` now re-attaches a DETACHED channel —
> mode-only check + ensure-active-channel) and RTO20e/RTO20e1 (event-driven `once(SYNCED)` waiters +
> `failSyncWaiters` replace the orphan-prone shared deferred). Their spec-correct tests are un-gated and
> pass by default.
>
> One **test-stimulus adaptation** (not an SDK deviation) remains inline in `RealtimeObjectTest.kt`: the
> RTO20e1 test drives the 92008 path via channel ERROR → FAILED instead of the spec's DETACHED stimulus —
> an unsolicited DETACHED auto-reattaches (RTL13a) and never settles, so the spec's stimulus is
> unobservable. Same adaptation ably-js uses and the spec adopted for the proxy tier (specification#501).

---

## UTS Spec Errors

*(none)*

## Failing Tests

*SDK non-compliance where the spec-correct assertion is present but skipped (env-gated behind `RUN_DEVIATIONS`). These are the primary output — each maps to a potential issue to file.*

> ⚠ **RTL13b / RTL13c:** the channel-state UTS tests these two entries cite are **not currently part of the
> uts suite** (no `RTL13*` tests or gates exist in `unit/realtime/` — only `ConnectionRecoveryTest.kt` is
> translated). The entries are retained as confirmed SDK gaps (cross-checked against ably-js in
> `ABLY-JS-JAVA-DEVIATIONS-COMPARISON.md`); re-verify them when the channels module translation lands.
> (RTL13b is recorded under **Adapted Tests** — it adapts the test setup rather than env-gating an assertion.)

### RTL13c — channelRetryTimeout not cancelled when connection leaves CONNECTED

**Spec point:** RTL13c  
**What the spec requires:** When the connection is no longer CONNECTED, any pending automatic channel reattach timer (channelRetryTimeout) must be cancelled. The channel should remain SUSPENDED without attempting to reattach until the connection is restored.  
**What the SDK does:** The channelRetryTimeout fires regardless of connection state. When it fires while disconnected, the channel transitions to ATTACHING even though there is no active connection, and no ATTACH message can be sent.  
**Tests affected:**
- `RTL13c - automatic retry cancelled when connection is no longer CONNECTED` (RTL13c/retry-cancelled-disconnected-0) — the assertions `assertEquals(attachCountAfterDisconnect, attachCount)` and `assertEquals(ChannelState.suspended, channel.state)` are gated behind `RUN_DEVIATIONS`.

**Status:** Open SDK bug — ably-js is compliant; fix the SDK.

### RTN16f — msgSerial not initialised from recovery key on connect

**Spec point:** RTN16f  
**What the spec requires:** When instantiated with the `recover` option, the SDK initialises its internal `msgSerial` counter to the value stored in the recovery key, so the first published message carries that serial.  
**What the SDK does:** `ConnectionManager.onConnected()` resets `msgSerial` to 0 whenever `connection.id` is null on the fresh client (line 1316), even when the `recover` option is set. The recovered serial is discarded.  
**Workaround in tests:** The spec-correct assertion (`assertEquals(42L, msgSerial)`) is gated behind `RUN_DEVIATIONS`. A regression guard assertion (`assertEquals(0L, msgSerial)`) runs by default to catch any unintentional change to the SDK's actual behaviour.  
**Tests affected:**
- `RTN16f - recover option initializes msgSerial from recoveryKey` (RTN16f/recover-initializes-msgserial-0) — `assertEquals(42L, ...)` gated; `assertEquals(0L, ...)` added as regression guard.

**Status:** Open SDK bug — ably-js is compliant; fix the SDK.

## Adapted Tests

*SDK non-compliance where the test asserts the SDK's actual behaviour (or adapts its setup/stimulus to the SDK's reality) instead of using the spec's unreachable path. It passes, guards against regressions, and documents the deviation.*

> ⚠ Not currently part of the uts suite — see the shared **RTL13b / RTL13c** caveat under **Failing Tests**.

### RTL13b — ATTACHING → SUSPENDED via realtimeRequestTimeout not implemented

**Spec point:** RTL13b  
**What the spec requires:** If a channel's reattach request (triggered by RTL13a) does not receive a response within `realtimeRequestTimeout`, the channel must transition from ATTACHING to SUSPENDED and schedule a retry after `channelRetryTimeout`.  
**What the SDK does:** The channel remains in ATTACHING indefinitely when no server response arrives. The `realtimeRequestTimeout` timer is not applied to channel attach requests; only a server-sent DETACH/ERROR while ATTACHING causes the ATTACHING → SUSPENDED transition.  
**Workaround in tests:** Tests that need a SUSPENDED state set up via failed reattach instead use server-sent DETACHED while ATTACHING (RTL13b's second condition, which IS implemented) to drive the channel to SUSPENDED.  
**Tests affected:**
- `RTL13a - server DETACHED on SUSPENDED channel triggers immediate reattach` (RTL13a/suspended-reattach-triggered-1) — setup path changed
- `RTL13b - failed reattach transitions to SUSPENDED with automatic retry` (RTL13b/failed-reattach-suspended-retry-0) — mock sends DETACHED instead of withholding response
- `RTL13b - repeated failures cycle SUSPENDED to ATTACHING indefinitely` (RTL13b/repeated-failure-cycle-2) — mock sends DETACHED instead of withholding response
- `RTL13c - automatic retry cancelled when connection is no longer CONNECTED` (RTL13c/retry-cancelled-disconnected-0) — setup path changed

**Status:** Open SDK bug — ably-js is compliant; fix the SDK.

### RTN16g2 — the spec's fatal-ERROR-plus-close trigger can't drive this SDK to FAILED

**Spec point:** RTN16g2  
**What the spec requires:** Trigger FAILED state by sending a fatal ERROR message followed by closing the WebSocket (`send_to_client_and_close`), using error code 50000/statusCode 500.  
**What the SDK does (two issues):**  
1. Error code 50000/statusCode 500 is not treated as fatal by `isFatalError()` (requires code 40000–49999 or statusCode < 500), so FAILED is never reached with the spec's values.  
2. Sending `close(1000)` after the ERROR dispatches a synchronous `DISCONNECTED` action that races with and preempts the async `FAILED` transition triggered by the ERROR message.  
**Workaround in tests:** Use `sendToClient` (no close frame) with code 40000/statusCode 400. The SDK's own FAILED-state handler calls `clearTransport()`, so the explicit close is not needed.  
**Tests affected:**
- `RTN16g2 - createRecoveryKey returns null in inactive states and before first connect` (RTN16g2/recovery-key-null-inactive-0) — error code and send method changed.

**Status:** Open SDK bug — ably-js is compliant; fix the SDK.

## Mock Infrastructure Limitations

*(none)*
