import com.vanniktech.maven.publish.MavenPublishBaseExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.lombok) apply false
    alias(libs.plugins.test.retry) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

subprojects {
    repositories {
        google()
        mavenCentral()
    }

    tasks.withType<Javadoc> {
        // To prevent javadoc warnings with Java 8
        options {
            this as StandardJavadocDocletOptions
            addBooleanOption("Xdoclint:none", true)
            addBooleanOption("quiet", true)
            addStringOption("Xmaxwarns", "1")
        }
    }
}

/*
 * Release pre-flight: the split ships core, core-android, device and server in lockstep
 * (one version, one run), so the set of published artifacts and their
 * coordinates are asserted here and the release workflow fails before anything is
 * uploaded if they drift. If you add or remove a published module, update this list
 * deliberately.
 */
val expectedReleaseArtifacts = sortedSetOf(
    "io.ably.pubsub:core:jar",
    "io.ably.pubsub:core-android:aar",
    "io.ably.pubsub:device:jar",
    "io.ably.pubsub:server:jar",
    "io.ably.pubsub:liveobjects:jar",
    "io.ably.pubsub:pubsub-adapter:jar",
    "io.ably.pubsub:network-client-core:jar",
    "io.ably.pubsub:network-client-default:jar",
    "io.ably.pubsub:network-client-okhttp:jar",
)

tasks.register("verifyReleaseArtifacts") {
    description = "Asserts the published artifact set, group and lockstep version before a release."
    doLast {
        val rootVersion = project.property("VERSION_NAME") as String
        val actual = sortedSetOf<String>()
        subprojects.filter { it.pluginManager.hasPlugin("com.vanniktech.maven.publish") }.forEach { p ->
            val artifactId = p.findProperty("POM_ARTIFACT_ID")
                ?: error("${p.path} applies maven-publish but has no POM_ARTIFACT_ID")
            val packaging = p.findProperty("POM_PACKAGING") ?: "jar"
            // The version each module publishes at comes from its effective VERSION_NAME
            // (a module-local gradle.properties can override the root's — exactly the
            // lockstep drift this guards against).
            val moduleVersion = p.findProperty("VERSION_NAME")
            if (moduleVersion != rootVersion) {
                error("Lockstep violation: ${p.path} has VERSION_NAME $moduleVersion, expected $rootVersion")
            }
            val group = p.findProperty("GROUP")
            actual.add("$group:$artifactId:$packaging")
        }
        if (actual != expectedReleaseArtifacts) {
            error(
                "Published artifact set does not match the expected release set.\n" +
                    "  expected: $expectedReleaseArtifacts\n" +
                    "  actual:   $actual\n" +
                    "If this change is deliberate, update expectedReleaseArtifacts in build.gradle.kts."
            )
        }
        logger.lifecycle("Release pre-flight OK: ${actual.size} artifacts at $rootVersion: $actual")
    }
}

configure(subprojects) {
    pluginManager.withPlugin("com.vanniktech.maven.publish") {
        extensions.configure<MavenPublishBaseExtension> {
            // Check if we're running a local publish task
            val isLocalPublish = gradle.startParameter.taskNames.any {
                it.contains("publishToMavenLocal") || it.contains("ToMavenLocal")
            }

            if (!isLocalPublish) {
                signAllPublications()
            }
        }
    }
}
