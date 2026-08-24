package com.mcguidesigner.core

import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.IntPoint
import com.mcguidesigner.core.model.int
import com.mcguidesigner.core.model.walkAll
import kotlin.test.Test
import kotlin.test.assertEquals

class RotateSelectionTest {

    /** A controller holding one button, selected. A new project starts empty. */
    private fun controllerWithOneElement(): EditorController {
        val controller = EditorController(EditorController.newProject(Edition.JAVA, "Rotate"))
        controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(0, 0))
        return controller
    }

    private fun EditorController.rotationOfFirst(): Int =
        current.project.elements.walkAll().first().props.int("rotation", 0)

    private fun EditorController.selectFirst() {
        val first = current.project.elements.walkAll().first()
        select(first.id)
    }

    @Test
    fun `rotating turns the selection`() {
        val controller = controllerWithOneElement()
        controller.selectFirst()
        controller.rotateSelection(90)
        assertEquals(90, controller.rotationOfFirst())
    }

    @Test
    fun `rotation accumulates and wraps at a full turn`() {
        val controller = controllerWithOneElement()
        controller.selectFirst()
        repeat(4) { controller.rotateSelection(90) }
        // Four right turns is 360, which is the same angle as 0 - and must be
        // recorded as 0, or the inspector, the diff and the export all show a
        // change that is not one.
        assertEquals(0, controller.rotationOfFirst())
    }

    @Test
    fun `turning left from zero wraps to 270 rather than going negative`() {
        // Kotlin's % keeps the sign of the left operand, so -90 % 360 is -90.
        // A negative rotation is not wrong on a canvas, but it is a different
        // number for the same angle, and two of them would never compare equal.
        val controller = controllerWithOneElement()
        controller.selectFirst()
        controller.rotateSelection(-90)
        assertEquals(270, controller.rotationOfFirst())
    }

    @Test
    fun `a full turn is not recorded as an edit`() {
        val controller = controllerWithOneElement()
        controller.selectFirst()
        val before = controller.current.dirty
        controller.rotateSelection(360)
        assertEquals(before, controller.current.dirty, "rotating by 360 changes nothing")
    }

    @Test
    fun `rotating nothing does nothing`() {
        val controller = controllerWithOneElement()
        controller.clearSelection()
        val before = controller.current.dirty
        controller.rotateSelection(90)
        assertEquals(before, controller.current.dirty)
    }

    @Test
    fun `rotation is undoable`() {
        val controller = controllerWithOneElement()
        controller.selectFirst()
        controller.rotateSelection(90)
        assertEquals(90, controller.rotationOfFirst())
        controller.undo()
        assertEquals(0, controller.rotationOfFirst())
    }
}
