import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.maven.publish)
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(project(":java"))
    implementation(libs.bundles.common)
    implementation(libs.coroutine.core)

    testImplementation(project(":java"))
    testImplementation(libs.bundles.kotlin.tests)  // keeps the JUnit4 API for the ~9 org.junit.* files
    // Shared UTS test infra + the exported (`api`) test toolkit: JUnit 5 (api+params+engine via the
    // aggregator), the kotlin.test Jupiter binding and coroutines all arrive transitively from :uts,
    // so the wrapper kotlin.test / bom / jupiter-params lines are no longer declared here. The
    // deterministic kotlin-test-junit5 binding (not the unpinned wrapper) also comes from :uts.
    testImplementation(project(":uts"))
    testRuntimeOnly(libs.junit.vintage.engine)     // runs the ~9 legacy JUnit4 files under the platform
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()                              // NEW
    testLogging { exceptionFormat = TestExceptionFormat.FULL }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }
    systemProperty(                                 // NEW (I6) — copied verbatim from uts/build.gradle.kts
        "uts.proxy.localPath",
        providers.systemProperty("uts.proxy.localPath")
            .orElse(providers.environmentVariable("UTS_PROXY_LOCAL_PATH"))
            .getOrElse(""),
    )
}

tasks.register<Test>("runLiveObjectsUnitTests") {
    filter {
        includeTestsMatching("io.ably.lib.liveobjects.unit.*")     // the module's own unit tests
        includeTestsMatching("io.ably.lib.liveobjects.uts.unit.*") // UTS objects unit suite (skill-generated)
    }
}

tasks.register<Test>("runLiveObjectsIntegrationTests") {
    filter {
        includeTestsMatching("io.ably.lib.liveobjects.integration.*")
        includeTestsMatching("io.ably.lib.liveobjects.uts.integration.*")   // NEW
        includeTestsMatching("io.ably.lib.liveobjects.uts.proxy.*")         // NEW
        // Exclude the base integration test class
        excludeTestsMatching("io.ably.lib.liveobjects.integration.setup.IntegrationTest")
    }
}

kotlin {
    explicitApi()
}
