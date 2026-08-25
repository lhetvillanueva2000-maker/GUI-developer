package com.mcguidesigner.styles.theme

import androidx.compose.ui.graphics.Color
import com.mcguidesigner.styles.other.OtherUiPalette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every colour pair a skin actually draws on top of itself has to be readable.
 *
 * This exists because of one bug that shipped. The home screen's edition card
 * filled its button with `palette.control` and wrote the label in
 * `palette.textOnAccent`. In both Minecraft skins that happens to work - their
 * controls are dark stone and the text is white - so nothing in the codebase
 * noticed that the pairing was a coincidence rather than a contract. The moment
 * a third skin arrived with pale controls, the card's only button had white
 * text on a near-white fill: not dim, not low contrast, genuinely invisible.
 *
 * Reviewing a palette by eye cannot catch that, because each colour is fine and
 * it is the *pair* that is wrong. So the pairs are asserted.
 */
class ContrastTest {

    /** WCAG 2.1 contrast ratio, 1.0 (identical) to 21.0 (black on white). */
    private fun contrast(a: Color, b: Color): Float {
        fun luminance(c: Color): Float {
            fun channel(v: Float) = if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
            return 0.2126f * channel(c.red) + 0.7152f * channel(c.green) + 0.0722f * channel(c.blue)
        }
        val x = luminance(a)
        val y = luminance(b)
        return (max(x, y) + 0.05f) / (min(x, y) + 0.05f)
    }

    private val skins get() = SkinRegistry.all

    /**
     * Skins that do not drop-shadow their text.
     *
     * The two Minecraft palettes are measurements of the game's own art, and
     * the game's own art is not WCAG-compliant - vanilla button labels are
     * white on mid-grey stone at about 3.4:1. The game gets away with it by
     * drawing every string twice, once in black one pixel down, and that
     * shadow is worth roughly a doubling of effective contrast that no formula
     * here models. Holding those two to an accessibility bar would mean
     * changing art whose entire purpose is to match a screenshot.
     *
     * A skin with no shadow has no such excuse, and is held to the real bar.
     */
    private val unshadowed get() = skins.filter { it.palette.textShadow.alpha == 0f }

    @Test
    fun `no skin can draw a label that vanishes into its own button`() {
        // The floor, for every skin including the two imitating a game. 1.6:1
        // is not readable - it is the line below which text stops being text.
        // The bug this guards against measured 1.0: white on white.
        val failures = skins.flatMap { skin ->
            val p = skin.palette
            listOf("rest" to p.ctaFill, "hover" to p.ctaFillHover, "pressed" to p.ctaFillPressed)
                .mapNotNull { (state, fill) ->
                    val ratio = contrast(fill, p.ctaText)
                    if (ratio >= 1.6f) null else "${skin.displayName}: cta $state ${ratio.round()}:1"
                }
        }
        assertTrue(failures.isEmpty(), "Invisible button label:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `the filled call-to-action meets the readable bar where there is no shadow`() {
        // 4.5 is the WCAG AA threshold for body text. A button label is larger
        // and bolder than body text, so this is stricter than it strictly has
        // to be - the right side to err on for the one button on the launch
        // screen. All three states, because hover is the state a person is
        // looking at while deciding whether they pressed the right thing.
        val failures = unshadowed.flatMap { skin ->
            val p = skin.palette
            listOf("rest" to p.ctaFill, "hover" to p.ctaFillHover, "pressed" to p.ctaFillPressed)
                .mapNotNull { (state, fill) ->
                    val ratio = contrast(fill, p.ctaText)
                    if (ratio >= 4.5f) null else "${skin.displayName}: cta $state ${ratio.round()}:1"
                }
        }
        assertTrue(failures.isEmpty(), "Unreadable primary button:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `body text can be read on every surface an unshadowed skin puts it on`() {
        val failures = unshadowed.flatMap { skin ->
            val p = skin.palette
            listOf(
                "surface" to p.surface,
                "surfaceRaised" to p.surfaceRaised,
                "surfaceSunken" to p.surfaceSunken,
                "control" to p.control,
            ).mapNotNull { (name, background) ->
                val ratio = contrast(p.textPrimary, background)
                if (ratio >= 4.5f) null else "${skin.displayName}: textPrimary on $name ${ratio.round()}:1"
            }
        }
        assertTrue(failures.isEmpty(), "Unreadable text:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `secondary text is quieter than primary without disappearing`() {
        val failures = unshadowed.mapNotNull { skin ->
            val p = skin.palette
            val ratio = contrast(p.textSecondary, p.surface)
            // 3.0 rather than 4.5: secondary text is meant to recede, and
            // holding it to the body-text bar would just make it primary text
            // by another name. It still has to be legible.
            if (ratio >= 3.0f) null else "${skin.displayName}: textSecondary ${ratio.round()}:1"
        }
        assertTrue(failures.isEmpty(), "Unreadable secondary text:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `the wordmark's slot never swallows the half of the name inside it`() {
        // The app's own name is "UI" on the panel and "LABS" inside a slot
        // drawn in the active skin's widget colours. The slot's fill and the
        // chrome's text belong to two different colour systems and nothing
        // makes them contrast: on a skin with a pale slot they landed within a
        // few percent of each other and the second half of the name vanished.
        //
        // Asserted against readableTextOn rather than against chromeText,
        // because that is now what the top bar uses - this test fails if
        // anybody puts it back.
        val failures = skins.flatMap { skin ->
            listOf(true, false).mapNotNull { dark ->
                val p = skin.paletteFor(dark)
                val ratio = contrast(readableTextOn(p.slot), p.slot)
                // Same split as the rest of this file: a skin that draws its
                // text with a shadow gets the lower bar, because the shadow is
                // doing work the ratio cannot see. Java's slot is vanilla
                // mid-grey and sits at 3.4:1 whichever ink is chosen - the top
                // bar shadows the letters for exactly that reason.
                val bar = if (p.textShadow.alpha > 0f) 3.0f else 4.5f
                if (ratio >= bar) null else {
                    "${skin.displayName} (${if (dark) "dark" else "light"}): slot ${ratio.round()}:1"
                }
            }
        }
        assertTrue(failures.isEmpty(), "Unreadable wordmark:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `at least one skin is actually being held to the strict bar`() {
        // Without this, deleting a token or flipping a shadow flag would empty
        // the strict tests out and turn three green checks into three no-ops.
        assertTrue(unshadowed.isNotEmpty(), "No unshadowed skin left for the strict contrast tests to cover")
    }

    @Test
    fun `a control is distinguishable from the surface it sits on`() {
        // Not a text ratio - this is about whether the *shape* of a control can
        // be seen at all. A 1.12 step is roughly where an edge stops being
        // visible on a phone screen at arm's length in daylight, which is the
        // condition this app is used in.
        val failures = skins.mapNotNull { skin ->
            val p = skin.palette
            val step = contrast(p.control, p.surface)
            val outline = contrast(p.outline, p.surface)
            when {
                step >= 1.12f || outline >= 1.6f -> null
                else -> "${skin.displayName}: control ${step.round()}:1, outline ${outline.round()}:1"
            }
        }
        assertTrue(failures.isEmpty(), "Invisible control:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `chrome text can be read in both themes`() {
        val failures = skins.flatMap { skin ->
            listOf(true, false).flatMap { dark ->
                val p = skin.paletteFor(dark)
                val where = if (dark) "dark" else "light"
                listOf(
                    "chromeText on background" to contrast(p.chromeText, p.chromeBackground),
                    "chromeText on panel" to contrast(p.chromeText, p.chromePanel),
                    "chromeTextMuted on panel" to contrast(p.chromeTextMuted, p.chromePanel),
                ).mapNotNull { (what, ratio) ->
                    if (ratio >= 4.5f) null else "${skin.displayName} ($where): $what ${ratio.round()}:1"
                }
            }
        }
        assertTrue(failures.isEmpty(), "Unreadable chrome:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `both Other UIs token sets are readable, not just the light one`() {
        // The light set is the one wired into SkinPalette, so it is the only
        // one the tests above ever see. The dark set is chosen at draw time
        // from the canvas backdrop and would otherwise be entirely unchecked -
        // which is how "we added a dark mode" and "the dark mode is unusable"
        // end up being the same commit.
        val sets = listOf("light" to OtherUiPalette.Light, "dark" to OtherUiPalette.Dark)
        val failures = sets.flatMap { (name, t) ->
            buildList {
                listOf(
                    "textPrimary on surface" to contrast(t.textPrimary, t.surface),
                    "textPrimary on page" to contrast(t.textPrimary, t.page),
                    "textPrimary on surfaceRaised" to contrast(t.textPrimary, t.surfaceRaised),
                    "textPrimary on surfaceSunken" to contrast(t.textPrimary, t.surfaceSunken),
                    "textPrimary on control" to contrast(t.textPrimary, t.control),
                ).forEach { (what, ratio) -> if (ratio < 4.5f) add("$name: $what ${ratio.round()}:1") }

                listOf(
                    "textSecondary on surface" to contrast(t.textSecondary, t.surface),
                    "textDisabled on surface" to contrast(t.textDisabled, t.surface),
                ).forEach { (what, ratio) -> if (ratio < 2.6f) add("$name: $what ${ratio.round()}:1") }

                // White on the accent, in every set: checkboxes, switch knobs
                // and the filled button all rely on it.
                val onAccent = contrast(OtherUiPalette.OnAccent, OtherUiPalette.Accent)
                if (onAccent < 4.5f) add("$name: onAccent ${onAccent.round()}:1")

                // And the accent has to be visible as a *shape* on the surface
                // it is drawn on, or a selected tab looks like an unselected one.
                val accentOnSurface = contrast(OtherUiPalette.Accent, t.surface)
                if (accentOnSurface < 2.4f) add("$name: accent on surface ${accentOnSurface.round()}:1")

                // Borders have to be findable. This is the specific thing the
                // first version of this palette got wrong: #DDE1E6 on #FFFFFF
                // is 1.17:1, an edge nobody can see.
                val divider = contrast(t.divider, t.surface)
                if (divider < 1.25f) add("$name: divider on surface ${divider.round()}:1")
                val outline = contrast(t.controlOutline, t.surface)
                if (outline < 1.6f) add("$name: controlOutline on surface ${outline.round()}:1")
            }
        }
        assertTrue(failures.isEmpty(), "Other UIs tokens:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `readableTextOn picks the side that can actually be seen`() {
        assertEquals(Color.White, readableTextOn(Color(0xFF11181C)))
        assertEquals(Color.White, readableTextOn(Color(0xFF2F6BE0)))
        // The case that started all of this: a white button.
        assertTrue(readableTextOn(Color.White) != Color.White)
        assertTrue(readableTextOn(Color(0xFFF1F3F6)) != Color.White)
    }

    private fun Float.round(): String {
        val hundredths = (this * 100f).toInt()
        return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
    }
}
