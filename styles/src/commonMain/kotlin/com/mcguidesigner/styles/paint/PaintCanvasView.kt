package com.mcguidesigner.styles.paint

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.mcguidesigner.styles.theme.LocalSkinPalette
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
fun PaintCanvasView(state: PaintState, modifier: Modifier = Modifier) {
    val palette = LocalSkinPalette.current
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    // Read so that the canvas recomposes when the pixels change. The value
    // itself is unused - see PaintState.revision.
    @Suppress("UNUSED_EXPRESSION")
    state.revision

    Box(
        modifier
            .fillMaxSize()
            .background(palette.chromeBackground)
            .pointerInput(state.document, state.tool) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    viewport = IntSize(size.width, size.height)
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

                    if (strokeTool && fit.contains(canvasPoint, state)) {
                        state.strokeStart(canvasPoint.x, canvasPoint.y)
                        painting = true
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
                            } else if (!cancelled && state.tool == PaintTool.PAN) {
                                state.pan += change.positionChange()
                            }
                            change.consume()
                        }
                    }

                    when {
                        painting -> state.strokeEnd(lastCanvasPoint.x, lastCanvasPoint.y)
                        cancelled -> Unit
                        !state.tool.isStroke -> {
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
            viewport = IntSize(size.width.roundToInt(), size.height.roundToInt())
            val fit = fitOf(state, size.width.roundToInt(), size.height.roundToInt())
            drawSheet(state, fit)
        }
    }
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

private fun DrawScope.drawSheet(state: PaintState, fit: CanvasFit) {
    val document = state.document
    val width = document.width * fit.scale
    val height = document.height * fit.scale

    // A shadow so the sheet reads as a sheet rather than as a hole in the grey.
    drawRect(
        color = Color(0x33000000),
        topLeft = Offset(fit.left + 2f, fit.top + 4f),
        size = Size(width, height),
    )

    val image = state.surface?.image()
    if (image != null) {
        drawImage(
            image = image,
            dstOffset = IntOffset(fit.left.roundToInt(), fit.top.roundToInt()),
            dstSize = IntSize(width.roundToInt(), height.roundToInt()),
            filterQuality = if (state.pixelated) FilterQuality.None else FilterQuality.Medium,
        )
    } else {
        drawRect(Color.White, Offset(fit.left, fit.top), Size(width, height))
    }

    if (state.showGrid && fit.scale > 0.05f) {
        val step = state.gridStep * fit.scale
        if (step >= 6f) {
            var x = fit.left
            while (x <= fit.left + width) {
                drawLine(Color(0x22000000), Offset(x, fit.top), Offset(x, fit.top + height))
                x += step
            }
            var y = fit.top
            while (y <= fit.top + height) {
                drawLine(Color(0x22000000), Offset(fit.left, y), Offset(fit.left + width, y))
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
