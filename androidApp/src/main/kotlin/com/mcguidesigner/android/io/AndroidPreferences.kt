package com.mcguidesigner.android.io

import android.content.Context
import com.mcguidesigner.core.editor.EditorSettings
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
    private const val KEY_BACKDROP = "backdropEnabled"
    private const val KEY_MOTION = "backdropMotion"
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
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val backdropEnabled: Boolean = true,
        val backdropMotion: Boolean = true,
        val editor: EditorSettings = EditorSettings(),
    )

    fun load(context: Context): Settings = runCatching {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        Settings(
            themeMode = ThemeMode.fromName(preferences.getString(KEY_THEME, null)),
            backdropEnabled = preferences.getBoolean(KEY_BACKDROP, true),
            backdropMotion = preferences.getBoolean(KEY_MOTION, true),
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
                .putString(KEY_THEME, settings.themeMode.name)
                .putBoolean(KEY_BACKDROP, settings.backdropEnabled)
                .putBoolean(KEY_MOTION, settings.backdropMotion)
                .putString(KEY_EDITOR, json.encodeToString(EditorSettings.serializer(), settings.editor))
                .apply()
        }
    }
}
