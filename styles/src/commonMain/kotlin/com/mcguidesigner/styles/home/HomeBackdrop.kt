package com.mcguidesigner.styles.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import com.mcguidesigner.styles.render.EditionSkin
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The pixel night scene, drawn rather than loaded.
 *
 * The app ships this as four bitmaps - backdrop-{java,bedrock}-{dark,light}.png
 * - which is the right call for the editor canvas, where it sits behind a
 * document and must not change. Home is different: the whole point is that the
 * scene retints as you move between the two cards, and cross-fading two 2560x1440
 * PNGs to do that is a lot of bandwidth for a hue shift. So the same
 * composition - stars, a glowing square sun, five blocky ridges - is rebuilt
 * from the skin's own colours.
 *
 * The *shape* of the scene is generated once and kept ([rememberSceneGeometry]);
 * only the colours are recomputed when the edition or theme changes. Both were
 * regenerated together before, which meant every hover - the thing this screen
 * does constantly - rolled 260 stars and about 570 ridge segments again to
 * arrive at exactly the same silhouette in a different colour.
 *
 * Everything is seeded, so the sky does not reshuffle when the theme flips.
 */
@Composable
fun HomeBackdrop(
    skin: EditionSkin,
    dark: Boolean,
    scrim: Color,
    modifier: Modifier = Modifier,
) {
    val palette = skin.paletteFor(dark)
    val sky = if (dark) skin.darkChrome.background else skin.lightChrome.background
    val hill = lerp(sky, palette.accentMuted, if (dark) 0.22f else 0.34f)
    val deep = lerp(hill, Color.Black, if (dark) 0.55f else 0.30f)
    val sun = palette.accent

    val scene = rememberSceneGeometry()

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val px = 14f

        drawRect(sky)

        // Portrait needs the horizon pushed down, or the ridgeline lands in the
        // middle of the type.
        val hz = if (w / h > 1.2f) 0.60f else 0.72f
        val sx = w * 0.70f
        val sy = h * (hz - 0.035f)

        // Glow first, then the sun, then the ridges over the top of both: the
        // occlusion is what makes it read as a horizon instead of a floating box.
        drawRect(
            brush = Brush.radialGradient(
                0f to sun.copy(alpha = if (dark) 0.50f else 0.28f),
                0.45f to sun.copy(alpha = if (dark) 0.13f else 0.09f),
                1f to sun.copy(alpha = 0f),
                center = Offset(sx, sy),
                radius = max(w, h) * 0.40f,
            )
        )

        if (dark) {
            val band = h * (hz - 0.04f)
            scene.stars.forEach { star ->
                val x = (star.fx * w / px).roundToInt() * px
                val y = (star.fy * band / px).roundToInt() * px
                drawRect(
                    if (star.white) Color.White.copy(alpha = star.alpha) else STAR_GREY.copy(alpha = star.alpha),
                    Offset(x, y),
                    Size(2f, 2f),
                )
            }
        }

        drawRect(sun, Offset((sx / px).roundToInt() * px, (sy / px).roundToInt() * px), Size(px * 3, px * 3))

        // Five ridges marching down, each a solid step darker than the one
        // behind it. Solid, not stacked alpha: translucent ridges over a dark
        // sky turn the lower third into mud.
        scene.ridges.forEachIndexed { band, offsets ->
            val base = h * (hz + band * 0.075f)
            val amp = 34f - band * 6f
            val path = Path().apply {
                moveTo(0f, h)
                var x = 0f
                var i = 0
                while (x <= w + px) {
                    val y = base + offsets[i % offsets.size] * amp
                    val q = (y / px).roundToInt() * px
                    lineTo(x, q); lineTo(x + px, q)
                    x += px
                    i++
                }
                lineTo(w, h)
                close()
            }
            drawPath(path, lerp(hill, deep, band / 4f))
        }

        // Darkest where the words are, clearing towards the edges so the sky
        // and the ridgeline still read. A flat scrim flattens it all to mud.
        drawRect(
            brush = Brush.radialGradient(
                0f to scrim,
                0.38f to scrim,
                1f to scrim.copy(alpha = 0f),
                center = Offset(w * 0.5f, h * 0.52f),
                radius = max(w, h) * 0.75f,
            )
        )
    }
}

private val STAR_GREY = Color(0xFF8A96A2)

/** One star, in fractions of the sky band rather than pixels. */
private class Star(val fx: Float, val fy: Float, val alpha: Float, val white: Boolean)

/**
 * The scene's shape, independent of its size and its colours.
 *
 * Held in normalised coordinates so one generation serves every window size:
 * a resize re-maps the same silhouette rather than rolling a new one, which is
 * also what stops the hills from reshuffling while a window is being dragged.
 */
private class SceneGeometry(val stars: List<Star>, val ridges: List<FloatArray>)

/**
 * How many ridge steps are generated per band.
 *
 * The draw walks one step per 14px, so 256 covers a window about 3,500px wide
 * before the silhouette starts repeating - past any real display, and cheap
 * enough that raising it further would be paying for nothing.
 */
private const val RIDGE_SAMPLES = 256

@Composable
private fun rememberSceneGeometry(): SceneGeometry = remember {
    // A tiny LCG rather than kotlin.random, so the sky is byte-identical on
    // every platform and every run. A backdrop that differed between Android
    // and desktop would make any screenshot comparison useless.
    fun generator(seed: Long): () -> Float {
        var state = seed
        return {
            state = (state * 1664525L + 1013904223L) and 0xFFFFFFFFL
            state.toFloat() / 4294967296f
        }
    }

    val starRnd = generator(20260823L)
    val stars = List(260) {
        Star(
            fx = starRnd(),
            fy = starRnd(),
            alpha = 0.30f + starRnd() * 0.70f,
            white = starRnd() > 0.86f,
        )
    }

    val ridges = List(5) { band ->
        val rnd = generator(9001L + band * 137L)
        var offset = 0f
        FloatArray(RIDGE_SAMPLES) {
            // Most steps hold the previous height - that is what makes it a
            // ridgeline rather than noise - and roughly one in three jumps.
            if (rnd() > 0.70f) offset = (rnd() - 0.5f) * 2f
            offset
        }
    }

    SceneGeometry(stars, ridges)
}
