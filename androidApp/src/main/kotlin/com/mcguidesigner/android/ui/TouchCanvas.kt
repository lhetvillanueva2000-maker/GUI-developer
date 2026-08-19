package com.mcguidesigner.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.editor.SnapResult
import com.mcguidesigner.core.model.ResizeHandle
import com.mcguidesigner.styles.canvas.CanvasTransform
import com.mcguidesigner.styles.canvas.GuiCanvas
import com.mcguidesigner.styles.render.TextureCache
import com.mcguidesigner.styles.theme.LocalSkinPalette
import kotlin.math.abs
import kotlin.math.sqrt

/** Touch-drag states. */
private enum class TouchMode { IDLE, PENDING, MOVE, RESIZE, PAN, TRANSFORM }

/** How far a finger may travel before a press stops counting as a tap. */
private const val TAP_SLOP_DP = 8f

/** Press duration that promotes a tap into a multi-select toggle. */
private const val LONG_PRESS_MS = 420L

/**
 * The canvas with touch input.
 *
 * The interaction model is deliberately *not* the desktop one:
 *
 * * one finger on empty space or an unselected element **pans** - on a phone
 *   the canvas is bigger than the viewport far more often than on a desktop,
 *   so panning has to be the cheapest gesture;
 * * one finger on an already-selected element **moves** it, which makes
 *   accidental drags essentially impossible;
 * * two fingers pinch to zoom and pan together;
 * * a long press toggles an element into the selection (multi-select without
 *   a modifier key);
 * * resize handles are sized in dp, not pixels, so they are thumb-sized on
 *   every density.
 */
@Composable
fun TouchDesignSurface(
    controller: EditorController,
    state: EditorState,
    textures: TextureCache,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    val density = LocalDensity.current
    var viewport by remember { mutableStateOf(Size.Zero) }
    var snapFeedback by remember { mutableStateOf(SnapResult.None) }

    val handleSize = with(density) { 20.dp.toPx() }
    val tapSlop = with(density) { TAP_SLOP_DP.dp.toPx() }

    val transform = CanvasTransform(
        zoom = state.zoom,
        panX = state.panX,
        panY = state.panY,
        viewport = viewport,
        canvas = state.project.canvas.size,
    )

    val liveState = rememberUpdatedState(state)
    val liveTransform = rememberUpdatedState(transform)

    Box(
        modifier
            .background(palette.chromeBackground)
            .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(controller) {
                touchGestureLoop(
                    controller = controller,
                    stateProvider = { liveState.value },
                    transformProvider = { liveTransform.value },
                    handleSize = handleSize,
                    tapSlop = tapSlop,
                    onSnapFeedback = { snapFeedback = it },
                )
            },
    ) {
        GuiCanvas(
            state = state,
            transform = transform,
            textures = textures,
            modifier = Modifier.fillMaxSize(),
            handleSize = handleSize,
            snapFeedback = snapFeedback,
        )
    }
}

private suspend fun PointerInputScope.touchGestureLoop(
    controller: EditorController,
    stateProvider: () -> EditorState,
    transformProvider: () -> CanvasTransform,
    handleSize: Float,
    tapSlop: Float,
    onSnapFeedback: (SnapResult) -> Unit,
) {
    var mode = TouchMode.IDLE
    var pressStart = Offset.Zero
    var pressTime = 0L
    var lastPosition = Offset.Zero
    var activeHandle: ResizeHandle? = null
    var candidateId: String? = null
    var lastPinchDistance = 0f
    var lastCentroid = Offset.Zero

    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            val state = stateProvider()
            val transform = transformProvider()

            // --- Two or more fingers: pinch-zoom and pan together ---------
            if (pressed.size >= 2) {
                val centroid = pressed.centroid()
                val distance = pressed.spread(centroid)
                if (mode != TouchMode.TRANSFORM) {
                    // Abandon whatever the single finger was doing; a second
                    // finger always means "navigate", never "edit".
                    if (mode == TouchMode.MOVE || mode == TouchMode.RESIZE) controller.endGesture()
                    mode = TouchMode.TRANSFORM
                    lastPinchDistance = distance
                    lastCentroid = centroid
                    onSnapFeedback(SnapResult.None)
                } else {
                    if (lastPinchDistance > 1f && distance > 1f) {
                        controller.zoomBy(distance / lastPinchDistance)
                    }
                    val panDelta = centroid - lastCentroid
                    controller.panBy(panDelta.x, panDelta.y)
                    lastPinchDistance = distance
                    lastCentroid = centroid
                }
                event.changes.forEach { it.consume() }
                continue
            }

            val change = event.changes.firstOrNull() ?: continue

            when (event.type) {
                PointerEventType.Press -> {
                    if (mode == TouchMode.TRANSFORM) {
                        // Second finger lifted then re-pressed; stay in
                        // transform mode until every finger is up.
                        change.consume()
                        continue
                    }
                    pressStart = change.position
                    lastPosition = change.position
                    pressTime = change.uptimeMillis
                    activeHandle = null
                    candidateId = null

                    val canvasPoint = transform.toCanvas(change.position)

                    // Placement armed from the component sheet.
                    val pending = state.pendingPlacementType
                    if (pending != null) {
                        controller.addElement(pending, canvasPoint, centreOnPoint = true)
                        mode = TouchMode.IDLE
                        change.consume()
                        continue
                    }

                    // Handles win, and they are thumb-sized on touch.
                    val primaryBounds = state.primarySelection?.let { state.absoluteBounds[it] }
                    if (primaryBounds != null && state.selection.size == 1) {
                        val handle = transform.handleAt(primaryBounds, change.position, handleSize * 1.6f)
                        if (handle != null) {
                            activeHandle = handle
                            controller.beginResize(state.primarySelection!!, handle)
                            mode = TouchMode.RESIZE
                            change.consume()
                            continue
                        }
                    }

                    val hit = controller.hitTest(canvasPoint)
                    candidateId = hit?.id
                    mode = TouchMode.PENDING
                    change.consume()
                }

                PointerEventType.Move -> {
                    if (mode == TouchMode.TRANSFORM || mode == TouchMode.IDLE) {
                        change.consume()
                        continue
                    }

                    val total = change.position - pressStart
                    val frame = change.position - lastPosition
                    lastPosition = change.position

                    if (mode == TouchMode.PENDING) {
                        if (sqrt(total.x * total.x + total.y * total.y) < tapSlop) {
                            change.consume()
                            continue
                        }
                        // Dragging an element that is already selected moves
                        // it; anything else pans the canvas.
                        mode = if (candidateId != null && candidateId in state.selection) {
                            controller.beginDrag(state.selection)
                            TouchMode.MOVE
                        } else {
                            TouchMode.PAN
                        }
                    }

                    when (mode) {
                        TouchMode.PAN -> controller.panBy(frame.x, frame.y)

                        TouchMode.MOVE -> {
                            val delta = transform.deltaToCanvas(total.x, total.y)
                            onSnapFeedback(controller.dragSelectionTo(delta.x, delta.y))
                        }

                        TouchMode.RESIZE -> activeHandle?.let { handle ->
                            val id = state.primarySelection ?: return@let
                            val delta = transform.deltaToCanvas(total.x, total.y)
                            controller.resizeBy(id, handle, delta.x, delta.y)
                        }

                        else -> Unit
                    }
                    change.consume()
                }

                PointerEventType.Release -> {
                    val heldFor = change.uptimeMillis - pressTime
                    when (mode) {
                        TouchMode.PENDING -> {
                            val id = candidateId
                            when {
                                id == null -> controller.select(null)
                                heldFor >= LONG_PRESS_MS -> controller.select(id, toggle = true)
                                else -> controller.select(id)
                            }
                        }

                        TouchMode.MOVE, TouchMode.RESIZE -> controller.endGesture()

                        else -> Unit
                    }
                    onSnapFeedback(SnapResult.None)
                    mode = TouchMode.IDLE
                    activeHandle = null
                    candidateId = null
                    change.consume()
                }

                else -> Unit
            }
        }
    }
}

private fun List<PointerInputChange>.centroid(): Offset {
    if (isEmpty()) return Offset.Zero
    var x = 0f
    var y = 0f
    forEach {
        x += it.position.x
        y += it.position.y
    }
    return Offset(x / size, y / size)
}

/** Mean distance of the pointers from their centroid - the pinch magnitude. */
private fun List<PointerInputChange>.spread(centroid: Offset): Float {
    if (size < 2) return 0f
    var total = 0f
    forEach {
        val dx = it.position.x - centroid.x
        val dy = it.position.y - centroid.y
        total += sqrt(dx * dx + dy * dy)
    }
    return total / size
}
