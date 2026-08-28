package com.mcguidesigner.styles.paint

import com.mcguidesigner.core.paint.PaintBackground
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * What one frame of a stroke is allowed to cost.
 *
 * The bug this exists to prevent: every tool recomputes its pixels from the
 * pre-stroke original, and it used to do that over the *whole* accumulated
 * rectangle of the stroke so far. A stroke drawn corner to corner therefore
 * re-blended an area that grew with every event - a quarter of a million
 * pixels by the middle of a diagonal swipe, a million by the end - sixty times
 * a second. The cost of drawing a line was quadratic in its length, which is
 * exactly what "it gets laggier the longer I draw" feels like.
 *
 * The rule is now: one event costs what that event actually changed. These
 * tests measure that in pixels rather than in milliseconds, because a
 * millisecond budget on a shared CI runner is a coin toss and a pixel count is
 * the same number on every machine.
 */
class StrokeCostTest {

    private fun state() = PaintState(512, 512, PaintBackground.WHITE)

    /**
     * A long diagonal stroke, one event at a time.
     *
     * The last event of a 400-pixel diagonal must not cost more than a few
     * brush stamps. Before the fix it cost the bounding box of the whole
     * stroke, so this number was six figures.
     */
    @Test
    fun lateEventsCostNoMoreThanEarlyOnes() {
        val state = state()
        state.brushSize = 12f
        state.eraserSize = 12f
        state.strokeStart(20f, 20f)

        var firstCost = 0L
        var lastCost = 0L
        for (i in 1..400) {
            val t = i.toFloat()
            state.resetBlendCounter()
            state.strokeMove(20f + t, 20f + t)
            val cost = state.blendedPixels
            if (i == 1) firstCost = cost
            if (i == 400) lastCost = cost
        }
        state.strokeEnd(420f, 420f)

        assertTrue(firstCost > 0, "the first event should blend something")
        // Generous: the point is the shape of the curve, not a tight bound. A
        // quadratic path puts the last event three orders of magnitude above
        // the first.
        assertTrue(
            lastCost <= firstCost * 4,
            "a late event cost $lastCost against $firstCost for an early one - " +
                "the per-event work is growing with the stroke",
        )
    }

    /** The same rule for the eraser, which takes a different branch. */
    @Test
    fun erasingIsBoundedToo() {
        val state = state()
        state.brushSize = 12f
        state.eraserSize = 12f
        state.tool = PaintTool.ERASER
        state.strokeStart(20f, 20f)

        var firstCost = 0L
        var lastCost = 0L
        for (i in 1..300) {
            val t = i.toFloat()
            state.resetBlendCounter()
            state.strokeMove(20f + t, 20f + t * 0.6f)
            val cost = state.blendedPixels
            if (i == 1) firstCost = cost
            if (i == 300) lastCost = cost
        }
        state.strokeEnd(320f, 200f)

        assertTrue(firstCost > 0)
        assertTrue(lastCost <= firstCost * 4, "eraser cost $lastCost against $firstCost")
    }

    /**
     * The whole stroke, end to end, must stay linear in its length.
     *
     * Doubling the number of events should roughly double the total work. Under
     * the old cumulative-rectangle rule it quadrupled it.
     */
    @Test
    fun totalWorkIsLinearInStrokeLength() {
        fun costOf(events: Int): Long {
            val state = state()
            state.brushSize = 10f
            state.resetBlendCounter()
            state.strokeStart(10f, 10f)
            for (i in 1..events) {
                state.strokeMove(10f + i * 1.2f, 10f + i * 1.2f)
            }
            state.strokeEnd(10f + events * 1.2f, 10f + events * 1.2f)
            return state.blendedPixels
        }

        val short = costOf(100)
        val long = costOf(200)
        assertTrue(short > 0)
        // Linear would be 2x. Allow 3x for the fixed overhead of the stamps
        // themselves; quadratic would be 4x and rising.
        assertTrue(
            long <= short * 3,
            "200 events cost $long against $short for 100 - that is not linear",
        )
    }
}
