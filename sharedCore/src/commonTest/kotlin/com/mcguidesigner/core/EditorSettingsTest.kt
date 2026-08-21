package com.mcguidesigner.core

import com.mcguidesigner.core.catalog.CustomPresets
import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorSettings
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.IntPoint
import com.mcguidesigner.core.model.ShapeKind
import com.mcguidesigner.core.model.TextureAsset
import com.mcguidesigner.core.model.string
import com.mcguidesigner.core.templates.BuiltInTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The configurable move step, and the presets the "add anything" surfaces
 * offer.
 *
 * The nudge tests are the important ones: three separate front-end affordances
 * (two move pads and the desktop arrow keys) all route through
 * `nudgeSelection`, so its behaviour is the contract that keeps them agreeing.
 */
class EditorSettingsTest {

    private fun controllerWithOneElement(): Pair<EditorController, String> {
        val controller = EditorController(EditorController.newProject(Edition.JAVA, "Test"))
        val id = controller.addElement(ElementCatalog.PANEL_FRAME, IntPoint(40, 40))
        assertNotNull(id)
        controller.select(id)
        return controller to id
    }

    private fun boundsOf(controller: EditorController, id: String) =
        assertNotNull(controller.project.element(id)).bounds

    // -- Nudging ------------------------------------------------------------

    @Test
    fun `a nudge moves by the configured small step`() {
        val (controller, id) = controllerWithOneElement()
        controller.setSettings(EditorSettings(nudgeStep = 3))
        val before = boundsOf(controller, id)

        controller.nudgeSelection(1, 0)
        assertEquals(before.x + 3, boundsOf(controller, id).x)

        controller.nudgeSelection(0, 1)
        assertEquals(before.y + 3, boundsOf(controller, id).y)

        controller.nudgeSelection(-1, 0)
        controller.nudgeSelection(0, -1)
        assertEquals(before, boundsOf(controller, id), "opposite nudges should cancel out")
    }

    @Test
    fun `a large nudge moves by the configured big step`() {
        val (controller, id) = controllerWithOneElement()
        controller.setSettings(EditorSettings(nudgeStep = 1, largeNudgeStep = 12))
        val before = boundsOf(controller, id)

        controller.nudgeSelection(1, 0, large = true)
        assertEquals(before.x + 12, boundsOf(controller, id).x)
    }

    @Test
    fun `nudging a locked element leaves it alone`() {
        val (controller, id) = controllerWithOneElement()
        val before = boundsOf(controller, id)
        controller.setLocked(id, true)

        controller.nudgeSelection(1, 1)
        assertEquals(before, boundsOf(controller, id))
    }

    @Test
    fun `grid-snapping nudges walk the grid instead of drifting off it`() {
        val (controller, id) = controllerWithOneElement()
        controller.updateCanvas { it.copy(gridSize = 8) }
        controller.setSettings(EditorSettings(nudgeStep = 3, nudgeSnapsToGrid = true))
        // A position deliberately off the grid, as a drag would leave it.
        controller.setBounds(id, boundsOf(controller, id).copy(x = 13, y = 13))

        controller.nudgeSelection(1, 0)
        assertEquals(16, boundsOf(controller, id).x, "should land on the next grid line, not 13+3")

        controller.nudgeSelection(1, 0)
        assertEquals(24, boundsOf(controller, id).x, "and then walk the grid a line at a time")

        controller.nudgeSelection(-1, 0)
        assertEquals(16, boundsOf(controller, id).x, "back down the same way")
    }

    @Test
    fun `a grid-snapping nudge from exactly on the grid still moves`() {
        val (controller, id) = controllerWithOneElement()
        controller.updateCanvas { it.copy(gridSize = 8) }
        controller.setSettings(EditorSettings(nudgeSnapsToGrid = true))
        controller.setBounds(id, boundsOf(controller, id).copy(x = 16, y = 16))

        controller.nudgeSelection(-1, 0)
        assertEquals(8, boundsOf(controller, id).x, "a nudge must never be a no-op")

        controller.nudgeSelection(1, 0)
        assertEquals(16, boundsOf(controller, id).x)
    }

    @Test
    fun `grid snapping falls back to a plain step when the grid is off`() {
        val (controller, id) = controllerWithOneElement()
        controller.updateCanvas { it.copy(gridSize = 0) }
        controller.setSettings(EditorSettings(nudgeStep = 5, nudgeSnapsToGrid = true))
        val before = boundsOf(controller, id)

        controller.nudgeSelection(0, 1)
        assertEquals(before.y + 5, boundsOf(controller, id).y)
    }

    @Test
    fun `repeated nudges collapse into one undo step`() {
        val (controller, id) = controllerWithOneElement()
        val before = boundsOf(controller, id)

        repeat(5) { controller.nudgeSelection(1, 0) }
        assertEquals(before.x + 5, boundsOf(controller, id).x)

        controller.undo()
        assertEquals(
            before,
            boundsOf(controller, id),
            "five taps of an arrow is one correction, so one undo should reverse it",
        )
    }

    // -- The settings themselves --------------------------------------------

    @Test
    fun `out-of-range settings are clamped rather than accepted`() {
        val controller = EditorController(EditorController.newProject(Edition.JAVA, "Test"))
        controller.setSettings(
            EditorSettings(nudgeStep = 0, largeNudgeStep = 9_999, autosaveSeconds = -4),
        )

        val settings = controller.current.settings
        assertEquals(EditorSettings.MIN_STEP, settings.nudgeStep)
        assertEquals(EditorSettings.MAX_STEP, settings.largeNudgeStep)
        assertEquals(0, settings.autosaveSeconds)
    }

    @Test
    fun `settings survive opening another document`() {
        val controller = EditorController(EditorController.newProject(Edition.JAVA, "Test"))
        controller.setSettings(EditorSettings(nudgeStep = 7, showNudgePad = false))

        controller.replaceProject(BuiltInTemplates.demo.instantiate())

        assertEquals(7, controller.current.settings.nudgeStep)
        assertTrue(!controller.current.settings.showNudgePad)
    }

    @Test
    fun `duplicating uses the configured offset`() {
        val (controller, id) = controllerWithOneElement()
        controller.setSettings(EditorSettings(duplicateOffset = 20))
        val original = boundsOf(controller, id)

        controller.duplicateSelection()
        val copyId = assertNotNull(controller.current.primarySelection)
        val copy = boundsOf(controller, copyId)

        assertTrue(copyId != id, "the duplicate should be a new element")
        assertEquals(original.x + 20, copy.x)
        assertEquals(original.y + 20, copy.y)
    }

    // -- Presets -------------------------------------------------------------

    @Test
    fun `every preset names a real catalog type`() {
        CustomPresets.all.forEach { preset ->
            assertNotNull(
                ElementCatalog[preset.typeId],
                "preset '${preset.id}' points at unknown type '${preset.typeId}'",
            )
        }
    }

    @Test
    fun `preset ids and labels are unique`() {
        val ids = CustomPresets.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size, "duplicate preset ids: $ids")

        val labels = CustomPresets.all.map { it.label }
        assertEquals(labels.size, labels.distinct().size, "duplicate preset labels: $labels")
    }

    @Test
    fun `there is one shape preset per shape kind`() {
        assertEquals(ShapeKind.entries.size, CustomPresets.shapes.size)
        ShapeKind.entries.forEach { kind ->
            assertNotNull(
                CustomPresets.shapes.firstOrNull { it.props.string("shape") == kind.id },
                "no preset creates a ${kind.displayName}",
            )
        }
    }

    @Test
    fun `adding a preset applies its property overrides`() {
        val controller = EditorController(EditorController.newProject(Edition.JAVA, "Test"))
        val star = assertNotNull(CustomPresets.shapes.firstOrNull { it.props.string("shape") == "star" })

        val id = assertNotNull(
            controller.addElement(
                typeId = star.typeId,
                at = IntPoint(50, 50),
                initialProps = star.props,
                nameHint = star.label,
            ),
        )

        val element = assertNotNull(controller.project.element(id))
        assertEquals("star", element.props.string("shape"))
        assertEquals(star.label, element.name)
        // The rest of the type's defaults must still be there.
        assertTrue(element.props.containsKey("fillColor"))
    }

    @Test
    fun `preset searching matches labels and descriptions`() {
        assertTrue(CustomPresets.search("star").any { it.id == "shape-star" })
        assertTrue(CustomPresets.search("GIF").any { it.id == "animated-image" })
        assertTrue(CustomPresets.search("").size == CustomPresets.all.size)
        assertTrue(CustomPresets.search("definitely not a shape").isEmpty())
    }

    // -- Shape geometry ------------------------------------------------------

    @Test
    fun `polygonal shapes produce closed outlines inside their box`() {
        ShapeKind.entries.filter { it.isPolygonal }.forEach { kind ->
            val points = kind.outline(sides = 7, innerRadius = 0.4f)
            assertTrue(points.size >= 3, "${kind.id} produced ${points.size} points")
            points.forEach { (x, y) ->
                assertTrue(x in -0.001f..1.001f, "${kind.id} has an x outside its box: $x")
                assertTrue(y in -0.001f..1.001f, "${kind.id} has a y outside its box: $y")
            }
        }
    }

    @Test
    fun `curved shapes have no polygon outline`() {
        assertTrue(ShapeKind.ELLIPSE.outline().isEmpty())
        assertTrue(ShapeKind.ROUNDED_RECTANGLE.outline().isEmpty())
        assertTrue(!ShapeKind.ELLIPSE.isPolygonal)
        assertTrue(!ShapeKind.ROUNDED_RECTANGLE.isPolygonal)
    }

    @Test
    fun `a regular polygon has as many corners as it has sides`() {
        (3..12).forEach { sides ->
            assertEquals(sides, ShapeKind.POLYGON.outline(sides = sides).size)
        }
        // A star alternates outer and inner points, so twice as many.
        assertEquals(10, ShapeKind.STAR.outline(sides = 5).size)
    }

    @Test
    fun `absurd side counts are clamped rather than crashing`() {
        assertEquals(ShapeKind.MIN_SIDES, ShapeKind.POLYGON.outline(sides = -5).size)
        assertEquals(ShapeKind.MAX_SIDES, ShapeKind.POLYGON.outline(sides = 9_999).size)
    }

    @Test
    fun `unknown shape ids fall back to a rectangle`() {
        assertEquals(ShapeKind.RECTANGLE, ShapeKind.fromId("no-such-shape"))
        assertEquals(ShapeKind.RECTANGLE, ShapeKind.fromId(null))
        assertEquals(ShapeKind.STAR, ShapeKind.fromId("star"))
    }

    @Test
    fun `nudging with nothing selected does nothing`() {
        val controller = EditorController(EditorController.newProject(Edition.JAVA, "Test"))
        val before = controller.project
        controller.nudgeSelection(1, 1)
        assertEquals(before, controller.project)
        assertNull(controller.current.primarySelection)
    }
}

/**
 * The two checks the validator gained for animated images.
 *
 * Both exist because the failure they describe is invisible: an animated
 * element sitting on one frame looks like a bug in the app, not a texture with
 * nothing to animate.
 */
class AnimationValidationTest {

    private fun projectWith(texture: TextureAsset) = EditorController
        .newProject(Edition.JAVA, "Anim")
        .let { project ->
            project.copy(
                textures = listOf(texture),
                elements = listOf(
                    com.mcguidesigner.core.model.GuiElement(
                        id = "a",
                        type = ElementCatalog.IMAGE_ANIMATED,
                        name = "Spinner",
                        bounds = com.mcguidesigner.core.model.IntRect(0, 0, 16, 16),
                        props = ElementCatalog.require(ElementCatalog.IMAGE_ANIMATED)
                            .defaultProps(Edition.JAVA) +
                            mapOf("texture" to com.mcguidesigner.core.model.TextureValue(texture.id)),
                    ),
                ),
            )
        }

    private fun texture(frames: Int, delays: List<Int> = emptyList()) = TextureAsset(
        id = "t",
        name = "spinner",
        format = "png",
        width = 16,
        height = 16 * frames,
        dataBase64 = "",
        frameCount = frames,
        frameDelaysMillis = delays,
    )

    @Test
    fun `an animated element pointed at a still is flagged`() {
        val report = com.mcguidesigner.core.validation.ProjectValidator
            .validate(projectWith(texture(frames = 1)))

        assertTrue(
            report.issues.any {
                it.code == com.mcguidesigner.core.validation.IssueCode.ANIMATION_NOT_ANIMATED
            },
            "a one-frame texture in an animated element should be reported: ${report.issues}",
        )
    }

    @Test
    fun `a proper frame strip raises no animation warning`() {
        val report = com.mcguidesigner.core.validation.ProjectValidator
            .validate(projectWith(texture(frames = 6)))

        assertTrue(
            report.issues.none {
                it.code == com.mcguidesigner.core.validation.IssueCode.ANIMATION_NOT_ANIMATED
            },
            "a six-frame strip is exactly what this element wants: ${report.issues}",
        )
    }

    @Test
    fun `uneven source timing is reported as information, not a problem`() {
        val report = com.mcguidesigner.core.validation.ProjectValidator
            .validate(projectWith(texture(frames = 3, delays = listOf(100, 300, 100))))

        val issue = report.issues.firstOrNull {
            it.code == com.mcguidesigner.core.validation.IssueCode.ANIMATION_TIMING_FLATTENED
        }
        assertNotNull(issue, "uneven timing should be mentioned: ${report.issues}")
        assertEquals(com.mcguidesigner.core.validation.Severity.INFO, issue.severity)
    }
}
