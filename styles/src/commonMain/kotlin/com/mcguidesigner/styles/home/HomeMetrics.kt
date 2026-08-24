package com.mcguidesigner.styles.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.mcguidesigner.styles.layout.AdaptiveMetrics
import com.mcguidesigner.styles.layout.LocalAdaptive
import com.mcguidesigner.styles.layout.WindowSizeClass

/**
 * Home-screen-only layout decisions.
 *
 * Everything numeric here is derived from [AdaptiveMetrics] rather than
 * respecified, so home breathes at the same rate as the editor. The two
 * things it does decide for itself:
 *
 *  - The navigation rail is suppressed. `metrics.usesRail` is true on tablet
 *    and desktop, but home has nothing to navigate *to* yet - the whole
 *    screen is the choice - so a rail would be a column of dead icons.
 *  - The edition cards sit side by side from MEDIUM up, and stack on COMPACT.
 */
object HomeMetrics {

    /**
     * Printed beside the wordmark.
     *
     * A constant rather than a build-time value: `styles` is a library module
     * with no `project.version` to read, and a wrong number here is a cosmetic
     * fib rather than a broken build - which is exactly the kind of thing that
     * rots unnoticed, so it lives in one place with a comment on it.
     */
    const val VERSION = "1.5.0"

    /** Two columns from tablet width up, one on a phone. */
    fun sideBySide(metrics: AdaptiveMetrics): Boolean =
        metrics.sizeClass != WindowSizeClass.COMPACT

    /** Height of the top chrome bar. Tablet gets a little more, for thumbs. */
    fun chromeHeight(metrics: AdaptiveMetrics) = when (metrics.sizeClass) {
        WindowSizeClass.MEDIUM -> 60.dp
        else -> 56.dp
    }

    /**
     * Icon button side, capped at 48dp so the phone's 52dp touch target does
     * not make the buttons taller than the bar holding them.
     */
    fun iconTarget(metrics: AdaptiveMetrics) =
        if (metrics.minTarget > 48.dp) 48.dp else metrics.minTarget

    /**
     * How far to scale a widget up from its authentic control height. A 20dp
     * Java button is correct in-game and an unreadable landing target here,
     * so it is drawn larger - tightest on desktop, where the pointer is
     * precise and the eye is closer.
     */
    fun widgetScale(metrics: AdaptiveMetrics): Float = when (metrics.sizeClass) {
        WindowSizeClass.COMPACT  -> 2.6f
        WindowSizeClass.MEDIUM   -> 2.8f
        WindowSizeClass.EXPANDED -> 2.4f
    }

    @Composable
    fun current(): AdaptiveMetrics = LocalAdaptive.current
}
