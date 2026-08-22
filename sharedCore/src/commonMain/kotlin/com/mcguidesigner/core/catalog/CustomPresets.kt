package com.mcguidesigner.core.catalog

import com.mcguidesigner.core.model.ColorValue
import com.mcguidesigner.core.model.EnumValue
import com.mcguidesigner.core.model.IntSize
import com.mcguidesigner.core.model.IntValue
import com.mcguidesigner.core.model.PropValue
import com.mcguidesigner.core.model.ShapeKind
import com.mcguidesigner.core.model.StringValue

/**
 * The contents of the "Add custom" surface, shared by both front-ends.
 *
 * A preset is a catalog type plus a handful of property overrides, not a type
 * of its own: seventeen shapes are seventeen values of `shape`, so the catalog,
 * the validator and both exporters keep dealing with exactly one shape type
 * however many the palette offers.
 *
 * Defining the list here rather than in each UI means the desktop dialog and
 * the Android sheet cannot end up offering different things.
 */
object CustomPresets {

    /** One entry in the add-custom picker. */
    data class Preset(
        val id: String,
        val label: String,
        val glyph: String,
        val typeId: String,
        val description: String = "",
        val props: Map<String, PropValue> = emptyMap(),
        val size: IntSize? = null,
    )

    /** Groups the picker renders as separate sections, in order. */
    enum class Group(val title: String, val subtitle: String) {
        SHAPES(
            "Shapes",
            "Drawn at any size, in any colour. Resize and rotate them like anything else.",
        ),
        MEDIA(
            "Animated & imagery",
            "Frame strips, GIFs and plain images.",
        ),
        ANYTHING(
            "Anything else",
            "A widget the catalog does not have - name it and give it whatever properties you need.",
        ),
    }

    /** Every shape, as its own one-tap preset. */
    val shapes: List<Preset> = ShapeKind.entries.map { kind ->
        Preset(
            id = "shape-${kind.id}",
            label = kind.displayName,
            glyph = kind.glyph,
            typeId = ElementCatalog.SHAPE_CUSTOM,
            description = kind.description,
            props = buildMap {
                put("shape", EnumValue(kind.id))
                // A star with its default six "sides" reads as a hexagram; five
                // points is the star anyone picturing a star has in mind.
                if (kind == ShapeKind.STAR) put("sides", IntValue(5))
            },
            // Shapes that read as horizontal - an arrow, a chevron, a
            // parallelogram - look wrong dropped in as a square.
            size = when (kind) {
                ShapeKind.ARROW_RIGHT, ShapeKind.PARALLELOGRAM, ShapeKind.TRAPEZOID ->
                    IntSize(64, 32)
                ShapeKind.CHEVRON -> IntSize(32, 48)
                ShapeKind.SPEECH_BUBBLE -> IntSize(72, 48)
                else -> null
            },
        )
    }

    val media: List<Preset> = listOf(
        Preset(
            id = "animated-image",
            label = "Animated image / GIF",
            glyph = "🎞",
            typeId = ElementCatalog.IMAGE_ANIMATED,
            description = "Import a GIF and it becomes a Minecraft frame strip that plays here " +
                "and in the game.",
            size = IntSize(48, 48),
        ),
        Preset(
            id = "still-image",
            label = "Still image",
            glyph = "🖼",
            typeId = ElementCatalog.IMAGE_PLACEHOLDER,
            description = "Any imported PNG or JPEG.",
        ),
    )

    val anything: List<Preset> = listOf(
        Preset(
            id = "custom-element",
            label = "Custom element",
            glyph = "✦",
            typeId = ElementCatalog.CUSTOM_ELEMENT,
            description = "Give it a type name and any `key=value` properties; every export " +
                "passes them through.",
        ),
        Preset(
            id = "custom-container",
            label = "Custom container",
            glyph = "❏",
            typeId = ElementCatalog.CUSTOM_ELEMENT,
            description = "The same thing, sized to hold other elements inside it.",
            props = mapOf(
                "customType" to StringValue("custom_container"),
                "label" to StringValue(""),
                "background" to ColorValue(0x40202020),
                "borderWidth" to IntValue(2),
            ),
            size = IntSize(140, 100),
        ),
    )

    fun presetsFor(group: Group): List<Preset> = when (group) {
        Group.SHAPES -> shapes
        Group.MEDIA -> media
        Group.ANYTHING -> anything
    }

    /** Every preset in group order, for search. */
    val all: List<Preset> get() = Group.entries.flatMap(::presetsFor)

    fun search(query: String): List<Preset> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return all
        return all.filter {
            needle in it.label.lowercase() || needle in it.description.lowercase()
        }
    }
}
