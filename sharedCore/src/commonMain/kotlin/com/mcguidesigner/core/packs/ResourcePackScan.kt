package com.mcguidesigner.core.packs

import com.mcguidesigner.core.model.Edition

/** What kind of archive was dropped on the importer. */
enum class PackKind(val displayName: String, val edition: Edition?) {
    JAVA_RESOURCE_PACK("Java Edition resource pack", Edition.JAVA),
    BEDROCK_RESOURCE_PACK("Bedrock Edition resource pack", Edition.BEDROCK),

    /** A zip full of images that is not laid out like either edition's pack. */
    PLAIN_ARCHIVE("Image archive", null),
}

/**
 * What part of the game an image belongs to.
 *
 * The importer sorts by this: someone importing a 4000-file pack to skin a
 * button wants the GUI art first and almost never wants the 900 block
 * textures at all.
 */
enum class PackTextureRole(val displayName: String, val defaultTag: String) {
    GUI("GUI", "gui"),
    ITEM("Items", "item"),
    BLOCK("Blocks", "block"),
    ENTITY("Entities", "entity"),
    FONT("Font", "font"),
    OTHER("Other", "misc"),
}

/** One importable image found inside an archive. */
data class PackTexture(
    /** Full path inside the archive. */
    val path: String,
    val role: PackTextureRole,
    /** Human-readable name, e.g. `widgets` for `.../gui/widgets.png`. */
    val displayName: String,
    /** Java namespace (`minecraft`, `mymod`, ...) when the layout has one. */
    val namespace: String? = null,
) {
    val fileName: String get() = path.substringAfterLast('/')
}

/** Everything the importer learned about one archive. */
data class PackScan(
    val kind: PackKind,
    val textures: List<PackTexture>,
    /** Pack name taken from the archive itself, when it declares one. */
    val packName: String? = null,
) {
    val isEmpty: Boolean get() = textures.isEmpty()

    /** Textures grouped by role, in declaration order, skipping empty roles. */
    fun byRole(): List<Pair<PackTextureRole, List<PackTexture>>> =
        PackTextureRole.entries.mapNotNull { role ->
            textures.filter { it.role == role }.takeIf { it.isNotEmpty() }?.let { role to it }
        }

    fun countFor(role: PackTextureRole): Int = textures.count { it.role == role }
}

/**
 * Reads the shape of a Minecraft resource pack from nothing but its entry
 * names.
 *
 * Deliberately free of any zip or file API: the desktop and Android shells
 * each open the archive with their own reader and hand the names here, which
 * keeps the classification rules - the part that is easy to get subtly wrong -
 * in one place with tests around it.
 */
object ResourcePackScan {

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "tga")

    /** Directories no one ever wants imported as art. */
    private val IGNORED_SEGMENTS = setOf("__macosx", ".git", "meta-inf")

    fun isImage(path: String): Boolean =
        path.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS

    /**
     * Java packs are identified by `pack.mcmeta` / `assets/`; Bedrock ones by
     * `manifest.json` / `textures/`.  A zip with neither is still useful - it
     * is just a folder of images - so it is accepted as [PackKind.PLAIN_ARCHIVE]
     * rather than rejected.
     */
    fun detectKind(paths: List<String>): PackKind {
        val normalised = paths.map { it.lowercase().trimStart('/') }
        val java = normalised.any { it == "pack.mcmeta" || it.endsWith("/pack.mcmeta") } ||
            normalised.any { it.startsWith("assets/") || it.contains("/assets/minecraft/") }
        val bedrock = normalised.any { it == "manifest.json" || it.endsWith("/manifest.json") } ||
            normalised.any { it.startsWith("textures/ui/") || it.contains("/textures/ui/") }
        return when {
            java && !bedrock -> PackKind.JAVA_RESOURCE_PACK
            bedrock && !java -> PackKind.BEDROCK_RESOURCE_PACK
            // A pack claiming to be both is almost always a Java pack with a
            // stray manifest; `assets/` is the stronger signal.
            java && bedrock -> PackKind.JAVA_RESOURCE_PACK
            else -> PackKind.PLAIN_ARCHIVE
        }
    }

    /**
     * Classifies one entry, or returns null when it is not an importable image
     * (directories, JSON, sounds, macOS resource forks, ...).
     */
    fun classify(rawPath: String): PackTexture? {
        val path = rawPath.trimStart('/')
        if (path.isEmpty() || path.endsWith("/")) return null
        if (!isImage(path)) return null

        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.any { it.lowercase() in IGNORED_SEGMENTS }) return null
        // A `.png` whose name starts with `._` is a macOS metadata twin, not art.
        if (segments.last().startsWith("._")) return null

        val lower = segments.map { it.lowercase() }
        val namespace = lower.indexOf("assets")
            .takeIf { it >= 0 && it + 1 < segments.size }
            ?.let { segments[it + 1] }

        val role = when {
            lower.contains("gui") || lower.contains("ui") || lower.contains("hud") -> PackTextureRole.GUI
            lower.contains("item") || lower.contains("items") -> PackTextureRole.ITEM
            lower.contains("block") || lower.contains("blocks") -> PackTextureRole.BLOCK
            lower.contains("entity") || lower.contains("models") -> PackTextureRole.ENTITY
            lower.contains("font") -> PackTextureRole.FONT
            else -> PackTextureRole.OTHER
        }

        return PackTexture(
            path = path,
            role = role,
            displayName = segments.last().substringBeforeLast('.'),
            namespace = namespace,
        )
    }

    /**
     * Full scan of an archive's entry names.
     *
     * GUI art comes first and everything is then ordered by path, so the
     * importer's list is stable between runs and the useful part is at the top.
     */
    fun scan(paths: List<String>, packName: String? = null): PackScan {
        val textures = paths.mapNotNull(::classify)
            .distinctBy { it.path }
            .sortedWith(compareBy({ it.role.ordinal }, { it.path }))
        return PackScan(detectKind(paths), textures, packName)
    }

    /**
     * A display name for the pack, derived from the archive's own file name.
     *
     * Reading it out of `pack.mcmeta` would be more accurate but needs the
     * entry's contents; the file name is what the user recognises anyway.
     */
    fun packNameFromFileName(fileName: String): String =
        fileName.substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .ifEmpty { "Imported pack" }
}
