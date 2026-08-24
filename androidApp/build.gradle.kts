import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
}

/**
 * Turns `1.2.3` into `10203`, a monotonically increasing integer.
 *
 * Two digits per component caps minor and patch at 99, which is plenty and
 * keeps the number readable. Anything unparseable falls back to 1 rather than
 * failing the build.
 */
fun androidVersionCode(version: String): Int {
    val parts = version.substringBefore('-').split('.')
    fun part(index: Int) = parts.getOrNull(index)?.trim()?.toIntOrNull() ?: 0
    val code = part(0) * 10_000 + part(1) * 100 + part(2)
    return if (code > 0) code else 1
}

android {
    namespace = "com.mcguidesigner.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.mcguidesigner.android"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // Derived from the version string so a release always outranks the
        // one before it. Android refuses to install an APK whose versionCode
        // is not greater than the installed one, and a hardcoded 1 would make
        // every update look like a downgrade.
        versionCode = androidVersionCode(project.version.toString())
        versionName = project.version.toString()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        // Android refuses to install an unsigned package outright - it reports
        // "package appears to be invalid" - so a release build must always end
        // up signed with *something*.
        //
        // Preferred: a real keystore supplied through `keystore.properties`
        // (CI writes it from the ANDROID_KEYSTORE_* secrets).
        //
        // Fallback: the development key committed at
        // `build-scripts/dev-signing.jks`. Its password is in this file and in
        // the repository, so it is not a secret and must never be used for
        // anything you publish - it exists purely so that every build produces
        // an APK you can actually install. Because it is stable, successive
        // builds also upgrade cleanly over each other.
        val releaseProperties = rootProject.file("build-scripts/keystore.properties")
        val developmentKeystore = rootProject.file("build-scripts/dev-signing.jks")

        create("release") {
            if (releaseProperties.exists()) {
                val props = Properties().apply { releaseProperties.inputStream().use { load(it) } }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            } else {
                logger.lifecycle(
                    "[androidApp] No keystore.properties found - signing the release build with the " +
                        "development key. Do not publish this APK; see build-scripts/README.md.",
                )
                storeFile = developmentKeystore
                storePassword = "mcguidev"
                keyAlias = "mcgui-dev"
                keyPassword = "mcguidev"
            }
            // v2 and v3. v1 (JAR signing) is left on for completeness but the
            // build correctly declines to produce it: minSdk is 26, every
            // device that can install this verifies v2+, and a v1 signature
            // would only add size and a second thing to get wrong.
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/*.kotlin_module",
        )
    }

    sourceSets.getByName("main") {
        // The editor wallpaper ships in the APK's assets, read straight from
        // `assets/backdrop` in the repository root rather than copied into the
        // build directory first.
        //
        // Pointing at the real folder rather than staging it through a Copy
        // task is deliberate: a generated asset directory has to be wired into
        // every task that reads assets - merge, lint, package - and missing one
        // fails the build with an implicit-dependency error. There is nothing
        // to generate here anyway; the desktop build reads the very same files,
        // which is what stops the two apps drifting into different artwork.
        assets.srcDir(rootProject.file("assets/backdrop"))
        // The donation QR, read the same way and for the same reason: the
        // desktop build copies this exact file onto its classpath, so the code
        // in the APK and the code in the installer cannot drift apart.
        assets.srcDir(rootProject.file("assets/donate"))
    }
}

dependencies {
    implementation(project(":sharedCore"))
    implementation(project(":styles"))
    implementation(project(":exporters"))

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.window)
    implementation(libs.kotlinx.coroutines.android)
    // Editor settings are persisted as JSON so the shared EditorSettings type
    // - the same one the desktop writes - can be stored whole. See
    // io/AndroidPreferences.kt.
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
}
