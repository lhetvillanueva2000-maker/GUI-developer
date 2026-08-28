package com.mcguidesigner.core.paint

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The shape of one dab, as a coverage mask.
 *
 * Every brush and every eraser in the app is one of these plus a size, an
 * opacity and a spacing. Keeping the shape separate from what is done with the
 * coverage is what lets the eraser be exactly as good as the brush - the two
 * share this file entirely, and an eraser that is a worse brush with the
 * colours inverted is the single most common way a paint app disappoints.
 */
enum class BrushShape(val label: String, val hardness: Float, val spacing: Float) {
    /** A hard round nib with an anti-aliased edge one pixel wide. */
    DIP_PEN_HARD("Dip Pen (Hard)", hardness = 0.96f, spacing = 0.06f),

    /** The default. Slight softness, so strokes join without banding. */
    DIP_PEN_SOFT("Dip Pen (Soft)", hardness = 0.78f, spacing = 0.06f),

    /** Broad and flat-ended, the marker you block colour in with. */
    FELT_TIP_HARD("Felt Tip Pen (Hard)", hardness = 0.90f, spacing = 0.05f),

    FELT_TIP_SOFT("Felt Tip Pen (Soft)", hardness = 0.62f, spacing = 0.05f),

    /** Digital pen: perfectly even, no falloff to speak of. */
    DIGITAL_PEN("Digital Pen", hardness = 1.0f, spacing = 0.04f),

    /** Airbrush: nearly all falloff, for haze and soft shading. */
    AIRBRUSH("Airbrush (Normal)", hardness = 0.12f, spacing = 0.03f),

    /** Softer still, and wide. What a background gradient is made of. */
    AIRBRUSH_WIDE("Airbrush (Soft)", hardness = 0.04f, spacing = 0.03f),

    /** A pencil-ish grain, for sketching. */
    PENCIL("Pencil", hardness = 0.85f, spacing = 0.09f),
    ;

    companion object {
        fun byLabel(label: String): BrushShape = entries.firstOrNull { it.label == label } ?: DIP_PEN_SOFT
    }
}

/**
 * A brush: a shape at a size and an opacity.
 *
 * [flow] and [opacity] are different things and both are needed. Flow is how
 * much each dab lays down; opacity is the ceiling the whole stroke may reach.
 * A soft airbrush is low flow at full opacity - it builds up as you go over it
 * again. A marker is full flow at 40% opacity - it lays down 40% immediately
 * and going over it again inside the same stroke changes nothing. Collapsing
 * the two into one slider is why cheap brush engines cannot do either properly.
 */
data class Brush(
    val shape: BrushShape = BrushShape.DIP_PEN_SOFT,
    /** Diameter in canvas pixels. */
    val size: Float = 12f,
    /** 0..1. The most this stroke may build up to. */
    val opacity: Float = 1f,
    /** 0..1. How much one dab contributes. */
    val flow: Float = 1f,
    /** Fraction of the diameter between dabs. Lower is smoother and slower. */
    val spacing: Float = 0f,
    /** 0..1. Higher smooths the input path harder; 0 is off. */
    val stabilizer: Float = 0f,
    /** Taper at the start and end of a stroke, as a fraction of its length. */
    val fadeIn: Float = 0f,
    val fadeOut: Float = 0f,
) {
    val effectiveSpacing: Float get() = if (spacing > 0f) spacing else shape.spacing

    /** Radius in pixels, never below half a pixel or nothing would be drawn. */
    val radius: Float get() = max(0.5f, size / 2f)
}

/**
 * A cached circular coverage mask.
 *
 * Recomputing a falloff curve per dab means a `pow` per pixel per dab, which at
 * a hundred dabs a second on a 48px brush is a few million calls a second on a
 * phone. The mask depends only on the size and the shape, and both change far
 * less often than the pointer moves, so it is built once and stamped.
 *
 * The mask is supersampled 4x4 at the rim, which is what makes edges look
 * drawn rather than aliased. Doing it only where coverage is partial keeps the
 * cost to the boundary rather than the area.
 */
class BrushStamp private constructor(
    val radius: Float,
    val diameter: Int,
    /** Coverage 0..255, row-major, [diameter] x [diameter]. */
    val coverage: ByteArray,
) {
    fun at(x: Int, y: Int): Int =
        if (x < 0 || y < 0 || x >= diameter || y >= diameter) 0
        else coverage[y * diameter + x].toInt() and 0xFF

    companion object {
        private const val CACHE = 8
        private val keys = LongArray(CACHE) { -1L }
        private val stamps = arrayOfNulls<BrushStamp>(CACHE)
        private var next = 0

        /**
         * The stamp for [radius] and [hardness], from cache when possible.
         *
         * Two things here are load-bearing on a large brush.
         *
         * The quantisation is *relative* - roughly a two-hundredth of the
         * radius - rather than a fixed twentieth of a pixel. A fixed step is
         * fine at radius 2 and disastrous at radius 200, where a taper varies
         * the radius continuously and every single dab misses the cache. Each
         * miss rebuilds a 400-square mask with sixteen samples per rim pixel,
         * which is more work than the dab it was meant to make cheap.
         *
         * And there are eight slots rather than one, because symmetry alternates
         * between tips and a taper walks the radius up and back down; a
         * single-entry cache thrashes on both.
         */
        fun of(radius: Float, hardness: Float): BrushStamp {
            // Quantised in log space, which is what makes the step relative and
            // - unlike scaling a step by the radius - a pure function of it.
            // Deriving the step from the radius being quantised does not settle:
            // 200 and 200.4 pick slightly different steps and land on different
            // buckets, so the cache misses on exactly the pairs it exists for.
            // One bucket per half percent of radius, all the way down.
            val safe = radius.coerceAtLeast(0.5f)
            val rKey = (ln(safe) * 200f).roundToInt()
            val quantised = exp(rKey / 200f)
            val hKey = (hardness.coerceIn(0f, 1f) * 100f).roundToInt().toLong()
            val key = (rKey.toLong() shl 8) or hKey

            for (i in 0 until CACHE) {
                if (keys[i] == key) stamps[i]?.let { return it }
            }
            val built = build(quantised, hKey / 100f)
            keys[next] = key
            stamps[next] = built
            next = (next + 1) % CACHE
            return built
        }

        private fun build(radius: Float, hardness: Float): BrushStamp {
            val r = max(0.5f, radius)
            val diameter = max(1, (r * 2f).roundToInt() + 2)
            val centre = diameter / 2f
            val coverage = ByteArray(diameter * diameter)

            // Where falloff begins, as a fraction of the radius.
            val solid = (hardness.coerceIn(0f, 1f)).pow(0.6f) * 0.98f
            val inner = r * solid
            val band = max(0.6f, r - inner)

            for (y in 0 until diameter) {
                for (x in 0 until diameter) {
                    val dx = x + 0.5f - centre
                    val dy = y + 0.5f - centre
                    val d = kotlin.math.sqrt(dx * dx + dy * dy)
                    val value = when {
                        // Comfortably inside: full coverage, no sampling needed.
                        d <= inner - 0.75f -> 1f
                        // Comfortably outside: nothing.
                        d >= r + 0.75f -> 0f
                        // The rim, where the eye actually looks.
                        else -> supersample(dx, dy, inner, band, r)
                    }
                    coverage[y * diameter + x] = (value.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
                }
            }
            return BrushStamp(r, diameter, coverage)
        }

        /** 4x4 samples through the falloff curve, averaged. */
        private fun supersample(dx: Float, dy: Float, inner: Float, band: Float, r: Float): Float {
            var total = 0f
            for (sy in 0 until 4) {
                for (sx in 0 until 4) {
                    val ox = dx + (sx + 0.5f) / 4f - 0.5f
                    val oy = dy + (sy + 0.5f) / 4f - 0.5f
                    val d = kotlin.math.sqrt(ox * ox + oy * oy)
                    total += falloff(d, inner, band, r)
                }
            }
            return total / 16f
        }

        /**
         * The falloff curve: 1 inside, 0 outside, smoothstep between.
         *
         * Smoothstep rather than linear because a linear ramp leaves a visible
         * crease where it meets the solid centre, and that crease is exactly
         * what makes a soft airbrush look like a badly drawn circle.
         */
        private fun falloff(d: Float, inner: Float, band: Float, r: Float): Float {
            if (d <= inner) return 1f
            if (d >= r) return 0f
            val t = ((d - inner) / band).coerceIn(0f, 1f)
            val s = t * t * (3f - 2f * t)
            return 1f - s
        }
    }
}

/**
 * Smooths the pointer path.
 *
 * A finger on glass is not a steady hand, and at any brush size above a few
 * pixels the wobble is the most visible thing about a stroke. This is an
 * exponential follower: the drawn point chases the real one, and the strength
 * decides how far behind it lags.
 *
 * The lag is the whole trade and it is deliberately exposed rather than tuned
 * to one "good" value. At 0 the line is exactly where the finger is and shakes;
 * near 1 it is glass-smooth and noticeably behind. Different people and
 * different strokes want different points on that line.
 */
class Stabilizer(private var strength: Float = 0f) {
    private var x = 0f
    private var y = 0f
    private var started = false

    fun reset(strength: Float) {
        this.strength = strength.coerceIn(0f, 0.98f)
        started = false
    }

    fun push(px: Float, py: Float): Pair<Float, Float> {
        if (!started) {
            x = px
            y = py
            started = true
            return px to py
        }
        // Squared so the slider's lower half stays useful; a linear mapping
        // makes everything below 0.5 feel identical to off.
        val a = 1f - strength * strength
        x += (px - x) * a
        y += (py - y) * a
        return x to y
    }

    /**
     * The tail of the stroke, so a stabilised line reaches where the finger
     * lifted instead of stopping short of it by exactly the lag.
     */
    fun drain(px: Float, py: Float, steps: Int = 12): List<Pair<Float, Float>> {
        if (!started || strength <= 0f) return emptyList()
        val out = ArrayList<Pair<Float, Float>>(steps)
        repeat(steps) {
            val a = 1f - strength * strength
            x += (px - x) * a
            y += (py - y) * a
            out.add(x to y)
        }
        return out
    }
}

/** One point of a stroke in canvas space, with how hard it was pressed. */
data class StrokePoint(val x: Float, val y: Float, val pressure: Float = 1f)

/**
 * Turns a path into dabs and lays them down.
 *
 * The important structural choice is the **stroke buffer**. Dabs within a
 * single stroke accumulate into a separate 8-bit coverage buffer with `max`
 * rather than compositing straight onto the layer, and only when the finger
 * lifts is that coverage applied once. Without it, a 40%-opacity brush turns
 * opaque wherever the dabs overlap - which is everywhere, since they overlap by
 * design - so a translucent stroke is translucent only at its two ends and
 * solid along its whole length. Every paint app that gets this wrong is
 * instantly recognisable, and it cannot be fixed by tuning the spacing.
 */
class StrokeEngine(private val width: Int, private val height: Int) {

    companion object {
        /** Enough for the widest radial symmetry the ruler offers, plus room. */
        const val MAX_CHANNELS = 32

        /** Stands in until the first stamp allocates the real one. */
        private val EMPTY_COVERAGE = ByteArray(0)
    }

    /**
     * Coverage laid down by the stroke in progress, 0..255 per pixel.
     *
     * Allocated on the first stamp rather than with the engine. It is one byte
     * per pixel of the document - two and a third megabytes on a 1536-square
     * canvas - and the engine is built while the paint screen is being opened,
     * where every megabyte is time the person spends looking at nothing. By the
     * time a stroke starts the screen is up and the allocation is invisible.
     */
    private var coverage: ByteArray = EMPTY_COVERAGE

    /**
     * Allocates the buffer, once, at the first stamp.
     *
     * A plain field rather than a lazy getter because [coverageAt] is read once
     * per pixel by every tool, and a null check on that path costs more over a
     * stroke than the allocation it would be guarding.
     */
    private fun ensureCoverage(): ByteArray {
        var buffer = coverage
        if (buffer.size != width * height) {
            buffer = ByteArray(width * height)
            coverage = buffer
        }
        return buffer
    }

    private var minX = 0
    private var minY = 0
    private var maxX = -1
    private var maxY = -1

    /**
     * One pen tip. Symmetry draws several at once and each needs its own
     * position and spacing remainder.
     *
     * They deliberately share the single coverage buffer above. Giving each
     * reflection its own engine would paint the crossing point twice, and at
     * any opacity below full that shows up as a dark seam running exactly along
     * the axis of symmetry - the one place a person is looking.
     */
    private val lastX = FloatArray(MAX_CHANNELS)
    private val lastY = FloatArray(MAX_CHANNELS)
    private val carry = FloatArray(MAX_CHANNELS)
    private val live = BooleanArray(MAX_CHANNELS)

    private var started = false
    private var travelled = 0f

    /**
     * The rectangle stamped since the last [consumeStep].
     *
     * Kept alongside the cumulative one because the two answer different
     * questions, and confusing them is what made drawing quadratic. The
     * cumulative rectangle is "everything this stroke has touched", which is
     * what the undo step and the on-screen patch need to cover. This one is
     * "what changed just now", which is all any tool has to recompute: a pixel
     * whose coverage did not move this event blends to the byte-identical
     * value it already holds, so revisiting it is pure waste - and under the
     * old code a stroke revisited its entire bounding box on every event, which
     * put a quarter of a million pixels through the blender sixty times a
     * second by the middle of a diagonal swipe.
     */
    private var stepMinX = 0
    private var stepMinY = 0
    private var stepMaxX = -1
    private var stepMaxY = -1

    val isActive: Boolean get() = started
    val dirtyLeft: Int get() = minX
    val dirtyTop: Int get() = minY
    val dirtyRight: Int get() = maxX
    val dirtyBottom: Int get() = maxY
    val hasDirt: Boolean get() = maxX >= minX && maxY >= minY

    val stepLeft: Int get() = stepMinX
    val stepTop: Int get() = stepMinY
    val stepRight: Int get() = stepMaxX
    val stepBottom: Int get() = stepMaxY
    val hasStep: Boolean get() = stepMaxX >= stepMinX && stepMaxY >= stepMinY

    /**
     * Marks the current step as dealt with.
     *
     * Called by whoever has just recomputed those pixels. Anything stamped
     * after this belongs to the next step.
     */
    fun consumeStep() {
        stepMinX = width
        stepMinY = height
        stepMaxX = -1
        stepMaxY = -1
    }

    /**
     * Starts a stroke and lays the first dab down immediately.
     *
     * The dab is not an optimisation, it is the tap. A stroke that only draws
     * on movement means touching the canvas and lifting produces nothing at
     * all, and "the brush doesn't work" is what that gets reported as - dotting
     * an eye or an i is a normal thing to want to do with a brush.
     */
    fun begin(point: StrokePoint, brush: Brush) {
        clear()
        travelled = 0f
        started = true
        beginChannel(0, point, brush)
    }

    /**
     * Starts one additional pen tip within the stroke already begun.
     *
     * Used by symmetry. Does not clear the buffer, so all the tips accumulate
     * into the same coverage and their crossings are drawn once.
     */
    fun beginChannel(channel: Int, point: StrokePoint, brush: Brush) {
        if (channel !in 0 until MAX_CHANNELS) return
        started = true
        lastX[channel] = point.x
        lastY[channel] = point.y
        carry[channel] = 0f
        live[channel] = true
        stamp(point.x, point.y, brush, fadeFactor(brush, 0f))
    }

    /**
     * Extends one pen tip to [point], stamping along the way.
     *
     * [brush] is passed per call rather than held, because a size slider moved
     * mid-stroke should take effect from that moment rather than at the next
     * stroke.
     */
    fun extendTo(point: StrokePoint, brush: Brush, estimatedLength: Float, channel: Int = 0) {
        if (channel !in 0 until MAX_CHANNELS) return
        if (!started || !live[channel]) {
            beginChannel(channel, point, brush)
            return
        }
        val dx = point.x - lastX[channel]
        val dy = point.y - lastY[channel]
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
        if (distance <= 0.0001f) return

        val step = max(0.5f, brush.radius * 2f * brush.effectiveSpacing)
        var t = carry[channel]
        val start = t
        while (t <= distance) {
            val f = t / distance
            // Only the first tip advances the length used for taper, or an
            // eight-way radial stroke would fade out eight times too early.
            if (channel == 0 && t != start) travelled += step
            stamp(lastX[channel] + dx * f, lastY[channel] + dy * f, brush, fadeFactor(brush, estimatedLength))
            t += step
        }
        carry[channel] = t - distance
        lastX[channel] = point.x
        lastY[channel] = point.y
    }

    /**
     * The taper multiplier at the current position along the stroke.
     *
     * Fade needs to know how long the stroke will be, which nothing does until
     * it ends. [estimatedLength] is the caller's running guess; the taper at
     * the start is therefore exact and the taper at the end is approximate,
     * which is the right way round - the start is drawn before anything is
     * known and the end is drawn when almost everything is.
     */
    private fun fadeFactor(brush: Brush, estimatedLength: Float): Float {
        if (brush.fadeIn <= 0f && brush.fadeOut <= 0f) return 1f
        if (estimatedLength <= 1f) return 1f
        var factor = 1f
        if (brush.fadeIn > 0f) {
            val span = estimatedLength * brush.fadeIn
            if (span > 0f) factor *= min(1f, travelled / span)
        }
        if (brush.fadeOut > 0f) {
            val span = estimatedLength * brush.fadeOut
            val remaining = estimatedLength - travelled
            if (span > 0f) factor *= min(1f, max(0f, remaining) / span)
        }
        return factor.coerceIn(0f, 1f)
    }

    /** One dab, accumulated into the coverage buffer with `max`. */
    private fun stamp(cx: Float, cy: Float, brush: Brush, fade: Float) {
        val radius = brush.radius * fade.coerceAtLeast(0.02f)
        val stamp = BrushStamp.of(radius, brush.shape.hardness)
        val flow = (brush.flow * fade).coerceIn(0f, 1f)
        if (flow <= 0f) return
        val flow8 = (flow * 255f).roundToInt()

        val left = (cx - stamp.diameter / 2f).roundToInt()
        val top = (cy - stamp.diameter / 2f).roundToInt()

        val x0 = max(0, left)
        val y0 = max(0, top)
        val x1 = min(width - 1, left + stamp.diameter - 1)
        val y1 = min(height - 1, top + stamp.diameter - 1)
        if (x1 < x0 || y1 < y0) return

        val buffer = ensureCoverage()
        for (y in y0..y1) {
            val row = y * width
            val sy = y - top
            for (x in x0..x1) {
                val c = stamp.at(x - left, sy)
                if (c == 0) continue
                val contribution = Pixels.mul(c, flow8)
                val index = row + x
                val existing = buffer[index].toInt() and 0xFF
                if (contribution > existing) buffer[index] = contribution.toByte()
            }
        }
        grow(x0, y0, x1, y1)
    }

    /**
     * Ends the stroke and hands the accumulated coverage to [apply].
     *
     * [apply] receives `(index, coverage0to255)` for every touched pixel and
     * decides what that means - paint for a brush, remove for an eraser. The
     * engine deliberately does not know which it is.
     */
    inline fun finish(apply: (index: Int, coverage: Int) -> Unit) {
        if (!hasDirt) {
            clear()
            return
        }
        for (y in dirtyTop..dirtyBottom) {
            val row = y * canvasWidth
            for (x in dirtyLeft..dirtyRight) {
                val index = row + x
                val c = coverageAt(index)
                if (c != 0) apply(index, c)
            }
        }
        clear()
    }

    /** Exposed for [finish], which is inline and cannot see private state. */
    val canvasWidth: Int get() = width

    /**
     * Coverage at [index], or zero before anything has been stamped.
     *
     * The bounds check is what lets the buffer be allocated at the first stamp
     * rather than with the engine - see [ensureCoverage].
     */
    fun coverageAt(index: Int): Int {
        val buffer = coverage
        return if (index < buffer.size) buffer[index].toInt() and 0xFF else 0
    }

    fun clear() {
        val buffer = coverage
        if (hasDirt && buffer.size == width * height) {
            for (y in minY..maxY) {
                val from = y * width + minX
                buffer.fill(0, from, from + (maxX - minX + 1))
            }
        }
        minX = width
        minY = height
        maxX = -1
        maxY = -1
        consumeStep()
        started = false
        travelled = 0f
        carry.fill(0f)
        live.fill(false)
    }

    private fun grow(x0: Int, y0: Int, x1: Int, y1: Int) {
        if (x0 < minX) minX = x0
        if (y0 < minY) minY = y0
        if (x1 > maxX) maxX = x1
        if (y1 > maxY) maxY = y1

        if (x0 < stepMinX) stepMinX = x0
        if (y0 < stepMinY) stepMinY = y0
        if (x1 > stepMaxX) stepMaxX = x1
        if (y1 > stepMaxY) stepMaxY = y1
    }
}

/** Straight-line distance, used by callers tracking stroke length for fade. */
fun distanceBetween(ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = ax - bx
    val dy = ay - by
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

/**
 * Snaps a point to a symmetry or ruler guide.
 *
 * Kept as pure geometry so the ruler popover's settings can be tested without a
 * canvas, and so the same code serves the on-screen preview and the stroke.
 */
object Guides {

    /** Reflections of [point] for a mirror through [axisX], vertical. */
    fun mirrorVertical(point: StrokePoint, axisX: Float): List<StrokePoint> =
        listOf(point, point.copy(x = 2f * axisX - point.x))

    fun mirrorHorizontal(point: StrokePoint, axisY: Float): List<StrokePoint> =
        listOf(point, point.copy(y = 2f * axisY - point.y))

    /** [slices] rotations of [point] about a centre. */
    fun radial(point: StrokePoint, cx: Float, cy: Float, slices: Int): List<StrokePoint> {
        if (slices <= 1) return listOf(point)
        val out = ArrayList<StrokePoint>(slices)
        val dx = point.x - cx
        val dy = point.y - cy
        for (i in 0 until slices) {
            val a = 2.0 * PI * i / slices
            val c = cos(a).toFloat()
            val s = kotlin.math.sin(a).toFloat()
            out.add(point.copy(x = cx + dx * c - dy * s, y = cy + dx * s + dy * c))
        }
        return out
    }

    /** [point] constrained to the line through [ax],[ay] at [degrees]. */
    fun toLine(point: StrokePoint, ax: Float, ay: Float, degrees: Float): StrokePoint {
        val radians = degrees * PI.toFloat() / 180f
        val dx = cos(radians)
        val dy = kotlin.math.sin(radians)
        val t = (point.x - ax) * dx + (point.y - ay) * dy
        return point.copy(x = ax + dx * t, y = ay + dy * t)
    }

    /** [point] snapped to whichever axis it has moved further along. */
    fun toNearestAxis(point: StrokePoint, ax: Float, ay: Float): StrokePoint =
        if (abs(point.x - ax) >= abs(point.y - ay)) point.copy(y = ay) else point.copy(x = ax)
}
