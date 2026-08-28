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
    /**
     * Null when there is no real Android graphics stack underneath.
     *
     * Every method below already tolerates that, because a disposed surface is
     * the same situation; the constructor was the only place that could not,
     * and it threw. That mattered in exactly one place and it was the wrong
     * place to lose: a plain JVM unit test, where `Bitmap.createBitmap` is an
     * unimplemented stub, so any test that drove a stroke died on the first
     * frame. The paint engine is arithmetic and deserves to be testable without
     * a device attached.
     */
    private var bitmap: Bitmap? = runCatching {
        Bitmap.createBitmap(maxOf(1, width), maxOf(1, height), Bitmap.Config.ARGB_8888)
    }.getOrNull()

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
        destX: Int,
        destY: Int,
        width: Int,
        height: Int,
    ) {
        val target = bitmap ?: return
        if (sourceX < 0 || sourceY < 0 || destX < 0 || destY < 0) return
        val w = width.coerceAtMost(this.width - destX)
        val h = height.coerceAtMost(this.height - destY)
        if (w <= 0 || h <= 0) return
        if ((sourceY + h - 1).toLong() * sourceStride + sourceX + w > pixels.size) return
        target.setPixels(pixels, sourceY * sourceStride + sourceX, sourceStride, destX, destY, w, h)
    }

    actual fun image(): ImageBitmap {
        wrapped?.let { return it }
        val target = checkNotNull(bitmap) {
            "This PaintSurface has no bitmap: either it was disposed, or there " +
                "is no Android graphics stack here. Nothing should be asking a " +
                "disposed surface to draw."
        }
        return target.asImageBitmap().also { wrapped = it }
    }

    actual fun dispose() {
        wrapped = null
        bitmap?.recycle()
        bitmap = null
    }
}
