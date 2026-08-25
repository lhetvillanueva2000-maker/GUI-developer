package com.mcguidesigner.core.paint

import com.mcguidesigner.core.paint.Pixels.alpha
import com.mcguidesigner.core.paint.Pixels.argb
import com.mcguidesigner.core.paint.Pixels.blue
import com.mcguidesigner.core.paint.Pixels.green
import com.mcguidesigner.core.paint.Pixels.mul
import com.mcguidesigner.core.paint.Pixels.red

/**
 * How a layer's colour combines with everything already under it.
 *
 * These are the Porter-Duff *separable* blend modes from the PDF and CSS
 * compositing specs, which is also what every paint app implements, so a file
 * exported from here and opened elsewhere looks the same. The formulas below
 * are the spec's `B(cb, cs)` on 8-bit channels, wrapped in the standard
 * source-over alpha composite.
 *
 * They are ordered the way a layer panel lists them - the ones people reach for
 * first at the top - rather than alphabetically or by mathematical family.
 */
enum class BlendMode(val label: String) {
    NORMAL("Normal"),
    MULTIPLY("Multiply"),
    SCREEN("Screen"),
    OVERLAY("Overlay"),
    DARKEN("Darken"),
    LIGHTEN("Lighten"),
    COLOR_DODGE("Color Dodge"),
    COLOR_BURN("Color Burn"),
    HARD_LIGHT("Hard Light"),
    SOFT_LIGHT("Soft Light"),
    DIFFERENCE("Difference"),
    EXCLUSION("Exclusion"),
    ADD("Add"),
    SUBTRACT("Subtract"),
    ;

    /** The per-channel blend function, on 0..255 backdrop and source. */
    fun channel(backdrop: Int, source: Int): Int = when (this) {
        NORMAL -> source
        MULTIPLY -> mul(backdrop, source)
        SCREEN -> 255 - mul(255 - backdrop, 255 - source)
        OVERLAY -> HARD_LIGHT.channel(source, backdrop)
        DARKEN -> minOf(backdrop, source)
        LIGHTEN -> maxOf(backdrop, source)

        COLOR_DODGE -> when {
            backdrop == 0 -> 0
            source == 255 -> 255
            else -> minOf(255, backdrop * 255 / (255 - source))
        }

        COLOR_BURN -> when {
            backdrop == 255 -> 255
            source == 0 -> 0
            else -> 255 - minOf(255, (255 - backdrop) * 255 / source)
        }

        HARD_LIGHT ->
            if (source <= 127) mul(backdrop, 2 * source)
            else 255 - mul(255 - backdrop, 2 * (255 - source))

        SOFT_LIGHT -> softLight(backdrop, source)

        DIFFERENCE -> if (backdrop > source) backdrop - source else source - backdrop
        EXCLUSION -> backdrop + source - 2 * mul(backdrop, source)
        ADD -> minOf(255, backdrop + source)
        SUBTRACT -> maxOf(0, backdrop - source)
    }

    private fun softLight(backdrop: Int, source: Int): Int {
        // The W3C formula, kept in 8-bit integers. The `d(cb)` branch is the
        // one that stops soft light from going flat in the shadows; dropping it
        // for the cheaper approximation is why some implementations look muddy.
        val cb = backdrop
        val cs = source
        return if (cs <= 127) {
            cb - mul(mul(255 - 2 * cs, cb), 255 - cb)
        } else {
            val d = if (cb <= 63) {
                mul(mul((16 * cb - 12 * 255), cb) + 4 * 255 * 255 / 255, cb) / 255
            } else {
                Pixels.isqrt(cb * 255)
            }
            cb + mul(2 * cs - 255, d - cb)
        }
    }

    companion object {
        fun byLabel(label: String): BlendMode = entries.firstOrNull { it.label == label } ?: NORMAL
    }
}

/**
 * One source pixel composited onto one backdrop pixel.
 *
 * [sourceAlphaScale] is the layer's own opacity, 0..255, folded in here rather
 * than applied as a separate pass so a layer at 40% costs nothing extra.
 *
 * Both inputs and the result are straight (non-premultiplied) ARGB - see
 * [Pixels] for why the engine stores them that way.
 */
fun blendPixel(backdrop: Int, source: Int, mode: BlendMode, sourceAlphaScale: Int): Int {
    val sa = mul(alpha(source), sourceAlphaScale)
    if (sa == 0) return backdrop
    val ba = alpha(backdrop)

    if (mode == BlendMode.NORMAL && sa == 255) return Pixels.withAlpha(source, 255)

    // Composite alpha: the standard source-over union.
    val outA = sa + mul(ba, 255 - sa)
    if (outA == 0) return Pixels.TRANSPARENT

    fun channel(bc: Int, sc: Int): Int {
        // Where the backdrop is transparent there is nothing to blend with, so
        // the blend function has to fall back to the source. Skipping this is
        // the classic bug where Multiply on the bottom layer turns everything
        // black: cb reads as 0 over empty pixels and 0 * anything is 0.
        val blended = if (ba == 0) sc else mode.channel(bc, sc)
        // Weighted by how much of the backdrop is actually there.
        val effective = if (ba == 0) sc else Pixels.lerp8(sc, blended, ba)
        // Source-over on straight alpha.
        val numerator = mul(effective, sa) + mul(mul(bc, ba), 255 - sa)
        return (numerator * 255) / outA
    }

    return argb(
        outA,
        channel(red(backdrop), red(source)).coerceIn(0, 255),
        channel(green(backdrop), green(source)).coerceIn(0, 255),
        channel(blue(backdrop), blue(source)).coerceIn(0, 255),
    )
}
