package com.mcguidesigner.styles.theme

import androidx.compose.ui.graphics.Color

/**
 * Which way round the editor chrome is painted.
 *
 * [SYSTEM] follows the host: the OS setting on Android, and the desktop's own
 * light/dark preference where the toolkit reports one.  Resolving it is the
 * caller's job because only the shell knows what the host says.
 */
enum class ThemeMode(val displayName: String) {
    DARK("Dark"),
    LIGHT("Light"),
    ;

    /**
     * Whether the chrome is painted dark.
     *
     * [systemIsDark] is still accepted so callers do not all have to change,
     * and is deliberately ignored: "match the system" was removed because on
     * desktop there is no portable way to ask, so it silently resolved to dark
     * and the setting was a choice between "dark", "light" and "dark again".
     * A control with a third option that does nothing is worse than two that
     * both do.
     */
    @Suppress("UNUSED_PARAMETER")
    fun isDark(systemIsDark: Boolean = true): Boolean = this == DARK

    companion object {
        /**
         * Anything unrecognised - including "SYSTEM" from a store written
         * before 1.7.0 - reads as dark, which is what SYSTEM resolved to on
         * desktop anyway and what the app has always looked like.
         */
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DARK
    }
}

/**
 * The colours of the application *around* the canvas.
 *
 * Split out from [SkinPalette] because these - and only these - flip with the
 * light/dark setting.  A Minecraft button is the same grey in a light-themed
 * editor as in a dark one; changing widget colours to match the app's theme
 * would make the canvas lie about what the game will draw, which is the one
 * thing a design tool must never do.
 */
data class ChromeColors(
    val background: Color,
    val panel: Color,
    val panelAlt: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val gridLine: Color,
    val gridLineMajor: Color,
    /** Tint laid over the backdrop artwork so panels stay readable on top. */
    val backdropScrim: Color,
)

/** Replaces only the chrome tokens of a palette, leaving the widget ramp alone. */
fun SkinPalette.withChrome(chrome: ChromeColors): SkinPalette = copy(
    chromeBackground = chrome.background,
    chromePanel = chrome.panel,
    chromePanelAlt = chrome.panelAlt,
    chromeBorder = chrome.border,
    chromeText = chrome.text,
    chromeTextMuted = chrome.textMuted,
    gridLine = chrome.gridLine,
    gridLineMajor = chrome.gridLineMajor,
    backdropScrim = chrome.backdropScrim,
)
