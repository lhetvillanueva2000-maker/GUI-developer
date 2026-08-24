package com.mcguidesigner.styles.support

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The donate mark: an open hand holding a heart with a coin slot in it.
 *
 * Drawn rather than shipped as a bitmap so it stays crisp at every density and
 * takes its ink colour from whatever it sits on - the same mark reads on a
 * dark toolbar and a light one, which a two-tone PNG would not.
 *
 * The geometry is authored in a 24x24 box, the size every other icon in the app
 * is drawn at, and scaled to fit.
 */
private const val VIEWPORT = 24f

/** The heart's own red. It is the one colour in the app that is not a token. */
val DonateHeartRed = Color(0xFFF4606B)

@Composable
fun DonateIcon(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    ink: Color = Color.White,
    heart: Color = DonateHeartRed,
    /**
     * What shows through the coin slot.  It has to match whatever is *behind*
     * the icon, because the slot is a hole in the heart rather than a colour of
     * its own.
     */
    slot: Color = Color.Black,
) {
    Canvas(modifier.size(size)) {
        drawDonateMark(ink = ink, heart = heart, slot = slot)
    }
}

/**
 * Paints the mark into the current [DrawScope], fitted to the shorter side.
 *
 * Split out from the composable so panels that already have a canvas - the
 * donate screen's own header, for one - can draw it at any size without
 * nesting another layout node.
 */
fun DrawScope.drawDonateMark(
    ink: Color,
    heart: Color,
    slot: Color,
) {
    val extent = minOf(size.width, size.height)
    if (extent <= 0f) return
    val unit = extent / VIEWPORT
    val left = (size.width - extent) / 2f
    val top = (size.height - extent) / 2f

    translate(left, top) {
        scale(unit, unit, pivot = Offset.Zero) {
            val line = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)

            // Three short rays, the "this is a gift" cue.
            drawLine(ink, Offset(12f, 1.6f), Offset(12f, 3.9f), 1.7f, StrokeCap.Round)
            drawLine(ink, Offset(7.4f, 3.0f), Offset(8.7f, 4.9f), 1.7f, StrokeCap.Round)
            drawLine(ink, Offset(16.6f, 3.0f), Offset(15.3f, 4.9f), 1.7f, StrokeCap.Round)

            drawPath(heartPath(), heart)
            // The slot is punched in the colour behind the icon, not in the
            // ink: it is meant to read as a gap in the heart.
            drawLine(slot, Offset(9.8f, 9.3f), Offset(14.2f, 9.3f), 1.5f, StrokeCap.Round)

            drawPath(handPath(), ink, style = line)
            drawPath(cuffPath(), ink, style = line)
            drawLine(ink, Offset(8.2f, 20.4f), Offset(12.6f, 20.4f), 1.7f, StrokeCap.Round)
        }
    }
}

/**
 * The theme toggle: a circle filled down one side.
 *
 * The same mark every OS uses for "light or dark", and the only honest one -
 * a sun or a moon claims which way the switch is about to go, and this switch
 * cycles through three states rather than flipping between two.
 */
@Composable
fun ThemeIcon(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    ink: Color = Color.White,
) {
    Canvas(modifier.size(size)) {
        val radius = minOf(this.size.width, this.size.height) / 2f
        val centre = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(ink, radius = radius, center = centre, style = Stroke(width = radius * 0.16f))
        // The filled half is drawn as an arc rather than a clipped circle so it
        // sits exactly inside the ring at any size.
        drawArc(
            color = ink,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(centre.x - radius, centre.y - radius),
            size = Size(radius * 2f, radius * 2f),
        )
    }
}

private fun heartPath(): Path = Path().apply {
    moveTo(12f, 15.4f)
    cubicTo(12f, 15.4f, 6.3f, 11.9f, 6.3f, 8.5f)
    cubicTo(6.3f, 6.7f, 7.7f, 5.3f, 9.4f, 5.3f)
    cubicTo(10.6f, 5.3f, 11.6f, 6.0f, 12f, 6.9f)
    cubicTo(12.4f, 6.0f, 13.4f, 5.3f, 14.6f, 5.3f)
    cubicTo(16.3f, 5.3f, 17.7f, 6.7f, 17.7f, 8.5f)
    cubicTo(17.7f, 11.9f, 12f, 15.4f, 12f, 15.4f)
    close()
}

private fun handPath(): Path = Path().apply {
    moveTo(3.9f, 17.5f)
    cubicTo(5.1f, 16.9f, 6.4f, 17.0f, 7.4f, 17.6f)
    cubicTo(9.2f, 18.7f, 11.3f, 19.4f, 13.5f, 19.5f)
    cubicTo(15.8f, 19.4f, 17.9f, 18.6f, 19.6f, 17.2f)
    cubicTo(20.6f, 16.4f, 21.9f, 17.7f, 21.0f, 18.7f)
    cubicTo(18.8f, 20.9f, 15.8f, 22.2f, 12.6f, 22.2f)
    lineTo(7.3f, 22.2f)
    cubicTo(5.6f, 22.2f, 4.5f, 21.6f, 3.9f, 20.9f)
    close()
}

/** The sleeve at the wrist, a rounded rectangle drawn by hand for the radius. */
private fun cuffPath(): Path = Path().apply {
    val l = 1.4f
    val t = 17.0f
    val r = l + 2.5f
    val b = t + 4.9f
    val c = 0.8f
    moveTo(l + c, t)
    lineTo(r - c, t)
    cubicTo(r, t, r, t, r, t + c)
    lineTo(r, b - c)
    cubicTo(r, b, r, b, r - c, b)
    lineTo(l + c, b)
    cubicTo(l, b, l, b, l, b - c)
    lineTo(l, t + c)
    cubicTo(l, t, l, t, l + c, t)
    close()
}
