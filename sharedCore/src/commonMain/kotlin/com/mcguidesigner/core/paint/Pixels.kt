package com.mcguidesigner.core.paint

/**
 * The pixel conventions the whole paint engine agrees on.
 *
 * One decision, written down once, because getting it wrong is invisible until
 * something is exported and the colours are subtly off: **straight (not
 * premultiplied) ARGB, 0xAARRGGBB, in an IntArray, row-major, no padding.**
 *
 * Straight rather than premultiplied costs a multiply and a divide per blend
 * and buys the thing that matters more here - an eraser that lowers alpha
 * without touching RGB. In premultiplied storage, erasing towards zero drags
 * the colour channels to zero with it, so a stroke that is erased to 1% alpha
 * and then painted back over is grey rather than the colour it was. Anybody who
 * uses the eraser as an adjustment rather than a delete notices immediately.
 *
 * It is also what [com.mcguidesigner.core.image.PngWriter] already expects, so
 * export is a copy rather than a conversion pass over every pixel.
 */
// The accessors below are one shift and one mask each, and they are called a
// few million times per composite. The compiler is right that inlining a
// function with no lambda parameter rarely pays; in this one file it is the
// difference between a bit-mask and a megabyte of call frames per repaint.
@Suppress("NOTHING_TO_INLINE")
object Pixels {

    const val TRANSPARENT: Int = 0

    inline fun alpha(argb: Int): Int = (argb ushr 24) and 0xFF
    inline fun red(argb: Int): Int = (argb ushr 16) and 0xFF
    inline fun green(argb: Int): Int = (argb ushr 8) and 0xFF
    inline fun blue(argb: Int): Int = argb and 0xFF

    inline fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    /** [argb] with its alpha replaced. Colour channels are left alone. */
    inline fun withAlpha(argb: Int, a: Int): Int = (argb and 0x00FFFFFF) or ((a and 0xFF) shl 24)

    /** Rounded `a * b / 255`, the standard 8-bit multiply. */
    inline fun mul(a: Int, b: Int): Int {
        val t = a * b + 0x80
        return (t + (t shr 8)) shr 8
    }

    /** Linear interpolation between two 8-bit values; [t] is 0..255. */
    inline fun lerp8(from: Int, to: Int, t: Int): Int = from + mul(to - from, t)

    /**
     * Perceptual-ish distance between two opaque colours, 0..~441.
     *
     * The weights are the "low-cost approximation" redmean formula rather than
     * a real Lab conversion. A true CIEDE2000 would be better and is about
     * forty times slower, which matters when this runs once per pixel over a
     * few million pixels inside a flood fill on a phone. The difference in
     * where a tolerance boundary lands is a pixel or two; the difference in
     * whether the bucket tool feels instant is the whole tool.
     */
    fun distance(a: Int, b: Int): Int {
        val rMean = (red(a) + red(b)) shr 1
        val dr = red(a) - red(b)
        val dg = green(a) - green(b)
        val db = blue(a) - blue(b)
        val weightR = 512 + rMean
        val weightB = 767 - rMean
        val sum = (weightR * dr * dr shr 8) + 4 * dg * dg + (weightB * db * db shr 8)
        return isqrt(sum)
    }

    /**
     * Distance including alpha.
     *
     * A transparent pixel and an opaque white one are not the same colour even
     * though both have white-ish channels, and a flood fill that cannot tell
     * them apart leaks straight through every erased area on the layer.
     */
    fun distanceWithAlpha(a: Int, b: Int): Int {
        val da = alpha(a) - alpha(b)
        val colour = distance(a, b)
        // Alpha differences are weighted at parity with a full colour swing,
        // so a hole in the layer stops a fill as firmly as a hard edge does.
        return maxOf(colour, if (da < 0) -da else da)
    }

    /** Integer square root, exact for the whole non-negative Int range. */
    fun isqrt(value: Int): Int {
        if (value <= 0) return 0
        var x = value
        var y = (x + 1) / 2
        while (y < x) {
            x = y
            y = (x + value / x) / 2
        }
        return x
    }
}
