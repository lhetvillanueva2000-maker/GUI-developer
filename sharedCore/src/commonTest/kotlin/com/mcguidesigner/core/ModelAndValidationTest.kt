package com.mcguidesigner.core

import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.Snapping
import com.mcguidesigner.core.model.Anchor
import com.mcguidesigner.core.model.BoolValue
import com.mcguidesigner.core.model.ColorValue
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.IntPoint
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.IntSize
import com.mcguidesigner.core.model.ResizeHandle
import com.mcguidesigner.core.model.absoluteBoundsMap
import com.mcguidesigner.core.serialization.LoadResult
import com.mcguidesigner.core.serialization.ProjectSerializer
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.core.validation.IssueCode
import com.mcguidesigner.core.validation.ProjectValidator
import com.mcguidesigner.core.validation.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GeometryTest {

    @Test
    fun anchorsResolveAgainstTheirContainer() {
        val container = IntSize(200, 100)
        val size = IntSize(40, 20)

        assertEquals(IntPoint(0, 0), Anchor.TOP_LEFT.resolve(container, size, IntPoint.Zero))
        assertEquals(IntPoint(80, 0), Anchor.TOP_CENTER.resolve(container, size, IntPoint.Zero))
        assertEquals(IntPoint(160, 80), Anchor.BOTTOM_RIGHT.resolve(container, size, IntPoint.Zero))
        assertEquals(IntPoint(85, 45), Anchor.CENTER.resolve(container, size, IntPoint(5, 5)))
    }

    @Test
    fun nestedAbsoluteBoundsAccumulateParentOffsets() {
        val tree = listOf(
            GuiElement(
                id = "panel",
                type = ElementCatalog.PANEL_FRAME,
                name = "Panel",
                bounds = IntRect(20, 10, 100, 80),
                children = listOf(
                    GuiElement(
                        id = "button",
                        type = ElementCatalog.BUTTON_NORMAL,
                        name = "Button",
                        bounds = IntRect(5, 5, 40, 20),
                    ),
                ),
            ),
        )

        val bounds = tree.absoluteBoundsMap(IntSize(200, 200))
        assertEquals(IntRect(20, 10, 100, 80), bounds["panel"])
        assertEquals(IntRect(25, 15, 40, 20), bounds["button"])
    }

    @Test
    fun rectHelpersBehave() {
        val a = IntRect(0, 0, 10, 10)
        val b = IntRect(5, 5, 10, 10)
        assertTrue(a.intersects(b))
        assertFalse(a.containsRect(b))
        assertEquals(IntRect(0, 0, 15, 15), a.union(b))
        assertEquals(IntRect(2, 2, 6, 6), a.inflate(-2))
        assertTrue(a.contains(9, 9))
        assertFalse(a.contains(10, 10))
    }

    @Test
    fun resizeFromTopLeftKeepsTheOppositeCornerFixed() {
        val start = IntRect(10, 10, 60, 40)
        val result = Snapping.applyResize(
            start = start,
            handle = ResizeHandle.TOP_LEFT,
            dx = 10,
            dy = 5,
            minSize = IntSize(2, 2),
            maxSize = IntSize(400, 400),
        )
        assertEquals(start.right, result.right)
        assertEquals(start.bottom, result.bottom)
        assertEquals(50, result.width)
        assertEquals(35, result.height)
    }

    @Test
    fun moveSnapsToASiblingEdgeWithinTheThreshold() {
        val moving = IntRect(38, 0, 20, 20)
        val other = IntRect(0, 0, 40, 20)

        val snap = Snapping.forMove(
            moving = moving,
            others = listOf(other),
            guides = emptyList(),
            canvas = IntSize(200, 200),
            gridSize = 0,
            snapToGrid = false,
            snapToElements = true,
            threshold = 4,
        )

        // The moving rect's left edge (38) snaps to the other's right edge (40).
        assertEquals(2, snap.dx)
        assertTrue(snap.verticalLines.contains(40))
    }
}

class SerializationTest {

    @Test
    fun projectsSurviveAJsonRoundTrip() {
        val original = BuiltInTemplates["java-machine"]!!.instantiate()
        val text = ProjectSerializer.encode(original)
        val result = ProjectSerializer.decode(text)

        assertTrue(result is LoadResult.Success, "expected a successful parse")
        val restored = (result as LoadResult.Success).project
        assertEquals(original, restored)
    }

    @Test
    fun everyPropertyKindRoundTrips() {
        val controller = EditorController(EditorController.newProject(Edition.JAVA, "Props"))
        val id = controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(0, 0))!!
        controller.setProp(id, "label", com.mcguidesigner.core.model.StringValue("Hello"))
        controller.setProp(id, "background", ColorValue(0x80FF00FFL))
        controller.setProp(id, "enabled", BoolValue(false))
        controller.setProp(
            id, "background", ColorValue(0xFF00FF00L),
            state = com.mcguidesigner.core.model.InteractionState.HOVER,
        )

        val restored = ProjectSerializer.decode(ProjectSerializer.encode(controller.project))
        assertTrue(restored is LoadResult.Success)
        val element = (restored as LoadResult.Success).project.element(id)
        assertNotNull(element)
        assertEquals("Hello", element.props["label"]?.asText())
        assertEquals(ColorValue(0x80FF00FFL), element.props["background"])
        assertEquals(
            ColorValue(0xFF00FF00L),
            element.stateOverrides[com.mcguidesigner.core.model.InteractionState.HOVER]?.get("background"),
        )
    }

    @Test
    fun malformedInputFailsCleanlyInsteadOfThrowing() {
        val result = ProjectSerializer.decode("{ this is not json")
        assertTrue(result is LoadResult.Failure)
    }

    @Test
    fun colorParsingAcceptsTheUsualHexForms() {
        assertEquals(ColorValue.of(0xAA, 0xBB, 0xCC), ColorValue.parse("#abc"))
        assertEquals(ColorValue.of(0x11, 0x22, 0x33), ColorValue.parse("#112233"))
        assertEquals(ColorValue.of(0x11, 0x22, 0x33, 0x44), ColorValue.parse("#11223344"))
        assertEquals(null, ColorValue.parse("nope"))
    }
}

class ValidationTest {

    @Test
    fun everyBundledTemplateIsValid() {
        BuiltInTemplates.all.forEach { template ->
            val report = ProjectValidator.validate(template.instantiate(), strict = true)
            assertTrue(
                report.errors.isEmpty(),
                "${template.id} has errors: ${report.errors.joinToString { it.message }}",
            )
        }
    }

    @Test
    fun elementsOutsideTheCanvasAreReported() {
        val controller = EditorController(EditorController.newProject(Edition.JAVA, "Overflow"))
        val id = controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(0, 0))!!
        controller.setBounds(id, IntRect(500, 500, 40, 20))

        val issues = controller.current.validation.issues
        assertTrue(issues.any { it.code == IssueCode.OUT_OF_CANVAS })
    }

    @Test
    fun bedrockOnlyWidgetsAreRejectedInAJavaProject() {
        val controller = EditorController(EditorController.newProject(Edition.BEDROCK, "Touch"))
        controller.addElement(ElementCatalog.BEDROCK_TOUCHPAD, IntPoint(10, 10))
        assertTrue(controller.current.validation.errors.isEmpty())

        controller.switchEdition(Edition.JAVA)
        assertTrue(
            controller.current.validation.issues.any { it.code == IssueCode.UNSUPPORTED_ELEMENT },
            "a touchpad must be flagged once the project targets Java",
        )
    }

    @Test
    fun missingTextureReferencesAreErrors() {
        val controller = EditorController(EditorController.newProject(Edition.JAVA, "Missing"))
        val id = controller.addElement(ElementCatalog.IMAGE_PLACEHOLDER, IntPoint(0, 0))!!
        controller.setProp(id, "texture", com.mcguidesigner.core.model.TextureValue("nope"))

        assertTrue(
            controller.current.validation.issues.any {
                it.code == IssueCode.MISSING_TEXTURE && it.severity == Severity.ERROR
            },
        )
    }

    @Test
    fun bedrockWarnsAboutTouchTargetsThatAreTooSmall() {
        val controller = EditorController(EditorController.newProject(Edition.BEDROCK, "Tiny"))
        val id = controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(0, 0))!!
        controller.setBounds(id, IntRect(0, 0, 12, 10))

        assertTrue(controller.current.validation.issues.any { it.code == IssueCode.TOUCH_TARGET_TOO_SMALL })
    }

    @Test
    fun parityReportListsWidgetsWithNoCounterpart() {
        val project = BuiltInTemplates["bedrock-hud"]!!.instantiate()
        val parity = ProjectValidator.parityIssues(project)
        assertTrue(parity.isNotEmpty(), "the touch HUD uses Bedrock-only widgets")
        assertTrue(parity.all { it.code == IssueCode.EDITION_PARITY })
    }
}

class CatalogTest {

    @Test
    fun everyDefinitionHasUsableDefaults() {
        ElementCatalog.all.forEach { definition ->
            assertTrue(definition.typeId.isNotBlank(), "type id must not be blank")
            assertTrue(definition.displayName.isNotBlank(), "${definition.typeId} needs a display name")
            assertTrue(
                definition.defaultSize.width >= definition.minSize.width &&
                    definition.defaultSize.height >= definition.minSize.height,
                "${definition.typeId} default size is below its own minimum",
            )
            assertTrue(
                definition.defaultSize.width <= definition.maxSize.width &&
                    definition.defaultSize.height <= definition.maxSize.height,
                "${definition.typeId} default size is above its own maximum",
            )
            assertTrue(definition.editions.isNotEmpty(), "${definition.typeId} supports no edition")
        }
    }

    @Test
    fun defaultPropertiesAlwaysSatisfyTheirOwnSpecs() {
        Edition.entries.forEach { edition ->
            ElementCatalog.forEdition(edition).forEach { definition ->
                definition.propertiesFor(edition).forEach { spec ->
                    assertTrue(
                        spec.accepts(spec.default),
                        "${definition.typeId}.${spec.key} default does not match its declared type",
                    )
                }
            }
        }
    }

    @Test
    fun bothEditionsExposeAUsablePalette() {
        Edition.entries.forEach { edition ->
            val palette = ElementCatalog.forEdition(edition)
            assertTrue(palette.size > 15, "${edition.displayName} palette is suspiciously small")
        }
        assertTrue(ElementCatalog.exclusiveTo(Edition.BEDROCK).isNotEmpty())
        assertTrue(ElementCatalog.exclusiveTo(Edition.JAVA).isNotEmpty())
    }
}
