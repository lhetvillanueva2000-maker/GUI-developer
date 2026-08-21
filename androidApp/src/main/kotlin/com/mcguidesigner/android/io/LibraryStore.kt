package com.mcguidesigner.android.io

import android.content.Context
import com.mcguidesigner.core.library.PrefabLibrary
import com.mcguidesigner.core.library.TextureLibrary
import com.mcguidesigner.core.serialization.LibrarySerializer
import java.io.File

/**
 * The two libraries that outlive any one project: saved prefabs and imported
 * textures.
 *
 * Internal storage, for the same reason [SessionStore] uses it: the app has to
 * be able to write these without a picker and without permissions.  They are
 * app data, not the user's documents.
 *
 * Every read degrades to an empty library.  A corrupt prefab file is a bad day;
 * an app that will not start because of one is a worse one.
 */
object LibraryStore {

    private const val PREFABS = "prefabs.json"
    private const val TEXTURES = "texture-library.json"

    private fun directory(context: Context) = File(context.filesDir, "library").apply { mkdirs() }

    // -- Prefabs -----------------------------------------------------------

    fun loadPrefabs(context: Context): PrefabLibrary {
        val file = File(directory(context), PREFABS)
        if (!file.isFile) return PrefabLibrary.Empty
        return runCatching { LibrarySerializer.decodePrefabs(file.readText()) }
            .getOrElse { PrefabLibrary.Empty }
    }

    fun savePrefabs(context: Context, library: PrefabLibrary) {
        write(context, PREFABS, LibrarySerializer.encodePrefabs(library))
    }

    // -- Textures ----------------------------------------------------------

    fun loadTextures(context: Context): TextureLibrary {
        val file = File(directory(context), TEXTURES)
        if (!file.isFile) return TextureLibrary.Empty
        return runCatching { LibrarySerializer.decodeTextures(file.readText()) }
            .getOrElse { TextureLibrary.Empty }
    }

    fun saveTextures(context: Context, library: TextureLibrary) {
        write(context, TEXTURES, LibrarySerializer.encodeTextures(library))
    }

    // -- Shared ------------------------------------------------------------

    /** Temp file then rename, so a kill mid-write cannot truncate the library. */
    private fun write(context: Context, name: String, content: String) {
        runCatching {
            val directory = directory(context)
            val temporary = File(directory, "$name.tmp")
            temporary.writeText(content)
            val target = File(directory, name)
            if (!temporary.renameTo(target)) {
                target.writeText(temporary.readText())
                temporary.delete()
            }
        }
    }
}
