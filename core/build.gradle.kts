import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.build.config)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.test.retry)
    checkstyle
    `java-library`
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
    }
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
    }
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
    jvmArgs("--add-opens", "java.base/java.lang=ALL-UNNAMED")
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }
}
