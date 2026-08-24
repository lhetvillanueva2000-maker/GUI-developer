package com.mcguidesigner.core.image

import com.mcguidesigner.core.model.CanvasSpec

/** One choice in the image-export size list. */
data class ImageSize(
    val label: String,
    val scale: Int,
    val width: Int,
    val height: Int,
) {
    /** `1280 x 1208` - what you actually get, not what you asked for. */
    val dimensions: String get() = "$width × $height"
}

/**
 * How big a PNG of the design should be.
 *
 * The whole reason this is not a free-text pixel height: **Minecraft GUI art is
 * pixel art**, and pixel art only survives being enlarged by a whole number.
 * Scaling a 176x166 screen to exactly 720 pixels tall means multiplying by
 * 4.337, which lands source pixels between destination pixels and either blurs
 * them or leaves some rows a pixel taller than their neighbours. Either way the
 * result no longer looks like the thing that was designed.
 *
 * So the familiar names are offered - somebody asking for "720p" knows what
 * they mean - and each one snaps to the nearest whole multiple, with the real
 * output size shown next to it. The name is the intent; the number beside it is
 * the truth.
 */
object ImageExport {

    /** The heights people name, in the order they think of them. */
    val namedHeights = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160)

    /** Whole multiples, for when the multiple is what you care about. */
    val namedScales = listOf(1, 2, 3, 4, 6, 8, 12, 16)

    /** Nothing sensible comes of a zero-sized canvas or a zero-sized export. */
    const val MAX_SCALE = 32

    /**
     * The whole multiple that lands closest to [targetHeight].
     *
     * Rounds rather than truncating: for a 166px canvas, 720p is 4.34x, and
     * truncating to 4 is further from what was asked for than rounding to 4
     * happens to be - but for a 100px canvas, 360p is 3.6x, where truncating
     * gives 300px and rounding gives 400px. Rounding is the better answer in
     * both, and never returns zero.
     */
    fun scaleForHeight(canvasHeight: Int, targetHeight: Int): Int {
        if (canvasHeight <= 0) return 1
        val exact = targetHeight.toFloat() / canvasHeight
        return kotlin.math.round(exact).toInt().coerceIn(1, MAX_SCALE)
    }

    /** The size a canvas comes out at when multiplied by [scale]. */
    fun sizeAt(canvas: CanvasSpec, scale: Int, label: String = "${scale}×"): ImageSize {
        val safe = scale.coerceIn(1, MAX_SCALE)
        return ImageSize(
            label = label,
            scale = safe,
            width = canvas.width * safe,
            height = canvas.height * safe,
        )
    }

    /**
     * Every size offered for this canvas, largest first, with duplicates gone.
     *
     * Two named heights can land on the same multiple - on a tall canvas 1440
     * and 2160 may both be 8x - and offering the same export twice under two
     * names is a menu that lies about having more choices than it does.
     */
    fun optionsFor(canvas: CanvasSpec): List<ImageSize> {
        val byHeight = namedHeights.map { height ->
            val scale = scaleForHeight(canvas.height, height)
            sizeAt(canvas, scale, label = "${height}p")
        }
        val byScale = namedScales.map { sizeAt(canvas, it) }

        // A named height wins over a bare multiple when they collide, because
        // "720p" tells you why you would pick it and "4×" does not.
        return (byHeight + byScale)
            .distinctBy { it.scale }
            .sortedBy { it.scale }
    }

    /** The one to start on: big enough to be useful, small enough to be quick. */
    fun defaultFor(canvas: CanvasSpec): ImageSize =
        optionsFor(canvas).minByOrNull { kotlin.math.abs(it.scale - 4) } ?: sizeAt(canvas, 1)
}
