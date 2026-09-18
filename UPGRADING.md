# Upgrading from 1.x to 2.0

2.0 splits the SDK into one artifact per side of the connection: your code now declares whether it
runs on an end-user device or on infrastructure you control.

| 1.x | 2.0 |
|-----|-----|
| `io.ably:ably-java` | `io.ably.pubsub:server` (backend) or `io.ably.pubsub:device` (end-user runtime) |
| `io.ably:ably-android` | `io.ably.pubsub:device` |
| `new AblyRealtime(options)` | `PubSubServer.realtimeClientBuilder()…build()` or `PubSubDevice.clientBuilder()…build()` |
| `new AblyRest(options)` | `PubSubServer.httpClientBuilder()…build()` |
| `io.ably.lib.*` | `io.ably.pubsub.*` |
| `io.ably.lib.rest.*` | `io.ably.pubsub.http.*` |
| `AblyRealtime`, `AblyRest` | `PubSubRealtimeClient`, `PubSubHttpClient` |

## 1. Choose the artifact for your side

| Artifact | For | Entry point |
|----------|-----|-------------|
| `io.ably.pubsub:device` | Android apps and other end-user runtimes | `PubSubDevice.clientBuilder(…)` |
| `io.ably.pubsub:server` | Servers and other trusted backend environments | `PubSubServer.realtimeClientBuilder(…)`, `PubSubServer.httpClientBuilder(…)` |

Pick by where the code runs, not by which client you want: both doors give you a realtime client,
and connectionless operations (history, presence queries, token requests, `request()`,
`batchPublish()`) are available on it either way.

Gradle:

```gradle
// Backend / trusted server environment (JVM)
implementation 'io.ably.pubsub:server:2.0.0'

// End-user device (Android app, or a JVM desktop client)
implementation 'io.ably.pubsub:device:2.0.0'
```

`device` is a Kotlin Multiplatform artifact, published as `device` (which Gradle resolves to the
right variant) plus `device-android` and `device-jvm`. Maven cannot resolve Gradle variants, so a
Maven consumer names `device-jvm` directly.

**Android minimum API level.** `io.ably.pubsub:device` requires **API 24**. 1.x `io.ably:ably-android`
supported API 19, so an app that still targets 19–23 cannot move to the device artifact yet; stay on
1.x for now and open an issue so we can weigh the floor.

The remaining published artifacts change group only, and all release in lockstep at the same version:
`io.ably:liveobjects` → `io.ably.pubsub:liveobjects`, and likewise `network-client-core`,
`network-client-default`, `network-client-okhttp` and `pubsub-adapter`.

## 2. Build clients through the door builders

The client constructors are no longer public. Construct through the builder of the artifact you
depend on — that call is what declares your side.

**Before (1.x):**

```java
import io.ably.lib.realtime.AblyRealtime;
import io.ably.lib.rest.AblyRest;

AblyRealtime realtime = new AblyRealtime("xVLyHw.MHOCLg:...");

ClientOptions options = new ClientOptions(apiKey);
options.clientId = "bob";
AblyRealtime configured = new AblyRealtime(options);

AblyRest rest = new AblyRest(apiKey);
```

**After (2.0), on a server:**

```java
import io.ably.pubsub.realtime.PubSubRealtimeClient;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.server.PubSubServer;

PubSubRealtimeClient realtime = PubSubServer.realtimeClientBuilder()
    .key("xVLyHw.MHOCLg:...")
    .build();

PubSubRealtimeClient configured = PubSubServer.realtimeClientBuilder()
    .key(apiKey)
    .clientId("bob")
    .build();

PubSubHttpClient http = PubSubServer.httpClientBuilder()
    .key(apiKey)
    .build();
```

**After (2.0), on a device:**

```kotlin
import io.ably.pubsub.device.PubSubDevice

val client = PubSubDevice.clientBuilder()
    .key("xVLyHw.MHOCLg:...")
    .clientId("bob")
    .build()
```

Note that the options object is handed to the client rather than copied, and `build()` replaces its
`agents` map with the stamped one; pass `ClientOptions.copy()` if either matters. Your own `agents`
entries are preserved — only the side entry is applied last and cannot be overridden.

`RealtimeClientFactory` and `HttpClientFactory` exist in the clients' own packages as the seam the
doors construct through. They are not application API; use the builders.

## 3. Update packages and class names

Every package moved from `io.ably.lib.*` to `io.ably.pubsub.*`, and the REST-named types moved with
it into `io.ably.pubsub.http`:

| 1.x | 2.0 |
|-----|-----|
| `io.ably.lib.realtime.AblyRealtime` | `io.ably.pubsub.realtime.PubSubRealtimeClient` |
| `io.ably.lib.rest.AblyRest` | `io.ably.pubsub.http.PubSubHttpClient` |
| `io.ably.lib.rest.Auth` | `io.ably.pubsub.http.Auth` |
| `io.ably.lib.rest.DeviceDetails` | `io.ably.pubsub.http.DeviceDetails` |
| `io.ably.lib.rest.RestAnnotations` | `io.ably.pubsub.http.HttpAnnotations` |
| `io.ably.lib.http.*` (the rest) | `io.ably.pubsub.http.*` |
| `io.ably.lib.types.*`, `io.ably.lib.realtime.*`, `io.ably.lib.push.*`, … | `io.ably.pubsub.types.*`, `io.ably.pubsub.realtime.*`, `io.ably.pubsub.push.*`, … |

For most codebases the import rewrite is mechanical. Rewrite the REST package first, since it is
the one that does not simply map by prefix:

```bash
# GNU sed; on macOS use `sed -i ''`
find src -name '*.java' -o -name '*.kt' | xargs sed -i \
  -e 's/io\.ably\.lib\.rest/io.ably.pubsub.http/g' \
  -e 's/io\.ably\.lib/io.ably.pubsub/g' \
  -e 's/AblyRealtime/PubSubRealtimeClient/g' \
  -e 's/AblyRest/PubSubHttpClient/g'
```

## 4. Deprecated API removed

2.0 drops every member 1.x had marked deprecated.

| Removed | Use instead |
|---------|-------------|
| `new AblyRealtime(…)`, `new AblyRest(…)` | `PubSubDevice.clientBuilder()` / `PubSubServer.realtimeClientBuilder()` / `PubSubServer.httpClientBuilder()` |
| `ChannelOptions.fromCipherKey(byte[])`, `fromCipherKey(String)` | `ChannelOptions.withCipherKey(…)` |
| `Connection.recoveryKey` | `Connection.createRecoveryKey()` |
| `Connection.emit/on/once(ConnectionState, …)` | The `ConnectionEvent`-keyed `EventEmitter` methods, e.g. `connection.on(ConnectionEvent.connected, listener)` |
| `Channel.publish(…, CompletionListener)`, `publishAsync(…, CompletionListener)` | The `Callback<PublishResult>` overloads, e.g. `publish(name, data, callback)` |
| `Channel.sync()` | Nothing. It was a no-op logging stub, intended only for internal testing per RTP19. |
| `Auth.authorise(…)` | `Auth.authorize(…)` |
| `Auth.renew()` | `Auth.renewAuth(…)`, which reports completion instead of returning early |
| `TokenDetails.fromJSON(…)`, `TokenRequest.fromJSON(…)` | `fromJsonElement(…)` |
| `ClientOptions.fallbackHostsUseDefault` | Drop it. Default fallback hosts apply automatically; set `fallbackHosts` only for custom hosts. |
| `RegistrationToken.Type.GCM` (Android) | `RegistrationToken.Type.FCM` |
