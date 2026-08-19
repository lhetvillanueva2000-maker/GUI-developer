package com.mcguidesigner.core.editor

import com.mcguidesigner.core.model.GuiProject

/**
 * One restorable point in time.  Selection travels with the document so undo
 * puts the user back where they were, not just what they had.
 */
data class HistorySnapshot(
    val project: GuiProject,
    val selection: Set<String>,
    val label: String,
)

/**
 * Snapshot-based undo/redo.
 *
 * Because [GuiProject] is a persistent immutable tree, a "snapshot" shares
 * almost all of its structure with its neighbours - only the spine of the
 * changed subtree is re-allocated.  That makes whole-document snapshots both
 * cheaper and considerably less error-prone than inverse-command undo, which
 * has to get every mutation's inverse exactly right.
 *
 * Continuous gestures (dragging, resizing, typing in a text field) pass a
 * `coalesceKey`; consecutive edits sharing a key collapse into a single undo
 * step so one drag is one Ctrl+Z.
 */
class UndoHistory(private val limit: Int = 250) {

    private val undoStack = ArrayDeque<HistorySnapshot>()
    private val redoStack = ArrayDeque<HistorySnapshot>()
    private var lastCoalesceKey: String? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Label of the edit that [undo] would revert, for the Edit menu. */
    val undoLabel: String? get() = undoStack.lastOrNull()?.label
    val redoLabel: String? get() = redoStack.lastOrNull()?.label

    val depth: Int get() = undoStack.size

    /**
     * Records the state *before* an edit is applied.
     *
     * @param coalesceKey when equal to the previous call's key the edit is
     *   folded into the previous undo step instead of creating a new one.
     */
    fun record(before: HistorySnapshot, coalesceKey: String? = null) {
        redoStack.clear()
        if (coalesceKey != null && coalesceKey == lastCoalesceKey && undoStack.isNotEmpty()) {
            // Same continuous gesture: the snapshot already on the stack is
            // the correct "before" state, so nothing else to do.
            return
        }
        undoStack.addLast(before)
        while (undoStack.size > limit) undoStack.removeFirst()
        lastCoalesceKey = coalesceKey
    }

    /**
     * Ends the current gesture so the next edit always starts a fresh undo
     * step.  Call this on pointer-up, focus loss, or any discrete command.
     */
    fun breakCoalescing() {
        lastCoalesceKey = null
    }

    fun undo(current: HistorySnapshot): HistorySnapshot? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        lastCoalesceKey = null
        return previous
    }

    fun redo(current: HistorySnapshot): HistorySnapshot? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        lastCoalesceKey = null
        return next
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        lastCoalesceKey = null
    }
}
