package com.mcguidesigner.core.model

/**
 * The outlines a custom shape element can take.
 *
 * Every entry is defined by [outline], which returns the shape's corner points
 * as fractions of its bounding box (0..1 on both axes).  Working in fractions
 * rather than pixels means one definition serves the editor canvas, the HTML
 * exporter's `clip-path`, the SVG exporter and the Bedrock/Java exporters at
 * once, and a shape that is resized never needs recomputing anywhere else.
 *
 * [ELLIPSE] and [ROUNDED_RECTANGLE] are the two exceptions: they are curves,
 * not polygons, so they carry no outline and every consumer special-cases
 * them.  [isPolygonal] says which is which.
 */
enum class ShapeKind(
    val id: String,
    val displayName: String,
    /** Palette glyph. ASCII/geometric only, so no font pack is required. */
    val glyph: String,
    val description: String = "",
) {
    RECTANGLE("rectangle", "Rectangle", "▭"),
    ROUNDED_RECTANGLE("rounded_rectangle", "Rounded rectangle", "▢", "Corner radius is set by the Corner radius property."),
    ELLIPSE("ellipse", "Ellipse / circle", "◯", "Square the bounds to get a perfect circle."),
    TRIANGLE("triangle", "Triangle", "△"),
    RIGHT_TRIANGLE("right_triangle", "Right triangle", "◺"),
    DIAMOND("diamond", "Diamond", "◇"),
    PENTAGON("pentagon", "Pentagon", "⬠"),
    HEXAGON("hexagon", "Hexagon", "⬡"),
    OCTAGON("octagon", "Octagon", "⯃"),
    STAR("star", "Star", "★", "Point count and inner radius are both adjustable."),
    CROSS("cross", "Cross / plus", "✚"),
    CHEVRON("chevron", "Chevron", "❯"),
    ARROW_RIGHT("arrow_right", "Arrow", "➜", "Rotate it to point any other direction."),
    SPEECH_BUBBLE("speech_bubble", "Speech bubble", "🗩"),
    PARALLELOGRAM("parallelogram", "Parallelogram", "▰"),
    TRAPEZOID("trapezoid", "Trapezoid", "⏢"),
    POLYGON("polygon", "Regular polygon", "⬢", "Side count is set by the Sides property."),

    // -- Added in 1.7.0 ----------------------------------------------------
    // Chosen for what interfaces are actually built out of rather than for
    // variety: dividers, plates, tabs, badges, meters and the two arrow forms
    // a HUD needs. Every one is a polygon, so the canvas, the HTML clip-path,
    // the SVG export and both pack exporters all get it for free.

    LINE("line", "Line / divider", "─", "A thin bar. Set the height to 1 or 2 for a rule."),
    CAPSULE("capsule", "Capsule / pill", "⬭", "A tag or a badge. Square it up for a circle."),
    PLATE("plate", "Cut-corner plate", "⬒", "A rectangle with the corners taken off - the usual backing plate."),
    TAB_TOP("tab_top", "Tab", "⌂", "A tab head, flat along the bottom to sit against a panel."),
    BANNER("banner", "Banner / ribbon", "⚑", "A notched end, for a title strip."),
    BOOKMARK("bookmark", "Bookmark", "🔖", "Notched at the bottom - a marker or a pin."),
    SHIELD("shield", "Shield", "⛊", "Crest shape, for a badge or a rank."),
    HEART("heart", "Heart", "♥", "The vanilla health icon's silhouette."),
    TRIANGLE_DOWN("triangle_down", "Triangle (down)", "▽", "A caret or a dropdown marker."),
    ARROW_UP("arrow_up", "Arrow (up)", "⬆", "Rotate it to point any other direction."),
    CHEVRON_DOWN("chevron_down", "Chevron (down)", "⌄", "An expand marker."),
    NOTCH("notch", "Notched bar", "⊓", "A progress track or a slot rail."),
    ;

    /** False for the two curved kinds, which have no polygon outline. */
    val isPolygonal: Boolean get() = this != ELLIPSE && this != ROUNDED_RECTANGLE

    /**
     * Corner points in 0..1 bounding-box space, clockwise from the top.
     *
     * [sides] is only read by [POLYGON] and [STAR] (where it is the point
     * count); [innerRadius] only by [STAR].  Curved kinds return an empty
     * list - callers must check [isPolygonal] first.
     */
    fun outline(sides: Int = 6, innerRadius: Float = 0.5f): List<Pair<Float, Float>> = when (this) {
        RECTANGLE -> listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)
        TRIANGLE -> listOf(0.5f to 0f, 1f to 1f, 0f to 1f)
        RIGHT_TRIANGLE -> listOf(0f to 0f, 1f to 1f, 0f to 1f)
        DIAMOND -> listOf(0.5f to 0f, 1f to 0.5f, 0.5f to 1f, 0f to 0.5f)
        PENTAGON -> regularPolygon(5)
        HEXAGON -> regularPolygon(6)
        OCTAGON -> regularPolygon(8)
        POLYGON -> regularPolygon(sides.coerceIn(MIN_SIDES, MAX_SIDES))
        STAR -> star(sides.coerceIn(MIN_SIDES, MAX_SIDES), innerRadius.coerceIn(0.05f, 0.95f))

        CROSS -> {
            // A third of the box on each axis gives the plus its arms.
            val a = 1f / 3f
            val b = 2f / 3f
            listOf(
                a to 0f, b to 0f, b to a, 1f to a,
                1f to b, b to b, b to 1f, a to 1f,
                a to b, 0f to b, 0f to a, a to a,
            )
        }

        CHEVRON -> listOf(
            0f to 0f, 0.5f to 0f, 1f to 0.5f, 0.5f to 1f,
            0f to 1f, 0.5f to 0.5f,
        )

        ARROW_RIGHT -> listOf(
            0f to 0.3f, 0.6f to 0.3f, 0.6f to 0f, 1f to 0.5f,
            0.6f to 1f, 0.6f to 0.7f, 0f to 0.7f,
        )

        // Body plus a tail in the lower left, the usual chat-bubble shape.
        SPEECH_BUBBLE -> listOf(
            0f to 0f, 1f to 0f, 1f to 0.75f, 0.35f to 0.75f,
            0.18f to 1f, 0.2f to 0.75f, 0f to 0.75f,
        )

        PARALLELOGRAM -> listOf(0.25f to 0f, 1f to 0f, 0.75f to 1f, 0f to 1f)
        TRAPEZOID -> listOf(0.22f to 0f, 0.78f to 0f, 1f to 1f, 0f to 1f)

        // -- Added in 1.7.0 ------------------------------------------------

        // Full width, centred band. The element's own height decides how thick
        // it is, so a 2px-tall LINE is a hairline and a 20px one is a bar -
        // rather than baking a thickness in here that resizing could not change.
        LINE -> listOf(0f to 0.35f, 1f to 0.35f, 1f to 0.65f, 0f to 0.65f)

        // Approximated with points rather than made a curved kind: a curved
        // kind means every consumer special-cases it, and sixteen points is
        // indistinguishable from a true capsule at the sizes this is used at.
        CAPSULE -> capsule()

        PLATE -> {
            val c = 0.18f
            listOf(
                c to 0f, 1f - c to 0f, 1f to c, 1f to 1f - c,
                1f - c to 1f, c to 1f, 0f to 1f - c, 0f to c,
            )
        }

        TAB_TOP -> listOf(0.12f to 0f, 0.88f to 0f, 1f to 1f, 0f to 1f)

        BANNER -> listOf(
            0f to 0f, 1f to 0f, 0.86f to 0.5f, 1f to 1f, 0f to 1f,
        )

        BOOKMARK -> listOf(
            0f to 0f, 1f to 0f, 1f to 1f, 0.5f to 0.72f, 0f to 1f,
        )

        SHIELD -> listOf(
            0f to 0f, 1f to 0f, 1f to 0.55f, 0.85f to 0.82f,
            0.5f to 1f, 0.15f to 0.82f, 0f to 0.55f,
        )

        HEART -> heart()

        TRIANGLE_DOWN -> listOf(0f to 0f, 1f to 0f, 0.5f to 1f)

        ARROW_UP -> listOf(
            0.5f to 0f, 1f to 0.4f, 0.7f to 0.4f, 0.7f to 1f,
            0.3f to 1f, 0.3f to 0.4f, 0f to 0.4f,
        )

        CHEVRON_DOWN -> listOf(
            0f to 0f, 0.5f to 0.5f, 1f to 0f, 1f to 0.4f,
            0.5f to 0.9f, 0f to 0.4f,
        )

        // A bar with a bite out of the middle of its top edge, which is what a
        // slot rail and a segmented meter are both drawn from.
        NOTCH -> listOf(
            0f to 0f, 0.35f to 0f, 0.35f to 0.3f, 0.65f to 0.3f,
            0.65f to 0f, 1f to 0f, 1f to 1f, 0f to 1f,
        )

        ELLIPSE, ROUNDED_RECTANGLE -> emptyList()
    }

    companion object {
        const val MIN_SIDES = 3
        const val MAX_SIDES = 24

        val ids: List<String> = entries.map { it.id }

        /** Half-circle caps joined by straight sides, as points. */
        private fun capsule(steps: Int = 8): List<Pair<Float, Float>> {
            val points = mutableListOf<Pair<Float, Float>>()
            // Right cap, top to bottom.
            for (i in 0..steps) {
                val a = -kotlin.math.PI.toFloat() / 2f + kotlin.math.PI.toFloat() * i / steps
                points += (0.75f + 0.25f * cosOf(a)) to (0.5f + 0.5f * sinOf(a))
            }
            // Left cap, bottom to top.
            for (i in 0..steps) {
                val a = kotlin.math.PI.toFloat() / 2f + kotlin.math.PI.toFloat() * i / steps
                points += (0.25f + 0.25f * cosOf(a)) to (0.5f + 0.5f * sinOf(a))
            }
            return points
        }

        /**
         * The classic two-lobe heart, as points.
         *
         * The parametric heart curve, sampled and squashed into the bounding
         * box - hand-placing the points gets the lobes wrong at any size other
         * than the one they were placed at.
         */
        private fun heart(steps: Int = 28): List<Pair<Float, Float>> {
            val raw = (0 until steps).map { i ->
                val t = 2f * kotlin.math.PI.toFloat() * i / steps
                val x = 16f * sinOf(t) * sinOf(t) * sinOf(t)
                // Screen space grows downwards, so the curve is negated here
                // rather than flipped later.
                val y = -(13f * cosOf(t) - 5f * cosOf(2 * t) - 2f * cosOf(3 * t) - cosOf(4 * t))
                x to y
            }
            // Normalised from the *sampled* extents rather than the curve's
            // analytic range. Hand-written bounds were wrong by 4% on the y
            // axis, which put two points outside the bounding box - and a
            // shape drawing outside its own element fails silently everywhere
            // it is used.
            val minX = raw.minOf { it.first }
            val maxX = raw.maxOf { it.first }
            val minY = raw.minOf { it.second }
            val maxY = raw.maxOf { it.second }
            val spanX = (maxX - minX).takeIf { it > 0f } ?: 1f
            val spanY = (maxY - minY).takeIf { it > 0f } ?: 1f
            return raw.map { (x, y) -> ((x - minX) / spanX) to ((y - minY) / spanY) }
        }

        fun fromId(id: String?): ShapeKind = entries.firstOrNull { it.id == id } ?: RECTANGLE

        /**
         * [count] evenly spaced points on the bounding ellipse, first point at
         * top centre.  Uses 0..1 space, so a non-square box yields a stretched
         * polygon - which is what someone resizing a hexagon expects.
         */
        private fun regularPolygon(count: Int): List<Pair<Float, Float>> =
            (0 until count).map { index ->
                val angle = TWO_PI * index / count - HALF_PI
                (0.5f + 0.5f * cosOf(angle)) to (0.5f + 0.5f * sinOf(angle))
            }

        /** Alternating outer and inner points, [points] of each. */
        private fun star(points: Int, innerRadius: Float): List<Pair<Float, Float>> =
            (0 until points * 2).map { index ->
                val radius = if (index % 2 == 0) 0.5f else 0.5f * innerRadius
                val angle = PI * index / points - HALF_PI
                (0.5f + radius * cosOf(angle)) to (0.5f + radius * sinOf(angle))
            }

        private const val PI = 3.1415927f
        private const val TWO_PI = PI * 2f
        private const val HALF_PI = PI / 2f

        private fun cosOf(angle: Float): Float = kotlin.math.cos(angle)
        private fun sinOf(angle: Float): Float = kotlin.math.sin(angle)
    }
}
