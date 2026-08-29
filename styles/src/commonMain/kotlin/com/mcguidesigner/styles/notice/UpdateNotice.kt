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
        headline = "${Branding.NAME} ${Branding.VERSION} — the preview runs now, and the paint canvas has a ruler",
        points = listOf(
            "The Preview tab is Preview / Demo, and the screen actually works. " +
                "Press a button and it presses. Flip a switch, tick a box, drag " +
                "a slider, cycle a dropdown, type into a field, scroll a list, " +
                "pinch to zoom. The whole point of a preview is finding out " +
                "whether the layout works when somebody uses it, and a picture " +
                "cannot answer that.",
            "Nothing you do in the demo touches the document. It never dirties " +
                "the project, never enters the undo history, and Reset puts every " +
                "widget back the way you drew it. The five state buttons are " +
                "still there for pinning everything to hover or pressed or " +
                "disabled at once, which is how you check a skin.",
            "A ruler in the paint canvas, and it is nine rulers: straight edge, " +
                "parallel, cross, circle, ellipse, radial, and one, two and " +
                "three-point perspective. Each holds a stroke to its shape " +
                "through the point you started at — so where you begin chooses " +
                "which line or which circle, and the ruler only decides the " +
                "direction. A finger cannot draw a straight line and now it does " +
                "not have to.",
            "A real selection: box, ellipse, lasso and magic wand, with expand, " +
                "contract, soften, invert, and add / subtract / intersect. " +
                "Everything is confined to it — the brush, the eraser, the " +
                "bucket, blur, smudge, fill, clear and the cutout. A selection " +
                "only some tools respect is worse than none, because it teaches " +
                "you not to trust it. Soften it and what you draw fades out at " +
                "the edge instead of stopping dead.",
            "The canvas zooms four times deeper — to 64x, where one design pixel " +
                "is a tile whose halves you can actually see. At the old ceiling " +
                "a pixel was smaller than the fingertip aiming at it.",
            "Scroll containers finally clip what does not fit, and their " +
                "scrollbars show where the list actually is instead of always " +
                "saying \"at the top\". The desktop / mobile layout switch is " +
                "gone: a safe-area margin is now just a margin, drawn when you " +
                "set one.",
        ),
    )

    /** Whether [dismissedId] means this build's note has already been read. */
    fun isUnread(dismissedId: String?): Boolean = dismissedId != current.id
}
