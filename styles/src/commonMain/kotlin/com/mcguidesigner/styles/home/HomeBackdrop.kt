package com.mcguidesigner.styles.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
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
 * The star field is seeded, so it does not reshuffle when the theme flips.
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

    Canvas(modifier) {
        val w = size.width
        val h = size.height
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
            var seed = 20260823L
            fun rnd(): Float {
                seed = (seed * 1664525L + 1013904223L) and 0xFFFFFFFFL
                return seed.toFloat() / 4294967296f
            }
            repeat(260) {
                val x = (rnd() * w / px).roundToInt() * px
                val y = (rnd() * h * (hz - 0.04f) / px).roundToInt() * px
                val a = 0.30f + rnd() * 0.70f
                val c = if (rnd() > 0.86f) Color.White else Color(0xFF8A96A2)
                drawRect(c.copy(alpha = a), Offset(x, y), Size(2f, 2f))
            }
        }

        drawRect(sun, Offset((sx / px).roundToInt() * px, (sy / px).roundToInt() * px), Size(px * 3, px * 3))

        // Five ridges marching down, each a solid step darker than the one
        // behind it. Solid, not stacked alpha: translucent ridges over a dark
        // sky turn the lower third into mud.
        for (band in 0 until 5) {
            var seed = 9001L + band * 137L
            fun rnd(): Float {
                seed = (seed * 1664525L + 1013904223L) and 0xFFFFFFFFL
                return seed.toFloat() / 4294967296f
            }
            val base = h * (hz + band * 0.075f)
            val amp = 34f - band * 6f
            val path = Path().apply {
                moveTo(0f, h)
                var y = base
                var x = 0f
                while (x <= w + px) {
                    if (rnd() > 0.70f) y = base + (rnd() - 0.5f) * amp * 2f
                    val q = (y / px).roundToInt() * px
                    lineTo(x, q); lineTo(x + px, q)
                    x += px
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
