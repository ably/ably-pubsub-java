# Deviations — UTS objects suites (`io.ably.lib.liveobjects.uts.*`)

> **Scope:** this file holds deviations for **all three objects tiers** now hosted in `:liveobjects` —
> objects **unit** (`io.ably.lib.liveobjects.uts.unit`), **integration**
> (`io.ably.lib.liveobjects.uts.integration`) and **proxy** (`io.ably.lib.liveobjects.uts.proxy`).
> The integration/proxy tiers moved here from `:uts` in Phase 3 alongside the tests they document.
>
> Records every place a generated test deviates from its UTS spec, using the manual's
> **Recording deviations** entry format. Structural deviation vocabulary for these suites:
> S-1…S-4 in `.claude/skills/uts-to-kotlin/references/objects-mapping.md` §17.9.
> (The realtime/rest tiers keep their own file, `:java`-hosted, at
> `lib/src/test/kotlin/io/ably/lib/uts/deviations.md`.)

## UTS Spec Errors

*(none)*

## Failing Tests

*(none)*

## Adapted Tests

### RTO18d — duplicate listener registration de-duplicates, fires once (`RealtimeObjectTest`)

1. **Spec point:** RTO18d (`objects/unit/RTO18d/duplicate-listener-0`).
2. **What the spec says:** the default asserts the general `EventEmitter#on` / RTE4 reference — the same listener registered twice for one event fires **twice**. The UTS carries an **OPTIONAL note** sanctioning SDKs that de-duplicate listeners by instance to instead assert `call_count == 1` (or skip the test).
3. **What the SDK does:** ably-java de-duplicates — the same listener registered twice for `SYNCED` is invoked **once** per emission. Its core `EventEmitter` keys per-event registrations by listener instance (`filters.put(listener, …)`, `lib/.../util/EventEmitter.java`, reached via `ObjectsStateEmitter`), so the duplicate registration overwrites the first. This is a long-standing, deliberate SDK-wide choice (its own Javadoc documents it as an RTE4 deviation) — a listener registered twice runs identical logic, so double invocation serves no purpose.
4. **Root cause:** `EventEmitter.on(Event, Listener)` de-duplicates by listener instance. This is the core emitter backing Connection/Channel/Presence, not a LiveObjects-specific choice; any move to the RTE4 default would need scope-limiting to `ObjectsStateEmitter` (large blast radius otherwise) and would change `off()`'s remove-one-vs-all semantics.
5. **Test impact:** the test asserts ably-java's actual behaviour (`call_count == 1`) per the RTO18d optional note — it **passes unconditionally** (no env-gate) and pins the dedup (a regression that lost it would fire twice in one emission and fail the assertion). Cross-SDK reference (verified in source): ably-js (`eventemitter.ts`, `listeners.push`) and ably-cocoa (`ARTEventEmitter.m`, a fresh `ARTEventListener` per `on:`) both append → fire **twice**; ably-java dedupes → fires **once**. Sanctioned by the UTS optional clause, so this is a documented design divergence, **not** an SDK bug to fix.

### S-2 — `pool.processAttached(...)` maps to the async `handleStateChange` (`ParentReferencesTest`)

1. **Spec point:** RTO5c10 / RTO5c10a (setup step `pool.processAttached(ProtocolMessage(action: ATTACHED, channel: "test", channelSerial: "...", flags: HAS_OBJECTS))` in `objects/unit/parent_references.md`).
2. **What the spec says:** the pseudocode calls `processAttached` synchronously, then immediately delivers the OBJECT_SYNC.
3. **What the SDK does:** the equivalent `DefaultRealtimeObject.handleStateChange(ChannelState.attached, hasObjects = true)` launches on the internal sequential scope, so its effects (SYNCING transition) are asynchronous (shape deviation S-2, `objects-mapping.md` §17.9).
4. **Root cause:** `DefaultRealtimeObject.handleStateChange` wraps its body in `sequentialScope.launch { ... }` (`DefaultRealtimeObject.kt`).
5. **Test impact:** the tests await the transition with `assertWaiter { ro.state == ObjectsState.Syncing }` before delivering the sync (helper `processAttachedWithObjects()`), then call `objectsManager.handleObjectSyncMessages(...)` directly (the §17.6 mapping of `processObjectSync`). Affected tests (all pass):
   - `objects/unit/RTO5c10/rebuild-from-sync-0`
   - `objects/unit/RTO5c10a/rebuild-clears-stale-refs-0` (two attach+sync rounds — the S-2 handling applies to each)
   - `objects/unit/RTO5c10/unreferenced-empty-refs-0`

### S-1 — emitted subscription event carries no diff/`noop`/`tombstone` payload (`ObjectsPoolTest`)

1. **Spec point:** RTO4b2a and RTO5c7 in `objects/unit/objects_pool.md` — on a no-objects ATTACHED / sync completion the pool emits a `LiveObjectUpdate` to `pool["root"].subscribe` listeners, and the tests assert on the emitted `updates[0].update` per-key diff.
2. **What the spec says:** the emitted `LiveObjectUpdate` carries the per-key `update` diff (e.g. `{ "name": "removed"/"updated" }`) as well as `noop`, `tombstone` and `objectMessage`.
3. **What the SDK does:** the update reaches subscribers as a `DefaultInstanceSubscriptionEvent`, which carries `getObject()` + `getMessage()` but **no diff/`noop`/`tombstone` accessors** (shape deviation S-1, `objects-mapping.md` §17.9). (Note: the op path `applyObject` now *returns* the `ObjectUpdate` per RTLC9g/RTLM7f, so the `internal_live_counter`/`internal_live_map` op-path tests assert the returned update directly — no adaptation is needed there; this deviation only concerns the emit path.)
4. **Root cause:** `InstanceSubscriptionEvent` exposes no diff payload (`instance/DefaultInstanceSubscriptionEvent.kt`).
5. **Test impact:** the `ObjectsPoolTest` RTO4b2a/RTO5c7 tests (marked `// DEVIATION S-1` inline) assert the emitted diff (`{ "name": "removed"/"updated" }`) via the pool/root **data state** instead of `updates[0].update`, and RTO4b2a's `objectMessage IS null` directly via `event.getMessage() == null`. All affected tests pass.

### S-2 — `pool.processAttached(...)` maps to the async `handleStateChange` (`ObjectsPoolTest`)

1. **Spec point:** every `pool.processAttached(ProtocolMessage(action: ATTACHED, ...))` setup/step in `objects/unit/objects_pool.md`.
2. **What the spec says:** `processAttached` executes synchronously; subsequent sync/object messages and assertions follow immediately.
3. **What the SDK does:** the equivalent `DefaultRealtimeObject.handleStateChange(ChannelState.attached, hasObjects)` launches its body on the internal sequential scope (shape deviation S-2, `objects-mapping.md` §17.9), so its effects are asynchronous — and several tests re-attach while already `Syncing`, where no observable state transition exists to await.
4. **Root cause:** `DefaultRealtimeObject.handleStateChange` wraps its body in `sequentialScope.launch { ... }` (`DefaultRealtimeObject.kt`).
5. **Test impact:** `ObjectsPoolTest.processAttached(hasObjects)` drives the manager steps of the attached branch synchronously (the §17.6/§17.9 sanctioned alternative to `assertWaiter`): `clearBufferedObjectOperations()` (RTO4d), `startNewSync(null)` (RTO4c), and for `HAS_OBJECTS=0` additionally `resetToInitialPool(true)` (RTO4b1/RTO4b2/RTO4b2a), `clearSyncObjectsPool()` (RTO4b3), `endSync()` (RTO4b4) — mirroring `handleStateChange`'s attached branch line-for-line. Affects every `ObjectsPoolTest` test that attaches; all pass. (The async production path itself is exercised by `ParentReferencesTest.processAttachedWithObjects`.)

### S-3 — GC time comes from the clock, not a `now` parameter (`InternalLiveMapTest`)

1. **Spec point:** RTLM19 (`objects/unit/RTLM19/gc-tombstoned-entries-0` — `map.gcTombstonedEntries(grace_period, now)`).
2. **What the spec says:** the GC entry point takes the current time as an explicit `now` argument alongside the grace period.
3. **What the SDK does:** the equivalent `InternalLiveMap.onGCInterval(gcGracePeriod)` has no `now` parameter — `LiveMapEntry.isEligibleForGc` reads the current time from `clock.currentTimeMillis()` (`SystemClock.clockFrom(adapter.clientOptions)`) (shape deviation S-3, `objects-mapping.md` §17.9).
4. **Root cause:** `InternalLiveMap.onGCInterval` / `LiveMapEntry.isEligibleForGc(gcGracePeriod, clock)` (`value/livemap/`).
5. **Test impact:** the test stubs `SystemClock.clockFrom(any())` via `mockkStatic(SystemClock::class)` to a fixed `Clock` returning the spec's `now` (a synchronous stub window — no poll/timeout overlaps it), then calls `onGCInterval(grace_period)`. `unmockkAll()` in `@AfterTest` restores it. The test passes.

### S-4 — `ObjectsPool` construction starts a real GC coroutine (`InternalLiveCounterTest`, `InternalLiveMapTest`, `ObjectsPoolTest`)

1. **Spec point:** every test in `objects/unit/internal_live_counter.md`, `objects/unit/internal_live_map.md` and `objects/unit/objects_pool.md` (setup steps `counter = InternalLiveCounter(...)` / `map = InternalLiveMap(...)` / `pool = ObjectsPool()`).
2. **What the spec says:** the objects are constructed as plain data structures with no lifecycle to manage.
3. **What the SDK does:** the internal classes are built against a `DefaultRealtimeObject` (§17.1 — no public constructors), whose `ObjectsPool.init` starts a GC coroutine plus an adapter `onGCGracePeriodUpdated` subscription (shape deviation S-4, `objects-mapping.md` §17.9).
4. **Root cause:** `ObjectsPool.init` (`ObjectsPool.kt`) — `startGCJob()` + `adapter.onGCGracePeriodUpdated`.
5. **Test impact:** each class builds against a `DefaultRealtimeObject("test", getMockAblyClientAdapter())` created in `@BeforeTest`, and `@AfterTest` tears down with `ro.objectsPool.dispose()` + `unmockkAll()`. No assertion is affected; all tests pass.

### T-1 — wrong-typed `Instance` operations surface as throwing `as*` casts, not ErrorInfo 92007 (`InstanceTest`)

1. **Spec point:** RTINS12d (`objects/unit/RTINS12d/set-non-map-throws-0`), RTINS14d (`objects/unit/RTINS14d/increment-non-counter-throws-0`), RTINS16c (`objects/unit/RTINS16c/subscribe-primitive-throws-0`).
2. **What the spec says:** calling `set` on a non-map, `increment` on a non-counter, or `subscribe` on a primitive Instance fails with an `ErrorInfo` with code `92007`.
3. **What the SDK does:** ably-java implements the typed-SDK variant (RTTS7c): `set`/`increment`/`subscribe` do not exist on the base `Instance` or on the wrong-typed sub-interface, so the dynamic-API call is not expressible; the equivalent failure is the fail-fast `Instance` cast (`asLiveMap()`/`asLiveCounter()`), which throws `IllegalStateException` per RTTS9d (`DefaultInstance.kt`) — no `ErrorInfo`/92007 is produced on this path.
4. **Root cause:** typed `Instance` hierarchy (RTTS7/RTTS9d); `DefaultInstance.as*` throws `IllegalStateException`.
5. **Test impact:** each test performs the spec's operation through the throwing cast and asserts `assertFailsWith<IllegalStateException> { inst.asLiveMap().set(...) }` (and analogues) instead of asserting error code 92007 (marked `// DEVIATION` inline). All three tests pass.

### T-2 — dynamic-API null-result reads have no typed-`Instance` equivalent (`InstanceTest`, `PathObjectTest`)

1. **Spec point:** RTINS4d (`objects/unit/RTINS4/value-counter-0` — `map_inst.value() == null`), RTINS9c (`objects/unit/RTINS9/size-0` — `counter_inst.size() == null`), RTINS3b via RTPO8f (`objects/unit/RTPO8f/instance-primitive-wrapped-0` — `name_inst.id() == null`).
2. **What the spec says:** the polymorphic `Instance` returns `null` from `value()` on a map, `size()` on a counter, and `id()` on a primitive.
3. **What the SDK does:** the typed `Instance` partition (RTTS7c) puts each accessor only on the sub-interfaces where it is meaningful — a `LiveMapInstance` has no `value()`, a `LiveCounterInstance` has no `size()`, primitive instances have no `id` — and `Instance` casts to the wrong view throw (RTTS9d), so there is no non-throwing expression that returns `null`.
4. **Root cause:** typed `Instance` hierarchy (RTTS7c/RTTS9d).
5. **Test impact:** the tests assert the wrapped type instead (`assertEquals(ValueType.LIVE_MAP, mapInst.type)` etc., marked `// DEVIATION` inline) — the accessor's absence on that type is enforced at compile time. (Contrast `PathObject`: its casts never throw (RTTS5d), so the analogous `PathObject` null-reads in `path_object.md` are translated as real `null` assertions, not deviations.) All affected tests pass.

### T-3 — `compact()` is not implemented; `compactJson()` is the typed-SDK equivalent (`InstanceTest`, `PathObjectTest`)

1. **Spec point:** RTINS10 (`objects/unit/RTINS10/compact-0`), RTPO13 (`objects/unit/RTPO13/compact-recursive-0`), RTPO13c5 (`objects/unit/RTPO13c5/compact-cycle-detection-0`), RTPO13c (`objects/unit/RTPO13c/compact-counter-0`), and the `compact() == null` line of RTPO3c1 (`objects/unit/RTPO3c1/read-null-on-failure-0`).
2. **What the spec says:** `compact()` returns a plain recursive snapshot with raw binary values and, for cycles, reuses the already-compacted in-memory object (`result["prefs"]["back_ref"] IS result`).
3. **What the SDK does:** typed SDKs need not implement `compact` (RTTS3f/RTTS7d); ably-java exposes only `compactJson()`, which base64-encodes binary values (RTPO14b1) and represents cyclic references as `{ "objectId": ... }` markers (RTPO14b2).
4. **Root cause:** `PathObject`/`Instance` expose `compactJson()` only (RTTS3f/RTTS7d).
5. **Test impact:** each test calls `compactJson()` (marked `// DEVIATION` inline); the snapshot assertions are unchanged except `avatar` asserting the base64 form `"AQID"` (RTPO13) and the cycle asserting the `{ "objectId": "map:profile@1000" }` marker (RTPO13c5), which makes RTPO13c5 assert the same observable as RTPO14. All affected tests pass.

### T-4 — invalid-input cases rejected at compile time by the typed signatures (`InternalLiveCounterApiTest`, `InternalLiveMapApiTest`, `PathObjectTest`)

1. **Spec point:** RTLC12e1 (`objects/unit/RTLC12e1/increment-non-number-0` and the `null`/string/boolean/array/object rows of `objects/unit/RTLC12e1/increment-invalid-amounts-table-0`), RTLM20/RTLMV4c (`objects/unit/RTLM20/set-invalid-values-table-0`), RTPO5b (`objects/unit/RTPO5b/get-non-string-throws-0`), RTPO6b (`objects/unit/RTPO6b/at-non-string-throws-0`).
2. **What the spec says:** passing a non-Number increment amount throws 40003; setting an unsupported value type (function/undefined/symbol) throws 40013; passing a non-String key/path to `get`/`at` throws 40003.
3. **What the SDK does:** the typed signatures (`increment(@NotNull Number)`, `set(String, LiveMapValue)` with the closed `LiveMapValue` union, `get(@NotNull String)`, `at(@NotNull String)`) reject these inputs at compile time, so the runtime validation cannot be reached; function/undefined/symbol have no Kotlin/Java equivalent at all.
4. **Root cause:** typed-SDK signatures (`objects-mapping.md` §6 note — signature-forbidden cases are not expressible).
5. **Test impact:** the tests exist under their `@UTS` ids with a `// DEVIATION` comment documenting the compile-time enforcement and no runtime assertion (`increment-non-number-0`, `set-invalid-values-table-0`, `get-non-string-throws-0`, `at-non-string-throws-0`). The runtime-reachable rows (NaN / ±Infinity) are asserted for real in `increment-invalid-amounts-table-0` (ErrorInfo 40003); the `null` row is skipped per the spec's own language-applicability note (null is not passable through Kotlin's non-null `Number` parameter). All affected tests pass.

### S-4 — `ObjectsPool` construction starts a real GC coroutine (`ParentReferencesTest`)

1. **Spec point:** every test in `objects/unit/parent_references.md` (setup step `pool = ObjectsPool()` / the implicit pool behind `InternalLiveCounter(...)` / `InternalLiveMap(...)` construction).
2. **What the spec says:** `ObjectsPool()` is constructed as a plain data structure with no lifecycle to manage.
3. **What the SDK does:** the pool is created inside `DefaultRealtimeObject` (§17.1 instantiation — internal classes have no public constructors), and `ObjectsPool.init` starts a GC coroutine plus an adapter `onGCGracePeriodUpdated` subscription (shape deviation S-4, `objects-mapping.md` §17.9).
4. **Root cause:** `ObjectsPool.init` (`ObjectsPool.kt`) — `startGCJob()` + `adapter.onGCGracePeriodUpdated`.
5. **Test impact:** every `ParentReferencesTest` test builds its objects against a `DefaultRealtimeObject("test", getMockAblyClientAdapter())` created in `@BeforeTest`, and `@AfterTest` tears down with `ro.objectsPool.dispose()` + `unmockkAll()`. No assertion is affected; all tests pass.

### T-4 — value-type invalid-input cases rejected at compile time by the typed signatures (`ValueTypesTest`)

1. **Spec point:** RTLCV3c (`objects/unit/RTLCV3c/no-validation-at-create-0`), RTLCV4a (`objects/unit/RTLCV4a/evaluate-validates-count-0`), RTLMV4a (`objects/unit/RTLMV4a/evaluate-validates-entries-0`), RTLMV4b (`objects/unit/RTLMV4b/evaluate-validates-keys-0`), RTLMV4c (`objects/unit/RTLMV4c/evaluate-validates-values-0`).
2. **What the spec says:** `LiveCounter.create("not_a_number")` succeeds at creation (RTLCV3c) and its evaluation throws 40003 (RTLCV4a); `LiveMap.create(null)` evaluation throws 40003 (RTLMV4a); a non-String key throws 40003 (RTLMV4b); an unsupported value (a function) throws 40013 (RTLMV4c).
3. **What the SDK does:** the typed factories (`LiveCounter.create(@NotNull Number)`, `LiveMap.create(@NotNull Map<String, LiveMapValue>)` with the closed `LiveMapValue` union) reject every one of these inputs at compile time (`objects-mapping.md` §6), so the runtime validations are unreachable as specified.
4. **Root cause:** typed-SDK signatures — same mechanism as the existing T-4 entry above.
5. **Test impact:** RTLMV4a/RTLMV4b/RTLMV4c exist under their `@UTS` ids with a `// DEVIATION` comment and no runtime assertion (RTLMV4b per the spec's own language-applicability note). RTLCV3c and RTLCV4a substitute the runtime-reachable invalid input the same RTLCV4a clause covers ("not a Number **or not finite**"): `LiveCounter.create(Double.NaN)` — creation does not throw (RTLCV3c) and evaluation throws ErrorInfo 40003 (RTLCV4a), asserted for real. All five tests pass.

### T-6 — value-type retained state and evaluation are internal; retained creates carried as `derivedFrom` (`ValueTypesTest`)

1. **Spec point:** RTLCV3 (`create-with-count-0`, `create-default-zero-0`), RTLCV4g5, RTLCV4 (`evaluate-zero-count-0`), RTLMV3 (`create-with-entries-0`), RTLMV4j5, RTLMV4d/RTLMV4d1/RTLMV4e2 and the `map-set-all-types-table-0` table (all in `objects/unit/value_types.md`).
2. **What the spec says:** the value type exposes its retained state (`vt.count`, `vt.entries`); `evaluate(vt)` is a first-class operation returning ObjectMessages; the evaluated operation retains `counterCreate`/`mapCreate` alongside the `*WithObjectId` payload.
3. **What the SDK does:** the retained state is deliberately non-public (RTLCV3d/RTLMV3d — no accessor on the public `LiveCounter`/`LiveMap`); evaluation is the internal `DefaultLiveCounter.createCounterCreateMessage` / `DefaultLiveMap.createMapCreateMessages` (RTLCV4h/RTLMV4k, `objects-mapping.md` §13 — the evaluation half of `value_types.md` is internal/wire-level); the retained create is carried as the local-only `derivedFrom` field on the wire `*CreateWithObjectId` types (`@Transient`, MCRO2/CCRO2 — the same modelling `public_object_message.md` uses for PAOOP3b2/PAOOP3c2).
4. **Root cause:** typed-SDK/wire modelling; no public evaluation surface exists by design.
5. **Test impact:** these tests (in `:liveobjects`'s own test source set) read `DefaultLiveCounter.initialCount` / `DefaultLiveMap.entries` for the retained-state assertions (marked `// DEVIATION T-6` inline), call the internal evaluation entry points via a local `evaluate(...)` helper against a §17.1 `DefaultRealtimeObject`, and assert the spec's `operation.counterCreate` / `operation.mapCreate` through `*CreateWithObjectId.derivedFrom`. All assertions are otherwise unchanged; all tests pass.

### S-4 — `ObjectsPool` construction starts a real GC coroutine (`ValueTypesTest`)

1. **Spec point:** every evaluation test in `objects/unit/value_types.md` (the `evaluate(vt)` step needs a realtime-object context for RTO14/RTO16 objectId derivation).
2. **What the spec says:** `evaluate(vt)` is a pure operation with no lifecycle to manage.
3. **What the SDK does:** evaluation runs against a `DefaultRealtimeObject` (§17.1 — internal classes have no public constructors), whose `ObjectsPool.init` starts a GC coroutine plus an adapter `onGCGracePeriodUpdated` subscription (shape deviation S-4, `objects-mapping.md` §17.9).
4. **Root cause:** `ObjectsPool.init` (`ObjectsPool.kt`) — `startGCJob()` + `adapter.onGCGracePeriodUpdated`.
5. **Test impact:** `ValueTypesTest` builds the harness in `@BeforeTest` (`DefaultRealtimeObject("test", getMockAblyClientAdapter())`) and tears down in `@AfterTest` with `unmockkAll()` + `ro.objectsPool.dispose()`. No assertion is affected; all tests pass.

## Mock Infrastructure Limitations

*(none)*
