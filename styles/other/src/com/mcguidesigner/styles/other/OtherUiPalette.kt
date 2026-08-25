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
 * one has nothing to imitate, so it is a design rather than a recording: a
 * neutral, flat, contemporary app surface of the kind every platform has
 * converged on, chosen so a screen mocked up here reads as itself rather than
 * as an impression of somebody's design system.
 *
 * Deliberately restrained. A mock-up's job is to show the *layout*; a palette
 * with opinions of its own competes with the thing being designed.
 *
 * This file belongs to `styles/other` and must never be referenced from
 * `styles/java` or `styles/bedrock`.
 */
object OtherUiPalette {

    // Surfaces: a light card on a slightly darker page, the arrangement almost
    // every app interface uses.
    val Surface = Color(0xFFFFFFFF)
    val SurfaceRaised = Color(0xFFF7F8FA)
    val SurfaceSunken = Color(0xFFEDEFF3)
    val Divider = Color(0xFFDDE1E6)
    val Shadow = Color(0x14000000)

    // Controls: a filled primary against neutral fills, at the tap-target size
    // both mobile platforms settle on.
    val ControlFill = Color(0xFFF1F3F6)
    val ControlHover = Color(0xFFE7EAEF)
    val ControlPressed = Color(0xFFDCE0E6)
    val ControlDisabled = Color(0xFFF3F4F6)
    val ControlOutline = Color(0xFFCBD2DA)

    val Accent = Color(0xFF3B82F6)
    val AccentMuted = Color(0xFFDBEAFE)

    val TextPrimary = Color(0xFF11181C)
    val TextSecondary = Color(0xFF5B6570)
    val TextDisabled = Color(0xFF9BA4AE)

    /**
     * No text shadow.
     *
     * Both Minecraft skins drop-shadow their text because the game does. Flat
     * interfaces do not, and copying it here would be the single clearest
     * "this was made by a Minecraft tool" tell on an otherwise neutral mock-up.
     */
    val TextShadow = Color(0x00000000)

    // Editor chrome: a cool near-black with the same blue accent, so switching
    // into this mode is as obvious as switching into either of the others.
    val ChromeBackground = Color(0xFF15171B)
    val ChromePanel = Color(0xFF1E2126)
    val ChromePanelAlt = Color(0xFF272B31)
    val ChromeBorder = Color(0xFF343941)
    val ChromeText = Color(0xFFE6E9ED)
    val ChromeTextMuted = Color(0xFF98A1AC)
    val Selection = Color(0xFF3B82F6)
    val SelectionFill = Color(0x223B82F6)
    val GuideLine = Color(0xFFF472B6)
    val GridLine = Color(0x1AFFFFFF)
    val GridLineMajor = Color(0x33FFFFFF)

    val LightChrome = ChromeColors(
        background = Color(0xFFEEF0F3),
        panel = Color(0xFFFFFFFF),
        panelAlt = Color(0xFFE6E9ED),
        border = Color(0xFFCCD2D9),
        text = Color(0xFF11181C),
        textMuted = Color(0xFF5B6570),
        gridLine = Color(0x14000000),
        gridLineMajor = Color(0x2E000000),
        backdropScrim = Color(0xC2F1F3F5),
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
        backdropScrim = Color(0xAA15171B),
    )

    val palette = SkinPalette(
        surface = Surface,
        surfaceRaised = SurfaceRaised,
        surfaceSunken = SurfaceSunken,
        slot = SurfaceSunken,
        slotShadow = Divider,
        slotHighlight = Color(0xFFFFFFFF),
        bevelLight = Color(0xFFFFFFFF),
        bevelDark = Divider,
        outline = ControlOutline,
        control = ControlFill,
        controlHover = ControlHover,
        controlPressed = ControlPressed,
        controlDisabled = ControlDisabled,
        controlFocusRing = Accent,
        accent = Accent,
        accentMuted = AccentMuted,
        textPrimary = TextPrimary,
        textSecondary = TextSecondary,
        textDisabled = TextDisabled,
        textShadow = TextShadow,
        textOnAccent = Color(0xFFFFFFFF),
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
        cornerRadius = 6,
        chromeCorner = 8.dp,
        // 36 rather than Java's 20: a control sized for a fingertip and a
        // pointer, not for a 16px game canvas.
        controlHeight = 36,
    )
}
