package com.mcguidesigner.core.editor

import kotlinx.serialization.Serializable

/** Which corner of the canvas the on-screen move pad sits in. */
@Serializable
enum class NudgePadCorner(val displayName: String) {
    BOTTOM_RIGHT("Bottom right"),
    BOTTOM_LEFT("Bottom left"),
    TOP_RIGHT("Top right"),
    TOP_LEFT("Top left"),
    ;

    val isBottom: Boolean get() = this == BOTTOM_RIGHT || this == BOTTOM_LEFT
    val isRight: Boolean get() = this == BOTTOM_RIGHT || this == TOP_RIGHT
}

/**
 * Editor behaviour the user can tune, shared by both front-ends.
 *
 * Kept in the editor state rather than in each platform's preferences file so
 * that one definition - and one set of defaults - drives the desktop keyboard,
 * the desktop move pad and the Android move pad alike.  Each platform still
 * owns *persisting* it, because where settings live is a platform question;
 * what they mean is not.
 *
 * Every value is a plain number or flag for the same reason the preferences
 * files are: settings outlive the build that wrote them.
 */
@Serializable
data class EditorSettings(
    /**
     * GUI pixels one press of a move button - or an arrow key - shifts the
     * selection.
     */
    val nudgeStep: Int = 1,
    /**
     * The larger step, used when Shift is held or the pad's "big step" toggle
     * is on.  Defaults to a vanilla Java container's 8px grid.
     */
    val largeNudgeStep: Int = 8,
    /**
     * Whether the arrow keys move by [nudgeStep] too.
     *
     * On by default so the keyboard and the buttons cannot disagree, which is
     * the whole point of having one setting.  Turning it off restores the
     * fixed 1px / 8px arrow behaviour for anyone who wants the buttons coarse
     * and the keys fine.
     */
    val arrowKeysUseNudgeStep: Boolean = true,
    /**
     * Round a nudged element onto the grid instead of offsetting it.
     *
     * Off by default: a nudge is a deliberate small correction, and quietly
     * snapping it somewhere else is the opposite of what was asked for.
     */
    val nudgeSnapsToGrid: Boolean = false,
    /** Whether the four move buttons appear when something is selected. */
    val showNudgePad: Boolean = true,
    val nudgePadCorner: NudgePadCorner = NudgePadCorner.BOTTOM_RIGHT,
    /** How far a duplicate lands from its original, in GUI pixels. */
    val duplicateOffset: Int = 8,
    /** Seconds between autosaves of a dirty document; 0 disables it. */
    val autosaveSeconds: Int = 10,
    /** Ask before deleting a selection, rather than relying on undo. */
    val confirmBeforeDelete: Boolean = false,
    /**
     * Keep the canvas animating imported GIFs.
     *
     * Off pins every animated element to its first frame, which makes precise
     * layout work against a busy animation far easier.
     */
    val playAnimations: Boolean = true,
) {
    /** The step for a nudge, honouring whether [large] was requested. */
    fun stepFor(large: Boolean): Int =
        (if (large) largeNudgeStep else nudgeStep).coerceIn(MIN_STEP, MAX_STEP)

    /** Clamps every field into its supported range. */
    fun sanitised(): EditorSettings = copy(
        nudgeStep = nudgeStep.coerceIn(MIN_STEP, MAX_STEP),
        largeNudgeStep = largeNudgeStep.coerceIn(MIN_STEP, MAX_STEP),
        duplicateOffset = duplicateOffset.coerceIn(0, MAX_STEP),
        autosaveSeconds = autosaveSeconds.coerceIn(0, 600),
    )

    companion object {
        const val MIN_STEP = 1
        const val MAX_STEP = 128

        /** Step sizes offered as one-tap presets in the settings screens. */
        val STEP_PRESETS = listOf(1, 2, 4, 8, 16)
    }
}
