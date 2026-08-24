package com.mcguidesigner.styles.layout

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The bug these exist for: a large phone in landscape is around 900dp wide,
 * which the width alone calls EXPANDED, and the phone would then be handed a
 * desktop layout - two docks open - on a screen four inches tall.
 */
class DeviceClassTest {

    @Test
    fun `a phone stays compact however it is turned`() {
        // A 6.7" phone: ~412dp portrait, ~915dp landscape.
        assertEquals(
            WindowSizeClass.COMPACT,
            WindowSizeClass.resolve(412.dp, DeviceClass.HANDSET),
            "portrait phone",
        )
        assertEquals(
            WindowSizeClass.COMPACT,
            WindowSizeClass.resolve(915.dp, DeviceClass.HANDSET),
            "the same phone on its side is still a phone",
        )
    }

    @Test
    fun `a tablet changes layout with its orientation`() {
        // A 10" tablet: ~800dp portrait, ~1280dp landscape.
        assertEquals(WindowSizeClass.MEDIUM, WindowSizeClass.resolve(800.dp, DeviceClass.TABLET))
        assertEquals(WindowSizeClass.EXPANDED, WindowSizeClass.resolve(1280.dp, DeviceClass.TABLET))
    }

    @Test
    fun `a narrow desktop window gets the compact layout`() {
        // Desktop windows are resizable, so unlike a phone the width really is
        // the whole story there.
        assertEquals(WindowSizeClass.COMPACT, WindowSizeClass.resolve(480.dp, DeviceClass.DESKTOP))
        assertEquals(WindowSizeClass.EXPANDED, WindowSizeClass.resolve(1600.dp, DeviceClass.DESKTOP))
    }

    @Test
    fun `the tablet threshold is the platform's own`() {
        assertEquals(DeviceClass.HANDSET, DeviceClass.ofSmallestWidth(599))
        assertEquals(DeviceClass.TABLET, DeviceClass.ofSmallestWidth(600))
        assertEquals(DeviceClass.TABLET, DeviceClass.ofSmallestWidth(800))
    }

    @Test
    fun `metrics built for a handset never claim a rail or a dock`() {
        val landscapePhone = AdaptiveMetrics.of(
            widthDp = 915.dp,
            heightDp = 412.dp,
            touchMode = true,
            device = DeviceClass.HANDSET,
        )
        assertEquals(WindowSizeClass.COMPACT, landscapePhone.sizeClass)
        assertEquals(false, landscapePhone.usesRail, "a phone must not grow a navigation rail")
        assertEquals(false, landscapePhone.usesDockedInspector)
        assertEquals(false, landscapePhone.usesSecondaryDock)
    }

    @Test
    fun `only a handset relies on the system back gesture`() {
        val phone = AdaptiveMetrics.of(WindowSizeClass.COMPACT, device = DeviceClass.HANDSET)
        val tablet = AdaptiveMetrics.of(WindowSizeClass.MEDIUM, device = DeviceClass.TABLET)
        val desktop = AdaptiveMetrics.of(WindowSizeClass.EXPANDED, device = DeviceClass.DESKTOP)

        assertEquals(false, phone.showsBackControl)
        assertEquals(true, tablet.showsBackControl)
        assertEquals(true, desktop.showsBackControl)
    }
}
