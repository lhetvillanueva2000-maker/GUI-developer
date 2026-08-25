package com.mcguidesigner.styles.paint

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/**
 * Desktop: a Skia raster bitmap.
 *
 * The colour type is BGRA rather than RGBA, which looks wrong and is not. An
 * 0xAARRGGBB Int written to memory on a little-endian machine lands as the
 * bytes B, G, R, A - so BGRA_8888 reads exactly what the engine wrote, with no
 * per-pixel swizzle. Getting this backwards produces an image with red and blue
 * exchanged, which is the single most common bug in code that hands raw pixels
 * to Skia.
 *
 * UNPREMUL because the engine stores straight alpha; telling Skia the pixels
 * are premultiplied when they are not makes everything translucent glow.
 */
actual class PaintSurface actual constructor(
    actual val width: Int,
    actual val height: Int,
) {
    private val info = ImageInfo(
        width = maxOf(1, width),
        height = maxOf(1, height),
        colorType = ColorType.BGRA_8888,
        alphaType = ColorAlphaType.UNPREMUL,
    )

    private var bitmap: Bitmap? = Bitmap().apply { allocPixels(info) }
    private var bytes = ByteArray(maxOf(1, width) * maxOf(1, height) * 4)

    actual fun update(pixels: IntArray) {
        val target = bitmap ?: return
        if (pixels.size != width * height) return
        var b = 0
        for (p in pixels) {
            bytes[b] = (p and 0xFF).toByte()
            bytes[b + 1] = ((p ushr 8) and 0xFF).toByte()
            bytes[b + 2] = ((p ushr 16) and 0xFF).toByte()
            bytes[b + 3] = ((p ushr 24) and 0xFF).toByte()
            b += 4
        }
        target.installPixels(info, bytes, info.minRowBytes)
    }

    actual fun image(): ImageBitmap = (bitmap ?: Bitmap().apply { allocPixels(info) }).asComposeImageBitmap()

    actual fun dispose() {
        bitmap?.close()
        bitmap = null
        bytes = ByteArray(0)
    }
}
