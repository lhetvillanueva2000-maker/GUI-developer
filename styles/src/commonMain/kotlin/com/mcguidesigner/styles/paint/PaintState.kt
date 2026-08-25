package com.mcguidesigner.styles.paint

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.mcguidesigner.core.image.PngWriter
import com.mcguidesigner.core.paint.AutoCutout
import com.mcguidesigner.core.paint.BlendMode
import com.mcguidesigner.core.paint.Brush
import com.mcguidesigner.core.paint.BrushShape
import com.mcguidesigner.core.paint.Compositor
import com.mcguidesigner.core.paint.Guides
import com.mcguidesigner.core.paint.MagicEraser
import com.mcguidesigner.core.paint.PaintBackground
import com.mcguidesigner.core.paint.PaintDocument
import com.mcguidesigner.core.paint.PaintLayer
import com.mcguidesigner.core.paint.PaintOps
import com.mcguidesigner.core.paint.Pixels
import com.mcguidesigner.core.paint.RegionFill
import com.mcguidesigner.core.paint.Stabilizer
import com.mcguidesigner.core.paint.StrokeEngine
import com.mcguidesigner.core.paint.StrokePoint
import com.mcguidesigner.core.paint.UndoStack
import kotlin.math.roundToInt

/** The tools on the wheel. */
enum class PaintTool(val label: String) {
    BRUSH("Brush"),
    ERASER("Eraser"),
    BUCKET("Bucket"),
    EYEDROPPER("Eyedropper"),
    MAGIC_ERASER("Magic Eraser"),
    SMUDGE("Smudge"),
    BLUR("Blur"),
    PAN("Pan"),
    ;

    /** Whether this tool draws with the brush engine. */
    val isStroke: Boolean get() = this == BRUSH || this == ERASER || this == SMUDGE || this == BLUR
}

/** Which panel is open over the canvas, if any. */
enum class PaintSheet { NONE, LAYERS, COLOUR, BRUSH, TOOLS, CUTOUT }

/** Which top-bar popover is open, if any. */
enum class PaintPopover { NONE, VIEW, SELECT, STROKE, RULER, MATERIALS }

/** Symmetry, from the ruler popover. */
enum class SymmetryMode(val label: String) {
    OFF("Off"),
    VERTICAL("Vertical"),
    HORIZONTAL("Horizontal"),
    QUAD("Both"),
    RADIAL("Radial"),
}

/**
 * Everything the paint screen reads and writes.
 *
 * A plain class holding Compose state rather than a ViewModel, for the same
 * reason the rest of this app does it: the desktop shell has no such concept,
 * and one of the two platforms having a different state model is how they drift
 * apart. The document and the undo stack are ordinary objects that know nothing
 * about Compose; only what the UI has to observe is `mutableStateOf`.
 */
class PaintState(
    width: Int = 1536,
    height: Int = 1536,
    background: PaintBackground = PaintBackground.WHITE,
) {
    var document by mutableStateOf(PaintDocument.blank(width, height, background))
        private set

    val undo = UndoStack()

    /**
     * Bumped whenever the pixels change.
     *
     * The canvas is a plain IntArray, which Compose cannot observe and should
     * not try to - it changes a few thousand times a second while drawing.
     * One integer that says "something moved" is all the recomposition signal
     * needed, and it means a stroke costs one state write per frame rather than
     * one per pixel.
     */
    var revision by mutableStateOf(0)
        private set

    /** Bumped when the layer list changes shape, so panels rebuild. */
    var layerRevision by mutableStateOf(0)
        private set

    private var flattened = Compositor.flatten(document)
    private var engine = StrokeEngine(document.width, document.height)
    private val stabilizer = Stabilizer()

    var surface: PaintSurface? = null
        private set

    // -- Tools -------------------------------------------------------------

    var tool by mutableStateOf(PaintTool.BRUSH)
    /** The tool the swap button returns to. */
    var previousTool by mutableStateOf(PaintTool.ERASER)

    var brushShape by mutableStateOf(BrushShape.DIP_PEN_SOFT)
    var eraserShape by mutableStateOf(BrushShape.FELT_TIP_SOFT)

    var brushSize by mutableStateOf(3f)
    var eraserSize by mutableStateOf(48f)
    var brushOpacity by mutableStateOf(1f)
    var eraserOpacity by mutableStateOf(1f)

    var stabilizerStrength by mutableStateOf(0f)
    var forceFade by mutableStateOf(false)
    var fadeIn by mutableStateOf(0.4f)
    var fadeOut by mutableStateOf(0.4f)

    var colour by mutableStateOf(0xFF000000.toInt())
    var secondaryColour by mutableStateOf(0xFFFFFFFF.toInt())
    var recentColours by mutableStateOf(listOf<Int>())

    var tolerance by mutableStateOf(32)
    var contiguous by mutableStateOf(true)
    var featherEdges by mutableStateOf(1)

    var symmetry by mutableStateOf(SymmetryMode.OFF)
    var radialSlices by mutableStateOf(8)
    var showGrid by mutableStateOf(false)
    var gridStep by mutableStateOf(64)
    var pixelated by mutableStateOf(false)

    var sheet by mutableStateOf(PaintSheet.NONE)
    var popover by mutableStateOf(PaintPopover.NONE)

    /** Result of the last auto cutout, kept so it can be tuned or undone. */
    var cutoutConfidence by mutableStateOf<Int?>(null)
    var cutoutKeep by mutableStateOf(50)
    var busy by mutableStateOf<String?>(null)

    // -- View --------------------------------------------------------------

    var zoom by mutableStateOf(1f)
    var pan by mutableStateOf(Offset.Zero)

    // -- Derived -----------------------------------------------------------

    val activeSize: Float get() = if (tool == PaintTool.ERASER) eraserSize else brushSize
    val activeOpacity: Float get() = if (tool == PaintTool.ERASER) eraserOpacity else brushOpacity
    val activeShape: BrushShape get() = if (tool == PaintTool.ERASER) eraserShape else brushShape

    fun setActiveSize(value: Float) {
        val clamped = value.coerceIn(1f, 400f)
        if (tool == PaintTool.ERASER) eraserSize = clamped else brushSize = clamped
    }

    fun setActiveOpacity(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        if (tool == PaintTool.ERASER) eraserOpacity = clamped else brushOpacity = clamped
    }

    val brush: Brush
        get() = Brush(
            shape = activeShape,
            size = activeSize,
            opacity = activeOpacity,
            flow = 1f,
            stabilizer = stabilizerStrength,
            fadeIn = if (forceFade) fadeIn else 0f,
            fadeOut = if (forceFade) fadeOut else 0f,
        )

    val canUndo: Boolean get() = undo.canUndo
    val canRedo: Boolean get() = undo.canRedo

    /** The composited pixels, for the renderer. Do not mutate. */
    val pixels: IntArray get() = flattened

    // -- Lifecycle ---------------------------------------------------------

    fun attachSurface() {
        if (surface?.width == document.width && surface?.height == document.height) return
        surface?.dispose()
        surface = PaintSurface(document.width, document.height)
        pushToSurface()
    }

    fun release() {
        surface?.dispose()
        surface = null
    }

    private fun pushToSurface() {
        surface?.update(flattened)
    }

    fun newDocument(width: Int, height: Int, background: PaintBackground) {
        document = PaintDocument.blank(width, height, background)
        undo.clear()
        engine = StrokeEngine(width, height)
        flattened = Compositor.flatten(document)
        surface?.dispose()
        surface = PaintSurface(width, height)
        pushToSurface()
        zoom = 1f
        pan = Offset.Zero
        cutoutConfidence = null
        bumpAll()
    }

    private fun bump() {
        pushToSurface()
        revision++
    }

    private fun bumpAll() {
        flattened = Compositor.flatten(document, flattened)
        pushToSurface()
        revision++
        layerRevision++
    }

    /** Recomposites everything. For operations with no useful bounds. */
    fun refresh() {
        flattened = Compositor.flatten(document, flattened)
        bump()
    }

    // -- Strokes -----------------------------------------------------------

    private var strokeLayer: PaintLayer? = null
    private var strokeLength = 0f
    private var lastPoint = Offset.Zero

    /**
     * Points a stroke should be mirrored to.
     *
     * Symmetry is applied here rather than inside the stroke engine so that all
     * the reflections share one coverage buffer. Running a separate engine per
     * reflection means the point where two mirrored strokes cross gets painted
     * twice and goes darker than the rest at any opacity below full - a seam
     * straight down the axis of symmetry, which is the one place it is most
     * obvious.
     */
    private fun reflections(point: StrokePoint): List<StrokePoint> {
        val cx = document.width / 2f
        val cy = document.height / 2f
        return when (symmetry) {
            SymmetryMode.OFF -> listOf(point)
            SymmetryMode.VERTICAL -> Guides.mirrorVertical(point, cx)
            SymmetryMode.HORIZONTAL -> Guides.mirrorHorizontal(point, cy)
            SymmetryMode.QUAD -> Guides.mirrorVertical(point, cx).flatMap { Guides.mirrorHorizontal(it, cy) }
            SymmetryMode.RADIAL -> Guides.radial(point, cx, cy, radialSlices)
        }
    }

    fun strokeStart(x: Float, y: Float) {
        val layer = document.active ?: return
        if (layer.locked) return
        strokeLayer = layer
        strokeLength = 0f
        lastPoint = Offset(x, y)
        stabilizer.reset(stabilizerStrength)
        val (sx, sy) = stabilizer.push(x, y)

        undo.begin(if (tool == PaintTool.ERASER) "Erase" else "Brush", layer)
        val b = brush
        reflections(StrokePoint(sx, sy)).forEachIndexed { channel, point ->
            if (channel == 0) engine.begin(point, b) else engine.beginChannel(channel, point, b)
        }
        applyLive()
    }

    fun strokeMove(x: Float, y: Float) {
        if (strokeLayer == null) return
        val (sx, sy) = stabilizer.push(x, y)
        strokeLength += (Offset(sx, sy) - lastPoint).getDistance()
        lastPoint = Offset(sx, sy)
        val b = brush
        reflections(StrokePoint(sx, sy)).forEachIndexed { channel, point ->
            engine.extendTo(point, b, strokeLength, channel)
        }
        applyLive()
    }

    fun strokeEnd(x: Float, y: Float) {
        val layer = strokeLayer ?: return
        val b = brush
        stabilizer.drain(x, y).forEach { (dx, dy) ->
            reflections(StrokePoint(dx, dy)).forEachIndexed { channel, point ->
                engine.extendTo(point, b, strokeLength, channel)
            }
        }
        applyLive()
        engine.clear()
        undo.commit(layer)
        strokeLayer = null
        rememberColour()
        bumpAll()
    }

    /**
     * Abandons the stroke in progress and puts the layer back.
     *
     * What happens when a second finger lands. A single finger paints from the
     * first frame - waiting to see whether a pinch is starting would put a
     * delay on every stroke to save a mistake that happens occasionally - so
     * the mistake is corrected afterwards instead, and the pre-stroke pixels
     * are already recorded in the open undo step.
     */
    fun cancelStroke() {
        val layer = strokeLayer ?: return
        if (engine.hasDirt) {
            val left = engine.dirtyLeft
            val top = engine.dirtyTop
            val right = engine.dirtyRight
            val bottom = engine.dirtyBottom
            for (y in top..bottom) {
                val row = y * document.width
                for (x in left..right) {
                    layer.pixels[row + x] = undo.originalAt(layer, row + x)
                }
            }
            Compositor.repaint(document, flattened, left, top, right, bottom)
        }
        engine.clear()
        undo.cancel()
        strokeLayer = null
        bump()
    }

    /**
     * Zooms by [factor] about a point on screen, keeping that point still.
     *
     * Pinching about the centre of the screen rather than the centre of the
     * fingers is the difference between zooming into what you are looking at
     * and having the drawing slide away from under you.
     */
    fun zoomAround(focus: Offset, factor: Float, viewWidth: Int, viewHeight: Int) {
        val next = (zoom * factor).coerceIn(0.1f, 32f)
        val applied = next / zoom
        if (applied == 1f) return
        val centre = Offset(viewWidth / 2f, viewHeight / 2f)
        // The offset of the focus from the sheet's centre scales with the zoom;
        // the pan has to absorb the difference.
        val fromCentre = focus - centre - pan
        pan += fromCentre * (1f - applied)
        zoom = next
    }

    fun resetView() {
        zoom = 1f
        pan = Offset.Zero
    }

    /**
     * Writes the stroke so far into the layer and repaints only what moved.
     *
     * Recomputed from the pre-stroke pixels every frame rather than accumulated
     * onto the live ones, which is what stops a translucent brush from going
     * opaque under a slow finger. [UndoStack.originalAt] already has those
     * pixels, so this costs no extra memory.
     */
    private fun applyLive() {
        val layer = strokeLayer ?: return
        if (!engine.hasDirt) return
        val left = engine.dirtyLeft
        val top = engine.dirtyTop
        val right = engine.dirtyRight
        val bottom = engine.dirtyBottom
        undo.touch(layer, left, top, right, bottom)

        val ceiling = (activeOpacity.coerceIn(0f, 1f) * 255f).roundToInt()
        val erasing = tool == PaintTool.ERASER
        val alphaLocked = layer.alphaLocked
        val tint = colour

        for (y in top..bottom) {
            val row = y * document.width
            for (x in left..right) {
                val index = row + x
                val coverage = engine.coverageAt(index)
                val original = undo.originalAt(layer, index)
                layer.pixels[index] = when {
                    coverage == 0 -> original
                    erasing -> PaintOps.erasedPixel(original, coverage, ceiling)
                    else -> PaintOps.paintedPixel(original, coverage, tint, ceiling, alphaLocked)
                }
            }
        }

        Compositor.repaint(document, flattened, left, top, right, bottom)
        bump()
    }

    // -- One-shot tools ----------------------------------------------------

    fun tap(x: Int, y: Int) {
        when (tool) {
            PaintTool.BUCKET -> bucket(x, y)
            PaintTool.EYEDROPPER -> pick(x, y)
            PaintTool.MAGIC_ERASER -> magicErase(x, y)
            else -> Unit
        }
    }

    private fun bucket(x: Int, y: Int) {
        val layer = document.active ?: return
        if (layer.locked) return
        undo.begin("Fill", layer)
        undo.touchAll(layer)
        val mask = RegionFill.flood(
            source = if (contiguous) layer.pixels else layer.pixels,
            width = document.width,
            height = document.height,
            startX = x,
            startY = y,
            tolerance = tolerance,
            contiguous = contiguous,
            feather = 0,
        )
        // Grown by one pixel before filling, because an anti-aliased outline's
        // half-transparent pixels sit just outside any sane tolerance and a
        // fill that stops at them leaves a pale halo tracing every line.
        val grown = RegionFill.expand(mask, document.width, document.height, 1)
        RegionFill.apply(layer, grown, colour, 1f)
        undo.commit(layer)
        rememberColour()
        bumpAll()
    }

    private fun pick(x: Int, y: Int) {
        val picked = PaintOps.pick(document, x, y, wholeImage = true, flattened = flattened)
        if (Pixels.alpha(picked) == 0) return
        colour = Pixels.withAlpha(picked, 255)
        rememberColour()
        // Returning to the brush is what everybody expects after picking a
        // colour; staying on the dropper means the next tap picks again and
        // the colour you just chose is thrown away.
        tool = if (previousTool == PaintTool.EYEDROPPER) PaintTool.BRUSH else previousTool
    }

    private fun magicErase(x: Int, y: Int) {
        val layer = document.active ?: return
        if (layer.locked) return
        undo.begin("Magic erase", layer)
        undo.touchAll(layer)
        MagicEraser.erase(layer, x, y, tolerance, contiguous, featherEdges)
        undo.commit(layer)
        bumpAll()
    }

    // -- Big operations ----------------------------------------------------

    /**
     * Runs the auto cutout on the active layer.
     *
     * Deliberately synchronous and deliberately behind a "working" flag: on a
     * large photograph this is a second or two of real arithmetic, and pushing
     * it onto a background thread would mean the layer being rewritten while a
     * stroke might be running on it. Blocking with an honest message is better
     * than a race that corrupts somebody's drawing.
     */
    fun autoCutout(invert: Boolean = false) {
        val layer = document.active ?: return
        if (layer.locked) return
        busy = "Working out the background"
        try {
            val options = AutoCutout.Options(keep = cutoutKeep, edgeSoftness = featherEdges + 1)
            val mask = AutoCutout.mask(layer.pixels, document.width, document.height, options)
            undo.begin("Auto cutout", layer)
            undo.touchAll(layer)
            AutoCutout.apply(layer, mask, options, invert)
            undo.commit(layer)
            cutoutConfidence = AutoCutout.confidence(mask, document.width, document.height)
            bumpAll()
        } finally {
            busy = null
        }
    }

    /** Erases the paper colour from a scan, keeping the ink's soft edges. */
    fun liftLineArt() {
        val layer = document.active ?: return
        if (layer.locked) return
        // Sampled from the corners: whatever the paper is, it is at the edges.
        val corners = listOf(
            layer[0, 0], layer[document.width - 1, 0],
            layer[0, document.height - 1], layer[document.width - 1, document.height - 1],
        ).filter { Pixels.alpha(it) > 0 }
        val paper = if (corners.isEmpty()) 0xFFFFFFFF.toInt() else corners.maxBy {
            Pixels.red(it) + Pixels.green(it) + Pixels.blue(it)
        }
        undo.begin("Lift line art", layer)
        undo.touchAll(layer)
        MagicEraser.liftLineArt(layer, paper)
        undo.commit(layer)
        bumpAll()
    }

    // -- Layers ------------------------------------------------------------

    fun addLayer() = structural("Add layer") { document.addLayer() }
    fun duplicateLayer() = structural("Duplicate layer") { document.duplicateActive() }
    fun deleteLayer() = structural("Delete layer") { document.removeActive() }
    fun mergeDown() = structural("Merge down") { document.mergeDown() }
    fun moveLayer(from: Int, to: Int) = structural("Reorder layers") { document.move(from, to) }

    fun clearLayer() {
        val layer = document.active ?: return
        if (layer.locked) return
        undo.begin("Clear layer", layer)
        undo.touchAll(layer)
        layer.clear()
        undo.commit(layer)
        bumpAll()
    }

    fun fillLayer() {
        val layer = document.active ?: return
        if (layer.locked) return
        undo.begin("Fill layer", layer)
        undo.touchAll(layer)
        layer.fill(colour)
        undo.commit(layer)
        bumpAll()
    }

    fun selectLayer(index: Int) {
        document.activeIndex = index
        layerRevision++
    }

    fun setLayerOpacity(value: Int) {
        document.active?.opacity = value.coerceIn(0, 255)
        bumpAll()
    }

    fun setLayerBlend(mode: BlendMode) {
        document.active?.blendMode = mode
        bumpAll()
    }

    fun toggleVisible(index: Int) {
        val layer = document.layers.getOrNull(index) ?: return
        layer.visible = !layer.visible
        bumpAll()
    }

    fun toggleAlphaLock() {
        document.active?.let { it.alphaLocked = !it.alphaLocked }
        layerRevision++
    }

    fun toggleClipping() {
        document.active?.let { it.clippedToBelow = !it.clippedToBelow }
        bumpAll()
    }

    private fun structural(label: String, action: () -> Unit) {
        undo.structural(label, document, action)
        bumpAll()
    }

    // -- History -----------------------------------------------------------

    fun undoStep() {
        if (undo.undo(document)) bumpAll()
    }

    fun redoStep() {
        if (undo.redo(document)) bumpAll()
    }

    // -- Colours -----------------------------------------------------------

    fun swapColours() {
        val held = colour
        colour = secondaryColour
        secondaryColour = held
    }

    private fun rememberColour() {
        val opaque = Pixels.withAlpha(colour, 255)
        recentColours = (listOf(opaque) + recentColours.filter { it != opaque }).take(18)
    }

    // -- Export ------------------------------------------------------------

    /** The whole painting as PNG bytes, background included. */
    fun exportPng(): ByteArray {
        val flat = Compositor.flatten(document)
        return PngWriter.encode(document.width, document.height, flat)
    }

    /** The painting with the background left out, for a cutout. */
    fun exportTransparentPng(): ByteArray {
        val flat = Compositor.flattenTransparent(document)
        return PngWriter.encode(document.width, document.height, flat)
    }

    /**
     * Replaces the active layer's pixels with an imported image, centred and
     * scaled down to fit.
     *
     * Scaled down but never up: enlarging an import to fill the canvas is
     * almost never what was wanted and destroys the resolution the auto cutout
     * needs to find a clean boundary.
     */
    fun placeImage(source: IntArray, sourceWidth: Int, sourceHeight: Int) {
        val layer = document.active ?: return
        if (layer.locked || sourceWidth <= 0 || sourceHeight <= 0) return
        undo.begin("Place image", layer)
        undo.touchAll(layer)

        val scale = minOf(
            document.width.toFloat() / sourceWidth,
            document.height.toFloat() / sourceHeight,
            1f,
        )
        val drawWidth = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
        val drawHeight = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        val offsetX = (document.width - drawWidth) / 2
        val offsetY = (document.height - drawHeight) / 2

        for (y in 0 until drawHeight) {
            val sy = (y * sourceHeight / drawHeight).coerceIn(0, sourceHeight - 1)
            for (x in 0 until drawWidth) {
                val sx = (x * sourceWidth / drawWidth).coerceIn(0, sourceWidth - 1)
                layer[offsetX + x, offsetY + y] = source[sy * sourceWidth + sx]
            }
        }
        undo.commit(layer)
        bumpAll()
    }
}
