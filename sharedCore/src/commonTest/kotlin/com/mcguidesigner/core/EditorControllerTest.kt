package com.mcguidesigner.core

import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.model.AlignMode
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.IntPoint
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.ResizeHandle
import com.mcguidesigner.core.model.findById
import com.mcguidesigner.core.model.walkAll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorControllerTest {

    private fun controller(edition: Edition = Edition.JAVA) =
        EditorController(EditorController.newProject(edition, "Test Screen"))

    @Test
    fun addElementPlacesItAndSelectsIt() {
        val controller = controller()
        val id = controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(16, 24))

        assertNotNull(id)
        val element = controller.project.element(id)
        assertNotNull(element)
        assertEquals(ElementCatalog.BUTTON_NORMAL, element.type)
        assertEquals(setOf(id), controller.current.selection)
        assertTrue(controller.current.dirty)
    }

    @Test
    fun addingIntoAContainerParentsAutomatically() {
        val controller = controller()
        val panelId = controller.addElement(ElementCatalog.PANEL_FRAME, IntPoint(0, 0))
        assertNotNull(panelId)
        controller.setBounds(panelId, IntRect(0, 0, 120, 80))

        val buttonId = controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(20, 20))
        assertNotNull(buttonId)

        val panel = controller.project.element(panelId)
        assertNotNull(panel)
        assertTrue(panel.children.any { it.id == buttonId }, "button should be nested in the panel")
    }

    @Test
    fun undoRestoresThePreviousDocumentAndSelection() {
        val controller = controller()
        val id = controller.addElement(ElementCatalog.TEXT_LABEL, IntPoint(4, 4))
        assertNotNull(id)
        assertEquals(1, controller.project.allElements.size)

        controller.undo()
        assertEquals(0, controller.project.allElements.size)
        assertTrue(controller.current.selection.isEmpty())
        assertTrue(controller.current.canRedo)

        controller.redo()
        assertEquals(1, controller.project.allElements.size)
        assertNotNull(controller.project.element(id))
    }

    @Test
    fun draggingCoalescesIntoASingleUndoStep() {
        val controller = controller()
        val id = controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(0, 0))
        assertNotNull(id)
        controller.setBounds(id, IntRect(0, 0, 40, 20))
        controller.endGesture()

        val before = controller.project.element(id)!!.bounds
        controller.beginDrag(setOf(id))
        // Several move events, as a real drag would produce.
        controller.dragSelectionTo(3, 0)
        controller.dragSelectionTo(7, 2)
        controller.dragSelectionTo(11, 5)
        controller.endGesture()

        val after = controller.project.element(id)!!.bounds
        assertTrue(after != before, "the drag should have moved the element")

        controller.undo()
        assertEquals(before, controller.project.element(id)!!.bounds)
    }

    @Test
    fun resizeRespectsCatalogMinimumsAndFixedSizes() {
        val controller = controller()
        val slotId = controller.addElement(ElementCatalog.SLOT_INVENTORY, IntPoint(0, 0))
        assertNotNull(slotId)

        controller.beginResize(slotId, ResizeHandle.BOTTOM_RIGHT)
        controller.resizeBy(slotId, ResizeHandle.BOTTOM_RIGHT, 50, 50)
        controller.endGesture()

        // Inventory slots are pinned to 18x18 by the catalog.
        val slot = controller.project.element(slotId)!!
        assertEquals(18, slot.bounds.width)
        assertEquals(18, slot.bounds.height)
    }

    @Test
    fun duplicateProducesFreshIdsAndUniqueNames() {
        val controller = controller()
        val id = controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(0, 0))
        assertNotNull(id)

        controller.duplicateSelection()
        val all = controller.project.allElements
        assertEquals(2, all.size)
        assertEquals(2, all.map { it.id }.toSet().size, "ids must be unique")
        assertEquals(2, all.map { it.name }.toSet().size, "names must be unique")
    }

    @Test
    fun alignLeftLinesUpEveryElementInTheSelection() {
        val controller = controller()
        val a = controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(10, 0))!!
        controller.setBounds(a, IntRect(10, 0, 40, 20))
        val b = controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(30, 40))!!
        controller.setBounds(b, IntRect(30, 40, 40, 20))

        controller.selectMany(setOf(a, b))
        controller.align(AlignMode.LEFT)

        val bounds = controller.project.absoluteBounds()
        assertEquals(bounds[a]!!.left, bounds[b]!!.left)
    }

    @Test
    fun deleteRemovesTheWholeSubtree() {
        val controller = controller()
        val panelId = controller.addElement(ElementCatalog.PANEL_FRAME, IntPoint(0, 0))!!
        controller.setBounds(panelId, IntRect(0, 0, 100, 100))
        controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(10, 10))

        assertEquals(2, controller.project.allElements.size)
        controller.select(panelId)
        controller.deleteSelection()
        assertEquals(0, controller.project.allElements.size)
    }

    @Test
    fun reparentingKeepsTheElementWhereItLooked() {
        val controller = controller()
        val panelId = controller.addElement(ElementCatalog.PANEL_FRAME, IntPoint(0, 0))!!
        controller.setBounds(panelId, IntRect(20, 20, 120, 80))

        // Place a button outside the panel, then move it in.
        val buttonId = controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(0, 0), parentId = null)!!
        controller.setBounds(buttonId, IntRect(60, 50, 40, 20))
        val absoluteBefore = controller.project.absoluteBounds()[buttonId]

        controller.reparent(buttonId, panelId)

        val absoluteAfter = controller.project.absoluteBounds()[buttonId]
        assertEquals(absoluteBefore, absoluteAfter, "reparenting must not visually move the element")
        assertTrue(controller.project.element(panelId)!!.children.any { it.id == buttonId })
    }

    @Test
    fun reparentingIntoOwnSubtreeIsRefused() {
        val controller = controller()
        val outerId = controller.addElement(ElementCatalog.PANEL_FRAME, IntPoint(0, 0))!!
        controller.setBounds(outerId, IntRect(0, 0, 140, 120))
        val innerId = controller.addElement(ElementCatalog.PANEL_FRAME, IntPoint(10, 10))!!

        controller.reparent(outerId, innerId)

        // The tree must be unchanged: outer still holds inner, not vice versa.
        val outer = controller.project.elements.findById(outerId)
        assertNotNull(outer)
        assertTrue(outer.children.any { it.id == innerId })
    }

    @Test
    fun zOrderCommandsMoveElementsWithinTheirParent() {
        val controller = controller()
        val first = controller.addElement(ElementCatalog.TEXT_LABEL, IntPoint(0, 0))!!
        val second = controller.addElement(ElementCatalog.TEXT_LABEL, IntPoint(0, 20))!!

        assertEquals(listOf(first, second), controller.project.elements.map { it.id })

        controller.select(first)
        controller.bringToFront()
        assertEquals(listOf(second, first), controller.project.elements.map { it.id })

        controller.sendToBack()
        assertEquals(listOf(first, second), controller.project.elements.map { it.id })
    }

    @Test
    fun hitTestPrefersTheTopmostVisibleUnlockedElement() {
        val controller = controller()
        val bottom = controller.addElement(ElementCatalog.PANEL_FRAME, IntPoint(0, 0))!!
        controller.setBounds(bottom, IntRect(0, 0, 100, 100))
        val top = controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(0, 0), parentId = null)!!
        controller.setBounds(top, IntRect(10, 10, 40, 20))

        assertEquals(top, controller.hitTest(IntPoint(20, 15))?.id)

        controller.setVisible(top, false)
        assertEquals(bottom, controller.hitTest(IntPoint(20, 15))?.id)
    }

    @Test
    fun lockedElementsAreNotMoved() {
        val controller = controller()
        val id = controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(0, 0))!!
        controller.setBounds(id, IntRect(5, 5, 40, 20))
        controller.setLocked(id, true)
        controller.select(id)

        controller.moveSelection(20, 20, null)

        assertEquals(IntRect(5, 5, 40, 20), controller.project.element(id)!!.bounds)
    }

    @Test
    fun copyAndPasteRoundTripsThroughText() {
        val controller = controller()
        val id = controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(8, 8))!!
        val clipboard = controller.copySelectionToText()
        assertNotNull(clipboard)

        assertTrue(controller.pasteFromText(clipboard, IntPoint(40, 40)))
        assertEquals(2, controller.project.allElements.size)
        assertEquals(2, controller.project.elements.walkAll().map { it.id }.toSet().size)
        // The original is untouched.
        assertNotNull(controller.project.element(id))
    }

    @Test
    fun removingATextureClearsEveryReferenceToIt() {
        val controller = controller()
        val id = controller.addElement(ElementCatalog.IMAGE_PLACEHOLDER, IntPoint(0, 0))!!
        val asset = com.mcguidesigner.core.model.TextureAsset(
            id = "tex_1",
            name = "panel.png",
            format = "png",
            width = 16,
            height = 16,
            dataBase64 = "AA==",
        )
        controller.addTexture(asset)
        controller.setProp(id, "texture", com.mcguidesigner.core.model.TextureValue("tex_1"))

        controller.removeTexture("tex_1")

        assertNull(controller.project.texture("tex_1"))
        val value = controller.project.element(id)!!.props["texture"]
        assertEquals(com.mcguidesigner.core.model.TextureValue(null), value)
        assertFalse(
            controller.current.validation.issues.any {
                it.code == com.mcguidesigner.core.validation.IssueCode.MISSING_TEXTURE
            },
        )
    }
}
