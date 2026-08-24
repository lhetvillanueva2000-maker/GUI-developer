package com.mcguidesigner.styles.notice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DragOutcomeTest {

    @Test
    fun `a long drag downwards opens the panel`() {
        assertTrue(dragOutcome(totalDrag = 60f, velocity = 0f, expanded = false))
    }

    @Test
    fun `a long drag upwards closes it`() {
        assertFalse(dragOutcome(totalDrag = -60f, velocity = 0f, expanded = true))
    }

    @Test
    fun `a fast flick counts even when it barely moved`() {
        // The whole point of the velocity threshold: a quick flick covers
        // almost no distance, and ignoring it makes the gesture feel broken.
        assertTrue(dragOutcome(totalDrag = 4f, velocity = 900f, expanded = false))
        assertFalse(dragOutcome(totalDrag = -4f, velocity = -900f, expanded = true))
    }

    @Test
    fun `a slipped tap changes nothing`() {
        assertFalse(dragOutcome(totalDrag = 3f, velocity = 20f, expanded = false))
        assertTrue(dragOutcome(totalDrag = -3f, velocity = -20f, expanded = true))
    }

    @Test
    fun `down opens even when it is already open`() {
        assertTrue(dragOutcome(totalDrag = 60f, velocity = 0f, expanded = true))
    }

    @Test
    fun `the sign is not inverted`() {
        // A flipped sign here turns "swipe down to open" into "swipe down to
        // close", which nothing that inspects composition would catch.
        assertTrue(dragOutcome(DRAG_DISTANCE_THRESHOLD + 1f, 0f, expanded = false))
        assertFalse(dragOutcome(-(DRAG_DISTANCE_THRESHOLD + 1f), 0f, expanded = true))
    }

    @Test
    fun `exactly at the threshold is not yet a swipe`() {
        assertFalse(dragOutcome(DRAG_DISTANCE_THRESHOLD, 0f, expanded = false))
        assertFalse(dragOutcome(0f, DRAG_VELOCITY_THRESHOLD, expanded = false))
    }
}

class NoticeQueueTest {

    private fun notice(id: String, transient: Boolean = false) =
        Notice(id = id, headline = id, transient = transient)

    @Test
    fun `the newest notice is the one on show`() {
        val queue = Notices.post(Notices.post(emptyList(), notice("a")), notice("b"))
        assertEquals("b", queue.first().id)
    }

    @Test
    fun `posting the same id replaces rather than stacks`() {
        // The failure this prevents: a status message that fires on every
        // nudge turning the panel into a pile of identical lines.
        var queue = Notices.post(emptyList(), notice("status"))
        repeat(5) { queue = Notices.post(queue, notice("status")) }
        assertEquals(1, queue.size)
    }

    @Test
    fun `the queue is capped`() {
        var queue = emptyList<Notice>()
        repeat(Notices.MAX + 3) { queue = Notices.post(queue, notice("n$it")) }
        assertEquals(Notices.MAX, queue.size)
        assertEquals("n${Notices.MAX + 2}", queue.first().id, "the newest must survive the trim")
    }

    @Test
    fun `dismissing removes exactly one`() {
        val queue = Notices.post(Notices.post(emptyList(), notice("a")), notice("b"))
        assertEquals(listOf("b"), Notices.dismiss(queue, "a").map { it.id })
        assertEquals(queue, Notices.dismiss(queue, "missing"), "an unknown id is a no-op")
    }

    @Test
    fun `dismissing every transient leaves the durable ones`() {
        var queue = Notices.post(emptyList(), notice("release"))
        queue = Notices.post(queue, notice("status", transient = true))
        assertEquals(listOf("release"), Notices.dismissTransient(queue).map { it.id })
    }

    @Test
    fun `an empty queue is what makes the panel disappear`() {
        // Not a rendering assertion - it is the contract the strip is written
        // against, and the reason the shells clear rather than blank.
        assertTrue(Notices.dismiss(listOf(notice("only")), "only").isEmpty())
    }

    @Test
    fun `only a notice with detail can be expanded`() {
        assertFalse(Notice(id = "a", headline = "one line").expandable)
        assertTrue(Notice(id = "a", headline = "one line", points = listOf("more")).expandable)
    }
}

class AppNoticeTest {

    @Test
    fun `the release note carries its detail`() {
        assertTrue(AppNotice.current.headline.isNotBlank())
        assertTrue(AppNotice.current.points.isNotEmpty())
        assertTrue(AppNotice.current.expandable)
        assertFalse(AppNotice.current.transient, "a release note must not expire on a timer")
    }

    @Test
    fun `the note is unread until this exact build's id is stored`() {
        assertTrue(AppNotice.isUnread(null), "a fresh install has read nothing")
        assertTrue(AppNotice.isUnread("whatsnew-0.0.1"), "an older build's note is not this one")
        assertFalse(AppNotice.isUnread(AppNotice.current.id))
    }

    @Test
    fun `every point says something`() {
        AppNotice.current.points.forEach { assertTrue(it.length > 30, "stub point: $it") }
    }
}
