package com.mcguidesigner.desktop

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.editor.EditorTool
import com.mcguidesigner.core.editor.SnapResult
import com.mcguidesigner.core.model.IntPoint
import com.mcguidesigner.core.model.ResizeHandle
import com.mcguidesigner.styles.canvas.CanvasRuler
import com.mcguidesigner.styles.canvas.CanvasTransform
import com.mcguidesigner.styles.canvas.GuiCanvas
import com.mcguidesigner.styles.render.TextureCache
import com.mcguidesigner.styles.theme.LocalSkinPalette
import java.awt.Cursor

private const val RULER_THICKNESS = 18

/** What the current mouse drag is doing. */
private enum class DragMode { NONE, MOVE, RESIZE, MARQUEE, PAN, GUIDE }

/**
 * The design surface plus its desktop input handling.
 *
 * Mouse-first by design: hover highlighting, precise 1px drags, handle
 * resizing, marquee selection, middle/right-drag panning and Ctrl+wheel zoom.
 * The Android app implements the same operations with a completely different
 * gesture vocabulary.
 */
@Composable
fun DesignCanvasArea(
    controller: EditorController,
    state: EditorState,
    textures: TextureCache,
    modifier: Modifier = Modifier,
    /**
     * Whether the workspace around the canvas is painted.
     *
     * Left unpainted when the shell is showing wallpaper behind the editor:
     * the ring of workspace around the canvas sheet is the only place that
     * artwork is ever visible, since every dock above it is opaque.
     */
    opaqueWorkspace: Boolean = true,
) {
    val palette = LocalSkinPalette.current
    var viewport by remember { mutableStateOf(Size.Zero) }
    var snapFeedback by remember { mutableStateOf(SnapResult.None) }

    val transform = CanvasTransform(
        zoom = state.zoom,
        panX = state.panX,
        panY = state.panY,
        viewport = viewport,
        canvas = state.project.canvas.size,
    )

    // The gesture loop below runs for the lifetime of the composable, so it
    // reads live state through these holders rather than capturing it.
    val liveState = rememberUpdatedState(state)
    val liveTransform = rememberUpdatedState(transform)

    Column(
        if (opaqueWorkspace) modifier.background(palette.chromeBackground) else modifier,
    ) {
        if (state.showRulers) {
            Row(Modifier.fillMaxWidth().height(RULER_THICKNESS.dp)) {
                Spacer(Modifier.size(RULER_THICKNESS.dp).background(palette.chromePanel))
                CanvasRuler(transform, vertical = false, modifier = Modifier.fillMaxSize())
            }
        }

        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (state.showRulers) {
                CanvasRuler(
                    transform,
                    vertical = true,
                    modifier = Modifier.width(RULER_THICKNESS.dp).fillMaxHeight(),
                )
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
                    .pointerHoverIcon(cursorFor(state))
                    .pointerInput(controller) {
                        canvasGestureLoop(
                            controller = controller,
                            stateProvider = { liveState.value },
                            transformProvider = { liveTransform.value },
                            onSnapFeedback = { snapFeedback = it },
                        )
                    },
            ) {
                GuiCanvas(
                    state = state,
                    transform = transform,
                    textures = textures,
                    modifier = Modifier.fillMaxSize(),
                    handleSize = 9f,
                    snapFeedback = snapFeedback,
                    workspaceColor = if (opaqueWorkspace) palette.chromeBackground else Color.Transparent,
                )
            }
        }
    }
}

private fun cursorFor(state: EditorState): PointerIcon = when (state.tool) {
    EditorTool.PAN -> PointerIcon(Cursor(Cursor.HAND_CURSOR))
    EditorTool.MARQUEE -> PointerIcon(Cursor(Cursor.CROSSHAIR_CURSOR))
    EditorTool.PLACE -> PointerIcon(Cursor(Cursor.CROSSHAIR_CURSOR))
    else -> PointerIcon(Cursor(Cursor.DEFAULT_CURSOR))
}

/**
 * One long-lived pointer loop.
 *
 * Restarting `pointerInput` whenever the editor state changes would cancel
 * in-flight drags (selection changes on the very first press), so the loop is
 * keyed only on the controller and pulls fresh state on every event.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.canvasGestureLoop(
    controller: EditorController,
    stateProvider: () -> EditorState,
    transformProvider: () -> CanvasTransform,
    onSnapFeedback: (SnapResult) -> Unit,
) {
    var mode = DragMode.NONE
    var gestureStart = Offset.Zero
    var lastPosition = Offset.Zero
    var activeHandle: ResizeHandle? = null
    var additive = false

    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: continue
            val state = stateProvider()
            val transform = transformProvider()
            val shift = event.keyboardModifiers.isShiftPressed
            val ctrl = event.keyboardModifiers.isCtrlPressed

            when (event.type) {
                PointerEventType.Press -> {
                    gestureStart = change.position
                    lastPosition = change.position
                    additive = shift
                    val canvasPoint = transform.toCanvas(change.position)

                    mode = when {
                        event.buttons.isSecondaryPressed || event.buttons.isTertiaryPressed -> DragMode.PAN

                        state.pendingPlacementType != null -> {
                            controller.addElement(state.pendingPlacementType!!, canvasPoint, centreOnPoint = true)
                            DragMode.NONE
                        }

                        state.tool == EditorTool.PAN -> DragMode.PAN

                        state.tool == EditorTool.MARQUEE -> {
                            controller.beginMarquee(canvasPoint.x, canvasPoint.y)
                            DragMode.MARQUEE
                        }

                        else -> beginSelectionGesture(
                            controller, state, transform, change.position, canvasPoint, shift,
                        ) { handle -> activeHandle = handle }
                    }
                    change.consume()
                }

                PointerEventType.Move -> {
                    if (!change.pressed) {
                        // Hover highlight only matters with a mouse, so this
                        // lives in the desktop layer rather than the shared one.
                        val hovered = controller.hitTest(transform.toCanvas(change.position))
                        if (hovered?.id != state.hoveredId) controller.setHovered(hovered?.id)
                        continue
                    }

                    val totalDelta = change.position - gestureStart
                    val frameDelta = change.position - lastPosition
                    lastPosition = change.position

                    when (mode) {
                        DragMode.PAN -> controller.panBy(
                            frameDelta.x, frameDelta.y,
                            transform.viewport.width, transform.viewport.height,
                        )

                        DragMode.MOVE -> {
                            val delta = transform.deltaToCanvas(totalDelta.x, totalDelta.y)
                            onSnapFeedback(controller.dragSelectionTo(delta.x, delta.y))
                        }

                        DragMode.RESIZE -> activeHandle?.let { handle ->
                            val id = state.primarySelection ?: return@let
                            val delta = transform.deltaToCanvas(totalDelta.x, totalDelta.y)
                            controller.resizeBy(id, handle, delta.x, delta.y)
                        }

                        DragMode.MARQUEE -> {
                            val point = transform.toCanvas(change.position)
                            controller.updateMarquee(point.x, point.y)
                        }

                        else -> Unit
                    }
                    change.consume()
                }

                PointerEventType.Release -> {
                    when (mode) {
                        DragMode.MARQUEE -> controller.commitMarquee(additive)
                        DragMode.NONE -> Unit
                        else -> controller.endGesture()
                    }
                    onSnapFeedback(SnapResult.None)
                    mode = DragMode.NONE
                    activeHandle = null
                    change.consume()
                }

                PointerEventType.Scroll -> {
                    val scroll = change.scrollDelta.y
                    val view = transform.viewport
                    if (ctrl) {
                        // Zoom towards the pointer, the way every design tool
                        // does. This used to be a zoom followed by a corrective
                        // pan computed from `toCanvas`, which floors to whole
                        // GUI pixels - so every wheel notch threw away up to a
                        // pixel of correction and the content crept away from
                        // the cursor. `zoomAround` solves for the pan exactly.
                        controller.zoomAround(
                            factor = if (scroll < 0) 1.12f else 0.89f,
                            focalX = change.position.x,
                            focalY = change.position.y,
                            viewportWidth = view.width,
                            viewportHeight = view.height,
                        )
                    } else if (shift) {
                        controller.panBy(-scroll * 40f, 0f, view.width, view.height)
                    } else {
                        controller.panBy(0f, -scroll * 40f, view.width, view.height)
                    }
                    change.consume()
                }

                else -> Unit
            }
        }
    }
}

/**
 * Decides what a plain left-press means: grab a handle, start moving the
 * selection, or rubber-band from empty canvas.
 */
private fun beginSelectionGesture(
    controller: EditorController,
    state: EditorState,
    transform: CanvasTransform,
    viewPoint: Offset,
    canvasPoint: IntPoint,
    shift: Boolean,
    onHandle: (ResizeHandle?) -> Unit,
): DragMode {
    // 1. Resize handles win over everything, including elements on top.
    val primaryBounds = state.primarySelection?.let { state.absoluteBounds[it] }
    if (primaryBounds != null && state.selection.size == 1) {
        val handle = transform.handleAt(primaryBounds, viewPoint, 11f)
        if (handle != null) {
            onHandle(handle)
            controller.beginResize(state.primarySelection!!, handle)
            return DragMode.RESIZE
        }
    }
    onHandle(null)

    // 2. An element under the cursor starts a move.
    val hit = controller.hitTest(canvasPoint)
    if (hit != null) {
        if (shift) {
            controller.select(hit.id, toggle = true)
        } else if (hit.id !in state.selection) {
            controller.select(hit.id)
        }
        val ids = controller.current.selection.ifEmpty { setOf(hit.id) }
        controller.beginDrag(ids)
        return DragMode.MOVE
    }

    // 3. Empty canvas: clear and rubber-band.
    if (!shift) controller.clearSelection()
    controller.beginMarquee(canvasPoint.x, canvasPoint.y)
    return DragMode.MARQUEE
}
