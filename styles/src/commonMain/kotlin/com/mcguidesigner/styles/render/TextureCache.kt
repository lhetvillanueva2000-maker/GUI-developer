package com.mcguidesigner.styles.render

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import com.mcguidesigner.core.image.AnimatedTextureImport
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
 *
 * Animated sources are converted here rather than at the call sites: a GIF is
 * turned into the vertical frame strip both editions animate, so importing one
 * on the phone and importing one on the desktop cannot produce different
 * projects, and nothing downstream ever has to handle a GIF.
 */
@OptIn(ExperimentalEncodingApi::class)
fun createTextureAsset(
    id: String,
    name: String,
    bytes: ByteArray,
    sourcePath: String? = null,
): TextureAsset {
    val cleanName = name.substringAfterLast('/').substringAfterLast('\\')
    if (AnimatedTextureImport.isAnimatedSource(bytes)) {
        AnimatedTextureImport.fromBytes(
            bytes = bytes,
            id = id,
            // A `.gif` suffix on what is now a PNG would be a lie the exporters
            // would faithfully write into the resource pack.
            name = cleanName.removeSuffix(".gif").removeSuffix(".GIF"),
            sourcePath = sourcePath,
        )?.let { return it }
        // Falling through means the GIF was unreadable; the platform decoder
        // below may still manage a first frame, which beats importing nothing.
    }

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
        name = cleanName,
        format = format,
        width = size.first,
        height = size.second,
        dataBase64 = Base64.encode(bytes),
        sourcePath = sourcePath,
    )
}

/**
 * Builds one animated [TextureAsset] out of several separate images.
 *
 * The route in for anything that is not a GIF: frames exported from a video,
 * a sequence rendered by another tool, a sprite sheet already cut up.  The
 * files are decoded with the platform's own decoder and stacked into the
 * vertical strip both editions animate, so from that point on the source
 * format is nobody's problem.
 *
 * [frameFiles] is used in the order given - callers should sort by file name,
 * which is how frame sequences are always numbered.  Returns null when fewer
 * than two frames could be decoded, because one frame is a still and should be
 * imported as one.
 */
fun createAnimatedTextureFromFrames(
    id: String,
    name: String,
    frameFiles: List<ByteArray>,
    frameDelayMillis: Int = AnimatedTextureImport.FALLBACK_FRAME_DELAY_MILLIS,
    sourcePath: String? = null,
): TextureAsset? {
    val frames = frameFiles.mapNotNull { file ->
        val bitmap = runCatching { decodeImageBitmap(file) }.getOrNull() ?: return@mapNotNull null
        val map = runCatching { bitmap.toPixelMap() }.getOrNull() ?: return@mapNotNull null
        AnimatedTextureImport.FramePixels(
            // toPixelMap can hand back a buffer with row padding, so the rows
            // are copied out rather than the backing array being trusted.
            pixels = IntArray(bitmap.width * bitmap.height) { index ->
                val x = index % bitmap.width
                val y = index / bitmap.width
                map[x, y].toArgbInt()
            },
            width = bitmap.width,
            height = bitmap.height,
            delayMillis = frameDelayMillis,
        )
    }
    if (frames.size < 2) return null

    return AnimatedTextureImport.fromFrameImages(
        frames = frames,
        id = id,
        name = name.substringAfterLast('/').substringAfterLast('\\'),
        frameDelayMillis = frameDelayMillis,
        sourcePath = sourcePath,
    )
}

/** Packs a Compose colour into the 0xAARRGGBB form the image pipeline uses. */
private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int {
    fun channel(value: Float) = (value * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (channel(alpha) shl 24) or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
}
