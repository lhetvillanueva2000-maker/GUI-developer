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
}
