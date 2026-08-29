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
import com.mcguidesigner.core.paint.distanceBetween
import com.mcguidesigner.core.paint.Guides
import com.mcguidesigner.core.paint.MagicEraser
import com.mcguidesigner.core.paint.MarqueeShape
import com.mcguidesigner.core.paint.PaintBackground
import com.mcguidesigner.core.paint.PaintDocument
import com.mcguidesigner.core.paint.PaintLayer
import com.mcguidesigner.core.paint.PaintOps
import com.mcguidesigner.core.paint.PaintSelection
import com.mcguidesigner.core.paint.Pixels
import com.mcguidesigner.core.paint.RecognisedShape
import com.mcguidesigner.core.paint.RegionFill
import com.mcguidesigner.core.paint.Ruler
import com.mcguidesigner.core.paint.RulerGuide
import com.mcguidesigner.core.paint.RulerKind
import com.mcguidesigner.core.paint.ScribbleSelection
import com.mcguidesigner.core.paint.SelectMode
import com.mcguidesigner.core.paint.SelectionOutline
import com.mcguidesigner.core.paint.ShapeGuess
import com.mcguidesigner.core.paint.ShapeRecogniser
import com.mcguidesigner.core.paint.Stabilizer
import com.mcguidesigner.core.paint.StrokeEngine
import com.mcguidesigner.core.paint.StrokePoint
import com.mcguidesigner.core.paint.UndoStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** The tools on the wheel. */
enum class PaintTool(val label: String) {
    BRUSH("Brush"),
    ERASER("Eraser"),
    BUCKET("Bucket"),
    EYEDROPPER("Eyedropper"),
    MAGIC_ERASER("Magic Eraser"),
    SHAPE("Shape"),
    SMUDGE("Smudge"),
    BLUR("Blur"),
    MARQUEE("Marquee"),
    LASSO("Lasso"),
    MAGIC_WAND("Magic Wand"),
    RULER("Ruler"),
    PAN("Pan"),
    ;

    /** Whether this tool draws with the brush engine. */
    val isStroke: Boolean get() = this == BRUSH || this == ERASER || this == SMUDGE || this == BLUR

    /**
     * Whether this tool follows a drag but paints nothing while it does.
     *
     * The magic eraser and the shape tool both collect a path and show it as an
     * overlay, then act once the finger lifts - the eraser because what it
     * removes depends on everywhere the scribble went, the shape tool because
     * what it draws depends on the shape of the whole drag. Neither can commit
     * anything mid-gesture without being wrong most of the way through. The two
     * selection drags are the same shape of thing, and the ruler is a drag that
     * moves a guide rather than the artwork.
     */
    val isGesture: Boolean
        get() = this == MAGIC_ERASER || this == SHAPE ||
            this == LASSO || this == MARQUEE || this == RULER

    /** Whether this tool makes a selection rather than marks. */
    val isSelection: Boolean get() = this == MARQUEE || this == LASSO || this == MAGIC_WAND
}

/** What the canvas is drawing over the artwork, if anything. */
enum class PaintOverlay { NONE, SCRIBBLE, SHAPE, LASSO, MARQUEE, RULER }

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

    // `blankFlatten`, not `flatten`: the document was built two lines ago and
    // has nothing on it, so compositing it is a full pass over every pixel to
    // discover that. See Compositor.blankFlatten.
    private var flattened = Compositor.blankFlatten(document)
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

    // -- The ruler ---------------------------------------------------------
    //
    // A shape strokes are held to. Not a tool you draw with: a thing you put
    // down and then draw against, which is what makes clean line art possible
    // with a finger. See core.paint.Ruler for the arithmetic.

    var ruler by mutableStateOf(RulerGuide())
        private set

    /** Which of the ruler's control points a drag is moving. */
    private var rulerHandle = 0

    fun setRulerKind(kind: RulerKind) {
        ruler = if (kind == RulerKind.OFF) {
            ruler.copy(kind = RulerKind.OFF)
        } else if (ruler.kind == RulerKind.OFF) {
            // First time it is switched on, put it somewhere it can be seen
            // rather than at the origin, where it looks like nothing happened.
            RulerGuide.placed(kind, document.width, document.height)
                .copy(angle = ruler.angle, slices = ruler.slices, flatten = ruler.flatten)
        } else {
            ruler.copy(kind = kind)
        }
    }

    fun setRulerAngle(degrees: Float) {
        ruler = ruler.copy(angle = degrees)
    }

    fun setRulerSlices(count: Int) {
        ruler = ruler.copy(slices = count.coerceIn(2, 48))
    }

    fun setRulerFlatten(value: Float) {
        ruler = ruler.copy(flatten = value.coerceIn(0.05f, 1f))
    }

    fun centreRuler() {
        ruler = RulerGuide.placed(ruler.kind, document.width, document.height)
            .copy(angle = ruler.angle, slices = ruler.slices, flatten = ruler.flatten)
    }

    // -- The selection -----------------------------------------------------
    //
    // When there is one, it is where everything happens: strokes, the eraser,
    // the bucket, blur, smudge, fill and clear are all confined to it. That is
    // the whole point of having one, and a selection that only some tools
    // respect is worse than none - it teaches you not to trust it.

    var selection by mutableStateOf<PaintSelection?>(null)
        private set

    /** The boundary, for the marching ants. Recomputed when the selection is. */
    var selectionOutline by mutableStateOf<SelectionOutline?>(null)
        private set

    /** How the next selection combines with this one. */
    var selectMode by mutableStateOf(SelectMode.REPLACE)

    var marqueeShape by mutableStateOf(MarqueeShape.RECTANGLE)

    /** How many pixels Expand, Contract and Soften move the edge by. */
    var selectionStep by mutableStateOf(4)

    val hasSelection: Boolean get() = selection?.isEmpty == false
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

    // -- The cost meter -----------------------------------------------------
    //
    // How many pixels the tools have re-blended. Not a statistic and not
    // diagnostics: it is what `StrokeCostTest` measures, and it is measured in
    // pixels rather than milliseconds because a millisecond budget on a shared
    // CI runner is a coin toss and this number is identical on every machine.
    //
    // The regression it guards is the one that made drawing feel like wading:
    // every tool recomputes from the pre-stroke original, and doing that over
    // the whole accumulated rectangle of the stroke made the cost of a line
    // quadratic in its length.

    var blendedPixels: Long = 0L
        private set

    fun resetBlendCounter() {
        blendedPixels = 0L
    }

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
        patchSurface?.dispose()
        patchSurface = null
        patchWidth = 0
        patchHeight = 0
    }

    private fun pushToSurface() {
        surface?.update(flattened)
    }

    fun newDocument(width: Int, height: Int, background: PaintBackground) {
        document = PaintDocument.blank(width, height, background)
        undo.clear()
        engine = StrokeEngine(width, height)
        flattened = Compositor.blankFlatten(document)
        surface?.dispose()
        surface = PaintSurface(width, height)
        patchSurface?.dispose()
        patchSurface = null
        patchWidth = 0
        patchHeight = 0
        pushToSurface()
        zoom = 1f
        pan = Offset.Zero
        cutoutConfidence = null
        selection = null
        selectionOutline = null
        ruler = RulerGuide()
        bumpAll()
    }

    /**
     * The canvas changed inside a rectangle. Uploads only that rectangle.
     *
     * The hot path, called on every frame of a stroke. A full upload here was
     * the single biggest cause of the canvas feeling like wading: nine
     * megabytes copied into the native bitmap sixty times a second, to show a
     * change covering a few thousand pixels.
     */
    private fun bumpRegion(left: Int, top: Int, right: Int, bottom: Int) {
        val x0 = left.coerceIn(0, document.width - 1)
        val y0 = top.coerceIn(0, document.height - 1)
        val x1 = right.coerceIn(0, document.width - 1)
        val y1 = bottom.coerceIn(0, document.height - 1)
        if (x1 < x0 || y1 < y0) return
        surface?.updateRegion(flattened, x0, y0, x1 - x0 + 1, y1 - y0 + 1)
        revision++
    }

    // -- The stroke patch ---------------------------------------------------
    //
    // Writing into the canvas bitmap invalidates it *entirely* as far as the
    // renderer is concerned: the platform tracks a generation counter on the
    // bitmap, not a dirty rectangle, so changing one pixel of a 1536-square
    // canvas re-uploads nine megabytes to the GPU on the next frame. Narrowing
    // the pixel copy - which 2.1.1 did - saves the copy and not the upload,
    // which is why drawing still dragged.
    //
    // So during a stroke the canvas bitmap is not touched at all. The changed
    // rectangle is mirrored into a second, small surface, and the canvas draws
    // the big bitmap and then this patch on top of the stale area. Only the
    // patch is re-uploaded per frame, and it is the size of the stroke rather
    // than the size of the document. The two are reconciled once, when the
    // finger lifts.

    var patchSurface: PaintSurface? = null
        private set

    /** The document-space rectangle [patchSurface] currently mirrors. */
    var patchLeft = 0
        private set
    var patchTop = 0
        private set
    var patchWidth = 0
        private set
    var patchHeight = 0
        private set

    val patchActive: Boolean get() = patchWidth > 0 && patchHeight > 0 && patchSurface != null

    /**
     * Mirrors the stroke into the patch, copying only what changed.
     *
     * The patch covers everything the stroke has touched, because the canvas
     * bitmap underneath it is stale over exactly that area. But only a
     * brush-sized piece of it changes per event, so only that piece is copied -
     * placed at its offset within the patch rather than rewritten from the
     * corner. Copying the whole rectangle every frame, which is what this did
     * before, moves a million pixels per frame by the middle of a long stroke
     * to show a change covering a few thousand.
     *
     * The patch keeps a fixed origin for as long as it can. A stroke that grows
     * down and right - most of them - only ever adds a band along an edge,
     * which is filled once. A stroke that doubles back above or left of its own
     * origin cannot keep the anchor, so the patch is rebuilt; that is rare and
     * costs one full copy.
     *
     * Returns false when the stroke has grown large enough that the patch has
     * stopped being a saving, at which point the caller falls back to writing
     * into the canvas bitmap - no worse than before, and only for strokes that
     * genuinely cover most of the document.
     */
    private fun updatePatch(
        stepLeft: Int,
        stepTop: Int,
        stepRight: Int,
        stepBottom: Int,
    ): Boolean {
        val left = strokeBounds[0]
        val top = strokeBounds[1]
        val right = strokeBounds[2]
        val bottom = strokeBounds[3]
        if (right < left || bottom < top) return false

        val width = right - left + 1
        val height = bottom - top + 1
        val documentArea = document.width.toLong() * document.height
        if (width.toLong() * height > documentArea / 2) return false

        val surface = patchSurface
        val anchorMoved = patchWidth == 0 || left < patchLeft || top < patchTop
        val outgrown = surface == null ||
            surface.width < (right - patchLeft + 1) ||
            surface.height < (bottom - patchTop + 1)

        if (anchorMoved || outgrown) {
            if (surface == null || anchorMoved || outgrown) {
                surface?.dispose()
                // Rounded up in chunks so a growing stroke does not reallocate
                // on every frame; a 128-pixel step is one reallocation per few
                // dozen.
                val capacityW = ((width + 127) / 128 * 128).coerceAtMost(document.width)
                val capacityH = ((height + 127) / 128 * 128).coerceAtMost(document.height)
                patchSurface = PaintSurface(capacityW, capacityH)
            }
            patchLeft = left
            patchTop = top
            patchWidth = width
            patchHeight = height
            // A fresh surface holds nothing, so this one copy is not optional.
            patchSurface?.updateFrom(flattened, document.width, left, top, 0, 0, width, height)
            revision++
            return true
        }

        // The anchor held. Fill whatever the rectangle just gained along its
        // right and bottom edges, because those pixels have never been copied
        // and the patch is drawn over them.
        val newWidth = right - patchLeft + 1
        val newHeight = bottom - patchTop + 1
        if (newWidth > patchWidth) {
            patchSurface?.updateFrom(
                flattened, document.width,
                patchLeft + patchWidth, patchTop,
                patchWidth, 0,
                newWidth - patchWidth, patchHeight,
            )
        }
        if (newHeight > patchHeight) {
            patchSurface?.updateFrom(
                flattened, document.width,
                patchLeft, patchTop + patchHeight,
                0, patchHeight,
                newWidth, newHeight - patchHeight,
            )
        }
        patchWidth = newWidth
        patchHeight = newHeight

        // And the pixels this event actually changed.
        val sx = stepLeft.coerceAtLeast(patchLeft)
        val sy = stepTop.coerceAtLeast(patchTop)
        val sw = stepRight - sx + 1
        val sh = stepBottom - sy + 1
        if (sw > 0 && sh > 0) {
            patchSurface?.updateFrom(
                flattened, document.width,
                sx, sy,
                sx - patchLeft, sy - patchTop,
                sw, sh,
            )
        }
        revision++
        return true
    }

    /** Folds the patch back into the canvas bitmap and puts it away. */
    private fun retirePatch() {
        if (patchWidth > 0 && patchHeight > 0) {
            surface?.updateRegion(flattened, patchLeft, patchTop, patchWidth, patchHeight)
        }
        patchWidth = 0
        patchHeight = 0
        revision++
    }

    /**
     * Recomposites the whole document and uploads all of it.
     *
     * Genuinely expensive - every pixel through the blend stack, then every
     * pixel into the bitmap - so it is reserved for changes with no bounds:
     * adding, deleting or reordering a layer, undo, a filter over everything.
     * A stroke must never come through here.
     */
    private fun bumpAll() {
        flattened = Compositor.flatten(document, flattened)
        pushToSurface()
        revision++
        layerRevision++
    }

    /**
     * Recomposites everything without disturbing the layer panel.
     *
     * For changes to how existing layers combine - opacity, blend mode,
     * visibility - where the list itself has not changed shape. Bumping
     * `layerRevision` there would rebuild every row and its thumbnail on every
     * frame of an opacity drag.
     */
    fun refresh() {
        flattened = Compositor.flatten(document, flattened)
        pushToSurface()
        revision++
    }

    // -- The gesture overlay ------------------------------------------------
    //
    // What the magic eraser and the shape tool draw while a finger is down.
    // Neither writes to the layer until the gesture ends, so this is the only
    // thing on screen showing what is about to happen - which makes it part of
    // the tool rather than decoration on top of one.

    var overlay by mutableStateOf(PaintOverlay.NONE)
        private set

    /**
     * The live path, in canvas coordinates.
     *
     * A plain list plus a counter rather than a `mutableStateOf(List)`: the
     * path grows on every pointer event and replacing an immutable list each
     * time would allocate a new one per frame for the collector to clean up.
     */
    val overlayPath = ArrayList<StrokePoint>()

    var overlayRevision by mutableStateOf(0)
        private set

    /** What the shape tool currently thinks the drag is. Null until it is sure. */
    var shapeGuess by mutableStateOf<ShapeGuess?>(null)
        private set

    /** Filled in after a scribble erase, so the result can be reported. */
    var lastScribbleShape by mutableStateOf<String?>(null)

    fun gestureStart(x: Float, y: Float) {
        overlayPath.clear()
        overlayPath.add(StrokePoint(x, y))
        shapeGuess = null
        overlay = when (tool) {
            PaintTool.SHAPE -> PaintOverlay.SHAPE
            PaintTool.LASSO -> PaintOverlay.LASSO
            PaintTool.MARQUEE -> PaintOverlay.MARQUEE
            PaintTool.RULER -> PaintOverlay.RULER
            else -> PaintOverlay.SCRIBBLE
        }
        if (overlay == PaintOverlay.RULER) rulerHandle = nearestRulerHandle(x, y)
        overlayRevision++
    }

    fun gestureMove(x: Float, y: Float) {
        if (overlay == PaintOverlay.NONE) return
        // The ruler is the one drag that changes something while it is
        // happening: it moves a guide rather than collecting a path, and a
        // guide that only appeared where you let go would be placed by trial
        // and error.
        if (overlay == PaintOverlay.RULER) {
            dragRuler(x, y)
            overlayRevision++
            return
        }
        val last = overlayPath.lastOrNull()
        // Thinned: a fast drag delivers far more points than the recogniser or
        // the trail can use, and the extras only slow both down.
        if (last != null && distanceBetween(last.x, last.y, x, y) < 1.5f) {
            // ...except a marquee, which is two corners rather than a path:
            // thinning it throws away the only point that matters, the one
            // under the finger now. The first corner is never replaced - it is
            // the anchor the box is measured from.
            if (overlay != PaintOverlay.MARQUEE) return
            if (overlayPath.size == 1) {
                overlayPath.add(StrokePoint(x, y))
            } else {
                overlayPath[overlayPath.lastIndex] = StrokePoint(x, y)
            }
            overlayRevision++
            return
        }
        overlayPath.add(StrokePoint(x, y))
        if (overlay == PaintOverlay.SHAPE && overlayPath.size % 4 == 0) {
            // Recognised live, so the preview settles as the shape closes
            // rather than appearing from nowhere when the finger lifts.
            shapeGuess = ShapeRecogniser.recognise(overlayPath)
        }
        overlayRevision++
    }

    suspend fun gestureEnd() {
        val path = overlayPath.toList()
        val mode = overlay
        overlay = PaintOverlay.NONE
        overlayPath.clear()
        overlayRevision++
        if (path.isEmpty()) {
            shapeGuess = null
            return
        }
        when (mode) {
            PaintOverlay.SCRIBBLE -> scribbleErase(path)
            PaintOverlay.SHAPE -> drawRecognisedShape(path)
            PaintOverlay.LASSO -> lassoSelect(path)
            PaintOverlay.MARQUEE -> marqueeSelect(path.first(), path.last())
            PaintOverlay.RULER, PaintOverlay.NONE -> Unit
        }
        shapeGuess = null
    }

    /** Which of the ruler's control points a touch at this point should move. */
    private fun nearestRulerHandle(x: Float, y: Float): Int {
        if (!ruler.kind.isPerspective || ruler.kind == RulerKind.PERSPECTIVE_1) return 0
        val candidates = buildList {
            add(0 to (ruler.x to ruler.y))
            add(2 to (ruler.x2 to ruler.y2))
            if (ruler.kind == RulerKind.PERSPECTIVE_3) add(3 to (ruler.x3 to ruler.y3))
        }
        return candidates.minBy { (_, point) ->
            val dx = point.first - x
            val dy = point.second - y
            dx * dx + dy * dy
        }.first
    }

    private fun dragRuler(x: Float, y: Float) {
        ruler = when (rulerHandle) {
            2 -> ruler.copy(x2 = x, y2 = y)
            3 -> ruler.copy(x3 = x, y3 = y)
            else -> ruler.copy(x = x, y = y)
        }
    }

    private suspend fun lassoSelect(path: List<StrokePoint>) {
        buildSelection("Working out what you drew round") {
            PaintSelection.lasso(path, document.width, document.height, featherEdges)
        }
    }

    private suspend fun marqueeSelect(from: StrokePoint, to: StrokePoint) {
        // A tap rather than a drag means "let go of the selection", which is
        // what clicking outside one does in every editor there has ever been.
        if (distanceBetween(from.x, from.y, to.x, to.y) < 2f) {
            deselect()
            return
        }
        buildSelection("Selecting") {
            PaintSelection.marquee(
                document.width, document.height,
                from.x, from.y, to.x, to.y,
                marqueeShape, featherEdges,
            )
        }
    }

    fun gestureCancel() {
        overlay = PaintOverlay.NONE
        overlayPath.clear()
        shapeGuess = null
        overlayRevision++
    }

    /**
     * Erases everything the scribble passed over.
     *
     * The band under the stroke scales with the brush size, so the same size
     * slider that controls the brush controls how wide a swipe counts - there
     * is no reason for this tool to have a second, separate width.
     */
    private suspend fun scribbleErase(path: List<StrokePoint>) {
        val layer = document.active ?: return
        if (layer.locked) return
        val radius = (activeSize / 2f).roundToInt().coerceIn(2, 80)
        val removed = heavy("Removing what you scribbled over") {
            val mask = ScribbleSelection.select(
                source = layer.pixels,
                width = document.width,
                height = document.height,
                path = path,
                radius = radius,
                tolerance = tolerance,
                contiguous = contiguous,
                feather = featherEdges + 1,
            )
            undo.begin("Magic erase", layer)
            undo.touchAll(layer)
            val changed = ScribbleSelection.erase(layer, mask)
            undo.commit(layer)
            changed
        }
        lastScribbleShape = if (removed) null else "Nothing matched that scribble."
        refreshInBackground()
    }

    /** Lays down whatever the drag turned out to be. */
    private suspend fun drawRecognisedShape(path: List<StrokePoint>) {
        val layer = document.active ?: return
        if (layer.locked) return
        val guess = ShapeRecogniser.recognise(path)
        val outline = if (guess.shape == RecognisedShape.FREEHAND) path else ShapeRecogniser.toStrokePath(guess)

        undo.begin(guess.shape.label, layer)
        val b = brush
        engine.clear()
        outline.forEachIndexed { index, point ->
            if (index == 0) engine.begin(point, b) else engine.extendTo(point, b, 0f)
        }
        if (engine.hasDirt) {
            undo.touch(layer, engine.dirtyLeft, engine.dirtyTop, engine.dirtyRight, engine.dirtyBottom)
        }
        PaintOps.paint(layer, engine, colour, activeOpacity)
        undo.commit(layer)
        lastScribbleShape = guess.shape.label
        rememberColour()
        refreshInBackground()
    }

    // -- Strokes -----------------------------------------------------------

    private var strokeLayer: PaintLayer? = null
    private var strokeLength = 0f
    private var lastPoint = Offset.Zero

    /**
     * Where this stroke started, unsnapped.
     *
     * The ruler needs it: which of a family of parallel lines, or which
     * concentric circle, a stroke belongs to is decided by where it began. See
     * core.paint.Ruler.
     */
    private var rulerAnchor: StrokePoint? = null

    /** [x], [y] moved onto the ruler, if there is one. */
    private fun onRuler(x: Float, y: Float): StrokePoint {
        val point = StrokePoint(x, y)
        if (!ruler.isOn) return point
        return Ruler.snap(ruler, rulerAnchor, point)
    }

    /** Everything this stroke has touched: left, top, right, bottom. */
    private val strokeBounds = intArrayOf(0, 0, -1, -1)

    /**
     * The stabilised path of the stroke in progress.
     *
     * Only smudge needs it - it drags colour *along* the stroke, so it needs
     * the order the points arrived in and not just the area they covered. Kept
     * as canvas coordinates and thinned to one point per two pixels, because a
     * fast drag can deliver a thousand events and smudging every one of them
     * moves the reservoir no further while costing a hundred times as much.
     */
    private val strokePath = ArrayList<StrokePoint>()

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
        strokeBounds[0] = 0
        strokeBounds[1] = 0
        strokeBounds[2] = -1
        strokeBounds[3] = -1
        strokePath.clear()
        smudgeConsumed = 0
        smudgeCarry = null
        stabilizer.reset(stabilizerStrength)
        rulerAnchor = if (ruler.isOn) StrokePoint(x, y) else null
        val (rx, ry) = onRuler(x, y).let { it.x to it.y }
        val (sx, sy) = stabilizer.push(rx, ry)

        undo.begin(strokeLabel(), layer)
        val b = brush
        strokePath.add(StrokePoint(sx, sy))
        reflections(StrokePoint(sx, sy)).forEachIndexed { channel, point ->
            if (channel == 0) engine.begin(point, b) else engine.beginChannel(channel, point, b)
        }
        applyLive()
    }

    fun strokeMove(x: Float, y: Float) {
        if (strokeLayer == null) return
        // The ruler moves the point before the stabilizer smooths it, not
        // after: smoothing a snapped point pulls it back off the line, which
        // makes a ruled stroke wobble exactly as much as the stabilizer is set
        // to smooth - a straight edge that is not straight.
        val onGuide = onRuler(x, y)
        val (sx, sy) = stabilizer.push(onGuide.x, onGuide.y)
        strokeLength += (Offset(sx, sy) - lastPoint).getDistance()
        lastPoint = Offset(sx, sy)
        val b = brush
        val last = strokePath.lastOrNull()
        if (last == null || distanceBetween(last.x, last.y, sx, sy) >= 2f) {
            strokePath.add(StrokePoint(sx, sy))
        }
        reflections(StrokePoint(sx, sy)).forEachIndexed { channel, point ->
            engine.extendTo(point, b, strokeLength, channel)
        }
        applyLive()
    }

    fun strokeEnd(x: Float, y: Float) {
        val layer = strokeLayer ?: return
        val b = brush
        val last = onRuler(x, y)
        stabilizer.drain(last.x, last.y).forEach { (dx, dy) ->
            reflections(StrokePoint(dx, dy)).forEachIndexed { channel, point ->
                engine.extendTo(point, b, strokeLength, channel)
            }
        }
        applyLive()
        val left = strokeBounds[0]
        val top = strokeBounds[1]
        val right = strokeBounds[2]
        val bottom = strokeBounds[3]
        engine.clear()
        undo.commit(layer)
        strokeLayer = null
        rulerAnchor = null
        rememberColour()
        // The layer list has not changed shape, and the pixels outside the
        // stroke have not moved - so neither a full recomposite nor a full
        // upload is needed to finish a stroke. Only the layer panel's
        // thumbnails are stale, and they are only on screen if it is open.
        if (right >= left && bottom >= top) {
            surface?.updateRegion(flattened, left, top, right - left + 1, bottom - top + 1)
        }
        retirePatch()
        layerRevision++
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
            surface?.updateRegion(flattened, left, top, right - left + 1, bottom - top + 1)
        }
        retirePatch()
        engine.clear()
        undo.cancel()
        strokeLayer = null
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
        when (tool) {
            PaintTool.SMUDGE -> applySmudge(layer)
            PaintTool.BLUR -> applyBlur(layer)
            else -> applyPaintOrErase(layer)
        }
    }

    private fun applyPaintOrErase(layer: PaintLayer) {
        // The step, not the whole stroke. A pixel whose coverage did not change
        // this event blends to the value it already holds, so revisiting it
        // costs time and changes nothing - see StrokeEngine.stepLeft.
        if (!engine.hasStep) return
        val left = engine.stepLeft
        val top = engine.stepTop
        val right = engine.stepRight
        val bottom = engine.stepBottom
        engine.consumeStep()
        undo.touch(layer, left, top, right, bottom)

        val ceiling = (activeOpacity.coerceIn(0f, 1f) * 255f).roundToInt()
        val erasing = tool == PaintTool.ERASER
        val alphaLocked = layer.alphaLocked
        val tint = colour

        val regionWidth = right - left + 1
        val needed = regionWidth * (bottom - top + 1)
        if (scratch.size < needed) scratch = IntArray(needed)
        undo.readOriginalInto(layer, left, top, right, bottom, scratch)

        blendedPixels += regionWidth.toLong() * (bottom - top + 1)
        for (y in top..bottom) {
            val row = y * document.width
            val scratchRow = (y - top) * regionWidth
            for (x in left..right) {
                val index = row + x
                val original = scratch[scratchRow + (x - left)]
                // The selection is a second coverage: outside it the brush has
                // no reach at all, and on a feathered edge it has some. One
                // multiply is the whole of confining a stroke.
                val coverage = Pixels.mul(engine.coverageAt(index), selectionAt(index))
                layer.pixels[index] = when {
                    coverage == 0 -> original
                    erasing -> PaintOps.erasedPixel(original, coverage, ceiling)
                    else -> PaintOps.paintedPixel(original, coverage, tint, ceiling, alphaLocked)
                }
            }
        }

        Compositor.repaint(document, flattened, left, top, right, bottom)
        growStroke(left, top, right, bottom)
        if (!updatePatch(left, top, right, bottom)) bumpRegion(left, top, right, bottom)
    }

    /**
     * Blur, recomputed from the pre-stroke pixels like every other tool.
     *
     * The neighbourhood read extends past the dirty rectangle by the radius,
     * because a pixel on the very edge of the brush still averages pixels
     * outside it. Reading only the dirty rectangle would darken the rim of
     * every blur stroke against its own boundary.
     */
    private fun applyBlur(layer: PaintLayer) {
        if (!engine.hasStep) return
        val radius = blurRadius
        val left = engine.stepLeft
        val top = engine.stepTop
        val right = engine.stepRight
        val bottom = engine.stepBottom
        engine.consumeStep()
        undo.touch(layer, left, top, right, bottom)

        val readLeft = (left - radius).coerceAtLeast(0)
        val readTop = (top - radius).coerceAtLeast(0)
        val readRight = (right + radius).coerceAtMost(document.width - 1)
        val readBottom = (bottom + radius).coerceAtMost(document.height - 1)
        val readWidth = readRight - readLeft + 1
        val readHeight = readBottom - readTop + 1
        val needed = readWidth * readHeight
        if (scratch.size < needed) scratch = IntArray(needed)
        // The originals of the *read* area, which is wider than what is written.
        undo.touch(layer, readLeft, readTop, readRight, readBottom)
        undo.readOriginalInto(layer, readLeft, readTop, readRight, readBottom, scratch)

        val ceiling = (activeOpacity.coerceIn(0f, 1f) * 255f).roundToInt()
        blendedPixels += (right - left + 1).toLong() * (bottom - top + 1)
        for (y in top..bottom) {
            val row = y * document.width
            for (x in left..right) {
                val index = row + x
                val coverage = Pixels.mul(engine.coverageAt(index), selectionAt(index))
                val original = scratch[(y - readTop) * readWidth + (x - readLeft)]
                if (coverage == 0) {
                    layer.pixels[index] = original
                    continue
                }
                val blurred = PaintOps.blurredPixel(
                    scratch, readLeft, readTop, readWidth, readHeight, x, y, radius,
                )
                layer.pixels[index] = PaintOps.mixPixels(original, blurred, Pixels.mul(coverage, ceiling))
            }
        }

        Compositor.repaint(document, flattened, left, top, right, bottom)
        growStroke(left, top, right, bottom)
        if (!updatePatch(left, top, right, bottom)) bumpRegion(left, top, right, bottom)
    }

    /**
     * Smudge, applied to the path points that have arrived since last time.
     *
     * The one tool that cannot be recomputed from the pre-stroke pixels: a
     * smear is the accumulation of every step, in order, so it is written into
     * the layer as it goes and the undo tiles are what make it reversible.
     */
    private fun applySmudge(layer: PaintLayer) {
        if (smudgeConsumed >= strokePath.size) return
        val radius = (activeSize / 2f).roundToInt().coerceIn(1, 96)
        val segment = strokePath.subList(smudgeConsumed, strokePath.size).toList()
        smudgeConsumed = strokePath.size

        var left = document.width
        var top = document.height
        var right = -1
        var bottom = -1
        segment.forEach { point ->
            val cx = point.x.roundToInt()
            val cy = point.y.roundToInt()
            left = minOf(left, cx - radius)
            top = minOf(top, cy - radius)
            right = maxOf(right, cx + radius)
            bottom = maxOf(bottom, cy + radius)
        }
        left = left.coerceIn(0, document.width - 1)
        top = top.coerceIn(0, document.height - 1)
        right = right.coerceIn(0, document.width - 1)
        bottom = bottom.coerceIn(0, document.height - 1)
        if (right < left || bottom < top) return

        undo.touch(layer, left, top, right, bottom)
        smudgeCarry = PaintOps.smudge(layer, segment, radius, activeOpacity, smudgeCarry)
        confineToSelection(layer, left, top, right, bottom)

        Compositor.repaint(document, flattened, left, top, right, bottom)
        growStroke(left, top, right, bottom)
        if (!updatePatch(left, top, right, bottom)) bumpRegion(left, top, right, bottom)
    }

    /** How far the blur tool reaches, in pixels, from the brush size. */
    private val blurRadius: Int get() = (activeSize / 6f).roundToInt().coerceIn(1, 24)

    private var smudgeConsumed = 0
    private var smudgeCarry: Int? = null

    /**
     * Scratch space for the pre-stroke pixels of the region being redrawn.
     *
     * Held rather than allocated per frame: it grows to the largest region a
     * stroke has needed and then stops, where allocating it each frame would
     * hand the collector a few hundred kilobytes a second during every drag.
     */
    private var scratch = IntArray(0)

    /** What the undo entry for this stroke is called. */
    private fun strokeLabel(): String = when (tool) {
        PaintTool.ERASER -> "Erase"
        PaintTool.SMUDGE -> "Smudge"
        PaintTool.BLUR -> "Blur"
        else -> "Brush"
    }

    private fun growStroke(left: Int, top: Int, right: Int, bottom: Int) {
        if (strokeBounds[2] < strokeBounds[0]) {
            strokeBounds[0] = left
            strokeBounds[1] = top
            strokeBounds[2] = right
            strokeBounds[3] = bottom
            return
        }
        if (left < strokeBounds[0]) strokeBounds[0] = left
        if (top < strokeBounds[1]) strokeBounds[1] = top
        if (right > strokeBounds[2]) strokeBounds[2] = right
        if (bottom > strokeBounds[3]) strokeBounds[3] = bottom
    }

    // -- One-shot tools ----------------------------------------------------

    /**
     * A tap with a one-shot tool.
     *
     * Suspending because two of the three do real work over every pixel of the
     * canvas - a flood fill on a 1536-square document is two and a third
     * million pixels - and doing that on the frame thread is a visible freeze
     * rather than a pause.
     */
    suspend fun tap(x: Int, y: Int) {
        when (tool) {
            PaintTool.BUCKET -> bucket(x, y)
            PaintTool.EYEDROPPER -> pick(x, y)
            PaintTool.MAGIC_WAND -> wandAt(x, y)
            else -> Unit
        }
    }

    /**
     * Runs [work] off the frame thread with a message on screen.
     *
     * The message is the point. An earlier version set `busy` and then did the
     * work synchronously, which meant the overlay never painted: the frame that
     * would have drawn it was the frame that was blocked. The app simply froze
     * for a few seconds and looked crashed. Compose state is only written
     * either side of the `withContext`, never inside it, so nothing observable
     * is touched from another thread.
     */
    private suspend fun <T> heavy(message: String, work: () -> T): T {
        busy = message
        return try {
            withContext(Dispatchers.Default) { work() }
        } finally {
            busy = null
        }
    }

    /**
     * Recomposites off-thread, then uploads and repaints on the caller's.
     *
     * Flattening is the expensive half and has no reason to be on the frame
     * thread; the upload has to be paired with the state write that triggers
     * the repaint, so it stays here.
     */
    private suspend fun refreshInBackground() {
        withContext(Dispatchers.Default) {
            flattened = Compositor.flatten(document, flattened)
        }
        pushToSurface()
        revision++
        layerRevision++
    }

    private suspend fun bucket(x: Int, y: Int) {
        val layer = document.active ?: return
        if (layer.locked) return
        heavy("Filling") {
            undo.begin("Fill", layer)
            undo.touchAll(layer)
            val mask = RegionFill.flood(
                source = layer.pixels,
                width = document.width,
                height = document.height,
                startX = x,
                startY = y,
                tolerance = tolerance,
                contiguous = contiguous,
                feather = 0,
            )
            // Grown by one pixel before filling, because an anti-aliased
            // outline's half-transparent pixels sit just outside any sane
            // tolerance and a fill that stops at them leaves a pale halo
            // tracing every line.
            val grown = RegionFill.expand(mask, document.width, document.height, 1)
            // Confined the same way a stroke is: a fill that escapes the
            // selection is the one that loses somebody an afternoon.
            selection?.let { region ->
                for (i in grown.indices) {
                    grown[i] = Pixels.mul(grown[i].toInt() and 0xFF, region.at(i)).toByte()
                }
            }
            RegionFill.apply(layer, grown, colour, 1f)
            undo.commit(layer)
        }
        rememberColour()
        refreshInBackground()
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

    private suspend fun magicErase(x: Int, y: Int) {
        val layer = document.active ?: return
        if (layer.locked) return
        heavy("Erasing that colour") {
            undo.begin("Magic erase", layer)
            undo.touchAll(layer)
            MagicEraser.erase(layer, x, y, tolerance, contiguous, featherEdges)
            undo.commit(layer)
        }
        refreshInBackground()
    }

    // -- Big operations ----------------------------------------------------

    /**
     * Runs the auto cutout on the active layer.
     *
     * Seconds of real arithmetic on a large photograph, so it runs off the
     * frame thread behind a message that can actually paint. Input to the
     * canvas is refused while [busy] is set, which is what keeps a stroke from
     * running on a layer that is being rewritten underneath it.
     */
    suspend fun autoCutout(invert: Boolean = false) {
        val layer = document.active ?: return
        if (layer.locked) return
        val options = AutoCutout.Options(keep = cutoutKeep, edgeSoftness = featherEdges + 1)
        val confidence = heavy("Working out the background") {
            val mask = AutoCutout.mask(layer.pixels, document.width, document.height, options)
            // With a selection up, the cutout only removes inside it - which is
            // how you take the background from behind one object in a busy
            // picture instead of from the whole thing.
            selection?.let { region ->
                for (i in mask.indices) {
                    val keep = mask[i].toInt() and 0xFF
                    val outside = 255 - region.at(i)
                    mask[i] = maxOf(keep, outside).toByte()
                }
            }
            undo.begin("Auto cutout", layer)
            undo.touchAll(layer)
            AutoCutout.apply(layer, mask, options, invert)
            undo.commit(layer)
            AutoCutout.confidence(mask, document.width, document.height)
        }
        cutoutConfidence = confidence
        refreshInBackground()
    }

    /** Erases the paper colour from a scan, keeping the ink's soft edges. */
    suspend fun liftLineArt() {
        val layer = document.active ?: return
        if (layer.locked) return
        heavy("Lifting the line art") {
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
        }
        refreshInBackground()
    }

    // -- Layers ------------------------------------------------------------

    fun addLayer() = structural("Add layer") { document.addLayer() }
    fun duplicateLayer() = structural("Duplicate layer") { document.duplicateActive() }
    fun deleteLayer() = structural("Delete layer") { document.removeActive() }
    fun mergeDown() = structural("Merge down") { document.mergeDown() }
    fun moveLayer(from: Int, to: Int) = structural("Reorder layers") { document.move(from, to) }

    /** Empties the selection, or the whole layer when there is not one. */
    fun clearLayer() {
        val layer = document.active ?: return
        if (layer.locked) return
        val region = selection
        undo.begin(if (region == null) "Clear layer" else "Clear selection", layer)
        undo.touchAll(layer)
        if (region == null) {
            layer.clear()
        } else {
            for (i in layer.pixels.indices) {
                val keep = 255 - region.at(i)
                layer.pixels[i] = if (keep == 0) 0 else Pixels.withAlpha(
                    layer.pixels[i],
                    Pixels.mul(Pixels.alpha(layer.pixels[i]), keep),
                )
            }
        }
        undo.commit(layer)
        bumpAll()
    }

    /** Fills the selection, or the whole layer when there is not one. */
    fun fillLayer() {
        val layer = document.active ?: return
        if (layer.locked) return
        val region = selection
        undo.begin(if (region == null) "Fill layer" else "Fill selection", layer)
        undo.touchAll(layer)
        if (region == null) {
            layer.fill(colour)
        } else {
            RegionFill.apply(layer, region.coverage, colour, 1f)
        }
        undo.commit(layer)
        rememberColour()
        bumpAll()
    }

    // -- Selection operations ----------------------------------------------

    /**
     * What the selection becomes when [next] arrives, and its outline.
     *
     * Both together, because they are both a pass over the whole canvas and
     * doing them in two places means doing that pass twice.
     */
    private fun resolveSelection(next: PaintSelection?): Pair<PaintSelection?, SelectionOutline?> {
        val combined = when {
            next == null -> null
            selection == null || selectMode == SelectMode.REPLACE -> next
            else -> selection!!.combine(next, selectMode)
        }?.takeIf { !it.isEmpty }
        return combined to combined?.outline()
    }

    /**
     * Puts [next] in, according to the current combine mode.
     *
     * Passing null clears the selection, which is not the same as an empty one:
     * "nothing is selected" means every tool acts on the whole layer, and it is
     * how a selection is meant to end.
     *
     * Synchronous, so it blocks the caller for a pass or two over the canvas.
     * Everything the user can press goes through [buildSelection] instead; this
     * is for the tests, and for a caller that already knows the work is small.
     */
    fun applySelection(next: PaintSelection?) {
        val (region, outline) = resolveSelection(next)
        selection = region
        selectionOutline = outline
        revision++
    }

    /**
     * The same, off the frame thread and behind a message.
     *
     * Selections are whole-canvas arithmetic - a flood, a combine, a boundary
     * trace, each a pass over two and a third million pixels on a full-size
     * document - and doing that between two frames is a visible freeze. Same
     * reasoning as [heavy], which this is a specialisation of: the Compose
     * state is only written either side of the `withContext`, never inside it.
     */
    private suspend fun buildSelection(message: String, build: () -> PaintSelection?) {
        busy = message
        val resolved = try {
            withContext(Dispatchers.Default) { resolveSelection(build()) }
        } finally {
            busy = null
        }
        selection = resolved.first
        selectionOutline = resolved.second
        revision++
    }

    suspend fun selectAll() = buildSelection("Selecting everything") {
        PaintSelection.all(document.width, document.height)
    }

    /** Lets the selection go. No work, so no message and no thread hop. */
    fun deselect() {
        if (selection == null) return
        selection = null
        selectionOutline = null
        revision++
    }

    suspend fun invertSelection() {
        val current = selection
        if (current == null) {
            // Inverting nothing is selecting everything, which is what this
            // means when it is pressed with no selection: the fastest route to
            // "all of it", and it costs nothing to allow.
            selectAll()
            return
        }
        val held = selectMode
        selectMode = SelectMode.REPLACE
        buildSelection("Inverting the selection") { current.invert() }
        selectMode = held
    }

    /** Grows the selection by [amount] pixels, or shrinks it if negative. */
    suspend fun growSelection(amount: Int) {
        val current = selection ?: return
        val held = selectMode
        selectMode = SelectMode.REPLACE
        buildSelection(if (amount >= 0) "Expanding the selection" else "Contracting the selection") {
            current.expand(amount)
        }
        selectMode = held
    }

    /** Softens the selection's edge, so what is done inside it fades out. */
    suspend fun featherSelection(radius: Int) {
        val current = selection ?: return
        val held = selectMode
        selectMode = SelectMode.REPLACE
        buildSelection("Softening the selection") { current.feather(radius) }
        selectMode = held
    }

    /** Everything on this layer that is not transparent. */
    suspend fun selectOpaque() {
        val layer = document.active ?: return
        buildSelection("Selecting what is on this layer") {
            val mask = ByteArray(document.width * document.height)
            for (i in mask.indices) mask[i] = Pixels.alpha(layer.pixels[i]).toByte()
            PaintSelection.of(mask, document.width, document.height)
        }
    }

    /** The magic wand: everything near the colour under a point. */
    private suspend fun wandAt(x: Int, y: Int) {
        val layer = document.active ?: return
        buildSelection("Finding that colour") {
            PaintSelection.wand(
                source = layer.pixels,
                width = document.width,
                height = document.height,
                x = x,
                y = y,
                tolerance = tolerance,
                contiguous = contiguous,
                feather = featherEdges,
            )
        }
    }

    /**
     * How much of the pixel at [index] the tools may touch.
     *
     * 255 when there is no selection - the absence of a selection means the
     * whole layer, not none of it - and the selection's own coverage when there
     * is one, which is what makes a feathered selection fade what is drawn into
     * it rather than cutting it off at a hard line.
     */
    private fun selectionAt(index: Int): Int = selection?.at(index) ?: 255

    /**
     * Puts back whatever a tool wrote outside the selection.
     *
     * Most tools multiply their own coverage by the selection's and never write
     * outside it. Smudge cannot: it drags colour along the stroke and is
     * accumulated into the layer as it goes, so it is confined afterwards -
     * mixed back towards the pre-stroke pixels by however little of each pixel
     * is selected. The undo tiles already hold those pixels, so this costs no
     * extra memory.
     */
    private fun confineToSelection(layer: PaintLayer, left: Int, top: Int, right: Int, bottom: Int) {
        val current = selection ?: return
        for (y in top..bottom) {
            val row = y * document.width
            for (x in left..right) {
                val index = row + x
                val keep = current.at(index)
                if (keep == 255) continue
                val original = undo.originalAt(layer, index)
                layer.pixels[index] =
                    if (keep == 0) original else PaintOps.mixPixels(original, layer.pixels[index], keep)
            }
        }
    }

    fun selectLayer(index: Int) {
        document.activeIndex = index
        layerRevision++
    }

    fun setLayerOpacity(value: Int) {
        document.active?.opacity = value.coerceIn(0, 255)
        // `refresh` rather than `bumpAll`: the list has not changed shape, and
        // rebuilding every row and its thumbnail on each frame of an opacity
        // drag is most of why the panel used to stutter.
        refresh()
    }

    fun setLayerBlend(mode: BlendMode) {
        document.active?.blendMode = mode
        refresh()
        layerRevision++
    }

    fun toggleVisible(index: Int) {
        val layer = document.layers.getOrNull(index) ?: return
        layer.visible = !layer.visible
        refresh()
        layerRevision++
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
