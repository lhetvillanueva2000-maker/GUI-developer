package com.mcguidesigner.styles.canvas

import androidx.compose.ui.geometry.Size
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.IntSize
import com.mcguidesigner.core.model.absoluteBoundsMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the canvas actually paints at a given pan and zoom.
 *
 * These exist because the bug they pin down was invisible to every other kind
 * of test: the document was correct, the exports were correct, and the editor
 * simply stopped drawing things once you scrolled far enough. The only way to
 * catch that without a human looking at a screen is to make the culling
 * decision inspectable, which is what [visibleElementIds] is for.
 */
class CanvasCullingTest {

    private val viewport = Size(800f, 600f)
    private val canvasSize = IntSize(176, 166)

    private fun transform(zoom: Float = 1f, panX: Float = 0f, panY: Float = 0f) =
        CanvasTransform(zoom = zoom, panX = panX, panY = panY, viewport = viewport, canvas = canvasSize)

    private fun node(id: String, x: Int, y: Int, w: Int, h: Int, children: List<GuiElement> = emptyList()) =
        GuiElement(id = id, type = "panel.frame", name = id, bounds = IntRect(x, y, w, h), children = children)

    private fun drawn(root: List<GuiElement>, transform: CanvasTransform): List<String> =
        visibleElementIds(root, root.absoluteBoundsMap(canvasSize), transform, viewport)

    // -- The regression this file exists for --------------------------------

    @Test
    fun `a child in view is drawn even when its parent is off-screen`() {
        // A button dragged past the edge of its panel stays a child of that
        // panel. Nothing stops a child sitting outside its parent's bounds, so
        // the parent's position says nothing about the child's.
        val root = listOf(
            node("panel", 0, 0, 40, 40, children = listOf(node("escapee", 500, 300, 20, 20))),
        )

        // Pan left far enough that the panel itself leaves the viewport while
        // the child, 500px further right, is still comfortably inside it.
        val panned = transform(panX = -500f)
        val visible = drawn(root, panned)

        assertTrue("panel" !in visible, "the parent really is off-screen here")
        assertTrue(
            "escapee" in visible,
            "the child is in full view and must still be painted; got $visible",
        )
    }

    @Test
    fun `a whole subtree in view survives an off-screen parent`() {
        val root = listOf(
            node(
                "container", 0, 0, 10, 10,
                children = listOf(
                    node("a", 400, 200, 20, 20),
                    node("b", 430, 200, 20, 20, children = listOf(node("b-child", 10, 10, 8, 8))),
                ),
            ),
        )

        val visible = drawn(root, transform(panX = -420f))
        assertTrue("container" !in visible)
        assertTrue("a" in visible && "b" in visible && "b-child" in visible, "got $visible")
    }

    // -- Ordinary culling still works ---------------------------------------

    @Test
    fun `everything on screen is drawn in paint order`() {
        val root = listOf(
            node("back", 0, 0, 100, 100, children = listOf(node("front", 10, 10, 20, 20))),
            node("sibling", 40, 40, 20, 20),
        )

        assertEquals(listOf("back", "front", "sibling"), drawn(root, transform()))
    }

    @Test
    fun `elements far outside the viewport are not drawn`() {
        val root = listOf(
            node("near", 0, 0, 20, 20),
            node("farRight", 5_000, 0, 20, 20),
            node("farBelow", 0, 5_000, 20, 20),
            node("farLeft", -5_000, 0, 20, 20),
            node("farAbove", 0, -5_000, 20, 20),
        )

        assertEquals(listOf("near"), drawn(root, transform()))
    }

    @Test
    fun `hiding a container hides everything inside it`() {
        // The deliberate asymmetry with the test above: an element the user
        // switched off takes its contents with it, the way hiding a group does
        // in any design tool. An element that merely scrolled out of view does
        // not, because that is a fact about the viewport and not about the
        // document.
        val root = listOf(
            node("hidden", 0, 0, 40, 40, children = listOf(node("inside", 5, 5, 10, 10)))
                .copy(visible = false),
            node("neighbour", 60, 0, 10, 10),
        )

        assertEquals(listOf("neighbour"), drawn(root, transform()))
    }

    // -- The predicate itself ------------------------------------------------

    @Test
    fun `an element touching the viewport edge counts as on screen`() {
        val at = { l: Float, t: Float, r: Float, b: Float ->
            isOnScreen(androidx.compose.ui.geometry.Rect(l, t, r, b), viewport)
        }

        assertTrue(at(-10f, -10f, 0f, 0f), "flush against the top-left corner")
        assertTrue(at(800f, 600f, 810f, 610f), "flush against the bottom-right corner")
        assertTrue(!at(-10f, 0f, -0.5f, 10f), "wholly past the left edge")
        assertTrue(!at(800.5f, 0f, 900f, 10f), "wholly past the right edge")
        assertTrue(!at(0f, -20f, 10f, -0.5f), "wholly above")
        assertTrue(!at(0f, 600.5f, 10f, 700f), "wholly below")
    }

    // -- Zoom ----------------------------------------------------------------

    @Test
    fun `zooming in keeps what stays in frame and drops what leaves it`() {
        // At 9x a 176x166 canvas is 1584x1494 against an 800x600 viewport, so
        // it is centred with its origin off the top-left at (-392, -447). A
        // slot at 8,8 is therefore genuinely off-screen while one at 44,40 is
        // not - and the panel containing both still covers the viewport.
        val root = listOf(
            node(
                "panel", 0, 0, 176, 166,
                children = listOf(
                    node("cornerSlot", 8, 8, 18, 18),
                    node("middleSlot", 44, 40, 18, 18),
                ),
            ),
        )

        assertEquals(listOf("panel", "middleSlot"), drawn(root, transform(zoom = 9f)))
    }

    @Test
    fun `panning past the far corner leaves nothing to draw`() {
        val root = listOf(node("panel", 0, 0, 176, 166, children = listOf(node("slot", 8, 8, 18, 18))))
        assertTrue(drawn(root, transform(panX = 10_000f, panY = 10_000f)).isEmpty())
    }
}
