package com.mcguidesigner.core

import com.mcguidesigner.core.paint.MarqueeShape
import com.mcguidesigner.core.paint.PaintSelection
import com.mcguidesigner.core.paint.Ruler
import com.mcguidesigner.core.paint.RulerGuide
import com.mcguidesigner.core.paint.RulerKind
import com.mcguidesigner.core.paint.SelectMode
import com.mcguidesigner.core.paint.StrokePoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two things a selection and a ruler have to get right.
 *
 * A selection has to hold exactly the pixels it looks like it holds - one pixel
 * out and a fill leaves a hairline - and a ruler has to put a stroke exactly on
 * its shape, or it is a suggestion rather than a ruler.
 */
class SelectionAndRulerTest {

    private val w = 64
    private val h = 64

    // -- Selections --------------------------------------------------------

    @Test
    fun aRectangleHoldsExactlyItsPixels() {
        val s = PaintSelection.marquee(w, h, 10f, 10f, 19f, 19f, MarqueeShape.RECTANGLE)
        assertEquals(10, s.left)
        assertEquals(10, s.top)
        assertEquals(19, s.right)
        assertEquals(19, s.bottom)
        assertEquals(255, s.at(10, 10))
        assertEquals(255, s.at(19, 19))
        assertEquals(0, s.at(9, 10))
        assertEquals(0, s.at(20, 19))
        assertEquals(100f, s.area)
    }

    @Test
    fun anEllipseIsRoundAndFitsItsBox() {
        val s = PaintSelection.marquee(w, h, 8f, 8f, 40f, 40f, MarqueeShape.ELLIPSE)
        assertEquals(255, s.at(24, 24), "the middle is not selected")
        assertEquals(0, s.at(8, 8), "a corner of the box was selected")
        assertEquals(255, s.at(24, 8), "the top of the ellipse is missing")
        // A circle of radius 16 is pi*r^2 ~= 804, give or take the rasterising.
        assertTrue(abs(s.area - 804f) < 60f, "an ellipse of area ${s.area} is not round")
    }

    /**
     * A loop that crosses itself leaves the overlap out.
     *
     * The even-odd rule, and the reason a lasso drawn as a figure-of-eight
     * behaves the way it looks rather than filling the crossing twice.
     */
    @Test
    fun aLassoFillsItsLoop() {
        val square = listOf(
            StrokePoint(10f, 10f), StrokePoint(30f, 10f),
            StrokePoint(30f, 30f), StrokePoint(10f, 30f),
        )
        val s = PaintSelection.lasso(square, w, h)
        assertEquals(255, s.at(20, 20), "the inside of the loop is not selected")
        assertEquals(0, s.at(5, 20), "outside the loop was selected")
        assertTrue(abs(s.area - 441f) < 60f, "a 20-square loop selected ${s.area} pixels")
    }

    @Test
    fun anUnclosedLassoStillEnclosesSomething() {
        // Three quarters of a square: the lasso closes itself, which is what
        // lets you let go without carefully returning to the start.
        val open = listOf(StrokePoint(10f, 10f), StrokePoint(30f, 10f), StrokePoint(30f, 30f))
        val s = PaintSelection.lasso(open, w, h)
        assertTrue(s.area > 100f, "an open path enclosed only ${s.area} pixels")
        assertEquals(255, s.at(26, 14), "the inside of the triangle is not selected")
    }

    @Test
    fun theWandTakesOneColourAndStopsAtTheNext() {
        val pixels = IntArray(w * h) { 0xFF204080.toInt() }
        for (y in 20 until 40) for (x in 20 until 40) pixels[y * w + x] = 0xFFCC3311.toInt()
        val s = PaintSelection.wand(pixels, w, h, 25, 25, tolerance = 16, contiguous = true, feather = 0)
        assertEquals(255, s.at(30, 30))
        assertEquals(0, s.at(10, 10))
        assertEquals(400f, s.area, "the wand did not take exactly the square")
    }

    @Test
    fun invertingSwapsInsideAndOut() {
        val s = PaintSelection.marquee(w, h, 10f, 10f, 19f, 19f, MarqueeShape.RECTANGLE)
        val inverted = s.invert()
        assertEquals(0, inverted.at(15, 15))
        assertEquals(255, inverted.at(0, 0))
        assertEquals((w * h - 100).toFloat(), inverted.area)
        // ...and back again.
        assertEquals(s.area, inverted.invert().area)
    }

    @Test
    fun expandingGrowsAndContractingShrinks() {
        val s = PaintSelection.marquee(w, h, 20f, 20f, 29f, 29f, MarqueeShape.RECTANGLE)
        val bigger = s.expand(2)
        assertEquals(18, bigger.left)
        assertEquals(31, bigger.right)
        val smaller = s.expand(-2)
        assertEquals(22, smaller.left)
        assertEquals(27, smaller.right)
        assertTrue(smaller.area < s.area && s.area < bigger.area)
    }

    @Test
    fun theCombineModesDoWhatTheyAreCalled() {
        val left = PaintSelection.marquee(w, h, 10f, 10f, 29f, 29f, MarqueeShape.RECTANGLE)
        val right = PaintSelection.marquee(w, h, 20f, 10f, 39f, 29f, MarqueeShape.RECTANGLE)

        assertEquals(right.area, left.combine(right, SelectMode.REPLACE).area)
        assertEquals(30f * 20f, left.combine(right, SelectMode.ADD).area)
        assertEquals(10f * 20f, left.combine(right, SelectMode.SUBTRACT).area)
        assertEquals(10f * 20f, left.combine(right, SelectMode.INTERSECT).area)
    }

    /**
     * The outline is runs, not pixels.
     *
     * A rectangle has four sides however big it is, and if this ever starts
     * returning one segment per boundary pixel the marching ants become a few
     * thousand draw calls a frame.
     */
    @Test
    fun theOutlineOfARectangleIsFourLines() {
        val s = PaintSelection.marquee(w, h, 10f, 10f, 29f, 29f, MarqueeShape.RECTANGLE)
        val outline = s.outline()
        assertEquals(4, outline.segmentCount, "a rectangle's outline came back in ${outline.segmentCount} pieces")
        assertEquals(2, outline.horizontal.size)
        assertEquals(2, outline.vertical.size)
    }

    @Test
    fun theOutlineOfACircleIsFarFewerRunsThanPixels() {
        val s = PaintSelection.marquee(w, h, 4f, 4f, 60f, 60f, MarqueeShape.ELLIPSE)
        val outline = s.outline()
        // A 57-pixel circle has a few hundred pixels on its boundary and each
        // of them can contribute up to four edges, so an unmerged outline would
        // be the better part of a thousand pieces. Merging brings it to ~140:
        // two short runs at each end of most rows, and one long one at the top
        // and bottom. The bound is what stops that regressing to per-pixel.
        assertTrue(outline.segmentCount < 200, "a circle's outline took ${outline.segmentCount} segments")
        assertTrue(!outline.isEmpty)
    }

    // -- Rulers ------------------------------------------------------------

    private fun on(guide: RulerGuide, start: StrokePoint, point: StrokePoint) =
        Ruler.snap(guide, start, point)

    @Test
    fun aStraightEdgePutsEveryStrokeOnOneLine() {
        val guide = RulerGuide(RulerKind.LINE, x = 0f, y = 0f, angle = 45f)
        val snapped = on(guide, StrokePoint(10f, 10f), StrokePoint(30f, 0f))
        // The line y = x through the origin: the foot of the perpendicular from
        // (30, 0) is (15, 15).
        assertTrue(abs(snapped.x - 15f) < 0.01f && abs(snapped.y - 15f) < 0.01f, "landed at $snapped")
    }

    @Test
    fun theParallelRulerGivesEachStrokeItsOwnLine() {
        val guide = RulerGuide(RulerKind.PARALLEL, angle = 0f)
        val high = on(guide, StrokePoint(0f, 10f), StrokePoint(40f, 25f))
        val low = on(guide, StrokePoint(0f, 30f), StrokePoint(40f, 25f))
        assertEquals(10f, high.y, "a stroke started at y=10 did not stay there")
        assertEquals(30f, low.y, "a stroke started at y=30 did not stay there")
        assertEquals(40f, high.x, "the ruler moved the point along its own line")
    }

    @Test
    fun theCrossRulerPicksTheAxisTheStrokeSetOffAlong() {
        val guide = RulerGuide(RulerKind.CROSS, angle = 0f)
        val across = on(guide, StrokePoint(10f, 10f), StrokePoint(40f, 13f))
        assertTrue(abs(across.y - 10f) < 1e-4f, "a mostly-horizontal stroke landed at $across")
        val down = on(guide, StrokePoint(10f, 10f), StrokePoint(13f, 40f))
        assertTrue(abs(down.x - 10f) < 1e-4f, "a mostly-vertical stroke landed at $down")
    }

    @Test
    fun theCircleRulerKeepsTheStrokeAtItsStartingRadius() {
        val guide = RulerGuide(RulerKind.CIRCLE, x = 32f, y = 32f)
        val start = StrokePoint(52f, 32f)
        val snapped = on(guide, start, StrokePoint(32f, 5f))
        assertTrue(
            abs(hypot(snapped.x - 32f, snapped.y - 32f) - 20f) < 0.01f,
            "the stroke left its circle: $snapped",
        )
        assertTrue(abs(snapped.x - 32f) < 0.01f && snapped.y < 32f, "it went the wrong way round")
    }

    @Test
    fun theRadialRulerSnapsToTheNearestSpoke() {
        val guide = RulerGuide(RulerKind.RADIAL, x = 0f, y = 0f, slices = 4)
        // Four spokes at 0, 90, 180, 270. A point just off east goes to east.
        val snapped = on(guide, StrokePoint(1f, 0f), StrokePoint(40f, 4f))
        assertTrue(abs(snapped.y) < 0.01f, "a point 6 degrees off east landed at $snapped")
    }

    @Test
    fun aPerspectiveRulerRunsStrokesToTheVanishingPoint() {
        val guide = RulerGuide(RulerKind.PERSPECTIVE_1, x = 100f, y = 0f)
        val start = StrokePoint(0f, 40f)
        val snapped = on(guide, start, StrokePoint(50f, 0f))
        // The line from (0,40) to the vanishing point (100,0): at x = 50 it is
        // at y = 20, and the foot of the perpendicular from (50,0) is near it.
        val onLine = abs((snapped.x - 0f) * (0f - 40f) - (snapped.y - 40f) * (100f - 0f))
        assertTrue(onLine < 0.1f, "the stroke was not on the line to the vanishing point: $snapped")
    }

    @Test
    fun aRulerThatIsOffChangesNothing() {
        val point = StrokePoint(13f, 27f)
        assertEquals(point, Ruler.snap(RulerGuide(), StrokePoint(0f, 0f), point))
    }

    /** With no anchor there is nothing to choose, so the point stands. */
    @Test
    fun theFirstPointOfAStrokeIsNeverMoved() {
        val guide = RulerGuide(RulerKind.LINE, x = 0f, y = 0f, angle = 90f)
        val point = StrokePoint(13f, 27f)
        assertEquals(point, Ruler.snap(guide, null, point))
    }

    /** Every ruler draws something, or it looks like the brush has broken. */
    @Test
    fun everyRulerHasAnOutline() {
        RulerKind.entries.filter { it != RulerKind.OFF }.forEach { kind ->
            val guide = RulerGuide.placed(kind, 200, 200)
            val outline = Ruler.outline(guide, 200, 200)
            assertTrue(outline.isNotEmpty(), "${kind.label} draws nothing")
            assertTrue(outline.all { it.size >= 2 }, "${kind.label} produced a degenerate piece")
        }
    }

    /** Lines are clipped to the canvas, however far off it the anchor is. */
    @Test
    fun aVanishingPointOffTheCanvasDoesNotProduceEnormousLines() {
        val guide = RulerGuide.placed(RulerKind.PERSPECTIVE_1, 200, 200).copy(x = -50_000f, y = -50_000f)
        Ruler.outline(guide, 200, 200).flatten().forEach { point ->
            assertTrue(
                point.x >= -1f && point.x <= 201f && point.y >= -1f && point.y <= 201f,
                "a ruler line reached $point, well outside the canvas",
            )
        }
    }
}
