package com.mcguidesigner.desktop.io

import com.mcguidesigner.core.editor.EditorSettings
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.serialization.LoadResult
import com.mcguidesigner.core.serialization.ProjectSerializer
import com.mcguidesigner.styles.settings.AppearanceSettings
import com.mcguidesigner.styles.theme.ChromeTheme
import com.mcguidesigner.styles.theme.ThemeMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Everything the designer remembers between runs.
 *
 * Deliberately all primitives and lists of strings: preferences outlive the
 * build that wrote them, so the file has to survive a version upgrade that
 * renamed an enum entry or dropped a panel. Unknown keys are ignored and a
 * missing key falls back to the default below, which means a corrupt or
 * half-written file degrades to "first run" instead of failing to start.
 */
@Serializable
data class DesktopPreferences(
    val recentFiles: List<String> = emptyList(),
    val lastDirectory: String? = null,
    val windowWidth: Int = 1600,
    val windowHeight: Int = 1000,
    val windowX: Int = -1,
    val windowY: Int = -1,
    val maximized: Boolean = false,
    val showLeftDock: Boolean = true,
    val showRightDock: Boolean = true,
    val showBottomDock: Boolean = false,
    val lastEdition: String = Edition.JAVA.name,
    val codeTarget: String? = null,
    val exportTarget: String? = null,
    val showWelcomeOnStart: Boolean = true,
    val autosaveEnabled: Boolean = true,
    /**
     * [ThemeMode] name.
     *
     * Anything unrecognised - including "SYSTEM" from a file written before
     * 1.7.0 - reads as dark, which is what SYSTEM resolved to on desktop
     * anyway, so the upgrade is invisible.
     */
    val themeMode: String = ThemeMode.DARK.name,
    /** [ChromeTheme] name; anything unrecognised falls back to the edition's own. */
    val chromeTheme: String = ChromeTheme.DEFAULT.name,
    /**
     * [MotionLevel] name, or null on a file written before 1.6.0.
     *
     * Nullable on purpose: a defaulted value cannot be told apart from one
     * somebody chose, and the difference matters exactly once - on upgrade,
     * where [backdropMotion] is the only record of what they wanted. See
     * [AppearanceSettings.fromStored].
     */
    val motionLevel: String? = null,
    /** Wallpaper behind the editor. Purely decorative, so trivially disabled. */
    val backdropEnabled: Boolean = true,
    /**
     * Whether the wallpaper drifts.
     *
     * Superseded by [motionLevel] in 1.6.0 and kept written for two reasons:
     * it is what an older build reads if someone downgrades, and it is what
     * the upgrade path reads to work out what a pre-1.6.0 user had chosen.
     */
    val backdropMotion: Boolean = true,
    /** Local display name. Never sent anywhere; there is nowhere to send it. */
    val profileName: String = "",
    /**
     * The id of the last release note that was dismissed.
     *
     * Stored as the note's id rather than a boolean so shipping a new version
     * shows its note again with no extra bookkeeping: the id contains the
     * version, so it simply stops matching.
     */
    val dismissedNoticeId: String? = null,
    /**
     * Editor behaviour from the Editor Settings dialog.
     *
     * Nested as its own object rather than flattened into this class because
     * the Android app persists the identical type - one definition, two
     * stores, no chance of the two platforms disagreeing about what a nudge
     * step is.
     */
    val editorSettings: EditorSettings = EditorSettings(),
) {
    val edition: Edition
        get() = Edition.entries.firstOrNull { it.name == lastEdition } ?: Edition.JAVA

    val theme: ThemeMode get() = ThemeMode.fromName(themeMode)

    /** Everything the settings screen owns, rebuilt from what was on disk. */
    val appearance: AppearanceSettings
        get() = AppearanceSettings.fromStored(
            themeMode = themeMode,
            chromeTheme = chromeTheme,
            storedMotion = motionLevel,
            backdropEnabled = backdropEnabled,
            legacyBackdropMotion = backdropMotion,
            profileName = profileName,
        )

    /** Recent entries that still exist on disk, newest first. */
    fun existingRecents(): List<File> = recentFiles.map(::File).filter { it.isFile }
}

/**
 * Reads and writes [DesktopPreferences] and the crash-recovery snapshot.
 *
 * Both live in the per-user application data directory rather than next to
 * the executable, because the Windows installer puts the app somewhere the
 * user cannot write to.
 */
object Workspace {

    private const val PREFERENCES_FILE = "preferences.json"
    private const val RECOVERY_DOCUMENT = "recovery.mcgui"
    private const val RECOVERY_MARKER = "recovery.json"
    private const val MAX_RECENTS = 10

    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Redirects the workspace, for tests.
     *
     * Without it a test would read and write the developer's real preferences
     * and could hand them back someone else's recovery snapshot.
     */
    private var directoryOverride: File? = null

    fun useDirectoryForTesting(target: File?) {
        directoryOverride = target?.also { it.mkdirs() }
    }

    /** Where preferences and the recovery snapshot live. */
    val directory: File
        get() = directoryOverride ?: defaultDirectory

    /**
     * Per-user data directory, following each platform's convention:
     * `%APPDATA%` on Windows, `~/Library/Application Support` on macOS and
     * `$XDG_CONFIG_HOME` (or `~/.config`) elsewhere.
     */
    private val defaultDirectory: File by lazy {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val home = File(System.getProperty("user.home"))
        val base = when {
            os.contains("win") ->
                System.getenv("APPDATA")?.let(::File) ?: File(home, "AppData/Roaming")

            os.contains("mac") || os.contains("darwin") ->
                File(home, "Library/Application Support")

            else ->
                System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
                    ?: File(home, ".config")
        }
        // Deliberately NOT renamed alongside the app in 1.6.0. This folder is
        // where every existing install's preferences, recent files and crash
        // recovery snapshot already live; pointing at a new one would silently
        // orphan all of it. A stale folder name is a cosmetic cost, and losing
        // somebody's unsaved work to a rename is not a trade worth making.
        File(base, "MinecraftGuiDesigner").also { runCatching { it.mkdirs() } }
    }

    // -- Preferences -------------------------------------------------------

    fun loadPreferences(): DesktopPreferences {
        val file = File(directory, PREFERENCES_FILE)
        if (!file.isFile) return DesktopPreferences()
        return runCatching {
            json.decodeFromString(DesktopPreferences.serializer(), file.readText())
        }.getOrElse { DesktopPreferences() }
    }

    /**
     * Writes preferences through a temporary file.
     *
     * A half-written preferences file would be indistinguishable from a
     * corrupt one on the next launch, and losing the recent-files list to a
     * badly timed shutdown is exactly the kind of small breakage that makes
     * an app feel unfinished.
     */
    fun savePreferences(preferences: DesktopPreferences) {
        runCatching {
            directory.mkdirs()
            val target = File(directory, PREFERENCES_FILE)
            val temporary = File(directory, "$PREFERENCES_FILE.tmp")
            temporary.writeText(json.encodeToString(DesktopPreferences.serializer(), preferences))
            if (!temporary.renameTo(target)) {
                target.writeText(temporary.readText())
                temporary.delete()
            }
        }
    }

    /** Adds [file] to the front of [recents], de-duplicated and capped. */
    fun withRecent(recents: List<File>, file: File): List<File> =
        (listOf(file.absoluteFile) + recents.map { it.absoluteFile })
            .distinctBy { it.path }
            .take(MAX_RECENTS)

    // -- Crash recovery ----------------------------------------------------

    @Serializable
    data class RecoveryMarker(
        val originalPath: String? = null,
        val projectName: String = "Untitled",
        val savedAtMillis: Long = 0L,
    )

    /** A recovered document plus the metadata needed to describe it. */
    data class Recovery(val project: GuiProject, val marker: RecoveryMarker) {
        val originalFile: File? get() = marker.originalPath?.let(::File)
    }

    /**
     * Snapshots the working document.
     *
     * Called on a timer while the document is dirty. The snapshot is deleted
     * on a clean save or a clean exit, so its mere existence on startup is
     * what signals that the previous session did not end normally.
     */
    fun writeRecoverySnapshot(project: GuiProject, originalPath: String?) {
        runCatching {
            directory.mkdirs()
            val document = File(directory, RECOVERY_DOCUMENT)
            val temporary = File(directory, "$RECOVERY_DOCUMENT.tmp")
            temporary.writeText(ProjectSerializer.encode(project))
            if (!temporary.renameTo(document)) {
                document.writeText(temporary.readText())
                temporary.delete()
            }
            File(directory, RECOVERY_MARKER).writeText(
                json.encodeToString(
                    RecoveryMarker.serializer(),
                    RecoveryMarker(originalPath, project.name, System.currentTimeMillis()),
                ),
            )
        }
    }

    /** The snapshot left behind by a session that did not exit cleanly. */
    fun pendingRecovery(): Recovery? {
        val document = File(directory, RECOVERY_DOCUMENT).takeIf { it.isFile } ?: return null
        val marker = runCatching {
            json.decodeFromString(RecoveryMarker.serializer(), File(directory, RECOVERY_MARKER).readText())
        }.getOrElse { RecoveryMarker() }
        return when (val result = ProjectSerializer.decode(document.readText())) {
            is LoadResult.Success -> Recovery(result.project, marker)
            is LoadResult.Failure -> {
                clearRecovery()
                null
            }
        }
    }

    fun clearRecovery() {
        runCatching {
            File(directory, RECOVERY_DOCUMENT).delete()
            File(directory, RECOVERY_MARKER).delete()
        }
    }
}
