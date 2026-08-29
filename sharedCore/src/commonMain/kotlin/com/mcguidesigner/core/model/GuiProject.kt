package com.mcguidesigner.core.model

import kotlinx.serialization.Serializable

/** Current on-disk format version of a `.mcgui` document. */
const val PROJECT_FORMAT_VERSION: Int = 1

/**
 * How the area outside the authored screen is drawn in the preview.  This is
 * purely a preview concern but is stored with the project because a Bedrock
 * full-screen menu and a Java container overlay want very different backdrops.
 */
@Serializable
enum class CanvasBackdrop {
    NONE, DIM, GAME_WORLD, DIRT_PANORAMA, SOLID;

    val displayName: String
        get() = name.split('_').joinToString(" ") { p -> p.lowercase().replaceFirstChar { it.uppercase() } }
}

@Serializable
data class CanvasSpec(
    val width: Int = 176,
    val height: Int = 166,
    /** Minecraft GUI scale used by the live preview (1..4, or 0 for "auto"). */
    val guiScale: Int = 3,
    val gridSize: Int = 8,
    val backdrop: CanvasBackdrop = CanvasBackdrop.DIM,
    val backdropColor: Long = 0xC0101018,
    /**
     * Margins the design must keep clear, drawn as a dashed inset in the
     * editor and warned about by the validator.
     *
     * A phone's rounded corners and notch are the usual reason to want one, but
     * it is not a property of "being a phone layout" - a TV's overscan and a
     * window's title bar are the same problem - so it is simply a margin that
     * is either set or not, and the safe-area overlay follows whether it has
     * been set rather than a device category.
     */
    val safeArea: Insets = Insets.Zero,
) {
    val size: IntSize get() = IntSize(width, height)

    /** Whether a safe-area margin has actually been asked for. */
    val hasSafeArea: Boolean
        get() = safeArea.left > 0 || safeArea.top > 0 || safeArea.right > 0 || safeArea.bottom > 0
}

@Serializable
data class ProjectMeta(
    val author: String = "",
    val description: String = "",
    /** ISO-8601 timestamps; kept as strings to avoid a datetime dependency. */
    val createdAt: String = "",
    val modifiedAt: String = "",
    val tags: List<String> = emptyList(),
    /**
     * Namespace used by the exporters: `assets/<namespace>/...` for Java and
     * the JSON-UI namespace for Bedrock.
     */
    val namespace: String = "mcgui",
    /** Screen identifier, e.g. `custom_chest` -> `custom_chest.json`. */
    val screenId: String = "custom_screen",
)

/**
 * The complete, serializable state of one design document.
 *
 * Everything the editor needs to reproduce a screen lives here: the element
 * tree, the imported textures, canvas metrics and export metadata.  The editor
 * treats it as an immutable value - every mutation produces a new instance,
 * which is what makes snapshot-based undo/redo cheap and correct.
 */
@Serializable
data class GuiProject(
    val formatVersion: Int = PROJECT_FORMAT_VERSION,
    val id: String,
    val name: String,
    val edition: Edition,
    val canvas: CanvasSpec = CanvasSpec(),
    val elements: List<GuiElement> = emptyList(),
    val textures: List<TextureAsset> = emptyList(),
    val meta: ProjectMeta = ProjectMeta(),
) {
    fun element(id: String): GuiElement? = elements.findById(id)

    fun texture(id: String?): TextureAsset? = id?.let { key -> textures.firstOrNull { it.id == key } }

    val allElements: List<GuiElement> get() = elements.walkAll().toList()

    fun absoluteBounds(): Map<String, IntRect> = elements.absoluteBoundsMap(canvas.size)

    fun withElements(next: List<GuiElement>): GuiProject = copy(elements = next)
}
