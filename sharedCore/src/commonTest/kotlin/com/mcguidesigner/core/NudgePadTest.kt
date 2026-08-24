package com.mcguidesigner.core

import com.mcguidesigner.core.editor.EditorSettings
import com.mcguidesigner.core.editor.NudgePad
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NudgePadTest {

    // -- Resizing ----------------------------------------------------------

    @Test
    fun `dragging out and back returns to where it started`() {
        // The gesture accumulates from a captured starting scale, so a drag
        // that ends where it began must leave the pad the size it was. Getting
        // this wrong is the classic resize bug: the control creeps a little
        // every time you touch it.
        val start = 1f
        assertEquals(start, NudgePad.scaleAfterDrag(NudgePad.scaleAfterDrag(start, 90f), -90f))
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

    // -- The centre step control -------------------------------------------

    @Test
    fun `dragging the centre back to where it started restores the step`() {
        val start = 4
        assertEquals(start, NudgePad.stepAfterDrag(NudgePad.stepAfterDrag(start, 60f), -60f))
    }

    @Test
    fun `the step never leaves its supported range`() {
        assertEquals(EditorSettings.MAX_STEP, NudgePad.stepAfterDrag(1, 100_000f))
        assertEquals(EditorSettings.MIN_STEP, NudgePad.stepAfterDrag(64, -100_000f))
        assertTrue(NudgePad.stepAfterDrag(1, -50f) >= 1, "a step of zero would make the arrows do nothing")
    }

    @Test
    fun `a small drag does not change the step at all`() {
        // Otherwise a tap that wobbles by two pixels changes the number, and
        // tapping is how you reset it.
        assertEquals(5, NudgePad.stepAfterDrag(5, 2f))
        assertEquals(5, NudgePad.stepAfterDrag(5, -2f))
    }

    @Test
    fun `the drag distance for a step is the inverse of the step for a distance`() {
        listOf(1, 3, 8, 16, 64).forEach { target ->
            assertEquals(target, NudgePad.stepAfterDrag(1, NudgePad.dragDpFor(1, target)), "for $target")
        }
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
        assertEquals(null, NudgePad.nearestLandmark(12), "12 is not near anything worth naming")
    }
}
