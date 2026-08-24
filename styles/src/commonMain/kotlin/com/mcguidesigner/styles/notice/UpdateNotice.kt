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
        headline = "${Branding.NAME} ${Branding.VERSION} — new name, settings page, seven themes",
        points = listOf(
            "The app is called ${Branding.NAME} now. Your projects, prefabs, texture " +
                "library and preferences are all read exactly as before.",
            "Settings live behind the gear on the home screen: seven themes, three " +
                "motion levels, and a name for the home screen to greet you by. " +
                "Everything stays on this device.",
            "Motion can be turned down or off. On an older phone that is the setting " +
                "worth changing — the drifting backdrop redraws every frame while it " +
                "moves, and Reduced stops everything that loops.",
            "Home links out to public plugins and public creations. Both galleries are " +
                "still being set up, so the buttons say so rather than opening nothing.",
            "Android tells a tablet from a phone by the screen rather than the window, " +
                "so a phone in landscape keeps the phone layout instead of being handed " +
                "a rail and a docked inspector.",
        ),
    )

    /** Whether [dismissedId] means this build's note has already been read. */
    fun isUnread(dismissedId: String?): Boolean = dismissedId != current.id
}
