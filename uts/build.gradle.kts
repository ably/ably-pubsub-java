import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

java {
    // Java-8 outgoing variants so :java (a Java-8 library) can consume this module on its classpath.
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    // `api` for types that appear in infra signatures; `implementation` for internals.
    api(project(":java"))
    api(project(":network-client-core"))
    // ktor stays implementation — the proxy infra uses it internally; it must NOT leak to consumers.
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)

    // The UTS test toolkit — exported (api) so any module consuming this infra via
    // testImplementation(project(":uts")) transitively gets the full stack needed to
    // WRITE and RUN UTS tests: JUnit 5 (api+params+engine via the aggregator), the
    // kotlin.test Jupiter binding, and coroutines (runTest etc.). Consumers declare
    // only the project dependency.
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter)
    api(libs.junit.jupiter.params)
    api(kotlin("test-junit5"))
    api(libs.coroutine.core)
    api(libs.coroutine.test)
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

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8) }
}
