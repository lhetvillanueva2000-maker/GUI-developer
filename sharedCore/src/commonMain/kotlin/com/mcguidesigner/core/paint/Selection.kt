package com.mcguidesigner.core.paint

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How a new selection combines with the one already there. */
enum class SelectMode(val label: String) {
    REPLACE("New"),
    ADD("Add"),
    SUBTRACT("Subtract"),
    INTERSECT("Intersect"),
}

/** The shape a dragged marquee makes. */
enum class MarqueeShape(val label: String) { RECTANGLE("Rectangle"), ELLIPSE("Ellipse") }

/**
 * A region of the canvas that everything else is confined to.
 *
 * Stored as one byte of coverage per pixel rather than a set of rectangles or
 * a path, for the same reason the brush is: the interesting selections in a
 * painting app are not rectangles. A magic wand follows the edge of an object,
 * a lasso follows a hand, and both have soft edges - a boundary that is
 * half-selected is what stops a filled selection from having a staircase down
 * one side. A coverage byte says "how much of this pixel is in", which is the
 * only representation all three can share.
 *
 * The bounds are kept because almost every operation only has to visit the part
 * of the canvas the selection touches, and a lasso around a thumbnail in the
 * corner of a large canvas should not cost a full pass over it.
 */
class PaintSelection(
    val width: Int,
    val height: Int,
    val coverage: ByteArray,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val isEmpty: Boolean get() = right < left || bottom < top

    /** How much of the pixel at [index] is selected, 0..255. */
    fun at(index: Int): Int = if (index in coverage.indices) coverage[index].toInt() and 0xFF else 0

    fun at(x: Int, y: Int): Int =
        if (x < 0 || y < 0 || x >= width || y >= height) 0 else at(y * width + x)

    /** How many pixels are in, counting a half-covered pixel as a half. */
    val area: Float
        get() {
            var total = 0L
            for (y in max(0, top)..min(height - 1, bottom)) {
                val row = y * width
                for (x in max(0, left)..min(width - 1, right)) total += at(row + x)
            }
            return total / 255f
        }

    fun combine(other: PaintSelection, mode: SelectMode): PaintSelection {
        if (mode == SelectMode.REPLACE) return other
        val out = ByteArray(coverage.size)
        for (i in out.indices) {
            val a = at(i)
            val b = other.at(i)
            out[i] = when (mode) {
                SelectMode.ADD -> max(a, b)
                SelectMode.SUBTRACT -> (a * (255 - b) / 255)
                SelectMode.INTERSECT -> (a * b / 255)
                SelectMode.REPLACE -> b
            }.toByte()
        }
        return of(out, width, height)
    }

    /**
     * Everything that was not selected, and nothing that was.
     *
     * The operation people reach for constantly and never think about: select
     * the sky, invert, and you have the thing under the sky - which is far
     * easier than selecting the thing.
     */
    fun invert(): PaintSelection {
        val out = ByteArray(coverage.size)
        for (i in out.indices) out[i] = (255 - at(i)).toByte()
        return of(out, width, height)
    }

    /** Grows (positive) or shrinks (negative) the region by [amount] pixels. */
    fun expand(amount: Int): PaintSelection {
        if (amount == 0) return this
        return of(RegionFill.expand(coverage.copyOf(), width, height, amount), width, height)
    }

    /** Softens the boundary by [radius] pixels. */
    fun feather(radius: Int): PaintSelection {
        if (radius <= 0) return this
        return of(RegionFill.feather(coverage.copyOf(), width, height, radius), width, height)
    }

    /**
     * The boundary, as runs rather than pixels.
     *
     * What the marching ants are drawn from. A lasso around a hand has a few
     * thousand boundary pixels and drawing a line segment for each of them is a
     * few thousand draw calls a frame for a dotted outline - so consecutive
     * edge pixels along a row or column are merged into one run first, which
     * for any real selection collapses it by an order of magnitude and for a
     * rectangle collapses it to four.
     *
     * Computed once when the selection changes, never per frame.
     */
    fun outline(): SelectionOutline {
        if (isEmpty) return SelectionOutline(emptyList(), emptyList())
        val horizontal = ArrayList<IntArray>()
        val vertical = ArrayList<IntArray>()

        fun inside(x: Int, y: Int) = at(x, y) >= 128

        // Top and bottom edges, merged along each row.
        for (y in max(0, top)..min(height - 1, bottom)) {
            var topRun = -1
            var bottomRun = -1
            for (x in max(0, left)..min(width - 1, right) + 1) {
                val here = x <= right && x < width && inside(x, y)
                val needsTop = here && !inside(x, y - 1)
                val needsBottom = here && !inside(x, y + 1)
                if (needsTop) {
                    if (topRun < 0) topRun = x
                } else if (topRun >= 0) {
                    horizontal.add(intArrayOf(y, topRun, x))
                    topRun = -1
                }
                if (needsBottom) {
                    if (bottomRun < 0) bottomRun = x
                } else if (bottomRun >= 0) {
                    horizontal.add(intArrayOf(y + 1, bottomRun, x))
                    bottomRun = -1
                }
            }
        }

        // Left and right edges, merged down each column.
        for (x in max(0, left)..min(width - 1, right)) {
            var leftRun = -1
            var rightRun = -1
            for (y in max(0, top)..min(height - 1, bottom) + 1) {
                val here = y <= bottom && y < height && inside(x, y)
                val needsLeft = here && !inside(x - 1, y)
                val needsRight = here && !inside(x + 1, y)
                if (needsLeft) {
                    if (leftRun < 0) leftRun = y
                } else if (leftRun >= 0) {
                    vertical.add(intArrayOf(x, leftRun, y))
                    leftRun = -1
                }
                if (needsRight) {
                    if (rightRun < 0) rightRun = y
                } else if (rightRun >= 0) {
                    vertical.add(intArrayOf(x + 1, rightRun, y))
                    rightRun = -1
                }
            }
        }
        return SelectionOutline(horizontal, vertical)
    }

    companion object {
        /** Wraps a coverage mask, measuring its bounds. Null when nothing is in. */
        fun of(mask: ByteArray, width: Int, height: Int): PaintSelection {
            var left = width
            var top = height
            var right = -1
            var bottom = -1
            for (y in 0 until height) {
                val row = y * width
                for (x in 0 until width) {
                    if (mask[row + x].toInt() == 0) continue
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
            return PaintSelection(width, height, mask, left, top, right, bottom)
        }

        fun all(width: Int, height: Int): PaintSelection =
            PaintSelection(width, height, ByteArray(width * height) { 255.toByte() }, 0, 0, width - 1, height - 1)

        /** A dragged rectangle or ellipse. */
        fun marquee(
            width: Int,
            height: Int,
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            shape: MarqueeShape,
            feather: Int = 0,
        ): PaintSelection {
            val mask = ByteArray(width * height)
            val minX = min(x0, x1).roundToInt().coerceIn(0, width - 1)
            val maxX = max(x0, x1).roundToInt().coerceIn(0, width - 1)
            val minY = min(y0, y1).roundToInt().coerceIn(0, height - 1)
            val maxY = max(y0, y1).roundToInt().coerceIn(0, height - 1)
            val cx = (minX + maxX) / 2f
            val cy = (minY + maxY) / 2f
            val rx = ((maxX - minX) / 2f).coerceAtLeast(0.5f)
            val ry = ((maxY - minY) / 2f).coerceAtLeast(0.5f)
            for (y in minY..maxY) {
                val row = y * width
                for (x in minX..maxX) {
                    if (shape == MarqueeShape.ELLIPSE) {
                        val dx = (x - cx) / rx
                        val dy = (y - cy) / ry
                        if (dx * dx + dy * dy > 1f) continue
                    }
                    mask[row + x] = 255.toByte()
                }
            }
            val out = if (feather > 0) RegionFill.feather(mask, width, height, feather) else mask
            return of(out, width, height)
        }

        /**
         * The inside of a freehand loop - the lasso.
         *
         * Scanline polygon fill with the even-odd rule, which is what makes a
         * loop that crosses itself behave the way it looks: the overlap is
         * outside, because it is enclosed an even number of times. The path is
         * closed automatically, so letting go without returning to the start
         * still gives a region rather than nothing - which is the whole reason
         * to draw a lasso by hand instead of clicking corners.
         */
        fun lasso(
            points: List<StrokePoint>,
            width: Int,
            height: Int,
            feather: Int = 0,
        ): PaintSelection {
            val mask = ByteArray(width * height)
            if (points.size < 3) return of(mask, width, height)

            var minY = height
            var maxY = -1
            points.forEach {
                val y = it.y.roundToInt()
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
            minY = minY.coerceIn(0, height - 1)
            maxY = maxY.coerceIn(0, height - 1)

            val crossings = ArrayList<Float>()
            for (y in minY..maxY) {
                crossings.clear()
                val scan = y + 0.5f
                for (i in points.indices) {
                    val a = points[i]
                    val b = points[(i + 1) % points.size]
                    // A vertex exactly on the scanline would otherwise be
                    // counted twice, which flips the inside and the outside for
                    // the rest of the row.
                    if ((a.y <= scan && b.y > scan) || (b.y <= scan && a.y > scan)) {
                        val t = (scan - a.y) / (b.y - a.y)
                        crossings.add(a.x + t * (b.x - a.x))
                    }
                }
                if (crossings.size < 2) continue
                crossings.sort()
                var i = 0
                while (i + 1 < crossings.size) {
                    val from = crossings[i].roundToInt().coerceIn(0, width - 1)
                    val to = crossings[i + 1].roundToInt().coerceIn(0, width - 1)
                    val row = y * width
                    for (x in from..to) mask[row + x] = 255.toByte()
                    i += 2
                }
            }
            val out = if (feather > 0) RegionFill.feather(mask, width, height, feather) else mask
            return of(out, width, height)
        }

        /**
         * Everything near the colour under a point - the magic wand.
         *
         * The same flood the bucket uses, which is the point: "what would the
         * bucket fill" and "what does the wand select" have to be the same
         * question or the two tools contradict each other. Tolerance and the
         * contiguous switch come from the same controls too.
         */
        fun wand(
            source: IntArray,
            width: Int,
            height: Int,
            x: Int,
            y: Int,
            tolerance: Int,
            contiguous: Boolean,
            feather: Int,
        ): PaintSelection = of(
            RegionFill.flood(source, width, height, x, y, tolerance, contiguous, feather),
            width,
            height,
        )
    }
}

/**
 * A selection's boundary as merged runs, ready to draw.
 *
 * Each horizontal entry is `[y, x0, x1]` - a line along the top of row `y` from
 * `x0` to `x1`; each vertical is `[x, y0, y1]`. Canvas coordinates, so the
 * caller scales them by whatever the view is showing.
 */
class SelectionOutline(val horizontal: List<IntArray>, val vertical: List<IntArray>) {
    val isEmpty: Boolean get() = horizontal.isEmpty() && vertical.isEmpty()
    val segmentCount: Int get() = horizontal.size + vertical.size
}
