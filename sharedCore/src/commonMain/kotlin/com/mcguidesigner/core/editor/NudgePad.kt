package com.mcguidesigner.core.editor

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The move pad's arithmetic, with no Compose in it.
 *
 * The pad is two things stacked, and keeping them separate is the whole point
 * of the layout:
 *
 *  - **The middle is only the four directions.** Up, down, left, right, and a
 *    hole where a fifth button would be. Nothing else lives in the cross,
 *    because everything else that ever lived there was something you pressed by
 *    accident while reaching for an arrow.
 *  - **Under it is one control**, and that control is the step. One bar, one
 *    number, one gesture.
 *
 * The step bar is *positional* rather than a relative drag: where your finger
 * is along the bar is the value, the way a slider works. A relative drag means
 * getting from 1 to 16 is a long push and a short one undoes an unknown amount
 * of it; a position means every value in the range is one touch away, which is
 * what "easily controlled" has to mean on a control this small.
 *
 * The scale is exponential, so 1, 2, 4, 8, 16, 32, 64 and 128 land at even
 * intervals along the bar. A linear scale would give the whole left half to
 * 1-64 territory nobody wants and squeeze 1 through 8 - the values actually
 * used - into the first few millimetres.
 */
object NudgePad {

    /** Key size at scale 1.0, in dp. What the pad has always been. */
    const val BASE_KEY_DP = 44f

    /** Gap between keys at scale 1.0, in dp. */
    const val BASE_GAP_DP = 3f

    /** How far the resize handle travels to take the pad from end to end, in dp. */
    private const val RESIZE_TRAVEL_DP = 150f

    /**
     * The value the bar returns to when tapped.
     *
     * One pixel, because the pad exists for the adjustment a fingertip cannot
     * make and that adjustment is nearly always one pixel. Tapping comes back
     * here from wherever a drag left it, so being lost at 37 is one tap from
     * fixed.
     */
    const val HOME_STEP = 1

    /** Sizes worth marking on the bar, and the reason each is worth marking. */
    val LANDMARKS = listOf(1, 8, 16)

    // -- The step bar ------------------------------------------------------

    /**
     * The step at [fraction] along the bar, where 0 is the left end.
     *
     * `2^(f * log2(MAX))`, so the ends are exactly [EditorSettings.MIN_STEP]
     * and [EditorSettings.MAX_STEP] and each doubling is the same distance.
     */
    fun stepAtFraction(fraction: Float): Int {
        if (fraction.isNaN()) return HOME_STEP
        val clamped = fraction.coerceIn(0f, 1f)
        val exponent = clamped * log2(EditorSettings.MAX_STEP.toFloat())
        return 2f.pow(exponent).roundToInt().coerceIn(EditorSettings.MIN_STEP, EditorSettings.MAX_STEP)
    }

    /**
     * Where [step] sits along the bar.
     *
     * The inverse of [stepAtFraction] to within the rounding that turns a
     * continuous position into a whole number of pixels - which is why the
     * round trip is asserted as "lands on the same step", not "the same float".
     */
    fun fractionForStep(step: Int): Float {
        val clamped = step.coerceIn(EditorSettings.MIN_STEP, EditorSettings.MAX_STEP)
        return (log2(clamped.toFloat()) / log2(EditorSettings.MAX_STEP.toFloat())).coerceIn(0f, 1f)
    }

    private fun log2(value: Float): Float = ln(value) / ln(2f)

    /**
     * Whether [step] is one of the sizes worth calling out on the bar.
     *
     * 1 is home, 8 is a vanilla Java container's grid and 16 is one texture
     * tile. Marked only - nothing snaps to them, because a bar that jumps past
     * the number you wanted is worse than one that does not help you find it.
     */
    fun isLandmark(step: Int): Boolean = step in LANDMARKS

    // -- Resizing ----------------------------------------------------------

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
     * Additive rather than multiplicative: a multiplicative drag moves fast at
     * the big end and crawls at the small end, so the pad overshoots exactly
     * where the fine control is wanted.
     */
    fun scaleAfterDrag(current: Float, dragDp: Float): Float =
        clampScale(clampScale(current) + dragDp / RESIZE_TRAVEL_DP)

    /** The nearest landmark within [tolerance] steps, or null. A hint, not a snap. */
    fun nearestLandmark(step: Int, tolerance: Int = 2): Int? =
        LANDMARKS.filter { abs(it - step) <= tolerance }.minByOrNull { abs(it - step) }
}
