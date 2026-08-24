package com.mcguidesigner.core

import com.mcguidesigner.core.image.ImageExport
import com.mcguidesigner.core.model.CanvasSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageExportTest {

    /** The vanilla chest screen, which is the size most of this gets used at. */
    private val chest = CanvasSpec(width = 176, height = 166)

    @Test
    fun `every offered size is a whole multiple of the canvas`() {
        // The point of the whole type: pixel art scaled by a fraction stops
        // looking like the thing that was drawn.
        ImageExport.optionsFor(chest).forEach { size ->
            assertEquals(0, size.width % chest.width, "${size.label} is not a whole multiple wide")
            assertEquals(0, size.height % chest.height, "${size.label} is not a whole multiple tall")
            assertEquals(chest.width * size.scale, size.width)
            assertEquals(chest.height * size.scale, size.height)
        }
    }

    @Test
    fun `a named height picks the nearest whole multiple`() {
        // 720 / 166 = 4.337, so 4x - and the honest output is 664px, not 720.
        assertEquals(4, ImageExport.scaleForHeight(166, 720))
        // 360 / 100 = 3.6, which rounds up rather than truncating to 3.
        assertEquals(4, ImageExport.scaleForHeight(100, 360))
        assertEquals(2, ImageExport.scaleForHeight(100, 240))
    }

    @Test
    fun `a scale is never zero, however small the request`() {
        // Asking for 144p from a 400px canvas is 0.36x. Zero would produce a
        // zero-byte image; one produces the original.
        assertEquals(1, ImageExport.scaleForHeight(400, 144))
        assertEquals(1, ImageExport.scaleForHeight(4000, 1))
    }

    @Test
    fun `a degenerate canvas does not divide by zero`() {
        assertEquals(1, ImageExport.scaleForHeight(0, 720))
    }

    @Test
    fun `the scale is capped so nobody asks for a gigapixel by accident`() {
        assertTrue(ImageExport.scaleForHeight(1, 100_000) <= ImageExport.MAX_SCALE)
        assertEquals(ImageExport.MAX_SCALE, ImageExport.sizeAt(chest, 9999).scale)
    }

    @Test
    fun `the same multiple is never offered twice`() {
        // Two named heights can land on one multiple; showing both would be a
        // menu claiming more choices than it has.
        val options = ImageExport.optionsFor(chest)
        assertEquals(options.map { it.scale }.distinct().size, options.size)
    }

    @Test
    fun `options run smallest to largest`() {
        val scales = ImageExport.optionsFor(chest).map { it.scale }
        assertEquals(scales.sorted(), scales)
    }

    @Test
    fun `a named height wins the label when it collides with a bare multiple`() {
        // "720p" says why you would choose it; "4×" does not.
        val four = ImageExport.optionsFor(chest).single { it.scale == 4 }
        assertEquals("720p", four.label)
    }

    @Test
    fun `the default is a usable size rather than the smallest`() {
        val default = ImageExport.defaultFor(chest)
        assertTrue(default.scale >= 2, "1x of a 176px screen is a thumbnail")
        assertTrue(default.scale <= 6)
    }

    @Test
    fun `dimensions read as the real output size`() {
        assertEquals("704 × 664", ImageExport.sizeAt(chest, 4).dimensions)
    }
}
