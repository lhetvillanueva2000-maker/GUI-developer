package com.mcguidesigner.exporters

import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.catalog.PropType
import com.mcguidesigner.core.model.ColorValue
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.PropValue
import com.mcguidesigner.core.model.StringValue
import com.mcguidesigner.core.model.TextureValue
import com.mcguidesigner.core.model.string
import com.mcguidesigner.core.model.walkAll
import com.mcguidesigner.core.util.Ids
import com.mcguidesigner.core.validation.ProjectValidator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Produces a Java Edition resource-pack shaped bundle.
 *
 * Vanilla Java has no data-driven screen format, so a faithful export is
 * necessarily two halves: the *assets* a pack can ship as-is (textures, lang
 * entries, `pack.mcmeta`), and a *generated `Screen` subclass* that lays the
 * widgets out exactly as designed.  The layout is additionally emitted as JSON
 * so mod loaders, KubeJS scripts or custom runtimes can consume it directly
 * instead of the generated Java.
 */
object JavaEditionExporter {

    /** Pack format for the Minecraft versions this layout schema targets. */
    const val DEFAULT_PACK_FORMAT = 34

    private val json = Json { prettyPrint = true; prettyPrintIndent = "  " }

    fun export(project: GuiProject, packFormat: Int = DEFAULT_PACK_FORMAT): ExportBundle {
        val report = ProjectValidator.validate(project, strict = true)
        val namespace = project.meta.namespace.ifBlank { "mcgui" }
        val screenId = project.meta.screenId.ifBlank { Ids.slug(project.name) }
        val root = "${screenId}_java_pack"
        val files = mutableListOf<ExportFile>()

        files += ExportFile.Text("$root/pack.mcmeta", packMcmeta(project, packFormat))
        files += ExportFile.Text("$root/assets/$namespace/gui/$screenId.json", layoutJson(project))
        files += ExportFile.Text("$root/assets/$namespace/lang/en_us.json", langJson(project, namespace, screenId))

        // The atlas source list is what makes these textures addressable as
        // `<namespace>:gui/<name>` sprites rather than raw files.
        files += ExportFile.Text(
            "$root/assets/$namespace/atlases/gui.json",
            NativeAssets.guiAtlasJson(namespace),
        )

        project.textures.forEach { texture ->
            files += ExportFile.Binary(
                "$root/assets/$namespace/textures/gui/${texture.exportFileName()}",
                texture.dataBase64,
            )
            // Nine-slice and animation live in a sidecar the game reads next
            // to the image; without it a stretched button skin smears its
            // corners and an imported GIF renders as one tall still.
            NativeAssets.mcmetaFor(texture)?.let { mcmeta ->
                files += ExportFile.Text(
                    "$root/assets/$namespace/textures/gui/${texture.mcmetaFileName()}",
                    mcmeta,
                )
            }
        }

        val className = screenClassName(project.name)
        files += ExportFile.Text(
            "$root/src/main/java/com/example/$namespace/client/gui/$className.java",
            screenSource(project, namespace, screenId, className),
        )
        files += ExportFile.Text("$root/README.md", readme(project, namespace, screenId, className))

        val warnings = report.issues.filterNot { it.code == com.mcguidesigner.core.validation.IssueCode.EMPTY_CANVAS } +
            crossEditionWarnings(project)

        return ExportBundle(ExportTarget.JAVA_RESOURCE_PACK, root, files, warnings)
    }

    /**
     * Class name for the generated screen.
     *
     * A project called "Sample Chest Screen" should produce
     * `SampleChestScreen`, not `SampleChestScreenScreen`, so an existing
     * `Screen` suffix is kept rather than doubled.
     */
    fun screenClassName(projectName: String): String {
        val base = ExportUtil.className(projectName).ifEmpty { "Custom" }
        return if (base.endsWith("Screen")) base else base + "Screen"
    }

    private fun crossEditionWarnings(project: GuiProject) =
        if (project.edition == Edition.JAVA) ProjectValidator.parityIssues(project) else emptyList()

    // -- pack.mcmeta -------------------------------------------------------

    private fun packMcmeta(project: GuiProject, packFormat: Int): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            putJsonObject("pack") {
                put("pack_format", packFormat)
                put(
                    "description",
                    project.meta.description.ifBlank { "${project.name} - built with Minecraft GUI Designer" },
                )
            }
        },
    )

    // -- Layout document ---------------------------------------------------

    /**
     * The portable description of the screen.  Everything the generated Java
     * does is derivable from this file, which is what makes the export useful
     * to runtimes other than the sample mod.
     */
    fun layoutJson(project: GuiProject): String = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("format", 1)
            put("edition", "java")
            put("generator", "minecraft-gui-designer")
            putJsonObject("screen") {
                put("id", project.meta.screenId)
                put("namespace", project.meta.namespace)
                put("title", project.name)
                put("width", project.canvas.width)
                put("height", project.canvas.height)
                put("gui_scale", project.canvas.guiScale)
                put("backdrop", project.canvas.backdrop.name.lowercase())
            }
            putJsonArray("textures") {
                project.textures.forEach { texture ->
                    add(
                        buildJsonObject {
                            put("id", texture.id)
                            put("name", texture.name)
                            put("path", "${project.meta.namespace}:gui/${texture.exportFileName()}")
                            put("width", texture.width)
                            put("height", texture.height)
                            if (texture.hasNineSlice) {
                                putJsonObject("nine_slice") {
                                    put("left", texture.nineSlice.left)
                                    put("top", texture.nineSlice.top)
                                    put("right", texture.nineSlice.right)
                                    put("bottom", texture.nineSlice.bottom)
                                }
                            }
                        },
                    )
                }
            }
            put("elements", JsonArray(project.elements.map { elementJson(it, project) }))
        },
    )

    private fun elementJson(element: GuiElement, project: GuiProject): JsonElement = buildJsonObject {
        put("id", element.id)
        put("type", element.type)
        put("name", element.name)
        put("x", element.bounds.x)
        put("y", element.bounds.y)
        put("width", element.bounds.width)
        put("height", element.bounds.height)
        put("anchor", element.anchor.name.lowercase())
        put("visible", element.visible)
        put("properties", propsJson(element.props, element, project))
        if (element.stateOverrides.isNotEmpty()) {
            putJsonObject("states") {
                element.stateOverrides.forEach { (state, overrides) ->
                    put(state.name.lowercase(), propsJson(overrides, element, project))
                }
            }
        }
        if (element.children.isNotEmpty()) {
            put("children", JsonArray(element.children.map { elementJson(it, project) }))
        }
    }

    private fun propsJson(props: Map<String, PropValue>, element: GuiElement, project: GuiProject): JsonObject {
        val definition = ElementCatalog[element.type]
        return buildJsonObject {
            props.forEach { (key, value) ->
                val spec = definition?.property(key, Edition.JAVA) ?: return@forEach
                when (value) {
                    is com.mcguidesigner.core.model.StringValue -> put(key, value.value)
                    is com.mcguidesigner.core.model.IntValue -> put(key, value.value)
                    is com.mcguidesigner.core.model.FloatValue -> put(key, value.value)
                    is com.mcguidesigner.core.model.BoolValue -> put(key, value.value)
                    is com.mcguidesigner.core.model.EnumValue -> put(key, value.value)
                    is ColorValue -> put(key, ExportUtil.javaColorLiteral(value.argb))
                    is TextureValue -> {
                        val texture = project.texture(value.assetId)
                        if (texture == null) {
                            put(key, JsonNull)
                        } else {
                            put(key, "${project.meta.namespace}:gui/${texture.exportFileName()}")
                        }
                    }

                    is com.mcguidesigner.core.model.ListValue ->
                        put(key, JsonArray(value.values.map { JsonPrimitive(it.asText()) }))
                }
                if (spec.type == PropType.COLOR && value is ColorValue) {
                    put("${key}_hex", value.toHex())
                }
            }
        }
    }

    // -- Language file -----------------------------------------------------

    private fun langJson(project: GuiProject, namespace: String, screenId: String): String {
        val entries = linkedMapOf<String, String>()
        entries["gui.$namespace.$screenId.title"] = project.name
        project.elements.walkAll().forEach { element ->
            val definition = ElementCatalog[element.type] ?: return@forEach
            val textKeys = listOf("label", "title", "text", "placeholder", "tooltip")
            textKeys.forEach { key ->
                if (definition.property(key, Edition.JAVA) == null) return@forEach
                val value = element.props.string(key)
                if (value.isNotBlank()) {
                    entries["gui.$namespace.$screenId.${ExportUtil.cssClass(element.name)}.$key"] = value
                }
            }
        }
        return json.encodeToString(
            JsonObject.serializer(),
            JsonObject(entries.mapValues { JsonPrimitive(it.value) }),
        )
    }

    // -- Generated screen source ------------------------------------------

    /**
     * Emits a compilable `Screen` subclass.  It targets the modern
     * `net.minecraft.client.gui` API surface and deliberately keeps every
     * position as an explicit integer so the file stays readable and hand
     * editable after generation.
     */
    fun screenSource(project: GuiProject, namespace: String, screenId: String, className: String): String {
        val flat = project.elements.walkAll().toList()
        val absolute = project.absoluteBounds()

        return buildString {
            appendLine("package com.example.$namespace.client.gui;")
            appendLine()
            appendLine("import net.minecraft.client.gui.GuiGraphics;")
            appendLine("import net.minecraft.client.gui.components.Button;")
            appendLine("import net.minecraft.client.gui.components.EditBox;")
            appendLine("import net.minecraft.client.gui.screens.Screen;")
            appendLine("import net.minecraft.network.chat.Component;")
            appendLine("import net.minecraft.resources.ResourceLocation;")
            appendLine()
            appendLine("/**")
            appendLine(" * Generated by Minecraft GUI Designer from '${project.name}'.")
            appendLine(" *")
            appendLine(" * Canvas: ${project.canvas.width}x${project.canvas.height} GUI pixels.")
            appendLine(" * Regenerating this file overwrites manual edits - subclass it instead.")
            appendLine(" */")
            appendLine("public class $className extends Screen {")
            appendLine()
            appendLine("    public static final int CANVAS_WIDTH = ${project.canvas.width};")
            appendLine("    public static final int CANVAS_HEIGHT = ${project.canvas.height};")
            project.textures.forEach { texture ->
                val constant = ExportUtil.identifier(texture.name).uppercase()
                appendLine(
                    "    public static final ResourceLocation TEX_$constant = " +
                        "ResourceLocation.fromNamespaceAndPath(\"$namespace\", \"textures/gui/${texture.exportFileName()}\");",
                )
            }
            appendLine()
            appendLine("    /** Left edge of the layout once centred in the window. */")
            appendLine("    private int originX;")
            appendLine("    /** Top edge of the layout once centred in the window. */")
            appendLine("    private int originY;")
            appendLine()
            appendLine("    public $className() {")
            appendLine("        super(Component.translatable(\"gui.$namespace.$screenId.title\"));")
            appendLine("    }")
            appendLine()
            appendLine("    @Override")
            appendLine("    protected void init() {")
            appendLine("        this.originX = (this.width - CANVAS_WIDTH) / 2;")
            appendLine("        this.originY = (this.height - CANVAS_HEIGHT) / 2;")
            appendLine()
            flat.filter { isWidget(it.type) }.forEach { element ->
                val rect = absolute[element.id] ?: element.bounds
                val variable = ExportUtil.identifier(element.name)
                when (element.type) {
                    ElementCatalog.BUTTON_NORMAL,
                    ElementCatalog.JAVA_RECT_BUTTON,
                    ElementCatalog.BUTTON_TOGGLE,
                    ElementCatalog.BUTTON_TAB,
                    ElementCatalog.BUTTON_ICON,
                    -> {
                        val label = element.props.string("label").ifBlank { element.name }
                        appendLine("        // ${element.name} (${element.type})")
                        appendLine("        Button $variable = Button")
                        appendLine("            .builder(Component.literal(\"${ExportUtil.escape(label)}\"), button -> on${ExportUtil.className(element.name)}())")
                        appendLine("            .bounds(this.originX + ${rect.x}, this.originY + ${rect.y}, ${rect.width}, ${rect.height})")
                        appendLine("            .build();")
                        if (!element.props.getOrElse("enabled") { StringValue("true") }.asText().toBoolean()) {
                            appendLine("        $variable.active = false;")
                        }
                        appendLine("        this.addRenderableWidget($variable);")
                        appendLine()
                    }

                    ElementCatalog.INPUT_TEXTBOX, ElementCatalog.INPUT_SEARCH -> {
                        val placeholder = element.props.string("placeholder")
                        appendLine("        // ${element.name} (${element.type})")
                        appendLine(
                            "        EditBox $variable = new EditBox(this.font, this.originX + ${rect.x}, " +
                                "this.originY + ${rect.y}, ${rect.width}, ${rect.height}, " +
                                "Component.literal(\"${ExportUtil.escape(placeholder)}\"));",
                        )
                        appendLine("        $variable.setMaxLength(${element.props.getOrElse("maxLength") { StringValue("32") }.asText()});")
                        appendLine("        this.addRenderableWidget($variable);")
                        appendLine()
                    }
                }
            }
            appendLine("        super.init();")
            appendLine("    }")
            appendLine()
            appendLine("    @Override")
            appendLine("    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {")
            appendLine("        this.renderBackground(graphics, mouseX, mouseY, partialTick);")
            appendLine()
            flat.filterNot { isWidget(it.type) }.filter { it.visible }.forEach { element ->
                val rect = absolute[element.id] ?: element.bounds
                appendLine("        // ${element.name} (${element.type})")
                when (element.type) {
                    ElementCatalog.TEXT_LABEL, ElementCatalog.BAR_HEADER -> {
                        val key = if (element.type == ElementCatalog.BAR_HEADER) "title" else "text"
                        val value = element.props.string(key).replace("\n", " ")
                        val color = (element.props["textColor"] as? ColorValue)?.argb ?: 0xFFE0E0E0
                        appendLine(
                            "        graphics.drawString(this.font, \"${ExportUtil.escape(value)}\", " +
                                "this.originX + ${rect.x}, this.originY + ${rect.y}, ${ExportUtil.javaColorLiteral(color)}, " +
                                "${element.props.getOrElse("shadow") { StringValue("true") }.asText()});",
                        )
                    }

                    ElementCatalog.IMAGE_PLACEHOLDER -> {
                        val texture = project.texture((element.props["texture"] as? TextureValue)?.assetId)
                        if (texture != null) {
                            val constant = ExportUtil.identifier(texture.name).uppercase()
                            appendLine(
                                "        graphics.blit(TEX_$constant, this.originX + ${rect.x}, this.originY + ${rect.y}, " +
                                    "0, 0, ${rect.width}, ${rect.height}, ${texture.width}, ${texture.height});",
                            )
                        } else {
                            appendLine("        // No texture assigned; nothing to blit.")
                        }
                    }

                    else -> {
                        val color = (element.props["background"] as? ColorValue)?.argb
                            ?: (element.props["color"] as? ColorValue)?.argb
                            ?: 0x80000000
                        appendLine(
                            "        graphics.fill(this.originX + ${rect.x}, this.originY + ${rect.y}, " +
                                "this.originX + ${rect.right}, this.originY + ${rect.bottom}, " +
                                "${ExportUtil.javaColorLiteral(color)});",
                        )
                    }
                }
            }
            appendLine()
            appendLine("        super.render(graphics, mouseX, mouseY, partialTick);")
            appendLine("    }")
            appendLine()
            flat.filter { isWidget(it.type) && it.type != ElementCatalog.INPUT_TEXTBOX && it.type != ElementCatalog.INPUT_SEARCH }
                .forEach { element ->
                    val action = element.props.string("action").ifBlank { ExportUtil.identifier(element.name) }
                    appendLine("    /** Handler for '${element.name}' (action id: $action). */")
                    appendLine("    protected void on${ExportUtil.className(element.name)}() {")
                    appendLine("        // TODO: implement '$action'.")
                    appendLine("    }")
                    appendLine()
                }
            appendLine("    @Override")
            appendLine("    public boolean isPauseScreen() {")
            appendLine("        return false;")
            appendLine("    }")
            appendLine("}")
        }
    }

    private fun isWidget(type: String) = type in setOf(
        ElementCatalog.BUTTON_NORMAL,
        ElementCatalog.BUTTON_TOGGLE,
        ElementCatalog.BUTTON_TAB,
        ElementCatalog.BUTTON_ICON,
        ElementCatalog.JAVA_RECT_BUTTON,
        ElementCatalog.INPUT_TEXTBOX,
        ElementCatalog.INPUT_SEARCH,
    )

    private fun readme(project: GuiProject, namespace: String, screenId: String, className: String): String = """
        # ${project.name} - Java Edition export

        Generated by **Minecraft GUI Designer**.

        ## What is in this folder

        | Path | Purpose |
        | --- | --- |
        | `pack.mcmeta` | Resource pack manifest. |
        | `assets/$namespace/gui/$screenId.json` | Portable layout description (the source of truth). |
        | `assets/$namespace/lang/en_us.json` | Translation keys for every label in the screen. |
        | `assets/$namespace/textures/gui/` | Every texture you imported into the project. |
        | `src/main/java/.../$className.java` | Ready-to-compile `Screen` subclass. |

        ## Using the resource pack half

        Zip the contents of this folder (the folder that contains `pack.mcmeta`,
        not the folder itself) and drop the zip into `.minecraft/resourcepacks`.
        The textures become available as `$namespace:textures/gui/<file>.png`.

        ## Using the generated screen

        1. Copy `$className.java` into your mod's source tree.
        2. Adjust the package statement if your mod does not use
           `com.example.$namespace`.
        3. Open it with `Minecraft.getInstance().setScreen(new $className());`

        The class centres the ${project.canvas.width}x${project.canvas.height}
        layout in the window, so it behaves correctly at every GUI scale.

        ## Regenerating

        Re-export from the designer at any time. The generated Java is
        overwritten wholesale, so keep custom logic in a subclass.
    """.trimIndent()
}
