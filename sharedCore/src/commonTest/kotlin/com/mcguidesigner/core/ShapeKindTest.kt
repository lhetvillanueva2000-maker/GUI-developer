package com.mcguidesigner.core

import com.mcguidesigner.core.model.ShapeKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every shape is defined as fractions of its bounding box, and four separate
 * consumers - the canvas, the HTML `clip-path`, the SVG export and both pack
 * exporters - trust that without checking. A point outside 0..1 does not fail
 * anywhere; it silently draws outside the element, and only in the export
 * nobody opened.
 */
class ShapeKindTest {

    @Test
    fun `every polygonal shape stays inside its own bounds`() {
        ShapeKind.entries.filter { it.isPolygonal }.forEach { kind ->
            kind.outline().forEachIndexed { index, (x, y) ->
                assertTrue(x in -0.001f..1.001f, "${kind.id} point $index has x=$x outside the box")
                assertTrue(y in -0.001f..1.001f, "${kind.id} point $index has y=$y outside the box")
            }
        }
    }

    @Test
    fun `every polygonal shape has enough points to be a shape`() {
        ShapeKind.entries.filter { it.isPolygonal }.forEach { kind ->
            assertTrue(
                kind.outline().size >= 3,
                "${kind.id} has ${kind.outline().size} points, which is not a polygon",
            )
        }
    }

    @Test
    fun `the two curved kinds carry no outline`() {
        assertTrue(ShapeKind.ELLIPSE.outline().isEmpty())
        assertTrue(ShapeKind.ROUNDED_RECTANGLE.outline().isEmpty())
        assertTrue(!ShapeKind.ELLIPSE.isPolygonal)
        assertTrue(!ShapeKind.ROUNDED_RECTANGLE.isPolygonal)
    }

    @Test
    fun `every shape actually uses the width and height it is given`() {
        // A shape whose points all share one x - or one y - is a line by
        // accident, which is what a mistyped constant produces.
        ShapeKind.entries.filter { it.isPolygonal && it != ShapeKind.LINE }.forEach { kind ->
            val points = kind.outline()
            assertTrue(points.map { it.first }.distinct().size > 1, "${kind.id} is degenerate horizontally")
            assertTrue(points.map { it.second }.distinct().size > 1, "${kind.id} is degenerate vertically")
        }
    }

    @Test
    fun `ids are unique and stable`() {
        // The id is what a saved project stores, so a collision would make two
        // shapes load as each other.
        assertEquals(ShapeKind.entries.size, ShapeKind.ids.distinct().size)
        ShapeKind.entries.forEach { assertEquals(it, ShapeKind.fromId(it.id)) }
    }

    @Test
    fun `an unknown id falls back rather than failing to load`() {
        assertEquals(ShapeKind.RECTANGLE, ShapeKind.fromId("no_such_shape"))
        assertEquals(ShapeKind.RECTANGLE, ShapeKind.fromId(null))
    }

    @Test
    fun `the shapes added in 1_7_0 are all present and drawable`() {
        val added = listOf(
            "line", "capsule", "plate", "tab_top", "banner", "bookmark",
            "shield", "heart", "triangle_down", "arrow_up", "chevron_down", "notch",
        )
        added.forEach { id ->
            val kind = ShapeKind.fromId(id)
            assertEquals(id, kind.id, "$id is missing")
            assertTrue(kind.outline().size >= 3, "$id has no usable outline")
            assertTrue(kind.glyph.isNotBlank(), "$id has no palette glyph")
        }
    }

    @Test
    fun `the heart is a heart and not a blob`() {
        val points = ShapeKind.HEART.outline()
        // Two lobes at the top and a point at the bottom: the lowest point
        // should sit near the horizontal centre.
        val lowest = points.maxByOrNull { it.second }!!
        assertTrue(lowest.first in 0.4f..0.6f, "the heart's point is off-centre at ${lowest.first}")
    }
}
