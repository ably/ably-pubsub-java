![Ably Pub/Sub Java Header](images/javaSDK-github.png)

[![Maven Central: server](https://img.shields.io/maven-central/v/io.ably.pubsub/server?label=io.ably.pubsub%3Aserver)](https://central.sonatype.com/artifact/io.ably.pubsub/server)
[![Maven Central: device](https://img.shields.io/maven-central/v/io.ably.pubsub/device?label=io.ably.pubsub%3Adevice)](https://central.sonatype.com/artifact/io.ably.pubsub/device)
[![License](https://badgen.net/github/license/ably/ably-pubsub-java)](./LICENSE)

# Ably Pub/Sub Java SDK

The Java and Kotlin SDK for [Ably Pub/Sub](https://ably.com/docs/basics): publish and subscribe to
channels, and use message history, presence and push notifications, from servers, Android apps and
JVM desktop clients.

## Overview

The SDK is published as two packages, one for each side of the connection. You choose the package
by where your code runs, and build your client through that package's entry point.

| Package | Use it for | Entry point | Platforms |
|---------|-----------|-------------|-----------|
| `io.ably.pubsub:server` | Servers and other trusted backend environments | `PubSubServer.httpClientBuilder()`, `PubSubServer.realtimeClientBuilder()` | JVM |
| `io.ably.pubsub:device` | Android apps and other end-user runtimes | `PubSubDevice.clientBuilder()` | Android, JVM |

## Requirements

| Platform | Requirement |
|----------|-------------|
| Java     | 8 or later (the SDK targets Java 8 bytecode). |
| Android  | Android 7.0 (API level 24) or later, for `io.ably.pubsub:device`. |
| Kotlin   | Optional. The Java API is usable from Kotlin; the `device` package is built with Kotlin 2.1.10. |

## Installation

Replace `2.0.0` with the version you want to use. Use exactly one of `server` or `device` in an
app; see [One package per app](#one-package-per-app).

### Server

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("io.ably.pubsub:server:2.0.0")
}
```

Gradle (Groovy):

```groovy
dependencies {
    implementation 'io.ably.pubsub:server:2.0.0'
}
```

Maven:

```xml
<dependency>
    <groupId>io.ably.pubsub</groupId>
    <artifactId>server</artifactId>
    <version>2.0.0</version>
</dependency>
```

### Device

`device` is a Kotlin Multiplatform artifact, published as `device` (root), `device-android` and
`device-jvm`. A Gradle build declares only `io.ably.pubsub:device`; Gradle module metadata selects the
Android or JVM variant for you.

Android, Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("io.ably.pubsub:device:2.0.0")
}
```

Android, Gradle (Groovy):

```groovy
dependencies {
    implementation 'io.ably.pubsub:device:2.0.0'
}
```

JVM desktop client, Gradle (Kotlin DSL or Groovy; the coordinate is the same):

```kotlin
dependencies {
    implementation("io.ably.pubsub:device:2.0.0")
}
```

JVM desktop client, Maven. Maven cannot read Gradle module metadata, so name the JVM artifact directly:

```xml
<dependency>
    <groupId>io.ably.pubsub</groupId>
    <artifactId>device-jvm</artifactId>
    <version>2.0.0</version>
</dependency>
```

### One package per app

- Depend on exactly one of `io.ably.pubsub:server` or `io.ably.pubsub:device` in each app.
- Never depend on `io.ably.pubsub:core` or `io.ably.pubsub:core-android`. They are internal
  implementation artifacts, and declaring them next to a package puts duplicate classes on the
  classpath.
- If one module of your system runs on a device and another on a server, give each module its own
  package.

### Migrating from 1.x

Remove the 1.x `io.ably:ably-java` and `io.ably:ably-android` dependencies when you add a 2.0
package. See [UPGRADING.md](./UPGRADING.md) for the artifact mapping, package renames, behaviour
changes and removed APIs.

## Quick start

### Server

Use an API key on a server. The following code connects, subscribes to a channel and publishes a
message to it.

```java
import io.ably.pubsub.realtime.Channel;
import io.ably.pubsub.realtime.ConnectionEvent;
import io.ably.pubsub.realtime.PubSubRealtimeClient;
import io.ably.pubsub.server.PubSubServer;
import io.ably.pubsub.types.AblyException;

public class ServerQuickStart {
    public static void main(String[] args) throws AblyException {
        PubSubRealtimeClient realtime = PubSubServer.realtimeClientBuilder()
            .key("your-ably-api-key")
            .build();

        realtime.connection.on(ConnectionEvent.connected, change -> {
            try {
                Channel channel = realtime.channels.get("test-channel");
                channel.subscribe(message -> System.out.println("Received: " + message.data));
                channel.publish("test-event", "hello world");
            } catch (AblyException e) {
                e.printStackTrace();
            }
        });
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
        PubSubHttpClient http = PubSubServer.httpClientBuilder()
            .key("your-ably-api-key")
            .build();

        http.channels.get("test-channel").publish("test-event", "hello world");
    }
}
```

> [!IMPORTANT]
> Clients from `PubSubServer` declare themselves as servers. Authenticate them with an API key.
> Token authentication on a realtime connection works only with a token that carries the signed
> `x-ably-clientType=server` claim, which today means a JWT; otherwise Ably rejects the connection
> with error 40167. See [Server clients and token authentication](./UPGRADING.md#server-clients-and-token-authentication).

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

The same in Kotlin:

```kotlin
import io.ably.pubsub.device.PubSubDevice

val realtime = PubSubDevice.clientBuilder()
    .authUrl("https://your-server.example.com/ably-auth")
    .clientId("me")
    .build()

val channel = realtime.channels.get("test-channel")
channel.subscribe { message -> println("Received: ${message.data}") }
channel.publish("test-event", "hello world")
```

## Common configuration

Both entry points accept the same options through their builders, or through a `ClientOptions`
object passed to `PubSubServer.realtimeClientBuilder(options)`, `PubSubServer.httpClientBuilder(options)`
or `PubSubDevice.clientBuilder(options)`.

### Endpoint

`endpoint` selects the Ably host for both REST requests and the realtime connection. It replaces
the 1.x `environment`, `restHost` and `realtimeHost` options.

```java
PubSubRealtimeClient realtime = PubSubServer.realtimeClientBuilder()
    .key("your-ably-api-key")
    .endpoint("nonprod:sandbox")
    .build();
```

Never put a port in `endpoint`. Set `port` or `tlsPort` on `ClientOptions` instead. See
[Choose the host with `endpoint`](./UPGRADING.md#6-choose-the-host-with-endpoint) for how each value resolves.

### Network client

By default, the published packages use OkHttp for HTTP and WebSocket connections. To use the
built-in `HttpURLConnection` engine instead, swap the engine under the package you depend on. For
example, on a server:

```kotlin
dependencies {
    implementation("io.ably.pubsub:server:2.0.0") {
        exclude(group = "io.ably.pubsub", module = "network-client-okhttp")
    }
    runtimeOnly("io.ably.pubsub:network-client-default:2.0.0")
}
```

See [CONTRIBUTING.md](./CONTRIBUTING.md) for details of the engines.

### Proxy

Pass a `ProxyOptions` object to the builder to connect through a proxy.

```java
import io.ably.pubsub.http.HttpAuth;
import io.ably.pubsub.realtime.PubSubRealtimeClient;
import io.ably.pubsub.server.PubSubServer;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.types.ProxyOptions;

public class AblyWithProxy {
    public static void main(String[] args) throws AblyException {
        ProxyOptions proxy = new ProxyOptions();
        proxy.host = "your-proxy-host";
        proxy.port = 8080;

        // Optional: only if the proxy requires authentication.
        proxy.username = "your-username";
        proxy.password = "your-password";
        proxy.prefAuthType = HttpAuth.Type.BASIC;

        PubSubRealtimeClient realtime = PubSubServer.realtimeClientBuilder()
            .key("your-ably-api-key")
            .proxy(proxy)
            .build();
    }
}
```

### Logging

Set `logLevel` to one of the constants in `io.ably.pubsub.util.Log` (`VERBOSE`, `DEBUG`, `INFO`,
`WARN`, `ERROR` or `NONE`; the default is `WARN`). Set `logHandler` to receive log output
yourself; by default it goes to standard output.

```java
import io.ably.pubsub.realtime.PubSubRealtimeClient;
import io.ably.pubsub.server.PubSubServer;
import io.ably.pubsub.types.AblyException;
import io.ably.pubsub.util.Log;

public class AblyWithLogging {
    public static void main(String[] args) throws AblyException {
        PubSubRealtimeClient realtime = PubSubServer.realtimeClientBuilder()
            .key("your-ably-api-key")
            .logLevel(Log.DEBUG)
            .logHandler((severity, tag, msg, tr) -> System.err.println(tag + ": " + msg))
            .build();
    }
}
```

## Live Objects

[Ably LiveObjects](https://ably.com/docs/liveobjects) provides realtime, collaborative data
structures that synchronize state across clients. It ships as a separate plugin,
`io.ably.pubsub:liveobjects`, that works with either package. Add it at runtime and the client
detects it on the classpath; no other setup is needed.

```kotlin
dependencies {
    runtimeOnly("io.ably.pubsub:liveobjects:2.0.0")
}
```

Attach the channel with the `object_subscribe` and `object_publish` modes to use objects on it. See
the [LiveObjects documentation](https://ably.com/docs/liveobjects) and the
[Java quickstart](https://ably.com/docs/liveobjects/quickstart/java), and run the
[example app](./examples/README.md) for a working demo.

## Further reading

- [Getting started with Pub/Sub using Java](https://ably.com/docs/getting-started/java)
- [SDK setup for Java](https://ably.com/docs/getting-started/setup?lang=java)
- [Ably Pub/Sub documentation](https://ably.com/docs/basics) and [examples](https://ably.com/examples?product=pubsub)
- [UPGRADING.md](./UPGRADING.md): migrating from 1.x
- [CHANGELOG.md](./CHANGELOG.md): release notes; all Ably releases are listed on [changelog.ably.com](https://changelog.ably.com)
- [examples/](./examples): the LiveObjects example app

## Contributing

Read [CONTRIBUTING.md](./CONTRIBUTING.md) before contributing.

## Support, feedback and troubleshooting

For help, visit Ably's [support page](https://ably.com/support). Report bugs and compatibility
problems on [GitHub Issues](https://github.com/ably/ably-pubsub-java/issues).

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](./LICENSE).
