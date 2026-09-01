plugins {
    alias(libs.plugins.build.config)
    alias(libs.plugins.maven.publish)
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
    api(project(":core"))
    testImplementation(libs.bundles.tests)
}

buildConfig {
    useJavaOutput()
    packageName = "io.ably.pubsub.server"
    buildConfigField("String", "VERSION", "\"${property("VERSION_NAME")}\"")
}

sourceSets {
    named("main") {
        java {
            // `../shared` holds the side-agent helper shared with the `device` module; it is
            // compiled into each door artifact rather than published as an artifact of its own.
            srcDirs("src/main/java", "../shared/src/main/java")
        }
    }
}

tasks.checkstyleMain.configure {
    exclude("io/ably/pubsub/server/BuildConfig.java")
}

tasks.register<Test>("runUnitTests") {
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }
}
