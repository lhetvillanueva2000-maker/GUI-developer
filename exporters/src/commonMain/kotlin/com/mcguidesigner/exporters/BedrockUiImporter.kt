package com.mcguidesigner.exporters

import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.model.Anchor
import com.mcguidesigner.core.model.CanvasSpec
import com.mcguidesigner.core.model.ColorValue
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.PropValue
import com.mcguidesigner.core.model.StringValue
import com.mcguidesigner.core.util.Ids
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads a Bedrock `ui/<screen>.json` back into a design.
 *
 * The format is the one the game itself parses, so this is the import that
 * matters most: a Bedrock screen exported from here, hand-tuned in a text
 * editor against the running game, and brought back - which is how anybody
 * actually works on a JSON-UI screen once it is nearly right.
 *
 * Two levels of fidelity, and the difference is worth knowing:
 *
 *  - A file this app wrote carries `$designer_type` on every control, naming
 *    the catalog element it came from. Those come back as exactly what they
 *    were.
 *  - Any other file has only JSON UI's own `type`, which is a much smaller
 *    vocabulary - `button`, `label`, `image`, `panel`. Those are mapped to the
 *    nearest catalog element and reported, because a `button` that was a tab
 *    coming back as a plain button is a change somebody should be told about
 *    rather than left to discover.
 */
object BedrockUiImporter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Keys that are structure rather than a control. */
    private val RESERVED = setOf("namespace")

    fun read(content: String, name: String): ImportOutcome {
        val root = runCatching { json.parseToJsonElement(content).jsonObject }.getOrElse {
            return ImportOutcome.failed(
                ImportFormat.BEDROCK_JSON_UI,
                "This is not valid JSON: ${it.message}",
            )
        }

        // Every top-level object with a `type` is a control definition. The
        // key may carry an inheritance suffix (`thing@namespace.other`), which
        // is not part of the name.
        val definitions = root.entries
            .filter { it.key !in RESERVED }
            .mapNotNull { (key, value) ->
                val obj = value as? JsonObject ?: return@mapNotNull null
                key.substringBefore('@') to obj
            }
            .toMap()

        val screenContent = definitions["screen_content"]
        val canvas = canvasFrom(screenContent)
        val notes = mutableListOf<String>()
        val generic = mutableSetOf<String>()

        // Prefer the declared tree; fall back to "every control nothing else
        // claims as a child" for a file that does not have one.
        //
        // The claimed-child filter is the whole point of the fallback. Without
        // it, a screen whose controls are all reachable from somewhere would
        // come back with every nested control *also* sitting at the top level -
        // one design, every element in it twice.
        val roots = screenContent?.let { childrenOf(it, definitions) }
            ?.takeIf { it.isNotEmpty() }
            ?: run {
                val claimed = definitions.values.flatMap { control ->
                    (control["controls"] as? JsonArray).orEmpty().mapNotNull { entry ->
                        val key = (entry as? JsonObject)?.entries?.firstOrNull()?.key
                        key?.let { resolvedName(it, definitions) }
                    }
                }.toSet()

                definitions
                    .filterKeys { it != "screen_content" && it != "screen" && it !in claimed }
                    .filterValues { it.containsKey("type") }
                    .values
                    .toList()
                    .also {
                        if (it.isNotEmpty()) {
                            notes += "No screen_content panel found; read the unclaimed controls as top level."
                        }
                    }
            }

        val elements = roots.mapNotNull { control ->
            toElement(control, definitions, generic)
        }

        if (elements.isEmpty()) {
            return ImportOutcome.failed(
                ImportFormat.BEDROCK_JSON_UI,
                "No controls with a size and offset were found. A screen whose controls " +
                    "are sized in percentages cannot be placed on a fixed canvas.",
            )
        }

        if (generic.isNotEmpty()) {
            notes += "Mapped by JSON-UI type rather than exactly: " +
                "${generic.sorted().joinToString(", ")}. Files exported from here carry " +
                "\$designer_type and come back precisely."
        }

        return ImportOutcome(
            format = ImportFormat.BEDROCK_JSON_UI,
            project = GuiProject(
                id = Ids.prefixed("proj"),
                name = name.ifBlank { "Imported screen" },
                edition = Edition.BEDROCK,
                canvas = canvas,
                elements = elements,
            ),
            notes = notes,
        )
    }

    // -- Structure ---------------------------------------------------------

    /**
     * The controls a JSON-UI `controls` array points at.
     *
     * Each entry is a single-key object. The value is usually empty - the key's
     * `@namespace.name` suffix is the whole reference - but may also carry the
     * control inline, which is how most hand-written screens are laid out.
     */
    private fun childrenOf(control: JsonObject, definitions: Map<String, JsonObject>): List<JsonObject> {
        val list = control["controls"] as? JsonArray ?: return emptyList()
        return list.mapNotNull { entry ->
            val obj = entry as? JsonObject ?: return@mapNotNull null
            val (key, value) = obj.entries.firstOrNull() ?: return@mapNotNull null
            val inline = value as? JsonObject
            when {
                inline != null && inline.containsKey("type") -> inline
                else -> resolvedName(key, definitions)?.let { definitions[it] }
            }
        }
    }

    /**
     * Which definition `close@common.button` actually refers to.
     *
     * JSON UI writes a child reference as `alias@namespace.template`: the part
     * before the `@` names the control here, and the part after it names what
     * it inherits from. Both can be the definition being pointed at - a screen
     * may override `close` locally, or may simply be reusing `button` - so the
     * local name is tried first and the inherited one second, which is the
     * precedence the game itself uses.
     *
     * This used to look only before the `@`, so every aliased child - which is
     * to say most children in any hand-written screen - resolved to nothing and
     * was dropped without a word.
     *
     * Returning the *name* rather than the object is deliberate: the root-finder
     * needs to know which key was claimed, and having it re-derive that from a
     * second copy of this rule is how the two came to disagree in the first
     * place.
     */
    private fun resolvedName(key: String, definitions: Map<String, JsonObject>): String? {
        val alias = key.substringBefore('@')
        if (alias in definitions) return alias
        val inherited = key.substringAfter('@', "").substringAfterLast('.')
        return inherited.takeIf { it.isNotBlank() && it in definitions }
    }

    private fun canvasFrom(screenContent: JsonObject?): CanvasSpec {
        val size = intPair(screenContent?.get("size")) ?: return CanvasSpec()
        return CanvasSpec(width = size.first, height = size.second)
    }

    private fun toElement(
        control: JsonObject,
        definitions: Map<String, JsonObject>,
        generic: MutableSet<String>,
        depth: Int = 0,
    ): GuiElement? {
        // A cycle in `controls` would otherwise recurse until the stack goes.
        // JSON UI allows a control to reference itself and the game tolerates
        // it; nothing here needs to.
        if (depth > MAX_DEPTH) return null

        val size = intPair(control["size"]) ?: return null
        val offset = intPair(control["offset"]) ?: (0 to 0)
        val declared = control["\$designer_type"]?.jsonPrimitive?.contentOrNull()
        val jsonUiType = control["type"]?.jsonPrimitive?.contentOrNull()

        val type = when {
            declared != null && ElementCatalog[declared] != null -> declared
            else -> {
                jsonUiType?.let { generic += it }
                catalogTypeFor(jsonUiType)
            }
        }

        return GuiElement(
            id = Ids.prefixed("el"),
            type = type,
            name = nameFor(control, type),
            bounds = IntRect(offset.first, offset.second, size.first, size.second),
            anchor = anchorFor(control["anchor_from"]?.jsonPrimitive?.contentOrNull()),
            visible = control["visible"]?.jsonPrimitive?.booleanOrNull ?: true,
            props = propsFor(control, type),
            children = childrenOf(control, definitions).mapNotNull {
                toElement(it, definitions, generic, depth + 1)
            },
        )
    }

    private const val MAX_DEPTH = 32

    // -- Values ------------------------------------------------------------

    /**
     * `[16, 24]` as a pair of pixels, or null.
     *
     * Null for `["100%", 20]` and friends on purpose: a percentage is relative
     * to a parent this importer has no layout engine to measure, and putting
     * the element at a made-up size would be worse than leaving it out and
     * saying so.
     */
    private fun intPair(element: kotlinx.serialization.json.JsonElement?): Pair<Int, Int>? {
        val array = element as? JsonArray ?: return null
        if (array.size < 2) return null
        val first = (array[0] as? JsonPrimitive)?.intOrNull ?: return null
        val second = (array[1] as? JsonPrimitive)?.intOrNull ?: return null
        return first to second
    }

    private fun catalogTypeFor(jsonUiType: String?): String = when (jsonUiType) {
        "button" -> ElementCatalog.BUTTON_NORMAL
        "label" -> ElementCatalog.TEXT_LABEL
        "edit_box" -> ElementCatalog.INPUT_TEXTBOX
        "slider" -> ElementCatalog.INPUT_SLIDER
        "toggle" -> ElementCatalog.INPUT_CHECKBOX
        "dropdown" -> ElementCatalog.INPUT_DROPDOWN
        "scroll_view" -> ElementCatalog.CONTAINER_SCROLL
        "image" -> ElementCatalog.IMAGE_PLACEHOLDER
        else -> ElementCatalog.PANEL_FRAME
    }

    private fun anchorFor(raw: String?): Anchor = when (raw) {
        "top_middle" -> Anchor.TOP_CENTER
        "top_right" -> Anchor.TOP_RIGHT
        "left_middle" -> Anchor.CENTER_LEFT
        "center" -> Anchor.CENTER
        "right_middle" -> Anchor.CENTER_RIGHT
        "bottom_left" -> Anchor.BOTTOM_LEFT
        "bottom_middle" -> Anchor.BOTTOM_CENTER
        "bottom_right" -> Anchor.BOTTOM_RIGHT
        else -> Anchor.TOP_LEFT
    }

    private fun nameFor(control: JsonObject, type: String): String {
        control["\$button_text"]?.jsonPrimitive?.contentOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        control["text"]?.jsonPrimitive?.contentOrNull()?.takeIf { it.isNotBlank() }?.let { return it.take(40) }
        return ElementCatalog[type]?.displayName ?: type
    }

    private fun propsFor(control: JsonObject, type: String): Map<String, PropValue> = buildMap {
        bedrockColor(control)?.let { argb ->
            // A label's colour is its text; everything else's is its fill.
            val key = if (type == ElementCatalog.TEXT_LABEL) "textColor" else "background"
            put(key, ColorValue(argb))
        }

        control["\$button_text"]?.jsonPrimitive?.contentOrNull()?.let { put("label", StringValue(it)) }
        control["text"]?.jsonPrimitive?.contentOrNull()?.let {
            val key = when (type) {
                ElementCatalog.BAR_HEADER -> "title"
                ElementCatalog.INPUT_TEXTBOX, ElementCatalog.INPUT_SEARCH -> "placeholder"
                else -> "text"
            }
            put(key, StringValue(it))
        }
        control["text_alignment"]?.jsonPrimitive?.contentOrNull()?.let { put("align", StringValue(it)) }
        control["font_type"]?.jsonPrimitive?.contentOrNull()?.let { put("font", StringValue(it)) }
    }

    /**
     * `"color": [r, g, b]` with 0..1 floats, plus an optional `"alpha"`.
     *
     * Packed back into the `0xAARRGGBB` the model uses, so a colour written by
     * [BedrockEditionExporter] survives the trip out and back.
     */
    private fun bedrockColor(control: JsonObject): Long? {
        val array = control["color"] as? JsonArray ?: return null
        if (array.size < 3) return null
        val channels = (0..2).map { index ->
            val value = (array[index] as? JsonPrimitive)?.floatOrNull ?: return null
            (value.coerceIn(0f, 1f) * 255f).toInt().toLong()
        }
        val alpha = ((control["alpha"] as? JsonPrimitive)?.floatOrNull ?: 1f)
            .coerceIn(0f, 1f).times(255f).toInt().toLong()
        return (alpha shl 24) or (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
    }

    /** `content` for a string, null for JSON null - which reads as "null". */
    private fun JsonPrimitive.contentOrNull(): String? = if (this is kotlinx.serialization.json.JsonNull) {
        null
    } else {
        content
    }
}
