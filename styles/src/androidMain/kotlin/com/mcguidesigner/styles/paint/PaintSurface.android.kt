package com.mcguidesigner.styles.paint

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Android: an ARGB_8888 bitmap, which is already the engine's pixel format.
 *
 * `setPixels` takes 0xAARRGGBB ints directly, so there is no channel shuffling
 * and no premultiply step, and `asImageBitmap` wraps the same bitmap rather
 * than copying it.
 *
 * The wrapper is created once and kept. An earlier version rebuilt it on every
 * update out of caution about stale uploads; it is not needed - Compose's
 * `drawImage` reads the underlying bitmap's live pixels each time it paints -
 * and allocating a wrapper per frame is sixty objects a second of garbage for
 * nothing.
 */
actual class PaintSurface actual constructor(
    actual val width: Int,
    actual val height: Int,
) {
    private var bitmap: Bitmap? =
        Bitmap.createBitmap(maxOf(1, width), maxOf(1, height), Bitmap.Config.ARGB_8888)

    private var wrapped: ImageBitmap? = bitmap?.asImageBitmap()

    actual fun update(pixels: IntArray) {
        val target = bitmap ?: return
        if (pixels.size != width * height) return
        target.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    actual fun updateRegion(pixels: IntArray, x: Int, y: Int, width: Int, height: Int) {
        val target = bitmap ?: return
        if (pixels.size != this.width * this.height) return
        val x0 = x.coerceIn(0, this.width - 1)
        val y0 = y.coerceIn(0, this.height - 1)
        val w = width.coerceIn(1, this.width - x0)
        val h = height.coerceIn(1, this.height - y0)
        // The stride is the *canvas* width, not the rectangle's - setPixels
        // walks the source array a full row at a time and picks the window out
        // of each row. Passing the rectangle's width here instead is the classic
        // way to get a sheared copy.
        target.setPixels(pixels, y0 * this.width + x0, this.width, x0, y0, w, h)
    }

    actual fun updateFrom(
        pixels: IntArray,
        sourceStride: Int,
        sourceX: Int,
        sourceY: Int,
        width: Int,
        height: Int,
    ) {
        val target = bitmap ?: return
        val w = width.coerceIn(1, this.width)
        val h = height.coerceIn(1, this.height)
        if (sourceX < 0 || sourceY < 0) return
        if ((sourceY + h - 1).toLong() * sourceStride + sourceX + w > pixels.size) return
        target.setPixels(pixels, sourceY * sourceStride + sourceX, sourceStride, 0, 0, w, h)
    }

    actual fun image(): ImageBitmap =
        wrapped ?: bitmap!!.asImageBitmap().also { wrapped = it }

    actual fun dispose() {
        wrapped = null
        bitmap?.recycle()
        bitmap = null
    }
}
