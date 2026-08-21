package com.mcguidesigner.core.library

import com.mcguidesigner.core.model.Anchor
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.IntSize
import com.mcguidesigner.core.model.TextureAsset
import com.mcguidesigner.core.model.TextureValue
import com.mcguidesigner.core.model.walkAll
import kotlinx.serialization.Serializable

/**
 * A reusable group of elements: a header bar and its buttons, a 3x9 slot grid,
 * a whole settings row - saved once and dropped into any later design.
 *
 * The elements are stored *rebased*, so the group's own top-left corner sits at
 * (0, 0) and every root carries [Anchor.TOP_LEFT].  Inserting is then a plain
 * translation, which is what makes a prefab behave identically whether it lands
 * on bare canvas or inside a container.
 *
 * Referenced [textures] travel with the prefab.  Without them a prefab saved
 * from one project and used in another would arrive with every custom skin
 * silently blank, which is worse than refusing to save it at all.
 */
@Serializable
data class Prefab(
    val id: String,
    val name: String,
    val edition: Edition,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val elements: List<GuiElement> = emptyList(),
    val textures: List<TextureAsset> = emptyList(),
    val createdAtMillis: Long = 0L,
) {
    /** Bounding size of the whole group, in GUI pixels. */
    val size: IntSize
        get() = elements.takeIf { it.isNotEmpty() }
            ?.let { roots -> IntRect.bounds(roots.map { it.bounds }).size }
            ?: IntSize.Zero

    /** Every node, not just the roots - what the gallery reports as its weight. */
    val elementCount: Int get() = elements.walkAll().count()

    val isEmpty: Boolean get() = elements.isEmpty()

    /** Free-text match over the name, description and tags. */
    fun matches(query: String): Boolean {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        return name.lowercase().contains(needle) ||
            description.lowercase().contains(needle) ||
            tags.any { it.lowercase().contains(needle) }
    }

    companion object {
        /**
         * Builds a prefab from element subtrees and the absolute canvas-space
         * bounds they currently occupy.
         *
         * [absoluteBounds] has to cover every root; roots without an entry are
         * dropped rather than guessed at, because a prefab that silently
         * misplaces one of its parts is harder to notice than a missing one.
         */
        fun fromElements(
            id: String,
            name: String,
            edition: Edition,
            roots: List<GuiElement>,
            absoluteBounds: Map<String, IntRect>,
            projectTextures: List<TextureAsset> = emptyList(),
            description: String = "",
            tags: List<String> = emptyList(),
            createdAtMillis: Long = 0L,
        ): Prefab? {
            val placed = roots.mapNotNull { root -> absoluteBounds[root.id]?.let { root to it } }
            if (placed.isEmpty()) return null

            val origin = IntRect.bounds(placed.map { it.second })
            val rebased = placed.map { (root, absolute) ->
                // Flattening to TOP_LEFT is deliberate: the anchor described a
                // relationship to a parent that the prefab no longer has.
                root.copy(
                    anchor = Anchor.TOP_LEFT,
                    bounds = IntRect(
                        absolute.x - origin.x,
                        absolute.y - origin.y,
                        root.bounds.width,
                        root.bounds.height,
                    ),
                )
            }

            val used = referencedTextureIds(rebased)
            return Prefab(
                id = id,
                name = name,
                edition = edition,
                description = description,
                tags = tags,
                elements = rebased,
                textures = projectTextures.filter { it.id in used },
                createdAtMillis = createdAtMillis,
            )
        }

        /** Ids of every texture asset the given subtrees refer to, in any state. */
        fun referencedTextureIds(elements: List<GuiElement>): Set<String> = buildSet {
            elements.walkAll().forEach { element ->
                val values = element.props.values + element.stateOverrides.values.flatMap { it.values }
                values.forEach { value ->
                    (value as? TextureValue)?.assetId?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
    }
}

/**
 * The user's saved prefabs.
 *
 * Stored as one document per platform (a file on desktop, internal storage on
 * Android) rather than inside a project, because the whole point is to carry a
 * component across projects.
 */
@Serializable
data class PrefabLibrary(
    val version: Int = CURRENT_VERSION,
    val prefabs: List<Prefab> = emptyList(),
) {
    operator fun get(id: String): Prefab? = prefabs.firstOrNull { it.id == id }

    fun forEdition(edition: Edition): List<Prefab> = prefabs.filter { it.edition == edition }

    /** Adds [prefab], replacing any existing entry with the same id. */
    fun with(prefab: Prefab): PrefabLibrary =
        copy(prefabs = listOf(prefab) + prefabs.filterNot { it.id == prefab.id })

    fun without(id: String): PrefabLibrary = copy(prefabs = prefabs.filterNot { it.id == id })

    fun renamed(id: String, name: String): PrefabLibrary =
        copy(prefabs = prefabs.map { if (it.id == id) it.copy(name = name) else it })

    fun search(query: String, edition: Edition? = null): List<Prefab> =
        prefabs.filter { (edition == null || it.edition == edition) && it.matches(query) }

    companion object {
        const val CURRENT_VERSION = 1
        val Empty = PrefabLibrary()
    }
}
