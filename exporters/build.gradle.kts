plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

val androidEnabled = project.extra["androidEnabled"] as Boolean
if (androidEnabled) apply(plugin = "com.android.library")

// The Android library extension has to be fully configured before anything
// resolves a Kotlin target: resolving a KMP compilation finalises AGP, and an
// unconfigured `compileSdk` fails the build at that moment.
if (androidEnabled) {
    extensions.configure<com.android.build.gradle.LibraryExtension> {
        namespace = "com.mcguidesigner.exporters"
        compileSdk = libs.versions.compileSdk.get().toInt()
        defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}

kotlin {
    jvmToolchain(17)
    jvm("desktop")
    if (androidEnabled) androidTarget()

    // Every exporter is pure Kotlin producing text or bytes, so the browser
    // build gets all of them for free. Only the TemplateExporter tool stays
    // desktop-only, because it writes files to disk.
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

    sourceSets {
        commonMain.dependencies {
            api(project(":sharedCore"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

// The `exportTemplates` / `validateProjects` tasks run the tool that lives in
// src/desktopMain/kotlin/.../tools/TemplateExporter.kt. They are registered in
// the root build script - see the note there.
