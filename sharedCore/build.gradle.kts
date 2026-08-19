plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

val androidEnabled = project.extra["androidEnabled"] as Boolean
if (androidEnabled) apply(plugin = "com.android.library")

kotlin {
    jvmToolchain(17)
    jvm("desktop")
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
