package com.mcguidesigner.core.editor

import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.model.BoolValue
import com.mcguidesigner.core.model.FloatValue
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.IntValue
import com.mcguidesigner.core.model.PointF
import com.mcguidesigner.core.model.PropValue
import com.mcguidesigner.core.model.Rotation
import com.mcguidesigner.core.model.StringValue
import com.mcguidesigner.core.model.bool
import com.mcguidesigner.core.model.findById
import com.mcguidesigner.core.model.int
import com.mcguidesigner.core.model.float
import com.mcguidesigner.core.model.pathTo
import com.mcguidesigner.core.model.string
import com.mcguidesigner.core.model.stringList
import com.mcguidesigner.core.model.walkAll

/**
 * Everything the demo has done to the screen since it was opened.
 *
 * A plain immutable value, and deliberately not a mutable session object: the
 * whole point of this state is that it is *not* the document. Pressing a button
 * in the demo must never dirty the project, never enter the undo history and
 * never survive the tab being closed, and the cheapest way to guarantee all
 * three is for the demo's changes to live somewhere the document cannot see.
 *
 * [overrides] is that somewhere - property values layered over the real ones at
 * draw time, keyed by element id. A toggle that has been flipped has
 * `"value" to BoolValue(true)` here; the element in the project still says
 * false, and closing the tab throws the layer away.
 */
data class DemoState(
    val overrides: Map<String, Map<String, PropValue>> = emptyMap(),
    /** Under the pointer. */
    val hovered: String? = null,
    /** Held down. */
    val pressed: String? = null,
    /** Has the keyboard, for the text fields. */
    val focused: String? = null,
    /** What is being dragged, and on which axis it started. */
    val dragging: String? = null,
    /** The last thing that happened, for the readout. */
    val lastAction: String? = null,
    /** How many things have happened, so a repeat still reads as new. */
    val actionCount: Int = 0,
) {
    val isClean: Boolean get() = overrides.isEmpty() && actionCount == 0

    internal fun override(id: String, key: String, value: PropValue): DemoState =
        copy(overrides = overrides + (id to (overrides[id].orEmpty() + (key to value))))

    internal fun announce(text: String): DemoState =
        copy(lastAction = text, actionCount = actionCount + 1)
}

/** An element the pointer landed on, and where on it. */
data class DemoHit(
    val id: String,
    /** Absolute canvas-space bounds, with any enclosing scroll applied. */
    val rect: IntRect,
    /** Where in the element the pointer is, 0..1 across and down. */
    val fractionX: Float,
    val fractionY: Float,
)

/**
 * The preview, made to actually run.
 *
 * A picture of a screen answers one question - does it look right - and people
 * open the preview to answer a different one: does it *work*. Is that button
 * big enough to hit, does the list scroll far enough to reach the last row, is
 * the switch obviously on when it is on, does the layout still hold when the
 * text field has something in it. None of that is visible in a still, and all
 * of it is visible the moment the screen responds.
 *
 * So the demo is a tiny widget runtime over the same tree the editor edits, and
 * over the same skins that draw it: pressing, toggling, cycling, dragging,
 * scrolling and typing all happen by writing a property into [DemoState] and
 * letting the existing renderer draw what it already knows how to draw. That is
 * why this file is arithmetic rather than a second set of widgets - a demo made
 * out of real Compose controls would look like the demo and not like the design,
 * which would make it worthless for the one thing it is for.
 *
 * It is deliberately in `sharedCore`, with no Compose in sight: desktop and
 * Android drive it from completely different gesture recognisers, and the
 * behaviour has to be the same on both or the demo is lying to one of them.
 */
object DemoRuntime {

    /** Types the demo will pick up a pointer for, beyond the catalog's own. */
    private val SCROLLABLE = setOf(ElementCatalog.CONTAINER_SCROLL)

    // -- Reading -----------------------------------------------------------

    /** The project as the demo has it: every override folded into the tree. */
    fun projectFor(project: GuiProject, demo: DemoState): GuiProject {
        if (demo.overrides.isEmpty()) return project
        fun fold(nodes: List<GuiElement>): List<GuiElement> = nodes.map { element ->
            val extra = demo.overrides[element.id]
            val children = if (element.children.isEmpty()) element.children else fold(element.children)
            when {
                extra == null && children === element.children -> element
                extra == null -> element.copy(children = children)
                else -> element.copy(props = element.props + extra, children = children)
            }
        }
        return project.copy(elements = fold(project.elements))
    }

    /**
     * How one element should be drawn.
     *
     * [forced] is the preview's old behaviour - pin everything to hover, or to
     * disabled, and look at the skin. It is still worth having and still there,
     * so passing it wins over anything the demo thinks.
     */
    fun stateFor(element: GuiElement, demo: DemoState, forced: InteractionState? = null): InteractionState {
        forced?.let { return it }
        val definition = ElementCatalog[element.type]
        if (definition?.interactive != true) return InteractionState.NORMAL
        if (!propsOf(element, demo).bool("enabled", true)) return InteractionState.DISABLED
        return when (element.id) {
            demo.pressed -> InteractionState.PRESSED
            demo.focused -> InteractionState.FOCUSED
            demo.hovered -> InteractionState.HOVER
            else -> InteractionState.NORMAL
        }
    }

    /** Every property of [element], with the demo's own layered on top. */
    fun propsOf(element: GuiElement, demo: DemoState): Map<String, PropValue> =
        demo.overrides[element.id]?.let { element.props + it } ?: element.props

    /** How far a scroll container has been scrolled, in GUI pixels. */
    fun scrollOf(element: GuiElement, demo: DemoState): Int = propsOf(element, demo).int("scrollOffset", 0)

    // -- Hit testing -------------------------------------------------------

    /**
     * The topmost thing the demo would react to at a canvas-space point.
     *
     * Walked in reverse paint order, because the last thing drawn is the thing
     * on top and therefore the thing a person believes they are pointing at.
     * The offset of any enclosing scroll container is carried down as the walk
     * descends, which is what makes a scrolled row hittable where it *looks*
     * like it is rather than where its untouched bounds say it is.
     */
    fun hitTest(
        project: GuiProject,
        demo: DemoState,
        x: Float,
        y: Float,
        /** Include containers that only scroll; false for taps. */
        includeScrollable: Boolean = false,
    ): DemoHit? {
        fun walk(nodes: List<GuiElement>, originX: Int, originY: Int, offsetX: Int, offsetY: Int): DemoHit? {
            for (element in nodes.asReversed()) {
                if (!element.visible) continue
                val rect = IntRect(
                    originX + element.bounds.x + offsetX,
                    originY + element.bounds.y + offsetY,
                    element.bounds.width,
                    element.bounds.height,
                )
                if (element.children.isNotEmpty()) {
                    val scroll = if (element.type == ElementCatalog.CONTAINER_SCROLL) {
                        scrollOf(element, demo)
                    } else {
                        0
                    }
                    val axis = element.props.string("direction", "vertical")
                    val childHit = walk(
                        element.children,
                        rect.x, rect.y,
                        if (axis == "horizontal" || axis == "both") -scroll else 0,
                        if (axis == "horizontal") 0 else -scroll,
                    )
                    // A child of a scroll container that has been scrolled out
                    // of sight is not on screen, so it is not hittable either.
                    if (childHit != null && (scroll == 0 || overlaps(childHit.rect, rect))) return childHit
                }
                if (!reactsToPointer(element, includeScrollable)) continue
                val local = unrotate(element, rect, x, y)
                if (!contains(rect, local.x, local.y)) continue
                return DemoHit(
                    id = element.id,
                    rect = rect,
                    fractionX = ((local.x - rect.x) / rect.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                    fractionY = ((local.y - rect.y) / rect.height.coerceAtLeast(1)).coerceIn(0f, 1f),
                )
            }
            return null
        }
        return walk(project.elements, 0, 0, 0, 0)
    }

    private fun overlaps(inner: IntRect, outer: IntRect): Boolean =
        inner.x + inner.width > outer.x && inner.x < outer.x + outer.width &&
            inner.y + inner.height > outer.y && inner.y < outer.y + outer.height

    private fun contains(rect: IntRect, x: Float, y: Float): Boolean =
        x >= rect.x && y >= rect.y && x <= rect.x + rect.width && y <= rect.y + rect.height

    /**
     * The pointer, moved into the element's own unturned frame.
     *
     * The skin draws a turned element by rotating about its centre, so the only
     * honest hit test is the same rotation run backwards. Without it a element
     * at 45 degrees is clickable in a box that is visibly not where it is.
     */
    private fun unrotate(element: GuiElement, rect: IntRect, x: Float, y: Float): PointF {
        val degrees = Rotation.normalise(element.props.int("rotation", 0))
        if (degrees == 0) return PointF(x, y)
        val centre = PointF(rect.x + rect.width / 2f, rect.y + rect.height / 2f)
        return Rotation.rotate(PointF(x, y), centre, -degrees.toFloat())
    }

    private fun reactsToPointer(element: GuiElement, includeScrollable: Boolean): Boolean {
        if (element.type in SCROLLABLE) return includeScrollable
        return ElementCatalog[element.type]?.interactive == true
    }

    // -- Pointer -----------------------------------------------------------

    fun hover(demo: DemoState, id: String?): DemoState =
        if (demo.hovered == id) demo else demo.copy(hovered = id)

    /**
     * A pointer went down.
     *
     * The press is registered here and the *action* happens on release, which
     * is how every real toolkit behaves and is not a detail: it is what lets a
     * person press a button, realise it is the wrong one, slide off it and
     * have nothing happen.
     */
    fun pointerDown(project: GuiProject, demo: DemoState, x: Float, y: Float): DemoState {
        val hit = hitTest(project, demo, x, y) ?: return demo.copy(focused = null, pressed = null)
        val element = project.elements.findById(hit.id) ?: return demo
        if (!propsOf(element, demo).bool("enabled", true)) return demo.copy(pressed = null)

        // A slider follows the finger from the moment it lands, like every
        // slider anybody has ever used - waiting for a drag would make a tap on
        // the track do nothing.
        if (element.type == ElementCatalog.INPUT_SLIDER) {
            return demo
                .copy(pressed = hit.id, dragging = hit.id, focused = null)
                .override(hit.id, "value", FloatValue(knobValue(hit)))
        }
        return demo.copy(pressed = hit.id, dragging = null)
    }

    /** The pointer moved with a button down. Only sliders care. */
    fun pointerDrag(project: GuiProject, demo: DemoState, x: Float, y: Float): DemoState {
        val id = demo.dragging ?: run {
            // Not dragging anything: the press is only still live while the
            // pointer is on the thing that was pressed.
            val over = hitTest(project, demo, x, y)?.id
            return if (over == demo.pressed) demo else demo.copy(pressed = null)
        }
        val element = project.elements.findById(id) ?: return demo
        val rect = absoluteRect(project, demo, id) ?: return demo
        val fraction = when (element.props.string("orientation", "horizontal")) {
            "vertical" -> ((y - rect.y) / rect.height.coerceAtLeast(1)).coerceIn(0f, 1f)
            else -> ((x - rect.x) / rect.width.coerceAtLeast(1)).coerceIn(0f, 1f)
        }
        return demo.override(id, "value", FloatValue(fraction))
    }

    /** The pointer lifted: whatever was pressed, if it is still pressed, fires. */
    fun pointerUp(project: GuiProject, demo: DemoState, x: Float, y: Float): DemoState {
        val pressed = demo.pressed ?: return demo.copy(dragging = null)
        val released = demo.copy(pressed = null, dragging = null)
        if (demo.dragging != null) {
            val element = project.elements.findById(pressed)
            val percent = ((released.overrides[pressed]?.float("value", 0f) ?: 0f) * 100).toInt()
            return released.announce("${element?.name ?: "Slider"} → $percent%")
        }
        val stillOn = hitTest(project, demo, x, y)?.id
        if (stillOn != pressed) return released
        return activate(project, released, pressed)
    }

    /** A press that was interrupted - a second finger, a scroll, leaving the canvas. */
    fun cancelPointer(demo: DemoState): DemoState =
        if (demo.pressed == null && demo.dragging == null) demo else demo.copy(pressed = null, dragging = null)

    // -- Activation --------------------------------------------------------

    /**
     * What a widget *does* when it is clicked.
     *
     * Every branch here changes something a person can see, which is the whole
     * bar this has to clear. A demo where the button only darkens for a frame
     * has not answered whether the button works; it has animated.
     */
    fun activate(project: GuiProject, demo: DemoState, id: String): DemoState {
        val element = project.elements.findById(id) ?: return demo
        val props = propsOf(element, demo)
        val label = element.name.ifBlank { ElementCatalog[element.type]?.displayName ?: "Element" }

        return when (element.type) {
            ElementCatalog.BUTTON_TOGGLE -> {
                val next = !props.bool("value", false)
                demo.override(id, "value", BoolValue(next))
                    .announce("$label → ${if (next) props.string("onLabel", "ON") else props.string("offLabel", "OFF")}")
            }

            ElementCatalog.INPUT_CHECKBOX -> {
                val next = !props.bool("checked", false)
                demo.override(id, "checked", BoolValue(next))
                    .announce("$label ${if (next) "checked" else "unchecked"}")
            }

            // A tab is only a tab because the others in its group turn off.
            // Selecting one without deselecting its siblings is the single most
            // common thing a static preview gets wrong about a tab strip.
            ElementCatalog.BUTTON_TAB -> {
                val group = props.int("groupIndex", 0)
                var next = demo
                project.elements.walkAll().forEach { other ->
                    if (other.type != ElementCatalog.BUTTON_TAB) return@forEach
                    val otherProps = propsOf(other, next)
                    if (otherProps.int("groupIndex", 0) != group) return@forEach
                    next = next.override(other.id, "selected", BoolValue(other.id == id))
                }
                next.announce("$label selected")
            }

            // Cycles, in both open modes. Vanilla Java options cycle on click,
            // and cycling is the honest thing to offer here anyway: it reaches
            // every option and it needs no popup that the skins do not draw.
            ElementCatalog.INPUT_DROPDOWN -> {
                val items = props.stringList("items")
                if (items.isEmpty()) return demo.announce("$label has no options")
                val next = (props.int("selectedIndex", 0) + 1) % items.size
                demo.override(id, "selectedIndex", IntValue(next))
                    .announce("$label → ${items[next]}")
            }

            // Tapping the clear cross empties it; tapping anywhere else takes
            // the keyboard.
            ElementCatalog.INPUT_SEARCH, ElementCatalog.INPUT_TEXTBOX -> {
                demo.copy(focused = id).announce("$label focused")
            }

            else -> demo.announce("$label pressed")
        }
    }

    // -- Keyboard ----------------------------------------------------------

    /** A character typed into whichever field has the keyboard. */
    fun type(project: GuiProject, demo: DemoState, character: Char): DemoState {
        val id = demo.focused ?: return demo
        val element = project.elements.findById(id) ?: return demo
        val props = propsOf(element, demo)
        if (!props.bool("editable", true)) return demo
        if (props.bool("numericOnly", false) && !character.isDigit() && character != '-' && character != '.') return demo
        val limit = props.int("maxLength", 32).coerceAtLeast(1)
        val next = (props.string("value") + character).take(limit)
        return demo.override(id, "value", StringValue(next))
    }

    fun backspace(project: GuiProject, demo: DemoState): DemoState {
        val id = demo.focused ?: return demo
        val element = project.elements.findById(id) ?: return demo
        val props = propsOf(element, demo)
        return demo.override(id, "value", StringValue(props.string("value").dropLast(1)))
    }

    fun blur(demo: DemoState): DemoState = if (demo.focused == null) demo else demo.copy(focused = null)

    // -- Scrolling ---------------------------------------------------------

    /**
     * A wheel notch, or a drag, over a scroll container.
     *
     * Clamped to the content: scrolling past the end is how a demo makes a list
     * look longer than it is, and the question being asked is whether the list
     * is long enough.
     */
    fun scrollBy(project: GuiProject, demo: DemoState, x: Float, y: Float, delta: Float): DemoState {
        val hit = hitTest(project, demo, x, y, includeScrollable = true) ?: return demo
        val container = enclosingScroller(project, hit.id) ?: return demo
        val props = propsOf(container, demo)
        val axis = props.string("direction", "vertical")
        val visible = if (axis == "horizontal") container.bounds.width else container.bounds.height
        val content = props.int("contentLength", 240).coerceAtLeast(visible)
        val limit = (content - visible).coerceAtLeast(0)
        val current = props.int("scrollOffset", 0)
        val next = (current + delta).toInt().coerceIn(0, limit)
        if (next == current) return demo
        return demo.override(container.id, "scrollOffset", IntValue(next))
    }

    /** [id] itself if it scrolls, else the nearest ancestor that does. */
    private fun enclosingScroller(project: GuiProject, id: String): GuiElement? {
        val path = project.elements.pathTo(id) ?: return null
        for (ancestorId in path.ids.asReversed()) {
            val element = project.elements.findById(ancestorId) ?: continue
            if (element.type == ElementCatalog.CONTAINER_SCROLL) return element
        }
        return null
    }

    // -- Geometry ----------------------------------------------------------

    /** Absolute bounds of one element with the demo's scrolling applied. */
    fun absoluteRect(project: GuiProject, demo: DemoState, id: String): IntRect? {
        val path = project.elements.pathTo(id) ?: return null
        var x = 0
        var y = 0
        var rect: IntRect? = null
        var nodes = project.elements
        for (step in path.ids) {
            val element = nodes.firstOrNull { it.id == step } ?: return null
            x += element.bounds.x
            y += element.bounds.y
            rect = IntRect(x, y, element.bounds.width, element.bounds.height)
            if (element.type == ElementCatalog.CONTAINER_SCROLL) {
                val scroll = scrollOf(element, demo)
                when (element.props.string("direction", "vertical")) {
                    "horizontal" -> x -= scroll
                    "both" -> { x -= scroll; y -= scroll }
                    else -> y -= scroll
                }
            }
            nodes = element.children
        }
        return rect
    }

    /**
     * Where a tap on a slider puts the knob.
     *
     * Straight from the tap position rather than from a drag delta, so the knob
     * lands under the finger on the first frame instead of crawling towards it.
     */
    private fun knobValue(hit: DemoHit): Float = hit.fractionX
}
