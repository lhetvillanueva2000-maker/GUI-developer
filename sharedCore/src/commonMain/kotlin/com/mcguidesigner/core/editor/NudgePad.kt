package com.mcguidesigner.core.editor

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The move pad's arithmetic, with no Compose in it.
 *
 * Everything here is a pure function of a gesture and the current settings,
 * which is the only reason any of it can be checked: a drag that resizes a
 * control and a drag that changes a number are exactly the sort of thing that
 * feels right on the developer's device, is unusable on somebody else's, and
 * cannot be argued about without the numbers written down somewhere.
 *
 * The pad itself is four arrows and one centre control. It used to be four
 * arrows, a centre that toggled between two step sizes, and a button
 * underneath that opened a settings sheet to change what those two sizes were -
 * three different ways to answer "how far does this move", two of which were
 * not on the pad. Now the centre *is* the step: one number, dragged.
 */
object NudgePad {

    /** Key size at scale 1.0, in dp. What the pad has always been. */
    const val BASE_KEY_DP = 44f

    /** Gap between keys at scale 1.0, in dp. */
    const val BASE_GAP_DP = 3f

    /** How far the resize handle must be dragged to double the pad, in dp. */
    private const val RESIZE_TRAVEL_DP = 180f

    /** How far the centre must be dragged to change the step by one, in dp. */
    private const val STEP_TRAVEL_DP = 20f

    /**
     * The value the centre returns to.
     *
     * One pixel, because the pad exists for the adjustment a fingertip cannot
     * make, and that adjustment is nearly always one pixel. Tapping the centre
     * comes back here from wherever a drag left it, so getting lost at 37 is
     * always one tap from fixed.
     */
    const val HOME_STEP = 1

    /** Key size in dp at [scale]. */
    fun keyDp(scale: Float): Float = BASE_KEY_DP * clampScale(scale)

    /** Gap between keys in dp at [scale]. */
    fun gapDp(scale: Float): Float = BASE_GAP_DP * clampScale(scale)

    /** [scale], brought inside the supported range and never NaN. */
    fun clampScale(scale: Float): Float =
        if (scale.isNaN()) 1f else scale.coerceIn(EditorSettings.MIN_PAD_SCALE, EditorSettings.MAX_PAD_SCALE)

    /**
     * The scale after dragging the resize handle by [dragDp].
     *
     * Positive is away from the pad's anchored corner, which is the direction
     * that makes it bigger whichever corner it is in - the shell converts the
     * raw gesture to that convention, because only it knows which corner the
     * pad is in.
     *
     * Additive rather than multiplicative on purpose: a multiplicative drag
     * moves fast at the big end and crawls at the small end, so the pad
     * overshoots exactly where the fine control is wanted.
     */
    fun scaleAfterDrag(current: Float, dragDp: Float): Float =
        clampScale(clampScale(current) + dragDp / RESIZE_TRAVEL_DP)

    /**
     * The step after dragging the centre by [dragDp], right or up being more.
     *
     * Accumulating in dp rather than counting drag events, because a slow drag
     * delivers many small deltas and a flick delivers a few large ones, and
     * counting events makes the two mean different things.
     */
    fun stepAfterDrag(current: Int, dragDp: Float): Int {
        val moved = (dragDp / STEP_TRAVEL_DP).roundToInt()
        return (current + moved).coerceIn(EditorSettings.MIN_STEP, EditorSettings.MAX_STEP)
    }

    /**
     * Drag distance in dp that [step] sits at, relative to [from].
     *
     * The inverse of [stepAfterDrag], so a shell can hold the *accumulated*
     * gesture rather than re-deriving it and drifting by a pixel per frame.
     */
    fun dragDpFor(from: Int, step: Int): Float = (step - from) * STEP_TRAVEL_DP

    /**
     * Whether [step] is one of the sizes worth calling out.
     *
     * 1 is the home value, 8 is a vanilla Java container's grid and 16 is a
     * texture tile, so those three are where somebody dragging is usually
     * trying to land. Used only to mark them; nothing is snapped, because a pad
     * that jumps past the number you wanted is worse than one that does not
     * help you find it.
     */
    fun isLandmark(step: Int): Boolean = step == 1 || step == 8 || step == 16

    /**
     * The nearest landmark within [toleranceSteps], or null.
     *
     * Offered as a hint the shell can show while dragging; acting on it is the
     * shell's choice, and the shipping pad does not.
     */
    fun nearestLandmark(step: Int, toleranceSteps: Int = 2): Int? =
        listOf(1, 8, 16).filter { abs(it - step) <= toleranceSteps }.minByOrNull { abs(it - step) }
}
