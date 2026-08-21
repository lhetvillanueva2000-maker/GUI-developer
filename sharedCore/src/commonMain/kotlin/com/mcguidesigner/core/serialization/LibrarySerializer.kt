package com.mcguidesigner.core.serialization

import com.mcguidesigner.core.library.PrefabLibrary
import com.mcguidesigner.core.library.TextureLibrary

/**
 * Reads and writes the two cross-project libraries: saved prefabs and the
 * texture library.
 *
 * Both reuse [ProjectSerializer]'s JSON configuration because prefabs contain
 * real [com.mcguidesigner.core.model.GuiElement]s, and those carry polymorphic
 * property values that only decode correctly under the same class
 * discriminator.
 *
 * Every read degrades to an empty library rather than throwing.  These files
 * are conveniences that accumulate over months; a half-written one should cost
 * the user their saved prefabs, not their ability to start the app.
 */
object LibrarySerializer {

    fun encodePrefabs(library: PrefabLibrary): String =
        ProjectSerializer.pretty.encodeToString(PrefabLibrary.serializer(), library)

    fun decodePrefabs(text: String): PrefabLibrary = runCatching {
        ProjectSerializer.pretty.decodeFromString(PrefabLibrary.serializer(), text)
    }.getOrElse { PrefabLibrary.Empty }

    fun encodeTextures(library: TextureLibrary): String =
        ProjectSerializer.compact.encodeToString(TextureLibrary.serializer(), library)

    fun decodeTextures(text: String): TextureLibrary = runCatching {
        ProjectSerializer.compact.decodeFromString(TextureLibrary.serializer(), text)
    }.getOrElse { TextureLibrary.Empty }
}
