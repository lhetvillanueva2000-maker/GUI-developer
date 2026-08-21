package com.mcguidesigner.android.io

import android.content.Context
import com.mcguidesigner.styles.theme.ThemeMode

/**
 * The handful of settings the Android app remembers between launches.
 *
 * Plain `SharedPreferences`: this is four values that must survive a reinstall
 * of the process, not a document.  The working document has its own store -
 * see [SessionStore] - and the libraries have theirs.
 */
object AndroidPreferences {

    private const val FILE = "mcgui-settings"
    private const val KEY_THEME = "themeMode"
    private const val KEY_BACKDROP = "backdropEnabled"
    private const val KEY_MOTION = "backdropMotion"

    /** Everything read in one go at startup, so the first frame is correct. */
    data class Settings(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val backdropEnabled: Boolean = true,
        val backdropMotion: Boolean = true,
    )

    fun load(context: Context): Settings = runCatching {
        val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        Settings(
            themeMode = ThemeMode.fromName(preferences.getString(KEY_THEME, null)),
            backdropEnabled = preferences.getBoolean(KEY_BACKDROP, true),
            backdropMotion = preferences.getBoolean(KEY_MOTION, true),
        )
    }.getOrElse { Settings() }

    fun save(context: Context, settings: Settings) {
        runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME, settings.themeMode.name)
                .putBoolean(KEY_BACKDROP, settings.backdropEnabled)
                .putBoolean(KEY_MOTION, settings.backdropMotion)
                .apply()
        }
    }
}
