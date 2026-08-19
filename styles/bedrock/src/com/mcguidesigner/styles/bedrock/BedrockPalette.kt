package com.mcguidesigner.styles.bedrock

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mcguidesigner.styles.theme.SkinPalette

/**
 * Bedrock Edition colour tokens.
 *
 * Bedrock's UI is a different design language from Java's: chunkier borders,
 * softened corners, translucent "glass" sheets over the world, and a much
 * higher-contrast accent so controls stay findable on a phone in daylight.
 * The palette leans on the greens and slate greys of the modern Pocket/UWP
 * skin rather than Java's flat stone greys.
 *
 * This file belongs to `styles/bedrock` and must never be referenced from
 * `styles/java`.
 */
object BedrockPalette {

    // Panels and sheets.
    val SheetBackground = Color(0xF01B1B1F)
    val SheetRaised = Color(0xFF2A2A31)
    val SheetSunken = Color(0xFF121216)
    val GlassOverlay = Color(0x66000000)
    val SlotFill = Color(0xFF3A3A42)
    val SlotShadow = Color(0xFF16161A)
    val SlotHighlight = Color(0xFF5C5C68)

    // Chunky 2px borders instead of Java's 1px bevel.
    val BorderLight = Color(0xFF6E6E7C)
    val BorderDark = Color(0xFF0E0E12)
    val Outline = Color(0xFF3D3D45)

    // Touch controls.
    val ControlFill = Color(0xFF3F8F3F)
    val ControlHover = Color(0xFF4EA84E)
    val ControlPressed = Color(0xFF2E6B2E)
    val ControlDisabled = Color(0xFF3A3A42)
    val FocusRing = Color(0xFF6BE86B)
    val TouchPad = Color(0x80FFFFFF)
    val TouchKnob = Color(0xFFEDEDED)

    val Accent = Color(0xFF5CE05C)
    val AccentMuted = Color(0xFF2C6B2C)

    val TextPrimary = Color(0xFFF2F2F5)
    val TextSecondary = Color(0xFFA8A8B4)
    val TextDisabled = Color(0xFF6A6A76)
    val TextShadow = Color(0x99000000)

    /**
     * Editor chrome for Bedrock mode: warmer near-black with a vivid green
     * accent, so switching editions visibly changes the whole application, not
     * just the canvas.
     */
    val ChromeBackground = Color(0xFF101014)
    val ChromePanel = Color(0xFF1A1A20)
    val ChromePanelAlt = Color(0xFF23232B)
    val ChromeBorder = Color(0xFF32323C)
    val ChromeText = Color(0xFFECECF2)
    val ChromeTextMuted = Color(0xFF9494A2)
    val Selection = Color(0xFF5CE05C)
    val SelectionFill = Color(0x265CE05C)
    val GuideLine = Color(0xFFFF9F45)
    val GridLine = Color(0x14FFFFFF)
    val GridLineMajor = Color(0x2EFFFFFF)

    val palette = SkinPalette(
        surface = SheetBackground,
        surfaceRaised = SheetRaised,
        surfaceSunken = SheetSunken,
        slot = SlotFill,
        slotShadow = SlotShadow,
        slotHighlight = SlotHighlight,
        bevelLight = BorderLight,
        bevelDark = BorderDark,
        outline = Outline,
        control = ControlFill,
        controlHover = ControlHover,
        controlPressed = ControlPressed,
        controlDisabled = ControlDisabled,
        controlFocusRing = FocusRing,
        accent = Accent,
        accentMuted = AccentMuted,
        textPrimary = TextPrimary,
        textSecondary = TextSecondary,
        textDisabled = TextDisabled,
        textShadow = TextShadow,
        textOnAccent = Color(0xFF061806),
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
        borderWidth = 2,
        cornerRadius = 3,
        chromeCorner = 12.dp,
        controlHeight = 28,
    )
}
