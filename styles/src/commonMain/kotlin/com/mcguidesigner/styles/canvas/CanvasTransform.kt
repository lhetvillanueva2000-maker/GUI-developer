package com.mcguidesigner.styles.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.mcguidesigner.core.model.IntPoint
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.IntSize
import com.mcguidesigner.core.model.PointF
import com.mcguidesigner.core.model.ResizeHandle
import com.mcguidesigner.core.model.Rotation
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Maps between canvas space (GUI pixels) and view space (device pixels).
 *
 * Both platforms create one of these per frame from the editor state and the
 * measured viewport; every hit-test, gesture and overlay goes through it, so
 * zoom and pan behave identically on desktop and on a phone.
 */
data class CanvasTransform(
    val zoom: Float,
    val panX: Float,
    val panY: Float,
    val viewport: Size,
    val canvas: IntSize,
) {
    val scaledWidth: Float get() = canvas.width * zoom
    val scaledHeight: Float get() = canvas.height * zoom

    /** View-space position of the canvas origin, centred then panned. */
    val originX: Float get() = (viewport.width - scaledWidth) / 2f + panX
    val originY: Float get() = (viewport.height - scaledHeight) / 2f + panY

    fun toView(x: Float, y: Float): Offset = Offset(originX + x * zoom, originY + y * zoom)

    fun toView(point: IntPoint): Offset = toView(point.x.toFloat(), point.y.toFloat())

    fun toView(rect: IntRect): Rect = Rect(
        left = originX + rect.x * zoom,
        top = originY + rect.y * zoom,
        right = originX + rect.right * zoom,
        bottom = originY + rect.bottom * zoom,
    )

    /** Rounds a view-space point down to the containing GUI pixel. */
    fun toCanvas(offset: Offset): IntPoint = IntPoint(
        x = floor((offset.x - originX) / zoom).toInt(),
        y = floor((offset.y - originY) / zoom).toInt(),
    )

    /**
     * The same mapping without the rounding.
     *
     * The editor works in whole GUI pixels and wants [toCanvas]; the demo needs
     * the fraction, because where along a slider a finger landed is the answer
     * to a question that has no integer form.
     */
    fun toCanvasPoint(offset: Offset): Offset =
        Offset((offset.x - originX) / zoom, (offset.y - originY) / zoom)

    /** Converts a view-space delta into whole GUI pixels. */
    fun deltaToCanvas(dx: Float, dy: Float): IntPoint =
        IntPoint((dx / zoom).roundToInt(), (dy / zoom).roundToInt())

    val canvasRect: Rect
        get() = Rect(originX, originY, originX + scaledWidth, originY + scaledHeight)

    /**
     * Handle positions for a selection box, in view space.
     *
     * [handleSize] is in *view* pixels because a resize handle has to stay
     * grabbable at every zoom level - scaling it with the canvas would make it
     * invisible when zoomed out and enormous when zoomed in.
     *
     * [rotation] turns the eight positions about the element's centre, so a
     * turned element's handles sit on the corners it actually has rather than
     * on the corners of the box that would contain it. The handle *boxes*
     * themselves stay square to the screen: they are the target you have to
     * hit, and a hit target that is easy to grab beats one that is drawn at a
     * pleasing angle. The handles are *drawn* turned - see the canvas - so this
     * is only the hit geometry.
     */
    fun handles(selection: IntRect, handleSize: Float, rotation: Int = 0): Map<ResizeHandle, Rect> {
        val h = handleSize / 2f
        return Rotation.handleCentres(selection, rotation).mapValues { (_, point) ->
            val view = toView(point.x, point.y)
            Rect(view.x - h, view.y - h, view.x + h, view.y + h)
        }
    }

    /** Which handle, if any, is under [point]. */
    fun handleAt(
        selection: IntRect,
        point: Offset,
        handleSize: Float,
        rotation: Int = 0,
    ): ResizeHandle? =
        handles(selection, handleSize, rotation).entries
            .firstOrNull { it.value.contains(point) }
            ?.key

    /**
     * Where the rotation knob sits, in view space.
     *
     * Above the element's top edge and turned with it, so it always points out
     * of the same side however far round the element has gone - a knob that
     * jumped to a different edge partway through a turn would be impossible to
     * follow.
     */
    fun rotationKnob(selection: IntRect, rotation: Int, distance: Float): Offset {
        val centre = Rotation.centreOf(selection)
        val top = Rotation.rotate(
            PointF(centre.x, selection.y.toFloat()),
            centre,
            rotation.toFloat(),
        )
        val view = toView(top.x, top.y)
        val pivot = toView(centre.x, centre.y)

        // Pushed out along the same direction in *view* space, so the gap is a
        // constant number of screen pixels rather than shrinking as you zoom
        // out and the element gets smaller.
        val dx = view.x - pivot.x
        val dy = view.y - pivot.y
        val length = kotlin.math.sqrt(dx * dx + dy * dy)
        if (length < 0.01f) return Offset(view.x, view.y - distance)
        return Offset(view.x + dx / length * distance, view.y + dy / length * distance)
    }
}
