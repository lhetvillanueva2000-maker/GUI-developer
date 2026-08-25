package com.mcguidesigner.styles.paint

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Android: an ARGB_8888 bitmap, which is already the engine's pixel format.
 *
 * `setPixels` takes 0xAARRGGBB ints directly, so there is no channel shuffling
 * and no premultiply step - `asImageBitmap` wraps the same bitmap rather than
 * copying it, so an update is one memcpy into memory the GPU uploads from.
 */
actual class PaintSurface actual constructor(
    actual val width: Int,
    actual val height: Int,
) {
    private var bitmap: Bitmap? =
        Bitmap.createBitmap(maxOf(1, width), maxOf(1, height), Bitmap.Config.ARGB_8888)

    private var wrapped: ImageBitmap? = null

    actual fun update(pixels: IntArray) {
        val target = bitmap ?: return
        if (pixels.size != width * height) return
        target.setPixels(pixels, 0, width, 0, 0, width, height)
        // Re-wrap so Compose treats it as new content. Wrapping is cheap - it
        // does not copy - and skipping it makes the canvas appear frozen on
        // some devices, where the previous ImageBitmap's upload is cached.
        wrapped = target.asImageBitmap()
    }

    actual fun image(): ImageBitmap =
        wrapped ?: bitmap!!.asImageBitmap().also { wrapped = it }

    actual fun dispose() {
        wrapped = null
        bitmap?.recycle()
        bitmap = null
    }
}
