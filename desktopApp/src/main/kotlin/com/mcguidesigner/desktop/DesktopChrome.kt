package com.mcguidesigner.desktop

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mcguidesigner.styles.layout.AdaptiveMetrics

/**
 * How much of the desktop chrome fits in a window of a given width.
 *
 * Separate from the shared [AdaptiveMetrics] because these numbers are
 * measurements of *this* toolbar and *these* docks. The shared metrics answer
 * "which of the three layouts is this?"; this answers "and does the align
 * cluster actually fit?", which no general breakpoint can know.
 *
 * Pure functions on purpose. The bug they exist to prevent - two docks open in
 * an 840dp window, leaving 240dp of canvas, and a toolbar quietly pushing the
 * view switcher off its right-hand edge - is invisible to every test that does
 * not look at a screen, right up until someone drags a window narrower.
 */
internal object DesktopChrome {

    /** The toolbox dock. Fixed, because its content is a fixed-width list. */
    val LEFT_DOCK_WIDTH = 320.dp

    /**
     * The least canvas worth keeping.
     *
     * Below this a dock has stopped being a help: the thing being designed no
     * longer fits beside the controls for designing it.
     */
    val MIN_CANVAS_WIDTH = 420.dp

    /** Guides and rulers need this much before they earn their place. */
    val TOOLBAR_TOGGLES_MIN_WIDTH = 900.dp

    /**
     * The header's two word-buttons - "Components" and "Import pack" - are
     * about 230dp between them and sit next to a title that can shrink.
     */
    val HEADER_BUTTONS_MIN_WIDTH = 1180.dp

    /**
     * Eight align buttons are roughly 290dp, on top of the 700dp of tools that
     * precede them and the 390dp of zoom and view controls pinned to the right
     * of the bar. Measured rather than guessed: at 1180dp only three of the
     * eight fit and the rest sat off the end of their scroll region, which
     * looks like a rendering fault rather than a deliberate cut.
     */
    val TOOLBAR_ALIGN_MIN_WIDTH = 1420.dp

    /**
     * What the two side docks do at this width.
     *
     * [rightAsDrawer] is the case worth naming: the inspector is still
     * reachable, it just slides over the canvas instead of sitting beside it.
     * Dropping it entirely would put half the editing controls out of reach at
     * exactly the width where someone is most short of room.
     */
    data class DockPlan(
        val left: Boolean,
        val rightDocked: Boolean,
        val rightAsDrawer: Boolean,
    )

    fun dockPlan(
        windowWidth: Dp,
        metrics: AdaptiveMetrics,
        wantLeft: Boolean,
        wantRight: Boolean,
    ): DockPlan {
        val rightDocked = wantRight && windowWidth - metrics.panelWidth >= MIN_CANVAS_WIDTH
        val left = wantLeft && metrics.sizeClass.isExpanded &&
            windowWidth - LEFT_DOCK_WIDTH - metrics.panelWidth >= MIN_CANVAS_WIDTH
        return DockPlan(
            left = left,
            rightDocked = rightDocked,
            rightAsDrawer = wantRight && !rightDocked,
        )
    }
}
