plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val androidEnabled = project.extra["androidEnabled"] as Boolean
if (androidEnabled) apply(plugin = "com.android.library")

kotlin {
    jvmToolchain(17)
    jvm("desktop")
    if (androidEnabled) androidTarget()

    sourceSets {
        commonMain {
            // The two editions live in their own top-level folders so their
            // skins can evolve completely independently:
            //
            //   styles/java/src     -> Java Edition skin + palette
            //   styles/bedrock/src  -> Bedrock Edition skin + palette
            //
            // Only the contracts in src/commonMain are shared between them.
            kotlin.srcDirs(
                "src/commonMain/kotlin",
                "java/src",
                "bedrock/src",
            )
            dependencies {
                api(project(":sharedCore"))
                api(compose.runtime)
                api(compose.foundation)
                api(compose.material3)
                api(compose.ui)
                implementation(compose.components.uiToolingPreview)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

if (androidEnabled) {
    extensions.configure<com.android.build.gradle.LibraryExtension> {
        namespace = "com.mcguidesigner.styles"
        compileSdk = libs.versions.compileSdk.get().toInt()
        defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}
