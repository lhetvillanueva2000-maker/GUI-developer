package com.mcguidesigner.styles.settings

import com.mcguidesigner.styles.theme.ChromeTheme
import com.mcguidesigner.styles.theme.MotionLevel
import com.mcguidesigner.styles.theme.ThemeMode

/**
 * Everything the settings screen can change about how the app looks and moves.
 *
 * One type, both shells. The desktop keeps it in `preferences.json` and Android
 * in its own store, but they persist the *same* object, so a setting cannot
 * exist on one platform and quietly not on the other - which is how the old
 * `backdropMotion` flag ended up meaning subtly different things in the two
 * places that read it.
 */
data class AppearanceSettings(
    /** Light, dark, or follow the host. */
    val theme: ThemeMode = ThemeMode.SYSTEM,

    /** How the chrome is recoloured on top of the edition's own palette. */
    val chromeTheme: ChromeTheme = ChromeTheme.DEFAULT,

    /** How much the interface is allowed to move. */
    val motion: MotionLevel = MotionLevel.FULL,

    /** Whether the artwork behind the editor is drawn at all. */
    val backdropEnabled: Boolean = true,

    /**
     * What to call you, on this device only.
     *
     * There is no account and nothing is sent anywhere; this is the name the
     * home screen greets you by and nothing more. It is stored beside the rest
     * of the preferences, in the same file, on the same machine.
     */
    val profileName: String = "",
) {
    /**
     * Whether the backdrop should actually be animating right now.
     *
     * Two separate questions collapse into one answer here so no caller has to
     * remember to ask both: the artwork can be switched off entirely, and
     * motion can be switched off while keeping the artwork. Drawing a still
     * backdrop is nearly free; looping one is a redraw every frame for as long
     * as the window is open.
     */
    val backdropMoves: Boolean get() = backdropEnabled && motion.allowsLoops

    /** The greeting for the home screen, or null when no name has been set. */
    val greeting: String? get() = profileName.trim().takeIf { it.isNotEmpty() }

    companion object {
        /**
         * Rebuilds the settings from what a preferences file actually holds.
         *
         * [storedMotion] is nullable on purpose. Releases before 1.6.0 had no
         * motion level, only a `backdropMotion` boolean, and a file written by
         * one of them is indistinguishable from a new one unless the absence
         * of the key is preserved rather than defaulted away. Somebody who had
         * turned the drift off was telling us they wanted less motion, so that
         * choice is carried forward to [MotionLevel.REDUCED] instead of being
         * silently reset to [MotionLevel.FULL] on upgrade.
         */
        fun fromStored(
            themeMode: String?,
            chromeTheme: String?,
            storedMotion: String?,
            backdropEnabled: Boolean,
            legacyBackdropMotion: Boolean,
            profileName: String,
        ): AppearanceSettings = AppearanceSettings(
            theme = ThemeMode.fromName(themeMode),
            chromeTheme = ChromeTheme.fromName(chromeTheme),
            motion = when {
                storedMotion != null -> MotionLevel.fromName(storedMotion)
                !legacyBackdropMotion -> MotionLevel.REDUCED
                else -> MotionLevel.FULL
            },
            backdropEnabled = backdropEnabled,
            profileName = profileName,
        )
    }
}
