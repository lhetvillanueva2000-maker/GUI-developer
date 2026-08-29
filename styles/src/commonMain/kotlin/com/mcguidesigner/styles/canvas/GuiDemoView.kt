package com.mcguidesigner.styles.canvas

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.editor.DemoRuntime
import com.mcguidesigner.core.editor.DemoState
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.model.bool
import com.mcguidesigner.core.model.walkAll
import com.mcguidesigner.styles.render.EditionSkin
import com.mcguidesigner.styles.render.TextureResolver
import com.mcguidesigner.styles.theme.LocalEditionSkin
import com.mcguidesigner.styles.theme.LocalSkinPalette

/**
 * The screen, running.
 *
 * Same tree, same skins, same drawing pass as the still preview - the only
 * addition is that pointers reach it. Buttons press and release, toggles flip,
 * checkboxes tick, tabs deselect their neighbours, dropdowns cycle, sliders
 * follow a finger, scroll regions scroll, and the whole thing pinches, wheels
 * and drags to any zoom.
 *
 * All of the behaviour lives in [DemoRuntime], over in `sharedCore`, so this
 * file is only about turning gestures into calls to it. That split is what
 * keeps the demo honest on both platforms: the mouse and the touchscreen
 * disagree about nearly everything except what a button does when you press it.
 *
 * ### Why it is not made of Compose widgets
 *
 * The obvious implementation - lay the design out as real `Button`s and
 * `Switch`es - fails at the only thing the demo is for. It would show Material's
 * idea of a button, not the Minecraft button that was drawn, and the question
 * being asked is whether *that* button is big enough, clear enough and in the
 * right place. So the demo draws the design and simulates the behaviour, rather
 * than the other way round.
 */
@Composable
fun GuiDemoView(
    project: GuiProject,
    textures: TextureResolver,
    demo: DemoState,
    onDemo: (DemoState) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * A state to pin every widget to, for checking a skin.
     *
     * Null is the demo proper, where each widget carries its own state.
     */
    forcedState: InteractionState? = null,
    /** Scale the design is drawn at before the view's own zoom. */
    baseZoom: Float = 3f,
    playAnimations: Boolean = true,
    skin: EditionSkin = LocalEditionSkin.current,
) {
    val measurer = rememberTextMeasurer()
    val palette = LocalSkinPalette.current

    // The demo's own view transform, which is nothing to do with the editor's.
    // Looking closely at a corner of a running screen is a different activity
    // from editing it, and inheriting the editor's zoom means arriving in the
    // demo somewhere nobody chose to be.
    var zoom by remember(project.id) { mutableFloatStateOf(1f) }
    var pan by remember(project.id) { mutableStateOf(Offset.Zero) }

    val shown = DemoRuntime.projectFor(project, demo)
    val playing = playAnimations && shown.hasRunningAnimation
    val clock by rememberDemoClock(playing)
    val time = if (playing) clock else 0L

    // Everything the gesture handlers read, held so that reading it does not
    // *restart* them.
    //
    // `pointerInput` cancels and re-launches its block whenever its keys
    // change, and the demo's state changes on every event a gesture produces -
    // so keying these on `demo` would tear down the handler in the middle of
    // the drag that just updated it. A slider would move once and then stop; a
    // scroll would jump and freeze. The keys are constant and the values are
    // read live instead, which is what `rememberUpdatedState` is for.
    //
    // Note the handlers pass the *document's* project to `DemoRuntime`, never
    // `shown`: every entry point there merges the overrides itself, so folding
    // a fresh tree per pointer event would allocate one for nothing.
    val liveProject by rememberUpdatedState(project)
    val liveDemo by rememberUpdatedState(demo)
    val liveOnDemo by rememberUpdatedState(onDemo)
    val liveBaseZoom by rememberUpdatedState(baseZoom)

    Box(modifier.clipToBounds()) {
        Canvas(
            Modifier
                .fillMaxSize()
                // Hover: half of what a mouse tells you about a layout is
                // whether the thing under the cursor is the thing you meant.
                // A touchscreen sends no hover events, so this costs nothing
                // there rather than having to be conditional.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            if (event.changes.any { it.pressed }) break
                            when (event.type) {
                                PointerEventType.Exit -> liveOnDemo(DemoRuntime.hover(liveDemo, null))
                                PointerEventType.Move, PointerEventType.Enter -> {
                                    val position = event.changes.lastOrNull()?.position ?: continue
                                    val fit = fitOf(size, liveProject, liveBaseZoom, zoom, pan)
                                    val point = fit.toCanvasPoint(position)
                                    val over = DemoRuntime.hitTest(liveProject, liveDemo, point.x, point.y)?.id
                                    if (over != liveDemo.hovered) liveOnDemo(DemoRuntime.hover(liveDemo, over))
                                }

                                else -> Unit
                            }
                        }
                    }
                }
                // The wheel scrolls whatever is under it, and zooms when there
                // is nothing under it to scroll. Two meanings on one input, but
                // they are the two meanings a wheel has everywhere else, so
                // nobody has to be told which is which.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            if (event.type != PointerEventType.Scroll) {
                                if (event.changes.any { it.pressed }) break else continue
                            }
                            val change = event.changes.firstOrNull() ?: continue
                            val notches = change.scrollDelta.y
                            if (notches == 0f) continue
                            val fit = fitOf(size, liveProject, liveBaseZoom, zoom, pan)
                            val point = fit.toCanvasPoint(change.position)
                            val held = liveDemo
                            val scrolled = DemoRuntime.scrollBy(
                                liveProject, held, point.x, point.y, notches * WHEEL_PIXELS,
                            )
                            if (scrolled !== held) {
                                liveOnDemo(scrolled)
                            } else {
                                val next = (zoom * if (notches > 0f) 1f / ZOOM_STEP else ZOOM_STEP)
                                    .coerceIn(MIN_DEMO_ZOOM, MAX_DEMO_ZOOM)
                                pan = anchoredPan(pan, change.position, size.toSize(), next / zoom)
                                zoom = next
                            }
                            change.consume()
                        }
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        var fit = fitOf(size, liveProject, liveBaseZoom, zoom, pan)
                        val start = fit.toCanvasPoint(first.position)
                        var live = DemoRuntime.pointerDown(liveProject, liveDemo, start.x, start.y)
                        liveOnDemo(live)
                        first.consume()

                        var pinching = false
                        var scrolling = false
                        var lastPosition = first.position
                        var lastCentroid = first.position
                        var lastSpan = 0f
                        var travelled = 0f

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val down = event.changes.filter { it.pressed }
                            if (down.isEmpty()) break

                            if (down.size > 1) {
                                if (!pinching) {
                                    // Two fingers is a pinch, never a press.
                                    pinching = true
                                    live = DemoRuntime.cancelPointer(live)
                                    liveOnDemo(live)
                                    lastCentroid = centroidOf(down.map { it.position })
                                    lastSpan = spanOf(down.map { it.position })
                                }
                                val centre = centroidOf(down.map { it.position })
                                val distance = spanOf(down.map { it.position })
                                if (lastSpan > 0f && distance > 0f) {
                                    val next = (zoom * (distance / lastSpan))
                                        .coerceIn(MIN_DEMO_ZOOM, MAX_DEMO_ZOOM)
                                    pan = anchoredPan(pan, centre, size.toSize(), next / zoom)
                                    zoom = next
                                }
                                pan += centre - lastCentroid
                                lastCentroid = centre
                                lastSpan = distance
                                down.forEach { it.consume() }
                                continue
                            }
                            if (pinching) {
                                // One finger left: keep panning from it rather
                                // than snapping the design back under it.
                                lastPosition = down.first().position
                                down.forEach { it.consume() }
                                continue
                            }

                            val change = down.first()
                            val moved = change.position - lastPosition
                            travelled += moved.getDistance()
                            lastPosition = change.position
                            fit = fitOf(size, liveProject, liveBaseZoom, zoom, pan)

                            // A drag that has gone far enough is a scroll rather
                            // than a press - which is how every touch list
                            // behaves, and the only way to scroll one without a
                            // wheel. The press it replaces is rolled back, so
                            // dragging out of a button never fires it.
                            if (!scrolling && live.dragging == null && travelled > TOUCH_SLOP) {
                                val anchor = fit.toCanvasPoint(first.position)
                                val scrolled = DemoRuntime.scrollBy(
                                    liveProject, live, anchor.x, anchor.y, -moved.y / fit.zoom,
                                )
                                if (scrolled !== live) {
                                    scrolling = true
                                    live = DemoRuntime.cancelPointer(scrolled)
                                    liveOnDemo(live)
                                    change.consume()
                                    continue
                                }
                            }

                            live = if (scrolling) {
                                val anchor = fit.toCanvasPoint(first.position)
                                DemoRuntime.scrollBy(liveProject, live, anchor.x, anchor.y, -moved.y / fit.zoom)
                            } else {
                                val point = fit.toCanvasPoint(change.position)
                                DemoRuntime.pointerDrag(liveProject, live, point.x, point.y)
                            }
                            liveOnDemo(live)
                            change.consume()
                        }

                        if (scrolling || pinching) {
                            liveOnDemo(DemoRuntime.cancelPointer(live))
                        } else {
                            val point = fit.toCanvasPoint(lastPosition)
                            liveOnDemo(DemoRuntime.pointerUp(liveProject, live, point.x, point.y))
                        }
                    }
                },
        ) {
            drawProject(
                project = shown,
                transform = fitOf(
                    androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                    project, baseZoom, zoom, pan,
                ),
                textures = textures,
                measurer = measurer,
                skin = skin,
                stateOf = { element -> DemoRuntime.stateFor(element, demo, forcedState) },
                timeMillis = time,
            )
        }

        // The zoom readout, which is also the way back when a pinch has ended
        // up somewhere strange. Hidden until the view has actually been moved:
        // an untouched demo should be the screen and nothing else.
        if (zoom != 1f || pan != Offset.Zero) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.chromePanel.copy(alpha = 0.92f))
                    .padding(3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                DemoViewButton("−") { zoom = (zoom / ZOOM_STEP).coerceIn(MIN_DEMO_ZOOM, MAX_DEMO_ZOOM) }
                DemoViewButton("${(zoom * 100).toInt()}%") {
                    zoom = 1f
                    pan = Offset.Zero
                }
                DemoViewButton("+") { zoom = (zoom * ZOOM_STEP).coerceIn(MIN_DEMO_ZOOM, MAX_DEMO_ZOOM) }
            }
        }
    }
}

@Composable
private fun DemoViewButton(label: String, onClick: () -> Unit) {
    val palette = LocalSkinPalette.current
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(palette.chromePanelAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.chromeText)
    }
}

/** How many GUI pixels one wheel notch scrolls. */
private const val WHEEL_PIXELS = 24f

/** How much one wheel notch or one button press changes the zoom. */
private const val ZOOM_STEP = 1.25f

private const val MIN_DEMO_ZOOM = 0.25f

/**
 * How far the demo will zoom in.
 *
 * Eight times the scale the screen is already drawn at, which on a Java canvas
 * puts a design pixel at about the size of a fingertip - close enough to check
 * the shape of a one-pixel bevel while the thing is still running.
 */
private const val MAX_DEMO_ZOOM = 8f

/** How far a finger may travel before a press is reinterpreted as a scroll. */
private const val TOUCH_SLOP = 8f

/**
 * The design, centred and scaled to the box, times the view's own zoom.
 *
 * Recomputed from the zoom and pan every time rather than stored, for the same
 * reason the paint canvas does it: two copies of this arithmetic is how a tap
 * ends up landing somewhere other than where it looks like it landed.
 */
private fun fitOf(
    view: androidx.compose.ui.unit.IntSize,
    project: GuiProject,
    baseZoom: Float,
    zoom: Float,
    pan: Offset,
): CanvasTransform {
    val canvas = project.canvas
    val viewport = Size(view.width.toFloat(), view.height.toFloat())
    if (view.width <= 0 || view.height <= 0 || canvas.width <= 0 || canvas.height <= 0) {
        return CanvasTransform(baseZoom, pan.x, pan.y, viewport, canvas.size)
    }
    // Never larger than the box at rest. The point of opening the demo is to
    // see the whole screen, and one that starts cropped has answered the layout
    // question wrongly before anybody has touched it.
    val fitScale = minOf(viewport.width / canvas.width, viewport.height / canvas.height)
    return CanvasTransform(
        zoom = minOf(baseZoom, fitScale) * zoom,
        panX = pan.x,
        panY = pan.y,
        viewport = viewport,
        canvas = canvas.size,
    )
}

private fun androidx.compose.ui.unit.IntSize.toSize() = Size(width.toFloat(), height.toFloat())

/**
 * The pan that keeps whatever is under [focus] still while the scale changes by
 * [factor].
 *
 * Without it a pinch slides the design out from under the fingers doing the
 * pinching, which on a screen the size of a phone loses what you were looking
 * at within one gesture.
 */
private fun anchoredPan(pan: Offset, focus: Offset, viewport: Size, factor: Float): Offset {
    if (factor == 1f) return pan
    val centre = Offset(viewport.width / 2f, viewport.height / 2f)
    return pan + (focus - centre - pan) * (1f - factor)
}

private fun centroidOf(points: List<Offset>): Offset {
    if (points.isEmpty()) return Offset.Zero
    var x = 0f
    var y = 0f
    points.forEach { x += it.x; y += it.y }
    return Offset(x / points.size, y / points.size)
}

private fun spanOf(points: List<Offset>): Float {
    if (points.size < 2) return 0f
    val c = centroidOf(points)
    var total = 0f
    points.forEach { total += (it - c).getDistance() }
    return total / points.size
}

/** Whether anything on the screen is an animation that should be running. */
private val GuiProject.hasRunningAnimation: Boolean
    get() = elements.walkAll().any {
        it.visible && it.type == ElementCatalog.IMAGE_ANIMATED && it.props.bool("playing", true)
    }

/** A frame clock that only runs while [enabled]; see the one in GuiCanvas. */
@Composable
private fun rememberDemoClock(enabled: Boolean): State<Long> {
    val held = remember { mutableLongStateOf(0L) }
    return produceState(initialValue = held.longValue, enabled) {
        if (!enabled) return@produceState
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
