package com.mcguidesigner.android.io

import android.content.Context
import com.mcguidesigner.core.editor.EditorSettings
import com.mcguidesigner.styles.settings.AppearanceSettings
import com.mcguidesigner.styles.theme.ThemeMode
import kotlinx.serialization.json.Json

/**
 * The handful of settings the Android app remembers between launches.
 *
 * Plain `SharedPreferences`: this is a few values that must survive a restart
 * of the process, not a document.  The working document has its own store -
 * see [SessionStore] - and the libraries have theirs.
 */
object AndroidPreferences {

    private const val FILE = "mcgui-settings"
    private const val KEY_THEME = "themeMode"
    private const val KEY_CHROME_THEME = "chromeTheme"
    private const val KEY_MOTION_LEVEL = "motionLevel"
    private const val KEY_BACKDROP = "backdropEnabled"
    private const val KEY_MOTION = "backdropMotion"
    private const val KEY_PROFILE_NAME = "profileName"
    private const val KEY_DISMISSED_NOTICE = "dismissedNoticeId"
    private const val KEY_EDITOR = "editorSettings"

    /**
     * [EditorSettings] is stored as its JSON rather than one key per field.
     *
     * It is the same type the desktop persists, so serialising it whole means
     * a field added to it is picked up by both platforms at once instead of
     * needing a new preference key here as well.
     */
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    /** Everything read in one go at startup, so the first frame is correct. */
    data class Settings(
        val appearance: AppearanceSettings = AppearanceSettings(),
        /**
         * The id of the last release note dismissed, or null.
         *
         * The id carries the version, so a new build's note simply stops
         * matching and shows again - no separate "seen" flag to reset.
         */
        val dismissedNoticeId: String? = null,
        val editor: EditorSettings = EditorSettings(),
    )

    fun load(context: Context): Settings = runCatching {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        Settings(
            appearance = AppearanceSettings.fromStored(
                themeMode = preferences.getString(KEY_THEME, null),
                chromeTheme = preferences.getString(KEY_CHROME_THEME, null),
                // Absent, not defaulted: on a store written before 1.6.0 the
                // old backdrop flag is the only record of what was chosen, and
                // a default would erase the difference. See the companion.
                storedMotion = preferences.getString(KEY_MOTION_LEVEL, null),
                backdropEnabled = preferences.getBoolean(KEY_BACKDROP, true),
                legacyBackdropMotion = preferences.getBoolean(KEY_MOTION, true),
                profileName = preferences.getString(KEY_PROFILE_NAME, null).orEmpty(),
            ),
            dismissedNoticeId = preferences.getString(KEY_DISMISSED_NOTICE, null),
            editor = preferences.getString(KEY_EDITOR, null)
                // A settings blob written by a newer build, or a corrupt one,
                // degrades to the defaults rather than failing the launch.
                ?.let { stored ->
                    runCatching { json.decodeFromString(EditorSettings.serializer(), stored) }.getOrNull()
                }
                ?.sanitised()
                ?: EditorSettings(),
        )
    }.getOrElse { Settings() }

    fun save(context: Context, settings: Settings) {
        runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME, settings.appearance.theme.name)
                .putString(KEY_CHROME_THEME, settings.appearance.chromeTheme.name)
                .putString(KEY_MOTION_LEVEL, settings.appearance.motion.name)
                .putBoolean(KEY_BACKDROP, settings.appearance.backdropEnabled)
                // Written in step with the motion level so a downgrade to an
                // older build still finds a flag it understands.
                .putBoolean(KEY_MOTION, settings.appearance.motion.allowsLoops)
                .putString(KEY_PROFILE_NAME, settings.appearance.profileName)
                .putString(KEY_DISMISSED_NOTICE, settings.dismissedNoticeId)
                .putString(KEY_EDITOR, json.encodeToString(EditorSettings.serializer(), settings.editor))
                .apply()
        }
    }
}
