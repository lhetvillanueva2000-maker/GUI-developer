package com.mcguidesigner.styles.settings

import com.mcguidesigner.styles.theme.ChromeTheme
import com.mcguidesigner.styles.theme.MotionLevel
import com.mcguidesigner.styles.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppearanceSettingsTest {

    private fun stored(
        themeMode: String? = null,
        chromeTheme: String? = null,
        motion: String? = null,
        backdropEnabled: Boolean = true,
        legacyBackdropMotion: Boolean = true,
        profileName: String = "",
    ) = AppearanceSettings.fromStored(
        themeMode = themeMode,
        chromeTheme = chromeTheme,
        storedMotion = motion,
        backdropEnabled = backdropEnabled,
        legacyBackdropMotion = legacyBackdropMotion,
        profileName = profileName,
    )

    @Test
    fun `a store written before the system theme was removed reads as dark`() {
        // "SYSTEM" was a real value up to 1.6.0 and resolved to dark on
        // desktop; upgrading must not leave the app themeless.
        assertEquals(ThemeMode.DARK, stored(themeMode = "SYSTEM").theme)
    }

    @Test
    fun `an empty store gives the defaults`() {
        assertEquals(AppearanceSettings(), stored())
    }

    @Test
    fun `a stored motion level wins`() {
        assertEquals(MotionLevel.OFF, stored(motion = "OFF").motion)
        assertEquals(MotionLevel.REDUCED, stored(motion = "REDUCED").motion)
    }

    @Test
    fun `upgrading from before 1_6_0 keeps the choice that was made`() {
        // Pre-1.6.0 had no motion level, only a backdrop-drift boolean.
        // Somebody who turned the drift off was asking for less motion, and
        // resetting them to Full on upgrade would quietly undo that.
        assertEquals(
            MotionLevel.REDUCED,
            stored(motion = null, legacyBackdropMotion = false).motion,
            "drift-off must carry forward as reduced motion",
        )
        assertEquals(
            MotionLevel.FULL,
            stored(motion = null, legacyBackdropMotion = true).motion,
        )
    }

    @Test
    fun `a stored level beats the legacy flag they disagree with`() {
        // Once the new key exists it is the answer, even where the old flag,
        // written alongside it for downgrade safety, says otherwise.
        assertEquals(
            MotionLevel.OFF,
            stored(motion = "OFF", legacyBackdropMotion = true).motion,
        )
    }

    @Test
    fun `unrecognised names fall back rather than failing`() {
        val settings = stored(themeMode = "PLAID", chromeTheme = "NEON", motion = "LUDICROUS")
        assertEquals(ThemeMode.DARK, settings.theme)
        assertEquals(ChromeTheme.DEFAULT, settings.chromeTheme)
        assertEquals(MotionLevel.FULL, settings.motion)
    }

    @Test
    fun `the backdrop only moves when it is drawn and motion allows loops`() {
        assertTrue(AppearanceSettings(backdropEnabled = true, motion = MotionLevel.FULL).backdropMoves)
        assertFalse(
            AppearanceSettings(backdropEnabled = false, motion = MotionLevel.FULL).backdropMoves,
            "artwork that is not drawn cannot drift",
        )
        assertFalse(
            AppearanceSettings(backdropEnabled = true, motion = MotionLevel.REDUCED).backdropMoves,
            "reduced motion must stop everything that loops",
        )
        assertFalse(AppearanceSettings(backdropEnabled = true, motion = MotionLevel.OFF).backdropMoves)
    }

    @Test
    fun `a blank profile name is no name at all`() {
        assertNull(AppearanceSettings(profileName = "").greeting)
        assertNull(AppearanceSettings(profileName = "   ").greeting)
        assertEquals("Elijah", AppearanceSettings(profileName = "  Elijah  ").greeting)
    }
}
