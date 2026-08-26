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

    /**
     * The byte mirror of the canvas, kept between updates.
     *
     * Skia's `installPixels` takes a whole buffer, so a partial update writes
     * only the changed rows into this and reinstalls. Rebuilding all of it per
     * frame - which the first version did - is nine megabytes of conversion per
     * frame on a large canvas.
     */
    private var bytes = ByteArray(maxOf(1, width) * maxOf(1, height) * 4)

    actual fun update(pixels: IntArray) {
        val target = bitmap ?: return
        if (pixels.size != width * height) return
        convert(pixels, 0, pixels.size)
        target.installPixels(info, bytes, info.minRowBytes)
    }

    actual fun updateRegion(pixels: IntArray, x: Int, y: Int, width: Int, height: Int) {
        val target = bitmap ?: return
        if (pixels.size != this.width * this.height) return
        val x0 = x.coerceIn(0, this.width - 1)
        val y0 = y.coerceIn(0, this.height - 1)
        val w = width.coerceIn(1, this.width - x0)
        val h = height.coerceIn(1, this.height - y0)
        for (row in 0 until h) {
            val start = (y0 + row) * this.width + x0
            convert(pixels, start, start + w)
        }
        target.installPixels(info, bytes, info.minRowBytes)
    }

    /** Writes `pixels[from until to]` into [bytes] at the matching offsets. */
    private fun convert(pixels: IntArray, from: Int, to: Int) {
        var b = from * 4
        for (i in from until to) {
            val p = pixels[i]
            bytes[b] = (p and 0xFF).toByte()
            bytes[b + 1] = ((p ushr 8) and 0xFF).toByte()
            bytes[b + 2] = ((p ushr 16) and 0xFF).toByte()
            bytes[b + 3] = ((p ushr 24) and 0xFF).toByte()
            b += 4
        }
    }

    actual fun image(): ImageBitmap = (bitmap ?: Bitmap().apply { allocPixels(info) }).asComposeImageBitmap()

    actual fun dispose() {
        bitmap?.close()
        bitmap = null
        bytes = ByteArray(0)
    }
}
