plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.ably.pubsub.device"
    defaultConfig {
        minSdk = 19
        compileSdk = 34
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    lint {
        abortOnError = false
    }

    testOptions.targetSdk = 34

    sourceSets {
        getByName("main") {
            // `../shared` holds the side-agent helper shared with the `server` module; it is
            // compiled into each door artifact rather than published as an artifact of its own.
            java.srcDirs("src/main/java", "../shared/src/main/java")
        }
    }
}

dependencies {
    api(project(":core-android"))
    androidTestImplementation(libs.bundles.instrumental.android)
}

configurations {
    all {
        exclude(group = "org.hamcrest", module = "hamcrest-core")
        resolutionStrategy {
            force(libs.jetbrains)
        }
    }
}
