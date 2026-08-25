package com.mcguidesigner.styles.paint

import androidx.compose.ui.graphics.ImageBitmap

/**
 * A block of ARGB pixels that can be handed to the renderer without being
 * encoded first.
 *
 * Compose Multiplatform can *read* pixels out of an ImageBitmap in common code
 * and decode one from PNG bytes, but there is no common way to write raw
 * pixels into one. For a canvas that is repainted while a finger is moving,
 * round-tripping through PNG is not a slow path, it is a non-starter: encoding
 * a 1500-square image takes longer than the frame it was meant to be drawn in.
 *
 * So this is the one thing in the paint feature that has to know which platform
 * it is on. Both implementations are a few lines and both wrap a native bitmap
 * that Compose can draw directly, so the pixels go from the compositor's
 * IntArray to the screen with one copy and no format conversion.
 */
expect class PaintSurface(width: Int, height: Int) {
    val width: Int
    val height: Int

    /**
     * Copies [pixels] in, in 0xAARRGGBB order.
     *
     * The array must be exactly `width * height` long. Callers hold one buffer
     * for the life of the document and hand the same one in every frame.
     */
    fun update(pixels: IntArray)

    /**
     * The renderer's view of the pixels.
     *
     * Valid until the next [update]; callers should not hold it across frames.
     */
    fun image(): ImageBitmap

    /** Frees the native bitmap. Called when the document closes. */
    fun dispose()
}
