package com.mcguidesigner.core.editor

import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.catalog.ElementDefinition
import com.mcguidesigner.core.library.Prefab
import com.mcguidesigner.core.Branding
import com.mcguidesigner.core.model.AlignMode
import com.mcguidesigner.core.model.Anchor
import com.mcguidesigner.core.model.CanvasSpec
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.model.IntPoint
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.IntSize
import com.mcguidesigner.core.model.ProjectMeta
import com.mcguidesigner.core.model.PropValue
import com.mcguidesigner.core.model.ResizeHandle
import com.mcguidesigner.core.model.TargetForm
import com.mcguidesigner.core.model.TextureAsset
import com.mcguidesigner.core.model.absoluteBoundsMap
import com.mcguidesigner.core.model.findById
import com.mcguidesigner.core.model.insert
import com.mcguidesigner.core.model.pathTo
import com.mcguidesigner.core.model.removeAll
import com.mcguidesigner.core.model.siblingsOf
import com.mcguidesigner.core.model.updateAll
import com.mcguidesigner.core.model.updateById
import com.mcguidesigner.core.model.walkAll
import com.mcguidesigner.core.serialization.ProjectSerializer
import com.mcguidesigner.core.util.Ids
import com.mcguidesigner.core.validation.ProjectValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * All editing behaviour of the designer, independent of any UI toolkit.
 *
 * Desktop and Android both drive this exact object; the only thing they own is
 * how gestures and shortcuts map onto these calls.  Keeping the logic here is
 * what makes the two front-ends genuinely different in feel while staying
 * identical in behaviour.
 */
class EditorController(initial: GuiProject) {

    private val history = UndoHistory()
    private val _state = MutableStateFlow(
        EditorState.of(initial).let { it.copy(validation = ProjectValidator.validate(it.project)) },
    )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    val current: EditorState get() = _state.value
    val project: GuiProject get() = _state.value.project

    // -- Internal plumbing -------------------------------------------------

    /** Applies a change to non-document state; never recorded in history. */
    private fun touch(block: (EditorState) -> EditorState) {
        _state.value = block(_state.value)
    }

    /**
     * Applies a document edit: records undo, marks the project dirty and
     * re-runs validation.
     */
    private fun edit(
        label: String,
        coalesceKey: String? = null,
        block: (EditorState) -> EditorState,
    ) {
        val before = _state.value
        val after = block(before)
        if (after.project == before.project && after.selection == before.selection) return

        history.record(before.snapshot(label), coalesceKey)
        _state.value = after.copy(
            dirty = true,
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            undoLabel = history.undoLabel,
            redoLabel = history.redoLabel,
            validation = if (after.project != before.project) {
                ProjectValidator.validate(after.project)
            } else {
                after.validation
            },
        )
    }

    /** Ends the current coalescing gesture (call on pointer-up / blur). */
    fun endGesture() {
        history.breakCoalescing()
        touch { it.copy(interaction = Interaction.None) }
    }

    // -- Document lifecycle ------------------------------------------------

    fun replaceProject(next: GuiProject, path: String? = null, resetHistory: Boolean = true) {
        if (resetHistory) history.clear()
        _state.value = EditorState.of(next).copy(
            filePath = path,
            dirty = false,
            canUndo = if (resetHistory) false else history.canUndo,
            canRedo = if (resetHistory) false else history.canRedo,
            validation = ProjectValidator.validate(next),
            statusMessage = null,
            // Settings belong to the person using the editor, not to the
            // document, so opening another project must not reset them.
            settings = _state.value.settings,
        )
    }

    fun markSaved(path: String?) {
        touch { it.copy(dirty = false, filePath = path ?: it.filePath) }
    }

    /**
     * Flags the document as having edits that are not on disk.
     *
     * Needed when a document is restored from an autosave or a crash-recovery
     * snapshot: the content came back but it was never written to the user's
     * own file, so the editor must keep asking to save it.
     */
    fun markUnsaved() {
        touch { it.copy(dirty = true) }
    }

    fun setStatus(message: String?) = touch { it.copy(statusMessage = message) }

    fun renameProject(name: String) = edit("Rename project") { s ->
        s.copy(project = s.project.copy(name = name))
    }

    fun updateMeta(transform: (ProjectMeta) -> ProjectMeta) = edit("Edit project info") { s ->
        s.copy(project = s.project.copy(meta = transform(s.project.meta)))
    }

    fun updateCanvas(transform: (CanvasSpec) -> CanvasSpec) = edit("Change canvas") { s ->
        val canvas = transform(s.project.canvas)
        s.copy(
            project = s.project.copy(canvas = canvas),
            previewForm = canvas.targetForm,
        )
    }

    /**
     * Switches the project's edition.  Elements that do not exist in the new
     * edition are kept (so the change is reversible) but the validator will
     * flag them, and unsupported properties are reported rather than dropped.
     */
    fun switchEdition(edition: Edition) = edit("Switch to ${edition.displayName}") { s ->
        s.copy(project = s.project.copy(edition = edition))
    }

    // -- Undo / redo -------------------------------------------------------

    fun undo() {
        val snapshot = history.undo(_state.value.snapshot("")) ?: return
        applySnapshot(snapshot)
    }

    fun redo() {
        val snapshot = history.redo(_state.value.snapshot("")) ?: return
        applySnapshot(snapshot)
    }

    private fun applySnapshot(snapshot: HistorySnapshot) {
        val live = snapshot.selection.filter { snapshot.project.element(it) != null }.toSet()
        _state.value = _state.value.copy(
            project = snapshot.project,
            selection = live,
            primarySelection = live.firstOrNull(),
            interaction = Interaction.None,
            dirty = true,
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            undoLabel = history.undoLabel,
            redoLabel = history.redoLabel,
            validation = ProjectValidator.validate(snapshot.project),
        )
    }

    // -- Selection ---------------------------------------------------------

    fun select(id: String?, additive: Boolean = false, toggle: Boolean = false) = touch { s ->
        when {
            id == null -> s.copy(selection = emptySet(), primarySelection = null)
            toggle && id in s.selection -> {
                val next = s.selection - id
                s.copy(selection = next, primarySelection = next.firstOrNull())
            }

            additive || toggle -> s.copy(selection = s.selection + id, primarySelection = id)
            else -> s.copy(selection = setOf(id), primarySelection = id)
        }
    }

    fun selectMany(ids: Set<String>, additive: Boolean = false) = touch { s ->
        val next = if (additive) s.selection + ids else ids
        s.copy(selection = next, primarySelection = next.lastOrNull() ?: s.primarySelection)
    }

    fun selectAll() = touch { s ->
        val ids = s.project.elements.walkAll().filterNot { it.locked }.map { it.id }.toSet()
        s.copy(selection = ids, primarySelection = ids.firstOrNull())
    }

    fun clearSelection() = select(null)

    fun setHovered(id: String?) = touch { it.copy(hoveredId = id) }

    /** Selects every unlocked, visible element intersecting [rect]. */
    fun selectInRect(rect: IntRect, additive: Boolean = false) = touch { s ->
        val bounds = s.absoluteBounds
        val ids = s.project.elements.walkAll()
            .filter { !it.locked && it.visible }
            .filter { bounds[it.id]?.intersects(rect) == true }
            .map { it.id }
            .toSet()
        val next = if (additive) s.selection + ids else ids
        s.copy(selection = next, primarySelection = next.lastOrNull())
    }

    /**
     * Topmost element containing [point], honouring z-order, visibility and
     * lock state.  Returns null when the point hits empty canvas.
     */
    fun hitTest(point: IntPoint, includeLocked: Boolean = false): GuiElement? {
        val s = _state.value
        val bounds = s.absoluteBounds
        // Later elements paint on top, so search the flattened tree in reverse.
        return s.project.elements.walkAll().toList().asReversed().firstOrNull { element ->
            element.visible &&
                (includeLocked || !element.locked) &&
                bounds[element.id]?.contains(point) == true
        }
    }

    // -- Creation & deletion -----------------------------------------------

    /**
     * Creates a new element of [typeId] at [at] (canvas space).
     *
     * When [parentId] is null the controller looks for the deepest container
     * under the point so dropping a button onto a chest panel parents it
     * automatically.
     */
    fun addElement(
        typeId: String,
        at: IntPoint = IntPoint(0, 0),
        parentId: String? = null,
        centreOnPoint: Boolean = false,
        /**
         * Property values applied on top of the type's defaults.
         *
         * How a preset works: "Add a star" is the custom-shape type with
         * `shape=star` set, not a type of its own, so the catalog does not
         * grow an entry per shape.
         */
        initialProps: Map<String, PropValue> = emptyMap(),
        /** Name for the new element; defaults to the type's display name. */
        nameHint: String? = null,
        /** Size override, for presets whose natural shape is not the default. */
        sizeOverride: IntSize? = null,
    ): String? {
        val definition = ElementCatalog[typeId] ?: return null
        val s = _state.value
        val size = sizeOverride ?: definition.defaultSizeFor(s.edition)
        val resolvedParent = parentId ?: containerAt(at, excluding = emptySet())?.id
        val parentOrigin = resolvedParent?.let { s.absoluteBounds[it] } ?: IntRect.Zero

        val localX = at.x - parentOrigin.x - if (centreOnPoint) size.width / 2 else 0
        val localY = at.y - parentOrigin.y - if (centreOnPoint) size.height / 2 else 0
        val snapped = if (s.snapToGrid && s.project.canvas.gridSize > 0) {
            val g = s.project.canvas.gridSize
            IntPoint((localX.toFloat() / g).roundToInt() * g, (localY.toFloat() / g).roundToInt() * g)
        } else {
            IntPoint(localX, localY)
        }

        val takenNames = s.project.elements.walkAll().map { it.name }.toSet()
        val element = GuiElement(
            id = Ids.prefixed(typeId.substringAfterLast('.')),
            type = typeId,
            name = Ids.uniqueName(nameHint ?: definition.displayName, takenNames),
            bounds = IntRect(snapped.x, snapped.y, size.width, size.height),
            props = definition.defaultProps(s.edition) + initialProps,
        )

        edit("Add ${definition.displayName}") { st ->
            st.copy(
                project = st.project.withElements(st.project.elements.insert(element, resolvedParent)),
                selection = setOf(element.id),
                primarySelection = element.id,
                pendingPlacementType = null,
                tool = if (st.tool == EditorTool.PLACE) EditorTool.SELECT else st.tool,
                expandedInTree = resolvedParent?.let { st.expandedInTree + it } ?: st.expandedInTree,
            )
        }
        history.breakCoalescing()
        return element.id
    }

    /** Inserts a fully-formed element subtree (used by presets and paste). */
    fun insertElement(element: GuiElement, parentId: String? = null, index: Int = -1, label: String = "Insert") {
        edit(label) { s ->
            val fresh = regenerateIds(element, s.project.elements.walkAll().map { it.name }.toSet())
            s.copy(
                project = s.project.withElements(s.project.elements.insert(fresh, parentId, index)),
                selection = setOf(fresh.id),
                primarySelection = fresh.id,
            )
        }
        history.breakCoalescing()
    }

    fun deleteSelection() {
        val ids = _state.value.selection
        if (ids.isEmpty()) return
        edit(if (ids.size == 1) "Delete element" else "Delete ${ids.size} elements") { s ->
            s.copy(
                project = s.project.withElements(s.project.elements.removeAll(ids)),
                selection = emptySet(),
                primarySelection = null,
            )
        }
        history.breakCoalescing()
    }

    /**
     * Copies the selection, offset so the copy is visible rather than hidden
     * exactly on top of what it came from.  The distance comes from
     * [EditorSettings.duplicateOffset] unless a caller overrides it.
     */
    fun duplicateSelection(offset: IntPoint? = null) {
        val s = _state.value
        if (s.selection.isEmpty()) return
        val offset = offset ?: s.settings.duplicateOffset.let { IntPoint(it, it) }
        val taken = s.project.elements.walkAll().map { it.name }.toSet().toMutableSet()
        val newIds = mutableSetOf<String>()
        var elements = s.project.elements

        // Duplicate top-level selected nodes only: a selected child of a
        // selected parent already travels with its parent.
        val roots = topLevelSelection(s)
        for (id in roots) {
            val source = elements.findById(id) ?: continue
            val path = elements.pathTo(id) ?: continue
            val clone = regenerateIds(source, taken).let {
                it.copy(bounds = it.bounds.translated(offset.x, offset.y))
            }
            clone.walk().forEach { taken += it.name }
            newIds += clone.id
            val siblings = elements.siblingsOf(id)
            val index = siblings?.second?.plus(1) ?: -1
            elements = elements.insert(clone, path.parentId, index)
        }

        val next = elements
        edit(if (roots.size == 1) "Duplicate element" else "Duplicate ${roots.size} elements") { st ->
            st.copy(project = st.project.withElements(next), selection = newIds, primarySelection = newIds.firstOrNull())
        }
        history.breakCoalescing()
    }

    /** Selected ids whose ancestors are not themselves selected. */
    private fun topLevelSelection(s: EditorState): List<String> {
        val selection = s.selection
        return selection.filter { id ->
            val path = s.project.elements.pathTo(id) ?: return@filter false
            path.ids.dropLast(1).none { it in selection }
        }
    }

    private fun regenerateIds(element: GuiElement, takenNames: Set<String>): GuiElement {
        val name = Ids.uniqueName(element.name, takenNames)
        return element.copy(
            id = Ids.prefixed(element.type.substringAfterLast('.')),
            name = name,
            children = element.children.map { regenerateIds(it, takenNames + name) },
        )
    }

    // -- Geometry ----------------------------------------------------------

    fun moveSelection(dx: Int, dy: Int, coalesceKey: String? = "move") {
        if (dx == 0 && dy == 0) return
        val ids = topLevelSelection(_state.value).toSet()
        if (ids.isEmpty()) return
        edit("Move", coalesceKey) { s ->
            s.copy(
                project = s.project.withElements(
                    s.project.elements.updateAll(ids) { element ->
                        if (element.locked) element else element.copy(bounds = element.bounds.translated(dx, dy))
                    },
                ),
            )
        }
    }

    /**
     * Moves the selection by one configured step in a cardinal direction.
     *
     * The single place a "nudge" is defined: the move buttons on both
     * front-ends and the desktop arrow keys all come through here, so they
     * cannot drift apart from each other or from the setting.
     *
     * [dirX] and [dirY] are -1, 0 or 1.  Nudges coalesce into one undo entry
     * while they keep coming, so holding a key or tapping a button repeatedly
     * is a single step back rather than forty.
     */
    fun nudgeSelection(dirX: Int, dirY: Int, large: Boolean = false) {
        val settings = _state.value.settings
        val step = settings.stepFor(large)

        if (!settings.nudgeSnapsToGrid) {
            moveSelection(dirX * step, dirY * step, coalesceKey = "nudge")
            return
        }

        // Snapping mode moves to the next grid line in that direction rather
        // than by a fixed amount, so repeated presses walk the grid instead of
        // drifting off it.
        val grid = _state.value.project.canvas.gridSize
        if (grid <= 0) {
            moveSelection(dirX * step, dirY * step, coalesceKey = "nudge")
            return
        }
        val anchor = _state.value.selectionBounds
            ?: run { moveSelection(dirX * step, dirY * step, coalesceKey = "nudge"); return }

        moveSelection(
            dx = gridDelta(anchor.x, dirX, grid),
            dy = gridDelta(anchor.y, dirY, grid),
            coalesceKey = "nudge",
        )
    }

    /**
     * Offset that lands [position] on the next grid line towards [direction].
     *
     * `floorDiv` rather than `/` throughout: integer division truncates
     * towards zero, so at x = -5 with an 8px grid the "previous line" would
     * come out as 0 - above the element rather than below it - and a nudge
     * left would move it right. Elements are allowed to sit at negative
     * coordinates (anything hanging off the canvas edge does), so this is a
     * real position and not a defensive check.
     */
    private fun gridDelta(position: Int, direction: Int, grid: Int): Int = when {
        direction == 0 -> 0
        direction > 0 -> {
            val next = (position.floorDiv(grid) + 1) * grid
            (next - position).coerceAtLeast(1)
        }
        else -> {
            // Already on a line: step to the one before it rather than staying.
            val previous = if (position.mod(grid) == 0) position - grid else position.floorDiv(grid) * grid
            (previous - position).coerceAtMost(-1)
        }
    }

    /** Replaces the tunable editor settings, clamping anything out of range. */
    fun setSettings(settings: EditorSettings) = touch { it.copy(settings = settings.sanitised()) }

    fun updateSettings(transform: (EditorSettings) -> EditorSettings) =
        setSettings(transform(_state.value.settings))

    fun setBounds(id: String, bounds: IntRect, coalesceKey: String? = null, label: String = "Resize") {
        val definition = _state.value.project.element(id)?.let { ElementCatalog[it.type] } ?: return
        val clamped = bounds.withSize(bounds.size.coerceIn(definition.minSize, definition.maxSize))
        edit(label, coalesceKey) { s ->
            s.copy(project = s.project.withElements(s.project.elements.updateById(id) { it.copy(bounds = clamped) }))
        }
    }

    fun setAnchor(id: String, anchor: Anchor) = edit("Change anchor") { s ->
        s.copy(project = s.project.withElements(s.project.elements.updateById(id) { it.copy(anchor = anchor) }))
    }

    /**
     * Applies a resize gesture in canvas space, running the result through
     * snapping and the definition's min/max constraints.
     */
    fun resizeBy(id: String, handle: ResizeHandle, dx: Int, dy: Int, coalesceKey: String? = "resize") {
        val s = _state.value
        val element = s.project.element(id) ?: return
        if (element.locked) return
        val definition = ElementCatalog[element.type] ?: return
        if (!definition.resizable) return

        val start = (s.interaction as? Interaction.Resizing)
            ?.takeIf { it.elementId == id }
            ?.startBounds
            ?: element.bounds

        val proposed = Snapping.applyResize(
            start = start,
            handle = handle,
            dx = dx,
            dy = dy,
            minSize = definition.minSize,
            maxSize = definition.maxSize,
            keepAspect = definition.keepAspect,
        )

        val snap = if (s.snapToGrid || s.snapToElements) {
            Snapping.forResize(
                resized = proposed,
                handle = handle,
                others = otherAbsoluteBounds(s, setOf(id)),
                guides = s.guides,
                canvas = s.project.canvas.size,
                gridSize = s.project.canvas.gridSize,
                snapToGrid = s.snapToGrid,
                snapToElements = s.snapToElements,
                threshold = snapThreshold(s),
            )
        } else {
            SnapResult.None
        }

        val snapped = Snapping.applyResize(
            start = start,
            handle = handle,
            dx = dx + snap.dx,
            dy = dy + snap.dy,
            minSize = definition.minSize,
            maxSize = definition.maxSize,
            keepAspect = definition.keepAspect,
        )
        setBounds(id, snapped, coalesceKey)
    }

    /** Begins a resize gesture, capturing the starting rectangle. */
    fun beginResize(id: String, handle: ResizeHandle) = touch { s ->
        val element = s.project.element(id) ?: return@touch s
        s.copy(interaction = Interaction.Resizing(id, handle, element.bounds))
    }

    fun beginDrag(ids: Set<String>, grabOffsetX: Int = 0, grabOffsetY: Int = 0) = touch { s ->
        val startBounds = ids.mapNotNull { id -> s.project.element(id)?.let { id to it.bounds } }.toMap()
        s.copy(interaction = Interaction.Dragging(ids, startBounds, grabOffsetX, grabOffsetY))
    }

    fun beginMarquee(x: Int, y: Int) = touch { it.copy(interaction = Interaction.Marquee(x, y, x, y)) }

    fun updateMarquee(x: Int, y: Int) = touch { s ->
        val marquee = s.interaction as? Interaction.Marquee ?: return@touch s
        s.copy(interaction = marquee.copy(currentX = x, currentY = y))
    }

    fun commitMarquee(additive: Boolean = false) {
        val marquee = _state.value.interaction as? Interaction.Marquee ?: return
        selectInRect(marquee.rect, additive)
        endGesture()
    }

    /**
     * Drags the current selection by a canvas-space delta measured from the
     * gesture's start position, applying snapping to the primary element.
     */
    fun dragSelectionTo(totalDx: Int, totalDy: Int): SnapResult {
        val s = _state.value
        val drag = s.interaction as? Interaction.Dragging ?: return SnapResult.None
        val anchorId = s.primarySelection?.takeIf { it in drag.elementIds } ?: drag.elementIds.firstOrNull()
        ?: return SnapResult.None
        val anchorStartLocal = drag.startBounds[anchorId] ?: return SnapResult.None
        val anchorAbsolute = s.absoluteBounds[anchorId] ?: return SnapResult.None

        val proposedAbsolute = anchorAbsolute
            .withPosition(
                IntPoint(
                    anchorAbsolute.x - (s.project.element(anchorId)?.bounds?.x ?: 0) + anchorStartLocal.x + totalDx,
                    anchorAbsolute.y - (s.project.element(anchorId)?.bounds?.y ?: 0) + anchorStartLocal.y + totalDy,
                ),
            )

        val snap = if (s.snapToGrid || s.snapToElements) {
            Snapping.forMove(
                moving = proposedAbsolute,
                others = otherAbsoluteBounds(s, drag.elementIds),
                guides = s.guides,
                canvas = s.project.canvas.size,
                gridSize = s.project.canvas.gridSize,
                snapToGrid = s.snapToGrid,
                snapToElements = s.snapToElements,
                threshold = snapThreshold(s),
            )
        } else {
            SnapResult.None
        }

        val dx = totalDx + snap.dx
        val dy = totalDy + snap.dy
        edit("Move", coalesceKey = "drag") { st ->
            st.copy(
                project = st.project.withElements(
                    st.project.elements.updateAll(drag.elementIds) { element ->
                        val start = drag.startBounds[element.id] ?: element.bounds
                        if (element.locked) element else element.copy(bounds = start.translated(dx, dy))
                    },
                ),
            )
        }
        return snap
    }

    private fun otherAbsoluteBounds(s: EditorState, excluding: Set<String>): List<IntRect> =
        s.absoluteBounds.filterKeys { it !in excluding }.values.toList()

    /** Snap tolerance in GUI pixels, derived from zoom so it feels constant. */
    private fun snapThreshold(s: EditorState): Int =
        max(1, (SNAP_SCREEN_PIXELS / s.zoom.coerceAtLeast(0.1f)).roundToInt())

    private fun containerAt(point: IntPoint, excluding: Set<String>): GuiElement? {
        val s = _state.value
        val bounds = s.absoluteBounds
        return s.project.elements.walkAll().toList().asReversed().firstOrNull { element ->
            element.id !in excluding &&
                element.visible &&
                !element.locked &&
                ElementCatalog[element.type]?.acceptsChildren == true &&
                bounds[element.id]?.contains(point) == true
        }
    }

    // -- Alignment & distribution -----------------------------------------

    fun align(mode: AlignMode) {
        val s = _state.value
        val ids = topLevelSelection(s)
        if (ids.size < 2 && mode.ordinal < AlignMode.DISTRIBUTE_HORIZONTAL.ordinal) {
            // Aligning a single element aligns it to the canvas instead.
            alignToCanvas(mode)
            return
        }
        if (ids.size < 2) return

        val absolute = s.absoluteBounds
        val rects = ids.mapNotNull { id -> absolute[id]?.let { id to it } }
        if (rects.size < 2) return
        val union = IntRect.bounds(rects.map { it.second })

        val deltas: Map<String, IntPoint> = when (mode) {
            AlignMode.LEFT -> rects.associate { (id, r) -> id to IntPoint(union.left - r.left, 0) }
            AlignMode.RIGHT -> rects.associate { (id, r) -> id to IntPoint(union.right - r.right, 0) }
            AlignMode.HORIZONTAL_CENTER -> rects.associate { (id, r) -> id to IntPoint(union.centerX - r.centerX, 0) }
            AlignMode.TOP -> rects.associate { (id, r) -> id to IntPoint(0, union.top - r.top) }
            AlignMode.BOTTOM -> rects.associate { (id, r) -> id to IntPoint(0, union.bottom - r.bottom) }
            AlignMode.VERTICAL_CENTER -> rects.associate { (id, r) -> id to IntPoint(0, union.centerY - r.centerY) }
            AlignMode.DISTRIBUTE_HORIZONTAL -> distribute(rects, horizontal = true, union = union)
            AlignMode.DISTRIBUTE_VERTICAL -> distribute(rects, horizontal = false, union = union)
        }

        edit("Align: ${mode.displayName}") { st ->
            st.copy(
                project = st.project.withElements(
                    st.project.elements.updateAll(deltas.keys) { element ->
                        val d = deltas[element.id] ?: IntPoint.Zero
                        if (element.locked) element else element.copy(bounds = element.bounds.translated(d.x, d.y))
                    },
                ),
            )
        }
        history.breakCoalescing()
    }

    private fun distribute(
        rects: List<Pair<String, IntRect>>,
        horizontal: Boolean,
        union: IntRect,
    ): Map<String, IntPoint> {
        if (rects.size < 3) return emptyMap()
        val sorted = rects.sortedBy { if (horizontal) it.second.left else it.second.top }
        val totalExtent = if (horizontal) union.width else union.height
        val occupied = sorted.sumOf { if (horizontal) it.second.width else it.second.height }
        val gap = (totalExtent - occupied).toFloat() / (sorted.size - 1)
        var cursor = (if (horizontal) union.left else union.top).toFloat()
        val out = LinkedHashMap<String, IntPoint>()
        for ((id, rect) in sorted) {
            val target = cursor.roundToInt()
            out[id] = if (horizontal) IntPoint(target - rect.left, 0) else IntPoint(0, target - rect.top)
            cursor += (if (horizontal) rect.width else rect.height) + gap
        }
        return out
    }

    private fun alignToCanvas(mode: AlignMode) {
        val s = _state.value
        val id = s.primarySelection ?: return
        val element = s.project.element(id) ?: return
        val parentSize = parentSizeOf(s, id)
        val next = when (mode) {
            AlignMode.LEFT -> element.bounds.withPosition(IntPoint(0, element.bounds.y))
            AlignMode.RIGHT -> element.bounds.withPosition(IntPoint(parentSize.width - element.bounds.width, element.bounds.y))
            AlignMode.HORIZONTAL_CENTER ->
                element.bounds.withPosition(IntPoint((parentSize.width - element.bounds.width) / 2, element.bounds.y))

            AlignMode.TOP -> element.bounds.withPosition(IntPoint(element.bounds.x, 0))
            AlignMode.BOTTOM -> element.bounds.withPosition(IntPoint(element.bounds.x, parentSize.height - element.bounds.height))
            AlignMode.VERTICAL_CENTER ->
                element.bounds.withPosition(IntPoint(element.bounds.x, (parentSize.height - element.bounds.height) / 2))

            else -> return
        }
        setBounds(id, next, label = "Align to container: ${mode.displayName}")
        history.breakCoalescing()
    }

    private fun parentSizeOf(s: EditorState, id: String): IntSize {
        val parentId = s.project.elements.pathTo(id)?.parentId ?: return s.project.canvas.size
        return s.project.element(parentId)?.bounds?.size ?: s.project.canvas.size
    }

    // -- Z-order & hierarchy ----------------------------------------------

    fun bringToFront() = reorderSelection("Bring to front") { siblings, index -> siblings.size - 1 }

    fun sendToBack() = reorderSelection("Send to back") { _, _ -> 0 }

    fun bringForward() = reorderSelection("Bring forward") { siblings, index -> min(siblings.size - 1, index + 1) }

    fun sendBackward() = reorderSelection("Send backward") { _, index -> max(0, index - 1) }

    private fun reorderSelection(label: String, targetIndex: (List<GuiElement>, Int) -> Int) {
        val s = _state.value
        val ids = topLevelSelection(s)
        if (ids.isEmpty()) return
        var elements = s.project.elements
        for (id in ids) {
            val (siblings, index) = elements.siblingsOf(id) ?: continue
            val target = targetIndex(siblings, index).coerceIn(0, siblings.size - 1)
            if (target == index) continue
            val parentId = elements.pathTo(id)?.parentId
            val node = siblings[index]
            elements = elements.removeAll(setOf(id)).insert(node, parentId, target)
        }
        val next = elements
        edit(label) { st -> st.copy(project = st.project.withElements(next)) }
        history.breakCoalescing()
    }

    /** Moves [id] into [newParentId] at [index]; null parent means the root. */
    fun reparent(id: String, newParentId: String?, index: Int = -1) {
        val s = _state.value
        val node = s.project.element(id) ?: return
        // Refuse to move a node inside its own subtree.
        if (newParentId != null && node.walk().any { it.id == newParentId }) return
        if (newParentId != null && ElementCatalog[s.project.element(newParentId)?.type ?: ""]?.acceptsChildren != true) return

        val oldAbsolute = s.absoluteBounds[id] ?: node.bounds
        val newParentAbsolute = newParentId?.let { s.absoluteBounds[it] } ?: IntRect.Zero
        val rebased = node.copy(
            bounds = node.bounds.withPosition(
                IntPoint(oldAbsolute.x - newParentAbsolute.x, oldAbsolute.y - newParentAbsolute.y),
            ),
        )

        edit("Reparent element") { st ->
            st.copy(
                project = st.project.withElements(
                    st.project.elements.removeAll(setOf(id)).insert(rebased, newParentId, index),
                ),
                expandedInTree = newParentId?.let { st.expandedInTree + it } ?: st.expandedInTree,
            )
        }
        history.breakCoalescing()
    }

    fun toggleExpanded(id: String) = touch { s ->
        s.copy(expandedInTree = if (id in s.expandedInTree) s.expandedInTree - id else s.expandedInTree + id)
    }

    // -- Element properties ------------------------------------------------

    fun rename(id: String, name: String) = edit("Rename element", coalesceKey = "rename:$id") { s ->
        s.copy(project = s.project.withElements(s.project.elements.updateById(id) { it.copy(name = name) }))
    }

    fun setVisible(id: String, visible: Boolean) = edit(if (visible) "Show element" else "Hide element") { s ->
        s.copy(project = s.project.withElements(s.project.elements.updateById(id) { it.copy(visible = visible) }))
    }

    fun setLocked(id: String, locked: Boolean) = edit(if (locked) "Lock element" else "Unlock element") { s ->
        s.copy(project = s.project.withElements(s.project.elements.updateById(id) { it.copy(locked = locked) }))
    }

    /**
     * Writes a property value.  When [state] is not
     * [InteractionState.NORMAL] the value is stored as a state override so the
     * base appearance is left untouched.
     */
    fun setProp(
        id: String,
        key: String,
        value: PropValue,
        state: InteractionState = InteractionState.NORMAL,
        coalesceKey: String? = "prop:$id:$key:${state.name}",
    ) {
        val element = _state.value.project.element(id) ?: return
        val spec = ElementCatalog[element.type]?.property(key, _state.value.edition)
        val coerced = spec?.coerce(value) ?: value
        edit("Edit ${spec?.label ?: key}", coalesceKey) { s ->
            s.copy(
                project = s.project.withElements(
                    s.project.elements.updateById(id) { it.withStateProp(state, key, coerced) },
                ),
            )
        }
    }

    /** Applies one property value to every selected element that supports it. */
    fun setPropOnSelection(key: String, value: PropValue, state: InteractionState = InteractionState.NORMAL) {
        val s = _state.value
        val ids = s.selection.filter { id ->
            val type = s.project.element(id)?.type ?: return@filter false
            ElementCatalog[type]?.property(key, s.edition) != null
        }.toSet()
        if (ids.isEmpty()) return
        edit("Edit $key on ${ids.size} elements") { st ->
            st.copy(
                project = st.project.withElements(
                    st.project.elements.updateAll(ids) { it.withStateProp(state, key, value) },
                ),
            )
        }
        history.breakCoalescing()
    }

    fun clearStateOverride(id: String, key: String, state: InteractionState) = edit("Reset $key override") { s ->
        s.copy(project = s.project.withElements(s.project.elements.updateById(id) { it.clearStateProp(state, key) }))
    }

    // -- Textures ----------------------------------------------------------

    fun addTexture(asset: TextureAsset): String {
        edit("Import texture '${asset.name}'") { s ->
            s.copy(project = s.project.copy(textures = s.project.textures + asset))
        }
        history.breakCoalescing()
        return asset.id
    }

    /**
     * Imports several textures as a single undoable step.
     *
     * Importing a resource pack can add dozens at once, and forcing the user to
     * press Ctrl+Z forty times to undo one mistaken import is not undo, it is a
     * punishment.  Assets whose id is already present are skipped.
     */
    fun addTextures(assets: List<TextureAsset>): Int {
        if (assets.isEmpty()) return 0
        val existing = _state.value.project.textures.map { it.id }.toSet()
        val fresh = assets.filterNot { it.id in existing }
        if (fresh.isEmpty()) return 0
        edit(if (fresh.size == 1) "Import texture '${fresh.first().name}'" else "Import ${fresh.size} textures") { s ->
            s.copy(project = s.project.copy(textures = s.project.textures + fresh))
        }
        history.breakCoalescing()
        return fresh.size
    }

    fun updateTexture(id: String, transform: (TextureAsset) -> TextureAsset) = edit("Edit texture") { s ->
        s.copy(
            project = s.project.copy(
                textures = s.project.textures.map { if (it.id == id) transform(it) else it },
            ),
        )
    }

    /**
     * Deletes a texture and clears every reference to it, so the document can
     * never contain a dangling asset id.
     */
    fun removeTexture(id: String) {
        edit("Delete texture") { s ->
            val cleared = s.project.elements.let { elements ->
                fun scrub(nodes: List<GuiElement>): List<GuiElement> = nodes.map { node ->
                    val props = node.props.mapValues { (_, v) ->
                        if (v is com.mcguidesigner.core.model.TextureValue && v.assetId == id) {
                            com.mcguidesigner.core.model.TextureValue(null)
                        } else {
                            v
                        }
                    }
                    val overrides = node.stateOverrides.mapValues { (_, map) ->
                        map.mapValues { (_, v) ->
                            if (v is com.mcguidesigner.core.model.TextureValue && v.assetId == id) {
                                com.mcguidesigner.core.model.TextureValue(null)
                            } else {
                                v
                            }
                        }
                    }
                    node.copy(props = props, stateOverrides = overrides, children = scrub(node.children))
                }
                scrub(elements)
            }
            s.copy(
                project = s.project.copy(
                    elements = cleared,
                    textures = s.project.textures.filterNot { it.id == id },
                ),
            )
        }
        history.breakCoalescing()
    }

    // -- View state --------------------------------------------------------

    fun setTool(tool: EditorTool) = touch { it.copy(tool = tool, pendingPlacementType = null) }

    fun armPlacement(typeId: String?) = touch {
        it.copy(pendingPlacementType = typeId, tool = if (typeId == null) EditorTool.SELECT else EditorTool.PLACE)
    }

    fun setViewMode(mode: ViewMode) = touch { it.copy(viewMode = mode) }

    fun setPreviewState(state: InteractionState) = touch { it.copy(previewState = state) }

    fun setPreviewForm(form: TargetForm) = touch {
        it.copy(previewForm = form, showSafeArea = form == TargetForm.MOBILE)
    }

    fun setZoom(zoom: Float) = touch { it.copy(zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)) }

    fun zoomBy(factor: Float) = setZoom(_state.value.zoom * factor)

    fun setPan(x: Float, y: Float) = touch { it.copy(panX = x, panY = y) }

    /**
     * Pans by a view-space delta.
     *
     * Pass the viewport and the canvas cannot be dragged off the screen -
     * without it there is nothing to clamp against, so the caller that knows
     * the viewport should always supply it.
     */
    fun panBy(dx: Float, dy: Float, viewportWidth: Float = 0f, viewportHeight: Float = 0f) = touch {
        it.copy(panX = it.panX + dx, panY = it.panY + dy)
            .withPanClampedTo(viewportWidth, viewportHeight)
    }

    /**
     * Zooms by [factor] while keeping the canvas point under ([focalX], [focalY])
     * where it is.
     *
     * This is what makes a pinch or a Ctrl+wheel feel attached to the content:
     * zooming about the canvas centre instead slides whatever you were looking
     * at out from under your fingers, and the further you are from the centre
     * the worse it gets.
     */
    fun zoomAround(
        factor: Float,
        focalX: Float,
        focalY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
    ) = touch { s ->
        if (viewportWidth <= 0f || viewportHeight <= 0f) {
            return@touch s.copy(zoom = (s.zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM))
        }
        val from = s.zoom
        val to = (from * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (to == from) return@touch s

        val canvas = s.project.canvas
        // origin = (viewport - canvas * zoom) / 2 + pan, so the canvas-space
        // point under the focus is (focus - origin) / zoom. Solving for the pan
        // that puts that same point back under the focus at the new zoom:
        val originX = (viewportWidth - canvas.width * from) / 2f + s.panX
        val originY = (viewportHeight - canvas.height * from) / 2f + s.panY
        val canvasX = (focalX - originX) / from
        val canvasY = (focalY - originY) / from

        s.copy(
            zoom = to,
            panX = focalX - canvasX * to - (viewportWidth - canvas.width * to) / 2f,
            panY = focalY - canvasY * to - (viewportHeight - canvas.height * to) / 2f,
        ).withPanClampedTo(viewportWidth, viewportHeight)
    }

    /**
     * Holds the canvas against the viewport so it can never be scrolled away
     * entirely.
     *
     * A canvas smaller than the viewport is free to move anywhere inside it but
     * not past the edges; a canvas larger than the viewport must always cover
     * at least [MIN_CANVAS_ON_SCREEN] of it. Losing the design off the edge of
     * the screen, with nothing on it to drag back, is not a state worth being
     * able to reach.
     */
    private fun EditorState.withPanClampedTo(viewportWidth: Float, viewportHeight: Float): EditorState {
        if (viewportWidth <= 0f || viewportHeight <= 0f) return this

        fun clamp(pan: Float, canvasLength: Int, viewportLength: Float): Float {
            val scaled = canvasLength * zoom
            if (scaled <= 0f) return pan
            val keep = min(scaled, viewportLength * MIN_CANVAS_ON_SCREEN)
            val centred = (viewportLength - scaled) / 2f
            // origin = centred + pan, and we need
            //   origin + scaled >= keep   and   origin <= viewportLength - keep
            return pan.coerceIn(keep - scaled - centred, viewportLength - keep - centred)
        }

        return copy(
            panX = clamp(panX, project.canvas.width, viewportWidth),
            panY = clamp(panY, project.canvas.height, viewportHeight),
        )
    }

    fun resetView() = touch { it.copy(zoom = 3f, panX = 0f, panY = 0f) }

    /** Fits the canvas into a viewport of [viewportWidth] x [viewportHeight] px. */
    fun zoomToFit(viewportWidth: Float, viewportHeight: Float, padding: Float = 48f) = touch { s ->
        val canvas = s.project.canvas
        if (canvas.width <= 0 || canvas.height <= 0) return@touch s
        val scale = min(
            (viewportWidth - padding) / canvas.width,
            (viewportHeight - padding) / canvas.height,
        ).coerceIn(MIN_ZOOM, MAX_ZOOM)
        s.copy(zoom = scale, panX = 0f, panY = 0f)
    }

    fun toggleGrid() = touch { it.copy(showGrid = !it.showGrid) }
    fun toggleSnapToGrid() = touch { it.copy(snapToGrid = !it.snapToGrid) }
    fun toggleSnapToElements() = touch { it.copy(snapToElements = !it.snapToElements) }
    fun toggleRulers() = touch { it.copy(showRulers = !it.showRulers) }
    fun toggleGuides() = touch { it.copy(showGuides = !it.showGuides) }
    fun toggleSafeArea() = touch { it.copy(showSafeArea = !it.showSafeArea) }

    fun addGuide(vertical: Boolean, position: Int) = touch { s ->
        s.copy(guides = s.guides + Guide(Ids.prefixed("guide"), vertical, position))
    }

    fun moveGuide(id: String, position: Int) = touch { s ->
        s.copy(guides = s.guides.map { if (it.id == id) it.copy(position = position) else it })
    }

    fun removeGuide(id: String) = touch { s -> s.copy(guides = s.guides.filterNot { it.id == id }) }

    fun clearGuides() = touch { it.copy(guides = emptyList()) }

    fun revalidate() = touch { it.copy(validation = ProjectValidator.validate(it.project)) }

    // -- Clipboard ---------------------------------------------------------

    /** Serialises the selection so it can go onto the system clipboard. */
    fun copySelectionToText(): String? {
        val s = _state.value
        val roots = topLevelSelection(s).mapNotNull { s.project.element(it) }
        if (roots.isEmpty()) return null
        return ProjectSerializer.compact.encodeToString(ListSerializer(GuiElement.serializer()), roots)
    }

    /** Parses clipboard text produced by [copySelectionToText] and inserts it. */
    fun pasteFromText(text: String, at: IntPoint? = null, parentId: String? = null): Boolean {
        val nodes = runCatching {
            ProjectSerializer.compact.decodeFromString(ListSerializer(GuiElement.serializer()), text)
        }.getOrNull() ?: return false
        if (nodes.isEmpty()) return false

        val s = _state.value
        val origin = at ?: IntPoint(nodes.first().bounds.x + 8, nodes.first().bounds.y + 8)
        val baseX = nodes.minOf { it.bounds.x }
        val baseY = nodes.minOf { it.bounds.y }
        val taken = s.project.elements.walkAll().map { it.name }.toSet().toMutableSet()

        var elements = s.project.elements
        val newIds = mutableSetOf<String>()
        for (node in nodes) {
            val clone = regenerateIds(node, taken).let {
                it.copy(
                    bounds = it.bounds.withPosition(
                        IntPoint(origin.x + (node.bounds.x - baseX), origin.y + (node.bounds.y - baseY)),
                    ),
                )
            }
            clone.walk().forEach { taken += it.name }
            newIds += clone.id
            elements = elements.insert(clone, parentId)
        }

        val next = elements
        edit("Paste ${nodes.size} element(s)") { st ->
            st.copy(project = st.project.withElements(next), selection = newIds, primarySelection = newIds.firstOrNull())
        }
        history.breakCoalescing()
        return true
    }

    // -- Prefabs -----------------------------------------------------------

    /**
     * Captures the current selection as a reusable prefab.
     *
     * Only top-level selected nodes are captured: a child that is selected
     * alongside its parent is already part of that parent's subtree, and
     * capturing it twice would duplicate it on every insert.
     */
    fun prefabFromSelection(
        name: String,
        description: String = "",
        tags: List<String> = emptyList(),
        createdAtMillis: Long = 0L,
    ): Prefab? {
        val s = _state.value
        val roots = topLevelSelection(s).mapNotNull { s.project.element(it) }
        if (roots.isEmpty()) return null
        return Prefab.fromElements(
            id = Ids.prefixed("prefab"),
            name = name.ifBlank { roots.first().name },
            edition = s.edition,
            roots = roots,
            absoluteBounds = s.absoluteBounds,
            projectTextures = s.project.textures,
            description = description,
            tags = tags,
            createdAtMillis = createdAtMillis,
        )
    }

    /**
     * Drops [prefab] onto the canvas at [at], as one undoable step.
     *
     * Any texture the prefab needs that this project does not have is imported
     * alongside it, so a prefab built in another project arrives fully skinned
     * rather than with blank buttons.  Textures already present under the same
     * id are left alone - the project's own copy wins.
     */
    fun insertPrefab(
        prefab: Prefab,
        at: IntPoint = IntPoint(0, 0),
        parentId: String? = null,
        centreOnPoint: Boolean = false,
    ): Set<String> {
        if (prefab.isEmpty) return emptySet()
        val s = _state.value

        val parentOrigin = parentId?.let { s.absoluteBounds[it] } ?: IntRect.Zero
        val size = prefab.size
        val originX = at.x - parentOrigin.x - if (centreOnPoint) size.width / 2 else 0
        val originY = at.y - parentOrigin.y - if (centreOnPoint) size.height / 2 else 0

        val taken = s.project.elements.walkAll().map { it.name }.toSet().toMutableSet()
        val newIds = mutableSetOf<String>()
        var elements = s.project.elements
        for (root in prefab.elements) {
            val clone = regenerateIds(root, taken).let {
                it.copy(bounds = it.bounds.translated(originX, originY))
            }
            clone.walk().forEach { taken += it.name }
            newIds += clone.id
            elements = elements.insert(clone, parentId)
        }

        val known = s.project.textures.map { it.id }.toSet()
        val missing = prefab.textures.filterNot { it.id in known }
        val next = elements

        edit("Insert '${prefab.name}'") { st ->
            st.copy(
                project = st.project
                    .withElements(next)
                    .copy(textures = st.project.textures + missing),
                selection = newIds,
                primarySelection = newIds.firstOrNull(),
                expandedInTree = parentId?.let { st.expandedInTree + it } ?: st.expandedInTree,
            )
        }
        history.breakCoalescing()
        return newIds
    }

    companion object {
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 16f

        /**
         * Least of the viewport the canvas must keep covering when panned.
         *
         * A fifth is enough to always leave something to grab and to make it
         * obvious which way the rest of the design lies.
         */
        const val MIN_CANVAS_ON_SCREEN = 0.2f

        /** Snap tolerance expressed in *screen* pixels. */
        const val SNAP_SCREEN_PIXELS = 7f

        fun blank(edition: Edition, name: String = "Untitled Screen"): EditorController =
            EditorController(newProject(edition, name))

        fun newProject(
            edition: Edition,
            name: String = "Untitled Screen",
            form: TargetForm = if (edition == Edition.BEDROCK) TargetForm.MOBILE else TargetForm.DESKTOP,
        ): GuiProject {
            val canvas = when {
                edition == Edition.JAVA -> CanvasSpec(width = 176, height = 166, guiScale = 3, gridSize = 8)
                form == TargetForm.MOBILE -> CanvasSpec(
                    width = 320, height = 180, guiScale = 3, gridSize = 4,
                    targetForm = TargetForm.MOBILE,
                    safeArea = com.mcguidesigner.core.model.Insets.symmetric(12, 8),
                )

                else -> CanvasSpec(width = 320, height = 240, guiScale = 3, gridSize = 4, targetForm = form)
            }
            return GuiProject(
                id = Ids.prefixed("project"),
                name = name,
                edition = edition,
                canvas = canvas,
                meta = ProjectMeta(
                    namespace = "mcgui",
                    screenId = Ids.slug(name),
                    description = "Created with ${Branding.NAME}.",
                ),
            )
        }

        /** Definitions available for the palette in [edition]. */
        fun paletteFor(edition: Edition): List<ElementDefinition> = ElementCatalog.forEdition(edition)
    }
}
