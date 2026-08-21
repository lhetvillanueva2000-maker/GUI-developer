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

        ELLIPSE, ROUNDED_RECTANGLE -> emptyList()
    }

    companion object {
        const val MIN_SIDES = 3
        const val MAX_SIDES = 24

        val ids: List<String> = entries.map { it.id }

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
