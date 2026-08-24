package com.mcguidesigner.styles.notice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which way a swipe on the notice strip goes.
 *
 * Gesture thresholds get tuned by feel and then regress silently: a flipped
 * sign here turns "pull down to open" into "pull down to close", and no test
 * that only looks at composition would notice. Down is positive, matching the
 * pointer axis.
 */
class NoticeDragTest {

    @Test
    fun `a downward drag opens it`() {
        assertTrue(dragOutcome(totalDrag = 60f, velocity = 0f, expanded = false))
    }

    @Test
    fun `an upward drag closes it`() {
        assertFalse(dragOutcome(totalDrag = -60f, velocity = 0f, expanded = true))
    }

    @Test
    fun `a flick counts even when it barely moved`() {
        // The whole point of reading velocity: a fast swipe covers very little
        // distance before the finger leaves, and ignoring it feels broken.
        assertTrue(dragOutcome(totalDrag = 4f, velocity = 900f, expanded = false))
        assertFalse(dragOutcome(totalDrag = -4f, velocity = -900f, expanded = true))
    }

    @Test
    fun `a nudge too small to mean anything leaves it alone`() {
        assertFalse(dragOutcome(totalDrag = 5f, velocity = 20f, expanded = false))
        assertTrue(dragOutcome(totalDrag = -5f, velocity = -20f, expanded = true))
    }

    @Test
    fun `a drag that repeats the current state is not a toggle`() {
        // Pulling down on something already open keeps it open. Treating the
        // gesture as a toggle would make a second pull close it, which is the
        // opposite of what the hand just asked for.
        assertTrue(dragOutcome(totalDrag = 80f, velocity = 400f, expanded = true))
        assertFalse(dragOutcome(totalDrag = -80f, velocity = -400f, expanded = false))
    }

    @Test
    fun `direction wins over the starting state at every magnitude`() {
        listOf(30f, 100f, 400f, 5000f).forEach { distance ->
            assertTrue(dragOutcome(distance, 0f, expanded = false), "down $distance")
            assertTrue(dragOutcome(distance, 0f, expanded = true), "down $distance")
            assertFalse(dragOutcome(-distance, 0f, expanded = false), "up $distance")
            assertFalse(dragOutcome(-distance, 0f, expanded = true), "up $distance")
        }
    }

    @Test
    fun `the thresholds are the right way round`() {
        assertTrue(DRAG_DISTANCE_THRESHOLD > 0f)
        assertTrue(DRAG_VELOCITY_THRESHOLD > 0f)
        // Small enough to reach with a thumb without committing to a full
        // sheet drag; large enough that a sloppy tap does not trip it.
        assertTrue(DRAG_DISTANCE_THRESHOLD in 8f..64f)
    }
}

/** The message itself. Static data nobody compiles against, so worth pinning. */
class UpdateNoticeTest {

    @Test
    fun `the shipped notice is filled in`() {
        val notice = AppNotice.current
        assertTrue(notice.version.isNotBlank())
        assertTrue(notice.headline.isNotBlank())
        assertTrue(notice.points.isNotEmpty())
        notice.points.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `the headline fits on one line`() {
        // It is rendered with maxLines = 1 and ellipsised. A headline that is
        // always cut off is a headline nobody reads.
        assertTrue(
            AppNotice.current.headline.length <= 90,
            "headline is ${AppNotice.current.headline.length} characters",
        )
    }

    @Test
    fun `the version is a plain version`() {
        val version = AppNotice.current.version
        assertTrue(version.all { it.isDigit() || it == '.' }, "'$version' is not a version")
        assertEquals(3, version.split('.').size, "expected major.minor.patch, got '$version'")
    }
}
