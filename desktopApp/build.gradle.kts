import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":sharedCore"))
    implementation(project(":styles"))
    implementation(project(":exporters"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.swing)
    testImplementation(kotlin("test"))
}

// The window and taskbar icon is the same artwork the installers stamp onto
// the executable, copied onto the classpath at build time so `gradle run` and
// the portable jar are not the odd ones out with a blank default icon. Copied
// rather than duplicated so `assets/icon` stays the single source of truth.
val copyAppIcon by tasks.registering(Copy::class) {
    from(rootProject.file("assets/icon/icon-256.png")) { rename { "app-icon.png" } }
    into(layout.buildDirectory.dir("generated/appIcon"))
}

// The editor wallpaper, one image per edition per theme. Same reasoning as the
// icon: `assets/backdrop` stays the single source of truth and the files are
// copied onto the classpath so every way of launching the app finds them.
val copyBackdrops by tasks.registering(Copy::class) {
    from(rootProject.file("assets/backdrop")) { include("backdrop-*.png") }
    into(layout.buildDirectory.dir("generated/backdrops"))
}

sourceSets.named("main") {
    resources.srcDir(layout.buildDirectory.dir("generated/appIcon"))
    resources.srcDir(layout.buildDirectory.dir("generated/backdrops"))
}

tasks.named("processResources") { dependsOn(copyAppIcon, copyBackdrops) }

compose.desktop {
    application {
        mainClass = "com.mcguidesigner.desktop.MainKt"

        nativeDistributions {
            // Windows is the primary desktop target; the others are produced
            // by the same task on their own hosts, which is why they are all
            // listed here rather than switched on the current OS.
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)

            packageName = "MinecraftGuiDesigner"
            packageVersion = project.version.toString()
            description = "Visual GUI designer for Minecraft Java and Bedrock Edition"
            vendor = "Minecraft GUI Designer"
            copyright = "Released under the MIT License"

            // Only the modules the app actually needs, so the bundled runtime
            // stays around 45 MB instead of shipping the whole JDK.
            modules(
                "java.base",
                "java.desktop",
                "java.logging",
                "java.prefs",
                "jdk.unsupported",
            )

            windows {
                iconFile.set(rootProject.file("assets/icon/app-icon.ico"))
                menuGroup = "Minecraft GUI Designer"
                dirChooser = true
                perUserInstall = true
                shortcut = true
                // A stable UUID is required for Windows upgrade installs to
                // replace the previous version rather than install alongside it.
                upgradeUuid = "0f2b7c1e-6b1a-4c53-9a3e-2f5b8d41c7a6"
            }

            linux {
                iconFile.set(rootProject.file("assets/icon/icon-512.png"))
                packageName = "minecraft-gui-designer"
                debMaintainer = "noreply@example.com"
                appCategory = "Graphics"
            }

            macOS {
                bundleID = "com.mcguidesigner.desktop"
                dockName = "MC GUI Designer"
            }
        }

        buildTypes.release.proguard {
            // The Compose/Skia runtime does not survive aggressive shrinking;
            // the packaged runtime is already trimmed by `modules(...)`.
            isEnabled.set(false)
        }
    }
}
