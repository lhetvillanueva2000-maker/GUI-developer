plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
}

val projectVersion: String = providers.gradleProperty("mcgui.version").getOrElse("1.0.0")

allprojects {
    group = "com.mcguidesigner"
    version = projectVersion
}

/**
 * Regenerates the bundled `.mcgui` documents in `templates`, plus the
 * worked example under `templates/sample-output`, and fails the build on any
 * validation error in a bundled template.
 *
 * The task lives here rather than in `:exporters` so its classpath can be
 * taken from `:desktopApp`, an ordinary JVM module: that avoids resolving a
 * Kotlin Multiplatform compilation during configuration, which is exactly the
 * kind of eager access that upsets the AGP/KMP plugin ordering in the shared
 * modules.  `:desktopApp` already depends on `:exporters`, so the tool class
 * and every runtime dependency it needs are on that classpath.
 */
val exportTemplates = tasks.register<JavaExec>("exportTemplates") {
    group = "documentation"
    description = "Regenerates the bundled templates and the sample export output."
    dependsOn(":desktopApp:classes")
    mainClass.set("com.mcguidesigner.exporters.tools.TemplateExporterKt")
    classpath = files(
        provider {
            project(":desktopApp")
                .extensions
                .getByType(SourceSetContainer::class.java)
                .getByName("main")
                .runtimeClasspath
        },
    )
    args(rootDir.absolutePath)
}

/**
 * Regenerates every form of the app icon - the desktop PNGs, the Windows
 * `.ico`, the reference SVG and the Android adaptive-icon vector drawables -
 * from the single geometry description in `build-scripts/icon`.
 *
 * It runs as a single-file source program, so there is nothing to compile and
 * no dependency to keep up to date.
 */
tasks.register<Exec>("generateIcons") {
    group = "documentation"
    description = "Re-renders the application icon for every platform."
    // Java 11+ single-file source mode: no compile step, no extra dependency.
    commandLine(
        File(System.getProperty("java.home"), "bin/java").absolutePath,
        rootProject.file("build-scripts/icon/IconRenderer.java").absolutePath,
        rootDir.absolutePath,
    )
}

tasks.register("validateProjects") {
    group = "verification"
    description = "Validates every bundled template; fails the build on any error."
    dependsOn(exportTemplates)
}

/**
 * Runs every test in the repository.
 *
 * `allTests` is a Kotlin Multiplatform task, so it covers `:sharedCore`,
 * `:styles` and `:exporters` but silently skips the plain-JVM `:desktopApp` -
 * which is exactly the kind of gap that lets a whole test class sit in the
 * repository never being run. This is what CI should call.
 */
tasks.register("testAll") {
    group = "verification"
    description = "Runs the multiplatform tests and the desktop app's own tests."
    // Named one by one rather than by convention: a module whose tests stop
    // being run should break this list loudly, not disappear from it quietly.
    dependsOn(
        ":sharedCore:allTests",
        ":styles:allTests",
        ":exporters:allTests",
        ":desktopApp:test",
    )
    // The Android module is dropped from the build entirely when there is no
    // SDK, so its tests can only be required when it is actually there.
    if (findProject(":androidApp") != null) {
        dependsOn(":androidApp:testDebugUnitTest")
    }
}

/**
 * Convenience aggregate: assembles every artifact that the current machine is
 * able to produce.  On Linux/macOS this is the desktop distributable plus the
 * Android APK; on Windows it additionally produces the `.exe` installer.
 */
tasks.register("assembleAll") {
    group = "distribution"
    description = "Builds every artifact that can be produced on the current host."
    dependsOn(":desktopApp:packageDistributionForCurrentOS")
    if (providers.gradleProperty("skipAndroid").getOrElse("false") != "true" &&
        findProject(":androidApp") != null
    ) {
        dependsOn(":androidApp:assembleRelease")
    }
}
