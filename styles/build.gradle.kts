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

    // The browser build. Compose Multiplatform draws through skiko on both the
    // JVM and wasm, so this target reuses the desktop's Skia code verbatim -
    // see the skikoMain source set below.
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
        commonMain {
            // The two editions live in their own top-level folders so their
            // skins can evolve completely independently:
            //
            //   styles/java/src     -> Java Edition skin + palette
            //   styles/bedrock/src  -> Bedrock Edition skin + palette
            //   styles/other/src    -> Other UIs skin + palette
            //
            // Only the contracts in src/commonMain are shared between them.
            kotlin.srcDirs(
                "src/commonMain/kotlin",
                "java/src",
                "bedrock/src",
                "other/src",
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
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // Skia is the renderer on the desktop and in the browser alike, so the
        // two `actual`s for PaintSurface and the texture decoders are one file
        // each rather than two copies that can drift. Android keeps its own,
        // because there the native bitmap really is a different thing.
        //
        // Done with srcDir rather than a proper intermediate source set: an
        // intermediate between a JVM target and a wasm target is a hierarchy
        // template the Kotlin plugin does not offer, and this achieves the same
        // sharing with nothing to configure.
        val desktopMain by getting {
            kotlin.srcDir("src/skikoMain/kotlin")
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
        val wasmJsMain by getting {
            kotlin.srcDir("src/skikoMain/kotlin")
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
