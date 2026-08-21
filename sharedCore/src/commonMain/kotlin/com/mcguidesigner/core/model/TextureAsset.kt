package com.mcguidesigner.core.model

import kotlinx.serialization.Serializable

/**
 * A user-imported image stored inside the project file.
 *
 * Projects are single self-contained `.mcgui` documents, so imported textures
 * travel with them as base64 payloads.  [nineSlice] lets an imported PNG be
 * used as a stretchable panel/button skin instead of a flat image.
 */
@Serializable
data class TextureAsset(
    val id: String,
    val name: String,
    /** `png`, `jpg`, ... - lower case, without the dot. */
    val format: String,
    val width: Int,
    val height: Int,
    /** Base64 (RFC 4648, no line breaks) of the original file bytes. */
    val dataBase64: String,
    /** Nine-slice insets in source pixels; zero means "stretch the whole image". */
    val nineSlice: Insets = Insets.Zero,
    /** Nearest-neighbour sampling keeps Minecraft art crisp; off for photos. */
    val pixelated: Boolean = true,
    /** Original file name/path, kept only for display. */
    val sourcePath: String? = null,
    /**
     * Frames stacked vertically in this image.
     *
     * 1 means an ordinary still. Anything higher means the PNG is a Minecraft
     * animated texture: [width] wide, `width * frameCount` tall, played top to
     * bottom.  Storing it this way rather than keeping the original GIF is
     * deliberate - it is the only animation format either edition understands,
     * so the project holds exactly what the export needs to write.
     */
    val frameCount: Int = 1,
    /** `frametime` for the exported `.mcmeta`, in Minecraft ticks (20 = 1s). */
    val frameTimeTicks: Int = 2,
    /** Whether the exported `.mcmeta` asks the game to cross-fade frames. */
    val interpolate: Boolean = false,
    /**
     * The source's own per-frame delays in milliseconds, when it had any.
     *
     * Minecraft's `frametime` is a single value for the whole animation, so a
     * GIF with variable delays cannot be reproduced exactly.  Keeping the
     * original timings lets the editor play the animation faithfully and lets
     * the exporter warn when the strip it writes will run at a fixed rate.
     */
    val frameDelaysMillis: List<Int> = emptyList(),
) {
    val aspectRatio: Float get() = if (height == 0) 1f else width.toFloat() / height.toFloat()

    /** Approximate decoded byte size, used by the assets panel. */
    val approximateBytes: Int get() = (dataBase64.length / 4) * 3

    /** File name an exporter should write this texture to. */
    fun exportFileName(): String {
        val base = name.lowercase()
            .replace(Regex("[^a-z0-9_\\-]"), "_")
            .trim('_')
            .ifEmpty { "texture_$id" }
        return if (base.endsWith(".$format")) base else "$base.$format"
    }

    val hasNineSlice: Boolean
        get() = nineSlice.left > 0 || nineSlice.top > 0 || nineSlice.right > 0 || nineSlice.bottom > 0

    val isAnimated: Boolean get() = frameCount > 1

    /** Height of a single frame; the whole image when this is a still. */
    val frameHeight: Int get() = if (frameCount > 1) height / frameCount else height

    /** Size of one frame, which is what an animated element actually shows. */
    val frameSize: IntSize get() = IntSize(width, frameHeight)

    /** Name of the `.mcmeta` sidecar Minecraft reads the animation from. */
    fun mcmetaFileName(): String = exportFileName() + ".mcmeta"

    /**
     * `true` when the source's frames did not all run for the same time.
     *
     * Minecraft has one `frametime` per texture, so these animations are
     * exported at an average rate and the exporter says so.
     */
    val hasVariableFrameTiming: Boolean
        get() = frameDelaysMillis.size > 1 && frameDelaysMillis.distinct().size > 1
}
