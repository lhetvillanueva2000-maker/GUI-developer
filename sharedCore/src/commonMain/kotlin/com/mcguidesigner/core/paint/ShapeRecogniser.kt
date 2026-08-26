package com.mcguidesigner.core.paint

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** What a rough drag turned out to be. */
enum class RecognisedShape(val label: String) {
    /** Nothing confident enough. The stroke is left as drawn. */
    FREEHAND("Freehand"),
    LINE("Line"),
    TRIANGLE("Triangle"),
    RECTANGLE("Rectangle"),
    SQUARE("Square"),
    ELLIPSE("Ellipse"),
    CIRCLE("Circle"),
}

/**
 * The result: what it is, and the tidy geometry to draw instead.
 *
 * [points] is the cleaned outline in canvas coordinates, ready to stroke or
 * fill. For a line it is two points; for a polygon its corners, closed; for an
 * ellipse a fine polygon around it, so every caller draws the same way and
 * nothing needs a special case for curves.
 */
data class ShapeGuess(
    val shape: RecognisedShape,
    val points: List<StrokePoint>,
    val closed: Boolean,
    /** 0..1. Below about 0.5 the caller should keep the freehand stroke. */
    val confidence: Float,
)

/**
 * Turning a rough drag into the shape that was meant.
 *
 * The rule people expect, and the one this follows: *what you drew decides what
 * you get*. Drag a rough box and a rectangle appears; drag a rough box with
 * even sides and it is a square; three strokes-worth of corners is a triangle;
 * a loop is a circle if it is round and an ellipse if it is not. Nothing is
 * chosen from a menu first, which is the whole appeal - a menu means deciding
 * before drawing, and by then you have already drawn it.
 *
 * The method is the classic one, in three steps.
 *
 * 1. **Simplify.** Ramer-Douglas-Peucker throws away every point that is not
 *    doing structural work, at a tolerance proportional to the drawing's own
 *    size, so a big sloppy square and a small neat one simplify alike.
 * 2. **Count corners.** What survives is the corner count, and that is most of
 *    the answer: two is a line, three a triangle, four a quadrilateral.
 * 3. **Test the alternative.** A shape with many surviving corners might be a
 *    circle, so the radius variance about the centroid decides: consistently
 *    equidistant is round, otherwise it stays freehand.
 *
 * Every decision reports a confidence, and a low one means "leave it as drawn".
 * A recogniser that always answers is worse than one that declines, because the
 * failure mode is silently replacing somebody's drawing with a wrong triangle.
 */
object ShapeRecogniser {

    /** Shapes the caller will accept. Everything else stays freehand. */
    fun recognise(
        path: List<StrokePoint>,
        /** Below this, the guess is reported but flagged as weak. */
        minimumConfidence: Float = 0.5f,
    ): ShapeGuess {
        if (path.size < 4) return ShapeGuess(RecognisedShape.FREEHAND, path, false, 0f)

        val bounds = boundsOf(path)
        val width = bounds[2] - bounds[0]
        val height = bounds[3] - bounds[1]
        val diagonal = sqrt(width * width + height * height)
        // Too small to have a shape at all: a tap with a wobble.
        if (diagonal < 12f) return ShapeGuess(RecognisedShape.FREEHAND, path, false, 0f)

        val closed = isClosed(path, diagonal)

        // The tolerance scales with the drawing, so the same hand wobble is
        // forgiven at every size.
        val simplified = simplify(path, diagonal * 0.055f)
        val corners = if (closed) simplified.dropLast(1) else simplified

        // A loop with many corners is very likely a curve.
        if (closed && corners.size >= 5) {
            val round = roundness(path, bounds)
            if (round.first > 0.62f) {
                val aspect = if (max(width, height) <= 0f) 1f else min(width, height) / max(width, height)
                return if (aspect > 0.82f) {
                    ShapeGuess(RecognisedShape.CIRCLE, circle(bounds), true, round.first)
                } else {
                    ShapeGuess(RecognisedShape.ELLIPSE, ellipse(bounds), true, round.first)
                }
            }
            return ShapeGuess(RecognisedShape.FREEHAND, path, closed, 0f)
        }

        return when {
            !closed && corners.size == 2 -> {
                val straightness = straightness(path)
                ShapeGuess(
                    if (straightness >= minimumConfidence) RecognisedShape.LINE else RecognisedShape.FREEHAND,
                    listOf(path.first(), path.last()),
                    false,
                    straightness,
                )
            }

            closed && corners.size == 3 -> {
                val fit = polygonFit(path, corners)
                ShapeGuess(
                    if (fit >= minimumConfidence) RecognisedShape.TRIANGLE else RecognisedShape.FREEHAND,
                    corners,
                    true,
                    fit,
                )
            }

            closed && corners.size == 4 -> {
                val fit = polygonFit(path, corners)
                if (fit < minimumConfidence) return ShapeGuess(RecognisedShape.FREEHAND, path, true, fit)
                // Axis-aligned unless the drawing is clearly turned: a hand-drawn
                // box is meant to be square-on far more often than not, and
                // straightening it is the thing that makes this feel tidy rather
                // than merely smoothed.
                val turned = tilt(corners)
                if (abs(turned) > 0.20f) {
                    ShapeGuess(quadrilateralKind(corners), corners, true, fit)
                } else {
                    val aspect = if (max(width, height) <= 0f) 1f else min(width, height) / max(width, height)
                    val square = aspect > 0.85f
                    val box = if (square) squared(bounds) else rectangle(bounds)
                    ShapeGuess(
                        if (square) RecognisedShape.SQUARE else RecognisedShape.RECTANGLE,
                        box,
                        true,
                        fit,
                    )
                }
            }

            else -> ShapeGuess(RecognisedShape.FREEHAND, path, closed, 0f)
        }
    }

    // -- Geometry ----------------------------------------------------------

    private fun boundsOf(path: List<StrokePoint>): FloatArray {
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        path.forEach {
            left = min(left, it.x); top = min(top, it.y)
            right = max(right, it.x); bottom = max(bottom, it.y)
        }
        return floatArrayOf(left, top, right, bottom)
    }

    /** Closed when the two ends are near each other relative to the size. */
    private fun isClosed(path: List<StrokePoint>, diagonal: Float): Boolean {
        val first = path.first()
        val last = path.last()
        return distance(first.x, first.y, last.x, last.y) < diagonal * 0.28f
    }

    /**
     * Ramer-Douglas-Peucker: keep only the points that carry the shape.
     *
     * Iterative rather than recursive - a long scribble is thousands of points
     * and the recursive form is a stack overflow waiting for one.
     */
    fun simplify(path: List<StrokePoint>, tolerance: Float): List<StrokePoint> {
        if (path.size < 3) return path
        val keep = BooleanArray(path.size)
        keep[0] = true
        keep[path.size - 1] = true

        val stack = ArrayDeque<IntArray>()
        stack.addLast(intArrayOf(0, path.size - 1))
        while (stack.isNotEmpty()) {
            val (from, to) = stack.removeLast().let { it[0] to it[1] }
            if (to <= from + 1) continue
            var worst = -1
            var worstDistance = 0f
            for (i in from + 1 until to) {
                val d = perpendicular(path[i], path[from], path[to])
                if (d > worstDistance) {
                    worstDistance = d
                    worst = i
                }
            }
            if (worst >= 0 && worstDistance > tolerance) {
                keep[worst] = true
                stack.addLast(intArrayOf(from, worst))
                stack.addLast(intArrayOf(worst, to))
            }
        }
        return path.filterIndexed { index, _ -> keep[index] }
    }

    private fun perpendicular(point: StrokePoint, a: StrokePoint, b: StrokePoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 0.0001f) return distance(point.x, point.y, a.x, a.y)
        val t = (((point.x - a.x) * dx + (point.y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        return distance(point.x, point.y, a.x + dx * t, a.y + dy * t)
    }

    /** How well the path hugs the straight line between its ends, 0..1. */
    private fun straightness(path: List<StrokePoint>): Float {
        val a = path.first()
        val b = path.last()
        val span = distance(a.x, a.y, b.x, b.y)
        if (span < 1f) return 0f
        var worst = 0f
        path.forEach { worst = max(worst, perpendicular(it, a, b)) }
        return (1f - (worst / (span * 0.12f))).coerceIn(0f, 1f)
    }

    /** How well the path hugs the polygon through [corners], 0..1. */
    private fun polygonFit(path: List<StrokePoint>, corners: List<StrokePoint>): Float {
        if (corners.size < 3) return 0f
        val bounds = boundsOf(path)
        val diagonal = distance(bounds[0], bounds[1], bounds[2], bounds[3])
        if (diagonal < 1f) return 0f
        var total = 0f
        path.forEach { point ->
            var nearest = Float.MAX_VALUE
            for (i in corners.indices) {
                val a = corners[i]
                val b = corners[(i + 1) % corners.size]
                nearest = min(nearest, perpendicular(point, a, b))
            }
            total += nearest
        }
        val average = total / path.size
        return (1f - (average / (diagonal * 0.06f))).coerceIn(0f, 1f)
    }

    /**
     * How circular the path is: 1 when every point is the same distance from
     * the centre, falling away as that distance varies.
     */
    private fun roundness(path: List<StrokePoint>, bounds: FloatArray): Pair<Float, Float> {
        val cx = (bounds[0] + bounds[2]) / 2f
        val cy = (bounds[1] + bounds[3]) / 2f
        val rx = (bounds[2] - bounds[0]) / 2f
        val ry = (bounds[3] - bounds[1]) / 2f
        if (rx < 1f || ry < 1f) return 0f to 0f

        // Measured against the *ellipse* through the bounding box, so a long
        // oval scores as well as a circle - the round/oval split is made later,
        // from the aspect ratio, which is the honest place for it.
        var total = 0f
        path.forEach { point ->
            val nx = (point.x - cx) / rx
            val ny = (point.y - cy) / ry
            total += abs(sqrt(nx * nx + ny * ny) - 1f)
        }
        val average = total / path.size
        return (1f - average / 0.35f).coerceIn(0f, 1f) to average
    }

    /** How far the quadrilateral is from axis-aligned, in radians. */
    private fun tilt(corners: List<StrokePoint>): Float {
        var best = 0f
        var longest = 0f
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            val length = distance(a.x, a.y, b.x, b.y)
            if (length <= longest) continue
            longest = length
            var angle = atan2(b.y - a.y, b.x - a.x)
            // Fold into 0..PI/4: a rectangle turned by 90 degrees is not turned.
            while (angle < 0f) angle += PI.toFloat()
            angle %= (PI.toFloat() / 2f)
            best = min(angle, PI.toFloat() / 2f - angle)
        }
        return best
    }

    private fun quadrilateralKind(corners: List<StrokePoint>): RecognisedShape {
        var shortest = Float.MAX_VALUE
        var longest = 0f
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            val length = distance(a.x, a.y, b.x, b.y)
            shortest = min(shortest, length)
            longest = max(longest, length)
        }
        return if (longest > 0f && shortest / longest > 0.85f) {
            RecognisedShape.SQUARE
        } else {
            RecognisedShape.RECTANGLE
        }
    }

    // -- Tidy outlines -----------------------------------------------------

    private fun rectangle(bounds: FloatArray): List<StrokePoint> = listOf(
        StrokePoint(bounds[0], bounds[1]),
        StrokePoint(bounds[2], bounds[1]),
        StrokePoint(bounds[2], bounds[3]),
        StrokePoint(bounds[0], bounds[3]),
    )

    /** A rectangle forced to equal sides, about the drawing's own centre. */
    private fun squared(bounds: FloatArray): List<StrokePoint> {
        val cx = (bounds[0] + bounds[2]) / 2f
        val cy = (bounds[1] + bounds[3]) / 2f
        val half = max(bounds[2] - bounds[0], bounds[3] - bounds[1]) / 2f
        return listOf(
            StrokePoint(cx - half, cy - half),
            StrokePoint(cx + half, cy - half),
            StrokePoint(cx + half, cy + half),
            StrokePoint(cx - half, cy + half),
        )
    }

    private fun ellipse(bounds: FloatArray, steps: Int = 64): List<StrokePoint> {
        val cx = (bounds[0] + bounds[2]) / 2f
        val cy = (bounds[1] + bounds[3]) / 2f
        val rx = (bounds[2] - bounds[0]) / 2f
        val ry = (bounds[3] - bounds[1]) / 2f
        return (0 until steps).map { i ->
            val a = 2f * PI.toFloat() * i / steps
            StrokePoint(cx + cos(a) * rx, cy + sin(a) * ry)
        }
    }

    private fun circle(bounds: FloatArray, steps: Int = 64): List<StrokePoint> {
        val cx = (bounds[0] + bounds[2]) / 2f
        val cy = (bounds[1] + bounds[3]) / 2f
        val r = max(bounds[2] - bounds[0], bounds[3] - bounds[1]) / 2f
        return (0 until steps).map { i ->
            val a = 2f * PI.toFloat() * i / steps
            StrokePoint(cx + cos(a) * r, cy + sin(a) * r)
        }
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * The outline as a dense path the stroke engine can walk.
     *
     * Corners are joined with straight runs at about a pixel apart, because the
     * engine draws by stamping along a path and would otherwise put one dab at
     * each corner and nothing between them.
     */
    fun toStrokePath(guess: ShapeGuess): List<StrokePoint> {
        val points = guess.points
        if (points.size < 2) return points
        val out = ArrayList<StrokePoint>()
        val limit = if (guess.closed) points.size else points.size - 1
        for (i in 0 until limit) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val length = distance(a.x, a.y, b.x, b.y)
            val steps = max(1, length.toInt())
            for (s in 0 until steps) {
                val t = s / steps.toFloat()
                out.add(StrokePoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
            }
        }
        out.add(points[if (guess.closed) 0 else points.size - 1])
        return out
    }
}
