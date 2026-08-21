package com.mcguidesigner.core.library

import com.mcguidesigner.core.model.TextureAsset
import kotlinx.serialization.Serializable

/**
 * Content identity of an imported image.
 *
 * Two files that decode to the same bytes are the same texture no matter what
 * they were called or which pack they came from, and a library that keeps
 * re-adding the same 16x16 button skin every time a pack is imported becomes
 * useless within a week.
 */
fun TextureAsset.contentKey(): String =
    "$format:${width}x$height:${dataBase64.length}:${dataBase64.hashCode()}"

/** One image in the cross-project library, plus how it got there. */
@Serializable
data class LibraryTexture(
    val asset: TextureAsset,
    val tags: List<String> = emptyList(),
    /** Where it came from: "Imported", or the name of a resource pack. */
    val source: String = "",
    val addedAtMillis: Long = 0L,
) {
    val id: String get() = asset.id

    fun matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        return asset.name.lowercase().contains(needle) ||
            source.lowercase().contains(needle) ||
            tags.any { it.lowercase().contains(needle) }
    }
}

/**
 * Every texture the user has ever imported, kept outside any one project.
 *
 * Projects stay self-contained - a `.mcgui` still embeds the images it uses -
 * but re-importing the same art for the fifth project is exactly the sort of
 * chore a design tool should have removed.
 */
@Serializable
data class TextureLibrary(
    val version: Int = CURRENT_VERSION,
    val entries: List<LibraryTexture> = emptyList(),
) {
    operator fun get(id: String): LibraryTexture? = entries.firstOrNull { it.id == id }

    val size: Int get() = entries.size

    /** Every tag in use, sorted, for the filter chips. */
    val allTags: List<String> get() = entries.flatMap { it.tags }.distinct().sorted()

    /** Every source in use, sorted - one entry per imported pack, in practice. */
    val allSources: List<String>
        get() = entries.map { it.source }.filter { it.isNotBlank() }.distinct().sorted()

    /**
     * Adds [entry] unless the same image is already stored, in which case the
     * existing entry is kept and its tags are merged with the new ones.
     */
    fun with(entry: LibraryTexture): TextureLibrary {
        val key = entry.asset.contentKey()
        val existing = entries.firstOrNull { it.asset.contentKey() == key }
        if (existing == null) return copy(entries = listOf(entry) + entries)
        val merged = existing.copy(
            tags = (existing.tags + entry.tags).distinct(),
            source = existing.source.ifBlank { entry.source },
        )
        return copy(entries = entries.map { if (it.id == existing.id) merged else it })
    }

    fun withAll(incoming: List<LibraryTexture>): TextureLibrary =
        incoming.fold(this) { library, entry -> library.with(entry) }

    fun without(id: String): TextureLibrary = copy(entries = entries.filterNot { it.id == id })

    fun retagged(id: String, tags: List<String>): TextureLibrary =
        copy(entries = entries.map { if (it.id == id) it.copy(tags = tags.distinct()) else it })

    fun search(query: String = "", tag: String? = null, source: String? = null): List<LibraryTexture> =
        entries.filter { entry ->
            entry.matches(query) &&
                (tag == null || tag in entry.tags) &&
                (source == null || entry.source == source)
        }

    /** True when [asset]'s bytes are already stored under some other name. */
    fun contains(asset: TextureAsset): Boolean {
        val key = asset.contentKey()
        return entries.any { it.asset.contentKey() == key }
    }

    companion object {
        const val CURRENT_VERSION = 1
        val Empty = TextureLibrary()
    }
}
