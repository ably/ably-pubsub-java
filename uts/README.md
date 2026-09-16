# UTS in ably-java — A Human-Readable Guide

> A practical, end-to-end explanation of the **Universal Test Specification (UTS)** and how it is
> realised in the `ably-java` repository. Written for a developer who has never touched UTS before
> and needs to understand *what it is*, *why it exists*, and *exactly how the Java/Kotlin code under
> `uts/` makes the unit, direct-sandbox, and proxy-integration tests work*.

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
9. [Walkthrough: the Unit Test (`ConnectionRecoveryTest`)](#9-walkthrough-the-unit-test-connectionrecoverytest)
10. [Walkthrough: the Direct-Sandbox Integration Test (`ChannelHistoryTest`)](#10-walkthrough-the-direct-sandbox-integration-test-channelhistorytest)
11. [Walkthrough: the Proxy Test (`AuthReauthTest`)](#11-walkthrough-the-proxy-test-authreauthtest)
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
        │   (this repo: Kotlin in uts/) │     e.g. ConnectionRecoveryTest.kt
        └──────────────────────────────┘
```

Three concepts you will see constantly:

| Term | Meaning |
|------|---------|
| **Spec point** | A tagged requirement in the features spec, e.g. `RTN16g`, `RTN22`, `RTL4f`. Test names embed these. |
| **UTS spec** | A markdown file of portable pseudocode describing the setup, steps, and assertions for one feature. The *source of truth for what to test.* |
| **Derived test** | A faithful translation of a UTS spec into a real test in a specific SDK/language. This is what lives in `ably-java/uts/`. |
| **Deviation** | A documented case where the SDK's actual behaviour diverges from the spec. Recorded in `deviations.md`. |

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
| **Unit** | **Mocked** (`MockWebSocket`, `MockHttpClient`) | none | Client-side logic: state machines, request formation, response parsing, timer behaviour. Fast & deterministic. | `unit/realtime/ConnectionRecoveryTest.kt` |
| **Direct sandbox integration** | Real network | Real Ably sandbox | Happy-path interop: connect, publish, subscribe. No fault injection. | `integration/standard/realtime/ChannelHistoryTest.kt` |
| **Proxy integration** | Real network **through a programmable proxy** | Real Ably sandbox | Fault behaviour: dropped connections, injected errors, timeouts, re-auth. | `integration/proxy/realtime/AuthReauthTest.kt` |

Each tier folder is further organised **by module** (`realtime`, `liveobjects`, …): `unit/<module>/`,
`integration/standard/<module>/`, and `integration/proxy/<module>/`. So a feature's tests sit together
by SDK area — the three example tests live at `unit/realtime/`, `integration/standard/realtime/`, and
`integration/proxy/realtime/`.

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
  [`helpers/proxy.md`](https://github.com/ably/specification/blob/main/uts/realtime/integration/helpers/proxy.md)).

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
- `RTN16` (connection recovery) → unit spec `connection_recovery_test.md` → **`ConnectionRecoveryTest.kt`**.
- `RTL10d` (channel history) → direct-sandbox spec
  `realtime/integration/channel_history_test.md` → **`ChannelHistoryTest.kt`**.
- `RTN22` / `RTC8a` (server-initiated re-auth) → proxy spec
  `realtime/integration/proxy/auth_reauth.md` → **`AuthReauthTest.kt`**.

> There is also a fifth, *referenced* spec:
> [`realtime/integration/helpers/proxy.md`](https://github.com/ably/specification/blob/main/uts/realtime/integration/helpers/proxy.md)
> (in the spec repo under `uts/realtime/integration/helpers/`). It defines the proxy's control API, rule format,
> action types, and the **protocol message action-number table** (CONNECTED=4, ATTACH=10, AUTH=17,
> …). The Kotlin `ProxySession` is the client for exactly that API.

---

## 4. The Java Setup: the `uts/` module

The `uts/` directory is a **standalone Gradle module** (`include("uts")` in
`settings.gradle.kts`) whose only job is to host UTS-derived tests. It contains *no production code* —
everything lives under `uts/src/test/`.

### 4.1 `uts/build.gradle.kts`
```kotlin
plugins { alias(libs.plugins.kotlin.jvm) }

dependencies {
    testImplementation(project(":core"))                 // the SDK under test
    testImplementation(project(":network-client-core"))  // HttpEngine / WebSocketEngine interfaces
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-params")  // @ParameterizedTest / @ValueSource (version from the JUnit BOM)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutine.core)              // kotlinx.coroutines
    testImplementation(libs.coroutine.test)              // runTest, virtual time
    testImplementation(libs.ktor.client.core)            // HTTP client for proxy/sandbox control
    testImplementation(libs.ktor.client.cio)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()                                   // JUnit 5
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    // Propagate a local proxy build override (see ProxyManager):
    systemProperty("uts.proxy.localPath", /* -Duts.proxy.localPath=… or $UTS_PROXY_LOCAL_PATH */ …)
}
```
Takeaways:
- Tests are **Kotlin + JUnit 5**, using **kotlinx.coroutines** for async control and **Ktor** as the
  HTTP client that talks to the sandbox REST API and the proxy control API.
- `junit-jupiter-params` adds **`@ParameterizedTest`** — used by integration specs to run their
  `json` / `msgpack` protocol variants as a single test parameterised on `useBinaryProtocol` (see §10.3).
  Its version is managed by the JUnit 5 BOM that `kotlin("test")` brings onto the test classpath, so no
  explicit version is pinned.
- It depends on `:java` (the SDK) and `:network-client-core` (the pluggable transport interfaces the
  mocks implement).
- The `--add-opens java.base/java.time` and `java.base/java.lang` flags grant reflective access into
  those JDK packages for the test runtime. They mirror the same flags set in `java/build.gradle.kts`
  for the SDK's own test module (which additionally opens `java.net` and `java.lang.reflect`).
- A system property carries an optional path to a **locally built** proxy binary (so you can test
  against an unreleased proxy).

### 4.2 Directory layout

Everything lives under the `io.ably.lib.uts` package, split cleanly into **infrastructure** (`infra/`,
no `@Test`s) and the **tests** themselves. Tests are organised **by tier, then by module**: `unit/` for
mocked-transport tests, and `integration/` for real-backend tests — the latter splitting again into
`standard/` (direct sandbox, happy-path) and `proxy/` (sandbox through the fault-injecting proxy). Under
each, a per-module folder (`realtime`, `liveobjects`, …) holds the actual test classes:

```text
uts/src/test/kotlin/io/ably/lib/uts/
├── deviations.md                        # the catalogue of SDK-vs-spec divergences
│
├── infra/                               # ── TEST INFRASTRUCTURE (no @Test methods) ──
│   ├── Utils.kt                         #   awaitState / awaitChannelState / pollUntil (shared)
│   │
│   ├── unit/                            #   UNIT infra (mocked transports)
│   │   ├── ClientFactories.kt           #     TestRealtimeClient / TestRestClient / ClientOptionsBuilder
│   │   ├── MockWebSocket.kt             #     fake WS transport + WebSocketMockConfig + CONNECTED_MESSAGE
│   │   ├── MockWebSocketEngineFactory.kt#     plugs the mock into the SDK's WebSocketEngine SPI
│   │   ├── MockHttpClient.kt            #     fake HTTP engine + HttpMockConfig
│   │   ├── MockHttpEngine.kt            #     plugs the mock into the SDK's HttpEngine SPI
│   │   ├── MockEvent.kt                 #     sealed log of everything on a mock transport
│   │   ├── PendingConnection.kt         #     interface: a connection attempt awaiting a response
│   │   ├── DefaultPendingConnection.kt  #     WS implementation of PendingConnection
│   │   ├── PendingRequest.kt            #     interface: an in-flight HTTP request awaiting a response
│   │   ├── DefaultPendingRequest.kt     #     HTTP implementation of PendingRequest
│   │   ├── FakeClock.kt                 #     virtual clock + virtual timers (deterministic time)
│   │   └── Utils.kt                     #     ConnectionDetails { } builder (reflective constructor)
│   │
│   └── integration/                     #   INTEGRATION infra (real backend)
│       ├── SandboxApp.kt                #     provisions/deletes a sandbox app
│       └── proxy/
│           ├── ProxyManager.kt          #       downloads/launches the uts-proxy binary
│           └── ProxySession.kt          #       proxy session: rules, actions, log + connectThroughProxy
│
├── unit/                                # ── UNIT TESTS (mock transport) ── · per module
│   ├── realtime/
│   │   └── ConnectionRecoveryTest.kt    #   ← the UNIT test (RTN16*)
│   └── liveobjects/                     #   (further modules as coverage grows)
│
└── integration/                         # ── INTEGRATION TESTS (real backend) ── · per module
    ├── standard/                        #   direct sandbox: happy-path, no fault injection
    │   ├── realtime/
    │   │   └── ChannelHistoryTest.kt    #   ← the DIRECT-SANDBOX test (RTL10d)
    │   └── liveobjects/
    └── proxy/                           #   sandbox through the fault-injecting uts-proxy
        ├── realtime/
        │   └── AuthReauthTest.kt        #   ← the PROXY test (RTN22, RTC8a)
        └── liveobjects/
```

The mental model: **`infra/unit/` powers the unit tests, `infra/integration/` powers both integration
kinds (`standard` + `proxy`), and `infra/Utils.kt` serves all of them.** Every tier is sub-divided **by
module** (`realtime`, `liveobjects`, …) so a feature's tests sit together regardless of SDK area. The
top-level `unit/` ↔ `infra/unit/` and `integration/` ↔ `infra/integration/` pairing is what the
`runUtsUnitTests` / `runUtsIntegrationTests` Gradle tasks key off (§13) — `runUtsIntegrationTests`
covers **both** `integration/standard/` and `integration/proxy/`.

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
advance it fires any due virtual timers **synchronously**, and wakes any `waitOn` sleepers. This is
how the unit test drives reconnection backoff and `connectionStateTtl` expiry **without real
sleeping**:
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

## 9. Walkthrough: the Unit Test (`ConnectionRecoveryTest`)

**File:** `uts/.../uts/unit/realtime/ConnectionRecoveryTest.kt` (package `io.ably.lib.uts.unit.realtime`)
**Tier:** Unit (mocked WebSocket, no network).
**Spec area:** RTN16 — connection recovery via the `recover` option and `createRecoveryKey()`.

It contains six tests; each carries an `@UTS realtime/unit/RTN16…/…` tag. Here's what each proves and
the technique it uses:

### 9.1 `RTN16g, RTN16g1` — recovery-key structure (incl. Unicode)
Connects (mock returns CONNECTED with a known key), attaches two channels — one ASCII, one Unicode
(`channel-éàü-世界`) — feeding each an `ATTACHED` with a `channelSerial` via `sendToClient`. Then calls
`connection.createRecoveryKey()`, decodes it with `RecoveryKeyContext.decode`, and asserts the
connection key, `msgSerial == 0`, and both channel serials survive — including a full
**encode→decode round-trip** to prove the Unicode name isn't corrupted (RTN16g1).
*Technique: callback-style `onConnectionAttempt`, `sendToClient` for ATTACHED, `awaitChannelState`.*

### 9.2 `RTN16g2` — `createRecoveryKey()` returns null in inactive states
The most elaborate test — it walks the connection through **five** states and asserts the key is null
in each inactive one:
- **INITIALIZED** (before connect) → null.
- **CONNECTED** → non-null (sanity).
- **CLOSING / CLOSED** → null (close nulls the key immediately).
- **FAILED** → null. *(Contains a documented **deviation** — see §12: the spec's fatal error
  code 50000/500 isn't treated as fatal by the SDK, and `send_to_client_and_close` races the FAILED
  transition; the test uses code 40000/400 and plain `sendToClient`.)*
- **SUSPENDED** → null. Built with a `FakeClock`: connect succeeds, then `simulateDisconnect()`,
  then a coroutine **refuses every reconnection attempt** while `fakeClock.advance(2.seconds)` loops
  until the short `connectionStateTtl` (800 ms) expires and the client gives up to SUSPENDED.
*Technique: this is the textbook example of **await-style** mocking — the first connection succeeds
via `awaitConnectionAttempt()`, but reconnections need the *refused* response, so a separate
`refuseJob` coroutine drives them; mixing this with fake timers gives deterministic SUSPENDED.*

### 9.3 `RTN16k` — `recover` adds the `recover` query param
Constructs the client with `recover = <recoveryKey>`, captures `conn.queryParams` on each connection
attempt, then `simulateDisconnect()` and reconnect. Asserts the **first** attempt carries
`recover=<key>` (and no `resume`), while the **second** (post-reconnect) carries `resume=<new key>`
(and no `recover`) — i.e. recover is a one-shot bootstrap, subsequent reconnections use resume.

### 9.4 `RTN16f` — `recover` initialises `msgSerial` *(env-gated deviation)*
Asserts the recovered `msgSerial` (42) is preserved. The SDK resets it to 0, so the spec-correct
assertion `assertEquals(42L, …)` runs only under `RUN_DEVIATIONS`; otherwise a regression-guard
`assertEquals(0L, …)` runs. (See §12.)

### 9.5 `RTN16f1` — malformed `recover` key degrades gracefully
`recover = "this-is-not-valid-json!!!"`. Asserts the client still connects normally with a fresh
identity, **no** `recover`/`resume` query params, and exactly one connection attempt — i.e. a bad key
is logged and ignored, not fatal.

### 9.6 `RTN16j` — `recover` instantiates channels with their serials (RTN16i too)
Recovery key carries three channels (incl. Unicode). Asserts each `channels.get(name).properties.
channelSerial` matches the key, that the channels are **NOT auto-attached** (state INITIALIZED —
RTN16i), and that a manual `attach()` sends an ATTACH frame carrying the recovered serial (verified
via `awaitNextMessageFromClient()`).

**What this test teaches about the infra:** callback vs await styles side by side, `FakeClock`-driven
SUSPENDED, `sendToClient` for server frames, `events`/`awaitNextMessageFromClient` for inspecting
client output, and the env-gated deviation pattern.

---

## 10. Walkthrough: the Direct-Sandbox Integration Test (`ChannelHistoryTest`)

**File:** `uts/.../uts/integration/standard/realtime/ChannelHistoryTest.kt` (package `io.ably.lib.uts.integration.standard.realtime`)
**Tier:** Direct-sandbox integration (real network, real Ably sandbox, **no** proxy, **no** fault injection).
**Spec point:** RTL10d — messages published by one realtime client are retrievable from a *separate*
client's `history()`.

This is the reference for the **middle tier**. Like a proxy test it talks to the real backend, but it
connects *straight* to `SandboxApp.sandboxHost` — there is no `ProxyManager`, no `ProxySession`, and no
`connectThroughProxy` wiring. It's the shape every happy-path interop spec
(connect/publish/subscribe/presence) follows.

### 10.1 Suite setup/teardown
Same `@TestInstance(PER_CLASS)` + `runBlocking` pattern as the proxy test, but provisioning **`SandboxApp`
only** — no `ProxyManager.ensureProxy()`:
```kotlin
@BeforeAll fun setUpAll()    = runBlocking { app = SandboxApp.create() }
@AfterAll  fun tearDownAll() = runBlocking { if (::app.isInitialized) app.delete() }
```

### 10.2 The client — wired straight to the sandbox
A tiny `newClient` helper points the **real** transport at the sandbox host (no proxy in between). Setting
explicit hosts auto-disables fallback hosts (REC2c2), so there's nothing else to configure:
```kotlin
private fun newClient(useBinaryProtocol: Boolean): AblyRealtime = TestRealtimeClient {
    key = app.defaultKey
    realtimeHost = SandboxApp.sandboxHost   // sandbox.realtime.ably-nonprod.net
    restHost     = SandboxApp.sandboxHost
    this.useBinaryProtocol = useBinaryProtocol
    autoConnect  = false
}
```
(`TestRealtimeClient` is the same builder the unit tests use — it just isn't fed any mocks here, so it
drives the SDK's real network transport instead of a `MockWebSocket`.)

### 10.3 Protocol variants — the `@ParameterizedTest` pattern
The spec declares a `PROTOCOL` dimension (`json` / `msgpack`) and says *each test runs once per variant*.
ably-java realises that with a JUnit 5 **parameterised test** over `useBinaryProtocol` — which is why the
module depends on `junit-jupiter-params` (§4.1):
```kotlin
@ParameterizedTest(name = "useBinaryProtocol={0}")
@ValueSource(booleans = [false, true])   // false = JSON, true = msgpack
fun `RTL10d - history contains messages published by another client`(useBinaryProtocol: Boolean) = runTest {
    …
}
```
A plain `@Test` test (no protocol dimension) stays a `@Test` — reach for `@ParameterizedTest` only when the
spec actually declares variants.

### 10.4 The scenario — real publish, real history
Two independent clients on the same app: the publisher's *confirmed* messages must appear in the
subscriber's history. The integration-specific techniques on show:
- **Awaiting a publish ack.** Realtime publish is fire-and-forget, so to honour the spec's `AWAIT publish`
  the test wraps the (non-deprecated) `publish(name, data, Callback<PublishResult>)` overload in a
  `suspendCancellableCoroutine` (`awaitPublish`), resuming on `onSuccess` and failing on `onError`. This is
  the integration analogue of the unit test's `awaitNextMessageFromClient()`.
- **`AWAIT attach()`** → `attach()` then `awaitChannelState(channel, ChannelState.attached)`.
- **Polling real REST state.** `history()` is a blocking REST call against the sandbox and the message
  store is eventually-consistent, so the test
  `pollUntil(10.seconds, 500.milliseconds) { subChannel.history(null).items().size == 3 }` — never a fixed
  sleep (the same anti-flake rule as the other tiers).
- **Order assertion.** History defaults to newest-first, so `items[0]` is `event3` … `items[2]` is `event1`.

**What this test teaches about the infra:** `SandboxApp`-only provisioning, the direct-sandbox client
wiring (`realtimeHost`/`restHost` from `SandboxApp.sandboxHost`, no proxy), the protocol-variant
`@ParameterizedTest`, awaiting a publish ack via `Callback<PublishResult>`, and `pollUntil` over a real
REST `history()` call.

---

## 11. Walkthrough: the Proxy Test (`AuthReauthTest`)

**File:** `uts/.../uts/integration/proxy/realtime/AuthReauthTest.kt` (package `io.ably.lib.uts.integration.proxy.realtime`)
**Tier:** Proxy integration (real sandbox + uts-proxy).
**Spec points:** RTN22 (server-initiated re-authentication) and RTC8a (the client sends an AUTH
frame with renewed auth details). Unit-test counterparts: `server_initiated_reauth_test.md`,
`realtime_authorize.md`.

### 11.1 Suite setup/teardown
```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)   // one instance, so @BeforeAll can be non-static
class AuthReauthTest {
    @BeforeAll fun setUpAll() = runBlocking {
        ProxyManager.ensureProxy()                // download+launch proxy if needed
        app = SandboxApp.create()                 // provision a real sandbox app
    }
    @AfterAll fun tearDownAll() = runBlocking { if (::app.isInitialized) app.delete() }
}
```

### 11.2 The test, step by step
1. **Create a session with no rules** — the fault will be injected *imperatively* later (late
   injection — the connect handshake runs against the real server unmodified):
   ```kotlin
   val session = ProxySession.create(rules = emptyList())
   ```
2. **Auth via `authCallback`** — the spec generates a JWT from the sandbox key; the idiomatic
   ably-java equivalent is a locally-signed `TokenRequest` from the same key (no external JWT
   library). A counter records how many times the callback is invoked:
   ```kotlin
   val tokenSigner = AblyRest(app.defaultKey)
   val authCallback = Auth.TokenCallback { params ->
       authCallbackCount.incrementAndGet()
       tokenSigner.auth.createTokenRequest(params, null)
   }
   ```
3. **Build the client through the proxy** and connect (JSON stays on so the proxy can inspect
   frames):
   ```kotlin
   val client = TestRealtimeClient {
       this.authCallback = authCallback
       connectThroughProxy(session)
       autoConnect = false
   }
   client.connect()
   awaitState(client, ConnectionState.connected, 15.seconds)
   ```
4. **Snapshot identity** — `connection.id` and the callback count, and assert the callback already
   ran ≥ 1 (initial auth).
5. **Start recording state changes**, then **inject a server-initiated AUTH** (protocol action 17)
   imperatively — simulating Ably asking the client to re-authenticate:
   ```kotlin
   session.triggerAction(mapOf("type" to "inject_to_client",
                               "message" to mapOf("action" to 17)))
   ```
6. **Wait for the re-auth round-trip** with `pollUntil { stateChanges.size > 1 }` (real network, so
   poll — don't sleep).
7. **Assertions** prove RTN22 + RTC8a:
   - `authCallback` was invoked **again** (count incremented) → re-auth was triggered.
   - Connection is still **CONNECTED** and `connection.id` is **unchanged** → re-auth does not
     reconnect.
   - **No** transitions away from CONNECTED were recorded.
   - The **proxy event log** contains a client→server **AUTH frame (action 17) carrying non-null
     `auth` details** (RTC8a) — verified by filtering `session.getLog()`.
8. **Nested teardown** in `finally`: close the client and wait for CLOSED, then always close the
   session and the token signer.

**What this test teaches about the infra:** `ProxyManager.ensureProxy` + `SandboxApp` setup,
`connectThroughProxy`, **late imperative fault injection** via `triggerAction`, real-network waiting
with `pollUntil`, and **proxy-log assertions** as the primary verification (`getLog()` →
filter by `type`/`direction`/`message.action`).

---

## 12. Deviations: when the SDK disagrees with the spec

`uts/.../io/ably/lib/uts/deviations.md` is the single catalogue of every place the ably-java SDK behaves
differently from the features spec, discovered during translation. Each entry records: the **spec
point**, **what the spec requires**, **what the SDK does**, the **root cause** (file/function, where
known), the **workaround in tests**, and the **affected tests**.

The mechanism (from [`writing-derived-tests.md`](https://github.com/ably/specification/blob/main/uts/docs/writing-derived-tests.md)): the test keeps the **spec-correct** assertion but
gates it behind the `RUN_DEVIATIONS` env var, with a regression-guard assertion for the SDK's actual
behaviour running by default. Normal runs stay green; `RUN_DEVIATIONS=1` turns the failing assertions
on so the gap is reproducible and the test flips automatically once the SDK is fixed.

Current entries relevant to the walkthrough tests:

| Spec point | Gist | Touches |
|------------|------|---------|
| **RTN16f** | SDK resets `msgSerial` to 0 on connect even with `recover`; spec says preserve it (42). | `ConnectionRecoveryTest` (§9.4) — `assertEquals(42L,…)` gated, `assertEquals(0L,…)` default guard. |
| **RTN16g2** | Spec's fatal error 50000/500 isn't fatal to the SDK (`isFatalError()` needs code 40000–49999 or status < 500); also `send_to_client_and_close` races the FAILED transition. | `ConnectionRecoveryTest` (§9.2) — uses 40000/400 + plain `sendToClient`. |
| **RTL13b** | `ATTACHING → SUSPENDED` via `realtimeRequestTimeout` not implemented for channel attach. | various channel tests (not the walkthroughs here). |
| **RTL13c** | `channelRetryTimeout` not cancelled when the connection leaves CONNECTED. | various channel tests; assertions gated behind `RUN_DEVIATIONS`. |

> These deviations are **valuable output**, not failures — each one is a precise, reproducible bug
> report the SDK team can act on, and the gated test becomes the acceptance test for the fix.

---

## 13. How to Run the Tests

There are two custom Gradle tasks (registered in `uts/build.gradle.kts`), filtered by package — they
mirror `runLiveObjectsUnitTests` / `runLiveObjectsIntegrationTests` in the `liveobjects` module:

```bash
# Unit tests only — io.ably.lib.uts.unit.*  (fast, no network). This is the PR gate.
./gradlew :uts:runUtsUnitTests

# Integration tests only — io.ably.lib.uts.integration.*  (real sandbox; covers both
# integration/standard/ and integration/proxy/ — proxy tests also download/launch the uts-proxy).
./gradlew :uts:runUtsIntegrationTests

# Everything (the default Test task still runs both):
./gradlew :uts:test

# Just one test class (works with any of the tasks above):
./gradlew :uts:runUtsUnitTests --tests "io.ably.lib.uts.unit.realtime.ConnectionRecoveryTest"
./gradlew :uts:runUtsIntegrationTests --tests "io.ably.lib.uts.integration.proxy.realtime.AuthReauthTest"

# Turn on the spec-correct (currently failing) deviation assertions:
RUN_DEVIATIONS=1 ./gradlew :uts:runUtsUnitTests --tests "*ConnectionRecoveryTest*"

# Run proxy tests against a locally built proxy instead of a GitHub release:
./gradlew :uts:runUtsIntegrationTests -Duts.proxy.localPath=/path/to/uts-proxy   # or .tar.gz
#   (equivalently: export UTS_PROXY_LOCAL_PATH=/path/to/uts-proxy)
```

**Where CI runs them:** `runUtsUnitTests` is part of the `check.yml` gate (alongside
`runLiveObjectsUnitTests`); `runUtsIntegrationTests` runs in the `check-uts` job of
`integration-test.yml` (alongside `check-liveobjects`).

Notes:
- `ProxyManager` **advises** running proxy suites single-fork (`maxParallelForks = 1`) because they
  share the control port (10100). This is not currently set in `uts/build.gradle.kts`; it isn't
  exercised yet because there is only one proxy test class.
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

A one-stop table of every Kotlin source file under `uts/src/test/` and the SDK seams they use, so
nothing is left implicit.

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
| `unit/realtime/ConnectionRecoveryTest.kt` | 6 `@Test`s: RTN16g/g1, RTN16g2, RTN16k, RTN16f, RTN16f1, RTN16j | Unit tier (`io.ably.lib.uts.unit.realtime`) — connection recovery (mocked WS, FakeClock, env-gated deviations). |
| `integration/standard/realtime/ChannelHistoryTest.kt` | 1 `@ParameterizedTest` (RTL10d) × {JSON, msgpack} | Direct-sandbox tier (`io.ably.lib.uts.integration.standard.realtime`) — cross-client history durability (`SandboxApp`, no proxy; awaited publish + `pollUntil` on `history()`). |
| `integration/proxy/realtime/AuthReauthTest.kt` | 1 `@Test` (two `@UTS`: RTN22, RTC8a) | Integration tier (`io.ably.lib.uts.integration.proxy.realtime`) — server-initiated re-authentication. |
| `deviations.md` | RTN16f, RTN16g2, RTL13b, RTL13c | Catalogue of SDK-vs-spec divergences. |

> **Coverage note:** this guide walks through **one reference test per tier** — `ConnectionRecoveryTest`
> (unit, §9), `ChannelHistoryTest` (direct-sandbox, §10), and `AuthReauthTest` (proxy, §11). The `uts/`
> module additionally carries LiveObjects tests across the same tiers, and the infrastructure under
> `infra/unit/` and `infra/integration/` is built out beyond what any single test exercises (full HTTP
> mock, all four rule builders, REST proxy wiring, etc.), anticipating the broader UTS coverage
> catalogued in [`completion-status.md`](https://github.com/ably/specification/blob/main/uts/docs/completion-status.md).

---

### Source map (where each fact in this doc comes from)

| Topic | File |
|-------|------|
| Authoring portable specs, test IDs, mock pseudocode | [`uts/docs/writing-test-specs.md`](https://github.com/ably/specification/blob/main/uts/docs/writing-test-specs.md) |
| Translating specs, deviation patterns, decision tree | [`uts/docs/writing-derived-tests.md`](https://github.com/ably/specification/blob/main/uts/docs/writing-derived-tests.md) |
| Integration/proxy policy, late fault injection, tiers | [`uts/docs/integration-testing.md`](https://github.com/ably/specification/blob/main/uts/docs/integration-testing.md) |
| Coverage matrix | [`uts/docs/completion-status.md`](https://github.com/ably/specification/blob/main/uts/docs/completion-status.md) |
| Proxy control API, rule format, action numbers | [`uts/realtime/integration/helpers/proxy.md`](https://github.com/ably/specification/blob/main/uts/realtime/integration/helpers/proxy.md) |
| SDK seams | `lib/.../debug/DebugOptions.java`, `lib/.../util/Clock.java` |
| Module wiring | `uts/build.gradle.kts`, `settings.gradle.kts` |
| Unit mocks | `uts/.../uts/infra/unit/*` |
| Integration helpers | `uts/.../uts/infra/integration/*` (+ `…/integration/proxy/*`) |
| Async helpers | `uts/.../uts/infra/Utils.kt` (awaits), `…/uts/infra/unit/Utils.kt` (ConnectionDetails builder) |
| The three example tests | `…/uts/unit/realtime/ConnectionRecoveryTest.kt`, `…/uts/integration/standard/realtime/ChannelHistoryTest.kt`, `…/uts/integration/proxy/realtime/AuthReauthTest.kt` |
| Deviations | `uts/.../io/ably/lib/uts/deviations.md` |
