package com.mcguidesigner.exporters

import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.Branding
import com.mcguidesigner.core.model.BoolValue
import com.mcguidesigner.core.model.ColorValue
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.EnumValue
import com.mcguidesigner.core.model.FloatValue
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.model.IntValue
import com.mcguidesigner.core.model.ListValue
import com.mcguidesigner.core.model.StringValue
import com.mcguidesigner.core.model.TextureValue
import com.mcguidesigner.core.model.int
import com.mcguidesigner.core.model.string
import com.mcguidesigner.core.model.stringList
import com.mcguidesigner.core.model.walkAll
import com.mcguidesigner.core.util.Ids
import com.mcguidesigner.core.validation.ProjectValidator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Produces a Bedrock Edition resource pack containing real JSON UI.
 *
 * Unlike Java, Bedrock screens *are* data: the emitted `ui/<screen>.json`
 * follows the JSON-UI conventions the game itself uses - a namespace, one
 * control definition per element, `@namespace.control` inheritance for the
 * screen tree, and `anchor_from`/`anchor_to`/`offset` positioning.
 */
object BedrockEditionExporter {

    /** Minecraft Bedrock resource pack format version. */
    val DEFAULT_MIN_ENGINE_VERSION = listOf(1, 21, 0)

    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun export(project: GuiProject): ExportBundle {
        val report = ProjectValidator.validate(project, strict = true)
        val screenId = project.meta.screenId.ifBlank { Ids.slug(project.name) }
        val namespace = "${project.meta.namespace.ifBlank { "mcgui" }}_$screenId"
        val root = "${screenId}_bedrock_pack"
        val files = mutableListOf<ExportFile>()

        files += ExportFile.Text("$root/manifest.json", manifest(project))
        files += ExportFile.Text("$root/ui/_ui_defs.json", uiDefs(screenId))
        files += ExportFile.Text("$root/ui/_global_variables.json", globalVariables(project, namespace))
        files += ExportFile.Text("$root/ui/$screenId.json", screenJson(project, namespace))
        files += ExportFile.Text("$root/texts/en_US.lang", langFile(project, namespace, screenId))

        project.textures.forEach { texture ->
            files += ExportFile.Binary("$root/textures/ui/${texture.exportFileName()}", texture.dataBase64)
            // Bedrock reads the same `animation` block Java does for textures
            // under `textures/`, so an imported GIF animates here too.
            NativeAssets.mcmetaFor(texture)?.let { mcmeta ->
                files += ExportFile.Text("$root/textures/ui/${texture.mcmetaFileName()}", mcmeta)
            }
        }

        files += ExportFile.Text("$root/README.md", readme(project, namespace, screenId))

        val warnings = report.issues.filterNot { it.code == com.mcguidesigner.core.validation.IssueCode.EMPTY_CANVAS } +
            if (project.edition == Edition.BEDROCK) ProjectValidator.parityIssues(project) else emptyList()

        return ExportBundle(ExportTarget.BEDROCK_UI_PACK, root, files, warnings)
    }

    // -- manifest ----------------------------------------------------------

    /**
     * Pack UUIDs derived from the project instead of randomly generated.
     *
     * Two reasons this matters. Re-exporting a project keeps the same pack
     * identity, so an updated pack *replaces* the previous one in the game
     * rather than showing up beside it. And it makes the export byte-for-byte
     * reproducible, which is what lets CI verify the committed sample output.
     */
    private fun uuidFrom(vararg parts: String): String {
        val key = parts.joinToString("|")
        val hi = fnv1a(FNV_OFFSET, key)
        val lo = fnv1a(FNV_OFFSET_ALT, "$key#lo")
        val hex = hi.toULong().toString(16).padStart(16, '0') +
            lo.toULong().toString(16).padStart(16, '0')

        // Force the version (4) and variant (10xx) nibbles so the result is a
        // well-formed UUID and not just a random-looking hex string.
        val variant = ((hex[16].digitToInt(16) and 0x3) or 0x8).toString(16)
        val normalised = hex.substring(0, 12) + "4" + hex.substring(13, 16) +
            variant + hex.substring(17, 32)

        return buildString {
            append(normalised, 0, 8); append('-')
            append(normalised, 8, 12); append('-')
            append(normalised, 12, 16); append('-')
            append(normalised, 16, 20); append('-')
            append(normalised, 20, 32)
        }
    }

    private const val FNV_OFFSET = -0x340d631b7bdddcdbL
    private const val FNV_OFFSET_ALT = 0x2545F4914F6CDD1DL
    private const val FNV_PRIME = 0x100000001b3L

    private fun fnv1a(seed: Long, text: String): Long {
        var hash = seed
        for (ch in text) {
            hash = hash xor ch.code.toLong()
            hash *= FNV_PRIME
        }
        return hash
    }

    private fun manifest(project: GuiProject): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format_version", 2)
            putJsonObject("header") {
                put("name", project.name)
                put(
                    "description",
                    project.meta.description.ifBlank { "UI pack generated by ${Branding.NAME}" },
                )
                put("uuid", uuidFrom(project.id, project.meta.namespace, project.meta.screenId, "header"))
                putJsonArray("version") { add(1); add(0); add(0) }
                putJsonArray("min_engine_version") { DEFAULT_MIN_ENGINE_VERSION.forEach { add(it) } }
            }
            putJsonArray("modules") {
                add(
                    buildJsonObject {
                        put("type", "resources")
                        put("uuid", uuidFrom(project.id, project.meta.namespace, project.meta.screenId, "resources"))
                        putJsonArray("version") { add(1); add(0); add(0) }
                        put("description", "UI definitions")
                    },
                )
            }
            putJsonObject("metadata") {
                putJsonArray("authors") {
                    add(project.meta.author.ifBlank { "${Branding.NAME}" })
                }
                put("generated_with", Branding.SLUG)
            }
        },
    )

    private fun uiDefs(screenId: String): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            putJsonArray("ui_defs") { add("ui/$screenId.json") }
        },
    )

    private fun globalVariables(project: GuiProject, namespace: String): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("\$${namespace}_canvas_width", project.canvas.width)
            put("\$${namespace}_canvas_height", project.canvas.height)
            put("\$${namespace}_safe_left", project.canvas.safeArea.left)
            put("\$${namespace}_safe_top", project.canvas.safeArea.top)
            put("\$${namespace}_safe_right", project.canvas.safeArea.right)
            put("\$${namespace}_safe_bottom", project.canvas.safeArea.bottom)
        },
    )

    // -- The screen --------------------------------------------------------

    /**
     * Maps a designer element onto the closest JSON-UI control type.  Anything
     * without a native equivalent falls back to a composed `panel`, which is
     * exactly what vanilla does for its own compound widgets.
     */
    private fun controlType(type: String): String = when (type) {
        ElementCatalog.TEXT_LABEL, ElementCatalog.BAR_HEADER -> "label"
        ElementCatalog.BUTTON_NORMAL,
        ElementCatalog.BUTTON_TOGGLE,
        ElementCatalog.BUTTON_TAB,
        ElementCatalog.BUTTON_ICON,
        ElementCatalog.BEDROCK_ACTION_BUTTON,
        -> "button"

        ElementCatalog.INPUT_TEXTBOX, ElementCatalog.INPUT_SEARCH -> "edit_box"
        ElementCatalog.INPUT_SLIDER -> "slider"
        ElementCatalog.INPUT_CHECKBOX -> "toggle"
        ElementCatalog.INPUT_DROPDOWN -> "dropdown"
        ElementCatalog.CONTAINER_SCROLL -> "scroll_view"
        ElementCatalog.IMAGE_PLACEHOLDER, ElementCatalog.PROGRESS_BAR, ElementCatalog.DECOR_SEPARATOR -> "image"
        // JSON UI animates an `image` from its texture's own .mcmeta, which is
        // why an animated element is an image here and not something exotic.
        ElementCatalog.IMAGE_ANIMATED -> "image"
        // A shape has no JSON-UI primitive. `image` is the closest thing that
        // can carry a colour and a texture, and it degrades to a coloured
        // rectangle rather than vanishing.
        ElementCatalog.SHAPE_CUSTOM -> "image"
        ElementCatalog.SLOT_INVENTORY -> "custom"
        else -> "panel"
    }

    fun screenJson(project: GuiProject, namespace: String): String {
        val controls = LinkedHashMap<String, JsonElement>()
        val rootRefs = mutableListOf<JsonElement>()

        project.elements.forEach { element ->
            collectControls(element, project, namespace, controls)
            rootRefs += buildJsonObject {
                put(
                    "${controlName(element)}@$namespace.${controlName(element)}",
                    buildJsonObject { },
                )
            }
        }

        val document = buildJsonObject {
            put("namespace", namespace)

            // Screen root - inherits the vanilla base screen so the pack keeps
            // working with the rest of the UI (input handling, back button...).
            put(
                "screen@common.base_screen",
                buildJsonObject {
                    putJsonArray("size") { add("100%"); add("100%") }
                    putJsonObject("variables") {
                        put("\$screen_content", "$namespace.screen_content")
                    }
                },
            )

            put(
                "screen_content",
                buildJsonObject {
                    put("type", "panel")
                    putJsonArray("size") { add(project.canvas.width); add(project.canvas.height) }
                    put("anchor_from", "center")
                    put("anchor_to", "center")
                    put("controls", JsonArray(rootRefs))
                },
            )

            controls.forEach { (name, element) -> put(name, element) }
        }

        return json.encodeToString(JsonObject.serializer(), document)
    }

    private fun controlName(element: GuiElement): String =
        ExportUtil.cssClass(element.name).replace('-', '_').ifEmpty { element.id }

    private fun collectControls(
        element: GuiElement,
        project: GuiProject,
        namespace: String,
        out: MutableMap<String, JsonElement>,
    ) {
        element.children.forEach { collectControls(it, project, namespace, out) }

        val childRefs = element.children.map { child ->
            buildJsonObject {
                put("${controlName(child)}@$namespace.${controlName(child)}", buildJsonObject { })
            }
        }

        out[controlName(element)] = buildJsonObject {
            put("type", controlType(element.type))
            put("\$designer_type", element.type)
            // Rotation is a transform every element has, so it is written for
            // every element. It used to be emitted only in the custom-shape
            // branch, which meant a turned button survived a round trip through
            // the Bedrock pack facing the wrong way.
            element.props.int("rotation", 0).takeIf { it != 0 }?.let { put("\$designer_rotation", it) }
            putJsonArray("size") { add(element.bounds.width); add(element.bounds.height) }
            putJsonArray("offset") { add(element.bounds.x); add(element.bounds.y) }
            put("anchor_from", bedrockAnchor(element.anchor))
            put("anchor_to", bedrockAnchor(element.anchor))
            put("visible", element.visible)
            put("layer", 1)

            appendTypeSpecifics(this, element, project)

            if (childRefs.isNotEmpty()) {
                put("controls", JsonArray(childRefs))
            }
        }

        // Per-state skins become sibling controls the button's state bindings
        // can reference, mirroring how vanilla splits `default`/`hover`.
        element.stateOverrides.forEach { (state, overrides) ->
            if (overrides.isEmpty() || state == InteractionState.NORMAL) return@forEach
            out["${controlName(element)}_${state.name.lowercase()}"] = buildJsonObject {
                put("type", controlType(element.type))
                put("\$designer_state", state.name.lowercase())
                overrides.forEach { (key, value) -> put(key, primitive(value, project)) }
            }
        }
    }

    private fun appendTypeSpecifics(
        builder: kotlinx.serialization.json.JsonObjectBuilder,
        element: GuiElement,
        project: GuiProject,
    ) = with(builder) {
        val props = element.props

        (props["background"] as? ColorValue)?.let { color ->
            put("color", json.parseToJsonElement(ExportUtil.bedrockColor(color.argb)))
            put("alpha", ExportUtil.bedrockAlpha(color.argb))
        }

        when (element.type) {
            ElementCatalog.TEXT_LABEL -> {
                put("text", props.string("text"))
                put("font_type", props.string("font", "default"))
                (props["textColor"] as? ColorValue)?.let {
                    put("color", json.parseToJsonElement(ExportUtil.bedrockColor(it.argb)))
                }
                put("shadow", (props["shadow"] as? BoolValue)?.value ?: true)
                put("text_alignment", props.string("align", "left"))
            }

            ElementCatalog.BAR_HEADER -> {
                put("text", props.string("title"))
                put("text_alignment", props.string("align", "left"))
            }

            ElementCatalog.BUTTON_NORMAL,
            ElementCatalog.BUTTON_TOGGLE,
            ElementCatalog.BUTTON_TAB,
            ElementCatalog.BEDROCK_ACTION_BUTTON,
            -> {
                put("\$button_text", props.string("label"))
                putJsonArray("button_mappings") {
                    add(
                        buildJsonObject {
                            put("from_button_id", "button.menu_select")
                            put("to_button_id", "button.${ExportUtil.cssClass(element.name).replace('-', '_')}")
                            put("mapping_type", "pressed")
                        },
                    )
                }
                val action = props.string("action")
                if (action.isNotBlank()) put("\$designer_action", action)
            }

            ElementCatalog.BUTTON_ICON -> {
                textureRef(props["icon"], project)?.let { put("texture", it) }
                put("\$designer_action", props.string("action"))
            }

            ElementCatalog.IMAGE_PLACEHOLDER -> {
                textureRef(props["texture"], project)?.let { put("texture", it) }
                put("keep_ratio", (props["keepAspect"] as? BoolValue)?.value ?: true)
                put("alpha", (props["opacity"] as? FloatValue)?.value?.toDouble() ?: 1.0)
                val fit = props.string("fit", "contain")
                if (fit == "nine_slice") {
                    val texture = project.texture((props["texture"] as? TextureValue)?.assetId)
                    if (texture != null && texture.hasNineSlice) {
                        putJsonArray("nineslice_size") {
                            add(texture.nineSlice.left); add(texture.nineSlice.top)
                            add(texture.nineSlice.right); add(texture.nineSlice.bottom)
                        }
                        put("tiled", false)
                    }
                } else if (fit == "tile") {
                    put("tiled", true)
                }
            }

            ElementCatalog.IMAGE_ANIMATED -> {
                textureRef(props["texture"], project)?.let { put("texture", it) }
                put("keep_ratio", (props["keepAspect"] as? BoolValue)?.value ?: true)
                put("alpha", (props["opacity"] as? FloatValue)?.value?.toDouble() ?: 1.0)
                // Bedrock plays the animation from the texture's own .mcmeta
                // sidecar, which `NativeAssets` writes alongside the PNG. The
                // timing is repeated here so the JSON is self-describing to
                // anyone reading the pack.
                val texture = project.texture((props["texture"] as? TextureValue)?.assetId)
                if (texture != null && texture.isAnimated) {
                    put("\$designer_frames", texture.frameCount)
                    put("\$designer_frametime_ticks", texture.frameTimeTicks)
                }
            }

            ElementCatalog.SHAPE_CUSTOM -> {
                // No JSON-UI primitive draws a polygon, so the export carries
                // the shape's own description alongside a coloured box. The
                // `$designer_` keys are inert to the game and are what lets a
                // re-import rebuild the real shape.
                put("\$designer_shape", props.string("shape", "rectangle"))
                put("\$designer_sides", (props["sides"] as? IntValue)?.value ?: 6)
                put("alpha", (props["opacity"] as? FloatValue)?.value?.toDouble() ?: 1.0)
                (props["fillColor"] as? ColorValue)?.let {
                    put("color", json.parseToJsonElement(ExportUtil.bedrockColor(it.argb)))
                }
            }

            ElementCatalog.CUSTOM_ELEMENT -> {
                put("\$designer_custom_type", props.string("customType", "custom_widget"))
                put("\$designer_export_as", props.string("exportAs", "panel"))
                props.stringList("attributes")
                    .mapNotNull { entry ->
                        // `key=value` per line, as the inspector describes it;
                        // anything without a `=` has no key to write it under.
                        val key = entry.substringBefore('=', "").trim()
                        val value = entry.substringAfter('=', "").trim()
                        if (key.isEmpty()) null else key to value
                    }
                    .forEach { (key, value) -> put(key, value) }
                textureRef(props["texture"], project)?.let { put("texture", it) }
                put("alpha", (props["opacity"] as? FloatValue)?.value?.toDouble() ?: 1.0)
            }

            ElementCatalog.INPUT_TEXTBOX, ElementCatalog.INPUT_SEARCH -> {
                put("place_holder_text", props.string("placeholder"))
                put("max_length", (props["maxLength"] as? IntValue)?.value ?: 32)
                put("text_edit_box_grid_collection_name", "designer_text")
                put("\$keyboard_type", props.string("keyboardType", "text"))
            }

            ElementCatalog.INPUT_SLIDER -> {
                put("\$slider_name", props.string("label"))
                put("slider_steps", ((props["maxValue"] as? FloatValue)?.value ?: 100f).toInt())
                put("\$default_slider_value", (props["value"] as? FloatValue)?.value?.toDouble() ?: 0.5)
            }

            ElementCatalog.INPUT_CHECKBOX -> {
                put("\$toggle_name", props.string("label"))
                put("\$toggle_state", (props["checked"] as? BoolValue)?.value ?: false)
            }

            ElementCatalog.INPUT_DROPDOWN -> {
                put("\$dropdown_name", props.string("placeholder"))
                putJsonArray("\$dropdown_content") {
                    (props["items"] as? ListValue)?.values?.forEach { add(it.asText()) }
                }
                put("\$full_screen_picker", (props["fullScreenPicker"] as? BoolValue)?.value ?: true)
            }

            ElementCatalog.CONTAINER_SCROLL -> {
                put("scroll_speed", 4)
                put("always_handle_scrolling", true)
                put("\$scroll_direction", props.string("direction", "vertical"))
                put("\$momentum", (props["momentum"] as? BoolValue)?.value ?: true)
            }

            ElementCatalog.PROGRESS_BAR -> {
                put("\$progress", (props["progress"] as? FloatValue)?.value?.toDouble() ?: 0.0)
                (props["fillColor"] as? ColorValue)?.let {
                    put("\$fill_color", json.parseToJsonElement(ExportUtil.bedrockColor(it.argb)))
                }
                put("\$fill_direction", props.string("direction", "right"))
            }

            ElementCatalog.BEDROCK_TOUCHPAD -> {
                put("\$touch_layout", props.string("layout", "dpad"))
                put("alpha", (props["opacity"] as? FloatValue)?.value?.toDouble() ?: 0.75)
                put("\$dead_zone", (props["deadZone"] as? IntValue)?.value ?: 12)
                put("\$haptic", (props["hapticFeedback"] as? BoolValue)?.value ?: true)
            }

            ElementCatalog.SLOT_INVENTORY -> {
                put("renderer", "inventory_item_renderer")
                put("\$slot_index", (props["slotIndex"] as? IntValue)?.value ?: 0)
            }

            ElementCatalog.STRIP_HOTBAR -> {
                put("\$slot_count", (props["slots"] as? IntValue)?.value ?: 9)
                put("\$selected_slot", (props["selectedIndex"] as? IntValue)?.value ?: 0)
            }
        }

        textureRef(props["texture"], project)?.let { if (element.type != ElementCatalog.IMAGE_PLACEHOLDER) put("texture", it) }
    }

    private fun textureRef(value: com.mcguidesigner.core.model.PropValue?, project: GuiProject): String? {
        val assetId = (value as? TextureValue)?.assetId ?: return null
        val texture = project.texture(assetId) ?: return null
        return "textures/ui/${texture.exportFileName().substringBeforeLast('.')}"
    }

    private fun primitive(value: com.mcguidesigner.core.model.PropValue, project: GuiProject): JsonElement =
        when (value) {
            is StringValue -> JsonPrimitive(value.value)
            is IntValue -> JsonPrimitive(value.value)
            is FloatValue -> JsonPrimitive(value.value)
            is BoolValue -> JsonPrimitive(value.value)
            is EnumValue -> JsonPrimitive(value.value)
            is ColorValue -> json.parseToJsonElement(ExportUtil.bedrockColor(value.argb))
            is TextureValue -> JsonPrimitive(textureRef(value, project) ?: "")
            is ListValue -> JsonArray(value.values.map { JsonPrimitive(it.asText()) })
        }

    private fun bedrockAnchor(anchor: com.mcguidesigner.core.model.Anchor): String = when (anchor) {
        com.mcguidesigner.core.model.Anchor.TOP_LEFT -> "top_left"
        com.mcguidesigner.core.model.Anchor.TOP_CENTER -> "top_middle"
        com.mcguidesigner.core.model.Anchor.TOP_RIGHT -> "top_right"
        com.mcguidesigner.core.model.Anchor.CENTER_LEFT -> "left_middle"
        com.mcguidesigner.core.model.Anchor.CENTER -> "center"
        com.mcguidesigner.core.model.Anchor.CENTER_RIGHT -> "right_middle"
        com.mcguidesigner.core.model.Anchor.BOTTOM_LEFT -> "bottom_left"
        com.mcguidesigner.core.model.Anchor.BOTTOM_CENTER -> "bottom_middle"
        com.mcguidesigner.core.model.Anchor.BOTTOM_RIGHT -> "bottom_right"
    }

    // -- texts/en_US.lang --------------------------------------------------

    private fun langFile(project: GuiProject, namespace: String, screenId: String): String = buildString {
        appendLine("## Generated by ${Branding.NAME}")
        appendLine("$namespace.$screenId.title=${project.name}")
        project.elements.walkAll().forEach { element ->
            listOf("label", "title", "text", "placeholder").forEach { key ->
                val value = element.props.string(key)
                if (value.isNotBlank()) {
                    val safe = value.replace("\n", " ")
                    appendLine("$namespace.${controlName(element)}.$key=$safe")
                }
            }
        }
    }

    private fun readme(project: GuiProject, namespace: String, screenId: String): String = """
        # ${project.name} - Bedrock Edition export

        Generated by **${Branding.NAME}**.

        ## Folder layout

        | Path | Purpose |
        | --- | --- |
        | `manifest.json` | Resource pack manifest with freshly generated UUIDs. |
        | `ui/_ui_defs.json` | Registers `ui/$screenId.json` with the game. |
        | `ui/_global_variables.json` | Canvas and safe-area values shared by the pack. |
        | `ui/$screenId.json` | The screen itself, namespace `$namespace`. |
        | `texts/en_US.lang` | Strings for every label in the screen. |
        | `textures/ui/` | Every texture you imported into the project. |

        ## Installing

        1. Copy this whole folder into
           `com.mojang/development_resource_packs/`.
        2. Enable it in the world's Resource Packs settings.
        3. Reload with `/reload` or by re-entering the world.

        On Windows the `com.mojang` folder lives under
        `%LOCALAPPDATA%/Packages/Microsoft.MinecraftUWP_8wekyb3d8bbwe/LocalState/games/`.

        ## Notes on the generated JSON

        * Positions use `anchor_from` / `anchor_to` / `offset` so the layout
          scales with the player's chosen GUI size.
        * Keys beginning with `${'$'}designer_` are metadata for round-tripping and
          are ignored by the game.
        * Widgets without a native JSON-UI equivalent (inventory slots, the
          touchpad) are emitted as `panel`/`custom` controls carrying their
          designer type, ready to be bound to your own renderers.

        ## Canvas

        Authored at ${project.canvas.width}x${project.canvas.height} for
        ${project.canvas.targetForm.displayName.lowercase()} layouts.
    """.trimIndent()
}
