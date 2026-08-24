package com.mcguidesigner.styles.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import com.mcguidesigner.core.model.ShapeKind
import com.mcguidesigner.core.model.TextureAsset
import com.mcguidesigner.core.model.bool
import com.mcguidesigner.core.model.color
import com.mcguidesigner.core.model.float
import com.mcguidesigner.core.model.int
import com.mcguidesigner.core.model.string
import com.mcguidesigner.core.model.texture
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renderers for the three element types that have no edition-specific look:
 * custom shapes, animated frame strips and the catch-all custom element.
 *
 * They live here rather than in `styles/java` or `styles/bedrock` because the
 * artwork is entirely the user's - a hexagon the user filled with their own
 * colour has no vanilla appearance to be faithful to, and giving each edition
 * its own copy would only mean the two drifting apart for no reason.  The
 * edition skins still call in through their own dispatch, so an edition that
 * ever *does* want its own treatment can take one back without disturbing the
 * other.
 */

// ---------------------------------------------------------------------------
// Custom shapes
// ---------------------------------------------------------------------------

/** Paints a `shape.custom` element: outline, fill, stroke and optional label. */
fun DrawScope.drawCustomShape(ctx: ElementRenderContext) {
    val rect = ctx.rect
    if (rect.width <= 0f || rect.height <= 0f) return

    val kind = ShapeKind.fromId(ctx.props.string("shape", ShapeKind.RECTANGLE.id))

    // Deliberately does *not* rotate. Rotation is applied once, for every
    // element of both editions, in `drawTurned` - doing it here as well turned
    // shapes twice as far as anything else, and left their labels upright while
    // the shape beneath them leaned over.
    drawShapeBody(ctx, kind, rect)
    drawAlignedLabel(ctx, ctx.props.string("label", ""), rect)
}

/**
 * Lays a label out inside [rect] using the element's own `align`, `textColor`
 * and `shadow` properties.
 *
 * [drawShadowedText] positions from a top-left offset, so the measuring and
 * centring that every labelled element needs lives here rather than being
 * repeated at each call site.
 */
private fun DrawScope.drawAlignedLabel(ctx: ElementRenderContext, text: String, rect: Rect) {
    if (text.isBlank()) return
    val color = Color(ctx.props.color("textColor", 0xFFE0E0E0))
    val style = ctx.textStyle(color)
    val layout = ctx.textMeasurer.measure(text = text, style = style, maxLines = 1)

    val x = when (ctx.props.string("align", "center")) {
        "left" -> rect.left + ctx.px(2)
        "right" -> rect.right - layout.size.width - ctx.px(2)
        else -> rect.left + (rect.width - layout.size.width) / 2f
    }
    val y = rect.top + (rect.height - layout.size.height) / 2f

    drawShadowedText(
        measurer = ctx.textMeasurer,
        text = text,
        topLeft = Offset(x, y),
        style = style,
        shadowColor = Color(0xFF3F3F3F),
        shadow = ctx.props.bool("shadow", true),
    )
}

private fun DrawScope.drawShapeBody(ctx: ElementRenderContext, kind: ShapeKind, rect: Rect) {
    val fillMode = ctx.props.string("fillMode", "solid")
    val opacity = ctx.props.float("opacity", 1f).coerceIn(0f, 1f)
    val fill = Color(ctx.props.color("fillColor", 0xFF56B84B))
    val strokeWidth = ctx.px(ctx.props.int("strokeWidth", 1))
    val stroke = Color(ctx.props.color("strokeColor", 0xFF000000))

    val brush: Brush? = when (fillMode) {
        "none" -> null
        "gradient" -> {
            val end = Color(ctx.props.color("gradientColor", 0xFF1E6F3A))
            gradientBrush(rect, ctx.props.int("gradientAngle", 90), fill, end)
        }
        else -> null
    }

    when (kind) {
        ShapeKind.ELLIPSE -> {
            if (fillMode != "none") {
                if (brush != null) {
                    drawOval(brush, rect.topLeft, rect.size, alpha = opacity)
                } else {
                    drawOval(fill, rect.topLeft, rect.size, alpha = opacity)
                }
            }
            if (strokeWidth > 0f) {
                drawOval(stroke, rect.topLeft, rect.size, alpha = opacity, style = Stroke(strokeWidth))
            }
        }

        ShapeKind.ROUNDED_RECTANGLE -> {
            // Clamped to half the shorter side: a radius larger than that is
            // geometrically meaningless and renders as a lozenge anyway.
            val radius = ctx.px(ctx.props.int("cornerRadius", 6))
                .coerceAtMost(minOf(rect.width, rect.height) / 2f)
            val corner = androidx.compose.ui.geometry.CornerRadius(radius, radius)
            if (fillMode != "none") {
                if (brush != null) {
                    drawRoundRect(brush, rect.topLeft, rect.size, corner, alpha = opacity)
                } else {
                    drawRoundRect(fill, rect.topLeft, rect.size, corner, alpha = opacity)
                }
            }
            if (strokeWidth > 0f) {
                drawRoundRect(stroke, rect.topLeft, rect.size, corner, alpha = opacity, style = Stroke(strokeWidth))
            }
        }

        else -> {
            val path = polygonPath(
                rect = rect,
                points = kind.outline(
                    sides = ctx.props.int("sides", 6),
                    innerRadius = ctx.props.float("innerRadius", 0.5f),
                ),
            ) ?: return
            if (fillMode != "none") {
                if (brush != null) drawPath(path, brush, alpha = opacity) else drawPath(path, fill, alpha = opacity)
            }
            if (strokeWidth > 0f) {
                drawPath(path, stroke, alpha = opacity, style = Stroke(strokeWidth))
            }
        }
    }
}

/** Maps 0..1 outline points onto [rect] and closes the path. */
private fun polygonPath(rect: Rect, points: List<Pair<Float, Float>>): Path? {
    if (points.size < 3) return null
    return Path().apply {
        points.forEachIndexed { index, (fx, fy) ->
            val x = rect.left + fx * rect.width
            val y = rect.top + fy * rect.height
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/** A linear gradient across [rect] at [angleDegrees], measured clockwise from east. */
private fun gradientBrush(rect: Rect, angleDegrees: Int, from: Color, to: Color): Brush {
    val radians = angleDegrees * PI_OVER_180
    // Half the diagonal reaches every corner whatever the angle, so the
    // gradient always spans the whole shape rather than banding at the edges.
    val reach = maxOf(rect.width, rect.height) / 2f
    val dx = cos(radians) * reach
    val dy = sin(radians) * reach
    return Brush.linearGradient(
        colors = listOf(from, to),
        start = Offset(rect.center.x - dx, rect.center.y - dy),
        end = Offset(rect.center.x + dx, rect.center.y + dy),
    )
}

private const val PI_OVER_180 = 0.017453292f

// ---------------------------------------------------------------------------
// Animated images
// ---------------------------------------------------------------------------

/**
 * Paints one frame of an `image.animated` element's strip.
 *
 * The strip is a single tall bitmap, so "playing" it is just choosing which
 * horizontal band to blit - the same thing Minecraft does with the texture at
 * runtime, which is why the editor preview and the game agree.
 */
fun DrawScope.drawAnimatedImage(ctx: ElementRenderContext) {
    val rect = ctx.rect
    val assetId = ctx.props.texture("texture")
    val bitmap = ctx.textures.resolve(assetId)
    val asset = ctx.project.texture(assetId)

    if (bitmap == null || asset == null) {
        checkerboard(rect, Color(ctx.props.color("placeholderColor", 0xFF404040)), Color(0xFF2E2E2E), ctx.px(4))
        strokeRect(rect, Color(0x66FFFFFF), ctx.px(1))
        // A film-strip mark, so an empty animated slot is not mistaken for an
        // empty still one.
        val notch = ctx.px(2)
        var y = rect.top + notch
        while (y + notch < rect.bottom) {
            fillRect(Rect(rect.left + notch, y, rect.left + notch * 2, y + notch), Color(0x88FFFFFF))
            fillRect(Rect(rect.right - notch * 2, y, rect.right - notch, y + notch), Color(0x88FFFFFF))
            y += notch * 3
        }
        return
    }

    val frames = effectiveFrameCount(ctx, asset)
    val frameHeight = if (frames > 1) bitmap.height / frames else bitmap.height
    if (frameHeight <= 0) return

    val index = currentFrameIndex(ctx, asset, frames)
    val source = Rect(
        left = 0f,
        top = (index * frameHeight).toFloat(),
        right = bitmap.width.toFloat(),
        bottom = ((index + 1) * frameHeight).toFloat(),
    )

    drawImageRegionFitted(
        image = bitmap,
        source = source,
        dest = rect,
        fit = ctx.props.string("fit", "contain"),
        alpha = ctx.props.float("opacity", 1f),
        pixelated = ctx.props.bool("pixelated", true),
        tint = Color(ctx.props.color("tint", 0xFFFFFFFF)),
    )
}

/** Frame count, preferring the element's override over the asset's own. */
private fun effectiveFrameCount(ctx: ElementRenderContext, asset: TextureAsset): Int {
    val override = ctx.props.int("frameCount", 0)
    return if (override > 0) override else asset.frameCount.coerceAtLeast(1)
}

/**
 * Which frame the animation is showing at [ElementRenderContext.timeMillis].
 *
 * Uses the source's own per-frame delays when it had uneven ones, so a GIF
 * imported with variable timing previews at the cadence it was authored with
 * rather than the flat rate the `.mcmeta` will fall back to.
 */
private fun currentFrameIndex(ctx: ElementRenderContext, asset: TextureAsset, frames: Int): Int {
    if (frames <= 1) return 0
    if (!ctx.props.bool("playing", true)) return 0

    val delays = asset.frameDelaysMillis.takeIf { it.size == frames }
        ?: List(frames) { ctx.props.int("frameTime", asset.frameTimeTicks).coerceAtLeast(1) * TICK_MILLIS }

    val total = delays.sum().coerceAtLeast(1)
    val playback = ctx.props.string("playback", "forward")
    val looping = ctx.props.bool("loop", true)

    // Ping-pong plays the sequence forwards then backwards, so one full period
    // is two passes with the endpoints not repeated.
    val period = if (playback == "ping_pong") total * 2 else total
    val elapsed = if (looping) {
        (ctx.timeMillis % period).toInt()
    } else {
        ctx.timeMillis.coerceAtMost(period.toLong() - 1).toInt()
    }

    val (position, reversed) = if (playback == "ping_pong" && elapsed >= total) {
        (period - 1 - elapsed) to false
    } else {
        elapsed to (playback == "reverse")
    }

    var remaining = position.coerceIn(0, total - 1)
    var index = 0
    while (index < frames - 1 && remaining >= delays[index]) {
        remaining -= delays[index]
        index++
    }
    return if (reversed) frames - 1 - index else index
}

private const val TICK_MILLIS = 50

// ---------------------------------------------------------------------------
// Custom elements
// ---------------------------------------------------------------------------

/**
 * Paints a `custom.element`: whatever the user says it is.
 *
 * With no vanilla appearance to imitate, it draws what it actually knows - the
 * texture if one is set, otherwise a plain filled box carrying the type name -
 * so the canvas shows something honest rather than a generic placeholder.
 */
fun DrawScope.drawCustomElement(ctx: ElementRenderContext) {
    val rect = ctx.rect
    val opacity = ctx.props.float("opacity", 1f).coerceIn(0f, 1f)
    val radius = ctx.px(ctx.props.int("cornerRadius", 0))
        .coerceAtMost(minOf(rect.width, rect.height) / 2f)

    val assetId = ctx.props.texture("texture")
    val bitmap = ctx.textures.resolve(assetId)
    if (bitmap != null) {
        val asset = ctx.project.texture(assetId)
        drawImageFitted(
            image = bitmap,
            dest = rect,
            fit = ctx.props.string("textureFit", "nine_slice"),
            insets = asset?.nineSlice ?: com.mcguidesigner.core.model.Insets.Zero,
            alpha = opacity,
            pixelated = true,
        )
    } else {
        val background = Color(ctx.props.color("background", 0xC0303030))
        if (radius > 0f) {
            pixelRoundRect(rect, background.copy(alpha = background.alpha * opacity), radius)
        } else {
            fillRect(rect, background.copy(alpha = background.alpha * opacity))
        }
    }

    val borderWidth = ctx.px(ctx.props.int("borderWidth", 1))
    if (borderWidth > 0f) {
        strokeRect(rect, Color(ctx.props.color("borderColor", 0xFF000000)), borderWidth)
    }

    // The label falls back to the type name so a custom element is always
    // identifiable on a busy canvas.
    drawAlignedLabel(
        ctx = ctx,
        text = ctx.props.string("label", "").ifBlank { ctx.props.string("customType", "custom") },
        rect = rect,
    )
}

/**
 * Draws [source] out of [image] into [dest], honouring [fit].
 *
 * A region-aware sibling of [drawImageFitted], needed because an animated
 * element blits one band of a tall strip rather than the whole bitmap.
 */
private fun DrawScope.drawImageRegionFitted(
    image: androidx.compose.ui.graphics.ImageBitmap,
    source: Rect,
    dest: Rect,
    fit: String,
    alpha: Float,
    pixelated: Boolean,
    tint: Color,
) {
    val sourceWidth = source.width
    val sourceHeight = source.height
    if (sourceWidth <= 0f || sourceHeight <= 0f) return

    val target = when (fit) {
        "stretch" -> dest
        "cover" -> {
            val scale = maxOf(dest.width / sourceWidth, dest.height / sourceHeight)
            centred(dest, sourceWidth * scale, sourceHeight * scale)
        }
        else -> {
            val scale = minOf(dest.width / sourceWidth, dest.height / sourceHeight)
            centred(dest, sourceWidth * scale, sourceHeight * scale)
        }
    }

    clipRect(dest.left, dest.top, dest.right, dest.bottom) {
        drawImage(
            image = image,
            srcOffset = androidx.compose.ui.unit.IntOffset(source.left.toInt(), source.top.toInt()),
            srcSize = androidx.compose.ui.unit.IntSize(sourceWidth.toInt(), sourceHeight.toInt()),
            dstOffset = androidx.compose.ui.unit.IntOffset(target.left.toInt(), target.top.toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(
                target.width.toInt().coerceAtLeast(1),
                target.height.toInt().coerceAtLeast(1),
            ),
            alpha = alpha,
            colorFilter = if (tint == Color.White) {
                null
            } else {
                androidx.compose.ui.graphics.ColorFilter.tint(
                    tint,
                    androidx.compose.ui.graphics.BlendMode.Modulate,
                )
            },
            filterQuality = if (pixelated) {
                androidx.compose.ui.graphics.FilterQuality.None
            } else {
                androidx.compose.ui.graphics.FilterQuality.Medium
            },
        )
    }
}

private fun centred(within: Rect, width: Float, height: Float) = Rect(
    offset = Offset(
        within.left + (within.width - width) / 2f,
        within.top + (within.height - height) / 2f,
    ),
    size = Size(width, height),
)
