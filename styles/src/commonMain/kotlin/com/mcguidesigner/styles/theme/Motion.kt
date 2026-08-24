package com.mcguidesigner.styles.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How much the interface is allowed to move.
 *
 * This is a settings-level choice with a real cost behind it, not a taste
 * knob. Three of the app's animations are *continuous* - the drifting
 * backdrop, the star field behind the home screen, the chrome cross-fade -
 * and a continuous animation means Compose redraws that subtree every frame
 * for as long as it is on screen. On a mid-range phone that is the difference
 * between the editor idling at nearly nothing and idling at a steady 60fps of
 * work it does not need to be doing.
 *
 * So the levels are ordered by what they *stop*, not by how fast things go:
 *
 *  - [FULL] - everything, including the loops that never end.
 *  - [REDUCED] - transitions still animate, but nothing loops. This is the
 *    level that matters for battery and for low-end hardware, and it is where
 *    a device that reports "prefers reduced motion" should land.
 *  - [OFF] - nothing animates. Every transition becomes a cut.
 *
 * [REDUCED] is not [FULL] with a smaller number: a shorter infinite animation
 * is still an infinite animation.
 */
enum class MotionLevel(
    val displayName: String,
    val blurb: String,
    /** Multiplier on every finite duration. Zero means "cut, do not animate". */
    val scale: Float,
    /** Whether animations that never finish on their own are allowed to run. */
    val allowsLoops: Boolean,
) {
    FULL(
        displayName = "Full",
        blurb = "Everything moves, including the drifting backdrop.",
        scale = 1f,
        allowsLoops = true,
    ),
    REDUCED(
        displayName = "Reduced",
        blurb = "Transitions stay, looping motion stops. Easier on battery and older devices.",
        scale = 0.6f,
        allowsLoops = false,
    ),
    OFF(
        displayName = "Off",
        blurb = "No animation at all. Every change is a cut.",
        scale = 0f,
        allowsLoops = false,
    ),
    ;

    /** True when a finite transition should play rather than jump. */
    val animates: Boolean get() = scale > 0f

    /**
     * [base] milliseconds, scaled to this level.
     *
     * Floors at 1ms rather than 0 for [FULL] and [REDUCED] so a caller cannot
     * accidentally produce a zero-length spec that some animation APIs treat
     * as a division by zero; [OFF] returns 0 deliberately, and callers reach
     * for [spec] rather than branching on it themselves.
     */
    fun duration(base: Int): Int = when {
        scale <= 0f -> 0
        else -> (base * scale).toInt().coerceAtLeast(1)
    }

    companion object {
        fun fromName(name: String?): MotionLevel =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: FULL
    }
}

/**
 * A tween at this motion level, or an instant cut when motion is off.
 *
 * Typed as [FiniteAnimationSpec] so it drops into `AnimatedVisibility`'s
 * enter/exit transitions as well as `animate*AsState`.
 */
fun <T> MotionLevel.spec(
    baseMillis: Int,
    delayMillis: Int = 0,
    easing: androidx.compose.animation.core.Easing = LinearOutSlowInEasing,
): FiniteAnimationSpec<T> = if (!animates) {
    snap()
} else {
    tween(
        durationMillis = duration(baseMillis),
        delayMillis = duration(delayMillis),
        easing = easing,
    )
}

/** [preferred] when this level allows loops, otherwise an instant cut. */
fun <T> MotionLevel.loopSpec(preferred: AnimationSpec<T>, still: AnimationSpec<T> = snap()): AnimationSpec<T> =
    if (allowsLoops) preferred else still

/**
 * The motion level in force for this part of the tree.
 *
 * Defaults to [MotionLevel.FULL] so a component rendered outside a configured
 * shell - a preview, a test - still looks like the shipping app.
 */
val LocalMotion = staticCompositionLocalOf { MotionLevel.FULL }
