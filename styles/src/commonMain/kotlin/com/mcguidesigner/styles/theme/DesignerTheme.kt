package com.mcguidesigner.styles.theme

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.styles.bedrock.BedrockEditionSkin
import com.mcguidesigner.styles.java.JavaEditionSkin
import com.mcguidesigner.styles.other.OtherUiSkin
import com.mcguidesigner.styles.render.EditionSkin

/** Look-up from edition to its skin. The only place the two styles meet. */
object SkinRegistry {
    val all: List<EditionSkin> = listOf(JavaEditionSkin, BedrockEditionSkin, OtherUiSkin)

    fun forEdition(edition: Edition): EditionSkin = when (edition) {
        Edition.JAVA -> JavaEditionSkin
        Edition.BEDROCK -> BedrockEditionSkin
        Edition.OTHER -> OtherUiSkin
    }
}

val LocalEditionSkin = staticCompositionLocalOf<EditionSkin> { JavaEditionSkin }

val LocalSkinPalette = staticCompositionLocalOf { JavaEditionSkin.palette }

/**
 * Whether the current UI is running in touch-first mode.
 *
 * The desktop app pins this to false and Android pins it to true; shared
 * components use it to pick hit-target sizes rather than branching on
 * platform, which keeps them testable on either host.
 */
val LocalTouchMode = staticCompositionLocalOf { false }

/** True when the chrome is currently painted dark. Read by decorative layers. */
val LocalDarkChrome = staticCompositionLocalOf { true }

/**
 * The chrome recolouring in force.
 *
 * Provided as well as applied because the home screen paints its own chrome
 * outside [DesignerTheme] - it has no edition yet, so it cannot ask a skin for
 * a palette - and still has to agree with the editor about which theme is on.
 */
val LocalChromeTheme = staticCompositionLocalOf { ChromeTheme.DEFAULT }

/** How long the chrome takes to cross-fade when the edition or theme changes. */
const val CHROME_TRANSITION_MILLIS = 420

/**
 * Application theme.
 *
 * The whole editor chrome - not just the canvas - is tinted by the active
 * edition's palette, so switching between Java and Bedrock modes is
 * immediately obvious rather than a subtle canvas-only change.  The chrome
 * colours are *animated* between the two, because the switch is a deliberate
 * mode change and a hard cut makes the app feel like it reloaded.
 *
 * Widget colours never animate and never follow the light/dark setting: the
 * canvas has to keep showing what the game will draw.
 */
@Composable
fun DesignerTheme(
    edition: Edition,
    touchMode: Boolean = false,
    dark: Boolean = true,
    chromeTheme: ChromeTheme = ChromeTheme.DEFAULT,
    motion: MotionLevel = MotionLevel.FULL,
    content: @Composable () -> Unit,
) {
    val skin = SkinRegistry.forEdition(edition)
    val target = skin.paletteFor(dark, chromeTheme)
    val palette = if (motion.animates) rememberAnimatedChrome(target, motion) else target

    val colorScheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.textOnAccent,
            primaryContainer = palette.accentMuted,
            onPrimaryContainer = palette.chromeText,
            secondary = palette.chromeTextMuted,
            onSecondary = palette.chromeBackground,
            background = palette.chromeBackground,
            onBackground = palette.chromeText,
            surface = palette.chromePanel,
            onSurface = palette.chromeText,
            surfaceVariant = palette.chromePanelAlt,
            onSurfaceVariant = palette.chromeTextMuted,
            outline = palette.chromeBorder,
            outlineVariant = palette.chromeBorder,
            error = ErrorRed,
            onError = palette.chromeText,
        )
    } else {
        // The accent is a vivid Minecraft green tuned for a dark background; on
        // paper it needs darkening or every button label washes out.
        val accentOnLight = palette.accentMuted
        lightColorScheme(
            primary = accentOnLight,
            onPrimary = Color.White,
            primaryContainer = palette.accent.copy(alpha = 0.24f),
            onPrimaryContainer = palette.chromeText,
            secondary = palette.chromeTextMuted,
            onSecondary = Color.White,
            background = palette.chromeBackground,
            onBackground = palette.chromeText,
            surface = palette.chromePanel,
            onSurface = palette.chromeText,
            surfaceVariant = palette.chromePanelAlt,
            onSurfaceVariant = palette.chromeTextMuted,
            outline = palette.chromeBorder,
            outlineVariant = palette.chromeBorder,
            error = ErrorRed,
            onError = Color.White,
        )
    }

    val baseScale = if (touchMode) 1.08f else 1f
    val typography = Typography(
        titleMedium = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = (15 * baseScale).sp,
        ),
        titleSmall = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = (13 * baseScale).sp,
        ),
        bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = (13 * baseScale).sp),
        bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = (12 * baseScale).sp),
        labelLarge = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = (13 * baseScale).sp,
        ),
        labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = (12 * baseScale).sp),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = (11 * baseScale).sp,
        ),
    )

    CompositionLocalProvider(
        LocalEditionSkin provides skin,
        LocalSkinPalette provides palette,
        LocalTouchMode provides touchMode,
        LocalDarkChrome provides dark,
        LocalChromeTheme provides chromeTheme,
        LocalMotion provides motion,
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
}

/**
 * Eases the chrome colours towards [target].
 *
 * Only the tokens that actually differ between editions and themes are
 * animated; animating the widget ramp as well would show the canvas passing
 * through colours the game never uses.
 */
@Composable
private fun rememberAnimatedChrome(target: SkinPalette, motion: MotionLevel): SkinPalette {
    val spec = tween<Color>(
        durationMillis = motion.duration(CHROME_TRANSITION_MILLIS),
        easing = LinearOutSlowInEasing,
    )

    val background by animateColorAsState(target.chromeBackground, spec, label = "chromeBackground")
    val panel by animateColorAsState(target.chromePanel, spec, label = "chromePanel")
    val panelAlt by animateColorAsState(target.chromePanelAlt, spec, label = "chromePanelAlt")
    val border by animateColorAsState(target.chromeBorder, spec, label = "chromeBorder")
    val text by animateColorAsState(target.chromeText, spec, label = "chromeText")
    val textMuted by animateColorAsState(target.chromeTextMuted, spec, label = "chromeTextMuted")
    val accent by animateColorAsState(target.accent, spec, label = "accent")
    val accentMuted by animateColorAsState(target.accentMuted, spec, label = "accentMuted")
    val selection by animateColorAsState(target.selection, spec, label = "selection")
    val selectionFill by animateColorAsState(target.selectionFill, spec, label = "selectionFill")
    val scrim by animateColorAsState(target.backdropScrim, spec, label = "backdropScrim")

    return target.copy(
        chromeBackground = background,
        chromePanel = panel,
        chromePanelAlt = panelAlt,
        chromeBorder = border,
        chromeText = text,
        chromeTextMuted = textMuted,
        accent = accent,
        accentMuted = accentMuted,
        selection = selection,
        selectionFill = selectionFill,
        backdropScrim = scrim,
    )
}

/** Severity colours shared by both editions (validation is not cosmetic). */
val ErrorRed = Color(0xFFE5534B)
val WarningAmber = Color(0xFFE3B341)
val InfoBlue = Color(0xFF58A6FF)
