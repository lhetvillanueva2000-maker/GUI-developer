package com.mcguidesigner.core

import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.editor.DemoRuntime
import com.mcguidesigner.core.editor.DemoState
import com.mcguidesigner.core.model.BoolValue
import com.mcguidesigner.core.model.CanvasSpec
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.IntValue
import com.mcguidesigner.core.model.ListValue
import com.mcguidesigner.core.model.PropValue
import com.mcguidesigner.core.model.StringValue
import com.mcguidesigner.core.model.bool
import com.mcguidesigner.core.model.findById
import com.mcguidesigner.core.model.float
import com.mcguidesigner.core.model.int
import com.mcguidesigner.core.model.string
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The demo, checked by driving it rather than by looking at it.
 *
 * Every one of these is a question somebody opens the preview to answer - does
 * this button do anything, does the switch show me it is on, does the list
 * reach its last row - so a regression here is not cosmetic: it is the tab
 * going back to being a picture.
 */
class DemoRuntimeTest {

    private fun element(
        id: String,
        type: String,
        x: Int, y: Int, w: Int, h: Int,
        props: Map<String, PropValue> = emptyMap(),
        children: List<GuiElement> = emptyList(),
    ) = GuiElement(
        id = id,
        type = type,
        name = id,
        bounds = IntRect(x, y, w, h),
        props = ElementCatalog[type]?.defaultProps(Edition.OTHER).orEmpty() + props,
        children = children,
    )

    private fun project(vararg elements: GuiElement) = GuiProject(
        id = "p",
        name = "Demo",
        edition = Edition.OTHER,
        canvas = CanvasSpec(width = 320, height = 240),
        elements = elements.toList(),
    )

    /** Presses and releases on the same element, which is what a click is. */
    private fun click(project: GuiProject, demo: DemoState, x: Float, y: Float): DemoState {
        val down = DemoRuntime.pointerDown(project, demo, x, y)
        return DemoRuntime.pointerUp(project, down, x, y)
    }

    // -- The basics --------------------------------------------------------

    @Test
    fun aButtonPressesAndLetsGo() {
        val p = project(element("b", ElementCatalog.BUTTON_NORMAL, 10, 10, 80, 20))
        val down = DemoRuntime.pointerDown(p, DemoState(), 50f, 20f)
        assertEquals(
            InteractionState.PRESSED,
            DemoRuntime.stateFor(p.elements.findById("b")!!, down),
        )
        val up = DemoRuntime.pointerUp(p, down, 50f, 20f)
        assertEquals(InteractionState.NORMAL, DemoRuntime.stateFor(p.elements.findById("b")!!, up))
        assertEquals(1, up.actionCount)
    }

    /**
     * Sliding off a button before letting go cancels it.
     *
     * The one piece of button behaviour everybody relies on and nobody can
     * name. A demo that fires anyway is worse than one that does nothing,
     * because it teaches the wrong thing about the design.
     */
    @Test
    fun draggingOffAButtonDoesNotFireIt() {
        val p = project(element("b", ElementCatalog.BUTTON_NORMAL, 10, 10, 80, 20))
        val down = DemoRuntime.pointerDown(p, DemoState(), 50f, 20f)
        val away = DemoRuntime.pointerDrag(p, down, 200f, 200f)
        val up = DemoRuntime.pointerUp(p, away, 200f, 200f)
        assertEquals(0, up.actionCount, "a button fired after the finger left it")
    }

    @Test
    fun aDisabledWidgetIsDrawnDisabledAndDoesNothing() {
        val p = project(
            element(
                "b", ElementCatalog.BUTTON_NORMAL, 10, 10, 80, 20,
                mapOf("enabled" to BoolValue(false)),
            ),
        )
        assertEquals(
            InteractionState.DISABLED,
            DemoRuntime.stateFor(p.elements.findById("b")!!, DemoState()),
        )
        assertEquals(0, click(p, DemoState(), 50f, 20f).actionCount)
    }

    // -- Widgets that change ----------------------------------------------

    @Test
    fun aToggleFlips() {
        val p = project(element("t", ElementCatalog.BUTTON_TOGGLE, 0, 0, 80, 20))
        val once = click(p, DemoState(), 40f, 10f)
        assertTrue(DemoRuntime.propsOf(p.elements.findById("t")!!, once).bool("value"))
        val twice = click(p, once, 40f, 10f)
        assertTrue(!DemoRuntime.propsOf(p.elements.findById("t")!!, twice).bool("value"))
    }

    @Test
    fun aCheckboxTicks() {
        val p = project(element("c", ElementCatalog.INPUT_CHECKBOX, 0, 0, 100, 14))
        val after = click(p, DemoState(), 8f, 7f)
        assertTrue(DemoRuntime.propsOf(p.elements.findById("c")!!, after).bool("checked"))
    }

    /** Selecting a tab has to deselect its neighbours, or it is not a tab. */
    @Test
    fun selectingATabDeselectsTheOthersInItsGroup() {
        val p = project(
            element("t1", ElementCatalog.BUTTON_TAB, 0, 0, 40, 16, mapOf("selected" to BoolValue(true))),
            element("t2", ElementCatalog.BUTTON_TAB, 40, 0, 40, 16),
            element("t3", ElementCatalog.BUTTON_TAB, 80, 0, 40, 16, mapOf("groupIndex" to IntValue(1))),
        )
        val after = click(p, DemoState(), 60f, 8f)
        fun selected(id: String) = DemoRuntime.propsOf(p.elements.findById(id)!!, after).bool("selected")
        assertTrue(selected("t2"), "the tab that was clicked is not selected")
        assertTrue(!selected("t1"), "its neighbour stayed selected")
        assertTrue(!selected("t3"), "a tab in another group was touched")
    }

    @Test
    fun aDropdownReachesEveryOption() {
        val items = ListValue(listOf(StringValue("A"), StringValue("B"), StringValue("C")))
        val p = project(element("d", ElementCatalog.INPUT_DROPDOWN, 0, 0, 100, 20, mapOf("items" to items)))
        var demo = DemoState()
        val seen = mutableListOf<Int>()
        repeat(4) {
            demo = click(p, demo, 50f, 10f)
            seen += DemoRuntime.propsOf(p.elements.findById("d")!!, demo).int("selectedIndex")
        }
        assertEquals(listOf(1, 2, 0, 1), seen, "cycling did not visit every option and wrap")
    }

    /** A slider goes where the finger is, on the first frame, not gradually. */
    @Test
    fun aSliderJumpsToWhereItIsTapped() {
        val p = project(element("s", ElementCatalog.INPUT_SLIDER, 20, 0, 100, 20))
        val down = DemoRuntime.pointerDown(p, DemoState(), 95f, 10f)
        val value = DemoRuntime.propsOf(p.elements.findById("s")!!, down).float("value")
        assertTrue(value in 0.7f..0.8f, "tapping three quarters along set the value to $value")
    }

    @Test
    fun aSliderFollowsADrag() {
        val p = project(element("s", ElementCatalog.INPUT_SLIDER, 20, 0, 100, 20))
        var demo = DemoRuntime.pointerDown(p, DemoState(), 25f, 10f)
        demo = DemoRuntime.pointerDrag(p, demo, 120f, 10f)
        assertEquals(1f, DemoRuntime.propsOf(p.elements.findById("s")!!, demo).float("value"))
        demo = DemoRuntime.pointerDrag(p, demo, -50f, 10f)
        assertEquals(0f, DemoRuntime.propsOf(p.elements.findById("s")!!, demo).float("value"))
    }

    // -- Text --------------------------------------------------------------

    @Test
    fun aTextFieldTakesTheKeyboardAndKeepsWhatIsTyped() {
        val p = project(element("f", ElementCatalog.INPUT_TEXTBOX, 0, 0, 120, 16))
        var demo = click(p, DemoState(), 60f, 8f)
        assertEquals("f", demo.focused)
        assertEquals(
            InteractionState.FOCUSED,
            DemoRuntime.stateFor(p.elements.findById("f")!!, demo),
        )
        "hi".forEach { demo = DemoRuntime.type(p, demo, it) }
        assertEquals("hi", DemoRuntime.propsOf(p.elements.findById("f")!!, demo).string("value"))
        demo = DemoRuntime.backspace(p, demo)
        assertEquals("h", DemoRuntime.propsOf(p.elements.findById("f")!!, demo).string("value"))
    }

    /** The clear cross on a search field is a button, and it works. */
    @Test
    fun tappingTheClearCrossEmptiesASearchField() {
        val p = project(
            element(
                "s", ElementCatalog.INPUT_SEARCH, 0, 0, 120, 16,
                mapOf("value" to StringValue("hello"), "showClear" to BoolValue(true)),
            ),
        )
        // Well inside: takes the keyboard and keeps what is there.
        val focused = click(p, DemoState(), 40f, 8f)
        assertEquals("hello", DemoRuntime.propsOf(p.elements.findById("s")!!, focused).string("value"))

        // On the cross at the right-hand end: empties it.
        val cleared = click(p, focused, 117f, 8f)
        assertEquals("", DemoRuntime.propsOf(p.elements.findById("s")!!, cleared).string("value"))
    }

    @Test
    fun aFieldWithNoClearButtonIsJustFocusedAtItsRightHandEnd() {
        val p = project(
            element(
                "f", ElementCatalog.INPUT_TEXTBOX, 0, 0, 120, 16,
                mapOf("value" to StringValue("hello")),
            ),
        )
        val after = click(p, DemoState(), 117f, 8f)
        assertEquals("hello", DemoRuntime.propsOf(p.elements.findById("f")!!, after).string("value"))
        assertEquals("f", after.focused)
    }

    @Test
    fun aNumericFieldRefusesLetters() {
        val p = project(
            element(
                "f", ElementCatalog.INPUT_TEXTBOX, 0, 0, 120, 16,
                mapOf("numericOnly" to BoolValue(true)),
            ),
        )
        var demo = click(p, DemoState(), 60f, 8f)
        "1a2".forEach { demo = DemoRuntime.type(p, demo, it) }
        assertEquals("12", DemoRuntime.propsOf(p.elements.findById("f")!!, demo).string("value"))
    }

    // -- Scrolling ---------------------------------------------------------

    private fun listProject() = project(
        element(
            "list", ElementCatalog.CONTAINER_SCROLL, 0, 0, 200, 100,
            mapOf("contentLength" to IntValue(300)),
            children = listOf(
                element("row1", ElementCatalog.BUTTON_NORMAL, 4, 4, 180, 20),
                element("row9", ElementCatalog.BUTTON_NORMAL, 4, 260, 180, 20),
            ),
        ),
    )

    @Test
    fun aListScrollsAndStopsAtTheEnd() {
        val p = listProject()
        val scrolled = DemoRuntime.scrollBy(p, DemoState(), 100f, 50f, 60f)
        assertEquals(60, DemoRuntime.scrollOf(p.elements.findById("list")!!, scrolled))

        // 300 of content in a 100-tall window leaves 200 to travel, and no more.
        val far = DemoRuntime.scrollBy(p, scrolled, 100f, 50f, 10_000f)
        assertEquals(200, DemoRuntime.scrollOf(p.elements.findById("list")!!, far))
        assertTrue(
            DemoRuntime.scrollBy(p, far, 100f, 50f, 50f) === far,
            "scrolling past the end kept going",
        )
    }

    /**
     * A row that has been scrolled into view is clickable where it looks.
     *
     * The bug this catches is the one that makes a scrolling demo useless: the
     * bounds in the document say where a row was before the list moved, so
     * without the offset every tap lands on the wrong row - or on nothing.
     */
    @Test
    fun aScrolledRowIsHittableWhereItIsDrawn() {
        val p = listProject()
        assertNull(
            DemoRuntime.hitTest(p, DemoState(), 100f, 70f),
            "the far row was hittable before the list was scrolled to it",
        )
        // Scrolled to the end - 300 of content in a 100-tall window, so 200 -
        // the last row's 260..280 is drawn at 60..80.
        val scrolled = DemoRuntime.scrollBy(p, DemoState(), 100f, 50f, 999f)
        val hit = DemoRuntime.hitTest(p, scrolled, 100f, 70f)
        assertEquals("row9", hit?.id, "the row under the finger was not the row that is drawn there")
    }

    /**
     * A row past the bottom of an unscrolled list is not there yet.
     *
     * The catalog has always called this element a "clipped, scrollable region"
     * and the renderer never clipped it: a twelve-row list in a six-row window
     * drew all twelve, over whatever was beneath. So the one element whose
     * entire purpose is holding more than it can show was the one element that
     * could not show that.
     */
    @Test
    fun aRowBelowAnUnscrolledListIsNotHittable() {
        val p = listProject()
        assertNull(
            DemoRuntime.hitTest(p, DemoState(), 100f, 270f),
            "a row well past the bottom of the list was clickable through it",
        )
    }

    @Test
    fun aRowScrolledOutOfSightIsNotHittable() {
        val p = listProject()
        val scrolled = DemoRuntime.scrollBy(p, DemoState(), 100f, 50f, 200f)
        val hit = DemoRuntime.hitTest(p, scrolled, 100f, 10f)
        assertTrue(hit?.id != "row1", "a row scrolled off the top was still being clicked")
    }

    // -- What the demo must never do --------------------------------------

    /**
     * The demo is not an edit.
     *
     * Every change it makes lives in the [DemoState] layer; the project it was
     * given comes back untouched. If this ever fails, pressing a button in a
     * preview has started modifying the document, which is a data-loss bug and
     * not a rendering one.
     */
    @Test
    fun nothingTheDemoDoesTouchesTheDocument() {
        val p = project(
            element("t", ElementCatalog.BUTTON_TOGGLE, 0, 0, 80, 20),
            element("s", ElementCatalog.INPUT_SLIDER, 0, 40, 100, 20),
        )
        var demo = click(p, DemoState(), 40f, 10f)
        demo = DemoRuntime.pointerDown(p, demo, 90f, 50f)
        demo = DemoRuntime.pointerUp(p, demo, 90f, 50f)

        assertTrue(!p.elements.findById("t")!!.props.bool("value"), "the document's toggle was flipped")
        assertEquals(0.5f, p.elements.findById("s")!!.props.float("value"), "the document's slider moved")

        // ...but the project the demo *draws* has both changes in it.
        val shown = DemoRuntime.projectFor(p, demo)
        assertTrue(shown.elements.findById("t")!!.props.bool("value"))
        assertNotNull(shown.elements.findById("s"))
    }

    @Test
    fun resettingPutsEverythingBack() {
        val p = project(element("t", ElementCatalog.BUTTON_TOGGLE, 0, 0, 80, 20))
        val touched = click(p, DemoState(), 40f, 10f)
        assertTrue(!touched.isClean)
        assertTrue(DemoState().isClean)
        assertEquals(p, DemoRuntime.projectFor(p, DemoState()))
    }

    /** Pinning a state wins over whatever the demo thinks, for skin checking. */
    @Test
    fun aForcedStateOverridesTheDemo() {
        val p = project(element("b", ElementCatalog.BUTTON_NORMAL, 0, 0, 80, 20))
        val down = DemoRuntime.pointerDown(p, DemoState(), 40f, 10f)
        assertEquals(
            InteractionState.HOVER,
            DemoRuntime.stateFor(p.elements.findById("b")!!, down, InteractionState.HOVER),
        )
    }

    /** The thing on top is the thing you are pointing at. */
    @Test
    fun theTopmostWidgetWins() {
        val p = project(
            element("under", ElementCatalog.BUTTON_NORMAL, 0, 0, 100, 40),
            element("over", ElementCatalog.BUTTON_NORMAL, 20, 10, 40, 20),
        )
        assertEquals("over", DemoRuntime.hitTest(p, DemoState(), 40f, 20f)?.id)
        assertEquals("under", DemoRuntime.hitTest(p, DemoState(), 90f, 20f)?.id)
    }

    /** A turned element is clickable where it is, not where its box was. */
    @Test
    fun aTurnedWidgetIsHitAtItsRealAngle() {
        val p = project(
            element(
                "b", ElementCatalog.BUTTON_NORMAL, 50, 50, 100, 20,
                mapOf("rotation" to IntValue(90)),
            ),
        )
        // Turned a quarter, the button is 20 wide and 100 tall about its centre
        // at (100, 60): a point well above the untouched box is now on it...
        assertEquals("b", DemoRuntime.hitTest(p, DemoState(), 100f, 20f)?.id)
        // ...and a point that used to be near its end is now off it.
        assertNull(DemoRuntime.hitTest(p, DemoState(), 145f, 60f)?.id)
    }
}
