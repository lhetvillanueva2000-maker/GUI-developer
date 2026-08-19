package com.mcguidesigner.core.editor

import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.IntSize
import com.mcguidesigner.core.model.ResizeHandle
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Adjustment produced by the snapping pass, plus the alignment lines that
 * should be drawn while the gesture is live.
 */
data class SnapResult(
    val dx: Int = 0,
    val dy: Int = 0,
    /** Canvas-space x positions of active vertical alignment lines. */
    val verticalLines: List<Int> = emptyList(),
    /** Canvas-space y positions of active horizontal alignment lines. */
    val horizontalLines: List<Int> = emptyList(),
) {
    val isEmpty: Boolean get() = dx == 0 && dy == 0 && verticalLines.isEmpty() && horizontalLines.isEmpty()

    companion object {
        val None = SnapResult()
    }
}

/**
 * Grid, element-edge and guide snapping.
 *
 * Snapping runs on canvas-space GUI pixels, never on screen pixels, so its
 * behaviour is identical at every zoom level and on both platforms.  The
 * threshold, however, is supplied by the caller in GUI pixels derived from the
 * current zoom - a 6-screen-pixel tolerance is generous on a phone and far too
 * sticky on a 4K desktop canvas.
 */
object Snapping {

    /** Positions considered "interesting" on one axis of a rectangle. */
    private fun edgesX(rect: IntRect) = intArrayOf(rect.left, rect.centerX, rect.right)
    private fun edgesY(rect: IntRect) = intArrayOf(rect.top, rect.centerY, rect.bottom)

    /**
     * Computes the offset that should be added to [moving] so that it lands on
     * the nearest grid line, sibling edge, guide, or canvas centre.
     *
     * @param others candidate rectangles to align against (usually every
     *   element except the ones being dragged).
     */
    fun forMove(
        moving: IntRect,
        others: List<IntRect>,
        guides: List<Guide>,
        canvas: IntSize,
        gridSize: Int,
        snapToGrid: Boolean,
        snapToElements: Boolean,
        threshold: Int,
    ): SnapResult {
        var bestDx = 0
        var bestDistX = threshold + 1
        var bestDy = 0
        var bestDistY = threshold + 1
        val vLines = mutableListOf<Int>()
        val hLines = mutableListOf<Int>()

        fun considerX(target: Int, source: Int, line: Int?) {
            val delta = target - source
            val dist = abs(delta)
            if (dist > threshold) return
            if (dist < bestDistX) {
                bestDistX = dist
                bestDx = delta
                vLines.clear()
                line?.let { vLines += it }
            } else if (dist == bestDistX && delta == bestDx) {
                line?.let { if (it !in vLines) vLines += it }
            }
        }

        fun considerY(target: Int, source: Int, line: Int?) {
            val delta = target - source
            val dist = abs(delta)
            if (dist > threshold) return
            if (dist < bestDistY) {
                bestDistY = dist
                bestDy = delta
                hLines.clear()
                line?.let { hLines += it }
            } else if (dist == bestDistY && delta == bestDy) {
                line?.let { if (it !in hLines) hLines += it }
            }
        }

        if (snapToElements) {
            for (other in others) {
                for (target in edgesX(other)) {
                    for (source in edgesX(moving)) considerX(target, source, target)
                }
                for (target in edgesY(other)) {
                    for (source in edgesY(moving)) considerY(target, source, target)
                }
            }
            // Canvas edges and centre lines.
            for (target in intArrayOf(0, canvas.width / 2, canvas.width)) {
                for (source in edgesX(moving)) considerX(target, source, target)
            }
            for (target in intArrayOf(0, canvas.height / 2, canvas.height)) {
                for (source in edgesY(moving)) considerY(target, source, target)
            }
        }

        for (guide in guides) {
            if (guide.vertical) {
                for (source in edgesX(moving)) considerX(guide.position, source, guide.position)
            } else {
                for (source in edgesY(moving)) considerY(guide.position, source, guide.position)
            }
        }

        if (snapToGrid && gridSize > 0) {
            val gridX = (moving.left.toFloat() / gridSize).roundToInt() * gridSize
            val gridY = (moving.top.toFloat() / gridSize).roundToInt() * gridSize
            considerX(gridX, moving.left, null)
            considerY(gridY, moving.top, null)
        }

        return SnapResult(
            dx = if (bestDistX <= threshold) bestDx else 0,
            dy = if (bestDistY <= threshold) bestDy else 0,
            verticalLines = vLines.toList(),
            horizontalLines = hLines.toList(),
        )
    }

    /**
     * Snapping for resize gestures: only the edges actually being dragged are
     * allowed to move, so the opposite edge stays exactly where the user put it.
     */
    fun forResize(
        resized: IntRect,
        handle: ResizeHandle,
        others: List<IntRect>,
        guides: List<Guide>,
        canvas: IntSize,
        gridSize: Int,
        snapToGrid: Boolean,
        snapToElements: Boolean,
        threshold: Int,
    ): SnapResult {
        val xTargets = buildList {
            if (snapToElements) {
                others.forEach { addAll(edgesX(it).toList()) }
                addAll(listOf(0, canvas.width / 2, canvas.width))
            }
            guides.filter { it.vertical }.forEach { add(it.position) }
        }
        val yTargets = buildList {
            if (snapToElements) {
                others.forEach { addAll(edgesY(it).toList()) }
                addAll(listOf(0, canvas.height / 2, canvas.height))
            }
            guides.filter { !it.vertical }.forEach { add(it.position) }
        }

        fun nearest(source: Int, targets: List<Int>, grid: Boolean): Pair<Int, Int?> {
            var bestDelta = 0
            var bestDist = threshold + 1
            var bestLine: Int? = null
            for (target in targets) {
                val dist = abs(target - source)
                if (dist < bestDist) {
                    bestDist = dist
                    bestDelta = target - source
                    bestLine = target
                }
            }
            if (grid && snapToGrid && gridSize > 0) {
                val snapped = (source.toFloat() / gridSize).roundToInt() * gridSize
                val dist = abs(snapped - source)
                if (dist < bestDist) {
                    bestDist = dist
                    bestDelta = snapped - source
                    bestLine = null
                }
            }
            return if (bestDist <= threshold) bestDelta to bestLine else 0 to null
        }

        val vLines = mutableListOf<Int>()
        val hLines = mutableListOf<Int>()
        var dx = 0
        var dy = 0

        when {
            handle.affectsLeft -> nearest(resized.left, xTargets, true).let { (d, line) ->
                dx = d; line?.let { vLines += it }
            }

            handle.affectsRight -> nearest(resized.right, xTargets, true).let { (d, line) ->
                dx = d; line?.let { vLines += it }
            }
        }
        when {
            handle.affectsTop -> nearest(resized.top, yTargets, true).let { (d, line) ->
                dy = d; line?.let { hLines += it }
            }

            handle.affectsBottom -> nearest(resized.bottom, yTargets, true).let { (d, line) ->
                dy = d; line?.let { hLines += it }
            }
        }

        return SnapResult(dx, dy, vLines, hLines)
    }

    /** Applies a resize delta to [start] according to which handle is grabbed. */
    fun applyResize(
        start: IntRect,
        handle: ResizeHandle,
        dx: Int,
        dy: Int,
        minSize: IntSize,
        maxSize: IntSize,
        keepAspect: Boolean = false,
    ): IntRect {
        var left = start.left
        var top = start.top
        var right = start.right
        var bottom = start.bottom

        if (handle.affectsLeft) left += dx
        if (handle.affectsRight) right += dx
        if (handle.affectsTop) top += dy
        if (handle.affectsBottom) bottom += dy

        var width = (right - left).coerceIn(minSize.width, maxSize.width)
        var height = (bottom - top).coerceIn(minSize.height, maxSize.height)

        if (keepAspect && start.width > 0 && start.height > 0) {
            val ratio = start.width.toFloat() / start.height.toFloat()
            // Follow whichever axis the user moved further.
            if (abs(width - start.width) >= abs(height - start.height)) {
                height = (width / ratio).roundToInt().coerceIn(minSize.height, maxSize.height)
                width = (height * ratio).roundToInt().coerceIn(minSize.width, maxSize.width)
            } else {
                width = (height * ratio).roundToInt().coerceIn(minSize.width, maxSize.width)
                height = (width / ratio).roundToInt().coerceIn(minSize.height, maxSize.height)
            }
        }

        val x = if (handle.affectsLeft) right - width else left
        val y = if (handle.affectsTop) bottom - height else top
        return IntRect(x, y, width, height)
    }
}
