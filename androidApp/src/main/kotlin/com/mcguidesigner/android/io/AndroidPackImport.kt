package com.mcguidesigner.android.io

import android.content.Context
import android.net.Uri
import com.mcguidesigner.core.model.TextureAsset
import com.mcguidesigner.core.packs.PackScan
import com.mcguidesigner.core.packs.PackTexture
import com.mcguidesigner.core.packs.ResourcePackScan
import com.mcguidesigner.core.util.Ids
import com.mcguidesigner.styles.render.createTextureAsset
import java.util.zip.ZipInputStream

/**
 * Reads a Minecraft resource pack picked through the Storage Access Framework.
 *
 * A content [Uri] is a stream, not a file, so this cannot use `ZipFile` the way
 * the desktop importer does - it walks the archive with [ZipInputStream]
 * instead, once to list what is inside and once more to read the entries the
 * user chose.  Two sequential passes is the price of not holding a
 * multi-hundred-megabyte pack in memory on a phone.
 */
object AndroidPackImport {

    /** Archive MIME types the picker should offer. */
    val PACK_MIME_TYPES = arrayOf("application/zip", "application/octet-stream", "*/*")

    /** Hard ceiling per image. Anything larger is not a GUI texture. */
    private const val MAX_TEXTURE_BYTES = 16 * 1024 * 1024

    /**
     * Cap on entries listed.
     *
     * Some community packs contain six figures of files. Listing all of them
     * would spend a phone's memory building a list nobody is going to scroll
     * to the end of.
     */
    private const val MAX_ENTRIES = 20_000

    /** An archive the user picked, plus what it turned out to contain. */
    data class OpenedPack(
        val uri: Uri,
        val name: String,
        val scan: PackScan,
    )

    /** Lists an archive's importable images without decoding any of them. */
    fun open(context: Context, uri: Uri): Result<OpenedPack> = runCatching {
        val displayName = AndroidFileIO.displayName(context, uri)
        val names = mutableListOf<String>()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ZipInputStream(stream.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null && names.size < MAX_ENTRIES) {
                    if (!entry.isDirectory) names += entry.name
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("Could not open the selected file.")

        OpenedPack(
            uri = uri,
            name = ResourcePackScan.packNameFromFileName(displayName),
            scan = ResourcePackScan.scan(names, ResourcePackScan.packNameFromFileName(displayName)),
        )
    }

    /**
     * Reads and decodes [wanted] out of the archive.
     *
     * Entries that cannot be read or decoded are skipped rather than failing
     * the import: one corrupt PNG in a community pack should not cost the user
     * the other four hundred.
     */
    fun read(context: Context, pack: OpenedPack, wanted: List<PackTexture>): List<TextureAsset> {
        if (wanted.isEmpty()) return emptyList()
        val byPath = wanted.associateBy { it.path }
        val out = mutableListOf<TextureAsset>()

        runCatching {
            context.contentResolver.openInputStream(pack.uri)?.use { stream ->
                ZipInputStream(stream.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null && out.size < byPath.size) {
                        val texture = if (entry.isDirectory) null else byPath[entry.name]
                        if (texture != null) {
                            val bytes = runCatching { zip.readBytes() }.getOrNull()
                            if (bytes != null && bytes.isNotEmpty() && bytes.size <= MAX_TEXTURE_BYTES) {
                                val asset = runCatching {
                                    createTextureAsset(
                                        id = Ids.prefixed("tex"),
                                        name = texture.displayName,
                                        bytes = bytes,
                                        sourcePath = "${pack.name}!/${texture.path}",
                                    )
                                }.getOrNull()
                                // A zero-sized decode means the platform
                                // decoder rejected it; importing it would only
                                // produce a blank slot.
                                if (asset != null && asset.width > 0 && asset.height > 0) out += asset
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
        }
        return out
    }
}
