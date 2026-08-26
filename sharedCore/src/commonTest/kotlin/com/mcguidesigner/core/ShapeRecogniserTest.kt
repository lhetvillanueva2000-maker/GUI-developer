package com.mcguidesigner.core

import com.mcguidesigner.core.paint.RecognisedShape
import com.mcguidesigner.core.paint.ScribbleSelection
import com.mcguidesigner.core.paint.ShapeRecogniser
import com.mcguidesigner.core.paint.PaintBackground
import com.mcguidesigner.core.paint.PaintDocument
import com.mcguidesigner.core.paint.Pixels
import com.mcguidesigner.core.paint.StrokePoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The recogniser has to be right about ordinary hand-drawn shapes and, just as
 * importantly, has to decline when it is not sure. Silently replacing somebody's
 * drawing with a confidently wrong triangle is worse than leaving it alone.
 */
class ShapeRecogniserTest {

    private val wobble = Random(20260826)

    /** Points along a segment, with hand-shake added. */
    private fun run(
        ax: Float, ay: Float, bx: Float, by: Float, jitter: Float, steps: Int = 40,
    ): List<StrokePoint> = (0 until steps).map { i ->
        val t = i / steps.toFloat()
        StrokePoint(
            ax + (bx - ax) * t + (wobble.nextFloat() - 0.5f) * jitter,
            ay + (by - ay) * t + (wobble.nextFloat() - 0.5f) * jitter,
        )
    }

    private fun polygon(corners: List<Pair<Float, Float>>, jitter: Float): List<StrokePoint> {
        val out = ArrayList<StrokePoint>()
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            out += run(a.first, a.second, b.first, b.second, jitter)
        }
        out += StrokePoint(corners[0].first, corners[0].second)
        return out
    }

    @Test
    fun `a rough box becomes a rectangle`() {
        val path = polygon(
            listOf(60f to 40f, 300f to 40f, 300f to 170f, 60f to 170f),
            jitter = 7f,
        )
        val guess = ShapeRecogniser.recognise(path)
        assertEquals(RecognisedShape.RECTANGLE, guess.shape, "confidence ${guess.confidence}")
        assertEquals(4, guess.points.size)
        assertTrue(guess.closed)
    }

    @Test
    fun `a rough box with even sides becomes a square`() {
        val path = polygon(
            listOf(80f to 80f, 280f to 74f, 286f to 278f, 74f to 284f),
            jitter = 7f,
        )
        val guess = ShapeRecogniser.recognise(path)
        assertEquals(RecognisedShape.SQUARE, guess.shape, "confidence ${guess.confidence}")
        // A square's sides really are equal, whatever the drag was.
        val width = guess.points[1].x - guess.points[0].x
        val height = guess.points[2].y - guess.points[1].y
        assertTrue(kotlin.math.abs(width - height) < 1f, "sides $width vs $height")
    }

    @Test
    fun `three corners become a triangle`() {
        val path = polygon(listOf(180f to 40f, 320f to 260f, 40f to 260f), jitter = 7f)
        val guess = ShapeRecogniser.recognise(path)
        assertEquals(RecognisedShape.TRIANGLE, guess.shape, "confidence ${guess.confidence}")
        assertEquals(3, guess.points.size)
    }

    @Test
    fun `a rough loop becomes a circle`() {
        val path = (0..72).map { i ->
            val a = 2f * PI.toFloat() * i / 72f
            StrokePoint(
                200f + cos(a) * 120f + (wobble.nextFloat() - 0.5f) * 8f,
                200f + sin(a) * 120f + (wobble.nextFloat() - 0.5f) * 8f,
            )
        }
        val guess = ShapeRecogniser.recognise(path)
        assertEquals(RecognisedShape.CIRCLE, guess.shape, "confidence ${guess.confidence}")
    }

    @Test
    fun `a squashed loop becomes an ellipse rather than a circle`() {
        val path = (0..72).map { i ->
            val a = 2f * PI.toFloat() * i / 72f
            StrokePoint(
                200f + cos(a) * 180f + (wobble.nextFloat() - 0.5f) * 8f,
                200f + sin(a) * 70f + (wobble.nextFloat() - 0.5f) * 8f,
            )
        }
        val guess = ShapeRecogniser.recognise(path)
        assertEquals(RecognisedShape.ELLIPSE, guess.shape, "confidence ${guess.confidence}")
    }

    @Test
    fun `a rough drag becomes a straight line`() {
        val path = run(40f, 60f, 320f, 130f, jitter = 6f)
        val guess = ShapeRecogniser.recognise(path)
        assertEquals(RecognisedShape.LINE, guess.shape, "confidence ${guess.confidence}")
        assertEquals(2, guess.points.size)
    }

    @Test
    fun `a scribble is left alone`() {
        // The important negative. Nobody drawing this meant a polygon, and
        // replacing it with one would destroy what they drew.
        val path = (0..200).map { i ->
            val t = i / 8f
            StrokePoint(
                160f + cos(t * 2.3f) * 90f + sin(t * 5.1f) * 40f,
                160f + sin(t * 1.7f) * 90f + cos(t * 4.3f) * 40f,
            )
        }
        val guess = ShapeRecogniser.recognise(path)
        assertEquals(RecognisedShape.FREEHAND, guess.shape, "confidence ${guess.confidence}")
    }

    @Test
    fun `a tap is not a shape`() {
        val path = (0..8).map { StrokePoint(100f + wobble.nextFloat(), 100f + wobble.nextFloat()) }
        assertEquals(RecognisedShape.FREEHAND, ShapeRecogniser.recognise(path).shape)
    }

    @Test
    fun `a tilted box keeps its angle rather than being straightened`() {
        // Somebody who drew it at an angle meant it at an angle.
        val path = polygon(
            listOf(120f to 40f, 300f to 120f, 220f to 300f, 40f to 220f),
            jitter = 6f,
        )
        val guess = ShapeRecogniser.recognise(path)
        assertTrue(
            guess.shape == RecognisedShape.SQUARE || guess.shape == RecognisedShape.RECTANGLE,
            "got ${guess.shape}",
        )
        // Not axis-aligned: at least one edge is genuinely diagonal.
        val diagonal = guess.points.indices.any { i ->
            val a = guess.points[i]
            val b = guess.points[(i + 1) % guess.points.size]
            kotlin.math.abs(a.x - b.x) > 12f && kotlin.math.abs(a.y - b.y) > 12f
        }
        assertTrue(diagonal, "a turned box should not be straightened: ${guess.points}")
    }

    @Test
    fun `the outline comes back dense enough to stamp along`() {
        val path = polygon(listOf(60f to 40f, 300f to 40f, 300f to 170f, 60f to 170f), jitter = 5f)
        val guess = ShapeRecogniser.recognise(path)
        val dense = ShapeRecogniser.toStrokePath(guess)
        dense.zipWithNext().forEach { (a, b) ->
            val gap = kotlin.math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
            assertTrue(gap <= 2f, "gap of $gap would leave a hole between dabs")
        }
    }

    // -- Scribble selection ------------------------------------------------

    @Test
    fun `scribbling over an object selects the whole object`() {
        // Two objects on a background. A scribble across one must take all of
        // it - both its colours - and none of the other.
        val w = 120
        val h = 80
        val document = PaintDocument.blank(w, h, PaintBackground.TRANSPARENT)
        val layer = document.layers[0]
        for (i in layer.pixels.indices) layer.pixels[i] = 0xFF202020.toInt()
        // Object A: a two-tone block on the left.
        for (y in 20 until 60) for (x in 10 until 50) {
            layer[x, y] = if (x < 30) 0xFFE04040.toInt() else 0xFFD03838.toInt()
        }
        // Object B: a block on the right, untouched.
        for (y in 20 until 60) for (x in 70 until 110) layer[x, y] = 0xFF40C060.toInt()

        val path = (10..40).map { StrokePoint(it.toFloat(), 40f) }
        val mask = ScribbleSelection.select(
            layer.pixels, w, h, path, radius = 3, tolerance = 40, contiguous = true, feather = 0,
        )
        ScribbleSelection.erase(layer, mask)

        assertEquals(0, Pixels.alpha(layer[15, 30]), "the scribbled half must go")
        assertEquals(0, Pixels.alpha(layer[45, 50]), "and so must the other tone of the same object")
        assertEquals(255, Pixels.alpha(layer[90, 40]), "the other object must survive")
        assertEquals(255, Pixels.alpha(layer[5, 5]), "and so must the background")
    }

    @Test
    fun `the scribble band itself is always taken`() {
        // Whatever the colour rules say, a pixel deliberately dragged across
        // was meant. Here the scribble crosses onto a colour nothing else
        // matches, and it still goes.
        val w = 60
        val h = 40
        val document = PaintDocument.blank(w, h, PaintBackground.TRANSPARENT)
        val layer = document.layers[0]
        for (i in layer.pixels.indices) layer.pixels[i] = 0xFF3060C0.toInt()
        layer[30, 20] = 0xFFFFFF00.toInt()

        val path = listOf(StrokePoint(30f, 20f))
        val mask = ScribbleSelection.select(
            layer.pixels, w, h, path, radius = 1, tolerance = 0, contiguous = true, feather = 0,
        )
        ScribbleSelection.erase(layer, mask)
        assertEquals(0, Pixels.alpha(layer[30, 20]))
    }

    @Test
    fun `a scribble over a large flat area does not take the whole canvas when not contiguous is off`() {
        val w = 100
        val h = 100
        val document = PaintDocument.blank(w, h, PaintBackground.TRANSPARENT)
        val layer = document.layers[0]
        for (i in layer.pixels.indices) layer.pixels[i] = 0xFF204080.toInt()
        // A wall the flood cannot cross.
        for (y in 0 until h) layer[50, y] = 0xFFFFFFFF.toInt()

        val path = (5..40).map { StrokePoint(it.toFloat(), 50f) }
        val mask = ScribbleSelection.select(
            layer.pixels, w, h, path, radius = 2, tolerance = 20, contiguous = true, feather = 0,
        )
        ScribbleSelection.erase(layer, mask)
        assertEquals(0, Pixels.alpha(layer[20, 20]), "the seeded side goes")
        assertEquals(255, Pixels.alpha(layer[80, 20]), "the far side stays")
    }
}
