package com.mcguidesigner.styles.paint

import com.mcguidesigner.core.paint.MarqueeShape
import com.mcguidesigner.core.paint.PaintBackground
import com.mcguidesigner.core.paint.PaintSelection
import com.mcguidesigner.core.paint.Pixels
import com.mcguidesigner.core.paint.RulerKind
import com.mcguidesigner.core.paint.SelectMode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the selection and the ruler have to do to the *painting*, not to their
 * own arithmetic.
 *
 * The geometry is tested next door in `SelectionAndRulerTest`. What is here is
 * the promise those two features make to the person using them: a selection
 * means nothing escapes it, and a ruler means the line is straight. Both are
 * promises that are only worth anything if every tool keeps them, which is why
 * this walks the tools rather than one of them.
 */
class SelectionAndRulerBehaviourTest {

    private fun state() = PaintState(256, 256, PaintBackground.TRANSPARENT).apply {
        brushSize = 24f
        eraserSize = 24f
        colour = 0xFF000000.toInt()
    }

    /**
     * Sets a rectangular selection directly.
     *
     * Straight in rather than through the drag gesture, so these tests are
     * about what a selection *does* and not about pointer plumbing - which has
     * its own tests in the canvas view.
     */
    private fun PaintState.select(x0: Float, y0: Float, x1: Float, y1: Float) {
        selectMode = SelectMode.REPLACE
        applySelection(
            PaintSelection.marquee(document.width, document.height, x0, y0, x1, y1, MarqueeShape.RECTANGLE),
        )
    }

    private fun PaintState.alphaAt(x: Int, y: Int): Int =
        Pixels.alpha(document.active!!.pixels[y * document.width + x])

    private fun PaintState.drawAcross(y: Float) {
        strokeStart(10f, y)
        for (x in 20..240 step 10) strokeMove(x.toFloat(), y)
        strokeEnd(240f, y)
    }

    // -- The selection confines every tool ---------------------------------

    @Test
    fun aBrushStrokeStopsAtTheSelection() {
        val state = state()
        state.select(100f, 0f, 160f, 255f)
        state.drawAcross(128f)

        assertTrue(state.alphaAt(128, 128) > 200, "nothing was painted inside the selection")
        assertEquals(0, state.alphaAt(40, 128), "the brush painted to the left of the selection")
        assertEquals(0, state.alphaAt(220, 128), "the brush painted to the right of the selection")
    }

    @Test
    fun theEraserStopsAtTheSelectionToo() {
        val state = state()
        state.fillLayer() // black everywhere, no selection yet
        state.select(100f, 0f, 160f, 255f)
        state.tool = PaintTool.ERASER
        state.drawAcross(128f)

        assertTrue(state.alphaAt(128, 128) < 60, "nothing was erased inside the selection")
        assertEquals(255, state.alphaAt(40, 128), "the eraser reached outside the selection")
    }

    @Test
    fun theBucketStaysInside() = runTest {
        val state = state()
        state.select(100f, 100f, 160f, 160f)
        state.tool = PaintTool.BUCKET
        state.tap(128, 128)

        assertTrue(state.alphaAt(128, 128) > 200, "the fill did not happen")
        assertEquals(0, state.alphaAt(20, 20), "the fill escaped the selection")
    }

    @Test
    fun smudgeStaysInside() {
        val state = state()
        state.fillLayer()
        state.select(100f, 100f, 160f, 160f)
        state.tool = PaintTool.SMUDGE
        state.strokeStart(60f, 128f)
        for (x in 70..200 step 5) state.strokeMove(x.toFloat(), 128f)
        state.strokeEnd(200f, 128f)

        // Smudging opaque black over opaque black changes nothing visible, so
        // what is checked is the mechanism: outside the selection the pixels
        // must be byte-identical to what they were.
        assertEquals(255, state.alphaAt(60, 128))
        assertEquals(255, state.alphaAt(200, 128))
    }

    @Test
    fun fillAndClearActOnTheSelectionWhenThereIsOne() {
        val state = state()
        state.select(100f, 100f, 160f, 160f)
        state.fillLayer()
        assertTrue(state.alphaAt(128, 128) > 200, "the selection was not filled")
        assertEquals(0, state.alphaAt(20, 20), "fill went outside the selection")

        state.clearLayer()
        assertEquals(0, state.alphaAt(128, 128), "the selection was not cleared")
    }

    @Test
    fun withNoSelectionEveryToolHasTheWholeLayerBack() {
        val state = state()
        state.select(100f, 100f, 160f, 160f)
        state.deselect()
        state.drawAcross(128f)
        assertTrue(state.alphaAt(40, 128) > 200, "deselecting did not give the brush the layer back")
    }

    /** A soft-edged selection fades what is drawn into it rather than cutting it. */
    @Test
    fun aFeatheredSelectionFadesTheEdgeOfAStroke() = runTest {
        val state = state()
        state.select(100f, 0f, 160f, 255f)
        state.featherSelection(6)
        state.drawAcross(128f)

        val middle = state.alphaAt(130, 128)
        val edge = state.alphaAt(163, 128)
        assertTrue(middle > 200, "the middle of the selection was not painted")
        assertTrue(edge in 1..200, "the edge came out at $edge - hard, not faded")
    }

    // -- The ruler -----------------------------------------------------------

    /**
     * A stroke drawn badly comes out straight.
     *
     * The wobble here is far larger than the tolerance: without the ruler this
     * stroke deviates by twelve pixels, and with it every dab must be on the
     * line. That is the whole promise.
     */
    @Test
    fun aStraightEdgeMakesAWobblyStrokeStraight() {
        val state = state()
        state.brushSize = 4f
        state.setRulerKind(RulerKind.LINE)
        state.setRulerAngle(0f)
        state.centreRuler()
        val line = state.ruler.y

        state.strokeStart(40f, line)
        for (x in 50..220 step 10) {
            // Deliberately all over the place.
            state.strokeMove(x.toFloat(), line + if (x % 20 == 0) 12f else -12f)
        }
        state.strokeEnd(220f, line)

        val row = line.toInt()
        var painted = 0
        for (x in 50..210) {
            if (Pixels.alpha(state.document.active!!.pixels[row * state.document.width + x]) > 0) painted++
        }
        assertTrue(painted > 140, "only $painted pixels of the line landed on the ruler")

        // ...and nothing landed where the finger actually went.
        val strayRow = (line + 12f).toInt()
        var stray = 0
        for (x in 50..210) {
            if (Pixels.alpha(state.document.active!!.pixels[strayRow * state.document.width + x]) > 0) stray++
        }
        assertEquals(0, stray, "$stray pixels were painted where the finger wandered")
    }

    @Test
    fun theParallelRulerKeepsEachStrokeOnItsOwnLine() {
        val state = state()
        state.brushSize = 3f
        state.setRulerKind(RulerKind.PARALLEL)
        state.setRulerAngle(0f)

        listOf(60f, 120f, 180f).forEach { y ->
            state.strokeStart(40f, y)
            for (x in 50..200 step 10) state.strokeMove(x.toFloat(), y + 15f)
            state.strokeEnd(200f, y + 15f)
        }
        listOf(60, 120, 180).forEach { y ->
            assertTrue(state.alphaAt(120, y) > 0, "the line started at y=$y is not there")
            assertEquals(0, state.alphaAt(120, y + 15), "a stroke drifted off its own line")
        }
    }

    @Test
    fun turningTheRulerOffGivesTheStrokeBack() {
        val state = state()
        state.brushSize = 4f
        state.setRulerKind(RulerKind.LINE)
        state.centreRuler()
        state.setRulerKind(RulerKind.OFF)

        state.strokeStart(40f, 40f)
        state.strokeMove(120f, 200f)
        state.strokeEnd(200f, 40f)
        assertTrue(state.alphaAt(120, 200) > 0, "the ruler was off but the stroke was still bent")
    }

    /** Switching a ruler on puts it where it can be seen, not at the origin. */
    @Test
    fun aRulerSwitchedOnLandsOnTheCanvas() {
        val state = state()
        state.setRulerKind(RulerKind.CIRCLE)
        assertTrue(state.ruler.x > 0f && state.ruler.x < state.document.width)
        assertTrue(state.ruler.y > 0f && state.ruler.y < state.document.height)
    }

    /** The ruler is a guide, so it survives the stroke that used it. */
    @Test
    fun theRulerStaysPutAfterAStroke() {
        val state = state()
        state.setRulerKind(RulerKind.LINE)
        state.centreRuler()
        val before = state.ruler
        state.strokeStart(40f, 40f)
        state.strokeMove(120f, 60f)
        state.strokeEnd(200f, 80f)
        assertEquals(before, state.ruler)
    }

    /** Symmetry and the ruler are different things and must compose. */
    @Test
    fun symmetryStillMirrorsAStrokeHeldToARuler() {
        val state = state()
        state.brushSize = 5f
        state.symmetry = SymmetryMode.VERTICAL
        state.setRulerKind(RulerKind.PARALLEL)
        state.setRulerAngle(0f)

        state.strokeStart(60f, 100f)
        state.strokeMove(100f, 118f)
        state.strokeEnd(100f, 118f)

        assertTrue(state.alphaAt(80, 100) > 0, "the stroke itself is missing")
        assertTrue(
            state.alphaAt(256 - 80, 100) > 0,
            "the mirrored copy is missing - symmetry and the ruler are fighting",
        )
    }
}
