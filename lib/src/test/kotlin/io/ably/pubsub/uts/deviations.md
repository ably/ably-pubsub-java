# Deviations — UTS realtime/rest suites (`io.ably.pubsub.uts.*`)

> **Scope:** this file lives alongside the realtime/rest UTS suites in the `:java` module
> (`lib/src/test/kotlin/io/ably/pubsub/uts/`) and holds deviations for the **realtime/rest tiers** it
> hosts, plus the REST `HttpObject` specs of the objects module (`objects/*/rest_object_*.md`, in
> `uts/unit/objects` and `uts/integration/standard/objects`), whose entries are under
> *Adapted Tests*. The other **objects** tiers (unit, integration and proxy) moved to `:liveobjects`'s
> own test source set alongside the tests they document; their deviations (the typed-SDK / language adaptations, the
> intentional RTO18d entry, and any objects integration/proxy entries) are in
> `liveobjects/src/test/kotlin/io/ably/pubsub/liveobjects/uts/deviations.md`. For the shared UTS infra these
> suites consume, the tier smoke examples, and the `RUN_DEVIATIONS` mechanism, see `uts/README.md`.
>
> Records every place a generated test deviates from its UTS spec, using the manual's **Recording
> deviations** entry format: each entry records the spec point, what the spec requires, what the SDK
> actually does, and which test carries the deviation gate. Every realtime/rest deviation recorded here
> is a **genuine SDK bug** (open; ably-js is compliant — fix the SDK), stated per entry under **Status**.
> The REST `HttpObject` entries (RSL16b, RSO2a1c, RSO2e, T-4, RSO9/RSO14/RSO15 result shapes) are the exception: they are typed-SDK
> adaptations or intentional design divergences, as their **Status** lines say. Other entries of those
> kinds are in the objects file.

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

### RSL16b — not applicable: `HttpObject` needs no plugin (`objects.RestObjectGetTest`)

1. **Spec point:** RSL16b (`objects/unit/RSL16b/missing-plugin-error-0`).
2. **What the spec says:** accessing `channel.object` on a client without the Objects plugin fails with `ErrorInfo` 400 / 40019. The test applies only to SDKs that handle this by throwing, and says SDKs that make the error impossible to express should note it as not applicable.
3. **What the SDK does:** `HttpObject` is implemented directly in the core HTTP client (`DefaultHttpObject`), so every HTTP channel has a working `channel.object` and there is no plugin to omit.
4. **Root cause:** design choice — the REST objects API is plain HTTP and has no dependency on the realtime LiveObjects engine.
5. **Test impact:** the test asserts that a client built without any plugin has a working `channel.object` (it issues the GET); passes.
6. **Status:** intentional.

### RSO2a1c — compact and full formats are separate methods (`getCompact`, `get`); compact is JSON (`objects.RestObjectGetTest`)

1. **Spec point:** RSO2a1c, RSO2f (`objects/unit/RSO2d/path-compact-query-params-0`, `objects/unit/RSO2f/compact-returned-verbatim-0`, `objects/unit/RSO2e/compact-msgpack-decoded-0`).
2. **What the spec says:** `get` takes a `compact` param; `compact: true` is sent as `compact=true`, and the compact result is the decoded body as is, so msgpack binary values are raw bytes.
3. **What the SDK does:** `getCompact` returns the compact result as a Gson `JsonElement`, like realtime `PathObject#compactJson()`, and `get` returns the full result as `HttpObjectGetFullResult`; the get params are a `Param[]` (built with `HttpObjectGetParams`), and a caller's `compact` param is dropped. `getCompact` never sends `compact` (the server default), `get` sends `compact=false`, and binary values in a compact result are Base64 strings for both protocols.
4. **Root cause:** a typed API: one method can't return both a JSON value and a typed full result without returning `Object`.
5. **Test impact:** the `compact: true` case asserts no `compact` param; the explicit-`compact: true` call is `getCompact()`; the msgpack avatar is asserted as the Base64 string `"AQID"`; compact results are compared through `fromJson`. All pass.
6. **Status:** intentional API design.

### RSO2e — binary `Accept` header also lists JSON (`objects.RestObjectGetTest`)

1. **Spec point:** RSO2e (`objects/unit/RSO2e/compact-msgpack-decoded-0`).
2. **What the spec says:** the msgpack request's `Accept` header is `application/x-msgpack`.
3. **What the SDK does:** sends `application/x-msgpack,application/json`, as every binary-protocol request in ably-java does.
4. **Root cause:** `HttpUtils.defaultAcceptHeaders(true)`, shared by all REST requests.
5. **Test impact:** adapted assertion on the actual header; passes.
6. **Status:** intentional, SDK-wide.

### T-4 (objects) — operation and generate-ID bodies can't be built with the wrong number of bodies (`objects.RestObjectPublishTest`, `objects.RestObjectGenerateIdTest`)

1. **Spec point:** RSO5b1 (`objects/unit/RSO5b1/no-body-field-error-0`, `objects/unit/RSO5b1/multiple-body-fields-error-1`), RSO4c (`objects/unit/RSO4c/missing-create-body-error-0`).
2. **What the spec says:** an operation with zero or several body fields, or a generate-ID body with neither `mapCreate` nor `counterCreate`, fails with 400 / 40003 before any request. Each test notes it is not applicable where the SDK makes these inputs unrepresentable.
3. **What the SDK does:** `HttpObjectOperation` and `HttpObjectGenerateIdBody` can only be built through factories that set one body, and the `with*` methods only change the common fields, so several bodies are unrepresentable. No body at all is reachable only through a `null` passed to a factory from Java (`HttpObjectOperation.mapCreate((MapCreate) null)`, `generateObjectId(null)`), and that fails with 400 / 40003 before any request, as the spec requires.
4. **Root cause:** typed-SDK construction (the same T-4 pattern as the objects suite, `liveobjects/.../uts/deviations.md`).
5. **Test impact:** `RSO5b1/multiple-body-fields-error-1` exists under its `@UTS` id with a `// DEVIATION T-4` comment and asserts only that no request was sent. `RSO5b1/no-body-field-error-0` and `RSO4c/missing-create-body-error-0` assert the 400 / 40003 for real, passing the `null` through reflection because Kotlin can't. The other runtime-reachable 40003 case — an unrecognised semantics, `ObjectsMapSemantics.UNKNOWN` — is asserted in `RSO3d1/unrecognized-semantics-error-1` and `RSO4e1a/unrecognized-semantics-error-0`.
6. **Status:** intentional — a typed-SDK adaptation, sanctioned by each spec test's own not-applicable note.

### RSO9, RSO14, RSO15 — typed result shapes, and a closed semantics enum (`objects.RestObjectGetTest`, `objects.RestObjectPublishTest`, `objects.RestObjectGenerateIdTest`)

1. **Spec point:** RSO9c1 (`objects/unit/RSO2g3/...` leaf tests), RSO14 (`objects/unit/RSO2g1/...`), RSO15 (`objects/unit/RSO2g2/...`), RSO3d1 / RSO4e1a (`objects/unit/RSO3d1/unrecognized-semantics-error-1`, `objects/unit/RSO4e1a/unrecognized-semantics-error-0`).
2. **What the spec says:** a full-format leaf has `objectId` and `encoding` unset (RSO9c1); a map exposes `map.semantics` (as `"LWW"` in the mocked responses) and `map.entries`; a counter exposes `counter.data.number`; an unrecognised semantics such as `"not-a-semantics"` fails with 400 / 40003.
3. **What the SDK does:** `HttpObjectData` has no `encoding` accessor at all, so RSO9c1's `encoding` half holds by construction and isn't asserted. `HttpLiveMap` flattens `map.*` into `getSemantics()` (an `ObjectsMapSemantics` enum) and `getEntries()`, and `HttpLiveCounter` exposes `counter.data.number` as `getCount()`. `ObjectsMapSemantics` is a closed enum, so the only unrecognised value a caller can pass is `UNKNOWN`.
4. **Root cause:** typed-SDK result types and enums.
5. **Test impact:** the leaf tests note the unasserted `encoding` check in a comment; the map and counter tests read `entries` / `semantics == LWW` / `count`; the unrecognised-semantics tests pass `ObjectsMapSemantics.UNKNOWN` and assert 400 / 40003. All pass.
6. **Status:** intentional — typed-SDK adaptations.

## Mock Infrastructure Limitations

*(none)*
