package com.mcguidesigner.core.paint

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * "Scribble over the thing you want gone."
 *
 * The magic eraser used to be a tap: one seed, one flood, one region. That is
 * fine for a flat colour and useless for an object, because an object is not
 * one colour - a face is a dozen, a leaf is a gradient - and tapping it removes
 * one of them and leaves the rest. So the gesture is a drag, and everything the
 * drag passes over is taken as "this, and things like this".
 *
 * The implementation is a **multi-seed flood**, not a flood per point. Running
 * a separate fill from each of a few hundred path points would re-walk the same
 * region hundreds of times; instead every point contributes its colour to a
 * small set, and one scanline pass takes any pixel close to *any* of them.
 * The cost is one flood regardless of how long the scribble is.
 *
 * The band under the scribble itself is always taken, whatever its colour. If
 * somebody deliberately dragged across a pixel, they meant that pixel - being
 * clever about it is how a tool ends up arguing with the person using it.
 */
object ScribbleSelection {

    /** How many distinct colours the scribble is reduced to. */
    private const val MAX_SEEDS = 12

    /**
     * The mask for everything [path] passed over.
     *
     * [radius] is the width of the scribble, in pixels: the band under the
     * stroke that is taken unconditionally. [tolerance] is how far a pixel may
     * be from a seed colour and still count. [contiguous] restricts the result
     * to regions the scribble actually reaches, which is almost always what is
     * wanted - the alternative removes every similarly-coloured thing in the
     * picture, which is occasionally exactly right and usually a surprise.
     */
    fun select(
        source: IntArray,
        width: Int,
        height: Int,
        path: List<StrokePoint>,
        radius: Int,
        tolerance: Int,
        contiguous: Boolean = true,
        feather: Int = 2,
    ): ByteArray {
        val mask = ByteArray(width * height)
        if (path.isEmpty() || width <= 0 || height <= 0) return mask

        // 1. The band under the scribble. Unconditional.
        val seeds = ArrayList<Int>()
        val r2 = radius * radius
        for (point in path) {
            val cx = point.x.roundToInt()
            val cy = point.y.roundToInt()
            if (cx < 0 || cy < 0 || cx >= width || cy >= height) continue
            seeds.add(source[cy * width + cx])
            for (dy in -radius..radius) {
                val y = cy + dy
                if (y < 0 || y >= height) continue
                for (dx in -radius..radius) {
                    val x = cx + dx
                    if (x < 0 || x >= width) continue
                    if (dx * dx + dy * dy > r2) continue
                    mask[y * width + x] = 255.toByte()
                }
            }
        }
        if (seeds.isEmpty()) return mask

        // 2. Reduce the colours the scribble crossed to a handful.
        val palette = reduce(seeds)

        // 3. One flood, seeded everywhere the band already covers.
        if (contiguous) {
            floodFromMask(source, width, height, mask, palette, tolerance)
        } else {
            for (i in source.indices) {
                if (mask[i].toInt() != 0) continue
                if (near(source[i], palette, tolerance)) mask[i] = 255.toByte()
            }
        }

        return if (feather > 0) RegionFill.feather(mask, width, height, feather) else mask
    }

    /**
     * Reduces the scribbled-over colours to at most [MAX_SEEDS] representatives.
     *
     * Keeping every sampled pixel would mean a few hundred comparisons per
     * canvas pixel. Most of them are near-duplicates anyway - a scribble across
     * one object crosses a handful of real colours and a lot of noise around
     * them - so anything close to a colour already held is folded into it.
     */
    private fun reduce(samples: List<Int>): IntArray {
        val kept = ArrayList<Int>(MAX_SEEDS)
        for (sample in samples) {
            var merged = false
            for (existing in kept) {
                if (Pixels.distance(existing, sample) < 24) {
                    merged = true
                    break
                }
            }
            if (!merged) {
                kept.add(sample)
                if (kept.size >= MAX_SEEDS) break
            }
        }
        return kept.toIntArray()
    }

    private fun near(pixel: Int, palette: IntArray, tolerance: Int): Boolean {
        // Alpha counts: an erased hole is not the same as an opaque pixel that
        // happens to share its stored colour, and a selection that cannot tell
        // them apart spreads through every gap in the layer.
        for (colour in palette) {
            if (Pixels.alpha(pixel) == 0 && Pixels.alpha(colour) != 0) continue
            if (Pixels.distance(pixel, colour) <= tolerance) return true
        }
        return false
    }

    /**
     * Grows the mask outward from everything already in it.
     *
     * A scanline flood whose starting frontier is the whole band rather than a
     * single seed - which is the trick that makes one pass do the work of one
     * flood per path point.
     */
    private fun floodFromMask(
        source: IntArray,
        width: Int,
        height: Int,
        mask: ByteArray,
        palette: IntArray,
        tolerance: Int,
    ) {
        val queue = ArrayDeque<Int>()
        val seen = BooleanArray(width * height)
        for (i in mask.indices) {
            if (mask[i].toInt() != 0) {
                seen[i] = true
                queue.addLast(i)
            }
        }

        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            val x = index % width
            val y = index / width
            for (d in NEIGHBOURS) {
                val nx = x + d[0]
                val ny = y + d[1]
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                val j = ny * width + nx
                if (seen[j]) continue
                if (!near(source[j], palette, tolerance)) continue
                seen[j] = true
                mask[j] = 255.toByte()
                queue.addLast(j)
            }
        }
    }

    private val NEIGHBOURS = arrayOf(
        intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1),
    )

    /** The rectangle [mask] covers, or null when it covers nothing. */
    fun boundsOf(mask: ByteArray, width: Int, height: Int): IntArray? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (mask[row + x].toInt() == 0) continue
                left = min(left, x)
                top = min(top, y)
                right = max(right, x)
                bottom = max(bottom, y)
            }
        }
        return if (right < left) null else intArrayOf(left, top, right, bottom)
    }

    /** Removes [mask] from [layer], scaled by [strength]. */
    fun erase(layer: PaintLayer, mask: ByteArray, strength: Float = 1f): Boolean {
        if (layer.locked) return false
        val ceiling = (strength.coerceIn(0f, 1f) * 255f).roundToInt()
        var touched = false
        for (i in mask.indices) {
            val m = Pixels.mul(mask[i].toInt() and 0xFF, ceiling)
            if (m == 0) continue
            val existing = Pixels.alpha(layer.pixels[i])
            if (existing == 0) continue
            layer.pixels[i] = Pixels.withAlpha(layer.pixels[i], Pixels.mul(existing, 255 - m))
            touched = true
        }
        return touched
    }
}
