plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/**
 * The browser build of the editor.
 *
 * A third shell alongside `:desktopApp` and `:androidApp`, and the same size
 * of thing: the screens themselves - home, the design canvas, the paint app,
 * settings - all come from `:styles`, exactly as they do for the other two.
 * What lives here is only what a browser does differently: opening and saving
 * files, remembering preferences, and the editor's own furniture, which each
 * shell has always owned privately because a desktop's docks and a phone's
 * bottom sheets are not the same thing rendered twice.
 *
 * The output is a `.wasm` module plus its JavaScript loader, which is copied
 * into `web/app` and served as static files - no server, no build step at
 * deploy time. See `web/README.md`.
 */
kotlin {
    jvmToolchain(17)

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        // Fixes the emitted file names, which the page in `web/app` links to
        // by hand. Letting them default means the loader is called
        // `webApp.js`, after the Gradle module, which is an implementation
        // detail leaking into a URL people will see.
        moduleName = "uilabs"
        browser {
            commonWebpackConfig {
                outputFileName = "uilabs.js"
            }
        }
        binaries.executable()

        compilerOptions {
            // Without this, a Kotlin exception that reaches JavaScript arrives
            // as an opaque `Exception` with no name, no message and no stack -
            // so a crash in the browser is reported as the word "Exception"
            // and nothing else, in the console and in any bug report anybody
            // sends. This attaches the real throwable, which is the difference
            // between a fixable report and a shrug.
            freeCompilerArgs.add("-Xwasm-attach-js-exception")
        }
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(project(":styles"))
                implementation(project(":exporters"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }
    }
}

/**
 * Puts the built app where the site expects it: `web/app`.
 *
 * The output is committed rather than built at deploy time, which is the whole
 * reason `web/` can be dragged onto Netlify and work. Netlify would otherwise
 * have to provision a JDK, an Android SDK check, Node, Yarn and Binaryen to
 * produce files that are byte-identical every time the source has not changed.
 *
 * `Sync` rather than `Copy`, so a file that stops being produced - a renamed
 * chunk, a dropped source map - stops being deployed as well, instead of
 * lingering in the folder for ever.
 */
val publishWebApp by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Copies the browser build into web/app, ready to deploy."
    dependsOn(tasks.named("wasmJsBrowserDistribution"))
    from(layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
    into(rootProject.layout.projectDirectory.dir("web/app"))
}
