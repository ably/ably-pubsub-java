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
    compileOnly(project(":core"))
    implementation(libs.bundles.common)
    implementation(libs.coroutine.core)

    testImplementation(project(":core"))
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.kotlin.tests)
}

tasks.withType<Test>().configureEach {
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
    }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }
}

tasks.register<Test>("runLiveObjectsUnitTests") {
    filter {
        includeTestsMatching("io.ably.lib.liveobjects.unit.*")
    }
}

tasks.register<Test>("runLiveObjectsIntegrationTests") {
    filter {
        includeTestsMatching("io.ably.lib.liveobjects.integration.*")
        // Exclude the base integration test class
        excludeTestsMatching("io.ably.lib.liveobjects.integration.setup.IntegrationTest")
    }
}

kotlin {
    explicitApi()
}
