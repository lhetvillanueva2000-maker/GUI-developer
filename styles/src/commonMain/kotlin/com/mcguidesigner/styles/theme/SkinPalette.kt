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

    // --- The filled call-to-action ---
    //
    // Both Minecraft editions draw their one prominent button as an ordinary
    // control - stone-grey fill, white text - because that is what the game
    // does, so for them these default to exactly that and nothing changes.
    //
    // They exist because an edition whose controls are pale has to be able to
    // say so. Other UIs fills its controls with a near-white neutral, and a
    // near-white fill under `textOnAccent` is white text on a white button: a
    // label that is not dim but genuinely absent. Anything drawing a *filled*
    // primary action asks for these instead of assuming that `control` and
    // `textOnAccent` happen to contrast with each other.
    val ctaFill: Color = control,
    val ctaFillHover: Color = controlHover,
    val ctaFillPressed: Color = controlPressed,
    val ctaText: Color = textOnAccent,

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
    /**
     * Wash drawn over the backdrop artwork behind the editor.
     *
     * The artwork is decoration; the docks and canvas sitting on it are the
     * work.  This is the token that keeps the second readable over the first,
     * and it is the one chrome colour that has to carry alpha.
     */
    val backdropScrim: Color = Color(0xAA000000),

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

    /** [blendControl] for the filled primary action; pairs with [ctaText]. */
    fun blendCta(pressed: Boolean, hovered: Boolean, enabled: Boolean): Color = when {
        !enabled -> controlDisabled
        pressed -> ctaFillPressed
        hovered -> ctaFillHover
        else -> ctaFill
    }
}

/**
 * Black or white, whichever can actually be read on [background].
 *
 * Every colour in a palette is chosen with the colours around it in mind, so
 * within one skin the pairs are known good. Element backgrounds are not: they
 * come from whoever is holding the tool, and "white label on a white button"
 * is one colour-picker tap away in every design tool ever written. Rather than
 * hope, ask.
 *
 * The weights are the sRGB luminance ones, and the 0.55 threshold is where
 * white text stops winning on a mid-grey. Alpha is ignored - a translucent
 * fill is judged as if it were laid on its own colour, which is close enough
 * for choosing between two extremes.
 */
fun readableTextOn(background: Color, light: Color = Color.White, dark: Color = Color(0xFF10151A)): Color {
    val luminance = 0.2126f * background.red + 0.7152f * background.green + 0.0722f * background.blue
    return if (luminance > 0.55f) dark else light
}
