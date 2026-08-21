package com.mcguidesigner.desktop.io

import com.mcguidesigner.core.library.PrefabLibrary
import com.mcguidesigner.core.library.TextureLibrary
import com.mcguidesigner.core.serialization.LibrarySerializer
import java.io.File

/**
 * The two libraries that outlive any one project: saved prefabs and imported
 * textures.
 *
 * They live beside the preferences in the per-user data directory, written with
 * the same write-to-temp-then-rename dance, because a library someone has been
 * filling for months is worth considerably more than the project currently open
 * in front of them.
 */
object LibraryStore {

    private const val PREFABS_FILE = "prefabs.json"
    private const val TEXTURES_FILE = "texture-library.json"

    private val directory: File get() = Workspace.directory

    // -- Prefabs -----------------------------------------------------------

    fun loadPrefabs(): PrefabLibrary {
        val file = File(directory, PREFABS_FILE)
        if (!file.isFile) return PrefabLibrary.Empty
        return runCatching { LibrarySerializer.decodePrefabs(file.readText()) }
            .getOrElse { PrefabLibrary.Empty }
    }

    fun savePrefabs(library: PrefabLibrary): Boolean =
        writeAtomically(PREFABS_FILE, LibrarySerializer.encodePrefabs(library))

    // -- Textures ----------------------------------------------------------

    fun loadTextures(): TextureLibrary {
        val file = File(directory, TEXTURES_FILE)
        if (!file.isFile) return TextureLibrary.Empty
        return runCatching { LibrarySerializer.decodeTextures(file.readText()) }
            .getOrElse { TextureLibrary.Empty }
    }

    fun saveTextures(library: TextureLibrary): Boolean =
        writeAtomically(TEXTURES_FILE, LibrarySerializer.encodeTextures(library))

    /** Bytes on disk, so the UI can tell the user what the library is costing. */
    fun textureLibraryBytes(): Long = File(directory, TEXTURES_FILE).takeIf { it.isFile }?.length() ?: 0L

    // -- Shared ------------------------------------------------------------

    private fun writeAtomically(name: String, content: String): Boolean = runCatching {
        directory.mkdirs()
        val target = File(directory, name)
        val temporary = File(directory, "$name.tmp")
        temporary.writeText(content)
        if (!temporary.renameTo(target)) {
            // Rename across a filesystem boundary can fail; a copy is still
            // better than losing the library outright.
            target.writeText(temporary.readText())
            temporary.delete()
        }
        true
    }.getOrElse { false }
}
