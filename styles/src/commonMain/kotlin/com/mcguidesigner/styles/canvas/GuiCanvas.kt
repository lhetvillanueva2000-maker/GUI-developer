package com.mcguidesigner.styles.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.editor.Interaction
import com.mcguidesigner.core.editor.SnapResult
import com.mcguidesigner.core.editor.ViewMode
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.TargetForm
import com.mcguidesigner.core.validation.Severity
import com.mcguidesigner.styles.render.EditionSkin
import com.mcguidesigner.styles.render.ElementRenderContext
import com.mcguidesigner.styles.render.TextureResolver
import com.mcguidesigner.styles.render.draw
import com.mcguidesigner.styles.render.drawBackdropOn
import com.mcguidesigner.styles.render.fillRect
import com.mcguidesigner.styles.render.strokeRect
import com.mcguidesigner.styles.theme.ErrorRed
import com.mcguidesigner.styles.theme.LocalEditionSkin
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.SkinPalette
import com.mcguidesigner.styles.theme.WarningAmber
import kotlin.math.abs

/** View-space size of a resize handle. Larger on touch, set by the caller. */
const val DEFAULT_HANDLE_SIZE = 9f

/**
 * The design surface, shared verbatim by desktop and Android.
 *
 * It is a single [Canvas]: the whole GUI tree, the grid, guides, selection
 * chrome and marquee are painted in one pass.  A composable-per-element tree
 * would be far more expensive here - a chest screen alone is ~90 slots, and
 * they all move together during a drag.
 *
 * Input is *not* handled here.  Each platform attaches its own gesture
 * modifiers to [modifier], because a mouse with hover, right-click and
 * modifier keys and a touchscreen with long-press and pinch have genuinely
 * different interaction models.
 */
@Composable
fun GuiCanvas(
    state: EditorState,
    transform: CanvasTransform,
    textures: TextureResolver,
    modifier: Modifier = Modifier,
    handleSize: Float = DEFAULT_HANDLE_SIZE,
    snapFeedback: SnapResult = SnapResult.None,
    skin: EditionSkin = LocalEditionSkin.current,
    chrome: SkinPalette = LocalSkinPalette.current,
    workspaceColor: Color = chrome.chromeBackground,
) {
    val measurer = rememberTextMeasurer()
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawCanvasSurface(
                state = state,
                transform = transform,
                textures = textures,
                measurer = measurer,
                skin = skin,
                chrome = chrome,
                workspaceColor = workspaceColor,
                handleSize = handleSize,
                snapFeedback = snapFeedback,
            )
        }
    }
}

/**
 * Read-only rendering of a project, used by the preview pane, the template
 * gallery and the palette thumbnails.
 */
@Composable
fun GuiPreview(
    project: GuiProject,
    textures: TextureResolver,
    modifier: Modifier = Modifier,
    zoom: Float = 2f,
    previewState: InteractionState = InteractionState.NORMAL,
    form: TargetForm = project.canvas.targetForm,
    drawBackdrop: Boolean = true,
    skin: EditionSkin = LocalEditionSkin.current,
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val transform = CanvasTransform(
            zoom = zoom,
            panX = 0f,
            panY = 0f,
            viewport = size,
            canvas = project.canvas.size,
        )
        if (drawBackdrop) {
            skin.drawBackdropOn(this, transform.canvasRect, project, zoom)
        }
        clipRect(
            transform.canvasRect.left,
            transform.canvasRect.top,
            transform.canvasRect.right,
            transform.canvasRect.bottom,
        ) {
            drawElements(
                nodes = project.elements,
                bounds = project.absoluteBounds(),
                project = project,
                transform = transform,
                textures = textures,
                measurer = measurer,
                skin = skin,
                state = previewState,
                form = form,
                selection = emptySet(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Drawing
// ---------------------------------------------------------------------------

private fun DrawScope.drawCanvasSurface(
    state: EditorState,
    transform: CanvasTransform,
    textures: TextureResolver,
    measurer: TextMeasurer,
    skin: EditionSkin,
    chrome: SkinPalette,
    workspaceColor: Color,
    handleSize: Float,
    snapFeedback: SnapResult,
) {
    val project = state.project
    val canvasRect = transform.canvasRect
    val designMode = state.viewMode == ViewMode.DESIGN

    // Workspace behind the canvas.
    //
    // Transparent when the shell is showing wallpaper behind the editor - this
    // is the only place the artwork can actually be seen, since every dock
    // above it is opaque.
    if (workspaceColor != Color.Transparent) {
        drawRect(workspaceColor, size = size)
    }

    // Drop shadow so the canvas reads as a sheet floating over the workspace.
    drawRect(
        color = Color.Black.copy(alpha = 0.35f),
        topLeft = Offset(canvasRect.left + 4f, canvasRect.top + 4f),
        size = Size(canvasRect.width, canvasRect.height),
    )

    // The screen itself is always opaque, whatever is behind the workspace: a
    // project whose backdrop is NONE must show the app's own surface, not
    // somebody's wallpaper bleeding through the design.
    drawRect(
        color = chrome.chromeBackground,
        topLeft = canvasRect.topLeft,
        size = Size(canvasRect.width, canvasRect.height),
    )

    skin.drawBackdropOn(this, canvasRect, project, transform.zoom)

    if (designMode && state.showGrid) {
        drawGrid(canvasRect, transform, project.canvas.gridSize, chrome)
    }

    val bounds = state.absoluteBounds
    clipRect(canvasRect.left, canvasRect.top, canvasRect.right, canvasRect.bottom) {
        drawElements(
            nodes = project.elements,
            bounds = bounds,
            project = project,
            transform = transform,
            textures = textures,
            measurer = measurer,
            skin = skin,
            state = if (designMode) InteractionState.NORMAL else state.previewState,
            form = state.previewForm,
            selection = state.selection,
        )
    }

    // Canvas outline is always visible so the screen edge is unambiguous.
    strokeRect(canvasRect, chrome.chromeBorder, 1f)

    if (!designMode) return

    if (state.showSafeArea && project.canvas.targetForm == TargetForm.MOBILE) {
        drawSafeArea(state, transform, chrome)
    }

    if (state.showGuides) {
        state.guides.forEach { guide ->
            val color = chrome.guideLine.copy(alpha = 0.85f)
            if (guide.vertical) {
                val x = transform.toView(guide.position.toFloat(), 0f).x
                drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f)
            } else {
                val y = transform.toView(0f, guide.position.toFloat()).y
                drawLine(color, Offset(0f, y), Offset(size.width, y), 1f)
            }
        }
    }

    drawValidationBadges(state, transform)
    drawSelectionChrome(state, transform, chrome, handleSize)

    snapFeedback.verticalLines.forEach { x ->
        val vx = transform.toView(x.toFloat(), 0f).x
        drawLine(chrome.accent, Offset(vx, 0f), Offset(vx, size.height), 1f)
    }
    snapFeedback.horizontalLines.forEach { y ->
        val vy = transform.toView(0f, y.toFloat()).y
        drawLine(chrome.accent, Offset(0f, vy), Offset(size.width, vy), 1f)
    }

    (state.interaction as? Interaction.Marquee)?.let { marquee ->
        val rect = transform.toView(marquee.rect)
        drawRect(chrome.selectionFill, topLeft = rect.topLeft, size = rect.size)
        drawRect(
            color = chrome.selection,
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))),
        )
    }
}

/** Recursive element painting; children are drawn on top of their parent. */
private fun DrawScope.drawElements(
    nodes: List<GuiElement>,
    bounds: Map<String, IntRect>,
    project: GuiProject,
    transform: CanvasTransform,
    textures: TextureResolver,
    measurer: TextMeasurer,
    skin: EditionSkin,
    state: InteractionState,
    form: TargetForm,
    selection: Set<String>,
) {
    for (element in nodes) {
        if (!element.visible) continue
        val rect = bounds[element.id] ?: continue
        val viewRect = transform.toView(rect)
        // Skip anything entirely off-screen; large layouts stay smooth when
        // the user zooms in on one corner.
        if (viewRect.right < 0f || viewRect.bottom < 0f ||
            viewRect.left > size.width || viewRect.top > size.height
        ) {
            continue
        }

        val context = ElementRenderContext(
            element = element,
            rect = viewRect,
            scale = transform.zoom,
            state = state,
            project = project,
            textures = textures,
            textMeasurer = measurer,
            form = form,
            selected = element.id in selection,
        )
        skin.draw(this, context)

        if (element.children.isNotEmpty()) {
            drawElements(
                element.children, bounds, project, transform, textures,
                measurer, skin, state, form, selection,
            )
        }
    }
}

private fun DrawScope.drawGrid(
    canvasRect: Rect,
    transform: CanvasTransform,
    gridSize: Int,
    chrome: SkinPalette,
) {
    if (gridSize <= 0) return
    val step = gridSize * transform.zoom
    // Below ~4 device pixels the grid turns into noise; hide it instead.
    if (step < 4f) return

    clipRect(canvasRect.left, canvasRect.top, canvasRect.right, canvasRect.bottom) {
        var index = 0
        var x = canvasRect.left
        while (x <= canvasRect.right + 0.5f) {
            val major = index % 4 == 0
            drawLine(
                color = if (major) chrome.gridLineMajor else chrome.gridLine,
                start = Offset(x, canvasRect.top),
                end = Offset(x, canvasRect.bottom),
                strokeWidth = 1f,
            )
            x += step
            index++
        }
        index = 0
        var y = canvasRect.top
        while (y <= canvasRect.bottom + 0.5f) {
            val major = index % 4 == 0
            drawLine(
                color = if (major) chrome.gridLineMajor else chrome.gridLine,
                start = Offset(canvasRect.left, y),
                end = Offset(canvasRect.right, y),
                strokeWidth = 1f,
            )
            y += step
            index++
        }
    }
}

private fun DrawScope.drawSafeArea(state: EditorState, transform: CanvasTransform, chrome: SkinPalette) {
    val safe = state.project.canvas.safeArea
    val canvas = state.project.canvas
    val inner = IntRect.fromEdges(
        safe.left, safe.top,
        canvas.width - safe.right,
        canvas.height - safe.bottom,
    )
    val rect = transform.toView(inner)
    drawRect(
        color = chrome.guideLine.copy(alpha = 0.5f),
        topLeft = rect.topLeft,
        size = rect.size,
        style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))),
    )
}

/**
 * Small corner markers on elements that have validation problems.  Being able
 * to see *where* an error is without opening the issues panel is the whole
 * point of validating continuously.
 */
private fun DrawScope.drawValidationBadges(state: EditorState, transform: CanvasTransform) {
    if (state.validation.issues.isEmpty()) return
    val bounds = state.absoluteBounds
    val worst = mutableMapOf<String, Severity>()
    state.validation.issues.forEach { issue ->
        val id = issue.elementId ?: return@forEach
        val current = worst[id]
        if (current == null || issue.severity.ordinal < current.ordinal) worst[id] = issue.severity
    }
    worst.forEach { (id, severity) ->
        if (severity == Severity.INFO) return@forEach
        val rect = bounds[id]?.let { transform.toView(it) } ?: return@forEach
        val color = if (severity == Severity.ERROR) ErrorRed else WarningAmber
        drawRect(
            color = color,
            topLeft = Offset(rect.right - 6f, rect.top - 2f),
            size = Size(6f, 6f),
        )
    }
}

private fun DrawScope.drawSelectionChrome(
    state: EditorState,
    transform: CanvasTransform,
    chrome: SkinPalette,
    handleSize: Float,
) {
    val bounds = state.absoluteBounds

    state.hoveredId
        ?.takeIf { it !in state.selection }
        ?.let { bounds[it] }
        ?.let { rect ->
            val view = transform.toView(rect)
            drawRect(
                color = chrome.selection.copy(alpha = 0.5f),
                topLeft = view.topLeft,
                size = view.size,
                style = Stroke(width = 1f),
            )
        }

    state.selection.forEach { id ->
        val rect = bounds[id] ?: return@forEach
        val view = transform.toView(rect)
        drawRect(chrome.selectionFill, topLeft = view.topLeft, size = view.size)
        drawRect(
            color = chrome.selection,
            topLeft = view.topLeft,
            size = view.size,
            style = Stroke(width = if (id == state.primarySelection) 2f else 1f),
        )
    }

    // Handles only on a single, unlocked, resizable selection - dragging a
    // handle with several elements selected is ambiguous.
    val primary = state.primarySelection ?: return
    if (state.selection.size != 1) return
    val element = state.project.element(primary) ?: return
    if (element.locked) return
    val definition = com.mcguidesigner.core.catalog.ElementCatalog[element.type]
    if (definition?.resizable != true) return
    val rect = bounds[primary] ?: return

    transform.handles(rect, handleSize).forEach { (_, handle) ->
        drawRect(chrome.chromeBackground, topLeft = handle.topLeft, size = handle.size)
        drawRect(
            color = chrome.selection,
            topLeft = handle.topLeft,
            size = handle.size,
            style = Stroke(width = 1.5f),
        )
    }
}

/**
 * Ruler strip drawn along one edge of the workspace.
 *
 * Kept separate from [GuiCanvas] so the Android layout can leave it out
 * entirely - rulers are precision affordances that cost screen space a phone
 * does not have.
 */
@Composable
fun CanvasRuler(
    transform: CanvasTransform,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    // The themed palette, not the skin's own: a ruler is editor chrome and has
    // to follow the light/dark setting like every other strip around the canvas.
    tickColor: Color = LocalSkinPalette.current.chromeTextMuted,
    background: Color = LocalSkinPalette.current.chromePanel,
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        drawRect(background, size = size)
        val step = chooseRulerStep(transform.zoom)
        val extent = if (vertical) transform.canvas.height else transform.canvas.width
        var value = 0
        while (value <= extent) {
            val position = if (vertical) {
                transform.toView(0f, value.toFloat()).y
            } else {
                transform.toView(value.toFloat(), 0f).x
            }
            val major = value % (step * 4) == 0
            val length = if (major) size.minDimension else size.minDimension * 0.45f
            if (vertical) {
                drawLine(tickColor, Offset(size.width - length, position), Offset(size.width, position), 1f)
            } else {
                drawLine(tickColor, Offset(position, size.height - length), Offset(position, size.height), 1f)
            }
            if (major && !vertical) {
                val layout = measurer.measure(
                    value.toString(),
                    androidx.compose.ui.text.TextStyle(
                        color = tickColor,
                        fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                )
                drawText(textLayoutResult = layout, topLeft = Offset(position + 2f, 1f))
            }
            value += step
        }
    }
}

/** Picks a ruler tick interval that stays legible at the current zoom. */
private fun chooseRulerStep(zoom: Float): Int {
    val candidates = intArrayOf(1, 2, 4, 8, 16, 32, 64, 128)
    return candidates.firstOrNull { it * zoom >= 8f } ?: 128
}

/** Convenience for platform gesture code: is [point] within [tolerance] of a guide? */
fun CanvasTransform.guideAt(
    guides: List<com.mcguidesigner.core.editor.Guide>,
    point: Offset,
    tolerance: Float = 5f,
): com.mcguidesigner.core.editor.Guide? = guides.firstOrNull { guide ->
    if (guide.vertical) {
        abs(toView(guide.position.toFloat(), 0f).x - point.x) <= tolerance
    } else {
        abs(toView(0f, guide.position.toFloat()).y - point.y) <= tolerance
    }
}

/** Fills a rect in view space - re-exported so platform overlays can use it. */
fun DrawScope.overlayRect(rect: Rect, color: Color) = fillRect(rect, color)
