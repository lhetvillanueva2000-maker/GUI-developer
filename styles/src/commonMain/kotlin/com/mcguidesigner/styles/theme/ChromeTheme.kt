package com.mcguidesigner.styles.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * A recolouring of the application chrome, on top of whatever the edition asks
 * for.
 *
 * Deliberately a *recipe* rather than a set of hand-authored palettes. There
 * are two editions and two light/dark modes, so seven hand-written themes would
 * mean twenty-eight palettes to keep in agreement, and the first one anybody
 * forgot to update would be the bug. A theme instead states a hue, how far to
 * pull the surfaces towards it, and how much to deepen the contrast; the
 * edition keeps ownership of its own starting colours, and a new edition gets
 * every theme for free.
 *
 * Two things are deliberately left alone:
 *
 *  - **Text.** [ChromeColors.text] and [ChromeColors.textMuted] pass through
 *    untouched, so no theme can make the app unreadable however badly its hue
 *    is chosen. Themes shift what the app is *made of*, not whether you can
 *    read it.
 *  - **The accent, and every widget colour.** Those belong to the edition and,
 *    through it, to the game. A Minecraft button is the same grey whatever the
 *    editor is painted, for the same reason the light theme never touched the
 *    canvas: a design tool that recolours its own preview is lying about the
 *    result.
 */
enum class ChromeTheme(
    val displayName: String,
    val blurb: String,
    private val hue: Color,
    private val strength: Float,
    private val contrast: Float,
    /**
     * How dark this theme sits in dark mode, as a luminance.
     *
     * Per-theme rather than one shared ceiling, because a shared ceiling makes
     * every dark theme exactly as dark as every other - and two of these are
     * greys, so normalising both to the same brightness produced *byte
     * identical* Slate and Deepslate. "Nearly black" and "mid grey" are the
     * whole difference between those two, and it has to be expressible.
     */
    private val darkLuma: Float = 0.09f,
) {
    /** No recolouring at all: whatever Java or Bedrock defines for itself. */
    DEFAULT(
        displayName = "Default",
        blurb = "Java green and Bedrock blue, exactly as each edition defines them.",
        hue = Color.Black,
        strength = 0f,
        contrast = 0f,
    ),

    MIDNIGHT(
        displayName = "Midnight",
        blurb = "Deep navy with the contrast pushed up. Easiest on a dark room.",
        hue = Color(0xFF0C1A38),
        strength = 0.62f,
        contrast = 0.14f,
        darkLuma = 0.085f,
    ),

    DEEPSLATE(
        displayName = "Deepslate",
        blurb = "Near-black and almost colourless. The least light a screen can throw.",
        hue = Color(0xFF16181C),
        strength = 0.78f,
        contrast = 0.20f,
        darkLuma = 0.065f,
    ),

    SLATE(
        displayName = "Slate",
        blurb = "Neutral grey. Nothing competes with the canvas for attention.",
        hue = Color(0xFF3A4048),
        strength = 0.66f,
        contrast = 0f,
        darkLuma = 0.155f,
    ),

    SANDSTONE(
        displayName = "Sandstone",
        blurb = "Warm and papery. Made for working in daylight.",
        hue = Color(0xFFC2A878),
        strength = 0.44f,
        contrast = -0.06f,
        darkLuma = 0.115f,
    ),

    NETHER(
        displayName = "Nether",
        blurb = "Crimson and warm shadow.",
        hue = Color(0xFF4A1418),
        strength = 0.58f,
        contrast = 0.08f,
        darkLuma = 0.090f,
    ),

    END(
        displayName = "End",
        blurb = "Violet and pale gold, the colours of nowhere in particular.",
        hue = Color(0xFF2A1F45),
        strength = 0.60f,
        contrast = 0.10f,
        darkLuma = 0.075f,
    ),
    ;

    /**
     * [base] recoloured for this theme.
     *
     * A theme names a *colour*, not a brightness, so the hue is first
     * normalised into the range the current mode can actually use. Skipping
     * that step breaks the themes at both ends: pulling a near-white light
     * background most of the way towards a dark navy turns the light theme
     * dark, and pulling a near-black dark background towards Sandstone's warm
     * tan turns the dark theme into milky coffee. Both were real - the second
     * one is what the dark-theme test caught. Normalising means every hue
     * works in both modes, and adding an eighth theme is a colour and two
     * numbers rather than a fresh pair of guesses.
     */
    fun apply(base: ChromeColors, dark: Boolean): ChromeColors {
        if (strength <= 0f) return base

        val pull = if (dark) hue.atLuma(darkLuma) else hue.paleTint()
        val amount = if (dark) strength else strength * 0.72f

        // The background goes darker than the panels sitting on it, which is
        // what keeps a panel legible as a distinct surface rather than a shape
        // that happens to have a border. This holds in both modes - a light
        // theme is grey paper with white cards on it, not the reverse - so
        // there is no sign to flip here.
        val depth = contrast

        fun surface(color: Color, extra: Float): Color {
            val tinted = lerp(color, pull, amount)
            return when {
                extra > 0f -> lerp(tinted, Color.Black, extra)
                extra < 0f -> lerp(tinted, Color.White, -extra)
                else -> tinted
            }
        }

        return base.copy(
            background = surface(base.background, depth),
            panel = surface(base.panel, 0f),
            panelAlt = surface(base.panelAlt, -depth * 0.4f),
            border = surface(base.border, 0f),
            gridLine = surface(base.gridLine, 0f),
            gridLineMajor = surface(base.gridLineMajor, 0f),
            // The scrim carries its own alpha and exists to keep panels
            // readable over the artwork; retinting it while preserving that
            // alpha keeps the backdrop in the theme without letting a theme
            // decide how legible the app is.
            backdropScrim = lerp(base.backdropScrim, pull.copy(alpha = base.backdropScrim.alpha), amount),
        )
    }

    companion object {
        fun fromName(name: String?): ChromeTheme =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}

/** Rec. 709 relative luminance - close enough to "how bright does this look". */
private fun Color.luma(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/**
 * This colour, moved to roughly [target] luminance, keeping its hue.
 *
 * Both directions on purpose. Darkening alone was the first version and it
 * cannot express a theme that sits *lighter* than the darkest one - which is
 * exactly what separates Slate from Deepslate, and without it the two came out
 * identical. Approximate rather than exact because the interpolation is
 * perceptual, not linear; what matters here is that it is monotone, and the
 * tests pin down the two things that actually have to hold - dark stays dark,
 * and no two themes land on the same colour.
 */
private fun Color.atLuma(target: Float): Color {
    val l = luma()
    return when {
        l <= 0.0001f -> Color(target, target, target)
        l > target -> lerp(this, Color.Black, 1f - target / l)
        else -> lerp(this, Color.White, (target - l) / (1f - l))
    }
}

/**
 * The pale version of this colour, for tinting a light theme.
 *
 * Not simply "lerp it towards white": that was the first attempt and it made
 * every light theme look the same. Lightening a deep navy by 87% keeps 13% of
 * its colour, which at the strength a surface tint is applied at is a grey
 * with a rumour of blue in it - so Midnight, Nether and End were
 * indistinguishable from each other and from plain Light.
 *
 * Instead the hue is scaled up to full brightness first, which keeps its
 * character, and only then mixed with white - by an amount proportional to how
 * saturated it was to begin with. That last part is what stops the reverse
 * error: normalising a near-grey like Slate amplifies whatever slight cast it
 * had, and a theme called Slate coming out blue is as wrong as Midnight coming
 * out grey. A neutral hue stays neutral, a vivid one stays vivid.
 */
private fun Color.paleTint(): Color {
    val peak = maxOf(red, green, blue)
    if (peak <= 0f) return Color.White
    val floor = minOf(red, green, blue)
    val saturation = (peak - floor) / peak
    val vivid = Color(red / peak, green / peak, blue / peak)
    return lerp(Color.White, vivid, saturation * PALE_TINT_CHROMA)
}

/** How much of a fully saturated hue survives into its pale form. */
private const val PALE_TINT_CHROMA = 0.55f
