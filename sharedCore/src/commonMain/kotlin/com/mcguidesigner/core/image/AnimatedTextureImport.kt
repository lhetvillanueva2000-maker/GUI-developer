package com.mcguidesigner.core.image

import com.mcguidesigner.core.model.TextureAsset
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Turns an animated source image into the one animation format both Minecraft
 * editions understand: a single PNG holding every frame stacked vertically,
 * plus the timing that goes in its `.mcmeta` sidecar.
 *
 * Converting on import rather than on export is the whole point.  The project
 * then contains exactly what the game needs, the editor animates the same
 * pixels the game will, and no exporter has to know what a GIF is.
 */
object AnimatedTextureImport {

    /**
     * Frames kept from one source.
     *
     * Long GIFs are usually long because they are smooth, not because every
     * frame carries information, and a 400-frame strip is a texture no
     * resource pack should ship.  Past this the import samples evenly across
     * the whole animation so the result still runs end to end.
     */
    const val MAX_FRAMES = 128

    /** Frames wider or taller than this are scaled down on import. */
    const val MAX_FRAME_DIMENSION = 512

    /** One Minecraft tick, in milliseconds. */
    const val TICK_MILLIS = 50

    /** True when [bytes] is an animated format this can convert. */
    fun isAnimatedSource(bytes: ByteArray): Boolean = GifDecoder.isGif(bytes)

    /**
     * Converts [bytes] into a frame-strip [TextureAsset], or returns null when
     * they are not an animated image (a still PNG, a JPEG, anything corrupt).
     *
     * A single-frame GIF comes back as an ordinary still texture rather than a
     * one-frame animation, because that is what it is.
     */
    fun fromBytes(bytes: ByteArray, id: String, name: String, sourcePath: String? = null): TextureAsset? {
        val decoded = GifDecoder.decode(bytes) ?: return null
        return fromFrames(
            frames = decoded.frames,
            sourceWidth = decoded.width,
            sourceHeight = decoded.height,
            id = id,
            name = name,
            sourcePath = sourcePath,
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun fromFrames(
        frames: List<GifDecoder.Frame>,
        sourceWidth: Int,
        sourceHeight: Int,
        id: String,
        name: String,
        sourcePath: String?,
    ): TextureAsset? {
        if (frames.isEmpty() || sourceWidth <= 0 || sourceHeight <= 0) return null

        val kept = sampleEvenly(frames, MAX_FRAMES)
        val scale = scaleFactorFor(sourceWidth, sourceHeight)
        val frameWidth = maxOf(1, (sourceWidth * scale).toInt())
        val frameHeight = maxOf(1, (sourceHeight * scale).toInt())

        val strip = IntArray(frameWidth * frameHeight * kept.size)
        kept.forEachIndexed { index, frame ->
            val resized = if (scale == 1f) {
                frame.pixels
            } else {
                resample(frame.pixels, sourceWidth, sourceHeight, frameWidth, frameHeight)
            }
            resized.copyInto(strip, index * frameWidth * frameHeight)
        }

        val png = PngWriter.encode(frameWidth, frameHeight * kept.size, strip)
        val delays = kept.map { it.delayMillis }

        return TextureAsset(
            id = id,
            name = name,
            format = "png",
            width = frameWidth,
            height = frameHeight * kept.size,
            dataBase64 = Base64.encode(png),
            pixelated = true,
            sourcePath = sourcePath,
            frameCount = kept.size,
            frameTimeTicks = frameTimeTicks(delays),
            interpolate = false,
            // A still has no timing worth keeping, and storing a one-element
            // list would make `hasVariableFrameTiming` reasoning fiddlier for
            // no gain.
            frameDelaysMillis = if (kept.size > 1) delays else emptyList(),
        )
    }

    /**
     * Rounds the animation's average frame delay to whole Minecraft ticks.
     *
     * Ticks are the only unit `frametime` accepts, so a 30fps GIF (33ms) and a
     * 20fps one (50ms) both land on 1 tick.  Exporters that need the original
     * cadence back read [TextureAsset.frameDelaysMillis] instead.
     */
    fun frameTimeTicks(delaysMillis: List<Int>): Int {
        if (delaysMillis.isEmpty()) return 2
        val average = delaysMillis.sum().toDouble() / delaysMillis.size
        return ((average / TICK_MILLIS) + 0.5).toInt().coerceIn(1, 600)
    }

    /**
     * Picks [limit] frames spread across [frames], always keeping the first.
     *
     * Sampling evenly rather than truncating means a trimmed animation still
     * plays the whole loop, just choppier - far better than stopping a third
     * of the way through.
     */
    private fun <T> sampleEvenly(frames: List<T>, limit: Int): List<T> {
        if (frames.size <= limit) return frames
        return (0 until limit).map { index -> frames[index * frames.size / limit] }
    }

    private fun scaleFactorFor(width: Int, height: Int): Float {
        val largest = maxOf(width, height)
        if (largest <= MAX_FRAME_DIMENSION) return 1f
        return MAX_FRAME_DIMENSION.toFloat() / largest
    }

    /**
     * Box-filtered downscale.
     *
     * Averaging every source pixel that lands in a target pixel, rather than
     * point-sampling, is what stops a shrunken animation from shimmering as
     * different pixels get picked from frame to frame.  Alpha is premultiplied
     * for the average and divided back out, so transparent pixels do not drag
     * their (arbitrary) colour into the result.
     */
    private fun resample(
        source: IntArray,
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
    ): IntArray {
        val out = IntArray(targetWidth * targetHeight)
        for (y in 0 until targetHeight) {
            val fromRow = y * sourceHeight / targetHeight
            val toRow = maxOf(fromRow + 1, (y + 1) * sourceHeight / targetHeight)
            for (x in 0 until targetWidth) {
                val fromColumn = x * sourceWidth / targetWidth
                val toColumn = maxOf(fromColumn + 1, (x + 1) * sourceWidth / targetWidth)

                var alphaSum = 0L
                var redSum = 0L
                var greenSum = 0L
                var blueSum = 0L
                var count = 0

                for (row in fromRow until toRow) {
                    for (column in fromColumn until toColumn) {
                        val argb = source[row * sourceWidth + column]
                        val alpha = (argb ushr 24) and 0xFF
                        alphaSum += alpha
                        redSum += ((argb ushr 16) and 0xFF) * alpha
                        greenSum += ((argb ushr 8) and 0xFF) * alpha
                        blueSum += (argb and 0xFF) * alpha
                        count++
                    }
                }

                out[y * targetWidth + x] = if (count == 0 || alphaSum == 0L) {
                    0
                } else {
                    val alpha = (alphaSum / count).toInt().coerceIn(0, 255)
                    val red = (redSum / alphaSum).toInt().coerceIn(0, 255)
                    val green = (greenSum / alphaSum).toInt().coerceIn(0, 255)
                    val blue = (blueSum / alphaSum).toInt().coerceIn(0, 255)
                    (alpha shl 24) or (red shl 16) or (green shl 8) or blue
                }
            }
        }
        return out
    }

    /**
     * The `.mcmeta` sidecar Minecraft reads a texture's animation from.
     *
     * When the source had uneven frame delays the per-frame `time` form is
     * used, which reproduces the original cadence exactly rather than
     * flattening it to one rate.  Non-square frames carry explicit `width` and
     * `height`, without which the game assumes square tiles and shreds the
     * animation into slivers.
     */
    fun mcmetaFor(texture: TextureAsset): String {
        val frameHeight = texture.frameHeight
        val squareFrames = frameHeight == texture.width

        return buildString {
            appendLine("{")
            appendLine("  \"animation\": {")
            appendLine("    \"frametime\": ${texture.frameTimeTicks},")
            appendLine("    \"interpolate\": ${texture.interpolate},")
            if (!squareFrames) {
                appendLine("    \"width\": ${texture.width},")
                appendLine("    \"height\": $frameHeight,")
            }
            append("    \"frames\": [")
            if (texture.hasVariableFrameTiming) {
                appendLine()
                texture.frameDelaysMillis.forEachIndexed { index, delay ->
                    val ticks = ((delay.toDouble() / TICK_MILLIS) + 0.5).toInt().coerceIn(1, 600)
                    val comma = if (index == texture.frameDelaysMillis.lastIndex) "" else ","
                    appendLine("      { \"index\": $index, \"time\": $ticks }$comma")
                }
                append("    ]")
            } else {
                append((0 until texture.frameCount).joinToString(", "))
                append("]")
            }
            appendLine()
            appendLine("  }")
            append("}")
        }
    }
}
