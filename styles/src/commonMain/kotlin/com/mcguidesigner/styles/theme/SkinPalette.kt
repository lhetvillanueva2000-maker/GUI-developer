package com.mcguidesigner.styles.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The colour and metric tokens one edition's skin is built from.
 *
 * Java and Bedrock each supply their own instance from their own style folder.
 * Nothing outside a skin folder is allowed to hard-code a colour, which is
 * what keeps the two visual identities genuinely separate: changing
 * `styles/bedrock` can never alter how Java screens look.
 */
data class SkinPalette(
    // --- Widget surfaces ---
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    val slot: Color,
    val slotShadow: Color,
    val slotHighlight: Color,

    // --- Borders & bevels ---
    val bevelLight: Color,
    val bevelDark: Color,
    val outline: Color,

    // --- Interactive ---
    val control: Color,
    val controlHover: Color,
    val controlPressed: Color,
    val controlDisabled: Color,
    val controlFocusRing: Color,
    val accent: Color,
    val accentMuted: Color,

    // --- Text ---
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val textShadow: Color,
    val textOnAccent: Color,

    // --- Editor chrome (the app around the canvas) ---
    val chromeBackground: Color,
    val chromePanel: Color,
    val chromePanelAlt: Color,
    val chromeBorder: Color,
    val chromeText: Color,
    val chromeTextMuted: Color,
    val selection: Color,
    val selectionFill: Color,
    val guideLine: Color,
    val gridLine: Color,
    val gridLineMajor: Color,

    // --- Metrics ---
    /** Border thickness in GUI pixels. Java is 1px crisp, Bedrock is chunkier. */
    val borderWidth: Int,
    /** Corner rounding in GUI pixels; Java is square, Bedrock rounds slightly. */
    val cornerRadius: Int,
    /** Editor chrome corner radius. */
    val chromeCorner: Dp = 6.dp,
    /** Default control height in GUI pixels for this edition. */
    val controlHeight: Int,
) {
    /** Interpolates towards [other] - used for hover/press transitions. */
    fun blendControl(pressed: Boolean, hovered: Boolean, enabled: Boolean): Color = when {
        !enabled -> controlDisabled
        pressed -> controlPressed
        hovered -> controlHover
        else -> control
    }
}
