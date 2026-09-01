import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.build.config)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.test.retry)
    checkstyle
    `java-library`
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    api(libs.gson)
    implementation(libs.bundles.common)
    compileOnly(libs.jetbrains)
    implementation(project(":network-client-core"))
    if (findProperty("httpURLConnection") == null) {
        runtimeOnly(project(":network-client-okhttp"))
    } else {
        runtimeOnly(project(":network-client-default"))
    }
    testImplementation(libs.bundles.tests)

    // Brings in the shared UTS test toolkit (JUnit 5 + kotlin.test + coroutines) transitively via :uts api.
    testImplementation(project(":uts"))
}

buildConfig {
    useJavaOutput()
    packageName = "io.ably.lib"
    buildConfigField("String", "LIBRARY_NAME", "\"java\"")
    buildConfigField("String", "VERSION", "\"${property("VERSION_NAME")}\"")
}

sourceSets {
    named("main") {
        java {
            srcDirs("src/main/java", "../lib/src/main/java")
        }
    }
    named("test") {
        java {
            srcDirs("src/test/java", "../lib/src/test/java")
        }
        kotlin {
            srcDirs("src/test/kotlin", "../lib/src/test/kotlin")
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_1_8) }   // match sourceCompatibility 1.8
}

tasks.checkstyleMain.configure {
    exclude("io/ably/lib/BuildConfig.java")
}

tasks.register<Test>("testRealtimeSuite") {
    filter {
        includeTestsMatching("*RealtimeSuite")
    }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
    }
    retry {
        maxRetries.set(3)
        maxFailures.set(15)
        failOnPassedAfterRetry.set(false)
        failOnSkippedAfterRetry.set(false)
    }
}

tasks.register<Test>("testRestSuite") {
    filter {
        includeTestsMatching("*RestSuite")
    }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.net=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }
    testLogging {
       exceptionFormat = TestExceptionFormat.FULL
    }
    retry {
        maxRetries.set(3)
        maxFailures.set(8)
        failOnPassedAfterRetry.set(false)
        failOnSkippedAfterRetry.set(false)
    }
}

/*
Test task to run pure unit tests, where pure means that they only run
locally and do not need to communicate with Ably servers.
This is achieved by excluding everything in the io.ably.lib.test package,
as it only contains the REST and Realtime suites.
*/
tasks.register<Test>("runUnitTests") {
    filter {
        excludeTestsMatching("io.ably.lib.test.*")
        excludeTestsMatching("io.ably.lib.uts.*")   // UTS Jupiter suites run via runUts* tasks only
    }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }
}

tasks.register<Test>("runUtsUnitTests") {
    useJUnitPlatform()
    filter {
        includeTestsMatching("io.ably.lib.uts.unit.*")
    }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }
}

tasks.register<Test>("runUtsIntegrationTests") {
    useJUnitPlatform()
    filter {
        includeTestsMatching("io.ably.lib.uts.integration.*")
    }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }

    // Gradle does not forward -D system properties to the forked test JVM, so propagate the
    // local uts-proxy override explicitly (invariant I6; AuthReauthTest launches the proxy).
    // Accepts either `-Duts.proxy.localPath=...` on the Gradle invocation or the
    // `UTS_PROXY_LOCAL_PATH` environment variable. See ProxyManager.
    systemProperty(
        "uts.proxy.localPath",
        providers.systemProperty("uts.proxy.localPath")
            .orElse(providers.environmentVariable("UTS_PROXY_LOCAL_PATH"))
            .getOrElse(""),
    )
}
