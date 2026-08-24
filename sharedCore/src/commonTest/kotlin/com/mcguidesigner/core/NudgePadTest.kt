package com.mcguidesigner.core

import com.mcguidesigner.core.editor.EditorSettings
import com.mcguidesigner.core.editor.NudgePad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NudgePadTest {

    // -- The step bar ------------------------------------------------------

    @Test
    fun `the ends of the bar are the ends of the range`() {
        assertEquals(EditorSettings.MIN_STEP, NudgePad.stepAtFraction(0f))
        assertEquals(EditorSettings.MAX_STEP, NudgePad.stepAtFraction(1f))
    }

    @Test
    fun `a finger past either end does not leave the range`() {
        assertEquals(EditorSettings.MIN_STEP, NudgePad.stepAtFraction(-3f))
        assertEquals(EditorSettings.MAX_STEP, NudgePad.stepAtFraction(9f))
    }

    @Test
    fun `every doubling is the same distance along the bar`() {
        // The reason the scale is exponential: on a linear bar, 1 through 8 -
        // the values actually used - would share the first six percent of it.
        val gaps = listOf(1, 2, 4, 8, 16, 32, 64, 128)
            .map { NudgePad.fractionForStep(it) }
            .zipWithNext { a, b -> b - a }

        gaps.forEach { gap ->
            assertEquals(1f / 7f, gap, 0.001f, "each doubling should be a seventh of the bar")
        }
    }

    @Test
    fun `a position and a step name each other`() {
        // Not float equality: a position is continuous and a step is a whole
        // number, so the promise is that going out and back lands on the same
        // step, not on the same float.
        listOf(1, 2, 3, 5, 8, 16, 33, 64, 128).forEach { step ->
            assertEquals(step, NudgePad.stepAtFraction(NudgePad.fractionForStep(step)), "for $step")
        }
    }

    @Test
    fun `the bar rises all the way along`() {
        // A scale that plateaus would have stretches of bar that do nothing,
        // which reads as the control being broken.
        var previous = 0
        var rises = 0
        (0..100).forEach { index ->
            val step = NudgePad.stepAtFraction(index / 100f)
            assertTrue(step >= previous, "the bar went backwards at $index%")
            if (step > previous) rises++
            previous = step
        }
        assertTrue(rises > 30, "expected the value to keep climbing, only changed $rises times")
    }

    @Test
    fun `a corrupt position reads as home rather than as nothing`() {
        assertEquals(NudgePad.HOME_STEP, NudgePad.stepAtFraction(Float.NaN))
    }

    @Test
    fun `home is one`() {
        assertEquals(1, NudgePad.HOME_STEP)
        assertTrue(NudgePad.isLandmark(NudgePad.HOME_STEP))
    }

    @Test
    fun `landmarks are the sizes people are aiming for`() {
        assertTrue(NudgePad.isLandmark(8), "a vanilla Java container's grid")
        assertTrue(NudgePad.isLandmark(16), "one texture tile")
        assertTrue(!NudgePad.isLandmark(7))
        assertEquals(8, NudgePad.nearestLandmark(9))
        assertNull(NudgePad.nearestLandmark(12), "12 is not near anything worth naming")
    }

    // -- Resizing ----------------------------------------------------------

    @Test
    fun `dragging out and back returns to where it started`() {
        // The gesture accumulates from a captured starting scale, so a drag
        // that ends where it began must leave the pad the size it was. Getting
        // this wrong is the classic resize bug: the control creeps a little
        // every time you touch it.
        val start = 1f
        assertEquals(start, NudgePad.scaleAfterDrag(NudgePad.scaleAfterDrag(start, 60f), -60f))
    }

    @Test
    fun `the pad cannot be dragged out of its supported range`() {
        assertEquals(EditorSettings.MAX_PAD_SCALE, NudgePad.scaleAfterDrag(1f, 10_000f))
        assertEquals(EditorSettings.MIN_PAD_SCALE, NudgePad.scaleAfterDrag(1f, -10_000f))
    }

    @Test
    fun `a stored scale from a future build is brought back into range`() {
        assertEquals(EditorSettings.MAX_PAD_SCALE, EditorSettings(nudgePadScale = 9f).sanitised().nudgePadScale)
        assertEquals(EditorSettings.MIN_PAD_SCALE, EditorSettings(nudgePadScale = 0f).sanitised().nudgePadScale)
    }

    @Test
    fun `a corrupt scale does not shrink the pad to nothing`() {
        // NaN compares false against everything, so coerceIn passes it straight
        // through - and a NaN size means a pad that is on screen but cannot be
        // touched. Both the settings type and the pad's own maths reject it.
        assertEquals(1f, EditorSettings(nudgePadScale = Float.NaN).sanitised().nudgePadScale)
        assertEquals(1f, NudgePad.clampScale(Float.NaN))
        assertTrue(NudgePad.keyDp(Float.NaN) > 0f)
    }

    @Test
    fun `keys and gaps scale together`() {
        // The pad has to stay recognisably itself at both ends: keys that grow
        // while the gaps stay put turn a grid into a slab.
        val ratio = NudgePad.BASE_KEY_DP / NudgePad.BASE_GAP_DP
        listOf(0.7f, 1f, 1.5f, 2f).forEach { scale ->
            assertEquals(ratio, NudgePad.keyDp(scale) / NudgePad.gapDp(scale), 0.001f, "at $scale")
        }
    }
}
