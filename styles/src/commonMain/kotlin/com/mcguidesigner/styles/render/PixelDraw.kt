package com.mcguidesigner.styles.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.mcguidesigner.core.model.Insets
import kotlin.math.roundToInt

/**
 * Low-level pixel-art drawing primitives shared by both edition skins.
 *
 * Everything snaps to whole device pixels before painting.  Minecraft UI is
 * nearest-neighbour pixel art, and a half-pixel rectangle edge is instantly
 * visible as a blurred seam once the canvas is zoomed in - which, in a GUI
 * designer, is most of the time.
 */
/** Rounds a rectangle onto the device pixel grid. */
fun snap(rect: Rect): Rect = Rect(
    left = rect.left.roundToInt().toFloat(),
    top = rect.top.roundToInt().toFloat(),
    right = rect.right.roundToInt().toFloat(),
    bottom = rect.bottom.roundToInt().toFloat(),
)

fun DrawScope.fillRect(rect: Rect, color: Color) {
    if (color.alpha <= 0f) return
    val r = snap(rect)
    if (r.width <= 0f || r.height <= 0f) return
    drawRect(color, topLeft = r.topLeft, size = r.size)
}

/** One-pixel-accurate outline drawn *inside* [rect]. */
fun DrawScope.strokeRect(rect: Rect, color: Color, thickness: Float) {
    if (color.alpha <= 0f || thickness <= 0f) return
    val r = snap(rect)
    val t = thickness.coerceAtLeast(1f)
    fillRect(Rect(r.left, r.top, r.right, r.top + t), color)
    fillRect(Rect(r.left, r.bottom - t, r.right, r.bottom), color)
    fillRect(Rect(r.left, r.top + t, r.left + t, r.bottom - t), color)
    fillRect(Rect(r.right - t, r.top + t, r.right, r.bottom - t), color)
}

/**
 * Vanilla-style raised bevel: light on the top/left, dark on the
 * bottom/right, flat fill in the middle.  Pass [inverted] for sunken
 * widgets such as text fields and inventory slots.
 */
fun DrawScope.bevelBox(
    rect: Rect,
    fill: Color,
    light: Color,
    dark: Color,
    thickness: Float = 1f,
    inverted: Boolean = false,
) {
    val r = snap(rect)
    val t = thickness.coerceAtLeast(1f)
    val topLeft = if (inverted) dark else light
    val bottomRight = if (inverted) light else dark

    fillRect(r, fill)
    // Top and left edges.
    fillRect(Rect(r.left, r.top, r.right - t, r.top + t), topLeft)
    fillRect(Rect(r.left, r.top, r.left + t, r.bottom - t), topLeft)
    // Bottom and right edges.
    fillRect(Rect(r.left + t, r.bottom - t, r.right, r.bottom), bottomRight)
    fillRect(Rect(r.right - t, r.top + t, r.right, r.bottom), bottomRight)
}

/** Rounded rectangle drawn with square pixels - Bedrock's chunky corners. */
fun DrawScope.pixelRoundRect(rect: Rect, color: Color, radius: Float) {
    val r = snap(rect)
    val rad = radius.coerceIn(0f, minOf(r.width, r.height) / 2f)
    if (rad <= 0.5f) {
        fillRect(r, color)
        return
    }
    // Centre band plus two inset bands reproduces a chamfered corner
    // without any anti-aliasing.
    fillRect(Rect(r.left + rad, r.top, r.right - rad, r.bottom), color)
    fillRect(Rect(r.left, r.top + rad, r.left + rad, r.bottom - rad), color)
    fillRect(Rect(r.right - rad, r.top + rad, r.right, r.bottom - rad), color)
    val step = (rad / 2f).coerceAtLeast(1f)
    var inset = 0f
    while (inset < rad) {
        val shrink = rad - inset
        fillRect(Rect(r.left + shrink, r.top + inset, r.right - shrink, r.top + inset + step), color)
        fillRect(Rect(r.left + shrink, r.bottom - inset - step, r.right - shrink, r.bottom - inset), color)
        inset += step
    }
}

/** Filled circle approximated on the pixel grid, for touch controls. */
fun DrawScope.pixelCircle(center: Offset, radius: Float, color: Color, pixel: Float) {
    if (color.alpha <= 0f || radius <= 0f) return
    val step = pixel.coerceAtLeast(1f)
    var y = -radius
    while (y < radius) {
        val halfWidth = kotlin.math.sqrt((radius * radius - y * y).coerceAtLeast(0f))
        fillRect(
            Rect(center.x - halfWidth, center.y + y, center.x + halfWidth, center.y + y + step),
            color,
        )
        y += step
    }
}

/**
 * Nine-slice blit: corners stay pixel-exact, edges and centre stretch.
 * Falls back to a plain stretch when [insets] is empty.
 */
fun DrawScope.nineSlice(
    image: ImageBitmap,
    dest: Rect,
    insets: Insets,
    filterQuality: FilterQuality = FilterQuality.None,
) {
    val d = snap(dest)
    if (d.width <= 0f || d.height <= 0f) return

    if (insets.horizontal == 0 && insets.vertical == 0) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(d.left.roundToInt(), d.top.roundToInt()),
            dstSize = IntSize(d.width.roundToInt(), d.height.roundToInt()),
            filterQuality = filterQuality,
        )
        return
    }

    val l = insets.left
    val t = insets.top
    val r = insets.right
    val b = insets.bottom
    val srcCenterW = (image.width - l - r).coerceAtLeast(1)
    val srcCenterH = (image.height - t - b).coerceAtLeast(1)
    val dstCenterW = (d.width.roundToInt() - l - r).coerceAtLeast(0)
    val dstCenterH = (d.height.roundToInt() - t - b).coerceAtLeast(0)
    val x0 = d.left.roundToInt()
    val y0 = d.top.roundToInt()

    fun blit(sx: Int, sy: Int, sw: Int, sh: Int, dx: Int, dy: Int, dw: Int, dh: Int) {
        if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0) return
        drawImage(
            image = image,
            srcOffset = IntOffset(sx, sy),
            srcSize = IntSize(sw, sh),
            dstOffset = IntOffset(dx, dy),
            dstSize = IntSize(dw, dh),
            filterQuality = filterQuality,
        )
    }

    // Corners.
    blit(0, 0, l, t, x0, y0, l, t)
    blit(image.width - r, 0, r, t, x0 + l + dstCenterW, y0, r, t)
    blit(0, image.height - b, l, b, x0, y0 + t + dstCenterH, l, b)
    blit(image.width - r, image.height - b, r, b, x0 + l + dstCenterW, y0 + t + dstCenterH, r, b)
    // Edges.
    blit(l, 0, srcCenterW, t, x0 + l, y0, dstCenterW, t)
    blit(l, image.height - b, srcCenterW, b, x0 + l, y0 + t + dstCenterH, dstCenterW, b)
    blit(0, t, l, srcCenterH, x0, y0 + t, l, dstCenterH)
    blit(image.width - r, t, r, srcCenterH, x0 + l + dstCenterW, y0 + t, r, dstCenterH)
    // Centre.
    blit(l, t, srcCenterW, srcCenterH, x0 + l, y0 + t, dstCenterW, dstCenterH)
}

/** Draws [image] into [dest] according to a CSS-like fit mode. */
fun DrawScope.drawImageFitted(
    image: ImageBitmap,
    dest: Rect,
    fit: String,
    insets: Insets = Insets.Zero,
    alpha: Float = 1f,
    pixelated: Boolean = true,
) {
    val quality = if (pixelated) FilterQuality.None else FilterQuality.Medium
    val d = snap(dest)
    when (fit) {
        "nine_slice" -> nineSlice(image, d, insets, quality)

        "tile" -> {
            var y = d.top
            while (y < d.bottom) {
                var x = d.left
                while (x < d.right) {
                    val w = minOf(image.width.toFloat(), d.right - x)
                    val h = minOf(image.height.toFloat(), d.bottom - y)
                    drawImage(
                        image = image,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
                        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
                        dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
                        alpha = alpha,
                        filterQuality = quality,
                    )
                    x += image.width
                }
                y += image.height
            }
        }

        "contain", "cover" -> {
            val scale = if (fit == "contain") {
                minOf(d.width / image.width, d.height / image.height)
            } else {
                maxOf(d.width / image.width, d.height / image.height)
            }
            val w = (image.width * scale).roundToInt().coerceAtLeast(1)
            val h = (image.height * scale).roundToInt().coerceAtLeast(1)
            val dx = (d.left + (d.width - w) / 2f).roundToInt()
            val dy = (d.top + (d.height - h) / 2f).roundToInt()
            clipRect(d) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset(dx, dy),
                    dstSize = IntSize(w, h),
                    alpha = alpha,
                    filterQuality = quality,
                )
            }
        }

        else -> drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(d.left.roundToInt(), d.top.roundToInt()),
            dstSize = IntSize(d.width.roundToInt().coerceAtLeast(1), d.height.roundToInt().coerceAtLeast(1)),
            alpha = alpha,
            filterQuality = quality,
        )
    }
}

private inline fun DrawScope.clipRect(rect: Rect, block: DrawScope.() -> Unit) {
    clipRect(rect.left, rect.top, rect.right, rect.bottom) { block() }
}

/** Diagonal hatch used for empty texture slots and unsupported widgets. */
fun DrawScope.hatch(rect: Rect, color: Color, spacing: Float) {
    val r = snap(rect)
    var x = r.left - r.height
    while (x < r.right) {
        drawLine(
            color = color,
            start = Offset(x, r.bottom),
            end = Offset(x + r.height, r.top),
            strokeWidth = 1f,
        )
        x += spacing
    }
}

/** Checkerboard fill marking "no texture assigned". */
fun DrawScope.checkerboard(rect: Rect, a: Color, b: Color, cell: Float) {
    val r = snap(rect)
    val size = cell.coerceAtLeast(2f)
    var row = 0
    var y = r.top
    while (y < r.bottom) {
        var column = 0
        var x = r.left
        while (x < r.right) {
            val color = if ((row + column) % 2 == 0) a else b
            fillRect(
                Rect(x, y, minOf(x + size, r.right), minOf(y + size, r.bottom)),
                color,
            )
            x += size
            column++
        }
        y += size
        row++
    }
}

/** Text with the classic offset drop shadow Minecraft uses everywhere. */
fun DrawScope.drawShadowedText(
    measurer: TextMeasurer,
    text: String,
    topLeft: Offset,
    style: TextStyle,
    shadowColor: Color,
    shadow: Boolean,
    maxWidth: Int = Int.MAX_VALUE,
): TextLayoutResult {
    val layout = measurer.measure(
        text = text,
        style = style,
        maxLines = if (maxWidth == Int.MAX_VALUE) 1 else Int.MAX_VALUE,
        constraints = androidx.compose.ui.unit.Constraints(
            maxWidth = if (maxWidth == Int.MAX_VALUE) Int.MAX_VALUE else maxWidth.coerceAtLeast(1),
        ),
    )
    if (shadow) {
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(topLeft.x + shadowOffset(style), topLeft.y + shadowOffset(style)),
            color = shadowColor,
        )
    }
    drawText(textLayoutResult = layout, topLeft = topLeft)
    return layout
}

private fun DrawScope.shadowOffset(style: TextStyle): Float =
    (style.fontSize.toPx() / 8f).coerceAtLeast(1f)

/** Focus ring used by both editions (drawn just outside the widget). */
fun DrawScope.focusRing(rect: Rect, color: Color, thickness: Float) {
    val r = snap(rect).inflate(thickness)
    drawRect(
        color = color,
        topLeft = r.topLeft,
        size = Size(r.width, r.height),
        style = Stroke(width = thickness),
    )
}

