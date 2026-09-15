import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
}

kotlin {
    explicitApi()

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
        publishLibraryVariants("release")
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }

    sourceSets {
        commonMain.dependencies {
            /*
             * The two platform artifacts publish the same io.ably.lib.* types, so common code
             * compiles against either one. ably-java is the arbitrary pick; each target below
             * brings the real one.
             */
            compileOnly(project(":java"))
        }
        androidMain.dependencies {
            api(project(":android"))
        }
        jvmMain.dependencies {
            api(project(":java"))
        }
        commonTest.dependencies {
            compileOnly(project(":java"))
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "io.ably.pubsub.device"
    compileSdk = 34

    defaultConfig {
        minSdk = 19
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

    testOptions {
        targetSdk = 34
        /*
         * AndroidPlatformAgentProvider reads android.os.Build.VERSION.SDK_INT, which is an unmocked
         * stub in local unit tests. Without this it throws instead of returning a default.
         */
        unitTests.isReturnDefaultValues = true
    }
}

mavenPublishing {
    configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty(), androidVariantsToPublish = listOf("release")))
}

/* check.yml invokes `runUnitTests` unqualified across all projects. */
tasks.register("runUnitTests") {
    dependsOn("jvmTest", "testDebugUnitTest")
}
