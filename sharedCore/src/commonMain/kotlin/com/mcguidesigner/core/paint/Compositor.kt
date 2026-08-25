package com.mcguidesigner.core.paint

/**
 * Flattens a stack of layers into one buffer.
 *
 * There is exactly one of these and both the on-screen canvas and the PNG
 * export go through it, so what is saved is what was seen. Two compositors -
 * one "fast, for the screen" and one "correct, for export" - is how a paint app
 * ends up with a preview that does not match its output, and the difference
 * only ever shows up in the finished file.
 */
object Compositor {

    /**
     * Composites [document] into [into], allocating if it is the wrong size.
     *
     * Returns the buffer written to, so callers can hold onto it across frames
     * and avoid allocating a few megabytes on every repaint.
     */
    fun flatten(document: PaintDocument, into: IntArray? = null): IntArray {
        val size = document.width * document.height
        val out = if (into != null && into.size == size) into else IntArray(size)
        out.fill(document.background.colour)
        composite(document, out, 0, 0, document.width - 1, document.height - 1)
        return out
    }

    /**
     * Recomposites only the rectangle a stroke has touched.
     *
     * The reason the canvas can keep up with a finger. A full flatten of a
     * 1500-square document is two and a quarter million pixels through the
     * blend stack, which no phone will do sixty times a second in Kotlin; the
     * area under a brush is a few thousand. Everything outside the rectangle is
     * already correct in [out] from the last time it changed.
     *
     * The rectangle is inclusive on all four edges and clamped to the document,
     * so a caller can hand it a brush's bounding box without checking.
     */
    fun repaint(document: PaintDocument, out: IntArray, left: Int, top: Int, right: Int, bottom: Int) {
        val x0 = left.coerceIn(0, document.width - 1)
        val y0 = top.coerceIn(0, document.height - 1)
        val x1 = right.coerceIn(0, document.width - 1)
        val y1 = bottom.coerceIn(0, document.height - 1)
        if (x1 < x0 || y1 < y0) return
        val background = document.background.colour
        for (y in y0..y1) {
            val row = y * document.width
            out.fill(background, row + x0, row + x1 + 1)
        }
        composite(document, out, x0, y0, x1, y1)
    }

    /**
     * The layer loop, onto whatever is already in [out].
     *
     * Clipping groups are the only part with any subtlety. A clipped layer is
     * visible only where the layer below it has alpha, and a run of clipped
     * layers all clip to the first *unclipped* one under them - not to their
     * immediate neighbour, which would make a stack of three clipped layers
     * mask each other progressively instead of all masking the base.
     */
    private fun composite(
        document: PaintDocument,
        out: IntArray,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ) {
        val layers = document.layers
        val width = document.width
        var index = 0
        while (index < layers.size) {
            val base = layers[index]
            index++

            // Collect the clipped run that belongs to this base layer.
            var runEnd = index
            while (runEnd < layers.size && layers[runEnd].clippedToBelow) runEnd++

            if (runEnd == index) {
                if (base.visible && base.opacity > 0) blendRegion(out, base, width, x0, y0, x1, y1)
            } else {
                // The base plus its clipped children are resolved into a
                // scratch buffer first, masked by the base's alpha, then that
                // whole group is composited as one. The scratch covers only the
                // region, not the document.
                val regionWidth = x1 - x0 + 1
                val group = IntArray(regionWidth * (y1 - y0 + 1))
                for (y in y0..y1) {
                    val src = y * width
                    val dst = (y - y0) * regionWidth
                    for (x in x0..x1) {
                        val p = base.pixels[src + x]
                        group[dst + (x - x0)] =
                            if (base.visible) Pixels.withAlpha(p, Pixels.mul(Pixels.alpha(p), base.opacity))
                            else Pixels.TRANSPARENT
                    }
                }
                for (c in index until runEnd) {
                    val child = layers[c]
                    if (!child.visible || child.opacity == 0) continue
                    for (y in y0..y1) {
                        val src = y * width
                        val dst = (y - y0) * regionWidth
                        for (x in x0..x1) {
                            val mask = Pixels.alpha(base.pixels[src + x])
                            if (mask == 0) continue
                            val cp = child.pixels[src + x]
                            val masked = Pixels.withAlpha(cp, Pixels.mul(Pixels.alpha(cp), mask))
                            val i = dst + (x - x0)
                            group[i] = blendPixel(group[i], masked, child.blendMode, child.opacity)
                        }
                    }
                }
                for (y in y0..y1) {
                    val dst = y * width
                    val src = (y - y0) * regionWidth
                    for (x in x0..x1) {
                        out[dst + x] = blendPixel(out[dst + x], group[src + (x - x0)], base.blendMode, 255)
                    }
                }
                index = runEnd
            }
        }
    }

    private fun blendRegion(
        out: IntArray,
        layer: PaintLayer,
        width: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
    ) {
        val mode = layer.blendMode
        val scale = layer.opacity
        val src = layer.pixels
        val plain = mode == BlendMode.NORMAL && scale == 255
        for (y in y0..y1) {
            val row = y * width
            for (x in x0..x1) {
                val i = row + x
                val s = src[i]
                val a = s ushr 24
                if (a == 0) continue
                out[i] = when {
                    // The overwhelmingly common case: an opaque pixel on a
                    // normal, full-opacity layer is just itself.
                    plain && a == 255 -> s
                    plain -> blendPixel(out[i], s, BlendMode.NORMAL, 255)
                    else -> blendPixel(out[i], s, mode, scale)
                }
            }
        }
    }

    /**
     * The flattened result with the background left transparent.
     *
     * What "Export PNG with transparency" needs. Not the same as flattening
     * onto transparent and hoping - the background is a real layer of the
     * document and has to be skipped deliberately.
     */
    fun flattenTransparent(document: PaintDocument, into: IntArray? = null): IntArray {
        val size = document.width * document.height
        val out = if (into != null && into.size == size) into else IntArray(size)
        out.fill(Pixels.TRANSPARENT)
        composite(document, out, 0, 0, document.width - 1, document.height - 1)
        return out
    }
}
