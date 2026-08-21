package com.mcguidesigner.styles.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.mcguidesigner.core.model.Edition
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Supplies the wallpaper drawn behind the editor.
 *
 * The artwork is a real image file that each shell loads its own way - from the
 * classpath on desktop, from `assets/` on Android - so this is an interface
 * rather than a shared loader.  Returning null is always allowed and always
 * safe: [DesignerBackdrop] paints a procedural scene instead, which means a
 * missing or unreadable asset costs some polish and nothing else.
 */
fun interface BackdropArtwork {
    fun imageFor(edition: Edition, dark: Boolean): ImageBitmap?

    companion object {
        val None = BackdropArtwork { _, _ -> null }
    }
}

val LocalBackdropArtwork = staticCompositionLocalOf { BackdropArtwork.None }

/** Whether the backdrop drifts. Off respects "reduce motion" and saves battery. */
val LocalBackdropMotion = staticCompositionLocalOf { true }

/**
 * The wallpaper behind the whole editor.
 *
 * Three layers, back to front: a sky wash in the edition's own colours, the
 * artwork itself scaled to cover and drifting slowly, and the palette's scrim
 * on top.  The scrim is what keeps this decoration rather than interference -
 * every dock and panel above it is opaque, and the artwork only ever shows
 * through the gaps.
 *
 * Drift is deliberately slower than anything the eye tracks (a full cycle takes
 * a little under a minute) so it reads as depth rather than as movement while
 * someone is trying to place a 2-pixel border.
 */
@Composable
fun DesignerBackdrop(
    edition: Edition,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = LocalSkinPalette.current
    val dark = LocalDarkChrome.current
    val artwork = LocalBackdropArtwork.current
    val motion = LocalBackdropMotion.current
    val image = artwork.imageFor(edition, dark)

    // The transition is created unconditionally - Compose requires a stable
    // call structure - and simply ignored when motion is switched off.
    val drift by rememberInfiniteTransition(label = "backdrop").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(BACKDROP_DRIFT_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "backdropPhase",
    )
    val phase = if (motion) drift else 0f

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawSky(edition, dark, palette)
            if (image != null) {
                drawArtwork(image, phase)
            } else {
                drawProceduralScene(edition, dark, palette, phase)
            }
            drawRect(palette.backdropScrim, size = size)
        }
        content()
    }
}

/** One full drift cycle. Long enough that the motion is felt, not watched. */
private const val BACKDROP_DRIFT_MILLIS = 56_000

/** Flat wash under everything, so an image with the wrong aspect never gaps. */
private fun DrawScope.drawSky(edition: Edition, dark: Boolean, palette: SkinPalette) {
    val top = when {
        !dark -> palette.chromePanel
        edition == Edition.JAVA -> Color(0xFF1A2733)
        else -> Color(0xFF141026)
    }
    val bottom = palette.chromeBackground
    drawRect(Brush.verticalGradient(listOf(top, bottom)), size = size)
}

/**
 * Draws the artwork scaled to cover, panned by a few percent.
 *
 * Cover rather than fit: a letterboxed wallpaper with the app's background
 * showing above and below looks like a mistake, and the scrim means nothing
 * important is lost to the crop.
 */
private fun DrawScope.drawArtwork(image: ImageBitmap, phase: Float) {
    if (image.width <= 0 || image.height <= 0) return
    // The extra 6% is the room the drift pans within.
    val cover = maxOf(size.width / image.width, size.height / image.height) * 1.06f
    val drawnWidth = image.width * cover
    val drawnHeight = image.height * cover
    val slackX = (drawnWidth - size.width).coerceAtLeast(0f)
    val slackY = (drawnHeight - size.height).coerceAtLeast(0f)

    // A sine both ways so the pan eases at each end instead of snapping back
    // when the linear phase wraps.
    val t = sin(phase * 2f * PI.toFloat())
    val left = -slackX / 2f + t * slackX / 2f
    val top = -slackY / 2f + sin(phase * 2f * PI.toFloat() + 1.2f) * slackY / 2f

    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(drawnWidth.roundToInt(), drawnHeight.roundToInt()),
    )
}

/**
 * The fallback scene, drawn when no artwork is available.
 *
 * A layered blocky horizon: distant ridges, a nearer ridge, a low sun and a
 * scatter of drifting motes.  It is built from the same shapes the generated
 * artwork uses, so a build without the asset looks intentional rather than
 * broken.
 */
private fun DrawScope.drawProceduralScene(
    edition: Edition,
    dark: Boolean,
    palette: SkinPalette,
    phase: Float,
) {
    val horizon = size.height * 0.66f
    val accent = palette.accent

    // Low sun / moon, drifting a little across the sky.
    val glowX = size.width * (0.22f + 0.10f * sin(phase * 2f * PI.toFloat()))
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = if (dark) 0.22f else 0.14f), Color.Transparent),
            center = Offset(glowX, horizon * 0.55f),
            radius = size.minDimension * 0.55f,
        ),
        radius = size.minDimension * 0.55f,
        center = Offset(glowX, horizon * 0.55f),
    )

    // Three ridges, each darker and blockier than the one behind it.
    val ridges = listOf(
        Triple(0.86f, 46f, if (dark) 0.30f else 0.18f),
        Triple(0.94f, 30f, if (dark) 0.46f else 0.26f),
        Triple(1.02f, 18f, if (dark) 0.62f else 0.34f),
    )
    ridges.forEachIndexed { index, (heightFactor, step, alpha) ->
        val base = horizon * heightFactor
        // Each ridge slides at its own rate: the classic parallax cue.
        val drift = phase * size.width * (0.05f + index * 0.035f)
        drawRidge(
            baseline = base,
            step = step,
            seed = index * 37 + 11,
            drift = drift,
            color = ridgeColor(edition, dark).copy(alpha = alpha),
        )
    }

    // Motes: slow, sparse, and never in the same place twice round the cycle.
    val moteColor = accent.copy(alpha = if (dark) 0.35f else 0.22f)
    repeat(28) { index ->
        val seed = index * 0.618f
        val x = ((seed + phase * (0.3f + (index % 5) * 0.08f)) % 1f) * size.width
        val y = (abs(sin(seed * 12.9898f)) * 0.9f) * horizon
        val radius = 1.2f + (index % 3)
        drawCircle(moteColor, radius = radius, center = Offset(x, y))
    }
}

private fun ridgeColor(edition: Edition, dark: Boolean): Color = when {
    !dark -> Color(0xFF8A96A2)
    edition == Edition.JAVA -> Color(0xFF0B1418)
    else -> Color(0xFF0A0716)
}

/**
 * One blocky ridge line.
 *
 * Heights come from a cheap deterministic hash rather than a random source, so
 * the silhouette is identical on every frame and every launch - a horizon that
 * reshuffled itself during a repaint would be the most distracting thing on
 * screen.
 */
private fun DrawScope.drawRidge(
    baseline: Float,
    step: Float,
    seed: Int,
    drift: Float,
    color: Color,
) {
    val columns = (size.width / step).toInt() + 4
    translate(left = -(drift % (step * 2f)) - step) {
        for (i in 0..columns) {
            val h = hash(i + seed)
            val top = baseline - (h * step * 2.4f)
            drawRect(
                color = color,
                topLeft = Offset(i * step, top),
                size = Size(step + 1f, size.height - top),
            )
        }
    }
}

/** Deterministic 0..1 from an integer. Stable across runs and platforms. */
private fun hash(value: Int): Float {
    val x = sin(value * 127.1f) * 43758.5453f
    return abs(x - x.toInt())
}
