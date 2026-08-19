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

android {
    namespace = "com.mcguidesigner.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.mcguidesigner.android"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
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
            // Both signature schemes: v1 for older devices, v2/v3 for modern
            // ones (and v2+ is mandatory from Android 11 for targetSdk 30+).
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
}
