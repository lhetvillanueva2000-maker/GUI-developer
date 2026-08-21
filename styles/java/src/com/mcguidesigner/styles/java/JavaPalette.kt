package com.mcguidesigner.styles.java

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mcguidesigner.styles.theme.ChromeColors
import com.mcguidesigner.styles.theme.SkinPalette

/**
 * Java Edition colour tokens.
 *
 * These are the actual vanilla values, read off the shipped `widgets.png`,
 * `generic_54.png` and the hard-coded constants in the Java client: the light
 * grey container body (#C6C6C6), the darker slot fill (#8B8B8B), the #373737
 * slot shadow, and the classic #404040 container title.
 *
 * This file belongs to `styles/java` and must never be referenced from
 * `styles/bedrock`.
 */
object JavaPalette {

    // Vanilla container chrome.
    val ContainerBody = Color(0xFFC6C6C6)
    val ContainerShadow = Color(0xFF555555)
    val ContainerHighlight = Color(0xFFFFFFFF)
    val SlotFill = Color(0xFF8B8B8B)
    val SlotShadow = Color(0xFF373737)
    val SlotHighlight = Color(0xFFFFFFFF)
    val TitleText = Color(0xFF404040)

    // Vanilla widgets.png button ramp.
    val ButtonFill = Color(0xFF6C6C6C)
    val ButtonFillHover = Color(0xFF7F7F7F)
    val ButtonFillPressed = Color(0xFF565656)
    val ButtonFillDisabled = Color(0xFF3E3E3E)
    val ButtonTop = Color(0xFFA0A0A0)
    val ButtonBottom = Color(0xFF4A4A4A)
    val ButtonOutline = Color(0xFF000000)
    val FocusRing = Color(0xFFFFFFFF)

    // Tooltip frame (the purple gradient border).
    val TooltipBackground = Color(0xF0100010)
    val TooltipBorderTop = Color(0xFF5000FF)
    val TooltipBorderBottom = Color(0xFF28007F)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA0A0A0)
    val TextDisabled = Color(0xFF6E6E6E)
    val TextShadow = Color(0xFF3F3F3F)
    val Accent = Color(0xFF54FB54)
    val AccentMuted = Color(0xFF2F7A2F)

    /**
     * Editor chrome for Java mode: cool, desaturated stone greys with an
     * emerald accent - it reads as "Java" the moment the mode is switched.
     */
    val ChromeBackground = Color(0xFF16181A)
    val ChromePanel = Color(0xFF1F2225)
    val ChromePanelAlt = Color(0xFF272B2F)
    val ChromeBorder = Color(0xFF34393E)
    val ChromeText = Color(0xFFE4E7EA)
    val ChromeTextMuted = Color(0xFF9AA2AA)
    val Selection = Color(0xFF54FB54)
    val SelectionFill = Color(0x2254FB54)
    val GuideLine = Color(0xFF4FC3F7)
    val GridLine = Color(0x1AFFFFFF)
    val GridLineMajor = Color(0x33FFFFFF)

    /**
     * The same Java identity in a light editor: paper-white stone with the
     * emerald accent darkened enough to stay legible on it.
     *
     * Only the chrome flips.  Every widget colour above is a vanilla value and
     * stays exactly as it is, because the canvas has to keep showing what the
     * game will actually draw.
     */
    val LightChrome = ChromeColors(
        background = Color(0xFFEDEFF1),
        panel = Color(0xFFF7F8F9),
        panelAlt = Color(0xFFE3E6E9),
        border = Color(0xFFC7CDD2),
        text = Color(0xFF1B1F23),
        textMuted = Color(0xFF5B646D),
        gridLine = Color(0x1A000000),
        gridLineMajor = Color(0x33000000),
        backdropScrim = Color(0xC2F2F4F6),
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
        backdropScrim = Color(0xAA16181A),
    )

    val palette = SkinPalette(
        surface = ContainerBody,
        surfaceRaised = ButtonFill,
        surfaceSunken = SlotFill,
        slot = SlotFill,
        slotShadow = SlotShadow,
        slotHighlight = SlotHighlight,
        bevelLight = ContainerHighlight,
        bevelDark = ContainerShadow,
        outline = ButtonOutline,
        control = ButtonFill,
        controlHover = ButtonFillHover,
        controlPressed = ButtonFillPressed,
        controlDisabled = ButtonFillDisabled,
        controlFocusRing = FocusRing,
        accent = Accent,
        accentMuted = AccentMuted,
        textPrimary = TextPrimary,
        textSecondary = TextSecondary,
        textDisabled = TextDisabled,
        textShadow = TextShadow,
        textOnAccent = Color(0xFF0B1A0B),
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
        borderWidth = 1,
        cornerRadius = 0,
        chromeCorner = 3.dp,
        controlHeight = 20,
    )
}
