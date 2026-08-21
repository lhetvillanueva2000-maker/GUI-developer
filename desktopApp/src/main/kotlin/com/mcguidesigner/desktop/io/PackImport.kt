package com.mcguidesigner.desktop.io

import com.mcguidesigner.core.model.TextureAsset
import com.mcguidesigner.core.packs.PackScan
import com.mcguidesigner.core.packs.PackTexture
import com.mcguidesigner.core.packs.ResourcePackScan
import com.mcguidesigner.core.util.Ids
import com.mcguidesigner.styles.render.createTextureAsset
import java.io.File
import java.util.zip.ZipFile

/**
 * Opens a Minecraft resource pack (`.zip`) and pulls textures out of it.
 *
 * Reading happens in two passes on purpose: the first lists the entry names so
 * the user can see what is in the archive and choose, the second reads only the
 * bytes they actually asked for.  A full vanilla pack is thousands of images
 * and tens of megabytes; decoding all of it to import one button skin would
 * stall the app for no reason.
 */
object PackImport {

    /** Hard ceiling per image. Anything larger is not a GUI texture. */
    private const val MAX_TEXTURE_BYTES = 16 * 1024 * 1024

    /** What one archive contains, plus where it came from. */
    data class OpenedPack(
        val file: File,
        val scan: PackScan,
    ) {
        val name: String get() = scan.packName ?: ResourcePackScan.packNameFromFileName(file.name)
    }

    /** Lists an archive's importable images without reading any of them. */
    fun open(file: File): Result<OpenedPack> = runCatching {
        ZipFile(file).use { zip ->
            val names = zip.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.toList()
            OpenedPack(
                file = file,
                scan = ResourcePackScan.scan(names, ResourcePackScan.packNameFromFileName(file.name)),
            )
        }
    }

    /**
     * Reads and decodes [wanted] out of the archive.
     *
     * Entries that cannot be read or decoded are skipped rather than failing
     * the whole import: one corrupt PNG in a community pack should not cost the
     * user the other four hundred.
     */
    fun read(pack: OpenedPack, wanted: List<PackTexture>): List<TextureAsset> {
        if (wanted.isEmpty()) return emptyList()
        val byPath = wanted.associateBy { it.path }
        val out = mutableListOf<TextureAsset>()
        runCatching {
            ZipFile(pack.file).use { zip ->
                for ((path, texture) in byPath) {
                    val entry = zip.getEntry(path) ?: continue
                    if (entry.size > MAX_TEXTURE_BYTES) continue
                    val bytes = runCatching { zip.getInputStream(entry).use { it.readBytes() } }
                        .getOrNull() ?: continue
                    if (bytes.isEmpty()) continue
                    val asset = runCatching {
                        createTextureAsset(
                            id = Ids.prefixed("tex"),
                            name = texture.displayName,
                            bytes = bytes,
                            sourcePath = "${pack.file.name}!/$path",
                        )
                    }.getOrNull() ?: continue
                    // A zero-sized decode means the platform decoder rejected
                    // it; importing it would only produce a blank slot.
                    if (asset.width <= 0 || asset.height <= 0) continue
                    out += asset
                }
            }
        }
        return out
    }
}
