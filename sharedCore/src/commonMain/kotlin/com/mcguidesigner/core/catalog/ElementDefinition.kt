package com.mcguidesigner.core.catalog

import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.model.IntSize
import com.mcguidesigner.core.model.PropValue

/** Palette grouping. Ordering here is the ordering shown in the UI. */
enum class ElementCategory(val displayName: String) {
    CONTAINERS("Containers"),
    INVENTORY("Inventory"),
    CONTROLS("Controls"),
    TEXT("Text & Input"),
    FEEDBACK("Feedback"),
    DECORATION("Decoration"),
    TOUCH("Touch Controls"),
    SHAPES("Shapes"),
    CUSTOM("Custom"),
}

/**
 * Everything the editor needs to know about one widget type.
 *
 * Definitions are pure data.  Rendering lives in the `:styles` module (one
 * skin per edition), export lives in `:exporters`, and the editor itself only
 * ever consults this catalog.
 */
data class ElementDefinition(
    val typeId: String,
    val displayName: String,
    val category: ElementCategory,
    val defaultSize: IntSize,
    val minSize: IntSize = IntSize(2, 2),
    val maxSize: IntSize = IntSize(4096, 4096),
    val editions: Set<Edition> = BOTH_EDITIONS,
    val acceptsChildren: Boolean = false,
    val interactive: Boolean = false,
    val resizable: Boolean = true,
    val keepAspect: Boolean = false,
    /** Glyph shown in the palette; deliberately ASCII so no font pack is needed. */
    val glyph: String = "▣",
    val description: String = "",
    val properties: List<PropertySpec> = emptyList(),
) {
    /** States this widget can be previewed/exported in. */
    val states: List<InteractionState>
        get() = when {
            !interactive -> listOf(InteractionState.NORMAL)
            else -> InteractionState.entries.toList()
        }

    fun supports(edition: Edition): Boolean = edition in editions

    fun property(key: String): PropertySpec? = properties.firstOrNull { it.key == key }

    /**
     * Edition-aware lookup.  A few keys (notably `font`) are declared twice
     * with different option sets, once per edition, so callers that know the
     * edition must use this overload.
     */
    fun property(key: String, edition: Edition): PropertySpec? =
        properties.firstOrNull { it.key == key && it.supports(edition) }

    fun propertiesFor(edition: Edition): List<PropertySpec> = properties.filter { it.supports(edition) }

    /** Default property map for a freshly created instance in [edition]. */
    fun defaultProps(edition: Edition): Map<String, PropValue> =
        propertiesFor(edition).associate { it.key to it.default }

    /** Default size, adjusted for touch targets on Bedrock where relevant. */
    fun defaultSizeFor(edition: Edition): IntSize =
        if (edition == Edition.BEDROCK && interactive) {
            IntSize(
                maxOf(defaultSize.width, minOf(TOUCH_MIN_TARGET, maxSize.width)),
                maxOf(defaultSize.height, minOf(TOUCH_MIN_TARGET, maxSize.height)),
            )
        } else {
            defaultSize
        }

    companion object {
        /**
         * Minimum comfortable touch target in GUI pixels.  Bedrock's own UI
         * guidance lands around this once the default 3x GUI scale is applied.
         */
        const val TOUCH_MIN_TARGET = 24
    }
}
