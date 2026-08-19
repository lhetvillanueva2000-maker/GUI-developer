package com.mcguidesigner.core.model

import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Integer geometry primitives.  The core module deliberately avoids depending
 * on Compose so that headless tooling (exporters, CLI, tests, CI validation)
 * can use the same types.  All values are expressed in *GUI pixels* - the
 * unscaled Minecraft coordinate space where a vanilla chest screen is
 * 176 x 166.
 */
@Serializable
data class IntSize(val width: Int, val height: Int) {
    operator fun times(factor: Int) = IntSize(width * factor, height * factor)

    fun coerceIn(min: IntSize, max: IntSize) = IntSize(
        width.coerceIn(min.width, max.width),
        height.coerceIn(min.height, max.height),
    )

    companion object {
        val Zero = IntSize(0, 0)
    }
}

@Serializable
data class IntPoint(val x: Int, val y: Int) {
    operator fun plus(other: IntPoint) = IntPoint(x + other.x, y + other.y)
    operator fun minus(other: IntPoint) = IntPoint(x - other.x, y - other.y)

    companion object {
        val Zero = IntPoint(0, 0)
    }
}

/** Axis-aligned rectangle with an exclusive right/bottom edge. */
@Serializable
data class IntRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    val left: Int get() = x
    val top: Int get() = y
    val right: Int get() = x + width
    val bottom: Int get() = y + height
    val centerX: Int get() = x + width / 2
    val centerY: Int get() = y + height / 2
    val size: IntSize get() = IntSize(width, height)
    val position: IntPoint get() = IntPoint(x, y)
    val area: Int get() = width * height

    fun contains(px: Int, py: Int): Boolean = px >= left && px < right && py >= top && py < bottom

    fun contains(point: IntPoint): Boolean = contains(point.x, point.y)

    fun intersects(other: IntRect): Boolean =
        left < other.right && right > other.left && top < other.bottom && bottom > other.top

    /** True when [other] lies fully inside this rectangle. */
    fun containsRect(other: IntRect): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

    fun translated(dx: Int, dy: Int) = copy(x = x + dx, y = y + dy)

    fun withSize(newSize: IntSize) = copy(width = newSize.width, height = newSize.height)

    fun withPosition(point: IntPoint) = copy(x = point.x, y = point.y)

    fun inflate(amount: Int) = IntRect(x - amount, y - amount, width + amount * 2, height + amount * 2)

    fun union(other: IntRect): IntRect {
        val l = min(left, other.left)
        val t = min(top, other.top)
        val r = max(right, other.right)
        val b = max(bottom, other.bottom)
        return IntRect(l, t, r - l, b - t)
    }

    companion object {
        val Zero = IntRect(0, 0, 0, 0)

        fun fromEdges(left: Int, top: Int, right: Int, bottom: Int) =
            IntRect(left, top, right - left, bottom - top)

        /** Builds a normalised rect from two arbitrary corner points. */
        fun fromCorners(a: IntPoint, b: IntPoint) = fromEdges(
            left = min(a.x, b.x),
            top = min(a.y, b.y),
            right = max(a.x, b.x),
            bottom = max(a.y, b.y),
        )

        /** Bounding box of a collection of rects, or [Zero] when empty. */
        fun bounds(rects: Collection<IntRect>): IntRect =
            rects.reduceOrNull { acc, rect -> acc.union(rect) } ?: Zero
    }
}

/**
 * Nine-slice insets used both for the built-in procedural skins and for
 * user-imported textures.
 */
@Serializable
data class Insets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    val horizontal: Int get() = left + right
    val vertical: Int get() = top + bottom

    companion object {
        val Zero = Insets()
        fun all(value: Int) = Insets(value, value, value, value)
        fun symmetric(horizontal: Int, vertical: Int) =
            Insets(horizontal, vertical, horizontal, vertical)
    }
}

/**
 * Anchoring rule applied when the parent container (or the canvas) is resized.
 * Java screens are centred on the window; Bedrock screens are anchored to
 * safe-area edges, which is why this lives on the element itself.
 */
@Serializable
enum class Anchor {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT;

    val displayName: String
        get() = name.split('_').joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }

    /** Horizontal weight in the 0..1 range. */
    val hFactor: Float
        get() = when (this) {
            TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> 0f
            TOP_CENTER, CENTER, BOTTOM_CENTER -> 0.5f
            TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> 1f
        }

    /** Vertical weight in the 0..1 range. */
    val vFactor: Float
        get() = when (this) {
            TOP_LEFT, TOP_CENTER, TOP_RIGHT -> 0f
            CENTER_LEFT, CENTER, CENTER_RIGHT -> 0.5f
            BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> 1f
        }

    /**
     * Resolves the offset of an element of [size] inside a container of
     * [container] size, given the element's authored [offset] from the anchor.
     */
    fun resolve(container: IntSize, size: IntSize, offset: IntPoint): IntPoint {
        val x = ((container.width - size.width) * hFactor).roundToInt() + offset.x
        val y = ((container.height - size.height) * vFactor).roundToInt() + offset.y
        return IntPoint(x, y)
    }
}

/** Which handle of a selection box a resize gesture is dragging. */
enum class ResizeHandle {
    TOP_LEFT, TOP, TOP_RIGHT,
    LEFT, RIGHT,
    BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT;

    val affectsLeft: Boolean get() = this == TOP_LEFT || this == LEFT || this == BOTTOM_LEFT
    val affectsRight: Boolean get() = this == TOP_RIGHT || this == RIGHT || this == BOTTOM_RIGHT
    val affectsTop: Boolean get() = this == TOP_LEFT || this == TOP || this == TOP_RIGHT
    val affectsBottom: Boolean get() = this == BOTTOM_LEFT || this == BOTTOM || this == BOTTOM_RIGHT
}

/** Alignment operations exposed by the align toolbar. */
enum class AlignMode {
    LEFT, HORIZONTAL_CENTER, RIGHT,
    TOP, VERTICAL_CENTER, BOTTOM,
    DISTRIBUTE_HORIZONTAL, DISTRIBUTE_VERTICAL;

    val displayName: String
        get() = name.split('_').joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { it.uppercase() }
        }
}
