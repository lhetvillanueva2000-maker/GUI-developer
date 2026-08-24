package com.mcguidesigner.exporters

import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.Branding
import com.mcguidesigner.core.model.BoolValue
import com.mcguidesigner.core.model.ColorValue
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.FloatValue
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.IntValue
import com.mcguidesigner.core.model.ShapeKind
import com.mcguidesigner.core.model.int
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
    /**
     * True when Minecraft itself parses this format.
     *
     * The distinction is the one that matters when choosing an export: a
     * `Screen` subclass is source you compile into a mod, whereas JSON UI is
     * read by the shipping game with nothing else involved.  The export
     * dialog groups on this so the difference is not something you have to
     * already know.
     */
    val readByMinecraft: Boolean = false,
    /**
     * Short label for a tab strip.
     *
     * Not derived from [language]: three of these targets are JSON, and a row
     * of tabs all reading "JSON" tells you nothing about which is which.
     */
    val tabLabel: String = language.uppercase(),
) {
    BEDROCK_JSON(
        "bedrock", "Bedrock JSON UI", "json", "json", Edition.BEDROCK,
        description = "The ui/<screen>.json the game itself reads. Drop it in a resource pack " +
            "and Bedrock draws this screen - no mod, no code.",
        readByMinecraft = true,
        tabLabel = "JSON UI",
    ),
    JAVA_GUI_DEFINITIONS(
        "java-native", "Java GUI definitions (.mcmeta + atlas)", "json", "jsonc", Edition.JAVA,
        description = "The vanilla sidecars that tell Java Edition how to scale and animate this " +
            "screen's images: gui.scaling, animation, and the GUI atlas source list.",
        readByMinecraft = true,
        tabLabel = "MCMETA",
    ),
    JAVA_SCREEN(
        "java", "Java (Minecraft Screen source)", "java", "java", Edition.JAVA,
        description = "Screen subclass for a Java Edition mod. Java draws its GUIs in code, so " +
            "this is the closest thing it has to a layout format.",
        tabLabel = "JAVA",
    ),
    HTML_CSS(
        "html", "HTML + CSS (standalone page)", "html", "html",
        description = "Self-contained page with embedded textures. Opens in any browser.",
    ),
    CSS(
        "css", "CSS stylesheet only", "css", "css",
        description = "Class-per-element stylesheet you can drop into an existing page.",
    ),
    SVG(
        "svg", "SVG vector drawing", "xml", "svg",
        description = "The layout as vector art, shapes included. Opens in any editor or browser.",
    ),
    COMPOSE(
        "compose", "Kotlin (Compose Multiplatform)", "kotlin", "kt",
        description = "A @Composable that reproduces the layout with Box/offset positioning.",
        tabLabel = "COMPOSE",
    ),
    REACT_JSX(
        "react", "React (JSX component)", "jsx", "jsx",
        description = "A function component with inline styles, positioned absolutely inside a " +
            "sized wrapper. Drops straight into a React app.",
        tabLabel = "JSX",
    ),
    SWIFTUI(
        "swiftui", "SwiftUI (View struct)", "swift", "swift",
        description = "A View backed by a ZStack, with each element offset from the top-left " +
            "corner rather than the centre SwiftUI would otherwise use.",
        tabLabel = "SWIFT",
    ),
    FLUTTER(
        "flutter", "Flutter (Dart widget)", "dart", "dart",
        description = "A StatelessWidget built from a Stack of Positioned children.",
        tabLabel = "DART",
    ),
    ANDROID_XML(
        "android-xml", "Android layout (XML)", "xml", "xml",
        description = "A ConstraintLayout-free FrameLayout using absolute margins, which is what " +
            "a fixed-canvas design maps onto without inventing constraints.",
        tabLabel = "XML",
    ),
    PROJECT_JSON(
        "project", "Project document (.mcgui)", "json", "mcgui",
        description = "The full editable project, exactly as saved to disk.",
        tabLabel = "MCGUI",
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
            CodeTarget.REACT_JSX -> GeneratedCode(target, "${pascal(project.name)}.jsx", reactJsx(project))
            CodeTarget.SWIFTUI -> GeneratedCode(target, "${pascal(project.name)}View.swift", swiftUi(project))
            CodeTarget.FLUTTER -> GeneratedCode(target, "${Ids.slug(project.name).replace('-', '_')}.dart", flutter(project))
            CodeTarget.ANDROID_XML -> GeneratedCode(target, "${Ids.slug(project.name).replace('-', '_')}.xml", androidXml(project))
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

            CodeTarget.JAVA_GUI_DEFINITIONS -> GeneratedCode(
                // .jsonc, not .json: the document is several files laid out
                // with `//` comments saying where each one goes, which plain
                // JSON has no room for.
                target, "$base-gui-definitions.jsonc", NativeAssets.javaDefinitionsDocument(project),
            )

            CodeTarget.SVG -> GeneratedCode(target, "$base.svg", svg(project))

            CodeTarget.PROJECT_JSON -> GeneratedCode(target, "$base.mcgui", ProjectSerializer.encode(project))
        }
    }

    fun targetsFor(edition: Edition): List<CodeTarget> = CodeTarget.entries.filter { it.appliesTo(edition) }

    /**
     * Targets Minecraft parses directly, for [edition].
     *
     * "What do I give the game?" is the first question anyone exporting a
     * screen has, so the dialog answers it separately from the rest.
     */
    fun nativeTargetsFor(edition: Edition): List<CodeTarget> =
        targetsFor(edition).filter { it.readByMinecraft }

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
            appendLine("    Generated by ${Branding.NAME} from '${project.name}'.")
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
            appendLine("/* Generated by ${Branding.NAME} - ${project.name} (${project.edition.displayName}) */")
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
            if (element.type == ElementCatalog.IMAGE_ANIMATED && texture.isAnimated) {
                appendAnimatedImageRules(sb, element, texture)
            } else {
                sb.appendLine("  background-size: 100% 100%;")
            }
        }
        if (element.type == ElementCatalog.SHAPE_CUSTOM) {
            appendShapeRules(sb, element)
        }
        // Every element, not only shapes. This lived inside the shape rules,
        // so a turned button exported square while the canvas showed it
        // turned - and CSS is the export people check first.
        element.props.int("rotation", 0).let {
            if (it != 0) sb.appendLine("  transform: rotate(${it}deg);")
        }
        sb.appendLine("}")

        // The keyframes have to sit outside the rule block.
        if (element.type == ElementCatalog.IMAGE_ANIMATED && texture?.isAnimated == true) {
            appendAnimationKeyframes(sb, element, texture)
        }

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

    /**
     * A custom shape as CSS.
     *
     * Ellipses and rounded rectangles have real CSS properties; everything else
     * becomes a `clip-path: polygon(...)`, built from the same 0..1 outline the
     * canvas draws, so the page and the editor cannot disagree about what a
     * shape looks like.
     */
    private fun appendShapeRules(sb: StringBuilder, element: GuiElement) {
        val kind = ShapeKind.fromId(element.props.string("shape", ShapeKind.RECTANGLE.id))
        val fillMode = element.props.string("fillMode", "solid")

        when (fillMode) {
            "none" -> sb.appendLine("  background-color: transparent;")
            "gradient" -> sb.appendLine(
                "  background-image: linear-gradient(${element.props.int("gradientAngle", 90)}deg, " +
                    "${ExportUtil.cssRgba(element.props.color("fillColor", 0xFF56B84B))}, " +
                    "${ExportUtil.cssRgba(element.props.color("gradientColor", 0xFF1E6F3A))});",
            )
            else -> sb.appendLine(
                "  background-color: ${ExportUtil.cssRgba(element.props.color("fillColor", 0xFF56B84B))};",
            )
        }

        val strokeWidth = element.props.int("strokeWidth", 1)
        when (kind) {
            ShapeKind.ELLIPSE -> sb.appendLine("  border-radius: 50%;")
            ShapeKind.ROUNDED_RECTANGLE ->
                sb.appendLine("  border-radius: ${element.props.int("cornerRadius", 6)}px;")

            else -> {
                val points = kind
                    .outline(element.props.int("sides", 6), element.props.float("innerRadius", 0.5f))
                    .joinToString(", ") { (fx, fy) -> "${percent(fx)} ${percent(fy)}" }
                if (points.isNotBlank()) sb.appendLine("  clip-path: polygon($points);")
            }
        }

        // A clip-path cuts the border off with everything else outside the
        // outline, so only the two shapes CSS can really outline get one.
        if (strokeWidth > 0 && !kind.isPolygonal) {
            sb.appendLine(
                "  border: ${strokeWidth}px solid " +
                    "${ExportUtil.cssRgba(element.props.color("strokeColor", 0xFF000000))};",
            )
            sb.appendLine("  box-sizing: border-box;")
        }

    }

    /**
     * Plays a frame strip with `steps()`, the same trick CSS sprite animations
     * have always used: the background jumps one frame height at a time rather
     * than sliding smoothly.
     *
     * Two details decide whether the frames land squarely or smear, and both
     * follow from `background-position` percentages being a fraction of
     * (container height - image height) rather than an absolute offset. For an
     * N-frame strip that difference is `H - N*H`, so `100%` is exactly the last
     * frame - which is why the keyframe ends at 100% and not at N*100%.
     *
     * And the step function has to be `jump-none`: plain `steps(N)` hands out
     * progress values of k/N, which land between frames, while `jump-none`
     * hands out k/(N-1) - exactly frame k, for every k.
     */
    private fun appendAnimatedImageRules(sb: StringBuilder, element: GuiElement, texture: TextureAsset) {
        val frames = texture.frameCount.coerceAtLeast(1)
        sb.appendLine("  background-size: 100% ${frames * 100}%;")
        sb.appendLine("  background-repeat: no-repeat;")
        sb.appendLine("  image-rendering: pixelated;")
        if (frames > 1) {
            val seconds = frames * element.props.int("frameTime", texture.frameTimeTicks)
                .coerceAtLeast(1) * TICK_SECONDS
            val direction = when (element.props.string("playback", "forward")) {
                "reverse" -> "reverse"
                "ping_pong" -> "alternate"
                else -> "normal"
            }
            val count = if (element.props.bool("loop", true)) "infinite" else "1"
            sb.appendLine(
                "  animation: ${elementClass(element)}-frames ${trim(seconds)}s " +
                    "steps($frames, jump-none) $direction $count;",
            )
        }
    }

    private fun appendAnimationKeyframes(sb: StringBuilder, element: GuiElement, texture: TextureAsset) {
        if (texture.frameCount <= 1) return
        sb.appendLine("@keyframes ${elementClass(element)}-frames {")
        sb.appendLine("  from { background-position: 0 0; }")
        // 100% is the bottom of the strip aligned with the bottom of the box,
        // which for an N-frame strip is exactly the last frame - see the note
        // on appendAnimatedImageRules.
        sb.appendLine("  to { background-position: 0 100%; }")
        sb.appendLine("}")
    }

    /** One Minecraft tick in seconds. */
    private const val TICK_SECONDS = 0.05f

    private fun percent(fraction: Float): String = "${trim(fraction * 100f)}%"

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
            appendLine(" * Generated by ${Branding.NAME} from '${project.name}'.")
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

    // -- SVG ---------------------------------------------------------------

    /**
     * The layout as vector art.
     *
     * The one export where a custom shape survives as a shape: HTML approximates
     * a hexagon with a `clip-path` and a resource pack can only rasterise it,
     * but SVG has real polygons, so a design built out of them comes out
     * editable in Illustrator, Inkscape or Figma.  Everything else is drawn as
     * its box with its own fill, which is enough to keep the composition
     * readable.
     */
    private fun svg(project: GuiProject): String {
        val bounds = project.absoluteBounds()
        val flat = project.elements.walkAll().toList()

        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" " +
                    "width=\"${project.canvas.width}\" height=\"${project.canvas.height}\" " +
                    "viewBox=\"0 0 ${project.canvas.width} ${project.canvas.height}\" " +
                    "shape-rendering=\"crispEdges\">",
            )
            appendLine("  <title>${ExportUtil.escape(project.name)}</title>")
            appendLine(
                "  <desc>Generated by ${Branding.NAME}. " +
                    "${project.edition.displayName}, ${project.canvas.width}x${project.canvas.height} GUI pixels.</desc>",
            )

            for (element in flat) {
                if (!element.visible) continue
                val rect = bounds[element.id] ?: continue
                appendLine("  <g id=\"${ExportUtil.cssClass(element.name)}\">")
                appendSvgElement(this, element, rect)
                appendLine("  </g>")
            }
            append("</svg>")
        }
    }

    private fun appendSvgElement(sb: StringBuilder, element: GuiElement, rect: IntRect) {
        // Rotation wraps the whole element, whatever it is. It used to be
        // written only inside the custom-shape branch below, so a turned button
        // came out of SVG square while CSS, React, SwiftUI and Android XML - all
        // of which read it off any element - came out turned.
        val rotation = element.props.int("rotation", 0)
        if (rotation != 0) {
            sb.appendLine(
                "    <g transform=\"rotate($rotation ${rect.centerX} ${rect.centerY})\">",
            )
        }
        appendSvgBody(sb, element, rect)
        if (rotation != 0) sb.appendLine("    </g>")
    }

    private fun appendSvgBody(sb: StringBuilder, element: GuiElement, rect: IntRect) {
        val opacity = element.props.float("opacity", 1f).coerceIn(0f, 1f)
        val label = displayText(element)

        if (element.type == ElementCatalog.SHAPE_CUSTOM) {
            val kind = ShapeKind.fromId(element.props.string("shape", ShapeKind.RECTANGLE.id))
            val fillMode = element.props.string("fillMode", "solid")
            val fill = if (fillMode == "none") "none" else ExportUtil.hex(element.props.color("fillColor", 0xFF56B84B))
            val stroke = ExportUtil.hex(element.props.color("strokeColor", 0xFF000000))
            val strokeWidth = element.props.int("strokeWidth", 1)
            // No transform here: the wrapping <g> in appendSvgElement turns
            // every element, and turning the shape again here would turn it
            // twice as far as anything else on the canvas.
            val paint = "fill=\"$fill\" stroke=\"$stroke\" stroke-width=\"$strokeWidth\" " +
                "opacity=\"${trim(opacity)}\""

            when (kind) {
                ShapeKind.ELLIPSE -> sb.appendLine(
                    "    <ellipse cx=\"${rect.centerX}\" cy=\"${rect.centerY}\" " +
                        "rx=\"${rect.width / 2}\" ry=\"${rect.height / 2}\" $paint/>",
                )

                ShapeKind.ROUNDED_RECTANGLE -> {
                    val radius = element.props.int("cornerRadius", 6)
                        .coerceAtMost(minOf(rect.width, rect.height) / 2)
                    sb.appendLine(
                        "    <rect x=\"${rect.x}\" y=\"${rect.y}\" width=\"${rect.width}\" " +
                            "height=\"${rect.height}\" rx=\"$radius\" ry=\"$radius\" $paint/>",
                    )
                }

                else -> {
                    val points = kind
                        .outline(element.props.int("sides", 6), element.props.float("innerRadius", 0.5f))
                        .joinToString(" ") { (fx, fy) ->
                            "${trim(rect.x + fx * rect.width)},${trim(rect.y + fy * rect.height)}"
                        }
                    sb.appendLine("    <polygon points=\"$points\" $paint/>")
                }
            }
        } else {
            val fill = ExportUtil.hex(
                element.props.color(
                    "background",
                    element.props.color("fillColor", DEFAULT_SVG_FILL),
                ),
            )
            sb.appendLine(
                "    <rect x=\"${rect.x}\" y=\"${rect.y}\" width=\"${rect.width}\" height=\"${rect.height}\" " +
                    "fill=\"$fill\" opacity=\"${trim(opacity)}\"/>",
            )
        }

        val text = label.ifBlank { element.props.string("label", "") }
        if (text.isNotBlank()) {
            sb.appendLine(
                "    <text x=\"${rect.centerX}\" y=\"${rect.centerY + 3}\" text-anchor=\"middle\" " +
                    "font-family=\"monospace\" font-size=\"8\" " +
                    "fill=\"${ExportUtil.hex(element.props.color("textColor", 0xFFE0E0E0))}\">" +
                    ExportUtil.escape(text.replace('\n', ' ')) +
                    "</text>",
            )
        }
    }

    /** Drops a trailing `.0`, which SVG accepts but which reads as noise. */
    private fun trim(value: Float): String {
        val rounded = ((value * 100) + if (value < 0) -0.5f else 0.5f).toInt() / 100.0
        return if (rounded == rounded.toInt().toDouble()) rounded.toInt().toString() else rounded.toString()
    }

    /** Neutral grey for elements with no colour of their own. */
    private const val DEFAULT_SVG_FILL = 0xFF8B8B8BL

    // -- React ---------------------------------------------------------------

    /**
     * A function component with inline styles.
     *
     * Absolute positioning inside a sized wrapper rather than flex or grid: a
     * Minecraft screen is a fixed pixel canvas, and translating fixed
     * coordinates into a flow layout would be inventing a structure the design
     * does not have and cannot round-trip.
     */
    private fun reactJsx(project: GuiProject): String {
        val bounds = project.absoluteBounds()
        val flat = project.elements.walkAll().filter { it.visible }.toList()
        val name = pascal(project.name)

        return buildString {
            appendLine("// Generated by ${Branding.NAME} from '${project.name}'.")
            appendLine("// Canvas: ${project.canvas.width}x${project.canvas.height}px at 1x.")
            appendLine()
            appendLine("export default function $name() {")
            appendLine("  return (")
            appendLine("    <div")
            appendLine("      style={{")
            appendLine("        position: 'relative',")
            appendLine("        width: ${project.canvas.width},")
            appendLine("        height: ${project.canvas.height},")
            appendLine("        background: '${cssHex(project.canvas.backdropColor)}',")
            appendLine("        imageRendering: 'pixelated',")
            appendLine("      }}")
            appendLine("    >")
            flat.forEach { element ->
                val rect = bounds[element.id] ?: element.bounds
                val background = element.props.color("background", 0x00000000)
                val text = displayText(element)
                appendLine("      {/* ${element.name} (${element.type}) */}")
                appendLine("      <div")
                appendLine("        style={{")
                appendLine("          position: 'absolute',")
                appendLine("          left: ${rect.x},")
                appendLine("          top: ${rect.y},")
                appendLine("          width: ${rect.width},")
                appendLine("          height: ${rect.height},")
                if (background != 0x00000000L) {
                    appendLine("          background: '${cssHex(background)}',")
                }
                element.props.int("rotation", 0).takeIf { it != 0 }?.let { angle ->
                    appendLine("          transform: 'rotate(${angle}deg)',")
                }
                appendLine("        }}")
                if (text.isBlank()) {
                    appendLine("      />")
                } else {
                    appendLine("      >")
                    appendLine("        ${escapeJsx(text)}")
                    appendLine("      </div>")
                }
            }
            appendLine("    </div>")
            appendLine("  );")
            appendLine("}")
        }
    }

    // -- SwiftUI -------------------------------------------------------------

    /**
     * A `View` backed by a ZStack.
     *
     * `.offset` in SwiftUI moves a view from its *centre*, and a ZStack centres
     * its children, so every element is shifted by half the canvas and half its
     * own size to land where the design says. Skipping that correction is the
     * classic way a SwiftUI export comes out looking almost right.
     */
    private fun swiftUi(project: GuiProject): String {
        val bounds = project.absoluteBounds()
        val flat = project.elements.walkAll().filter { it.visible }.toList()
        val name = "${pascal(project.name)}View"
        val cw = project.canvas.width
        val ch = project.canvas.height

        return buildString {
            appendLine("import SwiftUI")
            appendLine()
            appendLine("// Generated by ${Branding.NAME} from '${project.name}'.")
            appendLine("// Canvas: ${cw}x${ch}pt at 1x.")
            appendLine("struct $name: View {")
            appendLine("    var body: some View {")
            appendLine("        ZStack(alignment: .topLeading) {")
            appendLine("            ${swiftColor(project.canvas.backdropColor)}")
            flat.forEach { element ->
                val rect = bounds[element.id] ?: element.bounds
                val background = element.props.color("background", 0x00000000)
                val text = displayText(element)
                appendLine("            // ${element.name} (${element.type})")
                if (text.isBlank()) {
                    appendLine("            ${swiftColor(background)}")
                } else {
                    appendLine("            Text(${quote(text)})")
                    appendLine("                .frame(width: ${rect.width}, height: ${rect.height})")
                    if (background != 0x00000000L) {
                        appendLine("                .background(${swiftColor(background)})")
                    }
                }
                if (text.isBlank()) {
                    appendLine("                .frame(width: ${rect.width}, height: ${rect.height})")
                }
                element.props.int("rotation", 0).takeIf { it != 0 }?.let { angle ->
                    appendLine("                .rotationEffect(.degrees($angle))")
                }
                appendLine("                .offset(x: ${rect.x}, y: ${rect.y})")
            }
            appendLine("        }")
            appendLine("        .frame(width: ${cw}, height: ${ch}, alignment: .topLeading)")
            appendLine("    }")
            appendLine("}")
        }
    }

    // -- Flutter -------------------------------------------------------------

    /** A StatelessWidget built from a Stack of Positioned children. */
    private fun flutter(project: GuiProject): String {
        val bounds = project.absoluteBounds()
        val flat = project.elements.walkAll().filter { it.visible }.toList()
        val name = pascal(project.name)

        return buildString {
            appendLine("import 'package:flutter/material.dart';")
            appendLine()
            appendLine("// Generated by ${Branding.NAME} from '${project.name}'.")
            appendLine("// Canvas: ${project.canvas.width}x${project.canvas.height} at 1x.")
            appendLine("class $name extends StatelessWidget {")
            appendLine("  const $name({super.key});")
            appendLine()
            appendLine("  @override")
            appendLine("  Widget build(BuildContext context) {")
            appendLine("    return SizedBox(")
            appendLine("      width: ${project.canvas.width},")
            appendLine("      height: ${project.canvas.height},")
            appendLine("      child: Stack(")
            appendLine("        children: [")
            appendLine("          Positioned.fill(child: ColoredBox(color: ${dartColor(project.canvas.backdropColor)})),")
            flat.forEach { element ->
                val rect = bounds[element.id] ?: element.bounds
                val background = element.props.color("background", 0x00000000)
                val text = displayText(element)
                appendLine("          // ${element.name} (${element.type})")
                appendLine("          Positioned(")
                appendLine("            left: ${rect.x},")
                appendLine("            top: ${rect.y},")
                appendLine("            width: ${rect.width},")
                appendLine("            height: ${rect.height},")
                appendLine("            child: Container(")
                appendLine("              color: ${dartColor(background)},")
                if (text.isBlank()) {
                    appendLine("            ),")
                } else {
                    appendLine("              alignment: Alignment.center,")
                    appendLine("              child: Text(${quote(text)}),")
                    appendLine("            ),")
                }
                appendLine("          ),")
            }
            appendLine("        ],")
            appendLine("      ),")
            appendLine("    );")
            appendLine("  }")
            appendLine("}")
        }
    }

    // -- Android XML ---------------------------------------------------------

    /**
     * A FrameLayout with absolute margins.
     *
     * Not ConstraintLayout: constraints describe *relationships*, and a fixed
     * canvas has none to describe. Generating them would mean inventing a
     * structure the designer never expressed, and the first edit in Android
     * Studio would fight it.
     */
    private fun androidXml(project: GuiProject): String {
        val bounds = project.absoluteBounds()
        val flat = project.elements.walkAll().filter { it.visible }.toList()

        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            appendLine("<!-- Generated by ${Branding.NAME} from '${project.name}'. -->")
            appendLine("<!-- Canvas: ${project.canvas.width}x${project.canvas.height}dp at 1x. -->")
            appendLine("<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"")
            appendLine("    android:layout_width=\"${project.canvas.width}dp\"")
            appendLine("    android:layout_height=\"${project.canvas.height}dp\"")
            appendLine("    android:background=\"${cssHex(project.canvas.backdropColor)}\">")
            flat.forEach { element ->
                val rect = bounds[element.id] ?: element.bounds
                val background = element.props.color("background", 0x00000000)
                val text = displayText(element)
                appendLine()
                appendLine("    <!-- ${element.name} (${element.type}) -->")
                appendLine("    <TextView")
                appendLine("        android:layout_width=\"${rect.width}dp\"")
                appendLine("        android:layout_height=\"${rect.height}dp\"")
                appendLine("        android:layout_marginStart=\"${rect.x}dp\"")
                appendLine("        android:layout_marginTop=\"${rect.y}dp\"")
                if (background != 0x00000000L) {
                    appendLine("        android:background=\"${cssHex(background)}\"")
                }
                element.props.int("rotation", 0).takeIf { it != 0 }?.let { angle ->
                    appendLine("        android:rotation=\"$angle\"")
                }
                appendLine("        android:gravity=\"center\"")
                appendLine("        android:text=\"${escapeXml(text)}\" />")
            }
            appendLine()
            appendLine("</FrameLayout>")
        }
    }

    // -- Shared ------------------------------------------------------------

    /**
     * UpperCamelCase, for the languages that name a type after the file.
     *
     * Splits on both separators because `Ids.slug` uses underscores while
     * plenty of names arrive hyphenated; assuming one of them produces
     * `Chest_screen`, which is a type name in no language anybody uses.
     */
    private fun pascal(name: String): String =
        Ids.slug(name).split('-', '_', ' ').filter { it.isNotBlank() }
            .joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }
            .ifBlank { "Screen" }

    /** `#rrggbb` / `#aarrggbb`, the form CSS, XML and the web all read. */
    private fun cssHex(argb: Long): String {
        val hex = argb.toString(16).uppercase().padStart(8, '0')
        return if (hex.startsWith("FF")) "#" + hex.substring(2) else "#" + hex
    }

    private fun swiftColor(argb: Long): String {
        val a = ((argb shr 24) and 0xFF) / 255.0
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        return "Color(.sRGB, red: ${fixed(r)}, green: ${fixed(g)}, blue: ${fixed(b)}, opacity: ${fixed(a)})"
    }

    private fun dartColor(argb: Long): String =
        "Color(0x" + argb.toString(16).uppercase().padStart(8, '0') + ")"

    /** Three decimals - enough to be exact for 8-bit channels, short enough to read. */
    private fun fixed(v: Double): String {
        val scaled = kotlin.math.round(v * 1000).toInt()
        return "${scaled / 1000}.${(scaled % 1000).toString().padStart(3, '0')}"
    }

    private fun quote(text: String): String =
        "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun escapeJsx(text: String): String =
        text.replace("{", "&#123;").replace("}", "&#125;")
            .replace("<", "&lt;").replace(">", "&gt;")

    private fun escapeXml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;")


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
