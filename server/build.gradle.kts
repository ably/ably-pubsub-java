plugins {
    alias(libs.plugins.maven.publish)
    checkstyle
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    /*
     * api, not implementation: the factories return io.ably.pubsub.http.PubSubHttpClient and
     * io.ably.pubsub.realtime.RealtimeClient, and consumers configure them with io.ably.pubsub.types.*,
     * so the core is part of this module's compile-time ABI.
     */
    api(project(":core"))
    testImplementation(libs.bundles.tests)
}

tasks.register<Test>("runUnitTests") {
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }
}
