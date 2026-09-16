import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(project(":core"))
    testImplementation(project(":network-client-core"))
    // Runtime-only so compile-time stays decoupled from the plugin internals; the LiveObjects test
    // helpers reach the internal wire/message classes (e.g. for build_public_object_message) by reflection.
    testRuntimeOnly(project(":liveobjects"))
    testImplementation(kotlin("test"))
    // @ParameterizedTest / @ValueSource — version managed by the junit-bom on the test classpath.
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testImplementation(libs.mockk)
    testImplementation(libs.coroutine.core)
    testImplementation(libs.coroutine.test)
    testImplementation(libs.ktor.client.core)
    testImplementation(libs.ktor.client.cio)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
    }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }

    // Gradle does not forward -D system properties to the forked test JVM, so propagate the
    // local uts-proxy override explicitly. Accepts either `-Duts.proxy.localPath=...` on the
    // Gradle invocation or the `UTS_PROXY_LOCAL_PATH` environment variable. See ProxyManager.
    systemProperty(
        "uts.proxy.localPath",
        providers.systemProperty("uts.proxy.localPath")
            .orElse(providers.environmentVariable("UTS_PROXY_LOCAL_PATH"))
            .getOrElse(""),
    )
}

tasks.register<Test>("runUtsUnitTests") {
    filter {
        includeTestsMatching("io.ably.lib.uts.unit.*")
    }
}

tasks.register<Test>("runUtsIntegrationTests") {
    filter {
        includeTestsMatching("io.ably.lib.uts.integration.*")
    }
}
