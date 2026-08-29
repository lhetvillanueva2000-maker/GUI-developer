package com.mcguidesigner.core.paint

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** What shape the ruler holds a stroke to. */
enum class RulerKind(val label: String, val hint: String) {
    OFF("Off", "Strokes go wherever you draw them."),
    LINE("Straight edge", "Every stroke lands on one line. Drag the ruler to move or turn it."),
    PARALLEL("Parallel", "Each stroke becomes its own line at the ruler's angle."),
    CROSS("Cross", "Each stroke locks to whichever of the two axes it started along."),
    CIRCLE("Circle", "Each stroke rides the circle it started on, around the centre."),
    ELLIPSE("Ellipse", "As the circle, squashed - the shape a circle makes seen at an angle."),
    RADIAL("Radial", "Strokes run outward from the centre, along the nearest spoke."),
    PERSPECTIVE_1("1-point", "Everything runs to one vanishing point."),
    PERSPECTIVE_2("2-point", "Two vanishing points; a stroke takes the nearer one."),
    PERSPECTIVE_3("3-point", "Two on the horizon and one above or below it.");

    /** Whether this ruler needs the second and third vanishing points. */
    val isPerspective: Boolean
        get() = this == PERSPECTIVE_1 || this == PERSPECTIVE_2 || this == PERSPECTIVE_3

    val usesAngle: Boolean
        get() = this == LINE || this == PARALLEL || this == CROSS || this == ELLIPSE

    val usesCentre: Boolean
        get() = this != OFF && this != PARALLEL && this != CROSS
}

/**
 * A ruler: a shape strokes are held to while it is on.
 *
 * The point of a ruler in a painting app is the same as the point of one on a
 * desk. It is not a *tool* you draw with - it is a thing you put down and then
 * draw against, and everything you draw while it is there comes out straight,
 * or round, or converging on a vanishing point, without any of the care that
 * would otherwise take. It is what makes clean line art possible with a finger.
 *
 * Everything here is one immutable value so that placing a ruler, moving it and
 * turning it are all ordinary state changes, and so the geometry can be tested
 * without a canvas anywhere near it.
 */
data class RulerGuide(
    val kind: RulerKind = RulerKind.OFF,
    /** Centre, or the point the straight edge passes through. Canvas pixels. */
    val x: Float = 0f,
    val y: Float = 0f,
    /** Degrees, clockwise from east. */
    val angle: Float = 0f,
    /** How squashed the ellipse is: the second radius as a fraction of the first. */
    val flatten: Float = 0.6f,
    /** Spokes, for the radial ruler. */
    val slices: Int = 12,
    /** Second and third vanishing points, for the perspective rulers. */
    val x2: Float = 0f,
    val y2: Float = 0f,
    val x3: Float = 0f,
    val y3: Float = 0f,
) {
    val isOn: Boolean get() = kind != RulerKind.OFF

    companion object {
        /**
         * A ruler of [kind], placed where it is immediately useful.
         *
         * Everything that has a centre gets the middle of the canvas, which is
         * the only defensible default: a circle ruler that arrives three
         * quarters of the way across draws arcs that run off the edge, and the
         * first thing anybody would do is drag it back.
         *
         * The two- and three-point perspectives are the exception, and not
         * arbitrarily: their vanishing points belong out near the edges on a
         * shared horizon, because that is what makes the convergence look like
         * a room rather than a fan. The third goes well below the canvas, where
         * a worm's-eye vertical wants it.
         */
        fun placed(kind: RulerKind, width: Int, height: Int): RulerGuide {
            val horizon = height / 2f
            val twoPoint = kind == RulerKind.PERSPECTIVE_2 || kind == RulerKind.PERSPECTIVE_3
            return RulerGuide(
                kind = kind,
                x = if (twoPoint) width * 0.92f else width / 2f,
                y = if (twoPoint) horizon else height / 2f,
                angle = 0f,
                x2 = width * 0.08f,
                y2 = horizon,
                x3 = width / 2f,
                y3 = height * 1.6f,
            )
        }
    }
}

/**
 * The arithmetic that holds a stroke to a ruler.
 *
 * Every kind works the same way: take where the finger actually is, and return
 * the nearest point on whatever shape the ruler describes *through the point
 * the stroke started at*. That last part is what makes a ruler feel like a
 * ruler rather than a magnet - you choose which line, or which circle, by where
 * you start the stroke, and the ruler only decides its direction.
 */
object Ruler {

    /**
     * [point], moved onto the ruler.
     *
     * [anchor] is where this stroke began; it selects which of the ruler's
     * family of lines or circles the stroke belongs to. With no anchor - the
     * very first event of a stroke - the point is returned unchanged, because
     * there is nothing yet to choose.
     */
    fun snap(guide: RulerGuide, anchor: StrokePoint?, point: StrokePoint): StrokePoint {
        if (!guide.isOn) return point
        val start = anchor ?: return point
        return when (guide.kind) {
            RulerKind.OFF -> point
            RulerKind.LINE -> Guides.toLine(point, guide.x, guide.y, guide.angle)
            RulerKind.PARALLEL -> Guides.toLine(point, start.x, start.y, guide.angle)
            RulerKind.CROSS -> toTurnedAxis(point, start, guide.angle)
            RulerKind.CIRCLE -> toCircle(point, guide.x, guide.y, hypot(start.x - guide.x, start.y - guide.y))
            RulerKind.ELLIPSE -> toEllipse(point, start, guide)
            RulerKind.RADIAL -> toSpoke(point, guide)
            RulerKind.PERSPECTIVE_1 -> Guides.toLine(point, start.x, start.y, bearing(guide.x, guide.y, start))
            RulerKind.PERSPECTIVE_2, RulerKind.PERSPECTIVE_3 -> toNearestVanishing(point, start, guide)
        }
    }

    /**
     * Whichever of the ruler's two perpendicular axes the stroke set off along.
     *
     * Decided from the anchor to the current point rather than from the last
     * event, so a stroke cannot change its mind halfway and produce an L.
     */
    private fun toTurnedAxis(point: StrokePoint, start: StrokePoint, angle: Float): StrokePoint {
        val along = Guides.toLine(point, start.x, start.y, angle)
        val across = Guides.toLine(point, start.x, start.y, angle + 90f)
        val dAlong = distance(point, along)
        val dAcross = distance(point, across)
        return if (dAlong <= dAcross) along else across
    }

    /** The nearest point on the circle of [radius] about a centre. */
    private fun toCircle(point: StrokePoint, cx: Float, cy: Float, radius: Float): StrokePoint {
        if (radius < 0.5f) return point.copy(x = cx, y = cy)
        val dx = point.x - cx
        val dy = point.y - cy
        val length = hypot(dx, dy)
        if (length < 1e-4f) return point
        val scale = radius / length
        return point.copy(x = cx + dx * scale, y = cy + dy * scale)
    }

    /**
     * The nearest point on the ellipse through the anchor.
     *
     * Done in the ellipse's own frame - turned back upright, squashed into a
     * circle, snapped, and unsquashed - which is exact for the circle and a very
     * good approximation for the ellipse. The exact nearest point on an ellipse
     * is a quartic, and nobody drawing a line can tell the difference between
     * the true foot of the perpendicular and this one.
     */
    private fun toEllipse(point: StrokePoint, start: StrokePoint, guide: RulerGuide): StrokePoint {
        val flatten = guide.flatten.coerceIn(0.05f, 1f)
        val radians = guide.angle * PI.toFloat() / 180f
        val c = cos(radians)
        val s = sin(radians)

        fun intoFrame(px: Float, py: Float): Pair<Float, Float> {
            val dx = px - guide.x
            val dy = py - guide.y
            return (dx * c + dy * s) to ((-dx * s + dy * c) / flatten)
        }

        fun outOfFrame(fx: Float, fy: Float): Pair<Float, Float> {
            val sy = fy * flatten
            return (guide.x + fx * c - sy * s) to (guide.y + fx * s + sy * c)
        }

        val (ax, ay) = intoFrame(start.x, start.y)
        val radius = hypot(ax, ay)
        val (px, py) = intoFrame(point.x, point.y)
        val length = hypot(px, py)
        if (length < 1e-4f || radius < 0.5f) return point
        val (outX, outY) = outOfFrame(px / length * radius, py / length * radius)
        return point.copy(x = outX, y = outY)
    }

    /** Onto the nearest of the ruler's spokes, radiating from its centre. */
    private fun toSpoke(point: StrokePoint, guide: RulerGuide): StrokePoint {
        val slices = guide.slices.coerceAtLeast(1)
        val step = 360f / slices
        val bearing = bearing(guide.x, guide.y, point)
        val snapped = (bearing / step).let { kotlin.math.round(it) } * step + guide.angle
        return Guides.toLine(point, guide.x, guide.y, snapped)
    }

    /** Of the ruler's vanishing points, the one the stroke is heading towards. */
    private fun toNearestVanishing(point: StrokePoint, start: StrokePoint, guide: RulerGuide): StrokePoint {
        val candidates = buildList {
            add(guide.x to guide.y)
            add(guide.x2 to guide.y2)
            if (guide.kind == RulerKind.PERSPECTIVE_3) add(guide.x3 to guide.y3)
        }
        var best: StrokePoint? = null
        var bestDistance = Float.MAX_VALUE
        candidates.forEach { (vx, vy) ->
            val candidate = Guides.toLine(point, start.x, start.y, bearing(vx, vy, start))
            val d = distance(point, candidate)
            if (d < bestDistance) {
                bestDistance = d
                best = candidate
            }
        }
        return best ?: point
    }

    /** Degrees from ([fromX], [fromY]) to [to], clockwise from east. */
    private fun bearing(fromX: Float, fromY: Float, to: StrokePoint): Float =
        atan2(to.y - fromY, to.x - fromX) * 180f / PI.toFloat()

    private fun distance(a: StrokePoint, b: StrokePoint): Float = hypot(a.x - b.x, a.y - b.y)

    /**
     * The ruler's own outline, in canvas coordinates, for drawing it.
     *
     * A ruler you cannot see is a ruler that appears to have broken the brush,
     * so every kind returns something to draw. Lines are returned as segments
     * clipped to the canvas; the round ones as polylines, because a canvas
     * renderer that has to special-case each kind is a renderer that will
     * eventually disagree with the arithmetic above about where the ruler is.
     */
    fun outline(guide: RulerGuide, width: Int, height: Int): List<List<StrokePoint>> {
        if (!guide.isOn) return emptyList()
        val w = width.toFloat()
        val h = height.toFloat()
        return when (guide.kind) {
            RulerKind.OFF -> emptyList()
            RulerKind.LINE -> listOf(lineAcross(guide.x, guide.y, guide.angle, w, h))
            RulerKind.PARALLEL -> {
                // A family, drawn at a readable spacing so it looks like ruled
                // paper rather than one line that moved.
                val spacing = maxOf(w, h) / 12f
                (-8..8).mapNotNull { i ->
                    val radians = (guide.angle + 90f) * PI.toFloat() / 180f
                    val ox = guide.x + cos(radians) * spacing * i
                    val oy = guide.y + sin(radians) * spacing * i
                    lineAcross(ox, oy, guide.angle, w, h).takeIf { it.isNotEmpty() }
                }
            }

            RulerKind.CROSS -> listOf(
                lineAcross(guide.x, guide.y, guide.angle, w, h),
                lineAcross(guide.x, guide.y, guide.angle + 90f, w, h),
            )

            RulerKind.CIRCLE -> (1..5).map { ring ->
                circlePolyline(guide.x, guide.y, maxOf(w, h) / 12f * ring, 1f, 0f)
            }

            RulerKind.ELLIPSE -> (1..5).map { ring ->
                circlePolyline(
                    guide.x, guide.y, maxOf(w, h) / 12f * ring,
                    guide.flatten.coerceIn(0.05f, 1f), guide.angle,
                )
            }

            RulerKind.RADIAL -> {
                val slices = guide.slices.coerceAtLeast(1)
                (0 until slices).map { i ->
                    val degrees = guide.angle + 360f / slices * i
                    val radians = degrees * PI.toFloat() / 180f
                    val far = maxOf(w, h) * 2f
                    listOf(
                        StrokePoint(guide.x, guide.y),
                        StrokePoint(guide.x + cos(radians) * far, guide.y + sin(radians) * far),
                    )
                }
            }

            RulerKind.PERSPECTIVE_1, RulerKind.PERSPECTIVE_2, RulerKind.PERSPECTIVE_3 -> {
                val points = buildList {
                    add(guide.x to guide.y)
                    if (guide.kind != RulerKind.PERSPECTIVE_1) add(guide.x2 to guide.y2)
                    if (guide.kind == RulerKind.PERSPECTIVE_3) add(guide.x3 to guide.y3)
                }
                points.flatMap { (vx, vy) ->
                    (0 until 12).map { i ->
                        lineAcross(vx, vy, 360f / 12f * i, w, h)
                    }
                }.filter { it.isNotEmpty() }
            }
        }
    }

    /**
     * The chord an infinite line at [degrees] through a point cuts out of the
     * canvas.
     *
     * Clipped here rather than left to the renderer so a vanishing point far
     * off the edge of the canvas - which is the normal place for one - does not
     * turn into a line a hundred thousand pixels long being handed to a
     * drawing call.
     */
    private fun lineAcross(px: Float, py: Float, degrees: Float, width: Float, height: Float): List<StrokePoint> {
        val radians = degrees * PI.toFloat() / 180f
        val dx = cos(radians)
        val dy = sin(radians)
        var enter = -Float.MAX_VALUE
        var exit = Float.MAX_VALUE

        // Liang-Barsky against the four edges: for each, the parameter range
        // along the line that stays inside.
        fun clip(direction: Float, distance: Float): Boolean {
            if (abs(direction) < 1e-6f) return distance >= 0f
            val t = distance / direction
            if (direction > 0f) {
                if (t < exit) exit = t
            } else {
                if (t > enter) enter = t
            }
            return true
        }

        if (!clip(-dx, px - 0f)) return emptyList()
        if (!clip(dx, width - px)) return emptyList()
        if (!clip(-dy, py - 0f)) return emptyList()
        if (!clip(dy, height - py)) return emptyList()
        if (enter > exit) return emptyList()
        return listOf(
            StrokePoint(px + dx * enter, py + dy * enter),
            StrokePoint(px + dx * exit, py + dy * exit),
        )
    }

    private fun circlePolyline(
        cx: Float,
        cy: Float,
        radius: Float,
        flatten: Float,
        degrees: Float,
    ): List<StrokePoint> {
        val radians = degrees * PI.toFloat() / 180f
        val c = cos(radians)
        val s = sin(radians)
        // Enough segments that the join is under a pixel at any plausible ring
        // size, and few enough that five rings is not a thousand draw calls.
        val steps = (radius / 2f).roundToInt().coerceIn(24, 96)
        return (0..steps).map { i ->
            val a = 2f * PI.toFloat() * i / steps
            val fx = cos(a) * radius
            val fy = sin(a) * radius * flatten
            StrokePoint(cx + fx * c - fy * s, cy + fx * s + fy * c)
        }
    }

    /** The angle from a ruler's centre to a point, for dragging it round. */
    fun angleTo(guide: RulerGuide, x: Float, y: Float): Float =
        atan2(y - guide.y, x - guide.x) * 180f / PI.toFloat()

    /** Distance from a ruler's centre, for dragging its radius. */
    fun radiusTo(guide: RulerGuide, x: Float, y: Float): Float =
        sqrt((x - guide.x) * (x - guide.x) + (y - guide.y) * (y - guide.y))
}
