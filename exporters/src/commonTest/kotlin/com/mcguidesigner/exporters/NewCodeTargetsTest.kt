package com.mcguidesigner.exporters

import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.model.Edition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The bug these exist for was silent and total: a string template written as
 * `${'$'}{rect.x}` produces the *literal text* `${rect.x}`, so every generator
 * emitted its own source code instead of the design's numbers, compiled
 * cleanly, and would only have been noticed by someone opening the export.
 */
class NewCodeTargetsTest {

    private fun sample() = EditorController.newProject(Edition.JAVA, "Chest Screen")

    private val newTargets = listOf(
        CodeTarget.REACT_JSX,
        CodeTarget.SWIFTUI,
        CodeTarget.FLUTTER,
        CodeTarget.ANDROID_XML,
    )

    @Test
    fun `no generator emits an uninterpolated template`() {
        CodeTarget.entries.forEach { target ->
            if (!target.appliesTo(Edition.JAVA)) return@forEach
            val source = CodeGenerator.generate(sample(), target).source
            assertFalse(
                Regex("""\$\{[A-Za-z_]""").containsMatchIn(source),
                "${target.name} emitted an uninterpolated \${...} template",
            )
        }
    }

    @Test
    fun `every new target produces something with the canvas size in it`() {
        val project = sample()
        newTargets.forEach { target ->
            val generated = CodeGenerator.generate(project, target)
            assertTrue(generated.source.length > 120, "${target.name} produced almost nothing")
            assertTrue(
                generated.source.contains(project.canvas.width.toString()),
                "${target.name} never mentions the canvas width",
            )
        }
    }

    @Test
    fun `file names are sensible for each language`() {
        val project = sample()
        assertEquals("ChestScreen.jsx", CodeGenerator.generate(project, CodeTarget.REACT_JSX).fileName)
        assertEquals("ChestScreenView.swift", CodeGenerator.generate(project, CodeTarget.SWIFTUI).fileName)
        // Dart and Android resource names are snake_case by convention, and a
        // hyphen is a syntax error in both.
        assertEquals("chest_screen.dart", CodeGenerator.generate(project, CodeTarget.FLUTTER).fileName)
        assertEquals("chest_screen.xml", CodeGenerator.generate(project, CodeTarget.ANDROID_XML).fileName)
    }

    @Test
    fun `each target announces itself in its own comment syntax`() {
        val project = sample()
        assertTrue(CodeGenerator.generate(project, CodeTarget.REACT_JSX).source.startsWith("//"))
        assertTrue(CodeGenerator.generate(project, CodeTarget.SWIFTUI).source.contains("import SwiftUI"))
        assertTrue(CodeGenerator.generate(project, CodeTarget.FLUTTER).source.contains("package:flutter"))
        assertTrue(CodeGenerator.generate(project, CodeTarget.ANDROID_XML).source.startsWith("<?xml"))
    }

    @Test
    fun `the XML export is well formed enough to have balanced tags`() {
        val xml = CodeGenerator.generate(sample(), CodeTarget.ANDROID_XML).source
        assertEquals(1, Regex("<FrameLayout").findAll(xml).count())
        assertEquals(1, Regex("</FrameLayout>").findAll(xml).count())
        // Every TextView is self-closing, so there must be no closing tags.
        assertEquals(0, Regex("</TextView>").findAll(xml).count())
    }

    @Test
    fun `generated code is stable across runs`() {
        // Two runs of the same project must be byte-identical, or the
        // templates drift check in CI can never pass.
        val project = sample()
        newTargets.forEach { target ->
            assertEquals(
                CodeGenerator.generate(project, target).source,
                CodeGenerator.generate(project, target).source,
                "${target.name} is not reproducible",
            )
        }
    }
}
