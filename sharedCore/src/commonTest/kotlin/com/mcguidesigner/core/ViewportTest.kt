package com.mcguidesigner.core

import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.model.Edition
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Panning and zooming the view.
 *
 * The rules worth pinning down are the two that decide whether the editor
 * feels attached to the content: you cannot lose the canvas off the edge of
 * the screen, and zooming keeps whatever you aimed at where it is.
 */
class ViewportTest {

    private val viewW = 800f
    private val viewH = 600f

    private fun controller() = EditorController(EditorController.newProject(Edition.JAVA, "Test"))

    /** Where the canvas origin lands in view space, the way the canvas draws it. */
    private fun origin(c: EditorController): Pair<Float, Float> {
        val s = c.current
        return ((viewW - s.project.canvas.width * s.zoom) / 2f + s.panX) to
            ((viewH - s.project.canvas.height * s.zoom) / 2f + s.panY)
    }

    private fun canvasSpan(c: EditorController): Pair<ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>> {
        val s = c.current
        val (ox, oy) = origin(c)
        return (ox..ox + s.project.canvas.width * s.zoom) to (oy..oy + s.project.canvas.height * s.zoom)
    }

    // -- The canvas cannot be lost -------------------------------------------

    @Test
    fun `panning cannot push the canvas off the screen`() {
        val c = controller()

        // Shove it far past every edge, one direction at a time.
        listOf(
            50_000f to 0f, -50_000f to 0f, 0f to 50_000f, 0f to -50_000f,
            80_000f to 80_000f, -80_000f to -80_000f,
        ).forEach { (dx, dy) ->
            c.setPan(0f, 0f)
            c.panBy(dx, dy, viewW, viewH)

            val (xs, ys) = canvasSpan(c)
            assertTrue(
                xs.endInclusive > 0f && xs.start < viewW,
                "canvas left the viewport horizontally after panning ($dx, $dy): $xs",
            )
            assertTrue(
                ys.endInclusive > 0f && ys.start < viewH,
                "canvas left the viewport vertically after panning ($dx, $dy): $ys",
            )
        }
    }

    @Test
    fun `a canvas larger than the viewport still covers a fifth of it`() {
        val c = controller()
        c.setZoom(12f) // 176x166 at 12x is far bigger than 800x600
        c.panBy(50_000f, 0f, viewW, viewH)

        val (xs, _) = canvasSpan(c)
        val covered = minOf(xs.endInclusive, viewW) - maxOf(xs.start, 0f)
        assertTrue(
            covered >= viewW * EditorController.MIN_CANVAS_ON_SCREEN - 1f,
            "only ${covered}px of an ${viewW}px viewport still shows canvas",
        )
    }

    @Test
    fun `panning without a viewport is left alone`() {
        // The clamp needs a viewport to clamp against; callers that genuinely
        // do not have one must not silently get their pan mangled.
        val c = controller()
        c.setPan(0f, 0f)
        c.panBy(9_999f, 9_999f)

        assertEquals(9_999f, c.current.panX)
        assertEquals(9_999f, c.current.panY)
    }

    @Test
    fun `an ordinary pan is not clamped`() {
        val c = controller()
        c.setPan(0f, 0f)
        c.panBy(30f, -20f, viewW, viewH)

        assertEquals(30f, c.current.panX)
        assertEquals(-20f, c.current.panY)
    }

    // -- Zoom stays under the pointer ----------------------------------------

    /** The canvas-space point currently under a view-space position. */
    private fun canvasPointAt(c: EditorController, x: Float, y: Float): Pair<Float, Float> {
        val (ox, oy) = origin(c)
        val z = c.current.zoom
        return ((x - ox) / z) to ((y - oy) / z)
    }

    @Test
    fun `zooming keeps the point under the focus where it is`() {
        listOf(200f to 150f, 640f to 470f, 400f to 300f, 60f to 540f).forEach { (fx, fy) ->
            val c = controller()
            c.setZoom(3f)
            c.setPan(0f, 0f)

            val before = canvasPointAt(c, fx, fy)
            c.zoomAround(2.5f, fx, fy, viewW, viewH)
            val after = canvasPointAt(c, fx, fy)

            assertTrue(
                abs(before.first - after.first) < 0.01f && abs(before.second - after.second) < 0.01f,
                "focus ($fx, $fy) drifted from $before to $after",
            )
        }
    }

    @Test
    fun `zooming out keeps the focus too`() {
        val c = controller()
        c.setZoom(8f)
        c.setPan(0f, 0f)

        val before = canvasPointAt(c, 700f, 100f)
        c.zoomAround(0.4f, 700f, 100f, viewW, viewH)
        val after = canvasPointAt(c, 700f, 100f)

        assertTrue(
            abs(before.first - after.first) < 0.01f && abs(before.second - after.second) < 0.01f,
            "drifted from $before to $after",
        )
    }

    @Test
    fun `zoom stays inside its limits`() {
        val c = controller()

        repeat(40) { c.zoomAround(2f, 400f, 300f, viewW, viewH) }
        assertEquals(EditorController.MAX_ZOOM, c.current.zoom)

        repeat(80) { c.zoomAround(0.5f, 400f, 300f, viewW, viewH) }
        assertEquals(EditorController.MIN_ZOOM, c.current.zoom)
    }

    @Test
    fun `zooming at the limit changes nothing at all`() {
        val c = controller()
        c.setZoom(EditorController.MAX_ZOOM)
        val pan = c.current.panX to c.current.panY

        c.zoomAround(2f, 123f, 456f, viewW, viewH)

        assertEquals(EditorController.MAX_ZOOM, c.current.zoom)
        assertEquals(pan, c.current.panX to c.current.panY, "a no-op zoom must not shift the view")
    }

    @Test
    fun `zooming without a viewport still zooms`() {
        val c = controller()
        c.setZoom(2f)
        c.zoomAround(2f, 0f, 0f, 0f, 0f)
        assertEquals(4f, c.current.zoom)
    }

    @Test
    fun `a zoom that would strand the canvas is clamped back`() {
        val c = controller()
        c.setZoom(1f)
        c.setPan(0f, 0f)

        // Zoom hard onto the very corner of the viewport, repeatedly - the
        // case that would otherwise walk the canvas off the screen.
        repeat(12) { c.zoomAround(1.6f, 0f, 0f, viewW, viewH) }

        val (xs, ys) = canvasSpan(c)
        assertTrue(xs.endInclusive > 0f && xs.start < viewW, "horizontally stranded: $xs")
        assertTrue(ys.endInclusive > 0f && ys.start < viewH, "vertically stranded: $ys")
    }
}
