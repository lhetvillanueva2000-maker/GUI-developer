package com.mcguidesigner.styles.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.TextureAsset
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Decodes an encoded image (PNG/JPEG/...) into a Compose bitmap.
 *
 * Implemented with Skia on desktop and `BitmapFactory` on Android; both are
 * the platform's own decoder, so imported textures look exactly the way the
 * user's image viewer showed them.
 */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

/** Reads the intrinsic pixel dimensions without fully decoding, when possible. */
expect fun readImageSize(bytes: ByteArray): Pair<Int, Int>?

/**
 * Decoded-bitmap cache keyed by texture id.
 *
 * The canvas repaints on every pointer move during a drag, so decoding on each
 * frame is not an option; entries are invalidated by the texture's identity,
 * which changes whenever the asset is re-imported or its nine-slice is edited.
 */
@OptIn(ExperimentalEncodingApi::class)
class TextureCache : TextureResolver {

    private val cache = mutableMapOf<String, Entry>()

    private data class Entry(val fingerprint: Int, val bitmap: ImageBitmap?)

    private var assets: Map<String, TextureAsset> = emptyMap()

    /** Points the cache at the project's current asset list. */
    fun setAssets(textures: List<TextureAsset>) {
        assets = textures.associateBy { it.id }
        // Drop entries whose asset disappeared so the cache cannot leak.
        val live = assets.keys
        cache.keys.retainAll { it in live }
    }

    override fun resolve(assetId: String?): ImageBitmap? {
        val id = assetId?.takeIf { it.isNotBlank() } ?: return null
        val asset = assets[id] ?: return null
        val fingerprint = asset.dataBase64.hashCode() * 31 + asset.format.hashCode()
        val cached = cache[id]
        if (cached != null && cached.fingerprint == fingerprint) return cached.bitmap

        val bitmap = runCatching { decodeImageBitmap(Base64.decode(asset.dataBase64)) }.getOrNull()
        cache[id] = Entry(fingerprint, bitmap)
        return bitmap
    }

    fun clear() = cache.clear()
}

/** Remembers a [TextureCache] bound to [project]'s texture list. */
@Composable
fun rememberTextureCache(project: GuiProject): TextureCache {
    val cache = remember { TextureCache() }
    cache.setAssets(project.textures)
    return cache
}

/**
 * Builds a [TextureAsset] from raw file bytes, reading the real pixel size so
 * the assets panel and nine-slice editor have something accurate to show.
 */
@OptIn(ExperimentalEncodingApi::class)
fun createTextureAsset(
    id: String,
    name: String,
    bytes: ByteArray,
    sourcePath: String? = null,
): TextureAsset {
    val format = when {
        bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "png"
        bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
        bytes.size > 12 && bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() -> "webp"
        bytes.size > 3 && bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() -> "gif"
        else -> sourcePath?.substringAfterLast('.', "png")?.lowercase() ?: "png"
    }
    val size = readImageSize(bytes) ?: (decodeImageBitmap(bytes)?.let { it.width to it.height }) ?: (0 to 0)
    return TextureAsset(
        id = id,
        name = name.substringAfterLast('/').substringAfterLast('\\'),
        format = format,
        width = size.first,
        height = size.second,
        dataBase64 = Base64.encode(bytes),
        sourcePath = sourcePath,
    )
}
