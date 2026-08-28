package com.mcguidesigner.styles.paint

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.MotionLevel
import kotlin.math.roundToInt

/**
 * The canvas: a white sheet on a grey surround, with the painting on it.
 *
 * Two jobs, and they are more separable than they look. Drawing is a single
 * `drawImage` of the composited buffer, scaled by the zoom - the compositor has
 * already done everything. Input is the whole rest of the file, because on a
 * touchscreen the difference between "a stroke" and "a two-finger pan" is not
 * known until the second finger arrives, and by then the first has already been
 * painting for a frame or two.
 *
 * That is handled by *undoing* rather than by waiting. A single finger paints
 * immediately, which is what makes drawing feel direct; when a second finger
 * lands, the stroke in progress is cancelled and rolled back before the gesture
 * becomes a transform. Waiting to see whether a second finger arrives would add
 * a delay to every single stroke to save a mistake that happens rarely.
 */
@Composable
fun PaintCanvasView(
    state: PaintState,
    modifier: Modifier = Modifier,
    /** 0..1 entrance progress; the sheet scales up into place. */
    entrance: Float = 1f,
    motion: MotionLevel = MotionLevel.FULL,
) {
    val palette = LocalSkinPalette.current
    val scope = rememberCoroutineScope()

    // `state.revision` is deliberately *not* read here.
    //
    // Reading it in the composable body makes every frame of a stroke a full
    // recomposition - composition, layout, then draw - for a change that only
    // ever affects what is painted. Read inside the draw lambda instead, it
    // invalidates the draw phase alone, so a moving finger goes straight from
    // the pointer event to pixels on screen with nothing in between.

    // A clock for the trail's travelling colours, running only while a gesture
    // is on screen. An always-on infinite transition would keep the frame loop
    // awake for the whole session to animate something that is usually not
    // there.
    var shimmer by remember { mutableFloatStateOf(0f) }
    val gestureActive = state.overlay != PaintOverlay.NONE
    LaunchedEffect(gestureActive, motion.allowsLoops) {
        if (!gestureActive || !motion.allowsLoops) {
            shimmer = 0f
            return@LaunchedEffect
        }
        val started = withFrameMillis { it }
        while (true) {
            withFrameMillis { now ->
                shimmer = (((now - started) % TRAIL_CYCLE_MS) / TRAIL_CYCLE_MS.toFloat())
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(palette.chromeBackground)
            .pointerInput(state.document, state.tool) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    // A long operation is rewriting the layer. Touching the
                    // canvas now would start a stroke on pixels that are being
                    // replaced underneath it.
                    if (state.busy != null) {
                        first.consume()
                        return@awaitEachGesture
                    }
                    val fit = fitOf(state, size.width, size.height)

                    var pointers = 1
                    var painting = false
                    var cancelled = false
                    var lastCentroid = first.position
                    var lastSpan = 0f

                    val canvasPoint = fit.toCanvas(first.position, state)
                    var lastCanvasPoint = canvasPoint
                    val strokeTool = state.tool.isStroke && state.tool != PaintTool.PAN
                    val gestureTool = state.tool.isGesture
                    var gesturing = false

                    if (strokeTool && fit.contains(canvasPoint, state)) {
                        state.strokeStart(canvasPoint.x, canvasPoint.y)
                        painting = true
                    } else if (gestureTool && fit.contains(canvasPoint, state)) {
                        state.gestureStart(canvasPoint.x, canvasPoint.y)
                        gesturing = true
                    }
                    first.consume()

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val down = event.changes.filter { it.pressed }
                        if (down.isEmpty()) break

                        if (down.size > 1 && pointers == 1) {
                            // A second finger arrived. Roll back anything the
                            // first one drew: it was the beginning of a pinch,
                            // not the beginning of a stroke.
                            if (painting && !cancelled) {
                                state.cancelStroke()
                                cancelled = true
                                painting = false
                            }
                            if (gesturing) {
                                state.gestureCancel()
                                cancelled = true
                                gesturing = false
                            }
                            lastCentroid = centroid(down.map { it.position })
                            lastSpan = span(down.map { it.position })
                        }
                        pointers = down.size

                        if (pointers > 1) {
                            val centre = centroid(down.map { it.position })
                            val distance = span(down.map { it.position })
                            if (lastSpan > 0f && distance > 0f) {
                                val factor = distance / lastSpan
                                state.zoomAround(centre, factor, size.width, size.height)
                            }
                            state.pan += centre - lastCentroid
                            lastCentroid = centre
                            lastSpan = distance
                            down.forEach { it.consume() }
                        } else {
                            val change = down.first()
                            val p = fit.toCanvas(change.position, state)
                            lastCanvasPoint = p
                            if (painting) {
                                state.strokeMove(p.x, p.y)
                            } else if (gesturing) {
                                state.gestureMove(p.x, p.y)
                            } else if (!cancelled && state.tool == PaintTool.PAN) {
                                state.pan += change.positionChange()
                            }
                            change.consume()
                        }
                    }

                    when {
                        painting -> state.strokeEnd(lastCanvasPoint.x, lastCanvasPoint.y)
                        gesturing -> scope.launch { state.gestureEnd() }
                        cancelled -> Unit
                        !state.tool.isStroke && !state.tool.isGesture -> {
                            if (fit.contains(canvasPoint, state)) {
                                scope.launch {
                                    state.tap(canvasPoint.x.roundToInt(), canvasPoint.y.roundToInt())
                                }
                            }
                        }
                    }
                }
            },
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            // Read here, in the draw phase: this is what schedules the redraw.
            @Suppress("UNUSED_EXPRESSION")
            state.revision
            val fit = fitOf(state, size.width.roundToInt(), size.height.roundToInt())
            drawSheet(state, fit, entrance)
            // Read so the trail repaints as it travels.
            @Suppress("UNUSED_EXPRESSION")
            state.overlayRevision
            drawGestureOverlay(state, fit, shimmer, palette.accent)
        }
    }
}

/** How long the trail's colours take to travel once around, in milliseconds. */
private const val TRAIL_CYCLE_MS = 2200L

/**
 * What the magic eraser and the shape tool show while a finger is down.
 *
 * The trail is the whole point of the eraser being a drag. Nothing is removed
 * until the finger lifts, so without a trail the gesture is invisible: you
 * scribble over an object, see nothing at all, let go, and something vanishes.
 * The trail is the tool telling you what it is about to take.
 *
 * It is drawn in travelling colour rather than one flat highlight because a
 * single colour over a photograph disappears wherever the photograph happens to
 * be that colour. A band that runs through the whole hue circle cannot be
 * camouflaged by anything underneath it - and it also reads instantly as "the
 * app is considering this", which a plain line does not.
 *
 * Three passes: a wide soft glow, the colour band, and a bright core. That is
 * what gives it depth rather than looking like a coloured pencil line.
 */
private fun DrawScope.drawGestureOverlay(
    state: PaintState,
    fit: CanvasFit,
    shimmer: Float,
    accent: Color,
) {
    if (state.overlay == PaintOverlay.NONE) return
    val path = state.overlayPath
    if (path.isEmpty()) return

    fun toScreen(point: com.mcguidesigner.core.paint.StrokePoint) =
        Offset(fit.left + point.x * fit.scale, fit.top + point.y * fit.scale)

    if (state.overlay == PaintOverlay.SHAPE) {
        // The recognised shape, previewed over the rough drag, so the guess is
        // visible before committing to it.
        drawTrail(path.map(::toScreen), shimmer, width = 5f, glow = 0.35f)
        state.shapeGuess?.let { guess ->
            if (guess.shape == com.mcguidesigner.core.paint.RecognisedShape.FREEHAND) return@let
            val outline = guess.points.map(::toScreen)
            if (outline.size < 2) return@let
            val closed = if (guess.closed) outline + outline.first() else outline
            for (i in 0 until closed.size - 1) {
                drawLine(accent.copy(alpha = 0.9f), closed[i], closed[i + 1], strokeWidth = 3f, cap = StrokeCap.Round)
            }
            outline.forEach { corner ->
                drawCircle(accent, radius = 5f, center = corner)
                drawCircle(Color.White, radius = 2f, center = corner)
            }
        }
        return
    }

    val width = (state.activeSize * fit.scale).coerceIn(10f, 220f)
    drawTrail(path.map(::toScreen), shimmer, width = width, glow = 0.55f)
}

/**
 * A band of travelling colour along [points].
 *
 * The hue depends on distance along the path *plus* the clock, so the colours
 * slide along the stroke rather than sitting still on it - which is what makes
 * it read as active rather than merely coloured.
 *
 * Drawn as a fixed number of segments rather than one per point. A scribble
 * across a canvas arrives as several hundred points, and three coloured passes
 * over every one of them is a couple of thousand draw calls per frame for a
 * decoration - which made the trail itself the thing that stuttered, on a
 * gesture whose whole purpose is to feel responsive. Sampling the path at
 * [TRAIL_SEGMENTS] evenly spaced positions gives the same band: the segments
 * are a few pixels long on screen at that count, well under the width of the
 * line being drawn, so nothing is visibly straightened.
 */
private fun DrawScope.drawTrail(
    points: List<Offset>,
    shimmer: Float,
    width: Float,
    glow: Float,
) {
    if (points.size < 2) {
        // A tap, or the very first event of a drag: still show something, or
        // the tool looks dead for the first frame.
        points.firstOrNull()?.let { drawCircle(hueAt(shimmer), radius = width / 2f, center = it) }
        return
    }

    // Cumulative length, so the colour advances with distance rather than with
    // the number of events - otherwise a slow drag rainbows faster than a quick
    // one over the same line.
    var travelled = 0f
    val distances = FloatArray(points.size)
    for (i in 1 until points.size) {
        travelled += (points[i] - points[i - 1]).getDistance()
        distances[i] = travelled
    }
    val total = travelled.coerceAtLeast(1f)

    // The sample indices, chosen once and shared by all three passes. Always
    // includes the first and last point, so the trail starts and ends exactly
    // where the finger did.
    val steps = minOf(TRAIL_SEGMENTS, points.size - 1)
    val sampled = IntArray(steps + 1)
    for (s in 0..steps) {
        sampled[s] = ((points.size - 1).toLong() * s / steps).toInt()
    }

    fun pass(strokeWidth: Float, alpha: Float, white: Boolean) {
        for (s in 1..steps) {
            val a = sampled[s - 1]
            val b = sampled[s]
            if (a == b) continue
            val colour = if (white) {
                Color.White.copy(alpha = alpha)
            } else {
                hueAt(distances[b] / total * 1.6f + shimmer).copy(alpha = alpha)
            }
            drawLine(
                color = colour,
                start = points[a],
                end = points[b],
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }

    // Pass one: the glow. Wide, soft, low alpha.
    pass(width * 1.9f, glow * 0.35f, white = false)
    // Pass two: the band itself.
    pass(width, 0.85f, white = false)
    // Pass three: a bright core, which is what stops it looking like a fat
    // crayon and makes it look lit.
    pass((width * 0.28f).coerceAtLeast(1.5f), 0.55f, white = true)
}

/**
 * How many segments the trail is drawn with, however long the path is.
 *
 * Sixty-four is enough that the joins are invisible at any plausible trail
 * width - a scribble spanning a whole phone screen samples every twenty pixels,
 * against a band that is rarely narrower than ten - and it caps the trail's
 * cost at 192 draw calls a frame no matter how long the scribble grows.
 */
private const val TRAIL_SEGMENTS = 64

/** A saturated colour at [turn] revolutions around the hue circle. */
private fun hueAt(turn: Float): Color {
    val degrees = ((turn % 1f) + 1f) % 1f * 360f
    return Color(hsbToRgb(degrees, 0.85f, 1f))
}

private fun centroid(points: List<Offset>): Offset {
    if (points.isEmpty()) return Offset.Zero
    var x = 0f
    var y = 0f
    points.forEach { x += it.x; y += it.y }
    return Offset(x / points.size, y / points.size)
}

private fun span(points: List<Offset>): Float {
    if (points.size < 2) return 0f
    val c = centroid(points)
    var total = 0f
    points.forEach { total += (it - c).getDistance() }
    return total / points.size
}

/**
 * Where the sheet sits on screen.
 *
 * Recomputed per frame from the zoom and pan rather than stored, so there is
 * exactly one definition of the mapping and the drawing and the hit testing
 * cannot disagree about it. Two copies of this arithmetic is how a brush ends
 * up painting a few pixels away from where the finger is.
 */
class CanvasFit(val left: Float, val top: Float, val scale: Float)

private fun fitOf(state: PaintState, viewWidth: Int, viewHeight: Int): CanvasFit {
    val document = state.document
    if (viewWidth <= 0 || viewHeight <= 0) return CanvasFit(0f, 0f, 1f)
    // The zoom is relative to "fits the window", which is what a person means
    // by 100% on a phone. An absolute pixel scale would open a 4000-pixel
    // canvas at four times the screen width.
    val base = minOf(
        viewWidth.toFloat() / document.width,
        viewHeight.toFloat() / document.height,
    ) * 0.92f
    val scale = base * state.zoom
    val width = document.width * scale
    val height = document.height * scale
    return CanvasFit(
        left = (viewWidth - width) / 2f + state.pan.x,
        top = (viewHeight - height) / 2f + state.pan.y,
        scale = scale,
    )
}

private fun CanvasFit.toCanvas(screen: Offset, state: PaintState): Offset =
    Offset((screen.x - left) / scale, (screen.y - top) / scale)

private fun CanvasFit.contains(canvasPoint: Offset, state: PaintState): Boolean =
    canvasPoint.x >= -state.activeSize && canvasPoint.y >= -state.activeSize &&
        canvasPoint.x <= state.document.width + state.activeSize &&
        canvasPoint.y <= state.document.height + state.activeSize

private fun DrawScope.drawSheet(state: PaintState, fit: CanvasFit, entrance: Float) {
    val document = state.document
    // The sheet grows into place rather than appearing at full size - see the
    // entrance in PaintScreen. At rest this is exactly 1 and costs nothing.
    val grow = 0.88f + 0.12f * entrance.coerceIn(0f, 1f)
    val width = document.width * fit.scale * grow
    val height = document.height * fit.scale * grow

    // A shadow so the sheet reads as a sheet rather than as a hole in the grey.
    drawRect(
        color = Color(0x33000000),
        topLeft = Offset(fit.left + 2f, fit.top + 4f),
        size = Size(width, height),
    )

    val quality = if (state.pixelated) FilterQuality.None else FilterQuality.Medium
    val image = state.surface?.image()
    if (image != null) {
        drawImage(
            image = image,
            dstOffset = IntOffset(fit.left.roundToInt(), fit.top.roundToInt()),
            dstSize = IntSize(width.roundToInt(), height.roundToInt()),
            filterQuality = quality,
        )
    } else {
        drawRect(Color.White, Offset(fit.left, fit.top), Size(width, height))
    }

    // The stroke in progress, over the part of the canvas bitmap that is
    // deliberately stale. See PaintState's patch section: the big bitmap is
    // not written to while a finger is down, because touching it at all costs
    // a full re-upload of the whole thing.
    if (state.patchActive) {
        state.patchSurface?.let { patch ->
            drawImage(
                image = patch.image(),
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(state.patchWidth, state.patchHeight),
                dstOffset = IntOffset(
                    (fit.left + state.patchLeft * fit.scale).roundToInt(),
                    (fit.top + state.patchTop * fit.scale).roundToInt(),
                ),
                dstSize = IntSize(
                    (state.patchWidth * fit.scale).roundToInt().coerceAtLeast(1),
                    (state.patchHeight * fit.scale).roundToInt().coerceAtLeast(1),
                ),
                filterQuality = quality,
            )
        }
    }

    if (state.showGrid && fit.scale > 0.05f) {
        val step = state.gridStep * fit.scale
        if (step >= 6f) {
            // Clipped to the window, not to the sheet. Zoomed in, the sheet is
            // several times the size of the screen, and drawing the lines that
            // fall outside it is hundreds of draw calls a frame that land
            // nowhere - the cost of the grid used to rise with the zoom, which
            // is exactly backwards.
            val viewRight = size.width
            val viewBottom = size.height
            val firstX = fit.left + kotlin.math.floor(((0f - fit.left) / step).coerceAtLeast(0f)) * step
            var x = firstX
            val lastX = minOf(fit.left + width, viewRight)
            while (x <= lastX) {
                if (x >= 0f) {
                    drawLine(
                        Color(0x22000000),
                        Offset(x, maxOf(fit.top, 0f)),
                        Offset(x, minOf(fit.top + height, viewBottom)),
                    )
                }
                x += step
            }
            val firstY = fit.top + kotlin.math.floor(((0f - fit.top) / step).coerceAtLeast(0f)) * step
            var y = firstY
            val lastY = minOf(fit.top + height, viewBottom)
            while (y <= lastY) {
                if (y >= 0f) {
                    drawLine(
                        Color(0x22000000),
                        Offset(maxOf(fit.left, 0f), y),
                        Offset(minOf(fit.left + width, viewRight), y),
                    )
                }
                y += step
            }
        }
    }

    // The symmetry axis, so it is possible to see what is being mirrored.
    if (state.symmetry != SymmetryMode.OFF) {
        val cx = fit.left + width / 2f
        val cy = fit.top + height / 2f
        val guide = Color(0x66E0218A)
        if (state.symmetry != SymmetryMode.HORIZONTAL) {
            drawLine(guide, Offset(cx, fit.top), Offset(cx, fit.top + height), strokeWidth = 1.5f)
        }
        if (state.symmetry == SymmetryMode.HORIZONTAL || state.symmetry == SymmetryMode.QUAD) {
            drawLine(guide, Offset(fit.left, cy), Offset(fit.left + width, cy), strokeWidth = 1.5f)
        }
        if (state.symmetry == SymmetryMode.RADIAL) {
            drawCircle(guide, radius = 10f, center = Offset(cx, cy), style = Stroke(width = 1.5f))
        }
    }

    // A hairline round the sheet, which is what separates a white canvas from
    // a white background on a light theme.
    drawRect(
        color = Color(0x33000000),
        topLeft = Offset(fit.left, fit.top),
        size = Size(width, height),
        style = Stroke(width = 1f),
    )
}
