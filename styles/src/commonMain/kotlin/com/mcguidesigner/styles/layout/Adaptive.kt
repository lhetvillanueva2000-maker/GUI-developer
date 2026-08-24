package com.mcguidesigner.styles.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much room the window has, in the only three sizes worth designing for.
 *
 * The whole point of naming them is that a layout decision is made once, here,
 * instead of being re-derived from a raw width at every call site - which is
 * how an app ends up with a rail at 601dp and a bottom bar at 599dp that
 * disagree about where the padding goes.
 *
 * The breakpoints are Material's, and they are chosen around real hardware
 * rather than round numbers: 600dp is where a phone in landscape and a small
 * tablet in portrait begin, 840dp is where a tablet in landscape and a desktop
 * window begin.
 */
enum class WindowSizeClass(val displayName: String) {
    /** Phone portrait. One pane, bottom navigation, everything else modal. */
    COMPACT("Phone"),

    /** Tablet, and phones turned sideways. A rail, and room for two panes. */
    MEDIUM("Tablet"),

    /** Desktop windows and large tablets in landscape. Docks stay open. */
    EXPANDED("Desktop"),
    ;

    val isCompact: Boolean get() = this == COMPACT
    val isExpanded: Boolean get() = this == EXPANDED
    val atLeastMedium: Boolean get() = this != COMPACT

    companion object {
        const val MEDIUM_MIN_DP = 600
        const val EXPANDED_MIN_DP = 840

        fun ofWidth(widthDp: Int): WindowSizeClass = when {
            widthDp >= EXPANDED_MIN_DP -> EXPANDED
            widthDp >= MEDIUM_MIN_DP -> MEDIUM
            else -> COMPACT
        }

        fun ofWidth(widthDp: Dp): WindowSizeClass = ofWidth(widthDp.value.toInt())
    }
}

/**
 * Short windows - a phone in landscape, a laptop with a browser open on top -
 * cannot afford a tall app bar or a bottom bar with labels no matter how wide
 * they are.
 */
enum class WindowHeightClass {
    SHORT,
    TALL,
    ;

    companion object {
        const val TALL_MIN_DP = 520

        fun ofHeight(heightDp: Int): WindowHeightClass =
            if (heightDp >= TALL_MIN_DP) TALL else SHORT

        fun ofHeight(heightDp: Dp): WindowHeightClass = ofHeight(heightDp.value.toInt())
    }
}

/**
 * Every measurement that changes between form factors, resolved once.
 *
 * Deliberately not a scale factor applied to one set of numbers: a tablet is
 * not a big phone, and the difference between the three layouts is which
 * things are on screen at all, not how large they are.  What *does* scale is
 * the touch target, because that is set by the finger, not by the window.
 */
data class AdaptiveMetrics(
    val sizeClass: WindowSizeClass,
    val heightClass: WindowHeightClass,
    val touchMode: Boolean,

    /**
     * The window's own width, kept alongside the class it resolved to.
     *
     * Three buckets decide *which* layout to use; they cannot decide whether a
     * specific row of controls fits, because that depends on how wide those
     * controls are. A shell that has to do that arithmetic - the desktop
     * toolbar, for one - needs the real number, and taking it from here is
     * better than re-reading the constraints somewhere else and risking the two
     * disagreeing. Zero when the caller only named a size class.
     */
    val widthDp: Dp,
    val heightDp: Dp,

    /** Outer margin around a screen's content. */
    val gutter: Dp,
    /** Gap between sibling cards and panels. */
    val gap: Dp,
    /** Gap between whole sections of a screen. */
    val sectionGap: Dp,
    /** Minimum height of anything you can press. */
    val minTarget: Dp,
    /** Corner radius for cards and sheets. */
    val corner: Dp,
    /** Width of the navigation rail, when there is one. */
    val railWidth: Dp,
    /** Width of a docked side panel, when there is one. */
    val panelWidth: Dp,
    /**
     * The widest a column of reading content is allowed to get.
     *
     * Text set across 1600px is unreadable however nicely it is spaced, so the
     * desktop layout gets more *panels*, not longer lines.
     */
    val readingWidth: Dp,
) {
    /** Shorthand for the question every adaptive layout asks first. */
    val isCompact: Boolean get() = sizeClass.isCompact

    /** Bottom navigation is a phone pattern; anything wider gets the rail. */
    val usesBottomNav: Boolean get() = sizeClass.isCompact

    val usesRail: Boolean get() = sizeClass.atLeastMedium

    /**
     * Whether the inspector is docked beside the canvas rather than opened as
     * a sheet over it.  A tablet has the room; a phone does not, and half a
     * canvas is worse than a canvas plus a sheet you can dismiss.
     */
    val usesDockedInspector: Boolean get() = sizeClass.atLeastMedium

    /** Both docks at once is an expanded-only luxury. */
    val usesSecondaryDock: Boolean get() = sizeClass.isExpanded

    /** Navigation labels are dropped before the icons are. */
    val showsNavLabels: Boolean get() = heightClass == WindowHeightClass.TALL

    /** Columns for a grid of equal cards. */
    fun gridColumns(compact: Int = 2, medium: Int = 3, expanded: Int = 4): Int = when (sizeClass) {
        WindowSizeClass.COMPACT -> compact
        WindowSizeClass.MEDIUM -> medium
        WindowSizeClass.EXPANDED -> expanded
    }

    companion object {
        /**
         * The tokens for a window of this size.
         *
         * [touchMode] widens the hit targets without changing the layout, so a
         * touchscreen laptop and a tablet get fingers-sized controls at the
         * same width where a mouse-driven window would get tighter ones.
         */
        fun of(
            sizeClass: WindowSizeClass,
            heightClass: WindowHeightClass = WindowHeightClass.TALL,
            touchMode: Boolean = sizeClass != WindowSizeClass.EXPANDED,
            widthDp: Dp = 0.dp,
            heightDp: Dp = 0.dp,
        ): AdaptiveMetrics {
            val target = if (touchMode) {
                if (sizeClass.isCompact) 48.dp else 52.dp
            } else {
                36.dp
            }
            return when (sizeClass) {
                WindowSizeClass.COMPACT -> AdaptiveMetrics(
                    sizeClass = sizeClass,
                    heightClass = heightClass,
                    touchMode = touchMode,
                    widthDp = widthDp,
                    heightDp = heightDp,
                    gutter = 16.dp,
                    gap = 10.dp,
                    sectionGap = 20.dp,
                    minTarget = target,
                    corner = 18.dp,
                    railWidth = 0.dp,
                    // No dock at this width, but a shell may still slide the
                    // panel in over the content, and a drawer 0dp wide is not
                    // a drawer.
                    panelWidth = 300.dp,
                    readingWidth = 560.dp,
                )

                WindowSizeClass.MEDIUM -> AdaptiveMetrics(
                    sizeClass = sizeClass,
                    heightClass = heightClass,
                    touchMode = touchMode,
                    widthDp = widthDp,
                    heightDp = heightDp,
                    gutter = 24.dp,
                    gap = 14.dp,
                    sectionGap = 26.dp,
                    minTarget = target,
                    corner = 20.dp,
                    railWidth = 96.dp,
                    panelWidth = 320.dp,
                    readingWidth = 680.dp,
                )

                WindowSizeClass.EXPANDED -> AdaptiveMetrics(
                    sizeClass = sizeClass,
                    heightClass = heightClass,
                    touchMode = touchMode,
                    widthDp = widthDp,
                    heightDp = heightDp,
                    gutter = 20.dp,
                    gap = 12.dp,
                    sectionGap = 24.dp,
                    minTarget = target,
                    corner = 12.dp,
                    railWidth = 80.dp,
                    panelWidth = 340.dp,
                    readingWidth = 760.dp,
                )
            }
        }

        fun of(widthDp: Dp, heightDp: Dp, touchMode: Boolean): AdaptiveMetrics = of(
            sizeClass = WindowSizeClass.ofWidth(widthDp),
            heightClass = WindowHeightClass.ofHeight(heightDp),
            touchMode = touchMode,
            widthDp = widthDp,
            heightDp = heightDp,
        )
    }
}

/**
 * The metrics for the window the caller is in.
 *
 * Defaulted to the desktop set so a component read outside an
 * [AdaptiveScope] still lays out sensibly rather than crashing.
 */
val LocalAdaptive = staticCompositionLocalOf { AdaptiveMetrics.of(WindowSizeClass.EXPANDED) }

@Composable
fun rememberAdaptiveMetrics(widthDp: Dp, heightDp: Dp, touchMode: Boolean): AdaptiveMetrics =
    AdaptiveMetrics.of(widthDp, heightDp, touchMode)
