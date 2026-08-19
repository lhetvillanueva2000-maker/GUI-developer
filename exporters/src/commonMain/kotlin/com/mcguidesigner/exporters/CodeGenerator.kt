package com.mcguidesigner.exporters

import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.model.BoolValue
import com.mcguidesigner.core.model.ColorValue
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.FloatValue
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.IntValue
import com.mcguidesigner.core.model.TextureAsset
import com.mcguidesigner.core.model.TextureValue
import com.mcguidesigner.core.model.bool
import com.mcguidesigner.core.model.color
import com.mcguidesigner.core.model.float
import com.mcguidesigner.core.model.string
import com.mcguidesigner.core.model.walkAll
import com.mcguidesigner.core.serialization.ProjectSerializer
import com.mcguidesigner.core.util.Ids

/**
 * Languages the Code tab can render the current design into.
 *
 * The point of this tab is to turn a visual layout into something you can
 * paste somewhere else: a web mock-up, a Compose screen, a mod source file, or
 * the raw data another tool can read.
 */
enum class CodeTarget(
    val id: String,
    val displayName: String,
    val language: String,
    val fileExtension: String,
    val edition: Edition? = null,
    val description: String = "",
) {
    HTML_CSS(
        "html", "HTML + CSS (standalone page)", "html", "html",
        description = "Self-contained page with embedded textures. Opens in any browser.",
    ),
    CSS(
        "css", "CSS stylesheet only", "css", "css",
        description = "Class-per-element stylesheet you can drop into an existing page.",
    ),
    COMPOSE(
        "compose", "Kotlin (Compose Multiplatform)", "kotlin", "kt",
        description = "A @Composable that reproduces the layout with Box/offset positioning.",
    ),
    JAVA_SCREEN(
        "java", "Java (Minecraft Screen)", "java", "java", Edition.JAVA,
        description = "Screen subclass for a Java Edition mod.",
    ),
    BEDROCK_JSON(
        "bedrock", "Bedrock JSON UI", "json", "json", Edition.BEDROCK,
        description = "The ui/<screen>.json the Bedrock resource pack ships.",
    ),
    PROJECT_JSON(
        "project", "Project document (.mcgui)", "json", "mcgui",
        description = "The full editable project, exactly as saved to disk.",
    ),
    ;

    /** True when this target makes sense for a project of [projectEdition]. */
    fun appliesTo(projectEdition: Edition): Boolean = edition == null || edition == projectEdition
}

/** Generated source plus the file name it should be saved under. */
data class GeneratedCode(
    val target: CodeTarget,
    val fileName: String,
    val source: String,
) {
    val lineCount: Int get() = source.count { it == '\n' } + 1
}

/**
 * Turns a design into source code.
 *
 * Every generator works from absolute canvas-space bounds so the output is
 * flat and readable rather than a nest of relative offsets - the shapes people
 * actually want to copy out of a design tool.
 */
object CodeGenerator {

    fun generate(project: GuiProject, target: CodeTarget): GeneratedCode {
        val base = Ids.slug(project.name)
        return when (target) {
            CodeTarget.HTML_CSS -> GeneratedCode(target, "$base.html", html(project))
            CodeTarget.CSS -> GeneratedCode(target, "$base.css", css(project, includeSelectorsOnly = true))
            CodeTarget.COMPOSE -> GeneratedCode(target, "${composeFunctionName(project)}.kt", compose(project))
            CodeTarget.JAVA_SCREEN -> {
                val className = JavaEditionExporter.screenClassName(project.name)
                GeneratedCode(
                    target, "$className.java",
                    JavaEditionExporter.screenSource(
                        project,
                        project.meta.namespace.ifBlank { "mcgui" },
                        project.meta.screenId.ifBlank { base },
                        className,
                    ),
                )
            }

            CodeTarget.BEDROCK_JSON -> {
                val namespace = "${project.meta.namespace.ifBlank { "mcgui" }}_${project.meta.screenId.ifBlank { base }}"
                GeneratedCode(target, "${project.meta.screenId.ifBlank { base }}.json", BedrockEditionExporter.screenJson(project, namespace))
            }

            CodeTarget.PROJECT_JSON -> GeneratedCode(target, "$base.mcgui", ProjectSerializer.encode(project))
        }
    }

    fun targetsFor(edition: Edition): List<CodeTarget> = CodeTarget.entries.filter { it.appliesTo(edition) }

    // -- HTML --------------------------------------------------------------

    private fun html(project: GuiProject): String {
        val bounds = project.absoluteBounds()
        val flat = project.elements.walkAll().toList()
        val scale = project.canvas.guiScale.coerceAtLeast(1)

        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"en\">")
            appendLine("<head>")
            appendLine("  <meta charset=\"utf-8\">")
            appendLine("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            appendLine("  <title>${ExportUtil.escape(project.name)}</title>")
            appendLine("  <style>")
            append(css(project, includeSelectorsOnly = false).prependIndent("    "))
            appendLine()
            appendLine("  </style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("  <!--")
            appendLine("    Generated by Minecraft GUI Designer from '${project.name}'.")
            appendLine("    Edition: ${project.edition.displayName} | Canvas: ${project.canvas.width}x${project.canvas.height}")
            appendLine("    The canvas is scaled ${scale}x with image-rendering: pixelated to keep the art crisp.")
            appendLine("  -->")
            appendLine("  <div class=\"mcgui-stage\">")
            appendLine("    <div class=\"mcgui-canvas\">")
            flat.filter { it.visible }.forEach { element ->
                val rect = bounds[element.id] ?: element.bounds
                appendHtmlElement(this, element, rect, project, indent = 3)
            }
            appendLine("    </div>")
            appendLine("  </div>")
            appendLine("</body>")
            appendLine("</html>")
        }
    }

    private fun appendHtmlElement(
        sb: StringBuilder,
        element: GuiElement,
        rect: IntRect,
        project: GuiProject,
        indent: Int,
    ) {
        val pad = "  ".repeat(indent)
        val cls = elementClass(element)
        val typeClass = "mcgui-${element.type.replace('.', '-')}"
        val label = displayText(element)

        when (element.type) {
            ElementCatalog.IMAGE_PLACEHOLDER -> {
                val texture = project.texture((element.props["texture"] as? TextureValue)?.assetId)
                if (texture != null) {
                    sb.appendLine(
                        "$pad<img class=\"mcgui-el $typeClass $cls\" alt=\"${ExportUtil.escape(element.name)}\" " +
                            "src=\"${dataUri(texture)}\">",
                    )
                } else {
                    sb.appendLine("$pad<div class=\"mcgui-el $typeClass $cls\"></div>")
                }
            }

            ElementCatalog.INPUT_TEXTBOX, ElementCatalog.INPUT_SEARCH -> {
                val placeholder = ExportUtil.escape(element.props.string("placeholder"))
                val value = ExportUtil.escape(element.props.string("value"))
                sb.appendLine(
                    "$pad<input class=\"mcgui-el $typeClass $cls\" type=\"text\" " +
                        "placeholder=\"$placeholder\" value=\"$value\">",
                )
            }

            ElementCatalog.PROGRESS_BAR -> {
                val progress = (element.props.float("progress", 0f) * 100).toInt()
                sb.appendLine("$pad<div class=\"mcgui-el $typeClass $cls\">")
                sb.appendLine("$pad  <span class=\"mcgui-fill\" style=\"width: $progress%\"></span>")
                sb.appendLine("$pad</div>")
            }

            else -> {
                if (label.isBlank()) {
                    sb.appendLine("$pad<div class=\"mcgui-el $typeClass $cls\"></div>")
                } else {
                    sb.appendLine(
                        "$pad<div class=\"mcgui-el $typeClass $cls\">" +
                            "<span class=\"mcgui-text\">${ExportUtil.escape(label).replace("\\n", "<br>")}</span></div>",
                    )
                }
            }
        }
    }

    // -- CSS ---------------------------------------------------------------

    /**
     * Builds the stylesheet.  Colours, borders and per-state rules all come
     * from the element's own properties, so the CSS is a faithful translation
     * of the design rather than a generic skeleton.
     */
    private fun css(project: GuiProject, includeSelectorsOnly: Boolean): String {
        val bounds = project.absoluteBounds()
        val scale = project.canvas.guiScale.coerceAtLeast(1)

        return buildString {
            appendLine("/* Generated by Minecraft GUI Designer - ${project.name} (${project.edition.displayName}) */")
            appendLine()
            appendLine(":root {")
            appendLine("  --mcgui-scale: $scale;")
            appendLine("  --mcgui-canvas-w: ${project.canvas.width}px;")
            appendLine("  --mcgui-canvas-h: ${project.canvas.height}px;")
            appendLine("  --mcgui-backdrop: ${ExportUtil.cssRgba(project.canvas.backdropColor)};")
            appendLine("}")
            appendLine()

            if (!includeSelectorsOnly) {
                appendLine("* { box-sizing: border-box; }")
                appendLine()
                appendLine("body {")
                appendLine("  margin: 0;")
                appendLine("  min-height: 100vh;")
                appendLine("  display: grid;")
                appendLine("  place-items: center;")
                appendLine("  background: #1b1b1f;")
                appendLine("  font-family: 'Minecraft', 'Courier New', monospace;")
                appendLine("}")
                appendLine()
            }

            appendLine(".mcgui-stage {")
            appendLine("  transform: scale(var(--mcgui-scale));")
            appendLine("  transform-origin: center center;")
            appendLine("  image-rendering: pixelated;")
            appendLine("}")
            appendLine()
            appendLine(".mcgui-canvas {")
            appendLine("  position: relative;")
            appendLine("  width: var(--mcgui-canvas-w);")
            appendLine("  height: var(--mcgui-canvas-h);")
            appendLine("  background: var(--mcgui-backdrop);")
            appendLine("  overflow: hidden;")
            appendLine("}")
            appendLine()
            appendLine(".mcgui-el {")
            appendLine("  position: absolute;")
            appendLine("  margin: 0;")
            appendLine("  padding: 0;")
            appendLine("  image-rendering: pixelated;")
            appendLine("  font-size: 8px;")
            appendLine("  line-height: 1.25;")
            appendLine("  display: flex;")
            appendLine("  align-items: center;")
            appendLine("}")
            appendLine()
            appendLine(".mcgui-text { width: 100%; padding: 0 2px; }")
            appendLine(".mcgui-fill { display: block; height: 100%; background: #56b84b; }")
            appendLine()

            project.elements.walkAll().filter { it.visible }.forEach { element ->
                val rect = bounds[element.id] ?: element.bounds
                appendElementRule(this, element, rect, project)
            }
        }
    }

    private fun appendElementRule(sb: StringBuilder, element: GuiElement, rect: IntRect, project: GuiProject) {
        val selector = ".${elementClass(element)}"
        sb.appendLine("/* ${element.name} - ${element.type} */")
        sb.appendLine("$selector {")
        sb.appendLine("  left: ${rect.x}px;")
        sb.appendLine("  top: ${rect.y}px;")
        sb.appendLine("  width: ${rect.width}px;")
        sb.appendLine("  height: ${rect.height}px;")
        sb.appendLine("  z-index: ${zIndexOf(element, project)};")

        element.props["background"]?.let { value ->
            if (value is ColorValue) sb.appendLine("  background-color: ${ExportUtil.cssRgba(value.argb)};")
        }
        element.props["textColor"]?.let { value ->
            if (value is ColorValue) sb.appendLine("  color: ${ExportUtil.cssRgba(value.argb)};")
        }
        element.props["borderColor"]?.let { value ->
            if (value is ColorValue) {
                val width = (element.props["borderWidth"] as? IntValue)?.value ?: 1
                sb.appendLine("  border: ${width}px solid ${ExportUtil.cssRgba(value.argb)};")
            }
        }
        if (element.props.bool("shadow", false)) {
            val offset = (element.props["shadowOffset"] as? IntValue)?.value ?: 3
            val opacity = element.props.float("shadowOpacity", 0.45f)
            sb.appendLine("  box-shadow: ${offset}px ${offset}px 0 rgba(0, 0, 0, $opacity);")
        }
        when (element.props.string("corner", "square")) {
            "rounded" -> sb.appendLine("  border-radius: 4px;")
            "beveled" -> sb.appendLine("  border-radius: 1px;")
        }
        when (element.props.string("align", "")) {
            "center" -> sb.appendLine("  text-align: center;")
            "right" -> sb.appendLine("  text-align: right;")
            "left" -> sb.appendLine("  text-align: left;")
        }
        element.props.float("opacity", 1f).let { if (it < 1f) sb.appendLine("  opacity: $it;") }

        val texture = project.texture((element.props["texture"] as? TextureValue)?.assetId)
        if (texture != null && element.type != ElementCatalog.IMAGE_PLACEHOLDER) {
            sb.appendLine("  background-image: url(\"${dataUri(texture)}\");")
            sb.appendLine("  background-size: 100% 100%;")
        }
        sb.appendLine("}")

        // Interactive states become real CSS pseudo-classes so the exported
        // page behaves like the preview does.
        element.stateOverrides.forEach { (state, overrides) ->
            val pseudo = when (state) {
                com.mcguidesigner.core.model.InteractionState.HOVER -> ":hover"
                com.mcguidesigner.core.model.InteractionState.PRESSED -> ":active"
                com.mcguidesigner.core.model.InteractionState.FOCUSED -> ":focus"
                com.mcguidesigner.core.model.InteractionState.DISABLED -> "[disabled], $selector.is-disabled"
                com.mcguidesigner.core.model.InteractionState.NORMAL -> return@forEach
            }
            val body = buildString {
                overrides.forEach { (key, value) ->
                    when {
                        key == "background" && value is ColorValue ->
                            appendLine("  background-color: ${ExportUtil.cssRgba(value.argb)};")

                        key == "textColor" && value is ColorValue ->
                            appendLine("  color: ${ExportUtil.cssRgba(value.argb)};")

                        key == "borderColor" && value is ColorValue ->
                            appendLine("  border-color: ${ExportUtil.cssRgba(value.argb)};")
                    }
                }
            }
            if (body.isNotBlank()) {
                sb.appendLine("$selector$pseudo {")
                sb.append(body)
                sb.appendLine("}")
            }
        }
        sb.appendLine()
    }

    private fun zIndexOf(element: GuiElement, project: GuiProject): Int =
        project.elements.walkAll().indexOfFirst { it.id == element.id } + 1

    private fun elementClass(element: GuiElement): String =
        "mcgui-${ExportUtil.cssClass(element.name)}-${element.id.takeLast(4)}"

    private fun dataUri(texture: TextureAsset): String {
        val mime = when (texture.format.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/png"
        }
        return "data:$mime;base64,${texture.dataBase64}"
    }

    // -- Compose -----------------------------------------------------------

    /** Composable name, avoiding a doubled `Screen` suffix. */
    private fun composeFunctionName(project: GuiProject): String =
        JavaEditionExporter.screenClassName(project.name)

    private fun compose(project: GuiProject): String {
        val bounds = project.absoluteBounds()
        val flat = project.elements.walkAll().filter { it.visible }.toList()
        val functionName = composeFunctionName(project)

        return buildString {
            appendLine("package com.example.mcgui")
            appendLine()
            appendLine("import androidx.compose.foundation.background")
            appendLine("import androidx.compose.foundation.border")
            appendLine("import androidx.compose.foundation.layout.Box")
            appendLine("import androidx.compose.foundation.layout.offset")
            appendLine("import androidx.compose.foundation.layout.size")
            appendLine("import androidx.compose.material3.Text")
            appendLine("import androidx.compose.runtime.Composable")
            appendLine("import androidx.compose.ui.Alignment")
            appendLine("import androidx.compose.ui.Modifier")
            appendLine("import androidx.compose.ui.graphics.Color")
            appendLine("import androidx.compose.ui.unit.dp")
            appendLine("import androidx.compose.ui.unit.sp")
            appendLine()
            appendLine("/**")
            appendLine(" * Generated by Minecraft GUI Designer from '${project.name}'.")
            appendLine(" *")
            appendLine(" * Canvas: ${project.canvas.width}x${project.canvas.height} dp at 1x.")
            appendLine(" * Positions are absolute offsets from the canvas origin.")
            appendLine(" */")
            appendLine("@Composable")
            appendLine("fun $functionName(modifier: Modifier = Modifier) {")
            appendLine("    Box(")
            appendLine("        modifier = modifier")
            appendLine("            .size(${project.canvas.width}.dp, ${project.canvas.height}.dp)")
            appendLine("            .background(${colorLiteral(project.canvas.backdropColor)}),")
            appendLine("    ) {")
            flat.forEach { element ->
                val rect = bounds[element.id] ?: element.bounds
                val background = element.props.color("background", 0x00000000)
                val text = displayText(element)
                appendLine("        // ${element.name} (${element.type})")
                appendLine("        Box(")
                appendLine("            modifier = Modifier")
                appendLine("                .offset(x = ${rect.x}.dp, y = ${rect.y}.dp)")
                appendLine("                .size(${rect.width}.dp, ${rect.height}.dp)")
                if (background != 0x00000000L) {
                    appendLine("                .background(${colorLiteral(background)})")
                }
                val border = element.props["borderColor"] as? ColorValue
                if (border != null) {
                    val width = (element.props["borderWidth"] as? IntValue)?.value ?: 1
                    appendLine("                .border(${width}.dp, ${colorLiteral(border.argb)})")
                }
                appendLine("                ,")
                appendLine("            contentAlignment = Alignment.${composeAlignment(element)},")
                appendLine("        ) {")
                if (text.isNotBlank()) {
                    appendLine("            Text(")
                    appendLine("                text = \"${ExportUtil.escape(text)}\",")
                    appendLine("                color = ${colorLiteral(element.props.color("textColor", 0xFFE0E0E0))},")
                    appendLine("                fontSize = ${(8 * element.props.float("scale", 1f)).toInt().coerceAtLeast(6)}.sp,")
                    appendLine("            )")
                }
                appendLine("        }")
                appendLine()
            }
            appendLine("    }")
            appendLine("}")
        }
    }

    private fun composeAlignment(element: GuiElement): String = when (element.props.string("align", "center")) {
        "left" -> "CenterStart"
        "right" -> "CenterEnd"
        else -> "Center"
    }

    private fun colorLiteral(argb: Long): String =
        "Color(0x" + argb.toString(16).uppercase().padStart(8, '0') + ")"

    // -- Shared ------------------------------------------------------------

    /** The one string that best represents an element in a code preview. */
    private fun displayText(element: GuiElement): String = when (element.type) {
        ElementCatalog.TEXT_LABEL, ElementCatalog.PANEL_TOOLTIP -> element.props.string("text")
        ElementCatalog.BAR_HEADER, ElementCatalog.PANEL_CHEST -> element.props.string("title")
        ElementCatalog.BUTTON_TOGGLE -> {
            val on = element.props["value"].let { it is BoolValue && it.value }
            val suffix = if (on) element.props.string("onLabel") else element.props.string("offLabel")
            listOf(element.props.string("label"), suffix).filter { it.isNotBlank() }.joinToString(": ")
        }

        ElementCatalog.INPUT_SLIDER -> {
            val label = element.props.string("label")
            if (!element.props.bool("showValue", true)) {
                label
            } else {
                val min = element.props.float("minValue", 0f)
                val max = element.props.float("maxValue", 100f)
                val value = min + (max - min) * element.props.float("value", 0.5f)
                "$label: ${value.toInt()}${element.props.string("suffix")}"
            }
        }

        ElementCatalog.INPUT_CHECKBOX -> element.props.string("label")
        else -> element.props.string("label")
    }
}
