package com.mcguidesigner.core.model

/** One named colour offered as a swatch. */
data class Swatch(val name: String, val argb: Long)

/** A titled row of [Swatch]es. */
data class SwatchGroup(val title: String, val swatches: List<Swatch>)

/**
 * Colours to pick from, so the only way to choose one is not to type its hex.
 *
 * Typing `FF3C3C3C` is fine when you already know the number and hopeless when
 * you do not, which is most of the time. What was missing was not a colour
 * *space* - the hex field can reach every colour there is - but a starting
 * point.
 *
 * Three groups, because they answer three different questions:
 *
 *  - **Minecraft** is the answer to "what colour is the game actually using
 *    here". These are measured off vanilla widgets, and getting them right by
 *    eye is close to impossible - a panel that is four greys off looks wrong
 *    without looking obviously wrong, which is the worst kind of wrong.
 *  - **Greys** is the answer to "a bit lighter than that", which is most
 *    interface work.
 *  - **Colours** is a full hue circle at three lightnesses, for everything
 *    else.
 *
 * Kept in `sharedCore` rather than beside either picker for the usual reason:
 * two copies is two chances for the phone to offer a colour the desktop does
 * not.
 */
object ColorPalette {

    /** Greys and browns measured from vanilla Java and Bedrock interfaces. */
    private val minecraft = listOf(
        Swatch("Panel", 0xFFC6C6C6),
        Swatch("Panel shadow", 0xFF555555),
        Swatch("Panel light", 0xFFFFFFFF),
        Swatch("Slot", 0xFF8B8B8B),
        Swatch("Slot shadow", 0xFF373737),
        Swatch("Button", 0xFF6C6C6C),
        Swatch("Button hover", 0xFF8090FF),
        Swatch("Disabled", 0xFFA0A0A0),
        Swatch("Tooltip", 0xFF100010),
        Swatch("Tooltip edge", 0xFF5000FF),
        Swatch("Dirt", 0xFF7B5B3F),
        Swatch("Oak", 0xFFB08B54),
        Swatch("Stone", 0xFF7F7F7F),
        Swatch("Deepslate", 0xFF3A3A40),
        Swatch("Text", 0xFFE0E0E0),
        Swatch("Text shadow", 0xFF3F3F3F),
        Swatch("XP green", 0xFF80FF20),
        Swatch("Health red", 0xFFDC1414),
    )

    /** A neutral ramp from white to black. */
    private val greys = listOf(
        Swatch("White", 0xFFFFFFFF),
        Swatch("Grey 95", 0xFFF2F2F2),
        Swatch("Grey 85", 0xFFD9D9D9),
        Swatch("Grey 70", 0xFFB3B3B3),
        Swatch("Grey 55", 0xFF8C8C8C),
        Swatch("Grey 40", 0xFF666666),
        Swatch("Grey 28", 0xFF474747),
        Swatch("Grey 18", 0xFF2E2E2E),
        Swatch("Grey 10", 0xFF1A1A1A),
        Swatch("Black", 0xFF000000),
    )

    /**
     * Fourteen hues at three lightnesses.
     *
     * Written out rather than generated from a hue rotation: an even sweep
     * through HSL puts four near-identical greens next to each other and no
     * usable brown at all, because perceived lightness is not evenly spread
     * around the circle. These are picked to look evenly spaced, which is what
     * a palette is for.
     */
    private val colours = listOf(
        Swatch("Red light", 0xFFFF8A80), Swatch("Red", 0xFFE53935), Swatch("Red dark", 0xFF8E1B18),
        Swatch("Orange light", 0xFFFFB74D), Swatch("Orange", 0xFFF57C00), Swatch("Orange dark", 0xFF9A4B00),
        Swatch("Amber light", 0xFFFFE082), Swatch("Amber", 0xFFFFB300), Swatch("Amber dark", 0xFF9C6F00),
        Swatch("Yellow light", 0xFFFFF59D), Swatch("Yellow", 0xFFFDD835), Swatch("Yellow dark", 0xFF9E8420),
        Swatch("Lime light", 0xFFD4E86A), Swatch("Lime", 0xFFAFB42B), Swatch("Lime dark", 0xFF6B7016),
        Swatch("Green light", 0xFF9BE7A0), Swatch("Green", 0xFF43A047), Swatch("Green dark", 0xFF1F5F23),
        Swatch("Teal light", 0xFF80CBC4), Swatch("Teal", 0xFF00897B), Swatch("Teal dark", 0xFF004D42),
        Swatch("Cyan light", 0xFF80DEEA), Swatch("Cyan", 0xFF00ACC1), Swatch("Cyan dark", 0xFF00626F),
        Swatch("Blue light", 0xFF90CAF9), Swatch("Blue", 0xFF1E88E5), Swatch("Blue dark", 0xFF104A80),
        Swatch("Indigo light", 0xFF9FA8DA), Swatch("Indigo", 0xFF3949AB), Swatch("Indigo dark", 0xFF1F2861),
        Swatch("Violet light", 0xFFB39DDB), Swatch("Violet", 0xFF5E35B1), Swatch("Violet dark", 0xFF341C66),
        Swatch("Magenta light", 0xFFE1A6E8), Swatch("Magenta", 0xFF8E24AA), Swatch("Magenta dark", 0xFF521460),
        Swatch("Pink light", 0xFFF8BBD0), Swatch("Pink", 0xFFD81B60), Swatch("Pink dark", 0xFF7B0F37),
        Swatch("Brown light", 0xFFBCAAA4), Swatch("Brown", 0xFF6D4C41), Swatch("Brown dark", 0xFF3E2B24),
    )

    /**
     * Fully transparent, kept apart from every other swatch.
     *
     * A colour with no alpha is not a shade, it is the absence of one, and
     * putting it in a row of colours means somebody eventually picks it by
     * accident and cannot work out why their panel vanished.
     */
    val transparent = Swatch("None", 0x00000000)

    val groups: List<SwatchGroup> = listOf(
        SwatchGroup("Minecraft", minecraft),
        SwatchGroup("Greys", greys),
        SwatchGroup("Colours", colours),
    )

    /** Every swatch in one list, for a search or a compact grid. */
    val all: List<Swatch> get() = groups.flatMap { it.swatches }

    /**
     * The swatch matching [argb]'s colour, ignoring its alpha, or null.
     *
     * Alpha is ignored on purpose: a swatch says which *colour* is selected,
     * and a panel faded to half opacity is still that colour. Comparing all
     * four channels would leave the grid showing nothing selected the moment
     * anybody touched the alpha slider.
     */
    fun matching(argb: Long): Swatch? {
        val rgb = argb and 0xFFFFFF
        return all.firstOrNull { (it.argb and 0xFFFFFF) == rgb }
    }
}
