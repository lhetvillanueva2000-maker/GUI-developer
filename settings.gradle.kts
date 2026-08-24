rootProject.name = "surface-studio"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

// Lets Gradle fetch the JDK 17 toolchain automatically when the machine only
// has a newer (or older) JDK installed.  Without it, contributors would have to
// install exactly JDK 17 by hand before the first build.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

// ---------------------------------------------------------------------------
// Android SDK detection.
//
// The desktop side of this repository must remain buildable on machines that
// do not have the Android SDK installed (CI runners for the Windows target,
// contributors who only work on the desktop editor, ...).  When no SDK can be
// located we simply drop the Android application module and skip the
// `androidTarget()` of every shared module.  Pass -PskipAndroid=true to force
// that behaviour even when an SDK is present.
// ---------------------------------------------------------------------------
val forcedSkip = (startParameter.projectProperties["skipAndroid"] ?: "false").toBoolean()

fun locateAndroidSdk(): String? {
    if (forcedSkip) return null
    val fromLocalProperties = rootDir.resolve("local.properties")
        .takeIf { it.isFile }
        ?.let { file ->
            java.util.Properties().apply { file.inputStream().use { load(it) } }.getProperty("sdk.dir")
        }
    val candidates = listOfNotNull(
        fromLocalProperties,
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
    )
    return candidates.firstOrNull { path -> File(path).resolve("platforms").isDirectory }
}

val androidSdk = locateAndroidSdk()
val androidEnabled = androidSdk != null

gradle.beforeProject {
    extra.set("androidEnabled", androidEnabled)
    extra.set("androidSdkPath", androidSdk ?: "")
}

include(":sharedCore")
include(":styles")
include(":exporters")
include(":desktopApp")

if (androidEnabled) {
    include(":androidApp")
} else {
    logger.lifecycle(
        "[surface-studio] Android SDK not found - the :androidApp module and all " +
            "androidTarget() source sets are disabled for this build."
    )
}
