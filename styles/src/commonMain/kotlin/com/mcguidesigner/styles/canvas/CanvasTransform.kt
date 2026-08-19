package com.mcguidesigner.styles.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.mcguidesigner.core.model.IntPoint
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.IntSize
import com.mcguidesigner.core.model.ResizeHandle
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
     */
    fun handles(selection: IntRect, handleSize: Float): Map<ResizeHandle, Rect> {
        val r = toView(selection)
        val h = handleSize / 2f
        fun box(x: Float, y: Float) = Rect(x - h, y - h, x + h, y + h)
        return mapOf(
            ResizeHandle.TOP_LEFT to box(r.left, r.top),
            ResizeHandle.TOP to box(r.center.x, r.top),
            ResizeHandle.TOP_RIGHT to box(r.right, r.top),
            ResizeHandle.LEFT to box(r.left, r.center.y),
            ResizeHandle.RIGHT to box(r.right, r.center.y),
            ResizeHandle.BOTTOM_LEFT to box(r.left, r.bottom),
            ResizeHandle.BOTTOM to box(r.center.x, r.bottom),
            ResizeHandle.BOTTOM_RIGHT to box(r.right, r.bottom),
        )
    }

    /** Which handle, if any, is under [point]. */
    fun handleAt(selection: IntRect, point: Offset, handleSize: Float): ResizeHandle? =
        handles(selection, handleSize).entries.firstOrNull { it.value.contains(point) }?.key
}
