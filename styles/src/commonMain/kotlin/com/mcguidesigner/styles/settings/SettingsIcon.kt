package com.mcguidesigner.styles.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The settings mark: a gear, drawn in strokes.
 *
 * Strokes rather than a filled cog with a hole punched through it, because a
 * punched hole has to be filled with whatever is *behind* the icon, and this
 * one sits on a bar that changes colour with the edition, the theme and the
 * card the pointer happens to be over. A stroked gear needs to know nothing
 * about its background, so it reads correctly on all of them.
 */
@Composable
fun SettingsIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    ink: Color = Color.White,
) {
    Canvas(modifier.size(size)) {
        val extent = minOf(this.size.width, this.size.height)
        if (extent <= 0f) return@Canvas

        val unit = extent / 24f
        val centre = Offset(this.size.width / 2f, this.size.height / 2f)
        val weight = 1.9f * unit

        // Eight teeth, drawn as short radial spokes just outside the body.
        repeat(8) { i ->
            val angle = i * (kotlin.math.PI.toFloat() / 4f)
            val dx = cos(angle)
            val dy = sin(angle)
            drawLine(
                color = ink,
                start = centre + Offset(dx * 7.4f * unit, dy * 7.4f * unit),
                end = centre + Offset(dx * 10.3f * unit, dy * 10.3f * unit),
                strokeWidth = weight,
                cap = StrokeCap.Round,
            )
        }

        drawCircle(ink, radius = 6.6f * unit, center = centre, style = Stroke(width = weight))
        drawCircle(ink, radius = 2.7f * unit, center = centre, style = Stroke(width = weight))
    }
}

/**
 * The back mark: a left-pointing chevron with a shaft.
 *
 * Shared rather than redrawn per shell so the desktop's toolbar button and the
 * tablet's tool row cannot drift into two slightly different arrows.
 */
@Composable
fun BackIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    ink: Color = Color.White,
) {
    Canvas(modifier.size(size)) {
        val extent = minOf(this.size.width, this.size.height)
        if (extent <= 0f) return@Canvas

        val unit = extent / 24f
        val cy = this.size.height / 2f
        val left = (this.size.width - extent) / 2f
        val weight = 2.1f * unit

        fun at(x: Float, y: Float) = Offset(left + x * unit, cy + (y - 12f) * unit)

        drawLine(ink, at(5.5f, 12f), at(19f, 12f), weight, StrokeCap.Round)
        drawLine(ink, at(5.5f, 12f), at(11.5f, 6f), weight, StrokeCap.Round)
        drawLine(ink, at(5.5f, 12f), at(11.5f, 18f), weight, StrokeCap.Round)
    }
}
