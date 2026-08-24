package com.mcguidesigner.exporters

import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.model.CanvasSpec
import com.mcguidesigner.core.model.ColorValue
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.PropValue
import com.mcguidesigner.core.model.StringValue
import com.mcguidesigner.core.util.Ids

/**
 * Reads a page of absolutely positioned elements back into a design.
 *
 * Not a browser. It understands the shape this app writes - one `.mcgui-el`
 * per element, one CSS rule per element carrying `left/top/width/height` - and
 * generalises exactly as far as that shape does: any element with a class or
 * inline style that gives it those four numbers is read, and anything without
 * them is skipped and reported.
 *
 * Deliberately regex-and-string rather than a real HTML parser: pulling in a
 * parser that works on all six Kotlin targets to read a file format we also
 * write is a large dependency for a small job, and a real parser would not
 * make the *layout* rules any less approximate. The limits are in [read]'s
 * notes rather than hidden behind an interface that implies more than it does.
 */
object HtmlImporter {

    /** `left: 12px;` and friends. */
    private val declaration = Regex("([-a-zA-Z]+)\\s*:\\s*([^;{}]+)")

    /** One `selector { ... }` rule. Non-greedy so nested braces end it early. */
    private val cssRule = Regex("([^{}]+)\\{([^{}]*)\\}")

    /** `<div class="a b" style="...">text</div>` and the self-closing forms. */
    private val tag = Regex("<(div|span|img|input|p|section|button)\\b([^>]*)>", RegexOption.IGNORE_CASE)

    private val attribute = Regex("([-a-zA-Z]+)\\s*=\\s*\"([^\"]*)\"")

    fun read(content: String, name: String, edition: Edition): ImportOutcome {
        val notes = mutableListOf<String>()

        val styles = collectCssRules(content)
        val canvas = readCanvas(styles, content)
        val elements = mutableListOf<GuiElement>()
        var skipped = 0

        // Comments stripped once, and everything below indexes into *this*
        // string: taking a match range from the stripped text and reading the
        // label out of the original would land somewhere else entirely.
        val body = stripComments(content)

        tag.findAll(body).forEach { match ->
            val attributes = attribute.findAll(match.groupValues[2])
                .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val classes = attributes["class"].orEmpty().split(' ').filter { it.isNotBlank() }

            // Inline styles win over the stylesheet, as they do in a browser.
            val declarations = buildMap {
                classes.forEach { putAll(styles[".$it"].orEmpty()) }
                putAll(parseDeclarations(attributes["style"].orEmpty()))
            }

            val rect = rectFrom(declarations)
            if (rect == null) {
                // The stage and canvas wrappers have no geometry of their own;
                // they are not skipped elements, they are the frame.
                if (classes.none { it == "mcgui-stage" || it == "mcgui-canvas" }) skipped++
                return@forEach
            }

            val type = typeFrom(classes, match.groupValues[1])
            val label = textInside(body, match.range.last + 1)
            elements += GuiElement(
                id = Ids.prefixed("el"),
                type = type,
                name = nameFrom(classes, attributes, label, elements.size + 1),
                bounds = rect,
                props = propsFrom(declarations, type, label),
            )
        }

        if (elements.isEmpty()) {
            return ImportOutcome.failed(
                ImportFormat.HTML,
                "No positioned elements found. This reader needs each element to have " +
                    "left, top, width and height in pixels - either in a class rule or " +
                    "an inline style.",
            )
        }

        if (skipped > 0) {
            notes += "$skipped element(s) had no pixel geometry and were skipped."
        }
        notes += "Imported as flat elements: nesting, hover states and textures are not " +
            "recovered from HTML."

        return ImportOutcome(
            format = ImportFormat.HTML,
            project = GuiProject(
                id = Ids.prefixed("proj"),
                name = name.ifBlank { "Imported page" },
                edition = edition,
                canvas = canvas,
                elements = elements,
            ),
            notes = notes,
        )
    }

    // -- CSS ---------------------------------------------------------------

    /** Every `selector { declarations }` in every `<style>` block, flattened. */
    private fun collectCssRules(content: String): Map<String, Map<String, String>> {
        val styleBlocks = Regex("<style[^>]*>(.*?)</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(content)
            .map { it.groupValues[1] }
            .toList()
            // A bare .css file has no <style> tags; treat the whole thing as one.
            .ifEmpty { listOf(content.takeIf { !it.trimStart().startsWith("<") } ?: "") }

        val rules = mutableMapOf<String, MutableMap<String, String>>()
        styleBlocks.forEach { block ->
            cssRule.findAll(stripComments(block)).forEach { match ->
                val declarations = parseDeclarations(match.groupValues[2])
                // `a, b { ... }` applies to both, and a selector may appear
                // twice, in which case the later one adds to the earlier.
                match.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { selector ->
                    rules.getOrPut(selector) { mutableMapOf() }.putAll(declarations)
                }
            }
        }
        return rules
    }

    private fun parseDeclarations(text: String): Map<String, String> =
        declaration.findAll(text).associate {
            it.groupValues[1].trim().lowercase() to it.groupValues[2].trim()
        }

    /** Comments in both languages, so a commented-out rule is not read as one. */
    private fun stripComments(text: String): String = text
        .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")

    // -- Geometry ----------------------------------------------------------

    private fun rectFrom(declarations: Map<String, String>): IntRect? {
        val x = pixels(declarations["left"]) ?: return null
        val y = pixels(declarations["top"]) ?: return null
        val width = pixels(declarations["width"]) ?: return null
        val height = pixels(declarations["height"]) ?: return null
        if (width <= 0 || height <= 0) return null
        return IntRect(x, y, width, height)
    }

    /**
     * `12px` and `12` become 12; `50%`, `auto` and `calc(...)` become null.
     *
     * Percentages are refused rather than guessed at: this format has no layout
     * engine behind it, so "half of the parent" is a question nothing here can
     * answer, and inventing a number would put the element somewhere plausible
     * and wrong.
     */
    private fun pixels(raw: String?): Int? {
        val text = raw?.trim()?.lowercase() ?: return null
        if (text.endsWith("%") || text.startsWith("calc") || text == "auto") return null
        return text.removeSuffix("px").trim().toFloatOrNull()?.toInt()
    }

    private fun readCanvas(styles: Map<String, Map<String, String>>, content: String): CanvasSpec {
        val root = styles[":root"].orEmpty()
        val width = pixels(root["--mcgui-canvas-w"]) ?: pixels(styles[".mcgui-canvas"]?.get("width"))
        val height = pixels(root["--mcgui-canvas-h"]) ?: pixels(styles[".mcgui-canvas"]?.get("height"))
        val scale = root["--mcgui-scale"]?.trim()?.toIntOrNull()
        val backdrop = parseColor(root["--mcgui-backdrop"])

        return CanvasSpec(
            // A page with no declared canvas is measured by what is on it, so
            // an import from somewhere else is not clipped to a default size.
            width = width ?: measuredWidth(content) ?: CanvasSpec().width,
            height = height ?: CanvasSpec().height,
            guiScale = scale?.coerceIn(1, 4) ?: CanvasSpec().guiScale,
            backdropColor = backdrop ?: CanvasSpec().backdropColor,
        )
    }

    /** Widest `width:` seen anywhere, as a last resort for an unknown page. */
    private fun measuredWidth(content: String): Int? =
        Regex("width\\s*:\\s*([0-9]+)px").findAll(content)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .maxOrNull()

    // -- Element identity --------------------------------------------------

    /**
     * The catalog type, from the `mcgui-<type>` class this app writes.
     *
     * Falls back to the tag when there is no such class, which is what makes
     * a page from anywhere else import as something sensible rather than as a
     * screenful of anonymous panels.
     */
    private fun typeFrom(classes: List<String>, tagName: String): String {
        classes.forEach { candidate ->
            if (!candidate.startsWith("mcgui-")) return@forEach
            val typeId = candidate.removePrefix("mcgui-").replace('-', '.')
            if (ElementCatalog[typeId] != null) return typeId
        }
        return when (tagName.lowercase()) {
            "img" -> ElementCatalog.IMAGE_PLACEHOLDER
            "input" -> ElementCatalog.INPUT_TEXTBOX
            "button" -> ElementCatalog.BUTTON_NORMAL
            "span", "p" -> ElementCatalog.TEXT_LABEL
            else -> ElementCatalog.PANEL_FRAME
        }
    }

    private fun nameFrom(
        classes: List<String>,
        attributes: Map<String, String>,
        label: String,
        ordinal: Int,
    ): String {
        attributes["alt"]?.takeIf { it.isNotBlank() }?.let { return it }
        attributes["id"]?.takeIf { it.isNotBlank() }?.let { return it }
        if (label.isNotBlank()) return label.take(40)
        // `mcgui-save-button-a1b2` - the middle is the name it was exported
        // under, and the last four characters are the old element id.
        classes.firstOrNull { it.startsWith("mcgui-") && it.count { ch -> ch == '-' } >= 2 }
            ?.removePrefix("mcgui-")
            ?.substringBeforeLast('-')
            ?.replace('-', ' ')
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return "Element $ordinal"
    }

    /** The text between this tag and its close, with any nested tags removed. */
    private fun textInside(content: String, from: Int): String {
        val end = content.indexOf('<', from)
        if (end < 0) return ""
        return content.substring(from, end).trim().replace("&amp;", "&").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&quot;", "\"")
    }

    private fun propsFrom(
        declarations: Map<String, String>,
        type: String,
        label: String,
    ): Map<String, PropValue> = buildMap {
        parseColor(declarations["background-color"])?.let { put("background", ColorValue(it)) }
        parseColor(declarations["color"])?.let { put("textColor", ColorValue(it)) }
        if (label.isNotBlank()) {
            // Which property holds the words depends on the element; putting
            // it under the wrong one loses it silently.
            val key = if (type == ElementCatalog.INPUT_TEXTBOX) "placeholder" else "text"
            put(key, StringValue(label))
        }
    }

    // -- Colour ------------------------------------------------------------

    /**
     * `rgba(r, g, b, a)`, `rgb(r, g, b)`, `#rgb`, `#rrggbb` and `#aarrggbb`.
     *
     * Returns the same packed `0xAARRGGBB` the model stores, so what comes back
     * in is byte-identical to what went out for anything this app wrote.
     */
    fun parseColor(raw: String?): Long? {
        val text = raw?.trim()?.lowercase() ?: return null

        if (text.startsWith("rgb")) {
            val parts = text.substringAfter('(').substringBefore(')').split(',').map { it.trim() }
            if (parts.size < 3) return null
            val r = parts[0].toFloatOrNull()?.toInt() ?: return null
            val g = parts[1].toFloatOrNull()?.toInt() ?: return null
            val b = parts[2].toFloatOrNull()?.toInt() ?: return null
            val a = parts.getOrNull(3)?.toFloatOrNull()?.let { (it * 255).toInt() } ?: 255
            return pack(a, r, g, b)
        }

        if (text.startsWith("#")) {
            val hex = text.removePrefix("#")
            return when (hex.length) {
                3 -> pack(
                    255,
                    hex[0].digitToIntOrNull(16)?.times(17) ?: return null,
                    hex[1].digitToIntOrNull(16)?.times(17) ?: return null,
                    hex[2].digitToIntOrNull(16)?.times(17) ?: return null,
                )

                6 -> hex.toLongOrNull(16)?.let { 0xFF000000L or it }
                8 -> hex.toLongOrNull(16)
                else -> null
            }
        }

        return NAMED_COLORS[text]
    }

    private fun pack(a: Int, r: Int, g: Int, b: Int): Long =
        ((a.coerceIn(0, 255).toLong() shl 24) or
            (r.coerceIn(0, 255).toLong() shl 16) or
            (g.coerceIn(0, 255).toLong() shl 8) or
            b.coerceIn(0, 255).toLong())

    /**
     * The handful of CSS colour names worth carrying.
     *
     * Not all 148: the long list is dead weight for a format whose own exporter
     * only ever writes `rgba()`, and these are the ones a person hand-editing a
     * file actually types.
     */
    private val NAMED_COLORS = mapOf(
        "black" to 0xFF000000L,
        "white" to 0xFFFFFFFFL,
        "red" to 0xFFFF0000L,
        "green" to 0xFF008000L,
        "blue" to 0xFF0000FFL,
        "yellow" to 0xFFFFFF00L,
        "cyan" to 0xFF00FFFFL,
        "magenta" to 0xFFFF00FFL,
        "gray" to 0xFF808080L,
        "grey" to 0xFF808080L,
        "silver" to 0xFFC0C0C0L,
        "transparent" to 0x00000000L,
    )
}
