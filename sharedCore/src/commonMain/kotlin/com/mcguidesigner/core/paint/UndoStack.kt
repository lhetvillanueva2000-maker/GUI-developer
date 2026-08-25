package com.mcguidesigner.core.paint

/**
 * Undo that records the tiles a stroke touched, not the canvas it touched them
 * on.
 *
 * The obvious implementation - copy the layer before each stroke - is 16MB per
 * step on a 2048-square canvas, so a thirty-step history is half a gigabyte and
 * the app is killed on the fourth stroke. This one copies 64x64 tiles, and only
 * the ones actually written to, so a short dab costs about 16KB and a stroke
 * across the whole canvas costs the same as the full copy would have.
 *
 * The tile size is a compromise nobody notices either way: smaller tiles track
 * a thin line more tightly and spend more on bookkeeping, larger ones do the
 * reverse. 64 is where a one-pixel diagonal line across a large canvas stops
 * costing more than the naive copy would.
 *
 * Structural edits - adding, deleting, reordering, merging layers - are not
 * tile edits, so they are recorded as their own kind of step. Mixing the two
 * into one "just snapshot everything" mechanism is what makes undo in small
 * paint apps skip steps or resurrect deleted layers.
 */
class UndoStack(private val limit: Int = 40) {

    companion object {
        const val TILE = 64
    }

    /** One reversible thing. */
    private sealed interface Step {
        val label: String
    }

    /** Pixels inside one layer changed. */
    private class PixelStep(
        override val label: String,
        val layerId: String,
        val tiles: MutableMap<Int, IntArray> = LinkedHashMap(),
        /** Filled in on commit, so redo has something to go back to. */
        var after: MutableMap<Int, IntArray>? = null,
        val tilesAcross: Int,
        val width: Int,
        val height: Int,
    ) : Step

    /**
     * The layer list itself changed.
     *
     * Whole layers, by reference, because a deleted layer has to come back with
     * its pixels and there is nothing smaller that would do. These are rare -
     * one per structural action - so the cost is bounded by how fast a person
     * can press buttons rather than by how fast they can draw.
     */
    private class StructureStep(
        override val label: String,
        val before: List<PaintLayer>,
        val beforeActive: Int,
        val after: List<PaintLayer>,
        val afterActive: Int,
    ) : Step

    private val undoSteps = ArrayDeque<Step>()
    private val redoSteps = ArrayDeque<Step>()

    private var recording: PixelStep? = null

    val canUndo: Boolean get() = undoSteps.isNotEmpty()
    val canRedo: Boolean get() = redoSteps.isNotEmpty()
    val undoLabel: String? get() = undoSteps.lastOrNull()?.label
    val redoLabel: String? get() = redoSteps.lastOrNull()?.label

    /** Approximate bytes held, for the diagnostics screen. */
    val approximateBytes: Long
        get() = undoSteps.sumOf { step ->
            when (step) {
                is PixelStep -> (step.tiles.size + (step.after?.size ?: 0)).toLong() * TILE * TILE * 4L
                is StructureStep -> (step.before.size + step.after.size).toLong() * 64L
            }
        }

    // -- Recording ---------------------------------------------------------

    /**
     * Opens a step. Every [touch] until [commit] belongs to it.
     *
     * Nested calls are ignored rather than treated as an error: a tool that
     * begins a stroke inside another tool's stroke is a bug in the caller, but
     * throwing here would lose the user's work to make that point.
     */
    fun begin(label: String, layer: PaintLayer) {
        if (recording != null) return
        recording = PixelStep(
            label = label,
            layerId = layer.id,
            tilesAcross = (layer.width + TILE - 1) / TILE,
            width = layer.width,
            height = layer.height,
        )
    }

    /**
     * Records the current contents of every tile overlapping the given
     * rectangle, if they have not been recorded already in this step.
     *
     * Call this *before* writing. It is idempotent within a step, so a tool
     * that touches the same tile on every one of a thousand dabs pays for the
     * copy once.
     */
    fun touch(layer: PaintLayer, left: Int, top: Int, right: Int, bottom: Int) {
        val step = recording ?: return
        if (step.layerId != layer.id) return
        val x0 = (left.coerceAtLeast(0)) / TILE
        val y0 = (top.coerceAtLeast(0)) / TILE
        val x1 = (right.coerceAtMost(layer.width - 1)) / TILE
        val y1 = (bottom.coerceAtMost(layer.height - 1)) / TILE
        if (x1 < x0 || y1 < y0) return
        for (ty in y0..y1) {
            for (tx in x0..x1) {
                val key = ty * step.tilesAcross + tx
                if (step.tiles.containsKey(key)) continue
                step.tiles[key] = readTile(layer, tx, ty)
            }
        }
    }

    /** [touch] for a whole layer, for operations with no useful bounds. */
    fun touchAll(layer: PaintLayer) = touch(layer, 0, 0, layer.width - 1, layer.height - 1)

    /**
     * Closes the step and pushes it.
     *
     * A step that touched nothing is dropped rather than pushed, so tapping the
     * canvas without moving does not fill the history with no-ops that make
     * undo appear broken.
     */
    fun commit(layer: PaintLayer) {
        val step = recording ?: return
        recording = null
        if (step.tiles.isEmpty()) return
        if (step.layerId != layer.id) return

        // Keep only the tiles that really changed. A soft brush's bounding box
        // always covers more tiles than its stamp actually alters.
        val after = LinkedHashMap<Int, IntArray>()
        val kept = LinkedHashMap<Int, IntArray>()
        for ((key, before) in step.tiles) {
            val tx = key % step.tilesAcross
            val ty = key / step.tilesAcross
            val now = readTile(layer, tx, ty)
            if (!now.contentEquals(before)) {
                kept[key] = before
                after[key] = now
            }
        }
        if (kept.isEmpty()) return

        step.tiles.clear()
        step.tiles.putAll(kept)
        step.after = after
        push(step)
    }

    /** Abandons the open step without pushing it. */
    fun cancel() {
        recording = null
    }

    /**
     * What [index] held when the open step began.
     *
     * This is what makes a live stroke preview possible without a second full
     * copy of the layer. A stroke's coverage accumulates with `max`, so the
     * painted result has to be computed from the layer as it was *before* the
     * stroke - recomputing from the layer as it is now would compound every
     * frame and turn a 30% brush opaque in about a second of dragging.
     *
     * The tiles are already here for undo. Reading them back costs nothing and
     * saves the several megabytes a dedicated stroke backup would need.
     *
     * Returns the layer's current value for anything not yet recorded, which is
     * the right answer: an untouched pixel has not changed.
     */
    fun originalAt(layer: PaintLayer, index: Int): Int {
        val step = recording ?: return layer.pixels[index]
        if (step.layerId != layer.id) return layer.pixels[index]
        val x = index % layer.width
        val y = index / layer.width
        val tile = step.tiles[(y / TILE) * step.tilesAcross + (x / TILE)]
            ?: return layer.pixels[index]
        return tile[(y % TILE) * TILE + (x % TILE)]
    }

    /**
     * Records a structural change around [action].
     *
     * The layer list is captured before and after, so undo restores the exact
     * arrangement rather than trying to invert the operation.
     */
    fun structural(label: String, document: PaintDocument, action: () -> Unit) {
        val before = document.layers.toList()
        val beforeActive = document.activeIndex
        action()
        val after = document.layers.toList()
        val afterActive = document.activeIndex
        if (before == after && beforeActive == afterActive) return
        push(StructureStep(label, before, beforeActive, after, afterActive))
    }

    private fun push(step: Step) {
        undoSteps.addLast(step)
        redoSteps.clear()
        while (undoSteps.size > limit) undoSteps.removeFirst()
    }

    // -- Replaying ---------------------------------------------------------

    /** Reverts one step. Returns false when there was nothing to revert. */
    fun undo(document: PaintDocument): Boolean {
        val step = undoSteps.removeLastOrNull() ?: return false
        apply(document, step, forward = false)
        redoSteps.addLast(step)
        return true
    }

    fun redo(document: PaintDocument): Boolean {
        val step = redoSteps.removeLastOrNull() ?: return false
        apply(document, step, forward = true)
        undoSteps.addLast(step)
        return true
    }

    private fun apply(document: PaintDocument, step: Step, forward: Boolean) {
        when (step) {
            is PixelStep -> {
                val layer = document.layers.firstOrNull { it.id == step.layerId } ?: return
                val source = if (forward) step.after ?: return else step.tiles
                for ((key, data) in source) {
                    writeTile(layer, key % step.tilesAcross, key / step.tilesAcross, data)
                }
                document.activeIndex = document.layers.indexOf(layer)
            }

            is StructureStep -> {
                val target = if (forward) step.after else step.before
                document.layers.clear()
                document.layers.addAll(target)
                document.activeIndex = if (forward) step.afterActive else step.beforeActive
            }
        }
    }

    fun clear() {
        undoSteps.clear()
        redoSteps.clear()
        recording = null
    }

    // -- Tiles -------------------------------------------------------------

    private fun readTile(layer: PaintLayer, tx: Int, ty: Int): IntArray {
        val out = IntArray(TILE * TILE)
        val x0 = tx * TILE
        val y0 = ty * TILE
        val rows = minOf(TILE, layer.height - y0)
        val cols = minOf(TILE, layer.width - x0)
        for (row in 0 until rows) {
            val src = (y0 + row) * layer.width + x0
            layer.pixels.copyInto(out, row * TILE, src, src + cols)
        }
        return out
    }

    private fun writeTile(layer: PaintLayer, tx: Int, ty: Int, data: IntArray) {
        val x0 = tx * TILE
        val y0 = ty * TILE
        val rows = minOf(TILE, layer.height - y0)
        val cols = minOf(TILE, layer.width - x0)
        for (row in 0 until rows) {
            val dst = (y0 + row) * layer.width + x0
            data.copyInto(layer.pixels, dst, row * TILE, row * TILE + cols)
        }
    }
}
