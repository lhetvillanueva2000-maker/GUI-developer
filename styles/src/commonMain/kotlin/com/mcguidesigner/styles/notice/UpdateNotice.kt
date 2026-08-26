package com.mcguidesigner.styles.notice

import com.mcguidesigner.core.Branding

/**
 * One thing the notification panel can show.
 *
 * Deliberately one type for both kinds of message the app produces - the
 * what's-new note that ships with a release, and the transient "Now designing
 * for Java Edition." that used to pop up from the bottom of the screen. They
 * were two mechanisms in two places before, which is how the app ended up with
 * a permanent strip at the top and a floating snackbar at the bottom saying
 * different things at the same time. One panel, one queue, one place to look.
 *
 * The strip that draws it is [NoticeStrip]. It sits exactly where the edition
 * tabs used to, which is deliberate: that row already read as "something about
 * the app rather than about the document".
 */
data class Notice(
    /**
     * Identity, not a label.
     *
     * Posting a notice whose id is already in the queue replaces it in place
     * rather than stacking a second copy - which is what keeps a status
     * message that fires on every nudge from turning into a pile.
     */
    val id: String,
    /** One line, always visible. This is what has to earn the expansion. */
    val headline: String,
    /** The detail, revealed on expand. Each entry is one sentence or two. */
    val points: List<String> = emptyList(),
    /**
     * Whether this disappears on its own.
     *
     * A status message is a receipt for something you just did and is stale
     * within seconds; a release note is not. Only the first sort expires.
     */
    val transient: Boolean = false,
) {
    /** Whether there is anything to reveal by pulling the panel down. */
    val expandable: Boolean get() = points.isNotEmpty()
}

/**
 * The queue, as pure functions.
 *
 * Kept out of the shells so both platforms cannot drift, and out of the
 * composable so the ordering and replacement rules can be tested without a
 * renderer. Both shells hold the list itself, because only they know when to
 * post to it.
 */
object Notices {

    /** More than this on screen is a log, not a notification. */
    const val MAX = 4

    /**
     * [current] with [notice] at the front.
     *
     * Replaces any existing entry with the same id rather than adding a
     * second, and trims the oldest away past [max].
     */
    fun post(current: List<Notice>, notice: Notice, max: Int = MAX): List<Notice> =
        (listOf(notice) + current.filterNot { it.id == notice.id }).take(max)

    /** [current] without the entry identified by [id]. */
    fun dismiss(current: List<Notice>, id: String): List<Notice> =
        current.filterNot { it.id == id }

    /** [current] without any entry that expires on its own. */
    fun dismissTransient(current: List<Notice>): List<Notice> =
        current.filterNot { it.transient }
}

/** The release note this build ships with. */
object AppNotice {

    /**
     * The id is the version, so the "have they already seen this" check is a
     * string comparison against what was stored, and shipping a new version
     * shows the note again without any extra bookkeeping.
     */
    val current = Notice(
        id = "whatsnew-${Branding.VERSION}",
        headline = "${Branding.NAME} ${Branding.VERSION} — scribble it away, and draw shapes by drawing them",
        points = listOf(
            "The magic eraser is a scribble now, not a tap, and it shows you a " +
                "glowing rainbow trail while you do it. A tap could only ever " +
                "take one colour, which is useless on a real object — a face is " +
                "a dozen colours, a leaf is a gradient. Scribble across the " +
                "thing and everything the scribble touches goes with it.",
            "The trail is not decoration. Nothing is removed until you lift your " +
                "finger, so without it the gesture would be invisible: you " +
                "scribble, see nothing, let go, and something vanishes. It runs " +
                "through the whole hue circle so it cannot be camouflaged by " +
                "whatever is underneath it.",
            "A Shape tool that reads what you drew. Rough box → rectangle; even " +
                "sides → square; three corners → triangle; a loop → a circle, or " +
                "an ellipse if it was squashed. Drawn at an angle, it stays at " +
                "that angle. A scribble it cannot read is left exactly as drawn, " +
                "which matters more than the recognition: quietly replacing your " +
                "drawing with a confidently wrong triangle is the worse failure.",
            "The screen animates the way it should. The launch button ripples " +
                "under your thumb, the editor's chrome arrives first, and the " +
                "canvas grows into place behind it. All of it respects the " +
                "motion setting, so Reduced still cuts straight there.",
        ),
    )

    /** Whether [dismissedId] means this build's note has already been read. */
    fun isUnread(dismissedId: String?): Boolean = dismissedId != current.id
}
