plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.ably.lib"
    defaultConfig {
        minSdk = 19
        compileSdk = 34
        buildConfigField("String", "LIBRARY_NAME", "\"android\"")
        buildConfigField("String", "VERSION", "\"${property("VERSION_NAME")}\"")
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["class"] = "io.ably.lib.test.android.AndroidPushTest"
        testInstrumentationRunnerArguments["timeout_msec"] = "300000"
        consumerProguardFiles("proguard.txt")
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

    buildFeatures {
        buildConfig = true
    }

    lint {
        abortOnError = false
    }

    testOptions.targetSdk = 34

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", "../lib/src/main/java")
        }
        getByName("androidTest") {
            java.srcDirs("src/androidTest/java", "../lib/src/test/java")
            assets.srcDirs("../lib/src/test/resources")
        }
    }
}

dependencies {
    api(libs.gson)
    implementation(libs.bundles.common)
    compileOnly(libs.jetbrains)
    testImplementation(libs.bundles.tests)
    implementation(project(":network-client-core"))
    if (findProperty("httpURLConnection") == null) {
        runtimeOnly(project(":network-client-okhttp"))
    } else {
        runtimeOnly(project(":network-client-default"))
    }
    implementation(libs.firebase.messaging)
    androidTestImplementation(libs.bundles.instrumental.android)
}

configurations {
    all {
        exclude(group = "org.hamcrest", module = "hamcrest-core")
        resolutionStrategy {
            force(libs.jetbrains)
        }
    }
    getByName("androidTestImplementation") {
        extendsFrom(configurations.getByName("testImplementation"))
    }
}
