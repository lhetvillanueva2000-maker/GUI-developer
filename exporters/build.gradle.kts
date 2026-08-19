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
