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
        headline = "${Branding.NAME} ${Branding.VERSION} — the third card is a paint canvas now",
        points = listOf(
            "Other UIs opens a white sheet you can draw on, not the element " +
                "editor with different colours. Layers with fourteen blend " +
                "modes, alpha lock and clipping; eight nibs from a hard dip pen " +
                "to a wide airbrush; bucket, eyedropper and a stabilizer; " +
                "mirror and radial symmetry; undo that records only the tiles a " +
                "stroke touched, so it works on a phone.",
            "The eraser is the brush with the arithmetic reversed, so it is " +
                "exactly as good. It takes alpha away and leaves colour alone, " +
                "which means a stroke erased to a whisper and painted back over " +
                "is still its own colour rather than a grey ghost.",
            "Cut out the background without a download. It builds a colour " +
                "model of the image's edges, labels every pixel against it, " +
                "cleans the boundary up so it follows real edges, then works " +
                "out a partial transparency for every pixel along it — so soft " +
                "edges come out soft instead of cut with scissors, and no " +
                "fringe of the old background is left behind. It tells you how " +
                "confident it is, and says so when it is not.",
            "Both halves of the app's own name are visible again. LABS is drawn " +
                "inside a slot in the active skin's colours, and the letters " +
                "took the chrome's text colour — near-white letters on a " +
                "near-white plate. Same mistake as the launch card's button, " +
                "same fix, and now a test measures both.",
            "Translucent brushes stay translucent. Dabs within one stroke " +
                "accumulate into a shared buffer instead of compositing " +
                "individually, so a 40% brush is 40% along its whole length " +
                "rather than solid wherever it overlapped itself.",
        ),
    )

    /** Whether [dismissedId] means this build's note has already been read. */
    fun isUnread(dismissedId: String?): Boolean = dismissedId != current.id
}
