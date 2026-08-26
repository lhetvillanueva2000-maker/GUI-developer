package com.mcguidesigner.core.paint

import com.mcguidesigner.core.paint.Pixels.alpha
import com.mcguidesigner.core.paint.Pixels.argb
import com.mcguidesigner.core.paint.Pixels.blue
import com.mcguidesigner.core.paint.Pixels.green
import com.mcguidesigner.core.paint.Pixels.mul
import com.mcguidesigner.core.paint.Pixels.red
import com.mcguidesigner.core.paint.Pixels.withAlpha
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What a stroke's coverage *means* - the half the stroke engine deliberately
 * does not know.
 *
 * Painting and erasing are the same geometry and opposite arithmetic, and
 * writing them as one file makes it hard for the eraser to quietly become the
 * worse of the two.
 */
object PaintOps {

    /**
     * Lays [colour] onto [layer] wherever the stroke covered.
     *
     * `opacity` is the stroke ceiling: coverage is scaled by it once, here,
     * rather than per dab, which is what makes a translucent stroke translucent
     * along its whole length instead of only at the ends.
     */
    fun paint(layer: PaintLayer, engine: StrokeEngine, colour: Int, opacity: Float) {
        if (layer.locked) {
            engine.clear()
            return
        }
        val ceiling = (opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        val alphaLocked = layer.alphaLocked
        val r = red(colour)
        val g = green(colour)
        val b = blue(colour)

        engine.finish { index, coverage ->
            layer.pixels[index] = paintedPixel(layer.pixels[index], coverage, colour, ceiling, alphaLocked)
        }
    }

    /**
     * One pixel of a brush stroke, from what was there before it started.
     *
     * Public and pure because the live preview needs exactly this and must not
     * reimplement it. A preview that computes its pixels even slightly
     * differently from the committed stroke shows one thing while the finger is
     * down and another the instant it lifts, which reads as the app losing the
     * stroke and redrawing it wrong.
     *
     * [original] must be the value from *before* the stroke, not the current
     * one - see [UndoStack.originalAt].
     */
    fun paintedPixel(original: Int, coverage: Int, colour: Int, ceiling: Int, alphaLocked: Boolean): Int {
        val a = mul(coverage, ceiling)
        if (a == 0) return original
        if (alphaLocked && alpha(original) == 0) return original
        val out = sourceOver(original, red(colour), green(colour), blue(colour), a)
        return if (alphaLocked) withAlpha(out, alpha(original)) else out
    }

    /** [paintedPixel]'s counterpart: alpha comes off, colour stays. */
    fun erasedPixel(original: Int, coverage: Int, ceiling: Int): Int {
        val bite = mul(coverage, ceiling)
        if (bite == 0) return original
        val existing = alpha(original)
        if (existing == 0) return original
        return withAlpha(original, mul(existing, 255 - bite))
    }

    /**
     * Removes coverage from [layer].
     *
     * Alpha only. The colour channels are left exactly as they were, so a
     * stroke erased down to 10% and painted back over is still its own colour
     * rather than a grey ghost of it. That is the whole reason the engine
     * stores straight rather than premultiplied alpha, and it is what separates
     * an eraser you can use as a shading tool from one that only deletes.
     */
    fun erase(layer: PaintLayer, engine: StrokeEngine, opacity: Float) {
        if (layer.locked) {
            engine.clear()
            return
        }
        val ceiling = (opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        engine.finish { index, coverage ->
            layer.pixels[index] = erasedPixel(layer.pixels[index], coverage, ceiling)
        }
    }

    /**
     * The blurred value at one pixel, read out of a region snapshot.
     *
     * Pure, and reading from a *copy* rather than from the layer, which is the
     * whole trick. Blurring in place means the second pixel is averaged from an
     * already-blurred first one, so the effect drags along the scan order and a
     * stroke smears sideways instead of softening.
     *
     * Neighbours are weighted by their alpha. Without that, an erased pixel
     * drags the colour towards whatever happens to be stored underneath it -
     * which is arbitrary, since erasing only lowers alpha - and blurring near a
     * hole pulls in a colour that was never visible.
     */
    fun blurredPixel(
        source: IntArray,
        regionLeft: Int,
        regionTop: Int,
        regionWidth: Int,
        regionHeight: Int,
        x: Int,
        y: Int,
        radius: Int,
    ): Int {
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        var n = 0
        for (dy in -radius..radius) {
            val sy = y + dy - regionTop
            if (sy < 0 || sy >= regionHeight) continue
            for (dx in -radius..radius) {
                val sx = x + dx - regionLeft
                if (sx < 0 || sx >= regionWidth) continue
                val p = source[sy * regionWidth + sx]
                val pa = alpha(p)
                a += pa
                r += red(p) * pa
                g += green(p) * pa
                b += blue(p) * pa
                n++
            }
        }
        if (n == 0) return Pixels.TRANSPARENT
        val averageAlpha = a / n
        if (a == 0) return Pixels.TRANSPARENT
        return argb(averageAlpha, r / a, g / a, b / a)
    }

    /**
     * Drags colour along a path.
     *
     * The classic smudge: carry a small colour reservoir, and at every step
     * pull it towards what is under the brush while pushing what it is carrying
     * back onto the canvas. [strength] is how much of the reservoir survives
     * each step - high values smear a long way, low values barely blend.
     *
     * Applied along the path in order rather than over a bounding box, because
     * the direction of the drag *is* the direction the finger moved, and it is
     * inherently iterative: unlike every other tool here, the result cannot be
     * recomputed from the pre-stroke pixels, so it is applied incrementally and
     * the undo tiles are what make it reversible.
     *
     * Returns the reservoir, so a caller applying the path in segments can
     * carry it into the next one.
     */
    fun smudge(
        layer: PaintLayer,
        path: List<StrokePoint>,
        radius: Int,
        strength: Float,
        carried: Int? = null,
    ): Int {
        if (layer.locked || path.isEmpty()) return carried ?: Pixels.TRANSPARENT
        val carry = strength.coerceIn(0f, 0.98f)
        val amount8 = (carry * 255f).roundToInt()
        var reservoir = carried ?: layer[path.first().x.roundToInt(), path.first().y.roundToInt()]
        val r2 = radius * radius

        for (point in path) {
            val cx = point.x.roundToInt()
            val cy = point.y.roundToInt()
            reservoir = mixPixels(layer[cx, cy], reservoir, amount8)

            for (dy in -radius..radius) {
                val y = cy + dy
                if (y < 0 || y >= layer.height) continue
                for (dx in -radius..radius) {
                    val x = cx + dx
                    if (x < 0 || x >= layer.width) continue
                    val distance = dx * dx + dy * dy
                    if (distance > r2) continue
                    // Soft falloff, so the smudge has no hard rim.
                    val falloff = 1f - (Pixels.isqrt(distance).toFloat() / radius.coerceAtLeast(1))
                    val strengthHere = (falloff * falloff * carry * 255f).roundToInt().coerceIn(0, 255)
                    if (strengthHere == 0) continue
                    val index = y * layer.width + x
                    layer.pixels[index] = mixPixels(layer.pixels[index], reservoir, strengthHere)
                }
            }
        }
        return reservoir
    }

    /** Linear blend of two straight-alpha pixels; [t] is 0..255 towards [b]. */
    fun mixPixels(a: Int, b: Int, t: Int): Int = argb(
        Pixels.lerp8(alpha(a), alpha(b), t),
        Pixels.lerp8(red(a), red(b), t),
        Pixels.lerp8(green(a), green(b), t),
        Pixels.lerp8(blue(a), blue(b), t),
    )

    /** Straight-alpha source-over of one opaque colour at alpha [a]. */
    private fun sourceOver(dst: Int, r: Int, g: Int, b: Int, a: Int): Int {
        val da = alpha(dst)
        if (da == 0) return argb(a, r, g, b)
        if (a == 255) return argb(255, r, g, b)
        val outA = a + mul(da, 255 - a)
        if (outA == 0) return Pixels.TRANSPARENT
        fun ch(dc: Int, sc: Int): Int = ((mul(sc, a) + mul(mul(dc, da), 255 - a)) * 255) / outA
        return argb(
            outA,
            ch(red(dst), r).coerceIn(0, 255),
            ch(green(dst), g).coerceIn(0, 255),
            ch(blue(dst), b).coerceIn(0, 255),
        )
    }

    /**
     * The colour under a point, as seen rather than as stored.
     *
     * Two modes, and both are wanted often enough that offering one is a
     * complaint waiting to happen: the active layer alone, or everything
     * composited, which is what the eye is actually looking at.
     */
    fun pick(document: PaintDocument, x: Int, y: Int, wholeImage: Boolean, flattened: IntArray?): Int {
        if (x < 0 || y < 0 || x >= document.width || y >= document.height) return Pixels.TRANSPARENT
        val index = y * document.width + x
        if (wholeImage && flattened != null && flattened.size == document.width * document.height) {
            return flattened[index]
        }
        return document.active?.pixels?.get(index) ?: Pixels.TRANSPARENT
    }
}

/**
 * Region selection by colour: the bucket, the magic wand, and the magic eraser
 * all ask this the same question and do different things with the answer.
 *
 * Returns a coverage mask rather than a hard boolean set. The difference is
 * everything: a boolean region has a stair-stepped edge that no amount of
 * "expand by one" fixes, while a coverage mask can carry the partial pixels
 * along a photographic edge and produce a cut that does not look cut out.
 */
object RegionFill {

    /**
     * Flood from [startX],[startY] over pixels within [tolerance].
     *
     * [tolerance] is 0..255 in the perceptual-ish units of [Pixels.distance].
     *
     * The fill is a scanline flood - it swallows whole horizontal runs at a
     * time rather than pushing four neighbours per pixel - which on a large
     * flat area is the difference between instant and a visible pause, and
     * bounds the working stack at the number of *runs* rather than the number
     * of pixels. A pixel-at-a-time flood on a 12-megapixel image is also how
     * you overflow a stack.
     *
     * [contiguous] false compares against the seed colour everywhere at once,
     * which is what "select all the white" means as opposed to "select this
     * patch of white".
     */
    fun flood(
        source: IntArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        tolerance: Int,
        contiguous: Boolean = true,
        /** Softness of the boundary, in pixels. 0 is a hard edge. */
        feather: Int = 0,
    ): ByteArray {
        val mask = ByteArray(width * height)
        if (startX < 0 || startY < 0 || startX >= width || startY >= height) return mask
        val seed = source[startY * width + startX]
        val limit = tolerance.coerceIn(0, 442)

        if (!contiguous) {
            for (i in source.indices) {
                mask[i] = coverageFor(Pixels.distanceWithAlpha(source[i], seed), limit)
            }
            return if (feather > 0) feather(mask, width, height, feather) else mask
        }

        // Scanline flood. Each entry is one row segment still to expand from.
        val stack = ArrayDeque<Int>()
        stack.addLast(startY * width + startX)
        val seen = BooleanArray(width * height)

        while (stack.isNotEmpty()) {
            val index = stack.removeLast()
            val y = index / width
            var left = index % width

            if (seen[index]) continue

            // Walk left and right to the ends of the matching run.
            while (left > 0 && !seen[y * width + left - 1] &&
                within(source[y * width + left - 1], seed, limit)
            ) left--
            var right = index % width
            while (right < width - 1 && !seen[y * width + right + 1] &&
                within(source[y * width + right + 1], seed, limit)
            ) right++

            for (x in left..right) {
                val i = y * width + x
                if (seen[i]) continue
                seen[i] = true
                mask[i] = coverageFor(Pixels.distanceWithAlpha(source[i], seed), limit)
            }

            // Seed the rows above and below, once per run of matches rather
            // than once per pixel.
            for (dy in intArrayOf(-1, 1)) {
                val ny = y + dy
                if (ny < 0 || ny >= height) continue
                var x = left
                while (x <= right) {
                    val i = ny * width + x
                    if (!seen[i] && within(source[i], seed, limit)) {
                        stack.addLast(i)
                        // Skip to the end of this run; one seed per run is enough.
                        while (x <= right && within(source[ny * width + x], seed, limit)) x++
                    } else {
                        x++
                    }
                }
            }
        }

        return if (feather > 0) feather(mask, width, height, feather) else mask
    }

    private fun within(pixel: Int, seed: Int, limit: Int): Boolean =
        Pixels.distanceWithAlpha(pixel, seed) <= limit

    /**
     * Coverage from distance: full inside, tapering over the last quarter of
     * the tolerance.
     *
     * A hard cut at exactly the tolerance is what makes a bucket fill leave a
     * one-pixel halo of the old colour along every anti-aliased edge - the
     * in-between pixels are just outside the threshold and never get filled.
     * Taking them at partial coverage fills them partially, which is exactly
     * what they were.
     */
    private fun coverageFor(distance: Int, limit: Int): Byte {
        if (limit == 0) return if (distance == 0) 255.toByte() else 0
        if (distance > limit) return 0
        val soft = max(1, limit / 4)
        val start = limit - soft
        if (distance <= start) return 255.toByte()
        val t = (limit - distance).toFloat() / soft
        return (t.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
    }

    /**
     * Blurs the mask's boundary by [radius] pixels.
     *
     * A separable box blur run three times, which is close enough to a Gaussian
     * that nobody can tell and is a few dozen times faster. Only the boundary
     * band is touched: blurring the solid interior would only make the mask
     * slightly smaller and cost the whole area.
     */
    fun feather(mask: ByteArray, width: Int, height: Int, radius: Int): ByteArray {
        if (radius <= 0) return mask
        var current = mask
        repeat(3) {
            current = boxBlur(current, width, height, radius)
        }
        return current
    }

    private fun boxBlur(mask: ByteArray, width: Int, height: Int, radius: Int): ByteArray {
        val temp = ByteArray(mask.size)
        val out = ByteArray(mask.size)
        val span = radius * 2 + 1

        for (y in 0 until height) {
            var sum = 0
            val row = y * width
            for (x in -radius..radius) sum += mask[row + x.coerceIn(0, width - 1)].toInt() and 0xFF
            for (x in 0 until width) {
                temp[row + x] = (sum / span).toByte()
                val out0 = row + (x - radius).coerceIn(0, width - 1)
                val in0 = row + (x + radius + 1).coerceIn(0, width - 1)
                sum += (mask[in0].toInt() and 0xFF) - (mask[out0].toInt() and 0xFF)
            }
        }
        for (x in 0 until width) {
            var sum = 0
            for (y in -radius..radius) sum += temp[y.coerceIn(0, height - 1) * width + x].toInt() and 0xFF
            for (y in 0 until height) {
                out[y * width + x] = (sum / span).toByte()
                val out0 = (y - radius).coerceIn(0, height - 1) * width + x
                val in0 = (y + radius + 1).coerceIn(0, height - 1) * width + x
                sum += (temp[in0].toInt() and 0xFF) - (temp[out0].toInt() and 0xFF)
            }
        }
        return out
    }

    /**
     * Grows (positive) or shrinks (negative) a mask by [amount] pixels.
     *
     * The "Expand / Contract" in the selection menu, and the thing that closes
     * the one-pixel gap a magic wand leaves against a soft edge.
     */
    fun expand(mask: ByteArray, width: Int, height: Int, amount: Int): ByteArray {
        if (amount == 0) return mask
        val grow = amount > 0
        val steps = kotlin.math.abs(amount)
        var current = mask
        repeat(steps) {
            val next = ByteArray(current.size)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val i = y * width + x
                    var best = current[i].toInt() and 0xFF
                    for (d in NEIGHBOURS) {
                        val nx = x + d[0]
                        val ny = y + d[1]
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                        val v = current[ny * width + nx].toInt() and 0xFF
                        best = if (grow) max(best, v) else min(best, v)
                    }
                    next[i] = best.toByte()
                }
            }
            current = next
        }
        return current
    }

    private val NEIGHBOURS = arrayOf(
        intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1),
    )

    /** Fills [layer] with [colour] wherever [mask] has coverage. */
    fun apply(layer: PaintLayer, mask: ByteArray, colour: Int, opacity: Float) {
        if (layer.locked) return
        val ceiling = (opacity.coerceIn(0f, 1f) * 255f).roundToInt()
        val r = red(colour)
        val g = green(colour)
        val b = blue(colour)
        for (i in mask.indices) {
            val c = mask[i].toInt() and 0xFF
            if (c == 0) continue
            val a = mul(c, ceiling)
            if (a == 0) continue
            val dst = layer.pixels[i]
            if (layer.alphaLocked && alpha(dst) == 0) continue
            val painted = blendPixel(dst, argb(a, r, g, b), BlendMode.NORMAL, 255)
            layer.pixels[i] = if (layer.alphaLocked) withAlpha(painted, alpha(dst)) else painted
        }
    }
}
