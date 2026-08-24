package com.mcguidesigner.styles.export

import com.mcguidesigner.core.image.ImageExport
import com.mcguidesigner.core.model.CanvasSpec
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiProject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageFileNameTest {

    private fun project(name: String, width: Int = 176, height: Int = 166) = GuiProject(
        id = "test",
        name = name,
        edition = Edition.JAVA,
        canvas = CanvasSpec(width = width, height = height),
    )

    @Test
    fun `the file name carries the real output size`() {
        val subject = project("Custom Chest")
        assertEquals(
            "custom_chest_704x664.png",
            fileNameFor(subject, ImageExport.sizeAt(subject.canvas, 4)),
        )
    }

    @Test
    fun `a name that is mostly punctuation still produces a usable file name`() {
        // Project names are free text and end up in a file name on three
        // operating systems, two of which refuse most of what somebody might
        // type. Anything outside [a-z0-9_] has to be gone by here.
        val subject = project("  My Screen!! (v2) ")
        val name = fileNameFor(subject, ImageExport.sizeAt(subject.canvas, 1))
        assertTrue(
            Regex("[a-z0-9_]+\\.png").matches(name),
            "expected a file-system-safe name, got $name",
        )
    }

    @Test
    fun `every offered size is a whole multiple of the canvas`() {
        // The whole reason the list offers named heights rather than free text.
        val canvas = project("Any").canvas
        ImageExport.optionsFor(canvas).forEach { option ->
            assertEquals(canvas.width * option.scale, option.width, "width for ${option.label}")
            assertEquals(canvas.height * option.scale, option.height, "height for ${option.label}")
        }
    }
}
