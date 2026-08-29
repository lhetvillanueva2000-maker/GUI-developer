package com.mcguidesigner.styles.canvas

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.editor.Interaction
import com.mcguidesigner.core.editor.SnapResult
import com.mcguidesigner.core.editor.ViewMode
import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.PointF
import com.mcguidesigner.core.model.Rotation
import com.mcguidesigner.core.model.bool
import com.mcguidesigner.core.model.int
import com.mcguidesigner.core.model.string
import com.mcguidesigner.core.model.texture
import com.mcguidesigner.core.model.walkAll
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
    // Turning animation off pins every frame strip to frame zero rather than
    // freezing it wherever it happened to be, which is what makes laying out
    // against an animation predictable.
    val playing = state.settings.playAnimations && state.project.hasPlayingAnimation
    val clock by rememberAnimationClock(playing)
    val time = if (playing) clock else 0L
    Box(modifier) {
        // clipToBounds is not optional here. Compose does not clip a Canvas to
        // its own bounds, and this one deliberately draws a rect - the zoomed
        // canvas sheet - that is far larger than the widget whenever the user
        // zooms past "fit". Without the clip that overspill paints straight
        // over whatever sits beside the canvas: the docks and toolbar on the
        // desktop, the title bar and navigation bar on Android.
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
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
                timeMillis = time,
            )
        }
    }
}

/**
 * A millisecond clock that only runs while [enabled].
 *
 * Reading it in a composable that draws is what pulls the canvas onto the
 * frame clock, so it is deliberately gated: a project with no playing
 * animation must not repaint sixty times a second, which on a laptop is the
 * difference between an idle editor and a warm one.  When it stops the value
 * holds rather than resetting, so an animation paused and resumed picks up
 * where it was instead of snapping back to frame zero.
 */
@Composable
private fun rememberAnimationClock(enabled: Boolean): State<Long> {
    val held = remember { mutableLongStateOf(0L) }
    return produceState(initialValue = held.longValue, enabled) {
        if (!enabled) return@produceState
        // The frame clock hands out an arbitrary origin, so the first frame
        // becomes the zero point and everything after it is a delta.
        var origin = -1L
        while (true) {
            withInfiniteAnimationFrameMillis { frame ->
                if (origin < 0L) origin = frame - held.longValue
                held.longValue = frame - origin
                value = held.longValue
            }
        }
    }
}

/** True when any element is an animated image the editor should be playing. */
private val GuiProject.hasPlayingAnimation: Boolean
    get() = elements.walkAll().any { element ->
        element.visible &&
            element.type == ElementCatalog.IMAGE_ANIMATED &&
            element.props.bool("playing", true) &&
            texture(element.props.texture("texture"))?.isAnimated == true
    }

/**
 * Read-only rendering of a project, used by the template gallery, the palette
 * thumbnails and anywhere a still of a screen is wanted.
 *
 * The live, working version is [drawProject]'s other caller - see
 * `GuiDemoView`, which draws the same thing but lets you press it.
 */
@Composable
fun GuiPreview(
    project: GuiProject,
    textures: TextureResolver,
    modifier: Modifier = Modifier,
    zoom: Float = 2f,
    previewState: InteractionState = InteractionState.NORMAL,
    drawBackdrop: Boolean = true,
    playAnimations: Boolean = true,
    skin: EditionSkin = LocalEditionSkin.current,
) {
    val measurer = rememberTextMeasurer()
    val playing = playAnimations && project.hasPlayingAnimation
    val clock by rememberAnimationClock(playing)
    val time = if (playing) clock else 0L
    // Same reasoning as GuiCanvas: a preview zoomed past its box would
    // otherwise paint over whatever is laid out next to it.
    Canvas(modifier.clipToBounds()) {
        val transform = CanvasTransform(
            zoom = zoom,
            panX = 0f,
            panY = 0f,
            viewport = size,
            canvas = project.canvas.size,
        )
        drawProject(
            project = project,
            transform = transform,
            textures = textures,
            measurer = measurer,
            skin = skin,
            stateOf = { previewState },
            drawBackdrop = drawBackdrop,
            timeMillis = time,
        )
    }
}

/**
 * The whole screen, backdrop and all, clipped to the canvas rectangle.
 *
 * Shared by the still preview and the working demo so there is exactly one
 * answer to "what does this project look like". The only difference between the
 * two is [stateOf]: a constant for the still, and the demo's live per-widget
 * answer for the other.
 */
internal fun DrawScope.drawProject(
    project: GuiProject,
    transform: CanvasTransform,
    textures: TextureResolver,
    measurer: TextMeasurer,
    skin: EditionSkin,
    stateOf: (GuiElement) -> InteractionState,
    drawBackdrop: Boolean = true,
    timeMillis: Long = 0L,
) {
    val canvasRect = transform.canvasRect
    if (drawBackdrop) {
        skin.drawBackdropOn(this, canvasRect, project, transform.zoom)
    }
    clipRect(canvasRect.left, canvasRect.top, canvasRect.right, canvasRect.bottom) {
        drawElements(
            nodes = project.elements,
            bounds = project.absoluteBounds(),
            project = project,
            transform = transform,
            textures = textures,
            measurer = measurer,
            skin = skin,
            stateOf = stateOf,
            selection = emptySet(),
            timeMillis = timeMillis,
        )
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
    timeMillis: Long,
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
            stateOf = { if (designMode) InteractionState.NORMAL else state.previewState ?: InteractionState.NORMAL },
            selection = state.selection,
            timeMillis = timeMillis,
        )
    }

    // Canvas outline is always visible so the screen edge is unambiguous.
    strokeRect(canvasRect, chrome.chromeBorder, 1f)

    if (!designMode) return

    if (state.showSafeArea && project.canvas.hasSafeArea) {
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
    stateOf: (GuiElement) -> InteractionState,
    selection: Set<String>,
    timeMillis: Long,
) {
    for (element in nodes) {
        if (!element.visible) continue
        val rect = bounds[element.id] ?: continue
        val viewRect = transform.toView(rect)

        // Off-screen elements are not drawn, but their children are still
        // walked. A child is positioned relative to its parent and is free to
        // sit outside it - drag a button past the edge of its panel and it
        // stays a child - so "the parent is off-screen" says nothing about
        // where the children are. Skipping the subtree here made every child
        // of an off-screen parent vanish, including ones in full view.
        //
        // Walking a subtree that draws nothing costs a map lookup and four
        // comparisons per node, which is nothing next to the drawing this
        // still avoids.
        if (isOnScreen(viewRect, size)) {
            val context = ElementRenderContext(
                element = element,
                rect = viewRect,
                scale = transform.zoom,
                state = stateOf(element),
                project = project,
                textures = textures,
                textMeasurer = measurer,
                selected = element.id in selection,
                timeMillis = timeMillis,
            )
            skin.draw(this, context)
        }

        if (element.children.isEmpty()) continue

        // A scroll container hides what does not fit and moves what it holds.
        //
        // Both halves matter, and the first half is not new behaviour so much
        // as behaviour the catalog always claimed - "clipped, scrollable
        // region" - and the renderer never did. A list of twelve rows in a
        // window six rows tall drew all twelve, straight over whatever was laid
        // out beneath it, so the one element whose whole purpose is that its
        // contents are longer than itself was the one element that could not
        // show that.
        if (element.type != ElementCatalog.CONTAINER_SCROLL) {
            drawElements(
                element.children, bounds, project, transform, textures,
                measurer, skin, stateOf, selection, timeMillis,
            )
            continue
        }
        val scroll = element.props.int("scrollOffset", 0)
        val direction = element.props.string("direction", "vertical")
        val horizontal = direction == "horizontal" || direction == "both"
        val vertical = direction != "horizontal"
        clipRect(viewRect.left, viewRect.top, viewRect.right, viewRect.bottom) {
            translate(
                left = if (horizontal) -scroll * transform.zoom else 0f,
                top = if (vertical) -scroll * transform.zoom else 0f,
            ) {
                drawElements(
                    element.children, bounds, project, transform, textures,
                    measurer, skin, stateOf, selection, timeMillis,
                )
            }
        }
    }
}

/**
 * Whether [viewRect] intersects a viewport of [viewport] at the origin.
 *
 * Pulled out so the traversal in [visibleElementIds] can make exactly the same
 * decision the draw pass does - a culling rule that only exists inside a
 * `DrawScope` is a rule that cannot be tested.
 */
internal fun isOnScreen(viewRect: Rect, viewport: Size): Boolean =
    viewRect.right >= 0f &&
        viewRect.bottom >= 0f &&
        viewRect.left <= viewport.width &&
        viewRect.top <= viewport.height

/**
 * The ids a draw pass would paint, in paint order.
 *
 * Exists for the tests: rendering correctness that can only be checked by
 * looking at a screenshot is rendering correctness nobody checks.
 */
internal fun visibleElementIds(
    nodes: List<GuiElement>,
    bounds: Map<String, IntRect>,
    transform: CanvasTransform,
    viewport: Size,
): List<String> = buildList {
    fun walk(list: List<GuiElement>) {
        for (element in list) {
            if (!element.visible) continue
            val rect = bounds[element.id] ?: continue
            if (isOnScreen(transform.toView(rect), viewport)) add(element.id)
            walk(element.children)
        }
    }
    walk(nodes)
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

    // Started from the first line that is actually on screen rather than from
    // the corner of the sheet.
    //
    // Zoomed in, the sheet is many times the size of the window, so walking it
    // from its corner spends most of the loop drawing lines that land outside
    // the viewport and are clipped away - the cost of the grid rises with the
    // zoom, which is exactly backwards, and it is why deep zoom used to be
    // expensive. The clip below is still needed: it is what keeps the lines
    // inside the sheet. This only stops the ones that were never going to be
    // seen from being issued at all.
    //
    // The index still counts from the sheet's corner, because which lines are
    // major has to depend on where they are on the canvas and not on where the
    // window happens to be.
    fun firstIndex(from: Float, edge: Float): Int =
        if (from >= edge) 0 else kotlin.math.floor((edge - from) / step).toInt()

    clipRect(canvasRect.left, canvasRect.top, canvasRect.right, canvasRect.bottom) {
        var index = firstIndex(canvasRect.left, 0f)
        var x = canvasRect.left + index * step
        val lastX = minOf(canvasRect.right, size.width)
        while (x <= lastX + 0.5f) {
            val major = index % 4 == 0
            drawLine(
                color = if (major) chrome.gridLineMajor else chrome.gridLine,
                start = Offset(x, maxOf(canvasRect.top, 0f)),
                end = Offset(x, minOf(canvasRect.bottom, size.height)),
                strokeWidth = 1f,
            )
            x += step
            index++
        }
        index = firstIndex(canvasRect.top, 0f)
        var y = canvasRect.top + index * step
        val lastY = minOf(canvasRect.bottom, size.height)
        while (y <= lastY + 0.5f) {
            val major = index % 4 == 0
            drawLine(
                color = if (major) chrome.gridLineMajor else chrome.gridLine,
                start = Offset(maxOf(canvasRect.left, 0f), y),
                end = Offset(minOf(canvasRect.right, size.width), y),
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

    // Every outline follows its element's own angle. Drawing a turned element
    // inside a square-to-the-world box was the single most misleading thing the
    // canvas did: it said the element was one shape while the renderer, the
    // exports and the eye all said another.
    fun angleOf(id: String): Int =
        Rotation.normalise(state.project.element(id)?.props?.int("rotation", 0) ?: 0)

    state.hoveredId
        ?.takeIf { it !in state.selection }
        ?.let { id -> bounds[id]?.let { id to it } }
        ?.let { (id, rect) ->
            drawOutline(
                transform = transform,
                rect = rect,
                degrees = angleOf(id),
                color = chrome.selection.copy(alpha = 0.5f),
                width = 1f,
            )
        }

    state.selection.forEach { id ->
        val rect = bounds[id] ?: return@forEach
        val degrees = angleOf(id)
        drawOutline(
            transform = transform,
            rect = rect,
            degrees = degrees,
            color = chrome.selection,
            width = if (id == state.primarySelection) 2f else 1f,
            fill = chrome.selectionFill,
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
    val degrees = angleOf(primary)

    // The knob first, so its stalk passes behind the handles rather than over
    // them.
    val knob = transform.rotationKnob(rect, degrees, ROTATION_KNOB_DISTANCE)
    val topCentre = Rotation.rotate(
        PointF(Rotation.centreOf(rect).x, rect.y.toFloat()),
        Rotation.centreOf(rect),
        degrees.toFloat(),
    ).let { transform.toView(it.x, it.y) }

    drawLine(chrome.selection, start = topCentre, end = knob, strokeWidth = 1f)
    drawCircle(chrome.chromeBackground, radius = handleSize * 0.6f, center = knob)
    drawCircle(chrome.selection, radius = handleSize * 0.6f, center = knob, style = Stroke(width = 1.5f))

    transform.handles(rect, handleSize, degrees).forEach { (_, handle) ->
        // Drawn turned even though the hit box is square: the handle is a
        // little picture of the corner it grabs, and a corner that stays
        // upright on a turned element looks like a mistake.
        rotate(degrees.toFloat(), pivot = handle.center) {
            drawRect(chrome.chromeBackground, topLeft = handle.topLeft, size = handle.size)
            drawRect(
                color = chrome.selection,
                topLeft = handle.topLeft,
                size = handle.size,
                style = Stroke(width = 1.5f),
            )
        }
    }
}

/** View-space gap between an element's top edge and its rotation knob. */
const val ROTATION_KNOB_DISTANCE = 26f

/** One selection outline, turned to match its element. */
private fun DrawScope.drawOutline(
    transform: CanvasTransform,
    rect: com.mcguidesigner.core.model.IntRect,
    degrees: Int,
    color: androidx.compose.ui.graphics.Color,
    width: Float,
    fill: androidx.compose.ui.graphics.Color? = null,
) {
    val view = transform.toView(rect)
    if (degrees == 0) {
        fill?.let { drawRect(it, topLeft = view.topLeft, size = view.size) }
        drawRect(color = color, topLeft = view.topLeft, size = view.size, style = Stroke(width = width))
        return
    }
    rotate(degrees.toFloat(), pivot = view.center) {
        fill?.let { drawRect(it, topLeft = view.topLeft, size = view.size) }
        drawRect(color = color, topLeft = view.topLeft, size = view.size, style = Stroke(width = width))
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
    // Clipped for the same reason [GuiCanvas] is: a tick's position comes from
    // the same transform the canvas uses, so at any real zoom most of them land
    // outside this strip - and an unclipped Canvas will happily draw them over
    // whatever dock is next door.
    Canvas(modifier.clipToBounds()) {
        drawRect(background, size = size)
        val step = chooseRulerStep(transform.zoom)
        val extent = if (vertical) transform.canvas.height else transform.canvas.width
        val along = if (vertical) size.height else size.width
        var value = 0
        while (value <= extent) {
            val position = if (vertical) {
                transform.toView(0f, value.toFloat()).y
            } else {
                transform.toView(value.toFloat(), 0f).x
            }
            // The clip above makes an off-strip tick invisible; skipping it
            // here means the label is never measured in the first place, which
            // at a high zoom is most of them.
            if (position < -RULER_TICK_MARGIN || position > along + RULER_TICK_MARGIN) {
                value += step
                continue
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

/** Slack around a ruler so a label anchored just off the edge still shows. */
private const val RULER_TICK_MARGIN = 40f

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
