package com.mcguidesigner.core

import com.mcguidesigner.core.paint.AutoCutout
import com.mcguidesigner.core.paint.BlendMode
import com.mcguidesigner.core.paint.Brush
import com.mcguidesigner.core.paint.BrushShape
import com.mcguidesigner.core.paint.BrushStamp
import com.mcguidesigner.core.paint.Compositor
import com.mcguidesigner.core.paint.MagicEraser
import com.mcguidesigner.core.paint.PaintBackground
import com.mcguidesigner.core.paint.PaintDocument
import com.mcguidesigner.core.paint.PaintOps
import com.mcguidesigner.core.paint.Pixels
import com.mcguidesigner.core.paint.RegionFill
import com.mcguidesigner.core.paint.StrokeEngine
import com.mcguidesigner.core.paint.StrokePoint
import com.mcguidesigner.core.paint.UndoStack
import com.mcguidesigner.core.paint.blendPixel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val RED = 0xFFFF0000.toInt()
private const val BLUE = 0xFF0000FF.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()

class PaintEngineTest {

    // -- Compositing -------------------------------------------------------

    @Test
    fun `an opaque pixel over anything is itself`() {
        assertEquals(RED, blendPixel(BLUE, RED, BlendMode.NORMAL, 255))
    }

    @Test
    fun `a transparent pixel changes nothing`() {
        assertEquals(BLUE, blendPixel(BLUE, 0x00FF0000, BlendMode.NORMAL, 255))
    }

    @Test
    fun `half alpha lands halfway`() {
        val result = blendPixel(BLACK, 0x80FFFFFF.toInt(), BlendMode.NORMAL, 255)
        val grey = Pixels.red(result)
        // 128/255 of the way from 0 to 255, give or take rounding.
        assertTrue(abs(grey - 128) <= 2, "expected about 128, got $grey")
    }

    @Test
    fun `multiply over an empty backdrop does not turn everything black`() {
        // The classic bug: with nothing underneath, `cb` reads as zero and
        // multiply annihilates the layer. A blend mode on the bottom layer has
        // to fall back to the source.
        val result = blendPixel(Pixels.TRANSPARENT, RED, BlendMode.MULTIPLY, 255)
        assertEquals(255, Pixels.red(result))
        assertEquals(255, Pixels.alpha(result))
    }

    @Test
    fun `multiply darkens where there is something to darken`() {
        val result = blendPixel(WHITE, 0xFF808080.toInt(), BlendMode.MULTIPLY, 255)
        assertTrue(Pixels.red(result) in 126..130, "got ${Pixels.red(result)}")
    }

    @Test
    fun `screen lightens`() {
        val result = blendPixel(0xFF404040.toInt(), 0xFF404040.toInt(), BlendMode.SCREEN, 255)
        assertTrue(Pixels.red(result) > 0x40, "screen should lighten")
    }

    @Test
    fun `layer opacity scales the whole layer`() {
        val document = PaintDocument.blank(4, 4, PaintBackground.TRANSPARENT)
        document.layers[0].fill(RED)
        document.layers[0].opacity = 128
        val flat = Compositor.flattenTransparent(document)
        assertTrue(abs(Pixels.alpha(flat[0]) - 128) <= 2, "got ${Pixels.alpha(flat[0])}")
    }

    @Test
    fun `a hidden layer contributes nothing`() {
        val document = PaintDocument.blank(4, 4, PaintBackground.TRANSPARENT)
        document.layers[0].fill(RED)
        document.layers[0].visible = false
        val flat = Compositor.flattenTransparent(document)
        assertTrue(flat.all { Pixels.alpha(it) == 0 })
    }

    @Test
    fun `a white background is white where nothing is painted`() {
        val document = PaintDocument.blank(4, 4, PaintBackground.WHITE)
        val flat = Compositor.flatten(document)
        assertTrue(flat.all { it == WHITE }, "a new canvas is a white sheet")
    }

    @Test
    fun `a clipped layer only shows where the one below it has pixels`() {
        val document = PaintDocument.blank(4, 4, PaintBackground.TRANSPARENT)
        val base = document.layers[0]
        base[0, 0] = RED
        val clipped = document.addLayer()
        clipped.fill(BLUE)
        clipped.clippedToBelow = true

        val flat = Compositor.flattenTransparent(document)
        assertEquals(BLUE, flat[0], "the clipped layer shows over the base pixel")
        assertEquals(0, Pixels.alpha(flat[1]), "and nowhere else")
    }

    // -- Brush stamps ------------------------------------------------------

    @Test
    fun `a stamp is solid in the middle and empty outside`() {
        val stamp = BrushStamp.of(10f, BrushShape.DIGITAL_PEN.hardness)
        val centre = stamp.diameter / 2
        assertEquals(255, stamp.at(centre, centre))
        assertEquals(0, stamp.at(0, 0), "the corner of the square is outside the circle")
    }

    @Test
    fun `a soft stamp fades rather than stepping`() {
        val stamp = BrushStamp.of(16f, BrushShape.AIRBRUSH.hardness)
        val centre = stamp.diameter / 2
        val values = (0 until centre).map { stamp.at(centre - it, centre) }
        // Monotonically non-increasing from the centre outwards.
        values.zipWithNext().forEach { (inner, outer) ->
            assertTrue(inner >= outer, "coverage should not rise going outward: $values")
        }
        assertTrue(values.count { it in 1..254 } >= 4, "a soft brush needs a real gradient: $values")
    }

    @Test
    fun `a hard stamp still has an anti-aliased rim`() {
        // Fully hard edges alias badly, and nobody actually wants a jagged
        // circle - even the "hard" nib gets a partial pixel at the boundary.
        val stamp = BrushStamp.of(12f, BrushShape.DIP_PEN_HARD.hardness)
        var partial = 0
        for (y in 0 until stamp.diameter) {
            for (x in 0 until stamp.diameter) {
                if (stamp.at(x, y) in 1..254) partial++
            }
        }
        assertTrue(partial > 8, "expected an anti-aliased rim, found $partial partial pixels")
    }

    // -- Strokes -----------------------------------------------------------

    @Test
    fun `a translucent stroke stays translucent where it overlaps itself`() {
        // The one that matters. Dabs overlap by design; if they composite
        // individually, a 40% stroke goes opaque along its length and is only
        // translucent at the two ends.
        val document = PaintDocument.blank(64, 16, PaintBackground.TRANSPARENT)
        val layer = document.layers[0]
        val engine = StrokeEngine(64, 16)
        val brush = Brush(shape = BrushShape.DIGITAL_PEN, size = 8f, opacity = 0.4f)

        engine.begin(StrokePoint(4f, 8f), brush)
        var x = 4f
        while (x <= 60f) {
            engine.extendTo(StrokePoint(x, 8f), brush, 56f)
            x += 1f
        }
        PaintOps.paint(layer, engine, RED, brush.opacity)

        val middle = Pixels.alpha(layer[32, 8])
        assertTrue(
            abs(middle - 102) <= 6,
            "a 40% stroke should be about 102 alpha along its length, was $middle",
        )
    }

    @Test
    fun `erasing lowers alpha without touching colour`() {
        // So that a stroke erased to a whisper and painted back over is still
        // its own colour rather than a grey ghost.
        val document = PaintDocument.blank(32, 32, PaintBackground.TRANSPARENT)
        val layer = document.layers[0]
        layer.fill(RED)

        val engine = StrokeEngine(32, 32)
        val brush = Brush(shape = BrushShape.DIGITAL_PEN, size = 10f, opacity = 0.5f)
        engine.begin(StrokePoint(16f, 16f), brush)
        engine.extendTo(StrokePoint(16f, 16f), brush, 1f)
        PaintOps.erase(layer, engine, brush.opacity)

        val pixel = layer[16, 16]
        assertTrue(Pixels.alpha(pixel) < 200, "the eraser should have taken a bite")
        assertEquals(255, Pixels.red(pixel), "red must survive erasing")
        assertEquals(0, Pixels.green(pixel))
    }

    @Test
    fun `a locked layer refuses both painting and erasing`() {
        val document = PaintDocument.blank(16, 16, PaintBackground.TRANSPARENT)
        val layer = document.layers[0]
        layer.fill(RED)
        layer.locked = true

        val engine = StrokeEngine(16, 16)
        val brush = Brush(size = 8f)
        engine.begin(StrokePoint(8f, 8f), brush)
        engine.extendTo(StrokePoint(8f, 8f), brush, 1f)
        PaintOps.erase(layer, engine, 1f)
        assertEquals(RED, layer[8, 8])
    }

    @Test
    fun `alpha lock lets colour change but not coverage`() {
        val document = PaintDocument.blank(16, 16, PaintBackground.TRANSPARENT)
        val layer = document.layers[0]
        layer[8, 8] = RED
        layer.alphaLocked = true

        val engine = StrokeEngine(16, 16)
        val brush = Brush(shape = BrushShape.DIGITAL_PEN, size = 6f)
        engine.begin(StrokePoint(8f, 8f), brush)
        engine.extendTo(StrokePoint(8f, 8f), brush, 1f)
        PaintOps.paint(layer, engine, BLUE, 1f)

        assertEquals(255, Pixels.alpha(layer[8, 8]), "alpha must not change")
        assertEquals(255, Pixels.blue(layer[8, 8]), "colour must")
        assertEquals(0, Pixels.alpha(layer[0, 0]), "and empty pixels stay empty")
    }

    // -- Flood fill --------------------------------------------------------

    @Test
    fun `a flood fill stops at a hard edge`() {
        val width = 16
        val height = 8
        val source = IntArray(width * height) { WHITE }
        for (y in 0 until height) source[y * width + 8] = BLACK

        val mask = RegionFill.flood(source, width, height, 2, 4, tolerance = 8)
        assertEquals(255, mask[4 * width + 2].toInt() and 0xFF, "the seeded side is filled")
        assertEquals(0, mask[4 * width + 12].toInt() and 0xFF, "the far side is not")
    }

    @Test
    fun `a non-contiguous fill takes every matching pixel`() {
        val width = 16
        val height = 8
        val source = IntArray(width * height) { WHITE }
        for (y in 0 until height) source[y * width + 8] = BLACK

        val mask = RegionFill.flood(source, width, height, 2, 4, tolerance = 8, contiguous = false)
        assertEquals(255, mask[4 * width + 12].toInt() and 0xFF, "the far side matches by colour")
    }

    @Test
    fun `a fill does not leak through an erased hole`() {
        // Alpha has to count towards the distance, or a fill escapes through
        // every transparent pixel on the layer.
        val width = 16
        val height = 8
        val source = IntArray(width * height) { WHITE }
        for (y in 0 until height) source[y * width + 8] = Pixels.TRANSPARENT

        val mask = RegionFill.flood(source, width, height, 2, 4, tolerance = 32)
        assertEquals(0, mask[4 * width + 12].toInt() and 0xFF, "a hole is a wall")
    }

    @Test
    fun `a huge flat region does not overflow the stack`() {
        // The scanline flood exists for this. A four-neighbour recursive fill
        // dies somewhere around a megapixel.
        val width = 1200
        val height = 900
        val source = IntArray(width * height) { WHITE }
        val mask = RegionFill.flood(source, width, height, 0, 0, tolerance = 4)
        assertEquals(255, mask[mask.size - 1].toInt() and 0xFF, "the whole canvas should fill")
    }

    // -- Erasers -----------------------------------------------------------

    @Test
    fun `the magic eraser removes a matching region and leaves the rest`() {
        val document = PaintDocument.blank(16, 16, PaintBackground.TRANSPARENT)
        val layer = document.layers[0]
        layer.fill(WHITE)
        for (y in 0 until 16) for (x in 8 until 16) layer[x, y] = RED

        assertTrue(MagicEraser.erase(layer, 2, 2, tolerance = 16, feather = 0))
        assertEquals(0, Pixels.alpha(layer[2, 2]), "the white side is gone")
        assertEquals(255, Pixels.alpha(layer[12, 2]), "the red side is not")
    }

    @Test
    fun `keepOnly is the inverse and keeps the seeded colour`() {
        val document = PaintDocument.blank(16, 16, PaintBackground.TRANSPARENT)
        val layer = document.layers[0]
        layer.fill(WHITE)
        for (y in 0 until 16) for (x in 8 until 16) layer[x, y] = RED

        assertTrue(MagicEraser.keepOnly(layer, 12, 2, tolerance = 16, feather = 0))
        assertEquals(255, Pixels.alpha(layer[12, 2]), "the seeded colour stays")
        assertEquals(0, Pixels.alpha(layer[2, 2]), "everything else goes")
    }

    @Test
    fun `lifting line art turns paper into transparency and keeps the ink`() {
        val document = PaintDocument.blank(8, 8, PaintBackground.TRANSPARENT)
        val layer = document.layers[0]
        layer.fill(WHITE)
        layer[4, 4] = BLACK
        // A half-tone pixel, the anti-aliasing on the edge of a pencil line.
        layer[5, 4] = 0xFF808080.toInt()

        assertTrue(MagicEraser.liftLineArt(layer, WHITE))
        assertEquals(0, Pixels.alpha(layer[0, 0]), "paper becomes transparent")
        assertEquals(255, Pixels.alpha(layer[4, 4]), "ink stays solid")
        val edge = Pixels.alpha(layer[5, 4])
        assertTrue(edge in 100..160, "the soft edge should stay soft, was $edge")
    }

    @Test
    fun `the auto cutout separates a subject from a plain background`() {
        // A red square on a blue field: the easiest possible case, and one that
        // has to work perfectly or nothing harder stands a chance.
        val w = 96
        val h = 96
        val source = IntArray(w * h) { BLUE }
        for (y in 28 until 68) for (x in 28 until 68) source[y * w + x] = RED

        val mask = AutoCutout.mask(source, w, h)
        assertEquals(255, mask[48 * w + 48].toInt() and 0xFF, "the middle of the subject is kept")
        assertEquals(0, mask[2 * w + 2].toInt() and 0xFF, "the corner is removed")
        assertTrue(AutoCutout.confidence(mask, w, h) > 30, "a clean case should read as confident")
    }

    @Test
    fun `the auto cutout gives soft edges rather than a stencil`() {
        // A subject with a blurred boundary should come out with partial alpha
        // across it. Without matting the boundary is a cliff and the cutout has
        // the tell-tale stamped-out look.
        val w = 96
        val h = 96
        val source = IntArray(w * h) { BLUE }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val dx = x - 48
                val dy = y - 48
                val d = kotlin.math.sqrt((dx * dx + dy * dy).toFloat())
                val t = ((26f - d) / 6f).coerceIn(0f, 1f)
                val r = (t * 255).toInt()
                val b = ((1 - t) * 255).toInt()
                source[y * w + x] = Pixels.argb(255, r, 0, b)
            }
        }

        val mask = AutoCutout.mask(source, w, h)
        val partial = mask.count { (it.toInt() and 0xFF) in 30..225 }
        assertTrue(partial > 20, "expected a matted band, found $partial partial pixels")
    }

    @Test
    fun `applying a cutout lowers alpha and leaves the subject alone`() {
        val w = 96
        val h = 96
        val document = PaintDocument.blank(w, h, PaintBackground.TRANSPARENT)
        val layer = document.layers[0]
        for (i in layer.pixels.indices) layer.pixels[i] = BLUE
        for (y in 28 until 68) for (x in 28 until 68) layer[x, y] = RED

        val mask = AutoCutout.mask(layer.pixels, w, h)
        AutoCutout.apply(layer, mask)

        assertEquals(255, Pixels.alpha(layer[48, 48]), "the subject survives")
        assertEquals(0, Pixels.alpha(layer[2, 2]), "the background does not")
    }

    // -- Undo --------------------------------------------------------------

    @Test
    fun `undo restores exactly what a stroke changed`() {
        val document = PaintDocument.blank(200, 200, PaintBackground.TRANSPARENT)
        val layer = document.layers[0]
        layer.fill(WHITE)
        val before = layer.pixels.copyOf()

        val undo = UndoStack()
        undo.begin("Brush", layer)
        undo.touch(layer, 90, 90, 110, 110)
        for (y in 95..105) for (x in 95..105) layer[x, y] = RED
        undo.commit(layer)

        assertTrue(undo.canUndo)
        assertTrue(undo.undo(document))
        assertTrue(layer.pixels.contentEquals(before), "undo must restore the layer exactly")
    }

    @Test
    fun `redo puts it back`() {
        val document = PaintDocument.blank(200, 200, PaintBackground.TRANSPARENT)
        val layer = document.layers[0]
        val undo = UndoStack()
        undo.begin("Brush", layer)
        undo.touch(layer, 0, 0, 20, 20)
        layer[10, 10] = RED
        undo.commit(layer)

        undo.undo(document)
        assertEquals(0, layer[10, 10])
        assertTrue(undo.redo(document))
        assertEquals(RED, layer[10, 10])
    }

    @Test
    fun `a stroke that changed nothing does not fill the history`() {
        val document = PaintDocument.blank(64, 64, PaintBackground.TRANSPARENT)
        val layer = document.layers[0]
        val undo = UndoStack()
        undo.begin("Brush", layer)
        undo.touch(layer, 0, 0, 32, 32)
        undo.commit(layer)
        assertFalse(undo.canUndo, "a no-op should not be undoable")
    }

    @Test
    fun `undo only pays for the tiles a stroke touched`() {
        // The whole reason this exists. A dab on a large canvas must not cost
        // what a full-layer snapshot would.
        val document = PaintDocument.blank(2048, 2048, PaintBackground.TRANSPARENT)
        val layer = document.layers[0]
        val undo = UndoStack()
        undo.begin("Brush", layer)
        undo.touch(layer, 1000, 1000, 1010, 1010)
        layer[1005, 1005] = RED
        undo.commit(layer)

        val wholeLayer = 2048L * 2048L * 4L
        assertTrue(
            undo.approximateBytes < wholeLayer / 100,
            "a small dab held ${undo.approximateBytes} bytes against a ${wholeLayer}-byte layer",
        )
    }

    @Test
    fun `deleting a layer can be undone with its pixels intact`() {
        val document = PaintDocument.blank(32, 32, PaintBackground.TRANSPARENT)
        document.addLayer()
        document.layers[1].fill(RED)
        assertEquals(2, document.layers.size)

        val undo = UndoStack()
        undo.structural("Delete layer", document) { document.removeActive() }
        assertEquals(1, document.layers.size)

        assertTrue(undo.undo(document))
        assertEquals(2, document.layers.size)
        assertEquals(RED, document.layers[1][0, 0], "the pixels come back too")
    }

    @Test
    fun `the history is bounded`() {
        val document = PaintDocument.blank(64, 64, PaintBackground.TRANSPARENT)
        val layer = document.layers[0]
        val undo = UndoStack(limit = 5)
        repeat(20) { step ->
            undo.begin("Brush $step", layer)
            undo.touch(layer, 0, 0, 8, 8)
            layer[step % 8, 0] = RED or step
            undo.commit(layer)
        }
        var undone = 0
        while (undo.undo(document)) undone++
        assertEquals(5, undone, "the stack must not grow without limit")
    }

    // -- Documents ---------------------------------------------------------

    @Test
    fun `a new canvas has one layer and a white sheet`() {
        val document = PaintDocument.blank(100, 100)
        assertEquals(1, document.layers.size)
        assertEquals(PaintBackground.WHITE, document.background)
        assertTrue(document.layers[0].isEmpty())
    }

    @Test
    fun `deleting the only layer clears it rather than leaving none`() {
        val document = PaintDocument.blank(16, 16)
        document.layers[0].fill(RED)
        document.removeActive()
        assertEquals(1, document.layers.size, "there must always be somewhere to draw")
        assertTrue(document.layers[0].isEmpty())
    }

    @Test
    fun `merging down keeps the lower layer's own settings`() {
        val document = PaintDocument.blank(8, 8, PaintBackground.TRANSPARENT)
        val lower = document.layers[0]
        lower.fill(BLUE)
        lower.opacity = 128
        lower.blendMode = BlendMode.MULTIPLY

        val upper = document.addLayer()
        upper.fill(RED)

        assertTrue(document.mergeDown())
        assertEquals(1, document.layers.size)
        assertEquals(128, document.layers[0].opacity, "the surviving layer keeps its opacity")
        assertEquals(BlendMode.MULTIPLY, document.layers[0].blendMode)
        assertEquals(RED, document.layers[0][0, 0], "and takes the merged pixels")
    }

    @Test
    fun `moving a layer keeps it selected`() {
        val document = PaintDocument.blank(8, 8)
        document.addLayer()
        document.addLayer()
        val moved = document.layers[2]
        document.move(2, 0)
        assertEquals(moved, document.active, "the layer you moved is the one still selected")
    }
}
