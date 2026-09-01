pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "ably-java"

include("core")
include("core-android")
include("gradle-lint")
include("network-client-core")
include("network-client-default")
include("network-client-okhttp")
include("pubsub-adapter")
include("liveobjects")
include("examples")
include("uts")
