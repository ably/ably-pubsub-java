---
description: "Translate the UTS pseudocode test specs in a whole module directory into runnable Kotlin tests in the owning ably-java module (:java for realtime/rest, :liveobjects for objects; :uts hosts the shared infra + smoke examples). Takes a UTS module directory (e.g. <cloned-ably-specification-repo-path>/uts/objects), validates its structure, resolves the target ably-java module, lets you pick a tier (unit/integration/proxy) and which specs, then derives a Kotlin test per spec. Usage: /uts-to-kotlin <path-to-uts-module-directory>"
allowed-tools: Bash, Read, Edit, Write, WebFetch
---

Translate the UTS pseudocode test specs under the **module directory** `$ARGUMENTS` into runnable Kotlin
tests in the owning ably-java module (`:java` for realtime/rest, `:liveobjects` for objects; `:uts` hosts
the shared infra + smoke examples).

`$ARGUMENTS` is a UTS *module* directory — a directory sitting directly under the spec repo's `uts/`,
e.g. `<cloned-ably-specification-repo-path>/uts/objects`. Its name (`objects`, `realtime`,
`rest`, …) is the **source module**. A module directory holds many spec files, organised into tiers
(`unit/`, `integration/`, and `integration/proxy/`).

The work happens in two phases:

- **Phase 1 — Selection (Steps A–E below):** a bundled resolver script validates the directory and works
  out the target ably-java package, the spec files, and their class names; you then pick a tier, pick which
  specs, and choose whether to also evaluate.
- **Phase 2 — Per-spec translation (Steps 1–7):** for each selected spec file, derive a Kotlin test.

## Required reading — fetch first

Always fetch [writing-derived-tests.md](https://raw.githubusercontent.com/ably/specification/refs/heads/main/uts/docs/writing-derived-tests.md) first (once per run) — don't rely on memory or the inlined summaries; the manual is updated over time.

---

# Phase 1 — Selection

Path validation, the package mapping, spec discovery, and class-name derivation are all mechanical, so a
bundled script does them — that keeps selection byte-for-byte deterministic instead of relying on the model
to re-eyeball regexes, join paths, and hand-convert `snake_case` → `PascalCase` each run.

> **If `$ARGUMENTS` is empty or blank**, stop and show: `Usage: /uts-to-kotlin <path-to-uts-module-directory>`
> — with the example `/uts-to-kotlin <cloned-ably-specification-repo-path>/uts/objects`
> (the path to a module directory inside a local clone of
> [`ably/specification`](https://github.com/ably/specification)).

## Step A — Resolve the module

Run the resolver on the directory passed in (substitute the real path; the script path is relative to the
ably-java repo root):

```bash
python3 .claude/skills/uts-to-kotlin/scripts/resolve_uts.py "<module-dir>"
```

It prints one JSON object. **If `ok` is `false`, relay `message` to the user and stop** — error codes:
`NOT_A_UTS_MODULE_PATH` (not a `.../uts/<module>` directory), `DIR_NOT_FOUND`, `NO_TIER_DIRS` (no `unit/`
or `integration/`). On success it gives `sourceModule`, `mapped`, `translationNotes`, and a
`tiers` object with one entry per tier (`unit` / `integration` / `proxy`), each carrying `present`,
`sourceDir`, `targetDir`, `package`, `module` (the owning Gradle module — `:java` / `:liveobjects` /
`:uts`), and `specs` (a list of `{file, className}`). Everything downstream
reads from this output — treat it as the single source of truth and don't recompute paths or names by hand.

`translationNotes` is the path to a per-module ably-js → ably-java type/interface map when the module
declares one (its `notes` field in `uts-package-mapping.json`, e.g. `objects` →
`references/objects-mapping.md`), else `null`. When it's non-null, it is **required reading before
Phase 2** — see Step 1.

## Step B — Confirm or create the target mapping

The target dirs come from `uts-package-mapping.json` (alongside this skill); spec and ably-java module names
don't always match (e.g. `objects` → `liveobjects`), which is why it's explicit.

Each tier's mapping value is **one repo-root-relative path** (never a machine-absolute `/Users/...` path).
The resolver derives everything from it: `targetDir` is the path, `package` is the path after
`src/test/kotlin/` with `/` → `.`, and `module` is the owning Gradle module from the path's first segment
(`lib/` → `:java`, `liveobjects/` → `:liveobjects`, `uts/` → `:uts`). Use the resolver's `targetDir` /
`package` / `module` output directly — don't recompute. The `objects` entry is **hand-maintained**: its
tiers live in `:liveobjects`'s own test source set (its specs assert on `:liveobjects` internals only
visible to that module's own tests) under `liveobjects/src/test/kotlin/io/ably/lib/liveobjects/uts/{unit,
integration,proxy}` — a sibling layout that differs from the `--create` template below, so `--create` never
regenerates it.

- **If `mapped` is `true`**: show the resolved `targetDir` for each present tier and ask the user to confirm.
  If they say the mapping is wrong, ask for the correct ably-java module base name and re-run with `--create`
  (below) to overwrite the entry, then re-resolve.
- **If `mapped` is `false`**: there's no mapping for `sourceModule` yet. Ask for the target ably-java module
  base name (default to `sourceModule`; suggest a rename only when the SDK uses different terminology, e.g.
  `objects` → `liveobjects`), then create it deterministically and re-resolve:

  ```bash
  python3 .claude/skills/uts-to-kotlin/scripts/resolve_uts.py "<module-dir>" --create <target>
  ```

  This adds full `lib/`-rooted (`:java`) paths under `packages` —
  `lib/src/test/kotlin/io/ably/lib/uts/unit/<target>`, `.../integration/standard/<target>`, and
  `.../integration/proxy/<target>` — and re-prints the resolved output. **`--create` only scaffolds
  `:java`-hosted modules;** a module whose tiers live in another Gradle module (like `objects` →
  `:liveobjects`, a sibling `uts/{unit,integration,proxy}` layout) still needs a hand-edit afterwards.
  (`<target>` must be a simple module base name — letters, digits, underscore; the script returns
  `BAD_TARGET_NAME` otherwise, so just ask again.)

## Step C — Choose the tier

Offer the tiers whose `present` is `true`. The chosen tier fixes the `targetDir`, `package`, and `specs`
(from Step A) **and** the translation flow Phase 2 uses — don't re-detect any of it per spec:

| Tier | Translation flow |
|---|---|
| **unit** | mocked transport — Steps 3–4 below |
| **integration** (direct sandbox) | real sandbox, no faults — **Direct-sandbox integration tests** section |
| **proxy** | real sandbox + fault injection — **Integration tests** section (proxy subsections) |

## Step D — Choose which specs to translate

The chosen tier's `specs` list (from Step A) is the candidate set — each entry already has its source `file`
and derived `className`. Present it and ask whether to translate **all** of them or a **selected subset**.
Then continue to Step E.

## Step E — Translate only, or also evaluate?

`writing-derived-tests.md` splits the work into **Translation** (always) and **Evaluation** (only
meaningful once the SDK implementation for this module exists). Ask the user which they want, and carry the
answer into Phase 2:

- **Translate only** — generate each test and make it **compile** (Steps 1–5 and the Step 7 review). Don't
  run the tests. Use this when the SDK feature isn't implemented yet, so there's nothing to run against.
- **Translate and evaluate** — all of the above **plus** running the tests and **fixing until they pass**
  (Step 6): work the decision tree, and where the SDK genuinely diverges, gate/adapt the assertion and
  record a deviation. Use this when the implementation exists.

If you can't tell whether the implementation exists, ask the user rather than guessing.

---

# Phase 2 — Per-spec translation

Run this for **each** spec file selected in Step D. **Step 6 only applies in "translate and evaluate" mode
(Step E)** — in "translate only" mode, stop after compiling (Step 5) and reviewing (Step 7), and skip
Step 6 entirely.

When translating several specs, do Steps 1–4 (generate the file) for every spec first, then run Step 5
(compile) once for the whole module, then per file run Step 6 (only if evaluating) and the Step 7 review —
compiling once is faster than per-file and surfaces cross-file issues together. For a single spec, just go
through the steps in order.

## Step 1 — Read the spec (and any module translation notes)

**If Step A reported a non-null `translationNotes`, read that file first (once per run).** UTS specs are
written in a language-agnostic pseudocode that mirrors the *ably-js* API; for modules whose ably-java types
diverge (e.g. `objects` → `liveobjects`, where ably-java is a typed SDK with a partitioned `PathObject` /
`Instance` hierarchy and a `LiveMapValue` write union), the notes map each spec symbol to its ably-java
equivalent. Skipping them yields tests that read like ably-js and won't compile.

Then read the current spec file (the one being translated from the Step D selection). Identify:
- All test cases — each has a structured ID like `realtime/unit/RSA4c2/callback-error-connecting-disconnected-0` and a description
- The protocol used (WebSocket for Realtime, HTTP for REST)
- Any timer usage (`enable_fake_timers`, `ADVANCE_TIME`)
- Any **protocol-variant dimension** — a `PROTOCOL` (`json` / `msgpack`) matrix the spec header says to run "once per variant". In ably-java this becomes a `useBinaryProtocol` `@ParameterizedTest` (see the **Direct-sandbox integration tests** section), not a plain `@Test`.

---

## Step 2 — Output path and package

Don't derive anything here — the resolver (Step A) already produced it. For the chosen tier use its
`targetDir` and `package`, and for the spec being translated use its `className` from that tier's `specs`
list. Write the test to `<targetDir>/<className>.kt` with `package <package>` at the top.

The spec's own `<sub>` grouping (e.g. `connection/`, `channels/`) is **not** reflected in the output — every
test sits directly in `targetDir` (the resolver flattens it). The chosen tier also fixes the translation
flow: **unit** → the rules in Steps 3–4 below; **integration** (direct sandbox) → the **Direct-sandbox
integration tests** section; **proxy** → the proxy subsections of the **Integration tests** section.

---

## Step 3 — Read infrastructure files

> **Orientation — read `uts/README.md` first.** It's the human-readable guide to this module: the
> tier model (unit / direct-sandbox / proxy), the per-tier Gradle tasks, the test-layout convention,
> and a file-map of every infra helper with its public surface (Appendix B). Skim it for the *why* and
> the *what's available*; the per-file list below is the *what to open for exact signatures* before
> writing code.
>
> README §9–§11's walkthroughs use the `:uts` infra smoke tests (`UnitInfraSmokeTest` /
> `IntegrationInfraSmokeTest` / `ProxyInfraSmokeTest`) as examples — treat them as the **structural**
> template only (client wiring, guarded awaits, teardown). They are deliberately NOT spec-derived: no
> `@UTS` marker, several teaching points folded into each method. Every derived test still needs one
> `@Test` with a `@UTS <id>` KDoc per spec Test ID — never copy the smokes' marker-less
> multi-point-per-method shape.

Infrastructure lives in `:uts`'s **main** source set —
`uts/src/main/kotlin/io/ably/lib/uts/infra/` — so other Gradle modules can consume it via
`testImplementation(project(":uts"))`. Kotlin packages are `io.ably.lib.uts.infra.*`, so imports in
generated tests are unaffected. It is split by tier:

- `infra/Utils.kt` — shared async helpers (`awaitState`, `awaitChannelState`, `pollUntil`), package `io.ably.lib.uts.infra`.
- `infra/unit/` — unit-test mocks/factories (`ClientFactories.kt`, `MockWebSocket.kt`, `MockHttpClient.kt`, `FakeClock.kt`, `MockEvent.kt`, the `PendingConnection`/`PendingRequest` pairs, and `Utils.kt` with the `ConnectionDetails { }` builder), package `io.ably.lib.uts.infra.unit`.
- `infra/integration/` + `infra/integration/proxy/` — direct-sandbox + proxy helpers (`SandboxApp.kt`, `ProxyManager.kt`, `ProxySession.kt`) — see the **Integration tests** section.

For a **unit** test, read all files under `infra/unit/` plus `infra/Utils.kt` before generating any code (you need exact method signatures).

**Module-local helpers.** Every module's tiers live in that module's own test source set (the resolver's
`targetDir` / `module`), so also read any module-local helpers alongside the target — they sit in the
tier's `targetDir`. For **objects/unit** (module `:liveobjects`) that's
`liveobjects/src/test/kotlin/io/ably/lib/liveobjects/uts/unit/Helpers.kt` (package
`io.ably.lib.liveobjects.uts.unit`) — `setupSyncedChannel`, the `build_*` message builders (typed `Wire*`
constructions, no JSON/reflection), `STANDARD_POOL_OBJECTS` and the canonical serial constants. Access
convention for that suite: **public-tier specs use only the public API + helpers**; only the five
internal-graph specs (`internal_live_counter`, `internal_live_map`, `object_id`, `objects_pool`,
`parent_references`) and documented deviations may reference `io.ably.lib.liveobjects` `internal` members —
their symbol map is `references/objects-mapping.md` §17.

## Step 4 — Generate the Kotlin test file

Apply the translation rules below, then write the file.

### Client construction

Use `TestRealtimeClient { }` and `TestRestClient { }` from `io.ably.lib`. These are DSL builders that extend `DebugOptions` — all `ClientOptions` fields (`autoConnect`, `recover`, `disconnectedRetryTimeout`, etc.) are settable directly inside the block. The default API key is `"appId.keyId:keySecret"`.

| Pseudocode | Kotlin |
|---|---|
| `Rest(options: ClientOptions(key: "..."))` | `TestRestClient { key = "..." }` |
| `Realtime(options: ClientOptions(key: "...", autoConnect: false))` | `TestRealtimeClient { key = "..."; autoConnect = false }` |
| `Realtime(options: ClientOptions(autoConnect: false))` | `TestRealtimeClient { autoConnect = false }` |
| Installing mocks | `install(mock)` inside the builder block |
| Fake timers | Create `val fakeClock = FakeClock()` before the builder, then call `enableFakeTimers(fakeClock)` inside |

### Mock setup

**Prefer `onConnectionAttempt` callback** over `launch { awaitConnectionAttempt() }` for straightforward connections. Use `respondWithSuccess(message)` to open the socket and deliver the CONNECTED message in a single call.

```kotlin
val mock = MockWebSocket {
    onConnectionAttempt = { conn ->
        conn.respondWithSuccess(ProtocolMessage().apply {
            action = ProtocolMessage.Action.connected
            connectionId = "test-connection-id"
            connectionDetails = ConnectionDetails {
                connectionKey = "test-key"
                maxIdleInterval = 15_000L
                connectionStateTtl = 120_000L
            }
        })
    }
}
val client = TestRealtimeClient {
    autoConnect = false
    install(mock)
}
```

**Use `awaitConnectionAttempt()` only** when different connection attempts need different behaviour (e.g. first attempt succeeds, subsequent ones are refused). In that case, set up the initial connection in a `launch` block before calling `connect()`, then handle reconnections separately:

```kotlin
val mockWs = MockWebSocket()
val client = TestRealtimeClient {
    autoConnect = false
    install(mockWs)
}

launch {
    mockWs.awaitConnectionAttempt().respondWithSuccess(ProtocolMessage().apply { ... })
}

client.connect()
awaitState(client, ConnectionState.connected)

// handle reconnection attempts differently
val refuseJob = launch {
    repeat(10) {
        fakeClock.advance(2.seconds)
        mockWs.awaitConnectionAttempt().respondWithRefused()
    }
}
```

For HTTP:
```kotlin
val mockHttp = MockHttpClient { onRequest = { req -> req.respondWith(200, body) } }
val client = TestRestClient { install(mockHttp) }
```

### Inspecting outgoing frames

`ClientOptionsBuilder` sets `useBinaryProtocol = false`, so the SDK sends JSON text frames. Every outgoing frame is captured as `MockEvent.MessageFromClient` and queued in `awaitNextMessageFromClient()`.

To assert on a message the client sent:

```kotlin
// After triggering the SDK action (e.g. attach):
channelOne.attach()
val msg = mock.awaitNextMessageFromClient()
assertEquals(ProtocolMessage.Action.attach, msg.action)
assertEquals("channel-one", msg.channel)
assertEquals("expected-serial", msg.channelSerial)

// Or filter the full event log when order doesn't matter:
val sent = mock.events
    .filterIsInstance<MockEvent.MessageFromClient>()
    .firstOrNull { it.message.action == ProtocolMessage.Action.attach && it.message.channel == "channel-one" }
assertNotNull(sent)
assertEquals("expected-serial", sent!!.message.channelSerial)
```

### Mock method reference

| Pseudocode | Kotlin |
|---|---|
| `conn.respond_with_success()` | `conn.respondWithSuccess()` |
| `conn.respond_with_success(msg)` | `conn.respondWithSuccess(msg)` |
| `conn.respond_with_refused()` | `conn.respondWithRefused()` |
| `conn.respond_with_timeout()` | `conn.respondWithTimeout()` |
| `conn.respond_with_dns_error()` | `conn.respondWithDnsError()` |
| `mock_ws.send_to_client(msg)` | `mock.sendToClient(msg)` |
| `mock_ws.send_to_client_and_close(msg)` | `mock.sendToClientAndClose(msg)` |
| `mock_ws.simulate_disconnect()` | `mock.simulateDisconnect()` |
| `req.respond_with(200, {...})` | `req.respondWith(200, mapOf(...))` |
| `req.respond_with_timeout()` | `req.respondWithTimeout()` |

### Protocol messages and types

| Pseudocode | Kotlin |
|---|---|
| `ProtocolMessage(action: CONNECTED, ...)` | `ProtocolMessage().apply { action = ProtocolMessage.Action.connected; ... }` |
| `CONNECTED` / `DISCONNECTED` / `ERROR` / `HEARTBEAT` / `ATTACH` / `DETACHED` | `.connected` / `.disconnected` / `.error` / `.heartbeat` / `.attach` / `.detached` |
| `ErrorInfo(code: X, statusCode: Y, message: "...")` | `ErrorInfo("...", Y, X)` — arg order: message, statusCode, code |
| `ConnectionDetails(connectionKey: ..., maxIdleInterval: ..., connectionStateTtl: ...)` | `ConnectionDetails { connectionKey = "..."; maxIdleInterval = ...; connectionStateTtl = ... }` |
| `ConnectionState.connected` etc. | `ConnectionState.connected`, `.disconnected`, `.suspended`, `.failed`, `.connecting`, `.closing`, `.closed` |

### Awaiting state

`AWAIT_STATE client.connection.state == ConnectionState.X` → use the top-level `awaitState()` helper:

```kotlin
awaitState(client, ConnectionState.x)             // default 5s timeout
awaitState(client, ConnectionState.x, 10.seconds)
```

For channels: `awaitChannelState(channel, ChannelState.attached)`.

### Timer control

```kotlin
val fakeClock = FakeClock()
val client = TestRealtimeClient {
    autoConnect = false
    install(mock)
    enableFakeTimers(fakeClock)
}

// Advance time — timer callbacks fire synchronously within advance()
fakeClock.advance(30_000)
// or
fakeClock.advance(30.seconds)
```

After `fakeClock.advance()` inside a coroutine, yield to let newly dispatched coroutines run:

```kotlin
fakeClock.advance(30.seconds)
yield()
```

### Assertions

| Pseudocode | Kotlin |
|---|---|
| `ASSERT x == y` | `assertEquals(y, x)` |
| `ASSERT x IS NOT null` | `assertNotNull(x)` |
| `ASSERT x IS null` | `assertNull(x)` |
| `ASSERT x IS Auth` | `assertIs<Auth>(x)` |
| `ASSERT "key" IN map` | `assertContains(map, "key")` |
| `ASSERT x matches pattern "..."` | `assertTrue(x.matches(Regex("...")))` |
| `ASSERT list CONTAINS_IN_ORDER [a, b, c]` | `val it = list.iterator(); assertEquals(a, it.next()); assertEquals(b, it.next()); ...` |
| `AWAIT expr FAILS WITH error` | `val error = assertFailsWith<AblyException> { expr }; assertEquals(..., error.errorInfo.code)` |
| `ASSERT list.length == N` | `assertEquals(N, list.size)` |

### Test naming and annotation

- KDoc comment immediately above `@Test` using `/** @UTS <uts-id> */` — copy the spec's **full** structured
  id verbatim (e.g. `realtime/unit/RTN4a/some-description-0`; for the objects module it would start
  `objects/unit/…`). Don't hand-build the prefix — use what the spec file declares.
- Method name: backtick string `` `<spec-point> - <description>` `` — the spec point (e.g. `RTN4a`) plus a short description.
- Use `runTest { }` from `kotlinx.coroutines.test` for all async tests

```kotlin
/**
 * @UTS realtime/unit/RTN4a/some-description-0
 */
@Test
fun `RTN4a - description of what is being tested`() = runTest {
    ...
}
```

### File template (unit tier)

This scaffold is for the **unit** tier — it wires the mocked transport (`infra.unit.*`, `MockWebSocket`,
`ConnectionDetails`). For the **integration** (direct sandbox) and **proxy** tiers, start from the
**Proxy integration tests** section instead (`SandboxApp` / `ProxySession` wiring), not from this template.

The `package` is always the resolver's `package` for the chosen tier (Step 2), and the
`io.ably.lib.uts.infra.*` imports stay valid whatever module the tier lands in. For **objects/unit** the
same scaffold applies with the resolver's `io.ably.lib.liveobjects.uts.unit` package, and channel/objects
setup goes through the module-local helpers (`setupSyncedChannel` etc. from the same package — see Step 3)
rather than raw `MockWebSocket` wiring.

```kotlin
package <package>                              // the resolver's package for the chosen tier (Step 2)

import io.ably.lib.uts.infra.unit.*            // TestRealtimeClient/TestRestClient, MockWebSocket, MockHttpClient, FakeClock, CONNECTED_MESSAGE, ConnectionDetails { } builder
import io.ably.lib.uts.infra.awaitState
import io.ably.lib.uts.infra.awaitChannelState  // if testing channels
import io.ably.lib.uts.infra.pollUntil          // if polling on a predicate
import io.ably.lib.realtime.ChannelState        // if testing channels
import io.ably.lib.realtime.ConnectionState
import io.ably.lib.types.ErrorInfo
import io.ably.lib.types.ProtocolMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds  // if using Duration literals

class <className> {

    /**
     * @UTS <uts-id>
     */
    @Test
    fun `<spec-point> - <description>`() = runTest {
        val mock = MockWebSocket {
            onConnectionAttempt = { conn ->
                conn.respondWithSuccess(ProtocolMessage().apply {
                    action = ProtocolMessage.Action.connected
                    connectionId = "test-connection-id"
                    connectionDetails = ConnectionDetails {
                        connectionKey = "test-key"
                        maxIdleInterval = 15_000L
                        connectionStateTtl = 120_000L
                    }
                })
            }
        }
        val client = TestRealtimeClient {
            autoConnect = false
            install(mock)
        }

        client.connect()
        awaitState(client, ConnectionState.connected)

        assertEquals(ConnectionState.connected, client.connection.state)
        client.close()
    }
}
```

---

## Step 5 — Compile

Compile the module the resolver reports for the chosen tier (its `module` field — Step A):

```bash
./gradlew :java:compileTestKotlin          # realtime / rest tiers (module :java)
./gradlew :liveobjects:compileTestKotlin   # objects tiers (module :liveobjects)
```

(`:uts` is no longer a spec-test compile target — it holds only the shared infra + smoke tests.)

Fix any compilation errors and recompile until clean. Common issues:
- Missing imports
- Method names differ from what you read in the mock files (use the exact names from Step 3)
- `ErrorInfo` constructor arg order is `(message, statusCode, code)`

---

## Step 6 — Run tests *(evaluate mode only)*

Skip this whole step in "translate only" mode. In "translate and evaluate" mode, run the test and resolve
every failure via the decision tree below. Each test must end in exactly one of these states — never an
**unexplained** red:
- the spec-correct assertion **passes**; or
- a documented **SDK deviation** — env-gated or adapted, stays green (SDK ≠ spec; fix belongs in the SDK); or
- a documented **UTS spec error** — **fails fast** (the spec is wrong; fix belongs in the spec). This is the
  one acceptable red.

Use the per-tier task of the module the resolver reports for the tier (its `module` field), and the
resolver's `package` + the spec's `className` for the `--tests` filter:

```bash
# realtime / rest unit (module :java)               → io.ably.lib.uts.unit.*
./gradlew :java:runUtsUnitTests --tests "<package>.<className>"

# realtime / rest integration + proxy (module :java) → io.ably.lib.uts.integration.*
./gradlew :java:runUtsIntegrationTests --tests "<package>.<className>"

# objects unit (module :liveobjects)                → io.ably.lib.liveobjects.uts.unit.*
./gradlew :liveobjects:runLiveObjectsUnitTests --tests "<package>.<className>"

# objects integration + proxy (module :liveobjects) → io.ably.lib.liveobjects.uts.{integration,proxy}.*
./gradlew :liveobjects:runLiveObjectsIntegrationTests --tests "<package>.<className>"
```

(`./gradlew :uts:test` now runs only the infra smoke tests — see `uts/README.md`.)

Handle test failures using this decision tree (the **Required reading** doc you fetched up front has the full detail):

```
Test fails
  |
  +-- Does UTS spec match features spec?  (fetch the features spec — see Required reading)
  |     NO  → UTS SPEC ERROR — fix the spec at source, not the test. Record in deviations.md
  |           (UTS Spec Errors) + emit a fail-fast test (below) so it's fixed early.
  |     YES
  |       +-- Does test accurately translate the UTS spec?
  |             NO  → fix the test (no deviation entry needed)
  |             YES → SDK deviation — env-gated skip or adapted assertion (below); record in deviations file
```

### Test patterns for a diagnosed failure

Two patterns are for an **SDK deviation** (both write the spec-correct assertions); the third,
**spec-error fail-fast**, is for a **UTS spec error** and is not a deviation.

**Env-gated skip** (preferred *for a deviation you expect to be fixed*) — test contains spec-correct assertions but is skipped by default:

```kotlin
/**
 * @UTS realtime/unit/RSA4c2/callback-error-connecting-disconnected-0
 */
@Test
fun `RSA4c2 - callback error connecting disconnected`() = runTest {
    // DEVIATION: see deviations.md
    if (System.getenv("RUN_DEVIATIONS") == null) return@runTest

    // ... spec-correct setup and assertions ...
}
```

**Adapted assertion** — assert the SDK's actual behaviour to prevent regressions. **Prefer this over an env-gated skip when the divergence is permanent or intentional** (a running test guards regressions; a permanently-skipped spec assertion verifies nothing):

```kotlin
// DEVIATION: spec requires error code 40106, SDK returns 40160 — see deviations.md
assertEquals(40160, error.errorInfo.code)
```

**Spec-error fail-fast** *(UTS spec error — NOT an SDK deviation)* — when the decision tree's first branch is **NO** (the UTS spec contradicts the features spec, or is internally inconsistent), the spec is the fixable source of truth, so the test must **fail loudly** pointing at the deviation entry, rather than be quietly adapted to green. This is the opposite of the SDK-deviation patterns above (which stay green / assert actual behaviour) — failing fast is the forcing function that gets the spec fixed early.

```kotlin
/**
 * @UTS objects/unit/RTLC7c2/local-source-no-sitetimeserials-0
 */
@Test
fun `RTLC7c2 - LOCAL source does not write siteTimeserials`() = runTest {
    // SPEC ERROR RTLC7c2: replayed ACK serial "t:1:0" contradicts the harness ("ack-0:0").
    // Fix the UTS spec first — see deviations.md (UTS Spec Errors).
    fail("UTS spec error RTLC7c2 — fix the spec first; see deviations.md")
}
```

**Never use the accommodate-both pattern** (accept either spec or SDK behaviour). Every test must assert either spec behaviour or the SDK's actual behaviour — never both at once.

### Deviations file

Append to the deviations file that belongs to the tier's module:
`lib/src/test/kotlin/io/ably/lib/uts/deviations.md` for realtime/rest tiers (module `:java`), or
`liveobjects/src/test/kotlin/io/ably/lib/liveobjects/uts/deviations.md` for objects/unit. Use the manual's
**Recording deviations** entry format and sections. The ably-java-specific mapping: a **UTS Spec Error** (test fails fast — fix in
the spec) goes under the manual's *UTS Spec Errors* section; an **SDK deviation** (env-gated/adapted — fix
in the SDK) goes under *Failing Tests* / *Adapted Tests*.

---

## Step 7 — Review generated output against the spec

The translation isn't done when it compiles — it's done when **every line of the source spec is faithfully
represented** in the Kotlin: each test case, each setup step, each operation, and each assertion. Missing a
single `ASSERT` produces a test that compiles, passes, and silently checks less than the spec demands. This
review runs in **both** modes (it's static — it doesn't need the tests to have run).

### Deterministic faithfulness audit — run the script first

Eyeballing two files for "did I translate every line?" is exactly the kind of mechanical comparison the
model does inconsistently, so a bundled script extracts the ledger for you — same inputs, same report every
time. Run it for each translated spec — don't hand-build paths; use the resolver's output (Step A): the
**source spec file** is that spec's `specs[].file` (already an absolute path) and the **generated Kotlin
file** is the chosen tier's `targetDir` + `className` (Step 2):

```bash
python3 .claude/skills/uts-to-kotlin/scripts/audit_translation.py \
    "<tier.specs[].file>" \
    "<tier.targetDir>/<className>.kt"
```

It prints one JSON report and exits non-zero (2) when there are missing or orphan Test IDs. It does **no**
semantic judgement — it only extracts, deterministically, what you must then reconcile by hand:

- **`idCoverage`** — the spec's `**Test ID**` set vs the Kotlin's `@UTS` tag set.
  - `missingInKotlin` **must be empty.** Each entry is a spec test case with no `@Test` method — implement it.
  - `orphanInKotlin` **must be empty** (or every entry explained) — an `@UTS` tag that no longer matches any
    spec Test ID means a stale or hand-edited tag.
- **`perTest[]`** — for each spec test case, every non-comment code line inside the spec's `pseudo` blocks,
  **grouped by section** (`Setup` / `Test Steps` / `Assertions` / …) in `sections[]` and each line tagged
  `assert` (an `ASSERT*` outcome), `await` (an `AWAIT*` / `EXPECT` wait), or `step` (setup, mock/client
  construction, an operation). `specAsserts` / `specAwaits` are flat convenience views of the first two;
  `specCodeLineTotal` is the size of the spec test. The matching Kotlin method's assertion/await/poll calls
  and their count come alongside.

The audit is a review aid, not a gate — it never crashes, it degrades. It always prints one JSON object
(an `error` field instead of a report if a file is unreadable). If `idCoverage.specCount` is `0` for a file
you know is a real spec, the extractor couldn't find any `**Test ID**` markers (an unusual spec format) —
treat that as "couldn't verify deterministically" and fall back to a manual side-by-side read for that file.

### Coverage check — every test case is present

From the audit's `idCoverage`:
- [ ] `missingInKotlin` is empty (every spec Test ID has an `@Test` with that ID in its `@UTS` KDoc tag)
- [ ] `orphanInKotlin` is empty, or each orphan is a deliberate, explained rename
- [ ] Each method name contains the spec ID and a meaningful description

### Line-by-line completeness — every spec line is translated

Walk the audit's `perTest[].sections` and reconcile **each** extracted spec line against the Kotlin method.
This is the guarantee that no setup step, operation, or assertion was dropped — the whole point of this step:
- [ ] Every `assert` line maps to a concrete Kotlin assertion (`assertEquals`, `assertNotNull`, `assertNull`,
      `assertIs`, `assertTrue`, `assertFailsWith`, …) — not a comment, not a weaker check
- [ ] Every `await` line (state waits **and** awaited operations like `AWAIT channel.attach()` /
      `setup_synced_channel(...)`) is performed via `awaitState` / `awaitChannelState` / `pollUntil` or the
      corresponding SDK call
- [ ] Every `step` line — client/mock construction, `ClientOptions`, installed mocks, channel ops — is
      reflected in the test setup or body. Multi-line spec constructs split across several `step` lines;
      reconcile them as a group.
- [ ] `assertionShortfall > 0` for a test (`summary.testsWithShortfall`) is a **tripwire** — fewer Kotlin
      assertions than spec `ASSERT`s strongly suggests a dropped assertion; open that test and account for
      each one. (A negative shortfall is fine — the SDK mapping often needs *more* Kotlin lines per spec
      `ASSERT`, e.g. number-type normalisation.)

A spec line you intentionally don't translate is only acceptable as a documented deviation (a `// DEVIATION:`
comment + a `deviations.md` entry), never as a silent omission.

### Setup fidelity — preconditions match the spec

For each test case, verify:
- [ ] Client options (`autoConnect`, `recover`, timeouts, etc.) match the spec's `ClientOptions`
- [ ] Mock responses (success / refused / timeout / DNS error / custom messages) match the spec's prescribed network behaviour
- [ ] Timer setup (`enableFakeTimers`, `fakeClock.advance(...)`) matches every `enable_fake_timers` / `ADVANCE_TIME` in the spec
- [ ] Channel operations (attach, detach, publish) are performed in the order the spec requires

### Deviation honesty *(evaluate mode)*

Deviations are discovered by running, so this check applies in evaluate mode. For any place where the
generated test diverges from the spec pseudocode (adapted assertion, env-gated skip, or omitted step):
- [ ] A `// DEVIATION:` comment explains why
- [ ] The deviation is recorded in the tier's module deviations file (see "Deviations file" above: `lib/src/test/kotlin/io/ably/lib/uts/deviations.md` for realtime/rest tiers, `liveobjects/src/test/kotlin/io/ably/lib/liveobjects/uts/deviations.md` for objects tiers)

If you find gaps during this review, fix them, then **re-run the audit script** until `missingInKotlin` /
`orphanInKotlin` are empty and every `perTest` entry reconciles, and re-run Step 5 (compile) — and, in
evaluate mode, Step 6 — before finishing.

---

## Integration tests (direct sandbox + proxy)

Some specs are **integration tests** — they run against the **real Ably sandbox** instead of a mocked transport. Two tiers share one foundation and differ only in *transport*:

- **Direct sandbox** — the client connects straight to the sandbox. Happy-path interop (connect, publish, subscribe, presence); no faults.
- **Proxy** — the client is routed through the [`ably/uts-proxy`](https://github.com/ably/uts-proxy), a programmable HTTP/WebSocket proxy that forwards traffic transparently but can inject faults (dropped connections, modified/injected/delayed frames, error responses) via rules.

**Shared foundation (both tiers, covered once):** `SandboxApp` provisioning + the `@BeforeAll`/`@AfterAll` lifecycle (see **Infrastructure** below), `runTest` test bodies, suspend-function handling, and the `awaitState` / `awaitChannelState` / `pollUntil` waits — never a fixed sleep, since real network is involved (use generous 10–30s timeouts). Only the *wiring* differs per tier; that's what the two tier subsections below cover.

Recognise a **proxy** spec by a reference to `create_proxy_session()`, proxy `rules`, `trigger_action`, `get_log`, or a pointer to `uts/docs/proxy.md`. A spec with none of those is **direct sandbox**.

### Which integration tier?

| Test type | When the spec uses it |
|---|---|
| **Unit test** (mock HTTP/WS — the rest of this skill) | Client-side logic, state machines, request formation, error parsing. Fast, deterministic. |
| **Direct sandbox integration** | Happy-path behaviour (connect, publish, subscribe, presence). No fault injection. |
| **Proxy integration test** | Fault behaviour against the real backend: connection failures, resume, heartbeat starvation, token renewal under network errors, channel error injection. |

### Direct-sandbox integration tests

A **direct-sandbox** spec (no `create_proxy_session`, no rules — just happy-path interop against `nonprod:sandbox`) uses the same `SandboxApp` provisioning and the same `runTest` / `@BeforeAll`+`runBlocking` lifecycle as a proxy test, but **drops all proxy wiring**: no `ProxyManager.ensureProxy()`, no `ProxySession`, no `connectThroughProxy`. The client connects straight to the sandbox host. `ChannelHistoryTest` (realtime) and `ObjectsLifecycleTest` (liveobjects) are the reference examples — read one before translating a direct-sandbox spec.

**Client wiring** — point both transports at the sandbox host (explicit hosts auto-disable fallback hosts, so no `fallbackHosts`):

```kotlin
private fun newClient(useBinaryProtocol: Boolean): AblyRealtime = TestRealtimeClient {
    key = app.defaultKey
    realtimeHost = SandboxApp.sandboxHost   // sandbox.realtime.ably-nonprod.net
    restHost     = SandboxApp.sandboxHost
    this.useBinaryProtocol = useBinaryProtocol
    autoConnect  = false
}
```

**Class docstring** — use a direct-sandbox variant (drop the proxy/fault-injection wording):

```kotlin
/**
 * Direct-sandbox integration test against the Ably Sandbox (`sandbox.realtime.ably-nonprod.net`,
 * via SandboxApp.sandboxHost) — no proxy, no fault injection. Provisions one throwaway SandboxApp
 * for the suite and connects real clients straight to the sandbox.
 */
```

**Protocol variants (`json` / `msgpack`)** — when the spec header declares a `PROTOCOL` dimension and says each test runs once per variant, translate it to a `useBinaryProtocol` `@ParameterizedTest` (this is what the module's `junit-jupiter-params` dependency is for), not a plain `@Test`. The `@UTS` tag and method name stay singular — the parameter expresses the variant:

```kotlin
/** @UTS realtime/integration/RTL10d/history-cross-client-0 */
@ParameterizedTest(name = "useBinaryProtocol={0}")
@ValueSource(booleans = [false, true])   // false = JSON, true = msgpack
fun `RTL10d - history contains messages published by another client`(useBinaryProtocol: Boolean) = runTest {
    val publisher = newClient(useBinaryProtocol)
    // …
}
```

Import `org.junit.jupiter.params.ParameterizedTest` and `org.junit.jupiter.params.provider.ValueSource`. A spec with **no** protocol dimension stays a plain `@Test`.

**Awaiting real server outcomes** — integration specs assert on real backend state, so never sleep; await or poll:

| Pseudocode | Kotlin |
|---|---|
| `AWAIT channel.attach()` | `channel.attach()` then `awaitChannelState(channel, ChannelState.attached, 10.seconds)` |
| `AWAIT channel.publish(name, data)` (await the ack) | wrap the **non-deprecated** `publish(name, data, Callback<PublishResult>)` overload in `suspendCancellableCoroutine` — resume on `onSuccess`, fail on `onError` (the `CompletionListener` overload is deprecated) |
| `poll_until(() => AWAIT channel.history().items.length == N, …)` | `pollUntil(10.seconds, 500.milliseconds) { channel.history(null).items().size == N }` (`history()` is a blocking REST call; `null` = no params) |

Use generous timeouts (10–30s) — real network is involved. Everything else is the shared foundation described at the top of this section; a direct-sandbox test just skips the proxy-only subsections (`ProxySession`, rule factories, the event log).

### Infrastructure

Three helpers live under `uts/src/main/kotlin/io/ably/lib/uts/infra/integration/`. **Read the ones your tier uses before translating an integration spec** — they hold the exact method signatures. `SandboxApp` serves **both** tiers; `ProxyManager` and `ProxySession` are **proxy-only**.

- **`ProxyManager`** (`infra/integration/proxy/ProxyManager.kt`, package `io.ably.lib.uts.infra.integration.proxy`) — downloads/starts the shared `uts-proxy` process. Call `ProxyManager.ensureProxy()` once per suite in setup.
- **`ProxySession`** (`infra/integration/proxy/ProxySession.kt`, same package) — one programmable session wrapping the proxy control API; also defines the `connectThroughProxy` extension and the rule-builder helpers.
- **`SandboxApp`** (`infra/integration/SandboxApp.kt`, package `io.ably.lib.uts.infra.integration`) — provisions/deletes a sandbox test app from the shared `test-app-setup.json` in ably-common. `SandboxApp.create()` returns a `SandboxApp` with `appId`, `defaultKey`, and `keys` (`defaultKey` is a full-capability `appId.keyId:keySecret`); `app.delete()` tears it down. Provision in suite setup, delete in teardown. Also owns the single upstream sandbox host constant `SandboxApp.sandboxHost` (`sandbox.realtime.ably-nonprod.net`, the resolved `nonprod:sandbox` endpoint) — the default target of every `ProxySession` (both `realtimeHost` and `restHost`), and what direct-sandbox clients set `realtimeHost` / `restHost` from.

Import what the tier needs: a **direct-sandbox** test imports `io.ably.lib.uts.infra.integration.SandboxApp` plus `io.ably.lib.uts.infra.unit.TestRealtimeClient` and `io.ably.lib.uts.infra.{awaitState, pollUntil}`; a **proxy** test additionally imports `io.ably.lib.uts.infra.integration.proxy.{ProxyManager, ProxySession, connectThroughProxy}`.

`ensureProxy()`, the `ProxySession` methods, and the `SandboxApp` methods are all **`suspend`** functions. Per-test bodies use `runTest { }`; JUnit5 `@BeforeAll`/`@AfterAll` (with `@TestInstance(Lifecycle.PER_CLASS)`) wrap their suspend calls in `runBlocking { }`.

### Proxy test class docstring

Give every proxy integration test class this KDoc:

```kotlin
/**
 * Proxy integration test against Ably Sandbox endpoint.
 *
 * Uses the programmable uts-proxy to inject transport-level faults while the
 * SDK communicates with the real Ably backend. See
 * `uts/docs/proxy.md` for proxy infrastructure details.
 */
```

### Session lifecycle

`create_proxy_session(endpoint: "nonprod:sandbox", rules: [...])` → `ProxySession.create(...)`. The sandbox is already the default target, so an empty rule set is just:

```kotlin
val session = ProxySession.create(rules = emptyList())
```

Always close the session (and the client) in a `finally` block:

```kotlin
ProxyManager.ensureProxy()
val session = ProxySession.create(rules = listOf(
    wsConnectRule(action = mapOf("type" to "refuse_connection"), count = 2),
))
try {
    val client = TestRealtimeClient {
        key = sandboxKey
        connectThroughProxy(session)   // routes realtime + REST through the proxy
        autoConnect = false
    }
    client.connect()
    awaitState(client, ConnectionState.connected)
    // … scenario …
    client.close()
} finally {
    session.close()
}
```

| Pseudocode | Kotlin |
|---|---|
| `create_proxy_session(endpoint: "nonprod:sandbox", rules: [...])` | `ProxySession.create(rules = listOf(...))` |
| `session.add_rules(rules, position: "prepend")` | `session.addRules(rules, position = "prepend")` |
| `session.trigger_action({ type: "disconnect" })` | `session.triggerAction(mapOf("type" to "disconnect"))` |
| `session.get_log()` | `session.getLog()` |
| `session.close()` | `session.close()` |
| `session.proxy_port` / `session.proxy_host` | `session.proxyPort` / `session.proxyHost` |

### Connecting through the proxy

Call `connectThroughProxy(session)` inside the client builder block. It is a `ClientOptionsBuilder` extension (in `ProxySession.kt`) that wires the SDK through the proxy:

```kotlin
val client = TestRealtimeClient {
    key = sandboxKey
    connectThroughProxy(session)
    autoConnect = false
}
```

ably-java has **no `endpoint` ClientOptions field**; `connectThroughProxy` sets the discrete host fields for you:

| Proxy-def option | What `connectThroughProxy` sets |
|---|---|
| `endpoint: "localhost"` | `realtimeHost` **and** `restHost` = `session.proxyHost` (`"localhost"`) |
| `port: proxy_port` | `port = session.proxyPort` |
| `tls: false` | `tls = false` |
| `useBinaryProtocol: false` | already the `ClientOptionsBuilder` default — left untouched |

It does **not** touch `autoConnect`, so set that yourself. Setting explicit hosts disables fallback hosts automatically, so don't add `fallbackHosts`.

### Auth in proxy tests

A spec that needs to observe (re-)authentication uses an `authCallback`. Where the pseudocode "generates a JWT from the key parts", the idiomatic ably-java equivalent is a **locally-signed `TokenRequest`** from the same sandbox key — no JWT library required. The realtime client exchanges it for a token through the proxy:

```kotlin
val tokenSigner = AblyRest(app.defaultKey)          // local signing only; no network
val authCallbackCount = AtomicInteger(0)
val authCallback = Auth.TokenCallback { params ->
    authCallbackCount.incrementAndGet()
    tokenSigner.auth.createTokenRequest(params, null)
}
val client = TestRealtimeClient {
    this.authCallback = authCallback
    connectThroughProxy(session)
    autoConnect = false
}
```

`TokenParams`/`TokenRequest` are nested in `io.ably.lib.rest.Auth`; `AuthDetails` is nested in `io.ably.lib.types.ProtocolMessage`.

### Rule factory helpers

Build rules with the factory helpers in `ProxySession.kt` rather than raw map literals. Rules are evaluated in order, first match wins, unmatched traffic passes through, and `times` auto-removes a rule after N firings.

| Match condition | Kotlin |
|---|---|
| `{ "type": "ws_connect", "count": 2 }` | `wsConnectRule(action = ..., count = 2)` |
| `{ "type": "ws_connect", "queryContains": { "resume": "*" } }` | `wsConnectRule(action = ..., queryContains = mapOf("resume" to "*"))` |
| `{ "type": "ws_frame_to_client", "action": "ATTACHED", "channel": "c" }` | `wsFrameToClientRule(action = ..., messageAction = 11, channel = "c")` |
| `{ "type": "ws_frame_to_server", "action": "ATTACH", "channel": "c" }` | `wsFrameToServerRule(action = ..., messageAction = 10, channel = "c")` |
| `{ "type": "http_request", "method": "POST", "pathContains": "/keys/" }` | `httpRequestRule(action = ..., method = "POST", pathContains = "/keys/")` |

`messageAction` is the protocol action **number** (e.g. `4` CONNECTED, `6` DISCONNECTED, `9` ERROR, `10` ATTACH, `11` ATTACHED) — see the action-number table in `proxy.md`.

Actions are passed as `mapOf(...)`, e.g.:

```kotlin
mapOf("type" to "refuse_connection")
mapOf("type" to "disconnect")
mapOf("type" to "suppress")
mapOf("type" to "delay", "delayMs" to 2000)
mapOf("type" to "inject_to_client", "message" to mapOf("action" to 6))
mapOf("type" to "http_respond", "status" to 401, "body" to mapOf(...))
```

### Verifying the event log

`getLog()` returns a typed `List<Event>`. Access fields via dot notation (`it.type`, `it.direction`, `it.queryParams`, `it.status`); numeric fields (`status`, `closeCode`) are already `Int?`. The raw protocol message is exposed as `Event.message` (a Gson `JsonObject?`) — introspect it with `it.message?.get("action")?.asInt`.

```kotlin
val log = session.getLog()
val wsConnects = log.filter { it.type == "ws_connect" }
assertTrue(wsConnects.size >= 2)
val queryParams = wsConnects.first().queryParams
assertNotNull(queryParams["resume"])
```

### Conventions

1. Each test references the spec point and (where it exists) the corresponding unit test.
2. `ProxyManager.ensureProxy()` and sandbox-app provisioning go in `@BeforeAll` / suite setup; clean up in `@AfterAll`.
3. Each test creates its own `ProxySession` and closes it (and the client) in `finally`.
4. Use `awaitState` / `awaitChannelState` for state assertions; verify via SDK state **and** the proxy log where useful.
5. Use generous timeouts (10–30s) — real network is involved: `awaitState(client, ConnectionState.connected, 15.seconds)`.
6. Don't set `fallbackHosts`; explicit hosts already disable fallbacks.

Step 5 (compile) still applies; Step 6 (run) applies only in evaluate mode (Step E). Note that proxy tests hit the live sandbox and download the proxy binary on first run, so they are slower and require network access.
