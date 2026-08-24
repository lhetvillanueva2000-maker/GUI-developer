package com.mcguidesigner.core.image

import com.mcguidesigner.core.model.CanvasSpec

/** What fills the parts of the image the design does not cover. */
enum class ImageBackground(val displayName: String, val blurb: String) {
    /**
     * The canvas's own backdrop colour, as the editor draws it.
     *
     * The default, because it is what the design looks like - the backdrop is
     * part of the screen, not part of the editor.
     */
    CANVAS("Canvas backdrop", "The image looks like the editor does."),

    /**
     * Nothing at all: alpha zero wherever no element was drawn.
     *
     * For dropping a screen onto a page or a screenshot of the game, which is
     * most of what a PNG of a GUI is for.
     */
    TRANSPARENT("Transparent", "For layering the screen over something else."),
    ;

    companion object {
        val DEFAULT = CANVAS
        fun fromName(name: String?): ImageBackground =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DEFAULT
    }
}

/** One choice in the image-export size list. */
data class ImageSize(
    val label: String,
    val scale: Int,
    val width: Int,
    val height: Int,
) {
    /** `1280 × 1208` - what you actually get, not what you asked for. */
    val dimensions: String get() = "$width × $height"

    /** Pixel count, which is what actually has to be allocated twice over. */
    val pixels: Long get() = width.toLong() * height.toLong()

    /** `12.4 MP`, for sizes big enough that the number is worth seeing. */
    val megapixels: String get() = ((pixels / 100_000L).toDouble() / 10.0).toString() + " MP"
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

    /** No canvas is ever enlarged more than this, however small it is. */
    const val MAX_SCALE = 32

    /**
     * The most pixels an export is allowed to be.
     *
     * A real limit with a real reason, not a round number: rendering goes
     * through an `IntArray` of the whole image *and* a bitmap of it, so the
     * peak cost is eight bytes a pixel. At this budget that is ninety-odd
     * megabytes, which a desktop shrugs at and a mid-range phone can just about
     * do. Without it, 32x of a 1920x1080 canvas asks for two billion pixels and
     * the app dies with an out-of-memory error rather than saying no.
     */
    const val MAX_PIXELS = 12_000_000L

    /**
     * The largest whole multiple of [canvas] that fits inside [MAX_PIXELS].
     *
     * Always at least 1: a canvas too big to export even at original size is
     * still offered at original size, because refusing outright would be worse
     * than letting the platform try and fail with a real message.
     */
    fun maxScaleFor(canvas: CanvasSpec): Int {
        val area = canvas.width.toLong() * canvas.height.toLong()
        if (area <= 0L) return 1
        var scale = 1
        while (scale < MAX_SCALE && area * (scale + 1) * (scale + 1) <= MAX_PIXELS) scale++
        return scale
    }

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

    /**
     * Whether "[targetHeight]p" would be an honest label for this canvas.
     *
     * Two ways it would not be, and they are mirror images:
     *
     *  - **Too big to reach.** 2160p on a 1080-tall canvas needs 2x, which is
     *    fine, but on a 4K canvas the multiple that fits inside [MAX_PIXELS] is
     *    1x, and calling a 2160px image "2160p" when it is really the original
     *    is meaningless.
     *  - **Too small to reach.** 144p on a 1080-tall canvas is 0.13x, and the
     *    smallest whole multiple is 1x - so asking for 144p would hand back a
     *    1080px image. This is the one the tests caught, and it was quietly
     *    wrong in every build before now.
     *
     * The rule is the same for both: the whole multiple must land within a
     * factor of two of what the name promises. Nothing is lost by dropping a
     * name that cannot be met, because 1x is always offered under its own
     * label.
     */
    private fun canHonour(canvasHeight: Int, targetHeight: Int, ceiling: Int): Boolean {
        if (canvasHeight <= 0) return false
        val exact = targetHeight.toFloat() / canvasHeight
        if (exact < 0.5f) return false
        return scaleForHeight(canvasHeight, targetHeight) <= ceiling
    }

    /** The size a canvas comes out at when multiplied by [scale]. */
    fun sizeAt(canvas: CanvasSpec, scale: Int, label: String = "${scale}×"): ImageSize {
        val safe = scale.coerceIn(1, maxScaleFor(canvas))
        return ImageSize(
            label = label,
            scale = safe,
            width = canvas.width * safe,
            height = canvas.height * safe,
        )
    }

    /**
     * Every size offered for this canvas, smallest first, with duplicates gone.
     *
     * Two named heights can land on the same multiple - on a tall canvas 1440
     * and 2160 may both be 8x - and offering the same export twice under two
     * names is a menu that lies about having more choices than it does.
     */
    fun optionsFor(canvas: CanvasSpec): List<ImageSize> {
        val ceiling = maxScaleFor(canvas)

        val byHeight = namedHeights.mapNotNull { height ->
            if (!canHonour(canvas.height, height, ceiling)) return@mapNotNull null
            sizeAt(canvas, scaleForHeight(canvas.height, height), label = "${height}p")
        }
        val byScale = namedScales.filter { it <= ceiling }.map { sizeAt(canvas, it) }

        // A named height wins over a bare multiple when they collide, because
        // "720p" tells you why you would pick it and "4×" does not.
        return (byHeight + byScale)
            .distinctBy { it.scale }
            .sortedBy { it.scale }
            .ifEmpty { listOf(sizeAt(canvas, 1)) }
    }

    /** The one to start on: big enough to be useful, small enough to be quick. */
    fun defaultFor(canvas: CanvasSpec): ImageSize =
        optionsFor(canvas).minByOrNull { kotlin.math.abs(it.scale - 4) } ?: sizeAt(canvas, 1)
}
