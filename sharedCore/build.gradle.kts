plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

val androidEnabled = project.extra["androidEnabled"] as Boolean
if (androidEnabled) apply(plugin = "com.android.library")

kotlin {
    jvmToolchain(17)
    jvm("desktop")
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            // No browser test run. The tests in commonTest are logic tests and
            // they already run on the JVM and on Android against the identical
            // source; repeating them in a browser adds no coverage, and it does
            // add a Karma-plus-Chrome dependency to every CI run - which is
            // exactly what broke `testAll` the moment this target was added. A
            // wasm-specific test would be a reason to turn it back on; there is
            // not one yet.
            testTask { enabled = false }
        }
    }
    if (androidEnabled) androidTarget()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

if (androidEnabled) {
    extensions.configure<com.android.build.gradle.LibraryExtension> {
        namespace = "com.mcguidesigner.core"
        compileSdk = libs.versions.compileSdk.get().toInt()
        defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
