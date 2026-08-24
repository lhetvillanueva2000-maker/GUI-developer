package com.mcguidesigner.core

import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.PointF
import com.mcguidesigner.core.model.ResizeHandle
import com.mcguidesigner.core.model.Rotation
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RotationTest {

    /** A 100x60 box at the origin, so every expected number is easy to check. */
    private val box = IntRect(0, 0, 100, 60)

    private fun assertClose(expected: PointF, actual: PointF, message: String = "") {
        assertTrue(
            abs(expected.x - actual.x) < 0.01f && abs(expected.y - actual.y) < 0.01f,
            "$message expected ($${expected.x}, ${expected.y}) but was (${actual.x}, ${actual.y})",
        )
    }

    // -- Direction ---------------------------------------------------------

    @Test
    fun `positive degrees turn clockwise on screen`() {
        // Screen y grows downwards, so the same matrix that is anticlockwise in
        // maths is clockwise here. This is invisible at 180 degrees and obvious
        // at 90, which is a nasty way to discover it is backwards.
        val turned = Rotation.rotate(PointF(1f, 0f), PointF(0f, 0f), 90f)
        assertClose(PointF(0f, 1f), turned, "a point to the right should end up below.")
    }

    @Test
    fun `a full turn is the identity`() {
        val point = PointF(37f, -12f)
        assertClose(point, Rotation.rotate(point, PointF(5f, 5f), 360f))
    }

    @Test
    fun `the pivot never moves`() {
        val pivot = PointF(50f, 30f)
        listOf(0f, 17f, 90f, 180f, 271f).forEach { angle ->
            assertClose(pivot, Rotation.rotate(pivot, pivot, angle), "at $angle:")
        }
    }

    // -- Handles -----------------------------------------------------------

    @Test
    fun `an unturned element keeps its handles exactly where they were`() {
        assertEquals(Rotation.unrotatedHandleCentres(box), Rotation.handleCentres(box, 0))
    }

    @Test
    fun `turning ninety degrees puts the right handle below the centre`() {
        // The bug this file exists to fix: the handle marked RIGHT belongs to
        // the element's right edge, and on an element stood on its side that
        // edge is at the bottom of the screen.
        val handles = Rotation.handleCentres(box, 90)
        val centre = Rotation.centreOf(box)
        val right = handles.getValue(ResizeHandle.RIGHT)

        assertTrue(abs(right.x - centre.x) < 0.01f, "it should be directly below the centre")
        assertTrue(right.y > centre.y, "it should be below the centre, was ${right.y}")
    }

    @Test
    fun `every handle stays the same distance from the centre however far it turns`() {
        // A handle that drifts towards or away from the centre as the element
        // turns has come off the corner it belongs to.
        val centre = Rotation.centreOf(box)
        val reference = Rotation.unrotatedHandleCentres(box).mapValues { (_, p) ->
            val dx = p.x - centre.x
            val dy = p.y - centre.y
            dx * dx + dy * dy
        }

        listOf(7, 45, 90, 137, 180, 264, 359).forEach { angle ->
            Rotation.handleCentres(box, angle).forEach { (handle, point) ->
                val dx = point.x - centre.x
                val dy = point.y - centre.y
                assertTrue(
                    abs(reference.getValue(handle) - (dx * dx + dy * dy)) < 0.1f,
                    "$handle drifted at $angle degrees",
                )
            }
        }
    }

    @Test
    fun `opposite handles stay opposite`() {
        listOf(0, 23, 90, 180, 300).forEach { angle ->
            val handles = Rotation.handleCentres(box, angle)
            val centre = Rotation.centreOf(box)
            val topLeft = handles.getValue(ResizeHandle.TOP_LEFT)
            val bottomRight = handles.getValue(ResizeHandle.BOTTOM_RIGHT)
            assertClose(
                centre,
                PointF((topLeft.x + bottomRight.x) / 2f, (topLeft.y + bottomRight.y) / 2f),
                "at $angle the two corners should still straddle the centre:",
            )
        }
    }

    // -- Dragging ----------------------------------------------------------

    @Test
    fun `a drag is read in the element's own frame`() {
        // On an element turned ninety degrees, the RIGHT handle points down the
        // screen - so dragging downwards has to mean "wider", not "taller".
        val local = Rotation.toLocalDelta(dx = 0f, dy = 10f, degrees = 90)
        assertClose(PointF(10f, 0f), local, "a downward drag on a quarter turn is a widening.")
    }

    @Test
    fun `an unturned drag passes straight through`() {
        assertClose(PointF(3f, -7f), Rotation.toLocalDelta(3f, -7f, 0))
    }

    @Test
    fun `undoing the rotation of a delta gives it back`() {
        listOf(0, 30, 90, 200, 359).forEach { angle ->
            val local = Rotation.toLocalDelta(12f, -5f, angle)
            val world = Rotation.rotate(PointF(local.x, local.y), PointF(0f, 0f), angle.toFloat())
            assertClose(PointF(12f, -5f), world, "at $angle:")
        }
    }

    // -- Keeping the far corner still --------------------------------------

    @Test
    fun `an unturned resize is left completely alone`() {
        val resized = IntRect(0, 0, 140, 60)
        assertEquals(resized, Rotation.anchorAfterResize(box, resized, ResizeHandle.BOTTOM_RIGHT, 0))
    }

    @Test
    fun `the corner opposite the handle does not appear to move`() {
        // The whole reason this exists: the resize maths pins the opposite
        // corner in the element's own frame, which is not where that corner is
        // on screen once the element has been turned. Without the correction
        // the element slides sideways as you drag it.
        val angle = 40
        val resized = IntRect(0, 0, 160, 60)
        val moved = Rotation.anchorAfterResize(box, resized, ResizeHandle.BOTTOM_RIGHT, angle)

        val before = Rotation.corners(box, angle)[0]
        val after = Rotation.corners(moved, angle)[0]
        assertTrue(
            abs(before.x - after.x) <= 1f && abs(before.y - after.y) <= 1f,
            "the pinned corner moved from (${before.x}, ${before.y}) to (${after.x}, ${after.y})",
        )
    }

    @Test
    fun `dragging the top left pins the bottom right instead`() {
        val angle = 25
        val resized = IntRect(-40, -20, 140, 80)
        val moved = Rotation.anchorAfterResize(box, resized, ResizeHandle.TOP_LEFT, angle)

        val before = Rotation.corners(box, angle)[2]
        val after = Rotation.corners(moved, angle)[2]
        assertTrue(
            abs(before.x - after.x) <= 1f && abs(before.y - after.y) <= 1f,
            "the pinned corner moved from (${before.x}, ${before.y}) to (${after.x}, ${after.y})",
        )
    }

    @Test
    fun `a resize never changes the size it was given`() {
        // This function is allowed to move an element and nothing else.
        val resized = IntRect(0, 0, 137, 41)
        ResizeHandle.entries.forEach { handle ->
            listOf(0, 45, 90, 210).forEach { angle ->
                val moved = Rotation.anchorAfterResize(box, resized, handle, angle)
                assertEquals(resized.width, moved.width, "$handle at $angle")
                assertEquals(resized.height, moved.height, "$handle at $angle")
            }
        }
    }

    // -- The rotation knob -------------------------------------------------

    @Test
    fun `straight up is zero`() {
        // The knob sits above the element, so the angle has to be measured from
        // there - otherwise grabbing it snaps the element a quarter turn.
        assertEquals(0, Rotation.angleTo(PointF(0f, 0f), PointF(0f, -10f)))
    }

    @Test
    fun `the knob reads clockwise`() {
        assertEquals(90, Rotation.angleTo(PointF(0f, 0f), PointF(10f, 0f)), "to the right is a quarter turn")
        assertEquals(180, Rotation.angleTo(PointF(0f, 0f), PointF(0f, 10f)), "below is a half turn")
        assertEquals(270, Rotation.angleTo(PointF(0f, 0f), PointF(-10f, 0f)))
    }

    @Test
    fun `a knob dropped on the centre does not divide by zero`() {
        assertEquals(0, Rotation.angleTo(PointF(5f, 5f), PointF(5f, 5f)))
    }

    // -- Snapping ----------------------------------------------------------

    @Test
    fun `an angle near a landmark is pulled onto it`() {
        assertEquals(90, Rotation.snap(92))
        assertEquals(45, Rotation.snap(43))
        assertEquals(0, Rotation.snap(358), "just short of a full turn is square, not 358")
    }

    @Test
    fun `an angle between landmarks is left alone`() {
        // Otherwise "custom angles" would not be custom at all.
        assertEquals(37, Rotation.snap(37))
        assertEquals(52, Rotation.snap(52))
    }

    @Test
    fun `normalising handles negatives and overshoot`() {
        assertEquals(350, Rotation.normalise(-10))
        assertEquals(0, Rotation.normalise(360))
        assertEquals(90, Rotation.normalise(450))
        assertEquals(270, Rotation.normalise(-90))
    }
}
