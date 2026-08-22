package com.mcguidesigner.desktop

import androidx.compose.ui.unit.dp
import com.mcguidesigner.styles.layout.AdaptiveMetrics
import com.mcguidesigner.styles.layout.WindowSizeClass
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the desktop shell does as its window narrows.
 *
 * This file exists because of a bug that shipped past every other test: at
 * 900dp the window is "expanded", both docks stayed open, and the canvas -
 * the entire point of the application - was left 240dp wide. Nothing failed,
 * nothing threw, and the only way to find it was to look.
 */
class DesktopChromeTest {

    private fun metricsAt(width: Int) =
        AdaptiveMetrics.of(width.dp, 900.dp, touchMode = false)

    private fun planAt(width: Int, wantLeft: Boolean = true, wantRight: Boolean = true) =
        DesktopChrome.dockPlan(width.dp, metricsAt(width), wantLeft, wantRight)

    // -- The canvas is never squeezed out ------------------------------------

    @Test
    fun `the canvas always keeps a workable width`() {
        for (width in 320..2400 step 4) {
            val metrics = metricsAt(width)
            val plan = planAt(width)
            var canvas = width.dp
            if (plan.left) canvas -= DesktopChrome.LEFT_DOCK_WIDTH
            if (plan.rightDocked) canvas -= metrics.panelWidth
            assertTrue(
                canvas >= DesktopChrome.MIN_CANVAS_WIDTH || !plan.left && !plan.rightDocked,
                "at ${width}dp the docks left only $canvas of canvas",
            )
        }
    }

    @Test
    fun `a 900dp window drops the left dock rather than shrink the canvas`() {
        // The exact case that shipped broken: expanded by class, far too
        // narrow for 660dp of docks.
        val plan = planAt(900)
        assertFalse(plan.left, "two docks in a 900dp window leave 240dp of canvas")
        assertTrue(plan.rightDocked, "the inspector still fits and should stay")
    }

    @Test
    fun `a full-size window keeps everything the user asked for`() {
        val plan = planAt(1600)
        assertTrue(plan.left)
        assertTrue(plan.rightDocked)
        assertFalse(plan.rightAsDrawer)
    }

    // -- The inspector is always reachable -----------------------------------

    @Test
    fun `the inspector never simply disappears`() {
        // Docked or over the canvas, but never gone: it holds half the
        // editing controls, and "make your window bigger" is not a way to
        // reach a control.
        for (width in 320..2400 step 4) {
            val plan = planAt(width)
            assertTrue(
                plan.rightDocked || plan.rightAsDrawer,
                "at ${width}dp the inspector was unreachable",
            )
            assertFalse(
                plan.rightDocked && plan.rightAsDrawer,
                "at ${width}dp the inspector was docked and drawn as a drawer at once",
            )
        }
    }

    @Test
    fun `a narrow window slides the inspector over the canvas`() {
        val plan = planAt(560)
        assertFalse(plan.rightDocked)
        assertTrue(plan.rightAsDrawer)
    }

    @Test
    fun `closing a dock closes it at every width`() {
        listOf(400, 700, 900, 1280, 1920).forEach { width ->
            val plan = planAt(width, wantLeft = false, wantRight = false)
            assertFalse(plan.left, "at ${width}dp")
            assertFalse(plan.rightDocked, "at ${width}dp")
            assertFalse(plan.rightAsDrawer, "at ${width}dp")
        }
    }

    // -- Widening never takes something away ---------------------------------

    @Test
    fun `docks only ever appear as the window grows`() {
        var sawLeft = false
        var sawDocked = false
        for (width in 320..2400 step 4) {
            val plan = planAt(width)
            if (plan.left) sawLeft = true else assertFalse(sawLeft, "the left dock vanished again at ${width}dp")
            if (plan.rightDocked) {
                sawDocked = true
            } else {
                assertFalse(sawDocked, "the inspector undocked again at ${width}dp")
            }
        }
        assertTrue(sawLeft && sawDocked, "neither dock ever appeared")
    }

    // -- Toolbar thresholds --------------------------------------------------

    @Test
    fun `the toolbar sheds its widest group before its narrowest`() {
        // Widest block goes first, narrowest last, so narrowing the window
        // gives things up in order of what they cost.
        assertTrue(
            DesktopChrome.TOOLBAR_ALIGN_MIN_WIDTH > DesktopChrome.HEADER_BUTTONS_MIN_WIDTH,
            "the align cluster is wider than the two header buttons",
        )
        assertTrue(
            DesktopChrome.HEADER_BUTTONS_MIN_WIDTH > DesktopChrome.TOOLBAR_TOGGLES_MIN_WIDTH,
            "two word-buttons cost more than two toggles",
        )
    }

    @Test
    fun `every toolbar threshold is above the width it is meant to protect`() {
        // A threshold at or below the compact breakpoint would never fire.
        assertTrue(DesktopChrome.TOOLBAR_TOGGLES_MIN_WIDTH.value > WindowSizeClass.MEDIUM_MIN_DP)
        assertTrue(DesktopChrome.MIN_CANVAS_WIDTH < DesktopChrome.TOOLBAR_TOGGLES_MIN_WIDTH)
    }
}
