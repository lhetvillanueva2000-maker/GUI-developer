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
     * Copies only the given rectangle in.
     *
     * The reason the canvas can keep up with a finger. A 1536-square document
     * is 2.36 million pixels, and copying all of them into the native bitmap
     * sixty times a second is nine megabytes a frame - over half a gigabyte a
     * second of pure memory traffic, which is what made drawing feel like
     * wading. The area under a brush is a few thousand pixels.
     *
     * [pixels] is still the whole canvas buffer; only the rectangle is read out
     * of it. The bounds are clamped, so a caller can hand over a brush's
     * bounding box without checking it first.
     */
    fun updateRegion(pixels: IntArray, x: Int, y: Int, width: Int, height: Int)

    /**
     * Copies a window of a *larger* buffer into this surface at [destX], [destY].
     *
     * For the stroke patch: a small surface holding one rectangle cut out of
     * the full canvas buffer. [sourceStride] is the width of the buffer being
     * read, not of the window.
     *
     * The destination offset is what makes the patch incremental. The patch
     * covers everything the stroke has touched, but only a brush-sized piece of
     * that changes per event, so the copy is that piece placed where it belongs
     * rather than the whole rectangle again - which by the middle of a long
     * stroke is a million pixels moved per frame to show a change covering a
     * few thousand.
     */
    fun updateFrom(
        pixels: IntArray,
        sourceStride: Int,
        sourceX: Int,
        sourceY: Int,
        destX: Int,
        destY: Int,
        width: Int,
        height: Int,
    )

    /**
     * The renderer's view of the pixels.
     *
     * Valid until the next [update]; callers should not hold it across frames.
     */
    fun image(): ImageBitmap

    /** Frees the native bitmap. Called when the document closes. */
    fun dispose()
}
