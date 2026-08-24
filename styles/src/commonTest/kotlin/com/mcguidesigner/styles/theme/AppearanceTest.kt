package com.mcguidesigner.styles.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Perceived lightness, near enough for "did this get darker or lighter". */
private fun Color.luma(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private val darkBase = ChromeColors(
    background = Color(0xFF121417),
    panel = Color(0xFF1B1E23),
    panelAlt = Color(0xFF23272E),
    border = Color(0xFF30353D),
    text = Color(0xFFE6E9EF),
    textMuted = Color(0xFF9AA3AF),
    gridLine = Color(0xFF262A31),
    gridLineMajor = Color(0xFF343A43),
    backdropScrim = Color(0x99000000),
)

private val lightBase = ChromeColors(
    background = Color(0xFFF4F5F7),
    panel = Color(0xFFFFFFFF),
    panelAlt = Color(0xFFEDEFF2),
    border = Color(0xFFD4D8DE),
    text = Color(0xFF1B1E23),
    textMuted = Color(0xFF5A626D),
    gridLine = Color(0xFFE2E5EA),
    gridLineMajor = Color(0xFFCED3DA),
    backdropScrim = Color(0x33FFFFFF),
)

class ChromeThemeTest {

    @Test
    fun `the default theme is the identity`() {
        assertEquals(darkBase, ChromeTheme.DEFAULT.apply(darkBase, dark = true))
        assertEquals(lightBase, ChromeTheme.DEFAULT.apply(lightBase, dark = false))
    }

    @Test
    fun `no theme may touch the text colours`() {
        // The guarantee that makes it safe to ship seven of these: however
        // badly a hue is chosen, the app stays readable.
        ChromeTheme.entries.forEach { theme ->
            val dark = theme.apply(darkBase, dark = true)
            assertEquals(darkBase.text, dark.text, "${theme.name} moved the dark text colour")
            assertEquals(darkBase.textMuted, dark.textMuted, "${theme.name} moved the dark muted text")

            val light = theme.apply(lightBase, dark = false)
            assertEquals(lightBase.text, light.text, "${theme.name} moved the light text colour")
            assertEquals(lightBase.textMuted, light.textMuted, "${theme.name} moved the light muted text")
        }
    }

    @Test
    fun `every theme other than the default actually recolours something`() {
        ChromeTheme.entries.filter { it != ChromeTheme.DEFAULT }.forEach { theme ->
            assertNotEquals(
                darkBase.background,
                theme.apply(darkBase, dark = true).background,
                "${theme.name} claims to be a theme but changes nothing",
            )
        }
    }

    @Test
    fun `a light theme stays light`() {
        // The failure this guards: lerping a near-white background most of the
        // way towards a dark navy produces a dark background, so picking
        // "Midnight" plus "Light" would silently turn the light theme off.
        ChromeTheme.entries.forEach { theme ->
            val light = theme.apply(lightBase, dark = false)
            assertTrue(
                light.background.luma() > 0.6f,
                "${theme.name} darkened the light background to ${light.background.luma()}",
            )
            assertTrue(
                light.panel.luma() > 0.6f,
                "${theme.name} darkened the light panel to ${light.panel.luma()}",
            )
        }
    }

    @Test
    fun `a dark theme stays dark`() {
        ChromeTheme.entries.forEach { theme ->
            val dark = theme.apply(darkBase, dark = true)
            assertTrue(
                dark.background.luma() < 0.32f,
                "${theme.name} lightened the dark background to ${dark.background.luma()}",
            )
        }
    }

    @Test
    fun `the scrim keeps its alpha so panels stay readable over the artwork`() {
        ChromeTheme.entries.forEach { theme ->
            assertEquals(
                darkBase.backdropScrim.alpha,
                theme.apply(darkBase, dark = true).backdropScrim.alpha,
                absoluteTolerance = 0.001f,
                message = "${theme.name} changed how much the backdrop is dimmed",
            )
        }
    }

    @Test
    fun `the themes are actually distinguishable from each other`() {
        // The defect this catches: lightening a saturated hue most of the way
        // to white leaves so little colour that Midnight, Nether and End all
        // came out as the same pale grey as plain Light. A theme picker where
        // six of seven options look identical is not a theme picker.
        listOf(true, false).forEach { dark ->
            val base = if (dark) darkBase else lightBase
            val backgrounds = ChromeTheme.entries.associateWith { it.apply(base, dark).background }

            ChromeTheme.entries.forEach { a ->
                ChromeTheme.entries.forEach { b ->
                    if (a.ordinal >= b.ordinal) return@forEach
                    val x = backgrounds.getValue(a)
                    val y = backgrounds.getValue(b)
                    val distance = kotlin.math.abs(x.red - y.red) +
                        kotlin.math.abs(x.green - y.green) +
                        kotlin.math.abs(x.blue - y.blue)
                    val mode = if (dark) "dark" else "light"
                    assertTrue(
                        distance > 0.02f,
                        "${a.name} and ${b.name} are the same colour in $mode mode ($distance)",
                    )
                }
            }
        }
    }

    @Test
    fun `a neutral hue stays neutral and a vivid one stays vivid`() {
        // Two failures in opposite directions, both real: normalising a
        // near-grey amplifies its slight cast, so Slate came out blue; not
        // normalising at all washed Midnight out to grey.
        fun chroma(c: Color): Float =
            maxOf(c.red, c.green, c.blue) - minOf(c.red, c.green, c.blue)

        val slate = ChromeTheme.SLATE.apply(lightBase, dark = false).background
        val midnight = ChromeTheme.MIDNIGHT.apply(lightBase, dark = false).background

        assertTrue(chroma(slate) < 0.04f, "Slate picked up a colour cast: ${chroma(slate)}")
        assertTrue(chroma(midnight) > chroma(slate), "Midnight must read bluer than Slate")
    }

    @Test
    fun `an unknown stored name falls back to the default`() {
        assertEquals(ChromeTheme.MIDNIGHT, ChromeTheme.fromName("midnight"))
        assertEquals(ChromeTheme.DEFAULT, ChromeTheme.fromName("HOT_PINK"))
        assertEquals(ChromeTheme.DEFAULT, ChromeTheme.fromName(null))
    }
}

class MotionLevelTest {

    @Test
    fun `full motion runs durations unchanged`() {
        assertEquals(420, MotionLevel.FULL.duration(420))
        assertTrue(MotionLevel.FULL.animates)
        assertTrue(MotionLevel.FULL.allowsLoops)
    }

    @Test
    fun `reduced keeps transitions but stops anything that loops`() {
        // The distinction that matters: a shorter infinite animation is still
        // an infinite animation, and the loops are what cost battery.
        assertTrue(MotionLevel.REDUCED.animates, "transitions must still play")
        assertTrue(!MotionLevel.REDUCED.allowsLoops, "nothing may loop at this level")
        assertTrue(MotionLevel.REDUCED.duration(420) < 420)
        assertTrue(MotionLevel.REDUCED.duration(420) > 0)
    }

    @Test
    fun `off animates nothing`() {
        assertEquals(0, MotionLevel.OFF.duration(420))
        assertTrue(!MotionLevel.OFF.animates)
        assertTrue(!MotionLevel.OFF.allowsLoops)
    }

    @Test
    fun `a scaled duration never rounds down to zero while motion is on`() {
        // tween(0) is a division by zero in some animation APIs, so a level
        // that says it animates must never hand one out.
        MotionLevel.entries.filter { it.animates }.forEach { level ->
            assertTrue(
                level.duration(1) >= 1,
                "${level.name} produced a zero-length spec while claiming to animate",
            )
        }
    }

    @Test
    fun `an unknown stored name falls back to full`() {
        assertEquals(MotionLevel.REDUCED, MotionLevel.fromName("reduced"))
        assertEquals(MotionLevel.FULL, MotionLevel.fromName("TURBO"))
        assertEquals(MotionLevel.FULL, MotionLevel.fromName(null))
    }
}
