package com.mcguidesigner.styles.other

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mcguidesigner.styles.theme.ChromeColors
import com.mcguidesigner.styles.theme.SkinPalette

/**
 * Colour tokens for interfaces that are not Minecraft.
 *
 * The other two palettes are *measurements* - vanilla values read off the
 * game's own art, which is why neither of them may be changed for taste. This
 * one has nothing to imitate, so it is a design rather than a recording, and it
 * is allowed to be revised when the design is wrong.
 *
 * It has been revised once, and the reason is worth keeping written down. The
 * first version was built out of near-whites: a white card on a white page with
 * a #F1F3F6 control on it. Every value in it was defensible on its own and the
 * set of them was unusable - controls that did not read as controls, borders at
 * the edge of visibility, and a button that could not be told from the card
 * behind it. Contrast is not a finishing touch you add to a neutral palette; it
 * is the first thing a palette has to get right.
 *
 * So this version is built in pairs. Every surface has a token that is legibly
 * *not* it, every control sits a definite step away from what it is on, and the
 * two text weights are separated far enough to read as a hierarchy rather than
 * as one colour applied inconsistently.
 *
 * There are two full sets, [Light] and [Dark]. A mock-up of a dark app screen
 * drawn out of light tokens is not a dark screen with the colours wrong - it is
 * a light screen, and no amount of setting background colours by hand fixes the
 * borders and the placeholder text. [OtherUiSkin] picks the set from the canvas
 * so a dark design is one backdrop colour away.
 *
 * This file belongs to `styles/other` and must never be referenced from
 * `styles/java` or `styles/bedrock`.
 */
object OtherUiPalette {

    /**
     * One complete, self-consistent set of surface, control and text colours.
     *
     * Grouping them is what makes "the same screen, dark" a real operation
     * rather than a hundred individual decisions - and what stops a renderer
     * from reaching for a light token while drawing on a dark surface, which is
     * the only way this kind of skin ever goes wrong.
     */
    data class Tokens(
        val dark: Boolean,
        /** The page the cards sit on. */
        val page: Color,
        /** A card. Always a visible step away from [page]. */
        val surface: Color,
        /** A raised area inside a card - a list row, a section. */
        val surfaceRaised: Color,
        /** A recessed area: a slider track, an empty image well. */
        val surfaceSunken: Color,
        val divider: Color,
        val shadow: Color,

        val control: Color,
        val controlHover: Color,
        val controlPressed: Color,
        val controlDisabled: Color,
        val controlOutline: Color,

        val textPrimary: Color,
        val textSecondary: Color,
        val textDisabled: Color,
    ) {
        /** The tone one step *up* from [surface]; a header inside a card. */
        val surfaceAlt: Color get() = if (dark) surfaceRaised else surfaceSunken
    }

    /**
     * Light.
     *
     * A white card is fine; a white card on a white page with white controls is
     * not. The page is a definite grey, the sunken tone is darker than the old
     * palette's by four steps, and the outline is one a person can point at.
     */
    val Light = Tokens(
        dark = false,
        page = Color(0xFFEEF1F5),
        surface = Color(0xFFFFFFFF),
        surfaceRaised = Color(0xFFF6F8FB),
        surfaceSunken = Color(0xFFE2E7EE),
        divider = Color(0xFFD3DAE3),
        shadow = Color(0x1F0B1220),

        control = Color(0xFFE7ECF3),
        controlHover = Color(0xFFDCE3EC),
        controlPressed = Color(0xFFCDD6E1),
        controlDisabled = Color(0xFFEDEFF2),
        controlOutline = Color(0xFFB6C0CD),

        textPrimary = Color(0xFF0C1116),
        textSecondary = Color(0xFF4B5665),
        textDisabled = Color(0xFF959FAD),
    )

    /**
     * Dark.
     *
     * Not the light set inverted. Dark interfaces separate their layers by
     * *lifting* the nearer one, so the card is lighter than the page here and
     * darker than it in [Light]; borders carry less of the work and the fills
     * carry more, which is why the outline is close to the control rather than
     * a hard line around it.
     */
    val Dark = Tokens(
        dark = true,
        page = Color(0xFF14171C),
        surface = Color(0xFF1D2128),
        surfaceRaised = Color(0xFF262B34),
        surfaceSunken = Color(0xFF0F1216),
        divider = Color(0xFF333A45),
        shadow = Color(0x66000000),

        control = Color(0xFF2C323C),
        controlHover = Color(0xFF363D49),
        controlPressed = Color(0xFF434C5A),
        controlDisabled = Color(0xFF23272E),
        controlOutline = Color(0xFF454E5C),

        textPrimary = Color(0xFFF2F5F9),
        textSecondary = Color(0xFFA6B1BF),
        textDisabled = Color(0xFF6C7684),
    )

    // -- Accent ------------------------------------------------------------
    //
    // One accent, used for exactly three things: the filled primary action, the
    // selected state, and focus. A palette that accents more than that has no
    // accent. It is deep enough that white text on it clears the contrast bar
    // it needs to at button sizes, which the old #3B82F6 did not quite.

    // Hover and pressed go *down* from the base rather than up. Lightening an
    // accent to show hover walks it towards the white text sitting on it - the
    // first attempt at this put hover at 4.06:1, below the readable bar, so the
    // one state a person sees while their finger is on the button was the least
    // legible of the three. Darkening reads as pressed-ness just as well and
    // improves contrast instead of spending it.
    val Accent = Color(0xFF2F6BE0)
    val AccentHover = Color(0xFF2A61CE)
    val AccentPressed = Color(0xFF23539F)
    val AccentMuted = Color(0xFFD9E5FB)
    val AccentMutedDark = Color(0xFF1B2E4E)
    val OnAccent = Color(0xFFFFFFFF)

    // Kept as names because the rest of the codebase, the templates and the
    // saved documents all refer to them. They are the light set's values.
    val Surface = Light.surface
    val SurfaceRaised = Light.surfaceRaised
    val SurfaceSunken = Light.surfaceSunken
    val Divider = Light.divider
    val ControlFill = Light.control
    val ControlHover = Light.controlHover
    val ControlPressed = Light.controlPressed
    val ControlDisabled = Light.controlDisabled
    val ControlOutline = Light.controlOutline
    val TextPrimary = Light.textPrimary
    val TextSecondary = Light.textSecondary
    val TextDisabled = Light.textDisabled

    /**
     * No text shadow.
     *
     * Both Minecraft skins drop-shadow their text because the game does. Flat
     * interfaces do not, and copying it here would be the single clearest
     * "this was made by a Minecraft tool" tell on an otherwise neutral mock-up.
     */
    val TextShadow = Color(0x00000000)

    // -- Editor chrome -----------------------------------------------------
    //
    // The application around the canvas, rather than anything drawn on it. Near
    // black with a slight blue cast, which is where every tool that expects to
    // be looked at for hours has ended up: it stops competing with the artwork
    // without going to the flat #000 that makes panel edges disappear.

    val ChromeBackground = Color(0xFF0F1115)
    val ChromePanel = Color(0xFF171A20)
    val ChromePanelAlt = Color(0xFF20242C)
    val ChromeBorder = Color(0xFF2D323B)
    val ChromeText = Color(0xFFEDF0F4)
    val ChromeTextMuted = Color(0xFF98A3B2)
    val Selection = Accent
    val SelectionFill = Color(0x2E2F6BE0)
    val GuideLine = Color(0xFFF472B6)
    val GridLine = Color(0x1FFFFFFF)
    val GridLineMajor = Color(0x3DFFFFFF)

    val LightChrome = ChromeColors(
        background = Color(0xFFE7EBF1),
        panel = Color(0xFFFFFFFF),
        panelAlt = Color(0xFFDDE3EB),
        border = Color(0xFFBFC8D4),
        text = Light.textPrimary,
        textMuted = Light.textSecondary,
        gridLine = Color(0x1A0B1220),
        gridLineMajor = Color(0x380B1220),
        backdropScrim = Color(0xC7EDF0F4),
    )

    val DarkChrome = ChromeColors(
        background = ChromeBackground,
        panel = ChromePanel,
        panelAlt = ChromePanelAlt,
        border = ChromeBorder,
        text = ChromeText,
        textMuted = ChromeTextMuted,
        gridLine = GridLine,
        gridLineMajor = GridLineMajor,
        backdropScrim = Color(0xB00F1115),
    )

    val palette = SkinPalette(
        surface = Light.surface,
        surfaceRaised = Light.surfaceRaised,
        surfaceSunken = Light.surfaceSunken,
        slot = Light.surfaceSunken,
        slotShadow = Light.divider,
        slotHighlight = Color(0xFFFFFFFF),
        bevelLight = Color(0xFFFFFFFF),
        bevelDark = Light.divider,
        outline = Light.controlOutline,
        control = Light.control,
        controlHover = Light.controlHover,
        controlPressed = Light.controlPressed,
        controlDisabled = Light.controlDisabled,
        controlFocusRing = Accent,
        accent = Accent,
        accentMuted = AccentMuted,
        textPrimary = Light.textPrimary,
        textSecondary = Light.textSecondary,
        textDisabled = Light.textDisabled,
        textShadow = TextShadow,
        textOnAccent = OnAccent,
        // The filled primary action. Both Minecraft editions leave these at
        // their control colours; this one cannot, because its controls are a
        // pale neutral and white text on a pale neutral is not text.
        ctaFill = Accent,
        ctaFillHover = AccentHover,
        ctaFillPressed = AccentPressed,
        ctaText = OnAccent,
        chromeBackground = ChromeBackground,
        chromePanel = ChromePanel,
        chromePanelAlt = ChromePanelAlt,
        chromeBorder = ChromeBorder,
        chromeText = ChromeText,
        chromeTextMuted = ChromeTextMuted,
        selection = Selection,
        selectionFill = SelectionFill,
        guideLine = GuideLine,
        gridLine = GridLine,
        gridLineMajor = GridLineMajor,
        backdropScrim = DarkChrome.backdropScrim,
        // Rounded and 1px-bordered, against Java's hard 0-radius bevels and
        // Bedrock's 2px light-over-dark frame. The corner radius is the single
        // strongest signal that this is not a game widget.
        borderWidth = 1,
        cornerRadius = 8,
        chromeCorner = 12.dp,
        // 40 rather than Java's 20: a control sized for a fingertip and a
        // pointer, not for a 16px game canvas.
        controlHeight = 40,
    )
}
