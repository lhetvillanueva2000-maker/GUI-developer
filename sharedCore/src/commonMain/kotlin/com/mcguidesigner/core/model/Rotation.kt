package com.mcguidesigner.core.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** A point that has not been rounded to a whole GUI pixel yet. */
data class PointF(val x: Float, val y: Float)

/**
 * Turning things, and everything that follows from having turned them.
 *
 * Rotation used to be a number written into a property and read by exactly one
 * renderer. Everything else in the editor - the selection outline, the eight
 * resize handles, the drag that moves them - carried on as though every element
 * were square to the world. Turn a panel forty degrees and its handles stayed
 * resolutely horizontal, which is the bug this file exists to remove.
 *
 * The rules are here, as pure functions on plain numbers, rather than in the
 * canvas: the same maths has to agree in three places that cannot see each
 * other - where a handle is *drawn*, where it is *hit*, and what a drag on it
 * *does* - and three implementations of a rotation matrix is three chances for
 * the handles to sit somewhere the drag does not expect.
 *
 * Everything rotates about the element's own centre, which is where the
 * renderer and every code export already pivot.
 */
object Rotation {

    /** Degrees brought into 0..359, whichever direction they came from. */
    fun normalise(degrees: Int): Int = ((degrees % 360) + 360) % 360

    /**
     * [degrees] as radians.
     *
     * Positive is clockwise, matching Compose's `rotate` and CSS's `rotate()`,
     * because screen y grows downwards. Getting this backwards is invisible at
     * 180 degrees and obvious at 90, which is a nasty way to find out.
     */
    private fun radians(degrees: Float): Float = degrees * (PI.toFloat() / 180f)

    /** [point] turned [degrees] about [pivot]. */
    fun rotate(point: PointF, pivot: PointF, degrees: Float): PointF {
        if (degrees % 360f == 0f) return point
        val r = radians(degrees)
        val c = cos(r)
        val s = sin(r)
        val dx = point.x - pivot.x
        val dy = point.y - pivot.y
        return PointF(
            x = pivot.x + dx * c - dy * s,
            y = pivot.y + dx * s + dy * c,
        )
    }

    /** The centre of [rect], where every rotation pivots. */
    fun centreOf(rect: IntRect): PointF =
        PointF(rect.x + rect.width / 2f, rect.y + rect.height / 2f)

    /**
     * Where each handle sits once the element has been turned [degrees].
     *
     * The eight points are computed unrotated - which is trivial - and then
     * turned as a set, so a handle can never drift away from the corner it
     * belongs to.
     */
    fun handleCentres(rect: IntRect, degrees: Int): Map<ResizeHandle, PointF> {
        val pivot = centreOf(rect)
        val angle = degrees.toFloat()
        return unrotatedHandleCentres(rect).mapValues { (_, point) -> rotate(point, pivot, angle) }
    }

    /** The eight handle positions on an unturned rectangle. */
    fun unrotatedHandleCentres(rect: IntRect): Map<ResizeHandle, PointF> {
        val left = rect.x.toFloat()
        val right = (rect.x + rect.width).toFloat()
        val top = rect.y.toFloat()
        val bottom = (rect.y + rect.height).toFloat()
        val midX = (left + right) / 2f
        val midY = (top + bottom) / 2f
        return mapOf(
            ResizeHandle.TOP_LEFT to PointF(left, top),
            ResizeHandle.TOP to PointF(midX, top),
            ResizeHandle.TOP_RIGHT to PointF(right, top),
            ResizeHandle.LEFT to PointF(left, midY),
            ResizeHandle.RIGHT to PointF(right, midY),
            ResizeHandle.BOTTOM_LEFT to PointF(left, bottom),
            ResizeHandle.BOTTOM to PointF(midX, bottom),
            ResizeHandle.BOTTOM_RIGHT to PointF(right, bottom),
        )
    }

    /** The four corners of [rect] turned [degrees], clockwise from top-left. */
    fun corners(rect: IntRect, degrees: Int): List<PointF> {
        val pivot = centreOf(rect)
        val angle = degrees.toFloat()
        return listOf(
            PointF(rect.x.toFloat(), rect.y.toFloat()),
            PointF((rect.x + rect.width).toFloat(), rect.y.toFloat()),
            PointF((rect.x + rect.width).toFloat(), (rect.y + rect.height).toFloat()),
            PointF(rect.x.toFloat(), (rect.y + rect.height).toFloat()),
        ).map { rotate(it, pivot, angle) }
    }

    /**
     * A drag in world space, expressed in the element's own frame.
     *
     * This is what makes dragging the handle of a turned element feel like
     * anything at all. The handle labelled RIGHT on an element turned ninety
     * degrees is pointing *down* the screen, so a downward drag has to read as
     * "wider", not "taller" - which means undoing the rotation on the delta
     * before the resize maths, which knows only about left, right, top and
     * bottom, ever sees it.
     */
    fun toLocalDelta(dx: Float, dy: Float, degrees: Int): PointF =
        rotate(PointF(dx, dy), PointF(0f, 0f), -degrees.toFloat())

    /**
     * [start] resized to [resized], moved so the dragged handle's opposite
     * edge stays where it looks like it is.
     *
     * Without this the element appears to slide sideways as it is resized. The
     * resize maths pins the opposite corner in *local* space, which is correct
     * and, once the element is turned, not where that corner actually is on
     * screen: local top-left is up-and-left of centre, and after a forty-degree
     * turn that same corner is somewhere else entirely. So the fixed point is
     * computed in world space before and after, and the difference is given
     * back as a translation.
     *
     * At zero degrees this returns [resized] untouched, which is what makes it
     * safe to route every resize through here.
     */
    fun anchorAfterResize(start: IntRect, resized: IntRect, handle: ResizeHandle, degrees: Int): IntRect {
        val angle = normalise(degrees)
        if (angle == 0) return resized

        // The corner that must not appear to move is the one opposite the
        // handle being dragged. An edge handle pins the opposite edge, so its
        // free axis contributes nothing and the midpoint is the right anchor.
        val signX = when {
            handle.affectsLeft -> 1f
            handle.affectsRight -> -1f
            else -> 0f
        }
        val signY = when {
            handle.affectsTop -> 1f
            handle.affectsBottom -> -1f
            else -> 0f
        }

        val before = anchorPoint(start, signX, signY, angle)
        val after = anchorPoint(resized, signX, signY, angle)
        val shiftX = (before.x - after.x).roundToInt()
        val shiftY = (before.y - after.y).roundToInt()
        return IntRect(resized.x + shiftX, resized.y + shiftY, resized.width, resized.height)
    }

    /** The world position of the anchor at ([signX], [signY]) of a turned rect. */
    private fun anchorPoint(rect: IntRect, signX: Float, signY: Float, degrees: Int): PointF {
        val centre = centreOf(rect)
        val local = PointF(
            centre.x + signX * rect.width / 2f,
            centre.y + signY * rect.height / 2f,
        )
        return rotate(local, centre, degrees.toFloat())
    }

    /**
     * The angle from an element's centre to [point], as a whole degree.
     *
     * Used by the on-canvas rotation knob: where the finger is *is* the angle,
     * so the element follows the pointer exactly rather than accumulating a
     * drift over a long drag.
     */
    fun angleTo(centre: PointF, point: PointF): Int {
        val dx = point.x - centre.x
        val dy = point.y - centre.y
        if (dx == 0f && dy == 0f) return 0
        val degrees = kotlin.math.atan2(dy, dx) * (180f / PI.toFloat())
        // atan2 measures from the positive x axis; the knob sits above the
        // element, so straight up has to read as zero rather than -90.
        return normalise((degrees + 90f).roundToInt())
    }

    /**
     * [degrees] pulled onto the nearest multiple of [step] when it is close.
     *
     * A free rotation that cannot easily be brought back to square is a
     * frustrating one, and nobody drags to exactly 90 by hand. Only snaps
     * within [tolerance], so any angle in between is still reachable - which is
     * the whole point of allowing custom angles.
     */
    fun snap(degrees: Int, step: Int = 15, tolerance: Int = 4): Int {
        if (step <= 0) return normalise(degrees)
        val angle = normalise(degrees)
        val nearest = ((angle.toFloat() / step).roundToInt() * step)
        val distance = kotlin.math.abs(angle - nearest)
        return if (distance <= tolerance) normalise(nearest) else angle
    }

    /** Angles offered as one-tap choices next to the rotation field. */
    val PRESETS = listOf(0, 15, 30, 45, 60, 90, 135, 180, 225, 270, 315)
}
