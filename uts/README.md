# UTS in ably-java — A Human-Readable Guide

> A practical, end-to-end explanation of the **Universal Test Specification (UTS)** and how it is
> realised in the `ably-java` repository. Written for a developer who has never touched UTS before
> and needs to understand *what it is*, *why it exists*, and *exactly how the shared Kotlin
> infrastructure in the `:uts` module — plus the reference smoke tests that exercise it — makes the
> unit, direct-sandbox, and proxy-integration tiers work*.

---

## Table of Contents

1. [Introduction: What is UTS?](#1-introduction-what-is-uts)
2. [The Three Test Tiers](#2-the-three-test-tiers)
3. [The UTS Documents (the source of truth)](#3-the-uts-documents-the-source-of-truth)
4. [The Java Setup: the `uts/` module](#4-the-java-setup-the-uts-module)
5. [How a Test Reaches the SDK: the hook points](#5-how-a-test-reaches-the-sdk-the-hook-points)
6. [Unit-Test Infrastructure (mocked transports)](#6-unit-test-infrastructure-mocked-transports)
7. [Proxy-Integration Infrastructure (real backend + fault injection)](#7-proxy-integration-infrastructure-real-backend--fault-injection)
8. [Shared Async Helpers](#8-shared-async-helpers)
9. [Walkthrough: the Unit Smoke Test (`UnitInfraSmokeTest`)](#9-walkthrough-the-unit-smoke-test-unitinfrasmoketest)
10. [Walkthrough: the Direct-Sandbox Smoke Test (`IntegrationInfraSmokeTest`)](#10-walkthrough-the-direct-sandbox-smoke-test-integrationinfrasmoketest)
11. [Walkthrough: the Proxy Smoke Test (`ProxyInfraSmokeTest`)](#11-walkthrough-the-proxy-smoke-test-proxyinfrasmoketest)
12. [Deviations: when the SDK disagrees with the spec](#12-deviations-when-the-sdk-disagrees-with-the-spec)
13. [How to Run the Tests](#13-how-to-run-the-tests)
14. [Quick Reference / Cheat-Sheet](#14-quick-reference--cheat-sheet)
15. [Appendix A: Request-Flow Diagrams](#15-appendix-a-request-flow-diagrams)
16. [Appendix B: Per-File API Reference](#16-appendix-b-per-file-api-reference)

---

## 1. Introduction: What is UTS?

**UTS (Universal Test Specification)** is Ably's language-neutral catalogue of tests for its client
SDKs. The problem it solves: Ably ships many SDKs (JavaScript, Dart, Kotlin/Java, Swift, Go, …), and
every one of them must obey the *same* behavioural contract — the **Ably features spec**
(`specification/specifications/features.md`, whose requirements are tagged `RSC7`, `RTN15a`, `RTL4f`,
etc.). Without a shared test definition, each SDK would re-invent its own tests, drift apart, and
leave gaps.

UTS fixes this by separating **what to test** from **how to test it in a given language**:

```text
        ┌──────────────────────────────┐
        │   Ably features spec          │   ← the ultimate authority (RSC*, RTN*, RTL* …)
        │   (features.md)               │
        └──────────────┬───────────────┘
                       │ distilled into portable test specs
                       ▼
        ┌──────────────────────────────┐
        │   UTS test specs (.md)        │   ← language-neutral pseudocode, one file per feature
        │   "writing-test-specs"        │     e.g. realtime/unit/connection/connection_recovery_test.md
        └──────────────┬───────────────┘
                       │ translated ("derived") per SDK
                       ▼
        ┌──────────────────────────────┐
        │   Derived tests               │   ← concrete, runnable tests in the SDK's language
        │   (Kotlin, in ably-java)      │     spec suites: :java / :liveobjects;
        └──────────────────────────────┘     shared infra + smoke examples: :uts
```

Three concepts you will see constantly:

| Term | Meaning |
|------|---------|
| **Spec point** | A tagged requirement in the features spec, e.g. `RTN16g`, `RTN22`, `RTL4f`. Test names embed these. |
| **UTS spec** | A markdown file of portable pseudocode describing the setup, steps, and assertions for one feature. The *source of truth for what to test.* |
| **Derived test** | A faithful translation of a UTS spec into a real test in a specific SDK/language. These live in the owning module's test source set (`:java` for realtime/rest, `:liveobjects` for objects); the shared infra they use lives in `:uts`. |
| **Deviation** | A documented case where the SDK's actual behaviour diverges from the spec. Recorded in the owning module's `deviations.md` (`:java` and `:liveobjects` — see §12). |

The golden rule (from [`writing-derived-tests.md`](https://github.com/ably/specification/blob/main/uts/docs/writing-derived-tests.md)): **translate the UTS spec faithfully** — same
structure, same assertions, same naming — don't optimise or skip steps. Every derived test carries a
`// UTS: <id>` (here `@UTS …`) comment linking it back to its spec.

---

## 2. The Three Test Tiers

UTS divides tests into three tiers by *what infrastructure they need* and *what confidence they
give*. Understanding this split is the key to understanding the whole `uts/` module, because the
three example tests this guide walks through span all three tiers.

| Tier | Transport | Backend | Purpose | Example in this repo |
|------|-----------|---------|---------|----------------------|
| **Unit** | **Mocked** (`MockWebSocket`, `MockHttpClient`) | none | Client-side logic: state machines, request formation, response parsing, timer behaviour. Fast & deterministic. | `unit/UnitInfraSmokeTest.kt` |
| **Direct sandbox integration** | Real network | Real Ably sandbox | Happy-path interop: connect, publish, subscribe. No fault injection. | `integration/standard/IntegrationInfraSmokeTest.kt` |
| **Proxy integration** | Real network **through a programmable proxy** | Real Ably sandbox | Fault behaviour: dropped connections, injected errors, timeouts, re-auth. | `integration/proxy/ProxyInfraSmokeTest.kt` |

The three examples above are `:uts`'s own **tier smoke tests** (§9–§11) — the reference shapes this
guide walks through. The real, spec-derived suites live **by module**, in the module that owns the
code under test: realtime/rest under `:java` (`lib/src/test/kotlin/io/ably/lib/uts/…`), objects under
`:liveobjects` (`liveobjects/.../uts/…`). `:uts`'s own test tree holds only the tier smoke examples;
so a feature's tests always sit with the SDK code they exercise (see §4.2 "Where every module's UTS
tests live").

Key principles (from [`integration-testing.md`](https://github.com/ably/specification/blob/main/uts/docs/integration-testing.md)):

- **Integration tests do not replace unit tests.** A spec point covered by a proxy test should
  *also* have a unit test. The unit test proves the client logic; the proxy test proves the client
  and the real server agree.
- **Proxy tests prefer "late fault injection".** Let the real handshake complete against the real
  server, *then* inject the fault as the final interaction. This maximises how much of the test
  exercises genuine client-server behaviour (otherwise you've just written a slow unit test).
- **Proxy tests always use JSON** (`useBinaryProtocol = false`). ably-java *does* implement msgpack (it's
  the default — `ClientOptions.useBinaryProtocol = true`); the real constraint is the proxy, which only
  understands **text** WebSocket frames and so can't inspect or modify binary msgpack. The tests therefore
  force JSON regardless of SDK support
  ([`integration-testing.md`](https://github.com/ably/specification/blob/main/uts/docs/integration-testing.md) §Protocol Variants,
  [`docs/proxy.md`](https://github.com/ably/specification/blob/main/uts/docs/proxy.md)).

---

## 3. The UTS Documents (the source of truth)

These four documents live in the **specification repo** at
[`uts/docs/`](https://github.com/ably/specification/blob/main/uts/docs/) (in a local
`ably-specification` checkout, under `specification/uts/docs/`). They are the policy/authoring guides;
the Kotlin code in this repo is the *implementation* of what they describe. Each title below links to
the file on GitHub.

### 3.1 [`writing-test-specs.md`](https://github.com/ably/specification/blob/main/uts/docs/writing-test-specs.md) — how to author a portable UTS spec
The authoring manual. Defines:
- **Test types** (unit / integration / proxy) and when each applies.
- **Test IDs** — the format `<category>/<spec-point>/<descriptive-name>-<n>`, e.g.
  `realtime/proxy/RTN22/server-initiated-reauth-0`. These IDs are what appear in the `@UTS`
  comments in the Kotlin tests.
- **Mock infrastructure pseudocode interfaces** — `MockHttpClient`, `MockWebSocket`,
  `PendingConnection`, `PendingRequest`, with `respond_with_success()`, `send_to_client()`,
  `simulate_disconnect()`, etc. The Kotlin classes in `uts/infra/unit/` are direct realisations of
  these interfaces.
- **Handler vs await patterns** for mocks (see §6).
- **WebSocket closing semantics** — the crucial rule: `send_to_client_and_close()` for
  DISCONNECTED / connection-level ERROR (server closes the socket); `send_to_client()` for a
  channel-level ERROR (connection stays open).
- **Anti-flake conventions** — no fixed `WAIT`s; use polling, `AWAIT_STATE`, fake timers, and the
  **record-and-verify** pattern (`CONTAINS_IN_ORDER`) for transient states.

### 3.2 [`writing-derived-tests.md`](https://github.com/ably/specification/blob/main/uts/docs/writing-derived-tests.md) — how to translate a spec into a real SDK test
The translation manual. Two phases:
1. **Translation** (always): faithfully render the spec into the target language; map pseudocode to
   the SDK's API and test framework; flag ambiguities in comments; make sure it compiles.
2. **Evaluation** (when an implementation exists): run the test and, if it fails, work the
   **decision tree**:
   - *Is the UTS spec wrong* (contradicts features spec)? → fix the test, record a **UTS spec error**.
   - *Is the translation wrong*? → fix the test, no deviation.
   - *Is the SDK non-compliant*? → keep the spec-correct assertion but adapt/gate it, and record a
     **deviation**.
- Defines the **env-gated skip** pattern (`RUN_DEVIATIONS`) — the test holds the *spec-correct*
  assertion but only runs it when the env var is set, so normal runs stay green while each deviation
  stays individually reproducible. This is exactly what `ConnectionRecoveryTest` uses for RTN16f.

### 3.3 [`integration-testing.md`](https://github.com/ably/specification/blob/main/uts/docs/integration-testing.md) — the policy for integration & proxy tests
Defines what *deserves* an integration test (request/response interop, error interop, data
round-trips, stateful protocol sequences), the directory layout, sandbox provisioning, proxy session
lifecycle, timeout strategy, and the **late-fault-injection** philosophy. The `integration/proxy/`
segregation exists because proxy tests have different infra needs, CI cadence, and failure modes.

### 3.4 [`completion-status.md`](https://github.com/ably/specification/blob/main/uts/docs/completion-status.md) — the coverage matrix
A big table mapping every features-spec group (`RSC`, `RTN`, `RTL`, `RTP`, …) to the UTS specs that
cover it, with a per-tier summary (`unit:✓ proxy:✓`). This is the tracker for "what's done and
what's missing". The reference tests this guide walks through correspond to these rows:
- `RTN16` (connection recovery) → unit spec `connection_recovery_test.md` →
  **`ConnectionRecoveryTest.kt`** (`:java`, `lib/src/test/kotlin/io/ably/lib/uts/unit/realtime/`).
- `RTL10d` (channel history) → direct-sandbox spec
  `realtime/integration/channel_history_test.md` → **`ChannelHistoryTest.kt`**
  (`:java`, `.../integration/standard/realtime/`).
- `RTN22` / `RTC8a` (server-initiated re-auth) → proxy spec
  `realtime/integration/proxy/auth_reauth.md` → **`AuthReauthTest.kt`**
  (`:java`, `.../integration/proxy/realtime/`).

> There is also a fifth, *referenced* spec:
> [`docs/proxy.md`](https://github.com/ably/specification/blob/main/uts/docs/proxy.md)
> (in the spec repo under `uts/docs/`). It defines the proxy's control API, rule format,
> action types, and the **protocol message action-number table** (CONNECTED=4, ATTACH=10, AUTH=17,
> …). The Kotlin `ProxySession` is the client for exactly that API.

---

## 4. The Java Setup: the `uts/` module

The `uts/` directory is a **standalone Gradle module** (`include("uts")` in `settings.gradle.kts`)
that is the repo's **shared UTS test-support library** plus a small set of reference examples. Its
**main** source set *is* the shared test infrastructure — `uts/src/main/kotlin/io/ably/lib/uts/infra/`
— so any other Gradle module consumes it with a plain `testImplementation(project(":uts"))` (this
module's own tests see it automatically). Its **test** source set holds only the three tier **smoke
tests** (§9–§11): the permanent acceptance gate for the infra and the worked examples this guide
teaches from.

Two things this means — and a correction to how the module used to be described. First, the infra is
**this module's main/production code**, not a `testFixtures` variant and not test-only: `:uts`'s main
artifact *is* the infra. Second, `:uts` does **not** host the spec-derived UTS suites — those live in
their owning modules (`:java` for realtime/rest, `:liveobjects` for objects; see §4.2). `:uts` keeps
only the infra and the tier smoke examples.

### 4.1 `uts/build.gradle.kts`
```kotlin
plugins {
    `java-library`                          // provides the `api` configuration (kotlin.jvm alone
                                            // applies only the plain `java` plugin)
    alias(libs.plugins.kotlin.jvm)          // Kotlin/JVM — `java-test-fixtures` is REMOVED
}

java {
    // Declare Java-8 outgoing variants so :java's 8-requesting configurations can consume
    // project(":uts").
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    // The shared UTS infra (src/main/kotlin/io/ably/lib/uts/infra/**) — this module's main artifact,
    // consumed elsewhere via testImplementation(project(":uts")). `api` for types that appear in
    // infra signatures; `implementation` for internals. Invariant I1: :uts never depends on
    // :liveobjects.
    api(project(":java"))                    // the SDK + its types (DebugOptions, ProtocolMessage, …)
    api(project(":network-client-core"))     // HttpEngine / WebSocketEngine SPIs the mocks implement
    implementation(libs.ktor.client.core)    // proxy infra uses ktor internally — must NOT leak to consumers
    implementation(libs.ktor.client.cio)

    // The UTS test toolkit — exported (api) so any module consuming the infra via
    // testImplementation(project(":uts")) transitively gets JUnit 5, the kotlin.test Jupiter binding,
    // and coroutines (runTest etc.). :uts's own smoke tests inherit it from main's api — nothing to declare.
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter)
    api(libs.junit.jupiter.params)                  // @ParameterizedTest / @ValueSource
    api(kotlin("test-junit5"))
    api(libs.coroutine.core)
    api(libs.coroutine.test)                        // runTest, virtual time
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()                       // JUnit 5
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    // Propagate a local proxy-build override (see ProxyManager): -Duts.proxy.localPath=… or
    // $UTS_PROXY_LOCAL_PATH.
    systemProperty(
        "uts.proxy.localPath",
        providers.systemProperty("uts.proxy.localPath")
            .orElse(providers.environmentVariable("UTS_PROXY_LOCAL_PATH"))
            .getOrElse(""),
    )
}

tasks.register<Test>("runUtsUnitTests")        { filter { includeTestsMatching("io.ably.lib.uts.unit.*") } }
tasks.register<Test>("runUtsIntegrationTests") { filter { includeTestsMatching("io.ably.lib.uts.integration.*") } }

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_1_8) } }
```
Takeaways:
- `:uts` is a `java-library` + `kotlin.jvm` module. `java-test-fixtures` is **gone** — the infra is
  plain `src/main`, so consumers use `testImplementation(project(":uts"))` (no `testFixtures(...)`
  wrapper). `java-library` is what now supplies the `api` configuration.
- The module compiles to **Java 8** bytecode (source/target + `jvmTarget = JVM_1_8`), so `:java`
  (which requests Java-8 variants) can consume it. **mockk is not a dependency** — the infra imports
  no test library at all.
- It depends on `:java` (the SDK under test) and `:network-client-core` (the pluggable transport SPIs
  the mocks implement), both via `api` because they appear in infra signatures.
- Tests are **Kotlin + JUnit 5**, using **kotlinx.coroutines** for async control and **Ktor** for the
  sandbox REST API and proxy control API. `junit-jupiter-params` (version from the JUnit BOM) adds
  **`@ParameterizedTest`** for the protocol-variant integration tests (§10.3).
- `runUtsUnitTests` / `runUtsIntegrationTests` are package-filtered `Test` tasks (§13). The
  `--add-opens java.base/java.time` and `java.base/java.lang` flags grant the test runtime reflective
  access into those JDK packages, mirroring `java/build.gradle.kts`.
- A system property carries an optional path to a **locally built** proxy binary (so you can test
  against an unreleased proxy).

### 4.2 Directory layout

Everything lives under the `io.ably.lib.uts` package, split cleanly between the **main** source set —
the shared **infrastructure** (`infra/`, no `@Test`s) — and the **test** source set — the three tier
**smoke tests**. The infra is organised by tier: `infra/unit/` for mocked transports, and
`infra/integration/` for real-backend helpers — the latter with an `infra/integration/proxy/`
sub-package for the fault-injecting proxy — plus one shared `infra/Utils.kt` serving every tier:

```text
uts/src/main/kotlin/io/ably/lib/uts/         # ── shared infra, consumed via testImplementation(project(":uts")) ──
└── infra/                               # ── TEST INFRASTRUCTURE (no @Test methods) ──
    ├── Utils.kt                         #   awaitState / awaitChannelState / pollUntil (shared)
    │
    ├── unit/                            #   UNIT infra (mocked transports)
    │   ├── ClientFactories.kt           #     TestRealtimeClient / TestRestClient / ClientOptionsBuilder
    │   ├── MockWebSocket.kt             #     fake WS transport + WebSocketMockConfig + CONNECTED_MESSAGE
    │   ├── MockWebSocketEngineFactory.kt#     plugs the mock into the SDK's WebSocketEngine SPI
    │   ├── MockHttpClient.kt            #     fake HTTP engine + HttpMockConfig
    │   ├── MockHttpEngine.kt            #     plugs the mock into the SDK's HttpEngine SPI
    │   ├── MockEvent.kt                 #     sealed log of everything on a mock transport
    │   ├── PendingConnection.kt         #     interface: a connection attempt awaiting a response
    │   ├── DefaultPendingConnection.kt  #     WS implementation of PendingConnection
    │   ├── PendingRequest.kt            #     interface: an in-flight HTTP request awaiting a response
    │   ├── DefaultPendingRequest.kt     #     HTTP implementation of PendingRequest
    │   ├── FakeClock.kt                 #     virtual clock + virtual timers (deterministic time)
    │   └── Utils.kt                     #     ConnectionDetails { } builder (reflective constructor)
    │
    └── integration/                     #   INTEGRATION infra (real backend)
        ├── SandboxApp.kt                #     provisions/deletes a sandbox app
        └── proxy/
            ├── ProxyManager.kt          #       downloads/launches the uts-proxy binary
            └── ProxySession.kt          #       proxy session: rules, actions, log + connectThroughProxy

uts/src/test/kotlin/io/ably/lib/uts/         # ── the tier SMOKE TESTS (infra acceptance + worked examples) ──
├── unit/
│   └── UnitInfraSmokeTest.kt            #   ← UNIT smoke: mock WS + HTTP + FakeClock (§9)
└── integration/
    ├── standard/
    │   └── IntegrationInfraSmokeTest.kt #   ← DIRECT-SANDBOX smoke: SandboxApp (§10)
    └── proxy/
        └── ProxyInfraSmokeTest.kt       #   ← PROXY smoke: ProxyManager + ProxySession (§11)
```

The mental model: **`infra/unit/` powers the unit tier, `infra/integration/` powers both integration
kinds (`standard` + `proxy`), and `infra/Utils.kt` serves all of them.** The top-level `unit/` ↔
`infra/unit/` and `integration/` ↔ `infra/integration/` pairing is what the `runUtsUnitTests` /
`runUtsIntegrationTests` Gradle tasks key off (§13) — `runUtsIntegrationTests` covers **both**
`integration/standard/` and `integration/proxy/`.

#### Where every module's UTS tests live

The infra is shared, but the actual UTS suites live **in the module that owns the code under test**;
`:uts` itself holds only the infra and one smoke example per tier:

| Module | UTS tests | Location | Run with |
|---|---|---|---|
| `:java` | realtime (and future rest) — unit, integration, proxy | `lib/src/test/kotlin/io/ably/lib/uts/{unit, integration/standard, integration/proxy}/realtime/` | `:java:runUtsUnitTests` / `:java:runUtsIntegrationTests` |
| `:liveobjects` | objects — unit, integration, proxy | `liveobjects/src/test/kotlin/io/ably/lib/liveobjects/uts/{unit, integration, proxy}/` | `:liveobjects:runLiveObjectsUnitTests` / `:liveobjects:runLiveObjectsIntegrationTests` |
| `:uts` | **none** — shared infra + one smoke test per tier | `uts/src/main/.../infra/**` + `uts/src/test/.../{unit, integration/standard, integration/proxy}/` | `:uts:runUtsUnitTests` / `:uts:runUtsIntegrationTests` |

Every consuming module gets the infra via `testImplementation(project(":uts"))`. The objects suites
additionally reach `:liveobjects`-internal CRDT state (`InternalLiveMap`/`InternalLiveCounter`/
`ObjectsPool`), which is why they live in `:liveobjects`'s own test source set — including the objects
**unit** tier, whose internal-graph specs cannot be expressed from outside that module. See
`.claude/skills/uts-to-kotlin/uts-package-mapping.json` and `MOVE_COMMON_INFRA/`.

> **Future work — publishing `:uts` for out-of-repo consumers.** Promoting the infra into `:uts`'s
> own main source set (rather than a separate `:test-support` module) leaves it *publishable later
> without restructuring* — but publishing is a deliberate commitment, not a default. The trigger is a
> consumer outside this repo, in practice the **Chat SDK**: if Chat lands in-repo it just consumes
> `testImplementation(project(":uts"))` like every other module and nothing changes; if Chat is a
> separate repo, decide *deliberately* between publishing `:uts` as a versioned artifact, source-copying
> the infra, or a shared git submodule. Publishing turns test infra into a maintained artifact with a
> release cadence and compatibility expectations, so that choice (and the release-train / API-hygiene
> checklist it implies) stays gated and unscheduled until the trigger fires.

---

## 5. How a Test Reaches the SDK: the hook points

A test can only mock transports because the SDK was designed with **pluggable seams**. They live on
`io.ably.lib.debug.DebugOptions` (a subclass of `ClientOptions`):

```java
public class DebugOptions extends ClientOptions {
    public HttpEngine httpEngine;                       // ← MockHttpClient installs here
    public WebSocketEngineFactory webSocketEngineFactory; // ← MockWebSocket installs here
    public Clock clock;                                 // ← FakeClock installs here
    …
}
```

and the `Clock` interface:

```java
public interface Clock {
    long currentTimeMillis();
    long nanoTime();
    AblyTimer newTimer(String name);                    // every SDK timer is created through this
    void waitOn(Object target, long timeout) throws InterruptedException; // every blocking wait
}
```

So the recipe is:
- Want to fake the **WebSocket**? Set `webSocketEngineFactory` to a factory that produces a mock
  engine.
- Want to fake **HTTP**? Set `httpEngine` to a mock engine.
- Want to control **time** (timeouts, retries, TTL expiry) deterministically? Set `clock` to a
  `FakeClock`.

The `ClientOptionsBuilder` (next section) wraps all three so tests never touch `DebugOptions`
directly.

---

## 6. Unit-Test Infrastructure (mocked transports)

### 6.1 The client builder — `ClientFactories.kt`
Every unit test builds its client through a tiny DSL:

```kotlin
class ClientOptionsBuilder : DebugOptions("appId.keyId:keySecret") {
    init { useBinaryProtocol = false }                  // JSON so mocks can decode frames
    fun install(mock: MockWebSocket) = mock.installOn(this)
    fun install(mock: MockHttpClient) = mock.installOn(this)
    fun enableFakeTimers(fakeClock: FakeClock) { clock = fakeClock }
}

fun TestRealtimeClient(block: ClientOptionsBuilder.() -> Unit): AblyRealtime =
    AblyRealtime(ClientOptionsBuilder().apply(block))
fun TestRestClient(block: ClientOptionsBuilder.() -> Unit): AblyRest =
    AblyRest(ClientOptionsBuilder().apply(block))
```

- It seeds a **dummy API key** (`appId.keyId:keySecret`) — fine, because unit tests never hit a real
  server and tokens are opaque.
- It forces **JSON** so the mock can parse protocol frames.
- `install(mock)` / `enableFakeTimers(clock)` wire the seams from §5.

A typical unit test reads:
```kotlin
val mock = MockWebSocket { onConnectionAttempt = { it.respondWithSuccess(CONNECTED_MESSAGE) } }
val client = TestRealtimeClient {
    autoConnect = false
    install(mock)
}
```

### 6.2 `MockWebSocket` — the fake realtime transport
This is the heart of realtime unit testing. It plugs into the SDK via
`MockWebSocketEngineFactory` (which implements the SDK's `WebSocketEngineFactory` SPI from
`network-client-core`), and exposes two complementary control styles:

**(a) Callback style** — handle inline, synchronously on the SDK thread. Set fields on
`WebSocketMockConfig`:
```kotlin
val mock = MockWebSocket {
    onConnectionAttempt = { conn -> conn.respondWithSuccess(CONNECTED_MESSAGE) }
    onMessageFromClient  = { msg -> /* inspect frames the SDK sent */ }
}
```
Best when every connection attempt should behave the same way.

**(b) Await style** — suspend until the SDK triggers something, then respond. Leave the callbacks
null and call the `await*` methods:
```kotlin
val pending = mock.awaitConnectionAttempt()      // suspend until SDK opens a socket
pending.respondWithRefused()                     // …then decide how to answer
val frame = mock.awaitNextMessageFromClient()    // suspend until SDK sends a frame
```
Required when the *first* connection and a *reconnection* need different answers (e.g.
"connect succeeds, then all retries are refused" — exactly the SUSPENDED scenario in the unit test).

> ⚠️ You cannot mix the two styles for the same event type — a callback consumes the event before the
> queue ever sees it.

**Server → client direction** (driving the SDK), matching the spec's closing semantics:

| Method | What it does | Use for |
|--------|--------------|---------|
| `sendToClient(msg)` | deliver a frame, connection stays open | CONNECTED, ATTACHED, channel-level ERROR, normal messages |
| `sendToClientAndClose(msg)` | deliver a frame then close (code 1000) | DISCONNECTED, connection-level ERROR (fatal) |
| `simulateDisconnect()` | close with code 1006, no message | unexpected network drop → triggers DISCONNECTED/resume |

**Everything is logged.** `mock.events` is an ordered `List<MockEvent>` (a sealed class in
`MockEvent.kt`: `ConnectionAttempt`, `ConnectionEstablished`, `ConnectionRefused`, `SentToClient`,
`MessageFromClient`, `ClientClose`, `Disconnected`, …). Tests assert against it, e.g.
`mock.events.filterIsInstance<MockEvent.ConnectionAttempt>().size`.

**`CONNECTED_MESSAGE`** is a ready-to-use CONNECTED `ProtocolMessage` (connectionId
`test-connection-id`, a connection key, TTL 120 s, max-idle 15 s) so most tests don't hand-build it.
(It is a `val` with a custom getter, so each access returns a **fresh** instance — not a shared
singleton; safe to mutate per test, e.g. `CONNECTED_MESSAGE.apply { … }`.)

One subtlety encoded in `DefaultPendingConnection.respondWithSuccess(message)`: the CONNECTED frame is
delivered **asynchronously** on a separate `mock-ws-delivery` thread. That mirrors reality — the SDK
must store the WebSocket reference *before* it processes CONNECTED, so the mock must not deliver it
synchronously inside the connect call.

### 6.3 `MockHttpClient` — the fake REST transport
The HTTP analogue, plugged in via `MockHttpEngine` (implements the SDK's `HttpEngine` SPI). Same two
styles (`onConnectionAttempt`/`onRequest` callbacks, or `awaitConnectionAttempt()`/`awaitRequest()`).
A request flows in two phases inside `MockHttpCall.execute()`:
1. **Connect phase** → produces a `PendingConnection` (`respondWithSuccess/Refused/Timeout/DnsError`).
2. **Request phase** → produces a `PendingRequest` exposing `url`, `method`, `headers`, `body`, and
   `respondWith(status, body, headers)` / `respondWithDelay(...)` / `respondWithTimeout()`.

This lets REST unit tests assert on outgoing request shape (path, headers, query) and feed canned
responses back — all without a socket.

### 6.4 `FakeClock` — deterministic time
`FakeClock` implements the SDK's `Clock`. Time is frozen until you call `advance(ms)`; on each
advance it fires any due virtual timers **synchronously**, and wakes any `waitOn` sleepers. `advance`
runs due work **to quiescence** — it re-scans until a full pass fires nothing, so cascades due within
the advanced interval (a zero-delay reschedule, or a timer created by fired work) also run in that same
`advance` (the spec run-to-quiescence Guarantee; see §9.3). This is
how the unit test drives reconnection backoff and `connectionStateTtl` expiry **without real
sleeping**. Caveat: `waitOn(target, timeout)` still performs a real `target.wait(timeout)`, so a
sleeper also wakes once `timeout` ms of wall-clock elapse — `advance()` makes it wake *sooner*, but is
not a hard gate (the **advisory** model of the spec's `mock_websocket.md` §Fake-time semantics).
Drive transitions by owning the resulting attempt (`awaitConnectionAttempt()`), never
by asserting a wait has *not* yet returned.
```kotlin
val fakeClock = FakeClock()
val client = TestRealtimeClient { enableFakeTimers(fakeClock); … }
…
fakeClock.advance(2.seconds)      // jump forward; due timers fire now
```
`pendingTaskCount(timerName)` lets you assert how many tasks are scheduled — useful for verifying
retry state.

---

## 7. Proxy-Integration Infrastructure (real backend + fault injection)

Proxy tests connect the **real SDK** to the **real Ably sandbox**, but route the traffic through a
small Go program — [`ably/uts-proxy`](https://github.com/ably/uts-proxy) — that can be told to inject
faults. Three Kotlin helpers make this work.

### 7.1 `ProxyManager` — gets the proxy binary running
A singleton (`object`) responsible for the proxy *process*:
- Pins a proxy version (`v0.3.0`) and knows the **SHA-256 checksums** for each
  OS/arch archive.
- `ensureProxy()` (called in `@BeforeAll`) is idempotent: if a proxy is already healthy on the
  control port (**10100**) it's a no-op; otherwise it **downloads** the right
  `uts-proxy_<ver>_<os>_<arch>.tar.gz` from GitHub releases, **verifies the checksum**, extracts the
  binary with a hand-rolled tar/gzip reader (JDK-only, no extra deps), caches it under
  `~/.cache/uts-proxy/<version>/`, and launches it with `--port 10100`.
- The download is serialised **across JVMs** by a `FileLock` and **within a JVM** by a `Mutex`.
  Because process startup shares the control port, `ProxyManager`'s KDoc **advises** running proxy
  suites single-fork (`maxParallelForks = 1`) to avoid two Gradle workers racing to bind the control
  port. ⚠️ Note: this is currently only a documented recommendation — it is **not** set in
  `uts/build.gradle.kts`. With a single proxy test class today the race is not yet triggered, but it
  should be configured before a second proxy suite is added.
- A **JVM shutdown hook** force-kills the spawned process on exit (a `ProcessBuilder` child does not
  die with its parent).
- Override knob: set `-Duts.proxy.localPath=…` or `$UTS_PROXY_LOCAL_PATH` to use a **locally built**
  proxy binary or `.tar.gz` (skips download + checksum). The build script forwards this property
  into the test JVM.

### 7.2 `ProxySession` — one test's window into the proxy
The proxy exposes a **control REST API** on the control port; `ProxySession` is the typed Kotlin
client for it (via Ktor). One session per test.

- `ProxySession.create(rules, …)` → `POST /sessions` with a `target` (the sandbox realtime/REST
  hosts) and an initial **rule list**; the proxy assigns a `sessionId` and a fresh **listening
  port**.
- `addRules(rules, position)` → add rules mid-test (`POST /sessions/{id}/rules`).
- `triggerAction(action)` → fire an **imperative** action *right now* (`POST
  /sessions/{id}/actions`) — e.g. inject a frame or drop the connection at a precise moment.
- `getLog()` → `GET /sessions/{id}/log`, returning a typed `List<Event>`. Each `Event` carries
  `type` (`ws_connect`, `ws_frame`, `http_request`, …), `direction`, `queryParams`, and the parsed
  protocol `message` (a `JsonObject`, introspected via `message?.get("action")?.asInt`).
- `close()` → `DELETE /sessions/{id}`, always called in a `finally`.

**Rules** = `match` + `action` (+ optional `times`). Builder helpers keep tests readable:
`wsConnectRule`, `wsFrameToClientRule`, `wsFrameToServerRule`, `httpRequestRule`. Rules evaluate in
order, first match wins, unmatched traffic passes through, and `times: N` auto-removes a rule after N
firings. Common actions: `refuse_connection`, `suppress`, `replace`, `inject_to_client[_and_close]`,
`disconnect`, `http_respond`.

**Wiring the client to the proxy** — the `connectThroughProxy(session)` extension does exactly what
the proxy spec prescribes:
```kotlin
fun ClientOptionsBuilder.connectThroughProxy(session: ProxySession) {
    realtimeHost = session.proxyHost   // "localhost"
    restHost     = session.proxyHost
    port         = session.proxyPort   // the session's assigned port
    tls          = false               // proxy serves plain HTTP/WS; TLS is only upstream
}
```
Explicit hosts auto-disable fallback hosts (REC2c2), so no `fallbackHosts` juggling is needed.

### 7.3 `SandboxApp` — a throwaway app on the real sandbox
Provisioning helper for the real backend (provisioned **directly**, not through the proxy, so it's
independent of the fault rules):
- `SandboxApp.create()` fetches the canonical `test-app-setup.json` from `ably-common`,
  `POST`s it to `https://sandbox.realtime.ably-nonprod.net/apps`, and exposes `appId`, `defaultKey`
  (full-capability `appId.keyId:keySecret`), and the full `keys` list.
- `delete()` removes the app in teardown (best-effort — errors are swallowed since sandbox apps
  auto-expire).
- The Ktor client retries only **idempotent GETs** (never re-POSTs `/apps`, to avoid duplicate
  apps).
- Owns the single sandbox **host** constant `SandboxApp.sandboxHost`
  (`sandbox.realtime.ably-nonprod.net`) — the `nonprod:sandbox` endpoint used uniformly across the
  realtime/objects/rest integration specs, resolved to a hostname. It's the single source of truth for
  the upstream host: `ProxySession` defaults both its `realtimeHost` and `restHost` target to it, and
  direct-sandbox clients set `realtimeHost` / `restHost` from it (sandbox realtime and REST are the
  same host).

`SandboxApp` is the shared backbone of *both* integration kinds: **proxy** tests pair it with a
`ProxySession`, while **direct sandbox** tests (`integration/standard/<module>/`) use it alone —
connecting straight to `SandboxApp.sandboxHost` with no proxy and no fault rules, for happy-path
interop.

---

## 8. Shared Async Helpers

`Utils.kt` provides the coroutine glue every tier relies on (unit, direct sandbox, and proxy). All
three run on a **single-thread real dispatcher** so their timeouts measure **wall-clock** time (not the virtual time of
`kotlinx.coroutines.test`). The two state-waiters (`awaitState`/`awaitChannelState`) register their
listener *before* checking current state, to avoid a check-then-register race; `pollUntil` has no
listener — it re-evaluates the predicate every `interval` until it holds or the timeout fires.

| Helper | Signature | Purpose |
|--------|-----------|---------|
| `awaitState` | `(client, target, timeout=5s)` | suspend until `connection.state == target` (or already there) |
| `awaitChannelState` | `(channel, target, timeout=5s)` | same, for a channel's state |
| `pollUntil` | `(timeout=15s, interval=100ms) { condition }` | suspend until a boolean predicate holds — used in proxy tests to wait on real network/proxy state, e.g. `pollUntil { authCallbackCount.get() > original }` |

A second `Utils.kt` under `infra/unit/` adds the `ConnectionDetails { … }` builder DSL so tests can
write `ConnectionDetails { connectionKey = "key-1"; connectionStateTtl = 120000L }`. Since this file
no longer sits in the `io.ably.lib.types` package, it can't call `ConnectionDetails`'s package-private
constructor directly — it obtains an instance **reflectively** (the same package-private-access
technique used by `liveobjects/.../TestUtils.kt`). See Appendix B.1.

---

## 9. Walkthrough: the Unit Smoke Test (`UnitInfraSmokeTest`)

**File:** `uts/src/test/kotlin/io/ably/lib/uts/unit/UnitInfraSmokeTest.kt` (package `io.ably.lib.uts.unit`)
**Tier:** Unit (mocked WebSocket + mocked HTTP, no network).
**Purpose:** the permanent acceptance test for the unit-tier infra — it drives a real SDK through
`MockWebSocket`, `MockHttpClient` and `FakeClock` end-to-end. It carries **no** `@UTS` marker (it is
not derived from a spec) and must never trip the spec-parity tooling; it is the reference shape a
future unit-tier UTS test should take.

> The real spec-derived unit suites this pattern scales to live in `:java`
> (`lib/src/test/kotlin/io/ably/lib/uts/unit/realtime/`, e.g. `ConnectionRecoveryTest`) and
> `:liveobjects` — see §13.

It has **three** `@Test` methods: two end-to-end transport tests (§9.1, §9.2) that between them exercise
every teaching point of §5–§8, plus a focused `FakeClock` run-to-quiescence acceptance test (§9.3).

### 9.1 `unit infra drives the full mock-WebSocket connection lifecycle` — await style throughout
One long **await-style** test that walks the SDK through the whole transport lifecycle:

1. **Connect (await style).** A `launch`ed coroutine calls `awaitConnectionAttempt()`, captures the
   `PendingConnection`, then answers `respondWithSuccess(CONNECTED_MESSAGE)`:
   ```kotlin
   val firstConnection = CompletableDeferred<PendingConnection>()
   launch {
       val conn = mock.awaitConnectionAttempt()
       firstConnection.complete(conn)
       conn.respondWithSuccess(CONNECTED_MESSAGE)
   }
   client.connect()
   awaitState(client, ConnectionState.connected)
   ```
   It then asserts the captured connection's **query params** (`format == "json"`, `key` present —
   the same technique a `recover`/`resume` test uses), that `CONNECTED_MESSAGE`'s `test-connection-id`
   reached `client.connection.id`, and the event ordering (`events[0] is ConnectionAttempt`,
   `events[1] is ConnectionEstablished`).
2. **Server-initiated ATTACHED with a Unicode round-trip.** It attaches a channel whose name carries
   Unicode (`smoke-üñîçöðé-…`), asserts the **outbound** ATTACH frame via `awaitNextMessageFromClient()`,
   then feeds an ATTACHED back with `sendToClient` and checks the `channelSerial` round-tripped in:
   ```kotlin
   ch.attach()
   val attachFrame = mock.awaitNextMessageFromClient()
   assertEquals(ProtocolMessage.Action.attach, attachFrame.action)
   mock.sendToClient(ProtocolMessage().apply {
       action = ProtocolMessage.Action.attached
       channel = channelName
       channelSerial = "serial-1"
   })
   awaitChannelState(ch, ChannelState.attached)
   ```
3. **Publish**, asserting the full MESSAGE frame (`action`, `channel`, `messages[0].name`/`data`) again
   via `awaitNextMessageFromClient()`.
4. **Disconnect.** `simulateDisconnect()`, await DISCONNECTED, and assert the drop was recorded. Note
   we do **not** snapshot the `ConnectionAttempt` count here: `FakeClock.waitOn(target, timeout)` does a
   real `target.wait(timeout)`, so the disconnected-retry fires on its own after ~`disconnectedRetryTimeout`
   ms of wall-clock even without an `advance()`. `advance()` only wins that race sooner — it is not a
   hard gate — so a "still exactly one attempt" assertion would be racy on a loaded runner. Ownership
   of attempt #2 belongs to the next step, which gates on it deterministically.
5. **FakeClock-driven reconnect.** A coroutine loops `fakeClock.advance(2.seconds)` then answers the
   next attempt (received via the buffered `awaitConnectionAttempt()`, so it cannot be missed) with a
   short-TTL CONNECTED; the test awaits CONNECTED again and asserts a second `ConnectionAttempt`.
6. **Refuse → SUSPENDED (the centrepiece).** After another `simulateDisconnect()`, a `refuseJob`
   coroutine advances the clock and `respondWithRefused()`s every reconnection attempt until the short
   `connectionStateTtl` (800 ms, from the short-lived CONNECTED) expires and the client gives up to
   SUSPENDED; the test asserts `connection.createRecoveryKey()` is null in SUSPENDED.

*Why await-style throughout?* The initial connect, the FakeClock reconnect, and the refuse branch each
need a **different** answer per attempt — a single `onConnectionAttempt` callback answers every attempt
uniformly, and the two styles cannot be mixed on one mock. (This is the same reason `:java`'s
`ConnectionRecoveryTest` is await-style.) *Technique on show: await-style `awaitConnectionAttempt` /
`awaitNextMessageFromClient`, `sendToClient` for server frames, `events` for assertions, `FakeClock`
for deterministic backoff, and a Unicode channel-name round-trip.*

### 9.2 `unit infra serves a token-auth HTTP request through the mock engine` — callback WS + HTTP mock
The second test finally gives §6.3's `MockHttpClient` a worked example, and demonstrates the
**callback style** on the WebSocket side (every attempt is answered identically, so a callback is the
right tool):
```kotlin
val mockWs = MockWebSocket { onConnectionAttempt = { it.respondWithSuccess(CONNECTED_MESSAGE) } }
val mockHttp = MockHttpClient { onConnectionAttempt = { it.respondWithSuccess() } }
val client = TestRealtimeClient {
    authUrl = "https://auth.example.test/token"
    install(mockWs)
    install(mockHttp)
    autoConnect = false
}
```
The auth HTTP request is then handled **await style** — a `launch`ed coroutine `awaitRequest()`s it,
asserts the outbound request shape, and feeds a canned `TokenDetails` JSON back:
```kotlin
launch {
    val request = mockHttp.awaitRequest()
    captured.complete(request.method to request.url.path)
    request.respondWith(200, tokenJson, mapOf("Content-Type" to "application/json"))
}
client.connect()
awaitState(client, ConnectionState.connected)
```
It asserts the request was a `GET /token` and that the SDK reached CONNECTED with the fetched token.

> ⚠️ **Trap (documented inline in the test):** `MockEvent.HttpRequest` is declared in the `MockEvent`
> sealed class but is **never emitted** by the HTTP mock — asserting on
> `events.filterIsInstance<MockEvent.HttpRequest>()` would silently pass on an empty list. Assert via
> `MockHttpClient.awaitRequest()` / the `PendingRequest` instead (as this test does).

**What these two tests teach about the infra:** callback vs await styles side by side (WS callback in
§9.2, WS await throughout §9.1), `FakeClock`-driven reconnect and SUSPENDED, `sendToClient` for server
frames, `events` / `awaitNextMessageFromClient` for inspecting client output, and the full HTTP-mock
connect→request two-phase flow. The `RUN_DEVIATIONS` env-gated deviation pattern is **not** here (the
smoke tests carry no deviations) — that teaching lives in §12.

### 9.3 `FakeClock advance runs cascaded work to quiescence in one call` — the run-to-quiescence Guarantee
A focused, SDK-free test that pins the `FakeClock` contract §6.4 depends on: a single `advance(ms)` runs
**all** work due within the advanced interval, including cascades. It schedules a task that reschedules
itself at zero delay and a task that creates a brand-new timer mid-advance, then asserts one `advance`
fires the whole cascade (not just the first pass). This is the infra-level guarantee the reconnect/backoff
walkthroughs in §9.1 rely on; it exercises `FakeClock` directly because the Guarantee is about `advance`
alone reaching quiescence.

---

## 10. Walkthrough: the Direct-Sandbox Smoke Test (`IntegrationInfraSmokeTest`)

**File:** `uts/src/test/kotlin/io/ably/lib/uts/integration/standard/IntegrationInfraSmokeTest.kt` (package `io.ably.lib.uts.integration.standard`)
**Tier:** Direct-sandbox integration (real network, real Ably sandbox, **no** proxy, **no** fault injection).
**Purpose:** the permanent acceptance test for the middle-tier infra — `SandboxApp` +
`TestRealtimeClient`/`TestRestClient` wired straight to the sandbox. No `@UTS` marker.

> The real spec-derived direct-sandbox suites this pattern scales to live in `:java`
> (`lib/src/test/kotlin/io/ably/lib/uts/integration/standard/realtime/`, e.g. `ChannelHistoryTest`)
> and `:liveobjects` — see §13.

It talks to the real backend but connects *straight* to `SandboxApp.sandboxHost` — no `ProxyManager`,
no `ProxySession`, no `connectThroughProxy`. It's the shape every happy-path interop spec
(connect/publish/subscribe/history) follows.

### 10.1 Suite setup/teardown
`@TestInstance(PER_CLASS)` + `runBlocking`, provisioning **`SandboxApp` only** — no
`ProxyManager.ensureProxy()`:
```kotlin
@BeforeAll fun setUpAll()    = runBlocking { app = SandboxApp.create() }
@AfterAll  fun tearDownAll() = runBlocking { if (::app.isInitialized) app.delete() }
```

### 10.2 The clients — wired straight to the sandbox
Two tiny helpers point the **real** transports at the sandbox host (no proxy in between). Setting
explicit hosts auto-disables fallback hosts (REC2c2), so there's nothing else to configure:
```kotlin
private fun newRealtimeClient(useBinaryProtocol: Boolean): AblyRealtime = TestRealtimeClient {
    key = app.defaultKey
    realtimeHost = SandboxApp.sandboxHost   // sandbox.realtime.ably-nonprod.net
    restHost     = SandboxApp.sandboxHost
    this.useBinaryProtocol = useBinaryProtocol
    autoConnect  = false
}
```
(`TestRealtimeClient`/`TestRestClient` are the same builders the unit tests use — here fed no mocks, so
they drive the SDK's real network transport instead of a `MockWebSocket`.)

### 10.3 Protocol variants — the `@ParameterizedTest` pattern
The spec declares a `PROTOCOL` dimension (`json` / `msgpack`) and says *each test runs once per variant*.
ably-java realises that with a JUnit 5 **parameterised test** over `useBinaryProtocol` — which is why the
module depends on `junit-jupiter-params` (§4.1):
```kotlin
@ParameterizedTest(name = "useBinaryProtocol={0}")
@ValueSource(booleans = [false, true])   // false = JSON, true = msgpack
fun `sandbox infra works end to end`(useBinaryProtocol: Boolean) = runTest {
    …
}
```
A plain `@Test` test (no protocol dimension) stays a `@Test` — reach for `@ParameterizedTest` only when the
spec actually declares variants.

### 10.4 The scenario — real publish, real history
One realtime client and one REST client on the same app: the publisher's *confirmed* messages must
appear in the REST `history()`. The integration-specific techniques on show:
- **A recorded state sequence, not just the final state.** `client.connection.on { states.add(it.current) }`
  is registered *before* connect, then the test asserts `states.contains(connecting)` and
  `states.last() == connected`.
- **Awaiting a publish ack.** Realtime publish is fire-and-forget, so to honour the spec's `AWAIT publish`
  an `awaitPublish` extension wraps the `publish(name, data, Callback<PublishResult>)` overload in a
  `suspendCancellableCoroutine`, resuming on `onSuccess` and failing on `onError`. The test publishes
  **three** messages, each ack-awaited. This is the integration analogue of the unit test's
  `awaitNextMessageFromClient()`.
- **`AWAIT attach()`** → `attach()` then `awaitChannelState(channel, ChannelState.attached, 15.seconds)`.
- **Polling real REST state.** `history()` is a blocking REST call against the sandbox and the message
  store is eventually-consistent, so — never a fixed sleep (the same anti-flake rule as the other tiers):
  ```kotlin
  pollUntil(15.seconds, 500.milliseconds) {
      val result = rest.channels.get(channelName).history(null)
      history = result
      result.items().size == 3
  }
  ```
- **Order assertion.** History defaults to newest-first, so `items[0]` is `event3` … `items[2]` is `event1`.

**What this test teaches about the infra:** `SandboxApp`-only provisioning, the direct-sandbox client
wiring (`realtimeHost`/`restHost` from `SandboxApp.sandboxHost`, no proxy), the protocol-variant
`@ParameterizedTest`, awaiting a publish ack via `Callback<PublishResult>`, and `pollUntil` over a real
REST `history()` call.

---

## 11. Walkthrough: the Proxy Smoke Test (`ProxyInfraSmokeTest`)

**File:** `uts/src/test/kotlin/io/ably/lib/uts/integration/proxy/ProxyInfraSmokeTest.kt` (package `io.ably.lib.uts.integration.proxy`)
**Tier:** Proxy integration (real sandbox + uts-proxy).
**Purpose:** the permanent acceptance test for the proxy infra — `ProxyManager` + `ProxySession` +
`SandboxApp` + client wiring through the proxy, exercising **both** fault-injection styles. No `@UTS`
marker.

> The real spec-derived proxy suites this pattern scales to live in `:java`
> (`lib/src/test/kotlin/io/ably/lib/uts/integration/proxy/realtime/`, e.g. `AuthReauthTest`) and
> `:liveobjects` — see §13.

### 11.1 Suite setup/teardown
```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)   // one instance, so @BeforeAll can be non-static
class ProxyInfraSmokeTest {
    @BeforeAll fun setUpAll() = runBlocking {
        ProxyManager.ensureProxy()                // download+launch proxy if needed
        app = SandboxApp.create()                 // provision a real sandbox app
    }
    @AfterAll fun tearDownAll() = runBlocking { if (::app.isInitialized) app.delete() }
}
```
It has **two** `@Test` methods, one per fault-injection style.

### 11.2 Late imperative injection — `triggerAction`
The first test creates a **rule-less pass-through** session, authenticates through the proxy (basic key
auth is TLS-only, so a token is signed locally by an `AblyRest(app.defaultKey)` in the `authCallback`),
and connects:
```kotlin
val session = ProxySession.create(rules = emptyList())
```
Once CONNECTED, it proves the **typed proxy log** — the handshake recorded a `ws_connect` and a
server→client CONNECTED frame (protocol action 4):
```kotlin
val log = session.getLog()
assertTrue(log.any { it.type == "ws_connect" })
assertTrue(
    log.any {
        it.type == "ws_frame" &&
            it.direction == "server_to_client" &&
            it.message?.get("action")?.asInt == 4
    },
)
```
Only *after* the real handshake does it inject the fault — the **late imperative** way, firing an
action on the live connection right now, then observing DISCONNECTED and recovery:
```kotlin
session.triggerAction(mapOf("type" to "disconnect"))
pollUntil(20.seconds) { states.contains(ConnectionState.disconnected) }
awaitState(client, ConnectionState.connected, 20.seconds)
```

### 11.3 Declarative-rule injection — `wsFrameToClientRule`
The second test uses the *other* style — a **declarative rule** supplied at session creation that
rewrites the first ATTACHED frame (protocol action 11) into a disconnect, one-shot (`times = 1`):
```kotlin
val session = ProxySession.create(
    rules = listOf(
        wsFrameToClientRule(action = mapOf("type" to "disconnect"), messageAction = 11, times = 1),
    ),
)
```
It connects, attaches a channel, the rule fires on the ATTACHED so the client observes a DISCONNECTED
transition, then recovers (reconnects and the channel re-attaches once the one-shot rule is spent):
```kotlin
channel.attach()
pollUntil(20.seconds) { states.contains(ConnectionState.disconnected) }
awaitState(client, ConnectionState.connected, 20.seconds)
awaitChannelState(channel, ChannelState.attached, 20.seconds)
```
Rules evaluate for *every* matching frame (until `times` is exhausted), so a declarative rule is the
right tool when the fault must land on a frame the test can't easily await; the imperative
`triggerAction` is the right tool for a fault at a precise moment on an already-live connection.

### 11.4 Teardown
Both tests tear down in a nested `finally`: close the client, then always `session.close()` (`DELETE
/sessions/{id}`) and the token signer.

**What these tests teach about the infra:** `ProxyManager.ensureProxy` + `SandboxApp` setup,
`connectThroughProxy`, **both** fault-injection styles (declarative `wsFrameToClientRule` at creation
and late imperative `triggerAction`), real-network waiting with `pollUntil`, and **proxy-log
assertions** as the primary verification (`getLog()` → filter by `type`/`direction`/`message.action`).

---

## 12. Deviations: when the SDK disagrees with the spec

Deviations live **with their tests**, not in `:uts` (whose smoke tests carry none by design). There are
two catalogues:

- `lib/src/test/kotlin/io/ably/lib/uts/deviations.md` — the **realtime/rest** tiers, hosted in `:java`.
- `liveobjects/src/test/kotlin/io/ably/lib/liveobjects/uts/deviations.md` — **all three objects tiers**
  (unit, integration, proxy), hosted in `:liveobjects`.

Each entry records the **spec point**, **what the spec requires**, **what the SDK does**, the **root
cause** (file/function, where known), the **workaround in tests**, and the **affected tests**.

The mechanism (from [`writing-derived-tests.md`](https://github.com/ably/specification/blob/main/uts/docs/writing-derived-tests.md)): the test keeps the **spec-correct** assertion but
gates it behind the `RUN_DEVIATIONS` env var, with a regression-guard assertion for the SDK's actual
behaviour running by default. Normal runs stay green; `RUN_DEVIATIONS=1` turns the failing assertions
on so the gap is reproducible and the test flips automatically once the SDK is fixed. In code (from
`:java`'s `ConnectionRecoveryTest`, RTN16f):
```kotlin
if (System.getenv("RUN_DEVIATIONS") != null) {
    assertEquals(42L, currentRecoveryKey.msgSerial)   // spec-correct: recover preserves msgSerial
} else {
    assertEquals(0L, currentRecoveryKey.msgSerial)    // regression guard: the SDK's actual behaviour
}
```
Run it with `RUN_DEVIATIONS=1 ./gradlew :java:runUtsUnitTests --tests "*ConnectionRecoveryTest*"` (§13).

Representative entries from the realtime catalogue (`lib/.../deviations.md`):

| Spec point | Gist | Touches |
|------------|------|---------|
| **RTN16f** | SDK resets `msgSerial` to 0 on connect even with `recover`; spec says preserve it (42). | `ConnectionRecoveryTest` (`:java`) — `assertEquals(42L,…)` gated, `assertEquals(0L,…)` default guard. |
| **RTN16g2** | Spec's fatal error 50000/500 isn't fatal to the SDK (`isFatalError()` needs code 40000–49999 or status < 500); also `send_to_client_and_close` races the FAILED transition. | `ConnectionRecoveryTest` (`:java`) — uses 40000/400 + plain `sendToClient`. |
| **RTL13b / RTL13c** | Channel-state reattach / `channelRetryTimeout` gaps. Retained as confirmed SDK gaps; the citing channel tests aren't in the suite yet (only `ConnectionRecoveryTest` is translated). | pending the channels-module translation. |

The objects catalogue additionally records the typed-SDK / language adaptations (RTTS API
partitioning, compile-time-forbidden inputs, internal-wire visibility) and the intentional RTO18d
listener-dedup divergence — see `liveobjects/.../deviations.md`.

> These deviations are **valuable output**, not failures — each one is a precise, reproducible bug
> report the SDK team can act on, and the gated test becomes the acceptance test for the fix.

---

## 13. How to Run the Tests

Each module registers package-filtered Gradle tasks. `:uts` registers `runUtsUnitTests` /
`runUtsIntegrationTests` (in `uts/build.gradle.kts`); `:java` registers the same-named tasks for its
realtime suites; `:liveobjects` registers `runLiveObjectsUnitTests` / `runLiveObjectsIntegrationTests`.

| What | Command |
|---|---|
| `:uts` smoke (unit, offline) | `./gradlew :uts:runUtsUnitTests` |
| `:uts` smoke (integration + proxy) | `./gradlew :uts:runUtsIntegrationTests` |
| realtime UTS unit | `./gradlew :java:runUtsUnitTests` |
| realtime UTS integration + proxy | `./gradlew :java:runUtsIntegrationTests` |
| objects UTS unit | `./gradlew :liveobjects:runLiveObjectsUnitTests` |
| objects UTS integration + proxy | `./gradlew :liveobjects:runLiveObjectsIntegrationTests` |

Each `runUts*` / `runLiveObjects*` task is package-filtered (`io.ably.lib.uts.unit.*` /
`io.ably.lib.uts.integration.*` for `:uts` and `:java`; `io.ably.lib.liveobjects.uts.*` for
`:liveobjects`). The `…IntegrationTests` tasks cover **both** the direct-sandbox
(`integration/standard/`) and proxy (`integration/proxy/`) tiers — proxy tests additionally
download/launch the uts-proxy.

```bash
# All :uts smoke tests (every tier), or one class:
./gradlew :uts:test
./gradlew :uts:runUtsUnitTests --tests "io.ably.lib.uts.unit.UnitInfraSmokeTest"
./gradlew :java:runUtsIntegrationTests --tests "io.ably.lib.uts.integration.proxy.realtime.AuthReauthTest"

# Turn on the spec-correct (currently failing) deviation assertions (§12):
RUN_DEVIATIONS=1 ./gradlew :java:runUtsUnitTests --tests "*ConnectionRecoveryTest*"

# Run proxy tests against a locally built proxy instead of a GitHub release:
./gradlew :uts:runUtsIntegrationTests -Duts.proxy.localPath=/path/to/uts-proxy   # or .tar.gz
#   (equivalently: export UTS_PROXY_LOCAL_PATH=/path/to/uts-proxy)
```

> **Unqualified task names collide — intentionally.** `./gradlew runUtsUnitTests` (no module prefix)
> runs the task in **both** `:uts` and `:java` (Gradle matches the task name across every project that
> declares it). That's intended and harmless — both unit tiers are offline — and mirrors the existing
> `runUnitTests` precedent (registered in both `:java` and `:pubsub-adapter`). Qualify with `:uts:` /
> `:java:` to target one. The `RUN_DEVIATIONS`, `-Duts.proxy.localPath` and `UTS_PROXY_LOCAL_PATH`
> knobs are valid for **all three** modules.

**Where CI runs them:** `check.yml` runs
`… runUnitTests runLiveObjectsUnitTests :java:runUtsUnitTests :uts:runUtsUnitTests` as the PR gate
(all offline unit tiers); `integration-test.yml`'s `check-uts` job runs
`:java:runUtsIntegrationTests :uts:runUtsIntegrationTests`, and its `check-liveobjects` job runs
`runLiveObjectsIntegrationTests`.

Notes:
- `ProxyManager` **advises** running proxy suites single-fork (`maxParallelForks = 1`) because they
  share the control port (10100). This is not currently set in `uts/build.gradle.kts`; it isn't
  exercised yet because there is only one proxy smoke class.
- Proxy/sandbox tests need outbound network (sandbox + GitHub releases on first run; the binary is
  then cached under `~/.cache/uts-proxy/`).
- Before pushing, run the project's static-analysis gate (from `CLAUDE.md`):
  `./gradlew checkWithCodenarc checkstyleMain checkstyleTest` — Checkstyle is Java-only and easy to
  miss; remember **no star imports**.

---

## 14. Quick Reference / Cheat-Sheet

**The three seams that make unit tests possible** (`DebugOptions`):
`webSocketEngineFactory` (WS), `httpEngine` (HTTP), `clock` (time).

**Build a unit-test client:**
```kotlin
val mock = MockWebSocket { onConnectionAttempt = { it.respondWithSuccess(CONNECTED_MESSAGE) } }
val client = TestRealtimeClient { autoConnect = false; install(mock) }
client.connect(); awaitState(client, ConnectionState.connected)
```

**Build a proxy-test client:**
```kotlin
ProxyManager.ensureProxy(); val app = SandboxApp.create()
val session = ProxySession.create(rules = emptyList())
val client = TestRealtimeClient { authCallback = …; connectThroughProxy(session); autoConnect = false }
```

**Server→client (mock):** `sendToClient` (stays open) · `sendToClientAndClose` (DISCONNECTED /
fatal ERROR) · `simulateDisconnect` (1006 drop).

**Inspect what the SDK did:** `mock.events` (unit) · `session.getLog()` (proxy).

**Wait (never sleep):** `awaitState` · `awaitChannelState` · `pollUntil { … }` · `FakeClock.advance(…)`.

**Protocol action numbers** (used in rules & log assertions): CONNECTED=4, DISCONNECTED=6, ERROR=9,
ATTACH=10, ATTACHED=11, DETACH=12, DETACHED=13, **AUTH=17**.

**Test ID format:** `<category>/<spec-point>/<descriptive-name>-<n>` →
`@UTS realtime/proxy/RTN22/server-initiated-reauth-0`.

**The decision tree when a translated test fails:** spec wrong → fix test + record UTS spec error;
translation wrong → fix test; SDK non-compliant → gate spec-correct assertion behind `RUN_DEVIATIONS`
and record in `deviations.md`.

---

## 15. Appendix A: Request-Flow Diagrams

### A.1 Unit test — mocked WebSocket (no network)

A unit test installs `MockWebSocket` into `DebugOptions.webSocketEngineFactory`. The SDK believes it
is talking to a real socket; in fact every byte is intercepted by the mock and surfaced to the test.

```text
   ┌──────────────────────────────────── TEST (Kotlin coroutine) ────────────────────────────────────┐
   │                                                                                                   │
   │  TestRealtimeClient { install(mock); autoConnect = false }                                        │
   │        │  client.connect()                                  ▲   awaitState(client, connected)     │
   │        ▼                                                     │                                     │
   │  ┌───────────┐   webSocketEngineFactory   ┌──────────────────────────┐                            │
   │  │  AblyRealtime (SDK :java)  │──────────▶ │ MockWebSocketEngineFactory │ (implements SDK SPI)     │
   │  │  ConnectionManager, etc.   │            └─────────────┬────────────┘                            │
   │  └───────────┬────────────────┘                         │ create()                                │
   │              │ send(frame) ───────────────────────────▶ │                                         │
   │              │                                          ▼                                          │
   │              │                              ┌────────────────────────┐                            │
   │              │                              │      MockWebSocket      │                            │
   │   onMessage(frame) ◀───────────────────────│  • records MockEvent[]   │                           │
   │              ▲                              │  • onConnectionAttempt   │ ◀── PendingConnection ──┐ │
   │              │                              │  • onMessageFromClient   │                         │ │
   │              │                              └───────────┬────────────┘                          │ │
   │              │                                          │                                       │ │
   │   TEST drives the "server" side:                        │  TEST inspects/responds:              │ │
   │     mock.sendToClient(CONNECTED) ───────────────────────┘    pending.respondWithSuccess(msg) ───┘ │
   │     mock.sendToClientAndClose(DISCONNECTED)                  mock.awaitNextMessageFromClient()     │
   │     mock.simulateDisconnect()                               mock.events  (assert)                 │
   │                                                                                                   │
   │   FakeClock (DebugOptions.clock):  fakeClock.advance(2.s) ── fires due timers synchronously        │
   └───────────────────────────────────────────────────────────────────────────────────────────────┘

   No TCP, no DNS, no real time. Everything is in-process and deterministic.
```

(The HTTP path is identical in shape: `MockHttpClient` → `DebugOptions.httpEngine` →
`MockHttpEngine` → `PendingConnection` then `PendingRequest`, with `respondWith(status, body)`.)

### A.2 Proxy integration test — real backend through the fault-injecting proxy

A proxy test uses the **real** SDK transport but points its host/port at the local `uts-proxy`
process, which forwards to the Ably sandbox and can inject faults on command.

```text
  ┌─────────────────── TEST (Kotlin) ───────────────────┐
  │ @BeforeAll: ProxyManager.ensureProxy()               │      downloads/launches binary, control :10100
  │            SandboxApp.create() ─────────────────────────────────────────────┐ POST /apps (direct, TLS)
  │ session = ProxySession.create(rules)  ──────────── control REST :10100 ───┐  │
  │ client  = TestRealtimeClient { connectThroughProxy(session) }             │  │
  └──────────────┬───────────────────────────────────────────────────────────┘  │
                 │ client.connect()  (host=localhost, port=session.port, tls=false)  │
                 ▼                                                               │  ▼
        ┌──────────────────┐    ws/http (plain)    ┌───────────────────────┐    │ ┌───────────────────────┐
        │  AblyRealtime     │ ◀──────────────────▶ │       uts-proxy        │ ◀─┼▶│   Ably sandbox          │
        │  (REAL transport) │                       │  • forwards traffic    │   │ │  sandbox.realtime.      │
        └──────────────────┘                        │  • applies rules       │   │ │  ably-nonprod.net (TLS) │
                 ▲                                   │  • records event log   │   │ └───────────────────────┘
                 │   TEST controls the proxy:        └──────────┬────────────┘   │
                 │     session.triggerAction({inject_to_client, action:17})      │ control REST :10100
                 │     session.addRules([...])                                    │
                 │   TEST verifies via:                                           │
                 │     session.getLog() ── filter type/direction/message.action ─┘
                 │     awaitState(...) / pollUntil { ... }
                 └── (everything before the injected fault is REAL client↔server traffic)
```

**Why two channels to the proxy?** The **data plane** (the SDK's ws/http traffic on
`session.proxyPort`) is separate from the **control plane** (the test's REST calls on
`CONTROL_PORT = 10100` to create sessions, add rules, trigger actions, read the log). The SDK never
sees the control plane; the test never speaks the data plane directly.

---

## 16. Appendix B: Per-File API Reference

A one-stop table of every Kotlin source file under `uts/src/main/` (the `infra/` tree) and
`uts/src/test/` (the three tier smoke tests) and the SDK seams they use, so nothing is left
implicit.

### B.1 Unit-test infrastructure — `io.ably.lib.uts.infra.unit`

| File | Key public surface | Role |
|------|--------------------|------|
| `ClientFactories.kt` | `ClientOptionsBuilder` (extends `DebugOptions`), `TestRealtimeClient { }`, `TestRestClient { }`, `install(mock)`, `enableFakeTimers(clock)` | Entry point for building a mocked SDK client; seeds dummy key, forces JSON. |
| `MockWebSocket.kt` | `MockWebSocket`, `WebSocketMockConfig` (`onConnectionAttempt`, `onMessageFromClient`, `onTextDataFrame`, `onBinaryDataFrame`), `events`, `installOn`, `awaitConnectionAttempt`, `awaitNextMessageFromClient`, `awaitClientClose`, `sendToClient`, `sendToClientAndClose`, `simulateDisconnect`, `reset`; top-level `MockWebSocket { }`, `CONNECTED_MESSAGE` | Fake realtime transport (callback + await styles). |
| `MockWebSocketEngineFactory.kt` | `MockWebSocketEngineFactory`, `MockWebSocketEngine`, `MockWebSocketClient` (implement `WebSocketEngineFactory`/`Engine`/`Client`) | Adapts the mock to the SDK's WebSocket SPI; parses URL → host/port/tls/query. |
| `MockHttpClient.kt` | `MockHttpClient`, `HttpMockConfig` (`onConnectionAttempt`, `onRequest`), `engine`, `installOn`, `awaitConnectionAttempt`, `awaitRequest`, `reset`; top-level `MockHttpClient { }` | Fake REST transport. |
| `MockHttpEngine.kt` | `MockHttpEngine`, `MockHttpCall`, `DefaultHttpPendingConnection` (implement `HttpEngine`/`HttpCall`) | Adapts the mock to the SDK's HTTP SPI; two-phase connect→request in `execute()`. |
| `PendingConnection.kt` | `interface PendingConnection` (`host`,`port`,`tls`,`queryParams`, `respondWithSuccess[ (message) ]`, `respondWithRefused/Timeout/DnsError`); plus the top-level helper `parseQueryString()` (not an interface member) | Abstract connection attempt awaiting a verdict (shared WS + HTTP). |
| `DefaultPendingConnection.kt` | `DefaultPendingConnection : PendingConnection` | WS impl; **async** CONNECTED delivery on `mock-ws-delivery` thread. |
| `PendingRequest.kt` | `interface PendingRequest` (`url`,`method`,`headers`,`body`, `respondWith`, `respondWithDelay`, `respondWithTimeout`) | Abstract in-flight HTTP request awaiting a response. |
| `DefaultPendingRequest.kt` | `DefaultPendingRequest : PendingRequest` | HTTP impl backed by a `CompletableDeferred<HttpResponse>`. |
| `MockEvent.kt` | `sealed class MockEvent`: `ConnectionAttempt`, `ConnectionEstablished`, `ConnectionRefused`, `ConnectionTimeout`, `DnsError`, `HttpRequest`, `SentToClient`, `Disconnected`, `ClientClose`, `MessageFromClient` | Ordered, typed log of everything that happened on a mock transport. |
| `FakeClock.kt` | `FakeClock : Clock` (`advance(ms\|Duration)`, `pendingTaskCount(name)`, `currentTimeMillis`, `nanoTime`, `newTimer`, `waitOn`) | Virtual clock + virtual timers; deterministic time. |
| `Utils.kt` | `ConnectionDetails { }` builder | Test-only `ConnectionDetails` DSL; instantiates the type via its **package-private constructor reflectively** (see §8). |

### B.2 Integration infrastructure — `io.ably.lib.uts.infra.integration` (and `…integration.proxy`)

| File | Key public surface | Role |
|------|--------------------|------|
| `proxy/ProxyManager.kt` | `object ProxyManager`: `ensureProxy(timeoutMs)`, `stopProxy()`, `CONTROL_PORT=10100`; pinned `PROXY_VERSION=v0.3.0` + per-arch checksums; `uts.proxy.localPath` override | Downloads/verifies/launches the `uts-proxy` binary; one shared process per run. *(package `…integration.proxy`)* |
| `proxy/ProxySession.kt` | `class ProxySession` (`create(rules,port,timeoutMs,realtimeHost,restHost)`, `addRules`, `triggerAction`, `getLog(): List<Event>`, `close`, `sessionId`, `proxyPort`, `proxyHost`); `data class Event`; `typealias ProxyRule`; rule builders `wsConnectRule`/`wsFrameToClientRule`/`wsFrameToServerRule`/`httpRequestRule`; `ClientOptionsBuilder.connectThroughProxy(session)` | Typed client for the proxy control REST API + client wiring. *(package `…integration.proxy`)* |
| `SandboxApp.kt` | `class SandboxApp` (`create()`, `delete()`, `appId`, `defaultKey`, `keys`); `SandboxApp.sandboxHost` (`sandbox.realtime.ably-nonprod.net`) | Provisions/tears down a throwaway sandbox app from `ably-common`'s `test-app-setup.json`; owns the single upstream sandbox host constant. *(package `…integration`)* |

### B.3 Shared helpers & tests

| File | Key public surface | Role |
|------|--------------------|------|
| `infra/Utils.kt` | `awaitState(client,target,timeout=5s)`, `awaitChannelState(channel,target,timeout=5s)`, `pollUntil(timeout=15s,interval=100ms){ }` | Shared wall-clock coroutine waits (package `io.ably.lib.uts.infra`); listener registered before state check. |
| `unit/UnitInfraSmokeTest.kt` | 3 `@Test`s: full mock-WS lifecycle (await style), token-auth via mock HTTP (callback WS), FakeClock run-to-quiescence | Unit-tier infra acceptance (`io.ably.lib.uts.unit`) — MockWebSocket/MockHttpClient/FakeClock end-to-end. **No** `@UTS`. |
| `integration/standard/IntegrationInfraSmokeTest.kt` | 1 `@ParameterizedTest` × {JSON, msgpack} | Direct-sandbox infra acceptance (`io.ably.lib.uts.integration.standard`) — SandboxApp + realtime/REST round-trip, awaited publish + `pollUntil` on `history()`. **No** `@UTS`. |
| `integration/proxy/ProxyInfraSmokeTest.kt` | 2 `@Test`s: late imperative disconnect, declarative ws-frame rule | Proxy infra acceptance (`io.ably.lib.uts.integration.proxy`) — ProxyManager + ProxySession, both fault-injection styles, proxy-log asserts. **No** `@UTS`. |

> **Coverage note:** this guide walks through the **three tier smoke tests** — `UnitInfraSmokeTest`
> (unit, §9), `IntegrationInfraSmokeTest` (direct-sandbox, §10), and `ProxyInfraSmokeTest` (proxy, §11)
> — the permanent acceptance gate for the shared infra and the worked examples the walkthroughs teach
> from. The infra under `infra/unit/` and `infra/integration/` is built out beyond what the smokes
> exercise (full HTTP mock, all four rule builders, REST proxy wiring, etc.), anticipating the broader
> UTS coverage catalogued in [`completion-status.md`](https://github.com/ably/specification/blob/main/uts/docs/completion-status.md). The real spec-derived suites that consume this
> infra live in `:java` (`lib/src/test/kotlin/io/ably/lib/uts/…`) and `:liveobjects`
> (`liveobjects/.../uts/…`) — see §13.

---

### Source map (where each fact in this doc comes from)

| Topic | File |
|-------|------|
| Authoring portable specs, test IDs, mock pseudocode | [`uts/docs/writing-test-specs.md`](https://github.com/ably/specification/blob/main/uts/docs/writing-test-specs.md) |
| Translating specs, deviation patterns, decision tree | [`uts/docs/writing-derived-tests.md`](https://github.com/ably/specification/blob/main/uts/docs/writing-derived-tests.md) |
| Integration/proxy policy, late fault injection, tiers | [`uts/docs/integration-testing.md`](https://github.com/ably/specification/blob/main/uts/docs/integration-testing.md) |
| Coverage matrix | [`uts/docs/completion-status.md`](https://github.com/ably/specification/blob/main/uts/docs/completion-status.md) |
| Proxy control API, rule format, action numbers | [`uts/docs/proxy.md`](https://github.com/ably/specification/blob/main/uts/docs/proxy.md) |
| SDK seams | `lib/.../debug/DebugOptions.java`, `lib/.../util/Clock.java` |
| Module wiring | `uts/build.gradle.kts`, `settings.gradle.kts` |
| Unit mocks | `uts/src/main/.../uts/infra/unit/*` |
| Integration helpers | `uts/src/main/.../uts/infra/integration/*` (+ `…/integration/proxy/*`) |
| Async helpers | `uts/src/main/.../uts/infra/Utils.kt` (awaits), `…/infra/unit/Utils.kt` (ConnectionDetails builder) |
| The three tier smoke tests | `uts/src/test/.../uts/unit/UnitInfraSmokeTest.kt`, `…/uts/integration/standard/IntegrationInfraSmokeTest.kt`, `…/uts/integration/proxy/ProxyInfraSmokeTest.kt` |
| Deviations | `lib/src/test/kotlin/io/ably/lib/uts/deviations.md` (realtime/rest, `:java`), `liveobjects/src/test/kotlin/io/ably/lib/liveobjects/uts/deviations.md` (objects, `:liveobjects`) |
