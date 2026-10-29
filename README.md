![Ably Pub/Sub Java Header](images/javaSDK-github.png)
[![Latest Version](https://img.shields.io/maven-central/v/io.ably.pubsub/server?label=io.ably.pubsub%3Aserver)](https://central.sonatype.com/artifact/io.ably.pubsub/server)
[![License](https://badgen.net/github/license/ably/ably-pubsub-java)](https://github.com/ably/ably-pubsub-java/blob/main/LICENSE)

# Ably Pub/Sub Java SDK

Build any realtime experience using Ably’s Pub/Sub Java SDK. Supported on all popular platforms and frameworks, including Kotlin and Android.

Ably Pub/Sub provides flexible APIs that deliver features such as pub-sub messaging, message history, presence, and push notifications. Utilizing Ably’s realtime messaging platform, applications benefit from its highly performant, reliable, and scalable infrastructure.

Find out more:

* [Ably Pub/Sub docs.](https://ably.com/docs/basics)
* [Ably Pub/Sub examples.](https://ably.com/examples?product=pubsub)

---

> [!NOTE]
> **This branch carries the in-development 2.0 device/server package split.** The SDK is being
> restructured into new artifacts that declare which side of the network they run on, so that
> traffic classifies correctly on MAU-priced accounts:
>
> | Artifact | For | Entry point |
> |----------|-----|-------------|
> | `io.ably.pubsub:device` (aar) | Devices: Android apps and other end-user runtimes | `PubSubDevice.clientBuilder(...)` |
> | `io.ably.pubsub:server` (jar) | Servers and other trusted backend environments | `PubSubServer.httpClientBuilder(...)` / `PubSubServer.realtimeClientBuilder(...)` |
> | `io.ably.pubsub:core`, `io.ably.pubsub:core-android` | Internal implementation artifacts — do not depend on these directly | — |
>
> Use exactly one of `server` or `device` per app, and remove any 1.x `io.ably:ably-java` /
> `io.ably:ably-android` dependency. See [UPGRADING.md](./UPGRADING.md) for the migration guide.
>
> Nothing from this branch is published yet. The `io.ably:ably-java` and `io.ably:ably-android`
> 1.x artifacts continue to work and will receive security and critical fixes from a maintenance
> branch for one year after the 2.0 release.

---

## Getting started

Everything you need to get started with Ably:

* [Getting started with Pub/Sub using Java.](https://ably.com/docs/getting-started/java)
* [SDK Setup for Java.](https://ably.com/docs/getting-started/setup?lang=java)

---

## Supported platforms

Ably aims to support a wide range of platforms. If you experience any compatibility issues, open an issue in the repository or contact [Ably support](https://ably.com/support).

The following platforms are supported:

| Platform | Support |
|----------|---------|
| Java     | >= 1.8 (JRE 8 or later) |
| Kotlin   | All versions (>= 1.0 supported), but we recommend >= 1.8 for best compatibility. |
| Android  | >= 7.0 (API level 24) for `io.ably.pubsub:device`. For 1.x, which supports API level 19, see [UPGRADING.md](./UPGRADING.md). |

> [!IMPORTANT]
> SDK versions < 1.2.35 will be [deprecated](https://ably.com/docs/platform/deprecate/protocol-v1) from November 1, 2025.

---

## Installation

The Java SDK is available as a Maven dependency in two artifacts, one per side of the connection.
Install exactly one of them: `io.ably.pubsub:server` on infrastructure you control, or
`io.ably.pubsub:device` in an end-user app. Never depend on `io.ably.pubsub:core` or
`io.ably.pubsub:core-android` directly. Upgrading from 1.x? Read [UPGRADING.md](./UPGRADING.md).

### Install for Maven:

```xml
<!-- Servers and other trusted backends -->
<dependency>
    <groupId>io.ably.pubsub</groupId>
    <artifactId>server</artifactId>
    <version>2.0.0</version>
</dependency>
```

For a JVM desktop client, Maven consumers name the JVM variant of the device artifact directly
(`io.ably.pubsub:device-jvm:2.0.0`); Maven cannot resolve Gradle variants.

### Install for Gradle:

```gradle
// Servers and other trusted backends
implementation 'io.ably.pubsub:server:2.0.0'

// End-user devices (Android apps, JVM desktop clients)
implementation 'io.ably.pubsub:device:2.0.0'

implementation 'org.slf4j:slf4j-simple:2.0.7'
```

Run the following to instantiate a client:

```java
import io.ably.pubsub.realtime.PubSubRealtimeClient;
import io.ably.pubsub.server.PubSubServer;

PubSubRealtimeClient realtime = PubSubServer.realtimeClientBuilder()
    .key(apiKey)
    .build();
```

On an end-user device, depend on `io.ably.pubsub:device` and build through
`PubSubDevice.clientBuilder()` instead; the builder you call declares which side of the
connection your code runs on:

```java
import io.ably.pubsub.device.PubSubDevice;

PubSubRealtimeClient realtime = PubSubDevice.clientBuilder()
    .authUrl("https://your-server.example.com/ably-auth")
    .clientId("me")
    .build();
```

Never embed an API key in an app that runs on an end-user device; authenticate with tokens issued
by your own server (`authUrl` or `authCallback`).

> [!IMPORTANT]
> Clients from `PubSubServer` declare themselves as servers. Authenticate them with an API key.
> Token authentication on a realtime connection works only with a token carrying the signed
> `x-ably-clientType=server` claim, which today means a JWT; otherwise Ably rejects the connection
> with error 40167. Clients from `PubSubDevice` are not affected.

---

## Usage

The following code connects to Ably's realtime messaging service, subscribes to a channel to receive messages, and publishes a test message to that same channel.


```java
// Initialize Ably Realtime client
PubSubRealtimeClient realtimeClient = PubSubServer.realtimeClientBuilder()
    .key("your-ably-api-key")
    .clientId("me")
    .build();

// Wait for connection to be established
realtimeClient.connection.on(ConnectionEvent.connected, connectionStateChange -> {
    System.out.println("Connected to Ably");
    
    // Get a reference to the 'test-channel' channel
    Channel channel = realtimeClient.channels.get("test-channel");
    
    // Subscribe to all messages published to this channel
    channel.subscribe(message -> {
        System.out.println("Received message: " + message.data);
    });
    
    // Publish a test message to the channel
    channel.publish("test-event", "hello world");
});
```
---

## Live Objects

[Ably Live Objects](https://ably.com/docs/liveobjects) provide realtime, collaborative data structures that automatically synchronize state across all connected clients. Build interactive applications with shared data that updates instantly across devices.

For a comprehensive guide, check the [Ably Live Objects documentation](https://ably.com/docs/liveobjects), starting with the [Java quickstart](https://ably.com/docs/liveobjects/quickstart/java).

### Install Live Objects

Add the following dependency to your `build.gradle` file:

```groovy
dependencies {
    runtimeOnly("io.ably.pubsub:liveobjects:2.0.0")
}
```

### Example app

The Ably Live Objects [Example app](./examples) is an interactive demo showcasing Live Objects with realtime color voting and collaborative task management.

The example app demonstrates:
- **Color Voting**: Realtime voting system with live vote counts synchronized across all devices
- **Task Management**: Collaborative task management where users can add, edit, and delete tasks that sync in realtime

To run the example app, follow the setup instructions in the [examples README](./examples/README.md).

## Proxy support

You can add proxy support to the Ably Java SDK by configuring `ProxyOptions` in your client setup, enabling connectivity through corporate firewalls and secured networks.

<details>
<summary>Proxy support setup details.</summary>

Enable proxy support by passing a `ProxyOptions` object to the client builder when initializing your Ably client.

The following example sets up a proxy using the Pub/Sub Java SDK:

```java
import io.ably.pubsub.realtime.PubSubRealtimeClient;
import io.ably.pubsub.types.ProxyOptions;
import io.ably.pubsub.http.HttpAuth;
import io.ably.pubsub.http.PubSubHttpClient;
import io.ably.pubsub.server.PubSubServer;

public class AblyWithProxy {
    public static void main(String[] args) throws Exception {
        // Setup proxy settings
        ProxyOptions proxy = new ProxyOptions();
        proxy.host = "your-proxy-host";  // Replace with your proxy host
        proxy.port = 8080;               // Replace with your proxy port
        
        // Optional: If the proxy requires authentication
        proxy.username = "your-username";  // Replace with proxy username
        proxy.password = "your-password";  // Replace with proxy password
        proxy.prefAuthType = HttpAuth.Type.BASIC;  // Choose your preferred authentication type (e.g., BASIC or DIGEST)

        // Create an instance of Ably with the proxy settings attached
        PubSubHttpClient ably = PubSubServer.httpClientBuilder()
            .key("your-ably-api-key")
            .proxy(proxy)
            .build();

        // Alternatively, for real-time connections
        PubSubRealtimeClient realtimeClient = PubSubServer.realtimeClientBuilder()
            .key("your-ably-api-key")
            .proxy(proxy)
            .build();

        // Use the Ably client as usual
    }
}
```

</details>

---

## Contribute

Read the [CONTRIBUTING.md](./CONTRIBUTING.md) guidelines to contribute to Ably.

---

## Releases

The [CHANGELOG.md](./CHANGELOG.md) contains details of the latest releases for this SDK. You can also view all Ably releases on [changelog.ably.com](https://changelog.ably.com).

---

## Support, feedback, and troubleshooting

For help or technical support, visit Ably's [support page](https://ably.com/support) or [GitHub Issues](https://github.com/ably/ably-pubsub-java/issues) for community-reported bugs and discussions.
