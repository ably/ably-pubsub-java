plugins {
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

sourceSets {
    named("main") {
        java {
            // `../shared` holds the side-agent helper shared with the `device` module; it is
            // compiled into each door artifact rather than published as an artifact of its own.
            srcDirs("src/main/java", "../shared/src/main/java")
        }
    }
}

tasks.register<Test>("runUnitTests") {
    beforeTest(closureOf<TestDescriptor> { logger.lifecycle("-> $this") })
    outputs.upToDateWhen { false }
}
