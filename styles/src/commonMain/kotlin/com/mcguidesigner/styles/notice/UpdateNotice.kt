package com.mcguidesigner.styles.notice

/**
 * The what's-new message carried under the editor's top bar.
 *
 * Plain data with no UI in it, for the same reason the payment details are:
 * this is the one thing in the app most likely to be edited by someone who is
 * not reading the rendering code, and it should never be necessary to.
 *
 * The strip that draws it is [NoticeStrip]. It sits exactly where the edition
 * tabs used to, which is deliberate - that row already read as "something
 * about the app rather than about the document", and the notice is the only
 * thing left that belongs there.
 */
data class UpdateNotice(
    /** Shown as a chip on the left. Short enough to be read at a glance. */
    val version: String,
    /** One line, always visible. This is what has to earn the expansion. */
    val headline: String,
    /** The detail, revealed on expand. Each entry is one sentence or two. */
    val points: List<String>,
)

/** The notice this build ships with. */
object AppNotice {

    val current = UpdateNotice(
        version = "1.5.0",
        headline = "New home screen — the edition is chosen before the editor opens",
        points = listOf(
            "Java or Bedrock is now picked on a home screen, and each card is drawn " +
                "in that edition's own widget language. Java comes out flat and square; " +
                "Bedrock comes out soft-cornered with a 2px border. You are choosing by " +
                "looking at the thing you are choosing.",
            "The edition tabs have left the editor. The header reports which edition " +
                "you are in, and the arrow at the top left goes back to the picker. " +
                "Nothing about the open document is lost on the way there or back.",
            "Escape steps out of the editor once there is nothing left inside it to " +
                "dismiss — it still cancels a placement or clears a selection first. " +
                "On Android the system back gesture follows the same order.",
            "The support page moved to home, behind the hand-and-heart mark in the top " +
                "right. It is still the whole of the app's monetisation: no ads, no " +
                "paywall, no account, no telemetry.",
        ),
    )
}
