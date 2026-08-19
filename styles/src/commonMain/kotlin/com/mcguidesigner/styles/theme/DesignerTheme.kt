package com.mcguidesigner.styles.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.styles.bedrock.BedrockEditionSkin
import com.mcguidesigner.styles.java.JavaEditionSkin
import com.mcguidesigner.styles.render.EditionSkin

/** Look-up from edition to its skin. The only place the two styles meet. */
object SkinRegistry {
    val all: List<EditionSkin> = listOf(JavaEditionSkin, BedrockEditionSkin)

    fun forEdition(edition: Edition): EditionSkin = when (edition) {
        Edition.JAVA -> JavaEditionSkin
        Edition.BEDROCK -> BedrockEditionSkin
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

/**
 * Application theme.
 *
 * The whole editor chrome - not just the canvas - is tinted by the active
 * edition's palette, so switching between Java and Bedrock modes is
 * immediately obvious rather than a subtle canvas-only change.
 */
@Composable
fun DesignerTheme(
    edition: Edition,
    touchMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val skin = SkinRegistry.forEdition(edition)
    val palette = skin.palette

    val colorScheme = darkColorScheme(
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
    ) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
}

/** Severity colours shared by both editions (validation is not cosmetic). */
val ErrorRed = androidx.compose.ui.graphics.Color(0xFFE5534B)
val WarningAmber = androidx.compose.ui.graphics.Color(0xFFE3B341)
val InfoBlue = androidx.compose.ui.graphics.Color(0xFF58A6FF)
