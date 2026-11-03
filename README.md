![Ably Pub/Sub Java Header](https://raw.githubusercontent.com/ably/ably-pubsub-java/main/images/javaSDK-github.png)

[![Maven Central: server](https://img.shields.io/maven-central/v/io.ably.pubsub/server?label=io.ably.pubsub%3Aserver)](https://central.sonatype.com/artifact/io.ably.pubsub/server)
[![Maven Central: device](https://img.shields.io/maven-central/v/io.ably.pubsub/device?label=io.ably.pubsub%3Adevice)](https://central.sonatype.com/artifact/io.ably.pubsub/device)
[![License](https://badgen.net/github/license/ably/ably-pubsub-java)](https://github.com/ably/ably-pubsub-java/blob/main/LICENSE)

# Ably Pub/Sub Java SDK

The Java and Kotlin SDK for [Ably Pub/Sub](https://ably.com/docs/basics): publish and subscribe to
channels, and use message history, presence and push notifications, from servers, Android apps and
JVM desktop clients.

## Overview

The SDK is published as two packages, one for each side of the connection. You choose the package
by where your code runs, and build your client through that package's entry point.

| Package | Use it for | Entry point |
|---------|------------|-------------|
| `io.ably.pubsub:server` | Servers and other trusted backend environments | `PubSubServer.realtimeClientBuilder()`, `PubSubServer.httpClientBuilder()` |
| `io.ably.pubsub:device` | Android apps and other end-user runtimes, including JVM desktop apps | `PubSubDevice.clientBuilder()` |

## Requirements

Java 8 or later (the JVM, plus Android 7.0 / API level 24 or later for `io.ably.pubsub:device`).

## Installation

Use exactly one of `io.ably.pubsub:server` or `io.ably.pubsub:device` in an app.

### Server

```kotlin
dependencies {
    implementation("io.ably.pubsub:server:2.0.0")
}
```

### Device

On Android and on the JVM, Gradle picks the right variant of `io.ably.pubsub:device`:

```kotlin
dependencies {
    implementation("io.ably.pubsub:device:2.0.0")
}
```

Maven can't pick the variant, so a JVM desktop app names `device-jvm` directly:

```xml
<dependency>
    <groupId>io.ably.pubsub</groupId>
    <artifactId>device-jvm</artifactId>
    <version>2.0.0</version>
</dependency>
```

## Quick start

### Server

Use an API key on a server. The samples read it from the `ABLY_API_KEY` environment variable; set it
to an API key from your [Ably dashboard](https://ably.com/accounts/any/apps/any/app_keys). The
following code connects, subscribes to a channel, publishes a message to it and waits for the
message to arrive.

```java
import java.util.concurrent.CompletableFuture;

import io.ably.pubsub.realtime.Channel;
import io.ably.pubsub.realtime.PubSubRealtimeClient;
import io.ably.pubsub.server.PubSubServer;
import io.ably.pubsub.types.Message;

public class ServerQuickStart {
    public static void main(String[] args) throws Exception {
        String ablyApiKey = System.getenv("ABLY_API_KEY");
        PubSubRealtimeClient realtime = PubSubServer.realtimeClientBuilder()
            .key(ablyApiKey)
            .build();

        // Subscribing attaches the channel. A message published while connecting is queued.
        Channel channel = realtime.channels.get("test-channel");
        CompletableFuture<Message> received = new CompletableFuture<>();
        channel.subscribe(received::complete);
        channel.publish("test-event", "hello world");

        System.out.println("Received: " + received.get().data);
        realtime.close();
    }
}
```

To publish over HTTP without a realtime connection, build an HTTP client instead:

```java
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.server.PubSubServer;
import io.ably.pubsub.types.AblyException;

public class ServerPublish {
    public static void main(String[] args) throws AblyException {
        String ablyApiKey = System.getenv("ABLY_API_KEY");
        PubSubHttpClient http = PubSubServer.httpClientBuilder()
            .key(ablyApiKey)
            .build();

        http.channels.get("test-channel").publish("test-event", "hello world");
    }
}
```

> [!IMPORTANT]
> Server clients must authenticate with an API key. To use a token instead, see [Server clients and token authentication](https://ably.com/docs/auth/token#server-token-auth).

Your server also issues the tokens that devices authenticate with. Return a token request from
the endpoint your devices use as their `authUrl`:

```java
import io.ably.pubsub.http.Auth;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.server.PubSubServer;
import io.ably.pubsub.types.AblyException;

public class ServerTokenRequest {
    public static void main(String[] args) throws AblyException {
        String ablyApiKey = System.getenv("ABLY_API_KEY");
        PubSubHttpClient http = PubSubServer.httpClientBuilder()
            .key(ablyApiKey)
            .build();

        // Authenticate the caller first. Grant only the operations this user needs.
        Auth.TokenParams params = new Auth.TokenParams();
        params.clientId = "me"; // the authenticated user's ID
        // Without a capability, the token gets all of the API key's capabilities.
        params.capability = "{\"test-channel\":[\"publish\",\"subscribe\",\"history\"]}";

        Auth.TokenRequest tokenRequest = http.auth.createTokenRequest(params, null);

        // Return this JSON unchanged, with the content type application/json.
        System.out.println(tokenRequest.asJson());
    }
}
```

See [token authentication](https://ably.com/docs/auth/token) for how devices use it.

### Device

Authenticate a device with tokens issued by your own server, through `authUrl` or `authCallback`.
Never embed an API key in an app that runs on an end-user device.

```java
import io.ably.pubsub.device.PubSubDevice;
import io.ably.pubsub.realtime.Channel;
import io.ably.pubsub.realtime.PubSubRealtimeClient;
import io.ably.pubsub.types.AblyException;

public class DeviceQuickStart {
    public static void main(String[] args) throws AblyException {
        PubSubRealtimeClient realtime = PubSubDevice.clientBuilder()
            .authUrl("https://your-server.example.com/ably-auth")
            .clientId("me")
            .build();

        // The client connects automatically. Subscribing attaches the channel.
        Channel channel = realtime.channels.get("test-channel");
        channel.subscribe(message -> System.out.println("Received: " + message.data));
        channel.publish("test-event", "hello world");
    }
}
```

To get tokens from your own code instead of a URL, pass an `Auth.TokenCallback` to
`authCallback(...)`. It can return a token string, a `TokenDetails` or a `TokenRequest`.

## Migrating from 1.x

Version 2.0 splits the SDK into a server package and a device package, and builds clients with
builders.

| | 1.x | 2.0 |
|---|---|---|
| Package | `io.ably:ably-java`, `io.ably:ably-android` | `io.ably.pubsub:server` or `io.ably.pubsub:device` |
| Java package | `io.ably.lib.*` | `io.ably.pubsub.*` |
| Realtime client | `new AblyRealtime(options)` | `PubSubServer.realtimeClientBuilder()…build()` or `PubSubDevice.clientBuilder()…build()` |
| HTTP client | `new AblyRest(options)` | `PubSubServer.httpClientBuilder()…build()` |

See [UPGRADING.md](https://github.com/ably/ably-pubsub-java/blob/main/UPGRADING.md) for all other changes.

## Common configuration

Set options on the builders, or pass a `ClientOptions` object to `realtimeClientBuilder(options)`,
`httpClientBuilder(options)` or `clientBuilder(options)`.

### Endpoint

`endpoint` selects the Ably host for HTTP requests and the realtime connection. It replaces the
1.x `environment`, `restHost` and `realtimeHost` options.

```java
String ablyApiKey = System.getenv("ABLY_API_KEY");
PubSubRealtimeClient realtime = PubSubServer.realtimeClientBuilder()
    .key(ablyApiKey)
    .endpoint("nonprod:sandbox")
    .build();
```

- Never put a port in `endpoint`: `localhost:8080` is not a hostname and does not resolve.
- Set the port with `tlsPort`, or with `port` when TLS is off.
- With TLS off (`tls(false)`), use token authentication.

See [Choose the host with `endpoint`](https://github.com/ably/ably-pubsub-java/blob/main/UPGRADING.md#6-choose-the-host-with-endpoint) for how the
old options map to `endpoint`.

### Network client

The packages use OkHttp for HTTP and WebSocket connections. To use the built-in
`HttpURLConnection` engine instead, swap the engine. For example, on a server:

```kotlin
dependencies {
    implementation("io.ably.pubsub:server:2.0.0") {
        exclude(group = "io.ably.pubsub", module = "network-client-okhttp")
    }
    runtimeOnly("io.ably.pubsub:network-client-default:2.0.0")
}
```

### Proxy

Pass a `ProxyOptions` object to the builder to connect through a proxy.

```java
import io.ably.pubsub.http.HttpAuth;
import io.ably.pubsub.types.ProxyOptions;

ProxyOptions proxy = new ProxyOptions();
proxy.host = "your-proxy-host";
proxy.port = 8080;

// Only if the proxy requires authentication.
proxy.username = "your-username";
proxy.password = "your-password";
proxy.prefAuthType = HttpAuth.Type.BASIC;

String ablyApiKey = System.getenv("ABLY_API_KEY");
PubSubRealtimeClient realtime = PubSubServer.realtimeClientBuilder()
    .key(ablyApiKey)
    .proxy(proxy)
    .build();
```

### Logging

Set `logLevel` to a constant from `io.ably.pubsub.util.Log`: `VERBOSE`, `DEBUG`, `INFO`, `WARN`
(the default), `ERROR` or `NONE`. Log lines go to standard output unless you set a `logHandler`.

```java
import io.ably.pubsub.util.Log;

String ablyApiKey = System.getenv("ABLY_API_KEY");
PubSubRealtimeClient realtime = PubSubServer.realtimeClientBuilder()
    .key(ablyApiKey)
    .logLevel(Log.DEBUG)
    .logHandler((severity, tag, msg, tr) -> System.err.println(tag + ": " + msg))
    .build();
```

## Live Objects

[Ably LiveObjects](https://ably.com/docs/liveobjects) ships as a separate plugin,
`io.ably.pubsub:liveobjects`, that works with either package. Add it at runtime and the client finds
it on the classpath.

```kotlin
dependencies {
    runtimeOnly("io.ably.pubsub:liveobjects:2.0.0")
}
```

Attach the channel with the `object_subscribe` and `object_publish` modes to use objects on it. See
the [Java quickstart](https://ably.com/docs/liveobjects/quickstart/java) and the
[example app](https://github.com/ably/ably-pubsub-java/blob/main/examples/README.md).

## Known limitations

- `io.ably.pubsub:device` needs Android API level 24. Apps that support API levels 19 to 23 must
  stay on 1.x (`io.ably:ably-android`) for now.

## Further reading

- [Getting started with Pub/Sub using Java](https://ably.com/docs/getting-started/java)
- [Ably Pub/Sub documentation](https://ably.com/docs/basics) and [examples](https://ably.com/examples?product=pubsub)
- [Token authentication](https://ably.com/docs/auth/token)
- [UPGRADING.md](https://github.com/ably/ably-pubsub-java/blob/main/UPGRADING.md): migrating from 1.x
- [CHANGELOG.md](https://github.com/ably/ably-pubsub-java/blob/main/CHANGELOG.md): release notes; all Ably releases are listed on [changelog.ably.com](https://changelog.ably.com)
- [examples/](https://github.com/ably/ably-pubsub-java/tree/main/examples): the LiveObjects example app

## Contributing

Read [CONTRIBUTING.md](https://github.com/ably/ably-pubsub-java/blob/main/CONTRIBUTING.md) before contributing; it also describes the release process.

## Support, feedback and troubleshooting

For help, visit Ably's [support page](https://ably.com/support). Report bugs and compatibility
problems on [GitHub Issues](https://github.com/ably/ably-pubsub-java/issues).

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](https://github.com/ably/ably-pubsub-java/blob/main/LICENSE).
