pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "ably-java"

include("java")
include("android")
include("gradle-lint")
include("network-client-core")
include("network-client-default")
include("network-client-okhttp")
include("pubsub-adapter")
include("pubsub-server")
include("pubsub-device")
include("liveobjects")
include("examples")
include("uts")
