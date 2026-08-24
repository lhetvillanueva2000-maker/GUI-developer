package com.mcguidesigner.exporters

import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.model.CanvasSpec
import com.mcguidesigner.core.model.ColorValue
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.StringValue
import com.mcguidesigner.core.model.string
import com.mcguidesigner.core.model.walkAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImportTest {

    private val sample = GuiProject(
        id = "proj_test",
        name = "Custom Chest",
        edition = Edition.BEDROCK,
        canvas = CanvasSpec(width = 176, height = 166),
        elements = listOf(
            GuiElement(
                id = "el_panel01",
                type = ElementCatalog.PANEL_CHEST,
                name = "Backdrop",
                bounds = IntRect(0, 0, 176, 90),
                props = mapOf("background" to ColorValue(0xFF3C3C3CL)),
            ),
            GuiElement(
                id = "el_label02",
                type = ElementCatalog.TEXT_LABEL,
                name = "Title",
                bounds = IntRect(8, 6, 100, 10),
                props = mapOf("text" to StringValue("Storage")),
            ),
            GuiElement(
                id = "el_button3",
                type = ElementCatalog.BUTTON_NORMAL,
                name = "Close",
                bounds = IntRect(120, 140, 48, 20),
                props = mapOf("label" to StringValue("Close")),
            ),
        ),
    )

    // -- Detection ---------------------------------------------------------

    @Test
    fun `each format is recognised from its content, not its name`() {
        // All three of these can arrive as ".json", and two of them do.
        val project = CodeGenerator.generate(sample, CodeTarget.PROJECT_JSON).source
        val bedrock = CodeGenerator.generate(sample, CodeTarget.BEDROCK_JSON).source
        val html = CodeGenerator.generate(sample, CodeTarget.HTML_CSS).source
        val svg = CodeGenerator.generate(sample, CodeTarget.SVG).source

        assertEquals(ImportFormat.PROJECT, DesignImporter.detect("anything.json", project))
        assertEquals(ImportFormat.BEDROCK_JSON_UI, DesignImporter.detect("anything.json", bedrock))
        assertEquals(ImportFormat.HTML, DesignImporter.detect("anything.txt", html))
        assertEquals(ImportFormat.SVG, DesignImporter.detect("anything.txt", svg))
    }

    @Test
    fun `something that is not a design is refused rather than half read`() {
        val outcome = DesignImporter.import("notes.txt", "just some words")
        assertNull(outcome.project)
        assertTrue(outcome.notes.isNotEmpty(), "a refusal has to say why")
    }

    // -- Bedrock JSON UI ---------------------------------------------------

    @Test
    fun `a Bedrock screen this app wrote comes back with its own element types`() {
        val json = CodeGenerator.generate(sample, CodeTarget.BEDROCK_JSON).source
        val outcome = DesignImporter.import("storage.json", json)

        val project = assertNotNull(outcome.project, "expected a project, notes: ${outcome.notes}")
        assertEquals(ImportFormat.BEDROCK_JSON_UI, outcome.format)
        assertEquals(176, project.canvas.width)
        assertEquals(166, project.canvas.height)

        val types = project.elements.walkAll().map { it.type }.toSet()
        assertEquals(
            setOf(ElementCatalog.PANEL_CHEST, ElementCatalog.TEXT_LABEL, ElementCatalog.BUTTON_NORMAL),
            types,
            "\$designer_type should bring every element back as exactly what it was",
        )
    }

    @Test
    fun `positions and sizes survive the round trip`() {
        val json = CodeGenerator.generate(sample, CodeTarget.BEDROCK_JSON).source
        val project = assertNotNull(DesignImporter.import("storage.json", json).project)

        val original = sample.elements.associateBy { it.bounds }
        project.elements.walkAll().forEach { imported ->
            assertTrue(
                original.containsKey(imported.bounds),
                "no original element at ${imported.bounds}",
            )
        }
        assertEquals(sample.elements.size, project.elements.walkAll().count())
    }

    @Test
    fun `a hand written screen is mapped by JSON-UI type and says so`() {
        val json = """
            {
              "namespace": "demo",
              "screen_content": {
                "type": "panel",
                "size": [ 200, 120 ],
                "controls": [ { "ok_button@demo.ok_button": {} } ]
              },
              "ok_button": {
                "type": "button",
                "size": [ 60, 20 ],
                "offset": [ 10, 90 ],
                "${'$'}button_text": "OK"
              }
            }
        """.trimIndent()

        val outcome = DesignImporter.import("screen.json", json)
        val project = assertNotNull(outcome.project, "notes: ${outcome.notes}")

        assertEquals(200, project.canvas.width)
        assertEquals(1, project.elements.size)
        assertEquals(ElementCatalog.BUTTON_NORMAL, project.elements[0].type)
        assertEquals(IntRect(10, 90, 60, 20), project.elements[0].bounds)
        assertEquals("OK", project.elements[0].props.string("label"))
        assertTrue(
            outcome.notes.any { it.contains("button") },
            "an approximate mapping has to be reported: ${outcome.notes}",
        )
    }

    @Test
    fun `a control sized in percentages is left out rather than guessed at`() {
        val json = """
            {
              "namespace": "demo",
              "screen_content": {
                "type": "panel",
                "size": [ 200, 120 ],
                "controls": [ { "stretchy@demo.stretchy": {} }, { "fixed@demo.fixed": {} } ]
              },
              "stretchy": { "type": "panel", "size": [ "100%", 20 ], "offset": [ 0, 0 ] },
              "fixed": { "type": "panel", "size": [ 40, 20 ], "offset": [ 4, 4 ] }
            }
        """.trimIndent()

        val project = assertNotNull(DesignImporter.import("screen.json", json).project)
        assertEquals(1, project.elements.size, "the percentage-sized control has no place on a fixed canvas")
        assertEquals(IntRect(4, 4, 40, 20), project.elements[0].bounds)
    }

    @Test
    fun `a self referencing control does not hang the importer`() {
        // JSON UI tolerates this and the game survives it; a naive reader
        // recurses until the stack goes.
        val json = """
            {
              "namespace": "demo",
              "screen_content": {
                "type": "panel",
                "size": [ 100, 100 ],
                "controls": [ { "loop@demo.loop": {} } ]
              },
              "loop": {
                "type": "panel",
                "size": [ 20, 20 ],
                "offset": [ 0, 0 ],
                "controls": [ { "loop@demo.loop": {} } ]
              }
            }
        """.trimIndent()

        val project = assertNotNull(DesignImporter.import("loop.json", json).project)
        assertEquals(1, project.elements.size)
    }

    // -- HTML --------------------------------------------------------------

    @Test
    fun `the exported page comes back with its geometry intact`() {
        val html = CodeGenerator.generate(sample, CodeTarget.HTML_CSS).source
        val outcome = DesignImporter.import("chest.html", html, Edition.BEDROCK)

        val project = assertNotNull(outcome.project, "notes: ${outcome.notes}")
        assertEquals(176, project.canvas.width)
        assertEquals(166, project.canvas.height)

        val bounds = project.elements.map { it.bounds }.toSet()
        sample.elements.forEach { original ->
            assertTrue(original.bounds in bounds, "${original.name} at ${original.bounds} did not come back")
        }
    }

    @Test
    fun `element types come back from the class the exporter wrote`() {
        val html = CodeGenerator.generate(sample, CodeTarget.HTML_CSS).source
        val project = assertNotNull(DesignImporter.import("chest.html", html, Edition.BEDROCK).project)

        assertEquals(
            setOf(ElementCatalog.PANEL_CHEST, ElementCatalog.TEXT_LABEL, ElementCatalog.BUTTON_NORMAL),
            project.elements.map { it.type }.toSet(),
        )
    }

    @Test
    fun `a plain page with inline styles is read too`() {
        val html = """
            <html><body>
              <div style="position:absolute; left: 10px; top: 20px; width: 30px; height: 40px;
                          background-color: #ff0000">Hello</div>
              <div style="position:absolute; left: 0; top: 0; width: 50%; height: 10px"></div>
            </body></html>
        """.trimIndent()

        val outcome = DesignImporter.import("page.html", html)
        val project = assertNotNull(outcome.project, "notes: ${outcome.notes}")

        assertEquals(1, project.elements.size, "the percentage-width div has no pixel geometry")
        assertEquals(IntRect(10, 20, 30, 40), project.elements[0].bounds)
        assertEquals(0xFFFF0000L, (project.elements[0].props["background"] as ColorValue).argb)
        assertTrue(outcome.notes.any { it.contains("skipped") }, "notes: ${outcome.notes}")
    }

    @Test
    fun `a commented out rule is not read as a rule`() {
        val html = """
            <html><head><style>
              /* .ghost { left: 1px; top: 1px; width: 9px; height: 9px; } */
              .real { left: 5px; top: 5px; width: 20px; height: 20px; }
            </style></head><body>
              <!-- <div class="ghost"></div> -->
              <div class="real"></div>
            </body></html>
        """.trimIndent()

        val project = assertNotNull(DesignImporter.import("page.html", html).project)
        assertEquals(1, project.elements.size)
        assertEquals(IntRect(5, 5, 20, 20), project.elements[0].bounds)
    }

    @Test
    fun `colours are read in every notation people write them in`() {
        assertEquals(0xFFFF0000L, HtmlImporter.parseColor("#ff0000"))
        assertEquals(0xFFFF0000L, HtmlImporter.parseColor("#f00"))
        assertEquals(0xFFFF0000L, HtmlImporter.parseColor("rgb(255, 0, 0)"))
        assertEquals(0x80FF0000L, HtmlImporter.parseColor("rgba(255, 0, 0, 0.502)"))
        assertEquals(0xFF000000L, HtmlImporter.parseColor("black"))
        assertEquals(0x00000000L, HtmlImporter.parseColor("transparent"))
        assertNull(HtmlImporter.parseColor("chartreuse-ish"))
    }

    // -- SVG ---------------------------------------------------------------

    @Test
    fun `the exported drawing comes back at the right size`() {
        val svg = CodeGenerator.generate(sample, CodeTarget.SVG).source
        val outcome = DesignImporter.import("chest.svg", svg, Edition.BEDROCK)

        val project = assertNotNull(outcome.project, "notes: ${outcome.notes}")
        assertEquals(176, project.canvas.width)
        assertEquals(166, project.canvas.height)
        assertTrue(project.elements.isNotEmpty())
    }

    @Test
    fun `a drawing from somewhere else keeps its coordinates`() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 320 200">
              <rect x="16" y="24" width="64" height="20" fill="#3c3c3c"/>
              <text x="20" y="38" fill="#ffffff">Play</text>
              <circle cx="200" cy="100" r="15" fill="#56b84b"/>
              <path d="M0 0 L10 10"/>
            </svg>
        """.trimIndent()

        val outcome = DesignImporter.import("mock.svg", svg)
        val project = assertNotNull(outcome.project, "notes: ${outcome.notes}")

        assertEquals(320, project.canvas.width)
        assertEquals(200, project.canvas.height)
        assertEquals(IntRect(16, 24, 64, 20), project.elements[0].bounds)
        assertEquals(IntRect(185, 85, 30, 30), project.elements[1].bounds, "a circle becomes its bounding box")
        assertEquals("Play", project.elements[0].props.string("label"), "text lands on the shape beneath it")
        assertTrue(outcome.notes.any { it.contains("path") }, "what was dropped has to be said: ${outcome.notes}")
    }
}
