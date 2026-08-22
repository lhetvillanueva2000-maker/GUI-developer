package com.mcguidesigner.styles.layout

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which layout a given window gets.
 *
 * These are the decisions that used to be spelled `maxWidth > 600.dp` at each
 * call site, which is exactly how a rail and a bottom bar end up on screen
 * together at some width nobody tested.  Pulling them into one function makes
 * them assertable, and the assertions below are the contract the three shells
 * are written against.
 */
class AdaptiveTest {

    // -- Breakpoints ---------------------------------------------------------

    @Test
    fun `real devices land in the class they should`() {
        // Widths in dp, as Android and Compose Desktop report them.
        val cases = mapOf(
            360 to WindowSizeClass.COMPACT, // a phone, portrait
            412 to WindowSizeClass.COMPACT, // a large phone, portrait
            599 to WindowSizeClass.COMPACT, // the last compact width
            600 to WindowSizeClass.MEDIUM, // a small tablet, portrait
            740 to WindowSizeClass.MEDIUM, // a phone in landscape
            800 to WindowSizeClass.MEDIUM, // a 10" tablet, portrait
            839 to WindowSizeClass.MEDIUM, // the last medium width
            840 to WindowSizeClass.EXPANDED,
            1280 to WindowSizeClass.EXPANDED, // a tablet in landscape
            1920 to WindowSizeClass.EXPANDED, // a desktop window
        )
        cases.forEach { (width, expected) ->
            assertEquals(expected, WindowSizeClass.ofWidth(width), "at ${width}dp")
        }
    }

    @Test
    fun `the classes tile the whole number line with no gap and no overlap`() {
        // Every width from zero up gets exactly one class, and the class only
        // ever grows as the window does.
        var previous = WindowSizeClass.ofWidth(0)
        for (width in 0..2400) {
            val current = WindowSizeClass.ofWidth(width)
            assertTrue(
                current.ordinal >= previous.ordinal,
                "widening from ${width - 1}dp to ${width}dp went backwards: $previous -> $current",
            )
            previous = current
        }
        assertEquals(WindowSizeClass.EXPANDED, previous)
    }

    @Test
    fun `a negative or zero width is still compact rather than a crash`() {
        assertEquals(WindowSizeClass.COMPACT, WindowSizeClass.ofWidth(0))
        assertEquals(WindowSizeClass.COMPACT, WindowSizeClass.ofWidth(-100))
    }

    // -- What each class actually shows --------------------------------------

    @Test
    fun `a phone gets the bottom bar and a tablet gets the rail, never both`() {
        WindowSizeClass.entries.forEach { sizeClass ->
            val metrics = AdaptiveMetrics.of(sizeClass)
            assertFalse(
                metrics.usesBottomNav && metrics.usesRail,
                "$sizeClass asked for bottom navigation and a rail at the same time",
            )
            assertTrue(
                metrics.usesBottomNav || metrics.usesRail,
                "$sizeClass has no navigation at all",
            )
        }
    }

    @Test
    fun `only a phone opens the inspector as a sheet`() {
        assertFalse(AdaptiveMetrics.of(WindowSizeClass.COMPACT).usesDockedInspector)
        assertTrue(AdaptiveMetrics.of(WindowSizeClass.MEDIUM).usesDockedInspector)
        assertTrue(AdaptiveMetrics.of(WindowSizeClass.EXPANDED).usesDockedInspector)
    }

    @Test
    fun `both docks at once are an expanded-only luxury`() {
        assertFalse(AdaptiveMetrics.of(WindowSizeClass.COMPACT).usesSecondaryDock)
        assertFalse(AdaptiveMetrics.of(WindowSizeClass.MEDIUM).usesSecondaryDock)
        assertTrue(AdaptiveMetrics.of(WindowSizeClass.EXPANDED).usesSecondaryDock)
    }

    @Test
    fun `a short window drops its navigation labels`() {
        // A phone in landscape: wide enough for a rail, far too short for a
        // rail with words on it.
        val short = AdaptiveMetrics.of(740.dp, 360.dp, touchMode = true)
        assertEquals(WindowSizeClass.MEDIUM, short.sizeClass)
        assertFalse(short.showsNavLabels, "a 360dp-tall window has no room for labels")

        val tall = AdaptiveMetrics.of(740.dp, 1024.dp, touchMode = true)
        assertTrue(tall.showsNavLabels)
    }

    // -- Metrics -------------------------------------------------------------

    @Test
    fun `touch targets are set by the finger, not by the window`() {
        // The point of the touchMode flag: a touchscreen laptop at desktop
        // width still needs targets a finger can hit.
        val mouse = AdaptiveMetrics.of(WindowSizeClass.EXPANDED, touchMode = false)
        val finger = AdaptiveMetrics.of(WindowSizeClass.EXPANDED, touchMode = true)
        assertTrue(
            finger.minTarget > mouse.minTarget,
            "touch mode did not grow the target: ${finger.minTarget} vs ${mouse.minTarget}",
        )
        assertTrue(finger.minTarget >= 48.dp, "below the accessible minimum: ${finger.minTarget}")
    }

    @Test
    fun `every touch layout clears the accessible minimum`() {
        WindowSizeClass.entries.forEach { sizeClass ->
            val metrics = AdaptiveMetrics.of(sizeClass, touchMode = true)
            assertTrue(
                metrics.minTarget >= 48.dp,
                "$sizeClass touch target is ${metrics.minTarget}, under the 48dp minimum",
            )
        }
    }

    @Test
    fun `reading columns never grow past a readable line`() {
        // More space buys more panels, not longer lines of text.
        WindowSizeClass.entries.forEach { sizeClass ->
            val metrics = AdaptiveMetrics.of(sizeClass)
            assertTrue(
                metrics.readingWidth <= 800.dp,
                "$sizeClass would set text across ${metrics.readingWidth}",
            )
        }
    }

    @Test
    fun `a class that draws a rail gives it a width`() {
        WindowSizeClass.entries.forEach { sizeClass ->
            val metrics = AdaptiveMetrics.of(sizeClass)
            if (metrics.usesRail) {
                assertTrue(metrics.railWidth > 0.dp, "$sizeClass wants a rail 0dp wide")
            }
            if (metrics.usesDockedInspector) {
                assertTrue(metrics.panelWidth > 0.dp, "$sizeClass wants a panel 0dp wide")
            }
        }
    }

    @Test
    fun `grids get denser as the window grows`() {
        val columns = WindowSizeClass.entries.map { AdaptiveMetrics.of(it).gridColumns() }
        assertEquals(columns.sorted(), columns, "column counts are not monotonic: $columns")
        assertTrue(columns.first() >= 1)
    }
}
