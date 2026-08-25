package com.mcguidesigner.core.paint

import com.mcguidesigner.core.util.Ids

/**
 * One layer: a full-canvas ARGB buffer plus how it is combined with the rest.
 *
 * The buffer is mutable and shared by reference on purpose. A stroke touches a
 * few thousand pixels of a few million, and copying the whole layer to make an
 * "immutable update" would allocate 16MB per dab on a large canvas. Undo is
 * handled by [UndoStack] recording only the tiles that actually changed, which
 * is the same trade every paint app makes and the reason they can run on a
 * phone at all.
 *
 * Everything *about* the layer - name, opacity, mode, flags - is an ordinary
 * immutable property, so the UI can hold a copy and compare.
 */
class PaintLayer(
    val id: String,
    var name: String,
    val width: Int,
    val height: Int,
    val pixels: IntArray = IntArray(width * height),
    var visible: Boolean = true,
    /** 0..255. Folded into the blend, not applied as a second pass. */
    var opacity: Int = 255,
    var blendMode: BlendMode = BlendMode.NORMAL,
    /**
     * Alpha lock: painting can change a pixel's colour but not whether it is
     * there. The way to recolour line art without going outside it.
     */
    var alphaLocked: Boolean = false,
    /** Nothing may write to this layer at all. */
    var locked: Boolean = false,
    /**
     * Clipped to the layer below: this layer is only visible where that one is.
     * The standard way to shade inside a shape without a selection.
     */
    var clippedToBelow: Boolean = false,
) {
    val size: Int get() = width * height

    fun clear() {
        pixels.fill(Pixels.TRANSPARENT)
    }

    fun fill(colour: Int) {
        pixels.fill(colour)
    }

    fun copyOf(newId: String = Ids.prefixed("layer"), newName: String = "$name copy"): PaintLayer =
        PaintLayer(
            id = newId,
            name = newName,
            width = width,
            height = height,
            pixels = pixels.copyOf(),
            visible = visible,
            opacity = opacity,
            blendMode = blendMode,
            alphaLocked = alphaLocked,
            locked = locked,
            clippedToBelow = clippedToBelow,
        )

    /** True when every pixel is transparent. Used to grey out "merge down". */
    fun isEmpty(): Boolean {
        for (p in pixels) if (p and 0xFF000000.toInt() != 0) return false
        return true
    }

    operator fun get(x: Int, y: Int): Int =
        if (x < 0 || y < 0 || x >= width || y >= height) Pixels.TRANSPARENT else pixels[y * width + x]

    operator fun set(x: Int, y: Int, value: Int) {
        if (x < 0 || y < 0 || x >= width || y >= height) return
        pixels[y * width + x] = value
    }
}

/** What sits behind the bottom layer. */
enum class PaintBackground(val label: String) {
    /** A white sheet. The default, and what a new canvas is. */
    WHITE("White"),
    TRANSPARENT("Transparent"),
    /** A mid grey, for judging light art. */
    GREY("Grey"),
    BLACK("Black"),
    ;

    /** The colour to lay down first, or transparent for none. */
    val colour: Int
        get() = when (this) {
            WHITE -> 0xFFFFFFFF.toInt()
            TRANSPARENT -> Pixels.TRANSPARENT
            GREY -> 0xFF808080.toInt()
            BLACK -> 0xFF000000.toInt()
        }
}

/**
 * A painting: a stack of layers over a background, bottom-first.
 *
 * `layers[0]` is the bottom of the stack, which is the order a compositor wants
 * and the reverse of the order a layer panel shows. The panel does the
 * reversing, once, rather than every reader of this class having to remember
 * which way round it is.
 */
class PaintDocument(
    val width: Int,
    val height: Int,
    var background: PaintBackground = PaintBackground.WHITE,
    val layers: MutableList<PaintLayer> = mutableListOf(),
    var name: String = "Untitled canvas",
) {
    /** Index into [layers]; the one strokes go to. */
    var activeIndex: Int = 0
        set(value) {
            field = value.coerceIn(0, maxOf(0, layers.lastIndex))
        }

    val active: PaintLayer? get() = layers.getOrNull(activeIndex)

    fun newLayer(name: String = "Layer ${layers.size + 1}"): PaintLayer =
        PaintLayer(Ids.prefixed("layer"), name, width, height)

    fun addLayer(above: Int = activeIndex): PaintLayer {
        val layer = newLayer()
        val at = (above + 1).coerceIn(0, layers.size)
        layers.add(at, layer)
        activeIndex = at
        return layer
    }

    fun duplicateActive(): PaintLayer? {
        val source = active ?: return null
        val copy = source.copyOf()
        layers.add(activeIndex + 1, copy)
        activeIndex += 1
        return copy
    }

    /** Removing the last layer leaves an empty one rather than no layers. */
    fun removeActive() {
        if (layers.isEmpty()) return
        if (layers.size == 1) {
            layers[0].clear()
            return
        }
        layers.removeAt(activeIndex)
        activeIndex = activeIndex.coerceAtMost(layers.lastIndex)
    }

    fun move(from: Int, to: Int) {
        if (from !in layers.indices) return
        val target = to.coerceIn(0, layers.lastIndex)
        if (from == target) return
        val layer = layers.removeAt(from)
        layers.add(target, layer)
        activeIndex = target
    }

    /**
     * Flattens the active layer onto the one below it.
     *
     * The result keeps the *lower* layer's mode and opacity, because that is
     * the layer that remains, and its relationship to everything beneath it
     * must not change just because something was merged into it.
     */
    fun mergeDown(): Boolean {
        val upper = active ?: return false
        val lower = layers.getOrNull(activeIndex - 1) ?: return false
        if (lower.locked) return false
        val scale = if (upper.visible) upper.opacity else 0
        for (i in lower.pixels.indices) {
            lower.pixels[i] = blendPixel(lower.pixels[i], upper.pixels[i], upper.blendMode, scale)
        }
        layers.removeAt(activeIndex)
        activeIndex -= 1
        return true
    }

    companion object {
        /**
         * A new painting with one empty layer over a white sheet.
         *
         * One layer rather than zero because "add a layer before you can draw"
         * is a step nobody wants, and a background that is a real white rather
         * than a checkerboard because that is what a sheet of paper is.
         */
        fun blank(
            width: Int,
            height: Int,
            background: PaintBackground = PaintBackground.WHITE,
            name: String = "Untitled canvas",
        ): PaintDocument {
            val document = PaintDocument(width, height, background, name = name)
            document.layers.add(PaintLayer(Ids.prefixed("layer"), "Layer 1", width, height))
            document.activeIndex = 0
            return document
        }
    }
}
