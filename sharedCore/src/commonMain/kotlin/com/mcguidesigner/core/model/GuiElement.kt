package com.mcguidesigner.core.model

import kotlinx.serialization.Serializable

/**
 * A single node of the GUI tree.
 *
 * Elements form an ordered tree.  Within a parent, list order *is* z-order:
 * index 0 paints first (furthest back), the last child paints last (on top).
 * Containers such as `container.scroll` and `panel.frame` accept children;
 * every other type keeps an empty list.
 */
@Serializable
data class GuiElement(
    val id: String,
    /** Catalog type id, e.g. `button.normal`. See `ElementCatalog`. */
    val type: String,
    val name: String,
    /** Bounds relative to the parent's content box, in GUI pixels. */
    val bounds: IntRect,
    val anchor: Anchor = Anchor.TOP_LEFT,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val props: Map<String, PropValue> = emptyMap(),
    /**
     * Per-state property overrides, keyed by [InteractionState].  Only the
     * properties that actually differ from [props] are stored, so a project
     * that never customises its hover state stays small.
     */
    val stateOverrides: Map<InteractionState, Map<String, PropValue>> = emptyMap(),
    val children: List<GuiElement> = emptyList(),
) {
    /** Effective properties for a given interaction state. */
    fun propsFor(state: InteractionState): Map<String, PropValue> =
        if (state == InteractionState.NORMAL || stateOverrides[state].isNullOrEmpty()) {
            props
        } else {
            props + stateOverrides.getValue(state)
        }

    fun withProp(key: String, value: PropValue): GuiElement = copy(props = props + (key to value))

    fun withStateProp(state: InteractionState, key: String, value: PropValue): GuiElement {
        if (state == InteractionState.NORMAL) return withProp(key, value)
        val current = stateOverrides[state].orEmpty()
        return copy(stateOverrides = stateOverrides + (state to (current + (key to value))))
    }

    fun clearStateProp(state: InteractionState, key: String): GuiElement {
        val current = stateOverrides[state] ?: return this
        val next = current - key
        return copy(
            stateOverrides = if (next.isEmpty()) stateOverrides - state else stateOverrides + (state to next),
        )
    }

    /** Depth-first walk including this node. */
    fun walk(): Sequence<GuiElement> = sequence {
        yield(this@GuiElement)
        children.forEach { yieldAll(it.walk()) }
    }
}

/** Result of locating an element inside a tree. */
data class ElementPath(
    /** Ids from the root down to (and including) the element. */
    val ids: List<String>,
) {
    val elementId: String get() = ids.last()
    val parentId: String? get() = ids.dropLast(1).lastOrNull()
    val depth: Int get() = ids.size - 1
}

// --- Tree helpers ----------------------------------------------------------

/** Depth-first traversal of a forest. */
fun List<GuiElement>.walkAll(): Sequence<GuiElement> = asSequence().flatMap { it.walk() }

fun List<GuiElement>.findById(id: String): GuiElement? = walkAll().firstOrNull { it.id == id }

/** Locates the path to [id], or null when it is not in the tree. */
fun List<GuiElement>.pathTo(id: String): ElementPath? {
    fun search(nodes: List<GuiElement>, prefix: List<String>): ElementPath? {
        for (node in nodes) {
            val path = prefix + node.id
            if (node.id == id) return ElementPath(path)
            search(node.children, path)?.let { return it }
        }
        return null
    }
    return search(this, emptyList())
}

/** Applies [transform] to the element with [id], rebuilding the tree around it. */
fun List<GuiElement>.updateById(id: String, transform: (GuiElement) -> GuiElement): List<GuiElement> =
    map { node ->
        when {
            node.id == id -> transform(node)
            node.children.isEmpty() -> node
            else -> node.copy(children = node.children.updateById(id, transform))
        }
    }

/** Applies [transform] to every element whose id is in [ids]. */
fun List<GuiElement>.updateAll(ids: Set<String>, transform: (GuiElement) -> GuiElement): List<GuiElement> {
    if (ids.isEmpty()) return this
    return map { node ->
        val updated = if (node.id in ids) transform(node) else node
        if (updated.children.isEmpty()) updated
        else updated.copy(children = updated.children.updateAll(ids, transform))
    }
}

/** Removes every element in [ids] (and, implicitly, their subtrees). */
fun List<GuiElement>.removeAll(ids: Set<String>): List<GuiElement> =
    filterNot { it.id in ids }.map { it.copy(children = it.children.removeAll(ids)) }

/** Inserts [element] into [parentId]'s children at [index], or into the root. */
fun List<GuiElement>.insert(element: GuiElement, parentId: String?, index: Int = -1): List<GuiElement> {
    if (parentId == null) {
        val target = if (index in 0..size) index else size
        return toMutableList().apply { add(target, element) }
    }
    return updateById(parentId) { parent ->
        val target = if (index in 0..parent.children.size) index else parent.children.size
        parent.copy(children = parent.children.toMutableList().apply { add(target, element) })
    }
}

/** Siblings of [id] in tree order, plus the index of [id] among them. */
fun List<GuiElement>.siblingsOf(id: String): Pair<List<GuiElement>, Int>? {
    val index = indexOfFirst { it.id == id }
    if (index >= 0) return this to index
    for (node in this) {
        node.children.siblingsOf(id)?.let { return it }
    }
    return null
}

/** Absolute (canvas-space) bounds of [id], resolving anchors of every ancestor. */
fun List<GuiElement>.absoluteBounds(id: String, canvas: IntSize): IntRect? {
    fun search(nodes: List<GuiElement>, container: IntSize, origin: IntPoint): IntRect? {
        for (node in nodes) {
            val local = node.anchor.resolve(container, node.bounds.size, node.bounds.position)
            val abs = IntRect(origin.x + local.x, origin.y + local.y, node.bounds.width, node.bounds.height)
            if (node.id == id) return abs
            search(node.children, node.bounds.size, IntPoint(abs.x, abs.y))?.let { return it }
        }
        return null
    }
    return search(this, canvas, IntPoint.Zero)
}

/** Absolute bounds of every element in the tree, keyed by id. */
fun List<GuiElement>.absoluteBoundsMap(canvas: IntSize): Map<String, IntRect> {
    val out = LinkedHashMap<String, IntRect>()
    fun walk(nodes: List<GuiElement>, container: IntSize, origin: IntPoint) {
        for (node in nodes) {
            val local = node.anchor.resolve(container, node.bounds.size, node.bounds.position)
            val abs = IntRect(origin.x + local.x, origin.y + local.y, node.bounds.width, node.bounds.height)
            out[node.id] = abs
            walk(node.children, node.bounds.size, IntPoint(abs.x, abs.y))
        }
    }
    walk(this, canvas, IntPoint.Zero)
    return out
}
