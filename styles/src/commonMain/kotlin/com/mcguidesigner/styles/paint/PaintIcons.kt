package com.mcguidesigner.styles.paint

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Every icon in the paint screen, drawn rather than typed.
 *
 * A glyph from a font would be one line each, and would also be at the mercy of
 * whichever font the device happens to substitute - the reason the rest of this
 * app keeps its palette glyphs to plain ASCII. That is fine for a component
 * palette and not fine here: a toolbar of pencils and erasers rendered as
 * tofu boxes is not a toolbar. These are paths, so they are the same on every
 * device and scale to any size without a second asset.
 *
 * Each takes the [DrawScope] sized to the icon's box, so they compose at any
 * size, and a tint, so a selected tool can be drawn in the accent colour
 * without a second copy.
 */
object PaintIcons {

    private fun DrawScope.line(
        ax: Float, ay: Float, bx: Float, by: Float, tint: Color, weight: Float = 0.09f,
    ) {
        drawLine(
            color = tint,
            start = Offset(size.width * ax, size.height * ay),
            end = Offset(size.width * bx, size.height * by),
            strokeWidth = size.minDimension * weight,
            cap = StrokeCap.Round,
        )
    }

    private fun DrawScope.shape(tint: Color, filled: Boolean, weight: Float = 0.09f, build: Path.(Float, Float) -> Unit) {
        val path = Path().apply { build(size.width, size.height) }
        if (filled) drawPath(path, tint)
        else drawPath(
            path,
            tint,
            style = Stroke(width = size.minDimension * weight, join = StrokeJoin.Round, cap = StrokeCap.Round),
        )
    }

    /** Undo: an arrow curving back on itself. */
    fun DrawScope.undo(tint: Color) {
        shape(tint, filled = false) { w, h ->
            moveTo(w * 0.30f, h * 0.30f)
            lineTo(w * 0.14f, h * 0.46f)
            lineTo(w * 0.30f, h * 0.62f)
        }
        shape(tint, filled = false) { w, h ->
            moveTo(w * 0.16f, h * 0.46f)
            cubicTo(w * 0.55f, h * 0.36f, w * 0.92f, h * 0.46f, w * 0.80f, h * 0.84f)
        }
    }

    fun DrawScope.redo(tint: Color) {
        shape(tint, filled = false) { w, h ->
            moveTo(w * 0.70f, h * 0.30f)
            lineTo(w * 0.86f, h * 0.46f)
            lineTo(w * 0.70f, h * 0.62f)
        }
        shape(tint, filled = false) { w, h ->
            moveTo(w * 0.84f, h * 0.46f)
            cubicTo(w * 0.45f, h * 0.36f, w * 0.08f, h * 0.46f, w * 0.20f, h * 0.84f)
        }
    }

    /** The view popover: two slider rows, as on the settings button. */
    fun DrawScope.sliders(tint: Color) {
        line(0.12f, 0.32f, 0.88f, 0.32f, tint, 0.10f)
        line(0.12f, 0.68f, 0.88f, 0.68f, tint, 0.10f)
        drawCircle(tint, radius = size.minDimension * 0.13f, center = Offset(size.width * 0.68f, size.height * 0.32f))
        drawCircle(tint, radius = size.minDimension * 0.13f, center = Offset(size.width * 0.32f, size.height * 0.68f))
    }

    /** Selection: a dashed square. */
    fun DrawScope.marquee(tint: Color) {
        drawRect(
            color = tint,
            topLeft = Offset(size.width * 0.12f, size.height * 0.12f),
            size = Size(size.width * 0.76f, size.height * 0.76f),
            style = Stroke(
                width = size.minDimension * 0.09f,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(size.minDimension * 0.16f, size.minDimension * 0.12f),
                ),
            ),
        )
    }

    /** The stroke popover: a hand, standing for stabilizer and shape tools. */
    fun DrawScope.hand(tint: Color) {
        shape(tint, filled = false, weight = 0.08f) { w, h ->
            moveTo(w * 0.30f, h * 0.82f)
            lineTo(w * 0.30f, h * 0.34f)
            moveTo(w * 0.48f, h * 0.82f)
            lineTo(w * 0.48f, h * 0.24f)
            moveTo(w * 0.66f, h * 0.82f)
            lineTo(w * 0.66f, h * 0.34f)
        }
        shape(tint, filled = false, weight = 0.08f) { w, h ->
            moveTo(w * 0.22f, h * 0.86f)
            lineTo(w * 0.74f, h * 0.86f)
        }
    }

    /** The ruler popover: a ruler at an angle. */
    fun DrawScope.ruler(tint: Color) {
        shape(tint, filled = false, weight = 0.08f) { w, h ->
            moveTo(w * 0.18f, h * 0.66f)
            lineTo(w * 0.66f, h * 0.18f)
            lineTo(w * 0.84f, h * 0.36f)
            lineTo(w * 0.36f, h * 0.84f)
            close()
        }
        line(0.34f, 0.50f, 0.44f, 0.60f, tint, 0.06f)
        line(0.50f, 0.34f, 0.60f, 0.44f, tint, 0.06f)
    }

    /** The materials popover: a picture in a frame. */
    fun DrawScope.picture(tint: Color) {
        drawRect(
            color = tint,
            topLeft = Offset(size.width * 0.12f, size.height * 0.18f),
            size = Size(size.width * 0.76f, size.height * 0.64f),
            style = Stroke(width = size.minDimension * 0.09f, join = StrokeJoin.Round),
        )
        shape(tint, filled = true) { w, h ->
            moveTo(w * 0.18f, h * 0.74f)
            lineTo(w * 0.40f, h * 0.44f)
            lineTo(w * 0.56f, h * 0.62f)
            lineTo(w * 0.68f, h * 0.50f)
            lineTo(w * 0.82f, h * 0.74f)
            close()
        }
    }

    /** A brush: a handle and a nib. */
    fun DrawScope.brush(tint: Color) {
        shape(tint, filled = true) { w, h ->
            moveTo(w * 0.62f, h * 0.10f)
            lineTo(w * 0.90f, h * 0.34f)
            lineTo(w * 0.44f, h * 0.80f)
            lineTo(w * 0.20f, h * 0.56f)
            close()
        }
        shape(tint, filled = true) { w, h ->
            moveTo(w * 0.20f, h * 0.58f)
            lineTo(w * 0.42f, h * 0.80f)
            lineTo(w * 0.14f, h * 0.92f)
            close()
        }
    }

    /** An eraser: a rounded block at an angle. */
    fun DrawScope.eraser(tint: Color) {
        shape(tint, filled = true) { w, h ->
            moveTo(w * 0.56f, h * 0.14f)
            lineTo(w * 0.90f, h * 0.48f)
            lineTo(w * 0.58f, h * 0.80f)
            lineTo(w * 0.24f, h * 0.46f)
            close()
        }
        shape(tint, filled = false, weight = 0.08f) { w, h ->
            moveTo(w * 0.24f, h * 0.48f)
            lineTo(w * 0.14f, h * 0.58f)
            lineTo(w * 0.40f, h * 0.86f)
            lineTo(w * 0.80f, h * 0.86f)
        }
    }

    /** The swap button: a brush and an eraser with arrows between them. */
    fun DrawScope.swap(tint: Color) {
        line(0.10f, 0.28f, 0.42f, 0.28f, tint, 0.08f)
        shape(tint, filled = true) { w, h ->
            moveTo(w * 0.42f, h * 0.18f); lineTo(w * 0.56f, h * 0.28f); lineTo(w * 0.42f, h * 0.38f); close()
        }
        line(0.90f, 0.72f, 0.58f, 0.72f, tint, 0.08f)
        shape(tint, filled = true) { w, h ->
            moveTo(w * 0.58f, h * 0.62f); lineTo(w * 0.44f, h * 0.72f); lineTo(w * 0.58f, h * 0.82f); close()
        }
    }

    /** A paint bucket, tipped over. */
    fun DrawScope.bucket(tint: Color) {
        shape(tint, filled = false, weight = 0.08f) { w, h ->
            moveTo(w * 0.18f, h * 0.46f)
            lineTo(w * 0.52f, h * 0.14f)
            lineTo(w * 0.84f, h * 0.46f)
            lineTo(w * 0.52f, h * 0.78f)
            close()
        }
        shape(tint, filled = true) { w, h ->
            moveTo(w * 0.84f, h * 0.56f)
            cubicTo(w * 0.98f, h * 0.74f, w * 0.94f, h * 0.92f, w * 0.82f, h * 0.92f)
            cubicTo(w * 0.70f, h * 0.92f, w * 0.68f, h * 0.74f, w * 0.84f, h * 0.56f)
            close()
        }
    }

    /** An eyedropper. */
    fun DrawScope.dropper(tint: Color) {
        shape(tint, filled = false, weight = 0.09f) { w, h ->
            moveTo(w * 0.16f, h * 0.84f)
            lineTo(w * 0.20f, h * 0.62f)
            lineTo(w * 0.66f, h * 0.16f)
            lineTo(w * 0.86f, h * 0.36f)
            lineTo(w * 0.40f, h * 0.82f)
            close()
        }
        line(0.56f, 0.26f, 0.76f, 0.46f, tint, 0.07f)
    }

    /** The magic eraser: an eraser with a sparkle. */
    fun DrawScope.magicEraser(tint: Color) {
        shape(tint, filled = true) { w, h ->
            moveTo(w * 0.50f, h * 0.30f)
            lineTo(w * 0.84f, h * 0.62f)
            lineTo(w * 0.54f, h * 0.88f)
            lineTo(w * 0.20f, h * 0.56f)
            close()
        }
        // The sparkle: a four-pointed star, the universal "automatic" mark.
        shape(tint, filled = true) { w, h ->
            moveTo(w * 0.26f, h * 0.06f)
            lineTo(w * 0.32f, h * 0.22f)
            lineTo(w * 0.48f, h * 0.28f)
            lineTo(w * 0.32f, h * 0.34f)
            lineTo(w * 0.26f, h * 0.50f)
            lineTo(w * 0.20f, h * 0.34f)
            lineTo(w * 0.04f, h * 0.28f)
            lineTo(w * 0.20f, h * 0.22f)
            close()
        }
    }

    /** Smudge: a fingertip drawing a smear. */
    fun DrawScope.smudge(tint: Color) {
        shape(tint, filled = true) { w, h ->
            moveTo(w * 0.50f, h * 0.12f)
            cubicTo(w * 0.86f, h * 0.44f, w * 0.80f, h * 0.84f, w * 0.50f, h * 0.84f)
            cubicTo(w * 0.20f, h * 0.84f, w * 0.14f, h * 0.44f, w * 0.50f, h * 0.12f)
            close()
        }
    }

    /** Blur: concentric softening rings. */
    fun DrawScope.blur(tint: Color) {
        drawCircle(tint.copy(alpha = 0.25f), radius = size.minDimension * 0.42f, center = center)
        drawCircle(tint.copy(alpha = 0.45f), radius = size.minDimension * 0.28f, center = center)
        drawCircle(tint, radius = size.minDimension * 0.13f, center = center)
    }

    /** A hand for the pan tool. */
    fun DrawScope.pan(tint: Color) {
        line(0.50f, 0.10f, 0.50f, 0.90f, tint, 0.08f)
        line(0.10f, 0.50f, 0.90f, 0.50f, tint, 0.08f)
        shape(tint, filled = true) { w, h ->
            moveTo(w * 0.50f, h * 0.02f); lineTo(w * 0.60f, h * 0.18f); lineTo(w * 0.40f, h * 0.18f); close()
        }
        shape(tint, filled = true) { w, h ->
            moveTo(w * 0.50f, h * 0.98f); lineTo(w * 0.60f, h * 0.82f); lineTo(w * 0.40f, h * 0.82f); close()
        }
        shape(tint, filled = true) { w, h ->
            moveTo(w * 0.02f, h * 0.50f); lineTo(w * 0.18f, h * 0.40f); lineTo(w * 0.18f, h * 0.60f); close()
        }
        shape(tint, filled = true) { w, h ->
            moveTo(w * 0.98f, h * 0.50f); lineTo(w * 0.82f, h * 0.40f); lineTo(w * 0.82f, h * 0.60f); close()
        }
    }

    /** The down arrow that dismisses the bottom panel. */
    fun DrawScope.arrowDown(tint: Color) {
        line(0.50f, 0.12f, 0.50f, 0.80f, tint, 0.08f)
        shape(tint, filled = false, weight = 0.08f) { w, h ->
            moveTo(w * 0.26f, h * 0.56f)
            lineTo(w * 0.50f, h * 0.84f)
            lineTo(w * 0.74f, h * 0.56f)
        }
    }

    /** The back arrow at the far right of the bottom bar. */
    fun DrawScope.arrowLeft(tint: Color) {
        line(0.88f, 0.50f, 0.16f, 0.50f, tint, 0.08f)
        shape(tint, filled = false, weight = 0.08f) { w, h ->
            moveTo(w * 0.44f, h * 0.24f)
            lineTo(w * 0.12f, h * 0.50f)
            lineTo(w * 0.44f, h * 0.76f)
        }
    }

    /** Layers: three stacked sheets. */
    fun DrawScope.layers(tint: Color) {
        shape(tint, filled = false, weight = 0.075f) { w, h ->
            moveTo(w * 0.10f, h * 0.34f)
            lineTo(w * 0.62f, h * 0.34f)
            lineTo(w * 0.62f, h * 0.90f)
            lineTo(w * 0.10f, h * 0.90f)
            close()
        }
        line(0.26f, 0.22f, 0.78f, 0.22f, tint, 0.07f)
        line(0.78f, 0.22f, 0.78f, 0.74f, tint, 0.07f)
        line(0.38f, 0.10f, 0.90f, 0.10f, tint, 0.07f)
        line(0.90f, 0.10f, 0.90f, 0.62f, tint, 0.07f)
    }

    /** A plus, for adding a layer. */
    fun DrawScope.plus(tint: Color) {
        line(0.50f, 0.16f, 0.50f, 0.84f, tint, 0.10f)
        line(0.16f, 0.50f, 0.84f, 0.50f, tint, 0.10f)
    }

    /** A bin, for deleting one. */
    fun DrawScope.bin(tint: Color) {
        line(0.14f, 0.26f, 0.86f, 0.26f, tint, 0.08f)
        shape(tint, filled = false, weight = 0.08f) { w, h ->
            moveTo(w * 0.24f, h * 0.30f)
            lineTo(w * 0.30f, h * 0.88f)
            lineTo(w * 0.70f, h * 0.88f)
            lineTo(w * 0.76f, h * 0.30f)
        }
        shape(tint, filled = false, weight = 0.08f) { w, h ->
            moveTo(w * 0.38f, h * 0.24f)
            lineTo(w * 0.40f, h * 0.12f)
            lineTo(w * 0.60f, h * 0.12f)
            lineTo(w * 0.62f, h * 0.24f)
        }
    }

    /** Two overlapping sheets: duplicate. */
    fun DrawScope.duplicate(tint: Color) {
        shape(tint, filled = false, weight = 0.08f) { w, h ->
            moveTo(w * 0.12f, h * 0.30f)
            lineTo(w * 0.62f, h * 0.30f)
            lineTo(w * 0.62f, h * 0.88f)
            lineTo(w * 0.12f, h * 0.88f)
            close()
        }
        shape(tint, filled = false, weight = 0.08f) { w, h ->
            moveTo(w * 0.34f, h * 0.16f)
            lineTo(w * 0.86f, h * 0.16f)
            lineTo(w * 0.86f, h * 0.72f)
        }
    }

    /** An arrow pointing down into a line: merge down. */
    fun DrawScope.mergeDown(tint: Color) {
        line(0.50f, 0.10f, 0.50f, 0.62f, tint, 0.08f)
        shape(tint, filled = false, weight = 0.08f) { w, h ->
            moveTo(w * 0.30f, h * 0.44f)
            lineTo(w * 0.50f, h * 0.66f)
            lineTo(w * 0.70f, h * 0.44f)
        }
        line(0.16f, 0.86f, 0.84f, 0.86f, tint, 0.08f)
    }

    /** An open eye, and its struck-through form. */
    fun DrawScope.eye(tint: Color, open: Boolean) {
        shape(tint, filled = false, weight = 0.08f) { w, h ->
            moveTo(w * 0.08f, h * 0.50f)
            cubicTo(w * 0.30f, h * 0.16f, w * 0.70f, h * 0.16f, w * 0.92f, h * 0.50f)
            cubicTo(w * 0.70f, h * 0.84f, w * 0.30f, h * 0.84f, w * 0.08f, h * 0.50f)
            close()
        }
        if (open) {
            drawCircle(tint, radius = size.minDimension * 0.15f, center = center)
        } else {
            line(0.14f, 0.86f, 0.86f, 0.14f, tint, 0.09f)
        }
    }

    /** A lowercase alpha, for the alpha lock. */
    fun DrawScope.alphaLock(tint: Color) {
        shape(tint, filled = false, weight = 0.09f) { w, h ->
            moveTo(w * 0.72f, h * 0.28f)
            cubicTo(w * 0.20f, h * 0.20f, w * 0.16f, h * 0.86f, w * 0.60f, h * 0.72f)
            cubicTo(w * 0.80f, h * 0.64f, w * 0.66f, h * 0.30f, w * 0.78f, h * 0.78f)
        }
    }

    /** A downward arrow with a corner: clip to the layer below. */
    fun DrawScope.clip(tint: Color) {
        shape(tint, filled = false, weight = 0.09f) { w, h ->
            moveTo(w * 0.30f, h * 0.16f)
            lineTo(w * 0.30f, h * 0.70f)
            lineTo(w * 0.76f, h * 0.70f)
        }
        shape(tint, filled = true) { w, h ->
            moveTo(w * 0.60f, h * 0.52f); lineTo(w * 0.86f, h * 0.70f); lineTo(w * 0.60f, h * 0.88f); close()
        }
    }

    /** A checkerboard square: transparency, and "clear layer". */
    fun DrawScope.checker(tint: Color) {
        val cell = size.width / 4f
        for (row in 0 until 4) {
            for (column in 0 until 4) {
                if ((row + column) % 2 == 0) continue
                drawRect(
                    color = tint,
                    topLeft = Offset(column * cell, row * cell),
                    size = Size(cell, cell),
                )
            }
        }
    }

    /** A grid, for the palette tab. */
    fun DrawScope.grid(tint: Color) {
        val cell = size.width / 3.4f
        val gap = size.width * 0.06f
        for (row in 0 until 3) {
            for (column in 0 until 3) {
                drawRect(
                    color = tint,
                    topLeft = Offset(column * (cell + gap * 0.2f), row * (cell + gap * 0.2f)),
                    size = Size(cell * 0.8f, cell * 0.8f),
                )
            }
        }
    }

    /** Three overlapping circles: the RGB tab. */
    fun DrawScope.rgb(tint: Color) {
        val r = size.minDimension * 0.28f
        drawCircle(tint.copy(alpha = 0.7f), r, Offset(size.width * 0.5f, size.height * 0.32f))
        drawCircle(tint.copy(alpha = 0.7f), r, Offset(size.width * 0.32f, size.height * 0.66f))
        drawCircle(tint.copy(alpha = 0.7f), r, Offset(size.width * 0.68f, size.height * 0.66f))
    }

    /** A ring with a bite out of it: the HSB tab. */
    fun DrawScope.hsb(tint: Color) {
        drawCircle(
            tint,
            radius = size.minDimension * 0.36f,
            center = center,
            style = Stroke(width = size.minDimension * 0.22f),
        )
        drawRect(
            color = Color.Transparent,
            topLeft = Offset(size.width * 0.44f, 0f),
            size = Size(size.width * 0.2f, size.height * 0.5f),
            blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
        )
    }

    /** An arrow into a tray: import an image. */
    fun DrawScope.importImage(tint: Color) {
        line(0.50f, 0.10f, 0.50f, 0.58f, tint, 0.08f)
        shape(tint, filled = false, weight = 0.08f) { w, h ->
            moveTo(w * 0.30f, h * 0.40f)
            lineTo(w * 0.50f, h * 0.62f)
            lineTo(w * 0.70f, h * 0.40f)
        }
        shape(tint, filled = false, weight = 0.08f) { w, h ->
            moveTo(w * 0.14f, h * 0.72f)
            lineTo(w * 0.14f, h * 0.88f)
            lineTo(w * 0.86f, h * 0.88f)
            lineTo(w * 0.86f, h * 0.72f)
        }
    }

    /** An arrow out of a tray: export. */
    fun DrawScope.exportImage(tint: Color) {
        line(0.50f, 0.62f, 0.50f, 0.14f, tint, 0.08f)
        shape(tint, filled = false, weight = 0.08f) { w, h ->
            moveTo(w * 0.30f, h * 0.34f)
            lineTo(w * 0.50f, h * 0.12f)
            lineTo(w * 0.70f, h * 0.34f)
        }
        shape(tint, filled = false, weight = 0.08f) { w, h ->
            moveTo(w * 0.14f, h * 0.72f)
            lineTo(w * 0.14f, h * 0.88f)
            lineTo(w * 0.86f, h * 0.88f)
            lineTo(w * 0.86f, h * 0.72f)
        }
    }

    /** The circular brush preview inside the size bubble. */
    fun DrawScope.dot(tint: Color, fraction: Float) {
        drawCircle(tint, radius = size.minDimension * 0.5f * fraction.coerceIn(0.08f, 1f), center = center)
    }

    /** A diagonal red stroke over a swatch: "no colour" / transparent. */
    fun DrawScope.noColour(tint: Color) {
        drawRect(Color.White, Offset.Zero, size)
        drawLine(
            Color(0xFFE53935),
            start = Offset(size.width, 0f),
            end = Offset(0f, size.height),
            strokeWidth = size.minDimension * 0.12f,
        )
    }

    /** Bounds helper for icons that want to fill their box exactly. */
    fun DrawScope.box(): Rect = Rect(Offset.Zero, size)
}
