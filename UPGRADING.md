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

Remove `io.ably:ably-java` and `io.ably:ably-android` (1.x) from your build when you add the new
artifact. Depend on exactly one door per app, and never on
`io.ably.pubsub:core` or `io.ably.pubsub:core-android` directly: they are internal implementation
artifacts. Read [Behaviour changes](#4-behaviour-changes) before upgrading, since several 1.x calls
behave differently at runtime.

## 1. Choose the artifact for your side

| Artifact | For | Entry point |
|----------|-----|-------------|
| `io.ably.pubsub:device` | Android apps and other end-user runtimes | `PubSubDevice.clientBuilder(…)` |
| `io.ably.pubsub:server` | Servers and other trusted backend environments | `PubSubServer.realtimeClientBuilder(…)`, `PubSubServer.httpClientBuilder(…)` |

Pick by where the code runs, not by which client you want: both doors give you a realtime client,
and connectionless operations (history, presence queries, token requests, `request()`,
`batchPublish()`) are available on it either way.

Use exactly one of `io.ably.pubsub:server` or `io.ably.pubsub:device` in an app. On Android, `server`
brings `core` and `device` brings `core-android`, which contain the same `io.ably.pubsub` classes, so
declaring both, or declaring `core` or `core-android` yourself, puts duplicate classes on the
classpath; on the JVM both resolve to `core`. Either way, declaring both makes your side ambiguous. If one module of your app runs on
a device and another on a server, give each module its own door and keep them in separate artifacts.

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
    .authUrl("https://your-server.example.com/ably-auth")
    .clientId("bob")
    .build()
```

Never embed an API key in an application that runs on an end-user device. Authenticate devices with
tokens issued by your own server, through `authUrl(...)` or `authCallback(...)`.

The door's `build()` hands the client a copy of the options (`ClientOptions.copy()`). Reusing a builder,
or changing the `ClientOptions` you passed to `clientBuilder(options)` after `build()`, no longer
affects clients that were already built. Your own `agents` entries are preserved; the door's side
entry is applied to the copy and cannot be overridden, and a caller-supplied identifier for the
opposite side (for example `ably-pubsub-server` on a device client) is removed.

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
| `io.ably.lib.rest.AblyBase` | `io.ably.pubsub.http.PubSubBase` |
| `io.ably.lib.rest.ChannelBase`, `io.ably.lib.rest.MessageEditsMixin` | `io.ably.pubsub.http.ChannelBase`, `io.ably.pubsub.http.MessageEditsMixin` |
| `io.ably.lib.http.Http` | `io.ably.pubsub.http.HttpRequester` |
| `io.ably.lib.http.*` (the rest) | `io.ably.pubsub.http.*` |
| `io.ably.lib.network.*` | `io.ably.pubsub.network.*` |
| `io.ably.lib.liveobjects.*` | `io.ably.pubsub.liveobjects.*` |
| `Defaults.HOST_REST`, `Defaults.HOST_REALTIME` | Removed. Use `endpoint` (`Defaults.ENDPOINT` is the default, `main`); see [Choose the host with `endpoint`](#6-choose-the-host-with-endpoint) |
| `Defaults.HOST_FALLBACKS` | Kept, but now holds the default endpoint's fallback domains, `main.[a-e].fallback.ably-realtime.com` |
| `io.ably.lib.types.*`, `io.ably.lib.realtime.*`, `io.ably.lib.push.*`, … | `io.ably.pubsub.types.*`, `io.ably.pubsub.realtime.*`, `io.ably.pubsub.push.*`, … |

`ClientOptions.idempotentRestPublishing` deliberately keeps its name, which is the specification's (TO3n).

For most codebases the import rewrite is mechanical. Apply the specific renames first and the generic
package rules last, and keep the word boundaries so that identifiers merely containing `AblyRest` or
`AblyRealtime` are left alone. `perl` behaves the same on macOS and Linux (BSD `sed` has no `\b`):

```bash
find src \( -name '*.java' -o -name '*.kt' \) -print0 | xargs -0 perl -pi -e '
  s/\bio\.ably\.lib\.http\.Http\b/io.ably.pubsub.http.HttpRequester/g;
  s/\bAblyBase\b/PubSubBase/g;
  s/\bRestAnnotations\b/HttpAnnotations/g;
  s/\bio\.ably\.lib\.rest\b/io.ably.pubsub.http/g;
  s/\bio\.ably\.lib\b/io.ably.pubsub/g;
  s/\bAblyRealtime\b/PubSubRealtimeClient/g;
  s/\bAblyRest\b/PubSubHttpClient/g;
'
```

The script renames only the fully qualified `io.ably.lib.http.Http`. Usages of the simple name `Http`
are left alone (a word-bounded rule would clobber unrelated `Http` identifiers), so rename them to
`HttpRequester` by hand.

The script rewrites names only. You still need to replace `new AblyRealtime(...)` and `new AblyRest(...)`
with the door builders (see above), and review the [behaviour changes](#4-behaviour-changes) by hand.

## 4. Behaviour changes

### `Channels.release()` no longer detaches the channel (RTS4c-e)

A channel can be released only when it is `INITIALIZED`, `DETACHED` or `FAILED`. In any other state
(`ATTACHING`, `ATTACHED`, `DETACHING`, `SUSPENDED`) the realtime `Channels.release()` throws an
`AblyException` with code 90011 (status 400) and leaves the channel unchanged. Releasing a channel
that does not exist is a no-op. The method now declares the checked `AblyException`, so Java callers
must handle it. Detach first and wait for the detach to complete. Releasing a channel on the HTTP
client is unchanged.

**Before (1.x):**

```java
ably.channels.release("some-channel");   // detached, then removed
```

**After (2.0):**

```java
Channel channel = realtime.channels.get("some-channel");
channel.once(ChannelState.detached, stateChange -> {
    try {
        realtime.channels.release("some-channel");
    } catch (AblyException e) {
        // 90011 if the channel is no longer in a releasable state
    }
});
channel.detach();   // throws AblyException: call it from a method that declares it, or catch it
```

Closing the client detaches its channels, and the client and its channels become collectable together,
so you do not need to release them one by one.

### Requests identify as `ably-pubsub-java`

The `Ably-Agent` header, and the `agent` query parameter of the realtime connection, now start with
`ably-pubsub-java/<version>` instead of `ably-java/<version>`, and carry a bare `ably-pubsub-server`
or `ably-pubsub-device` entry according to the door used. Update anything that matches on
`ably-java/`, such as proxy rules or log queries. Entries you add through `agents(...)` are kept.
The door adds its side entry to a copy of your options, removes the opposite side's entry, and the
side entry cannot be overridden. After `ably-pubsub-java/<version>`, the additional entries
(yours and the side entry) are sent in alphabetical order, followed by the platform entry.

### Server clients and token authentication

Clients from `PubSubServer` declare themselves as servers. Authenticate them with an API key. Token
authentication on a realtime connection works only with a token that carries the signed
`x-ably-clientType=server` claim, which today means a JWT; otherwise Ably rejects the connection with
error 40167. Tokens without the signed claim (for example Ably native tokens, whether from `authCallback`,
`token` or `tokenDetails`) are rejected. Clients from `PubSubDevice` are not affected: on a device, authenticate with tokens issued by
your own server (`authCallback` or `authUrl`), never with an API key.

### Options are copied when a client is built

`build()` on each door passes a copy of the options to the client, so a builder can be reused and
the options handed to `clientBuilder(options)` can be reassigned afterwards without affecting clients
that were already built. The copy is shallow: the `headers`, `agents`, `fallbackHosts` and
`transportParams` maps and arrays are copied, but nested objects such as `proxy`, `authParams`,
callbacks and listeners are shared. See the end of [section 2](#2-build-clients-through-the-door-builders).

### Other changes

- **Re-authentication.** When Ably asks a connected client to re-authenticate, the client now emits
  `UPDATE` once `renewAuth` completes, so the event reports the outcome of the re-authentication
  instead of firing before it has finished.
- **Android push registration token.** The push registration token type is now persisted by name
  rather than by enum ordinal. A token stored by an earlier 1.x release as FCM is migrated and kept.
  A stored GCM token (GCM is no longer supported), or an unrecognised stored value, is discarded, and
  the device registers with FCM again on next activation.
- **Log tags.** Log tags are class names, so they changed with the packages (for example
  `io.ably.lib.rest.AblyRest` became `io.ably.pubsub.http.PubSubHttpClient`). Update logcat filters
  and `LogHandler` matchers that match on tags.

## 5. Deprecated API removed

2.0 drops every member 1.x had marked deprecated.

| Removed | Use instead |
|---------|-------------|
| `new AblyRealtime(…)`, `new AblyRest(…)` | `PubSubDevice.clientBuilder()` / `PubSubServer.realtimeClientBuilder()` / `PubSubServer.httpClientBuilder()` |
| `ChannelOptions.fromCipherKey(byte[])`, `fromCipherKey(String)` | `ChannelOptions.withCipherKey(…)` |
| `Connection.recoveryKey` | `Connection.createRecoveryKey()` |
| `Connection.emit/on/once(ConnectionState, …)` | The `ConnectionEvent`-keyed `EventEmitter` methods, e.g. `connection.on(ConnectionEvent.connected, listener)` |
| Realtime `Channel.publish(…, CompletionListener)` | The `Callback<PublishResult>` overloads, e.g. `publish(name, data, callback)` |
| HTTP client `Channel.publishAsync(…, CompletionListener)` | `publishAsync(name, data, Callback<PublishResult>)`; the HTTP channel has no `publish(…, callback)` overload |
| `Channel.sync()` | Nothing. It was a no-op logging stub, intended only for internal testing per RTP19. |
| `Auth.authorise(…)` | `Auth.authorize(…)` |
| `Auth.renew()` | `Auth.renewAuth(…)`, which reports completion instead of returning early |
| `TokenDetails.fromJSON(…)`, `TokenRequest.fromJSON(…)` | `fromJsonElement(…)` |
| `ClientOptions.fallbackHostsUseDefault` | Drop it. Default fallback hosts apply automatically, except with a hostname endpoint, where you must pass `fallbackHosts` explicitly (see [endpoint](#6-choose-the-host-with-endpoint)). |
| `RegistrationToken.Type.GCM` (Android) | `RegistrationToken.Type.FCM` |

## 6. Choose the host with `endpoint`

2.0 replaces (REC1) the `ClientOptions.environment`, `restHost` and `realtimeHost` fields with a single
`ClientOptions.endpoint` field; the door builders also offer `endpoint(…)`. REST requests and the realtime connection now always use the
same host.

| 1.x | 2.0 |
|-----|-----|
| No host options | No change; traffic moves to `main.realtime.ably.net` (see below) |
| `environment = "sandbox"` | `endpoint = "nonprod:sandbox"` |
| `environment = "acme"` (dedicated cluster) | `endpoint = "acme"` |
| `restHost` / `realtimeHost = "localhost"` | `endpoint = "localhost"` |
| `restHost` and `realtimeHost` set to the same custom host | `endpoint = "<that host>"` |
| `restHost = h` with `fallbackHostsUseDefault = true` | `endpoint = h` plus an explicit `fallbackHosts` of `main.a.fallback.ably-realtime.com` to `main.e.fallback.ably-realtime.com` (the five `main.[a-e]` hosts). A hostname endpoint has no default fallback hosts (REC2c2). |
| `restHost` with a non-default `port` or `tlsPort` | `endpoint = "<host>"` plus `port` / `tlsPort` |
| `restHost` and `realtimeHost` set to different hosts | Not supported; use one host that serves both, or contact Ably |
| Custom `fallbackHosts` | Unchanged |

Never put a port in `endpoint`. Set `port` or `tlsPort` instead. A value such as `localhost:8080`
is not recognised as a hostname (it contains no `.` or `::` and is not exactly `localhost`), so it is
treated as a routing policy name and resolves to `localhost:8080.realtime.ably.net`.

How `endpoint` resolves (REC1, REC2c):

| `endpoint` | Primary host | Default fallback hosts |
|------------|--------------|------------------------|
| Unset | `main.realtime.ably.net` | `main.[a-e].fallback.ably-realtime.com` |
| A routing policy name, e.g. `acme` | `acme.realtime.ably.net` | `acme.[a-e].fallback.ably-realtime.com` |
| `nonprod:` plus a name, e.g. `nonprod:sandbox` | `sandbox.realtime.ably-nonprod.net` | `sandbox.[a-e].fallback.ably-realtime-nonprod.com` |
| A hostname (contains `.` or `::`, or is `localhost`) | The value as given | None |

An explicit `fallbackHosts` always replaces the default fallback hosts. A custom `port` or `tlsPort`
no longer disables the default fallback hosts.

**Before (1.x):**

```java
ClientOptions options = new ClientOptions("xVLyHw.MHOCLg:...");
options.environment = "sandbox";
AblyRealtime realtime = new AblyRealtime(options);
```

**After (2.0):**

```java
PubSubRealtimeClient realtime = PubSubServer.realtimeClientBuilder()
    .key("xVLyHw.MHOCLg:...")
    .endpoint("nonprod:sandbox")
    .build();
```

**Firewalls and proxies.** 1.x connected to `rest.ably.io`, `realtime.ably.io` and
`[a-e].ably-realtime.com` by default. If your network restricts outbound traffic, allow
`*.realtime.ably.net` and `*.fallback.ably-realtime.com` before upgrading. If you use a `nonprod:`
endpoint, also allow `*.realtime.ably-nonprod.net` and `*.fallback.ably-realtime-nonprod.com`.
