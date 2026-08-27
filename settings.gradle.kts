rootProject.name = "uilabs"

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

        // Node and Yarn, for the browser build.
        //
        // Kotlin's wasm/JS toolchain fetches both as ordinary dependencies -
        // `org.nodejs:node` and `com.yarnpkg:yarn` - and normally reaches its
        // own repositories to do it. `repositoriesMode = PREFER_SETTINGS`
        // above overrides that, which is why adding a wasm target otherwise
        // fails the whole build with "Could not find org.nodejs:node", in
        // modules that have nothing to do with the browser.
        //
        // Both are release archives with no POM, so the layout has to be spelt
        // out and the metadata source restricted to the artifact itself.
        ivy("https://nodejs.org/dist") {
            name = "Node.js distributions"
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy("https://github.com/yarnpkg/yarn/releases/download") {
            name = "Yarn distributions"
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]).[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }
        // Binaryen, which is `wasm-opt` - the pass that takes the release
        // build of the browser app from tens of megabytes to a few. Fetched
        // from the project's GitHub releases, and named after the tag rather
        // than the version, which is why the pattern says `version_` twice.
        ivy("https://github.com/WebAssembly/binaryen/releases/download") {
            name = "Binaryen distributions"
            patternLayout {
                artifact("version_[revision]/[artifact]-version_[revision]-[classifier].[ext]")
            }
            metadataSources { artifact() }
            content { includeModule("com.github.webassembly", "binaryen") }
        }
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
include(":webApp")

if (androidEnabled) {
    include(":androidApp")
} else {
    logger.lifecycle(
        "[uilabs] Android SDK not found - the :androidApp module and all " +
            "androidTarget() source sets are disabled for this build."
    )
}
