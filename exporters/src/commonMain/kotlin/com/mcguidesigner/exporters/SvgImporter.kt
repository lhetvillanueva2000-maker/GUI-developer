package com.mcguidesigner.exporters

import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.model.CanvasSpec
import com.mcguidesigner.core.model.ColorValue
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.PropValue
import com.mcguidesigner.core.model.ShapeKind
import com.mcguidesigner.core.model.StringValue
import com.mcguidesigner.core.util.Ids
import kotlin.math.roundToInt

/**
 * Reads rectangles, ellipses and text out of an SVG drawing.
 *
 * The point of this one is not round-tripping our own export - it is that
 * every drawing tool in existence writes SVG, so a mock-up made in Figma,
 * Inkscape or Illustrator can be brought in as a starting layout instead of
 * being re-measured by hand against a screenshot.
 *
 * What it reads: `<rect>`, `<ellipse>`, `<circle>`, `<polygon>` bounding boxes
 * and `<text>`. What it does not: paths, gradients, filters, clip paths,
 * transforms other than the plain `translate` on a group. Those are named in
 * the notes rather than dropped in silence, because a drawing that comes in
 * missing its artwork should say which artwork it is missing.
 */
object SvgImporter {

    private val svgTag = Regex("<svg\\b([^>]*)>", RegexOption.IGNORE_CASE)
    private val shapeTag = Regex("<(rect|ellipse|circle|polygon|text)\\b([^>]*?)(/?)>", RegexOption.IGNORE_CASE)
    private val attribute = Regex("([-a-zA-Z:]+)\\s*=\\s*\"([^\"]*)\"")
    private val unsupported = Regex("<(path|linearGradient|radialGradient|filter|clipPath|use|image)\\b", RegexOption.IGNORE_CASE)

    fun read(content: String, name: String, edition: Edition): ImportOutcome {
        val notes = mutableListOf<String>()
        val header = svgTag.find(content)
            ?: return ImportOutcome.failed(ImportFormat.SVG, "No <svg> element found.")

        val rootAttributes = attributes(header.groupValues[1])
        val canvas = canvasFrom(rootAttributes)
        val elements = mutableListOf<GuiElement>()

        // Text is attached to the shape it sits inside rather than becoming a
        // label of its own, because that is what a drawing tool produces for a
        // button: a rectangle, then the word on top of it. Two elements would
        // be two things to move every time.
        val pendingText = mutableListOf<Pair<IntRect, TextRun>>()

        shapeTag.findAll(content).forEach { match ->
            val tagName = match.groupValues[1].lowercase()
            val attributes = attributes(match.groupValues[2])

            if (tagName == "text") {
                val x = number(attributes["x"]) ?: return@forEach
                val y = number(attributes["y"]) ?: return@forEach
                val label = textAfter(content, match.range.last + 1)
                if (label.isNotBlank()) {
                    pendingText += IntRect(x, y, 1, 1) to
                        TextRun(label, parseColor(attributes["fill"]))
                }
                return@forEach
            }

            val rect = boundsFor(tagName, attributes) ?: return@forEach
            elements += GuiElement(
                id = Ids.prefixed("el"),
                type = if (tagName == "rect") ElementCatalog.PANEL_FRAME else ElementCatalog.SHAPE_CUSTOM,
                name = attributes["id"]?.takeIf { it.isNotBlank() }?.replace('-', ' ')
                    ?: "${tagName.replaceFirstChar { it.uppercase() }} ${elements.size + 1}",
                bounds = rect,
                props = propsFor(tagName, attributes),
            )
        }

        // Attach each text run to the smallest shape that contains its origin,
        // which is the one it was drawn on top of. Anything with nothing under
        // it becomes a label of its own.
        var unattached = 0
        pendingText.forEach { (origin, run) ->
            val host = elements
                .withIndex()
                .filter { (_, element) -> element.bounds.contains(origin.x, origin.y) }
                .minByOrNull { (_, element) -> element.bounds.area }

            if (host == null) {
                unattached++
                elements += GuiElement(
                    id = Ids.prefixed("el"),
                    type = ElementCatalog.TEXT_LABEL,
                    name = run.text.take(40),
                    // A bare text run has no box of its own; give it one big
                    // enough for what it says at the 8px font both editions use.
                    bounds = IntRect(origin.x, origin.y - 6, (run.text.length * 5).coerceAtLeast(8), 10),
                    props = buildMap {
                        put("text", StringValue(run.text))
                        run.color?.let { put("textColor", ColorValue(it)) }
                    },
                )
            } else {
                val (index, element) = host
                elements[index] = element.copy(
                    name = run.text.take(40),
                    props = element.props + buildMap {
                        put(if (element.type == ElementCatalog.PANEL_FRAME) "label" else "text", StringValue(run.text))
                        run.color?.let { put("textColor", ColorValue(it)) }
                    },
                )
            }
        }

        if (elements.isEmpty()) {
            return ImportOutcome.failed(
                ImportFormat.SVG,
                "Nothing this reader understands. It reads rect, ellipse, circle, " +
                    "polygon and text; a drawing built from paths has none of those.",
            )
        }

        val skipped = unsupported.findAll(content).map { it.groupValues[1] }.toSet()
        if (skipped.isNotEmpty()) {
            notes += "Not read: ${skipped.sorted().joinToString(", ")}. " +
                "Only plain shapes and text come across."
        }
        notes += "Everything landed at its own coordinates, flat. " +
            "Group and re-anchor as needed."
        if (unattached > 0) notes += "$unattached text run(s) had no shape under them."

        return ImportOutcome(
            format = ImportFormat.SVG,
            project = GuiProject(
                id = Ids.prefixed("proj"),
                name = name.ifBlank { "Imported drawing" },
                edition = edition,
                canvas = canvas,
                elements = elements,
            ),
            notes = notes,
        )
    }

    private data class TextRun(val text: String, val color: Long?)

    // -- Attributes --------------------------------------------------------

    private fun attributes(raw: String): Map<String, String> =
        attribute.findAll(raw).associate { it.groupValues[1].lowercase() to it.groupValues[2] }

    /**
     * The canvas, from `viewBox` first and `width`/`height` second.
     *
     * `viewBox` wins because it is in the drawing's own units, whereas `width`
     * may be in millimetres or points - a drawing tool's idea of how big to
     * print it, which is not what a GUI canvas measures.
     */
    private fun canvasFrom(attributes: Map<String, String>): CanvasSpec {
        val viewBox = attributes["viewbox"]?.trim()?.split(Regex("[ ,]+"))
        val fromViewBox = viewBox?.takeIf { it.size == 4 }?.let {
            val width = it[2].toFloatOrNull()?.roundToInt()
            val height = it[3].toFloatOrNull()?.roundToInt()
            if (width != null && height != null && width > 0 && height > 0) width to height else null
        }
        val pair = fromViewBox ?: run {
            val width = number(attributes["width"])
            val height = number(attributes["height"])
            if (width != null && height != null && width > 0 && height > 0) width to height else null
        }
        return pair?.let { CanvasSpec(width = it.first, height = it.second) } ?: CanvasSpec()
    }

    /** A length, with any unit suffix dropped. Percentages are refused. */
    private fun number(raw: String?): Int? {
        val text = raw?.trim() ?: return null
        if (text.endsWith("%")) return null
        return text.trimEnd { !it.isDigit() && it != '.' && it != '-' }.toFloatOrNull()?.roundToInt()
    }

    private fun boundsFor(tagName: String, attributes: Map<String, String>): IntRect? = when (tagName) {
        "rect" -> {
            val x = number(attributes["x"]) ?: 0
            val y = number(attributes["y"]) ?: 0
            val width = number(attributes["width"]) ?: return null
            val height = number(attributes["height"]) ?: return null
            if (width > 0 && height > 0) IntRect(x, y, width, height) else null
        }

        "circle" -> {
            val cx = number(attributes["cx"]) ?: return null
            val cy = number(attributes["cy"]) ?: return null
            val r = number(attributes["r"]) ?: return null
            if (r > 0) IntRect(cx - r, cy - r, r * 2, r * 2) else null
        }

        "ellipse" -> {
            val cx = number(attributes["cx"]) ?: return null
            val cy = number(attributes["cy"]) ?: return null
            val rx = number(attributes["rx"]) ?: return null
            val ry = number(attributes["ry"]) ?: return null
            if (rx > 0 && ry > 0) IntRect(cx - rx, cy - ry, rx * 2, ry * 2) else null
        }

        "polygon" -> polygonBounds(attributes["points"])

        else -> null
    }

    /** The bounding box of a points list, which is what a shape element has. */
    private fun polygonBounds(points: String?): IntRect? {
        val numbers = points?.trim()?.split(Regex("[ ,]+"))?.mapNotNull { it.toFloatOrNull() } ?: return null
        if (numbers.size < 6) return null
        val xs = numbers.filterIndexed { index, _ -> index % 2 == 0 }
        val ys = numbers.filterIndexed { index, _ -> index % 2 == 1 }
        val left = xs.min().roundToInt()
        val top = ys.min().roundToInt()
        val width = (xs.max() - xs.min()).roundToInt().coerceAtLeast(1)
        val height = (ys.max() - ys.min()).roundToInt().coerceAtLeast(1)
        return IntRect(left, top, width, height)
    }

    private fun propsFor(tagName: String, attributes: Map<String, String>): Map<String, PropValue> = buildMap {
        val fill = parseColor(attributes["fill"])
        if (tagName == "rect") {
            fill?.let { put("background", ColorValue(it)) }
        } else {
            put(
                "shape",
                StringValue(
                    when (tagName) {
                        "ellipse", "circle" -> ShapeKind.ELLIPSE.id
                        else -> ShapeKind.RECTANGLE.id
                    },
                ),
            )
            fill?.let { put("fillColor", ColorValue(it)) }
            parseColor(attributes["stroke"])?.let { put("strokeColor", ColorValue(it)) }
            number(attributes["stroke-width"])?.let { put("strokeWidth", com.mcguidesigner.core.model.IntValue(it)) }
        }
    }

    /** Text between a `<text ...>` and the next `<`. */
    private fun textAfter(content: String, from: Int): String {
        val end = content.indexOf('<', from)
        if (end < 0) return ""
        return content.substring(from, end).trim()
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
    }

    /** SVG paints are CSS colours; the HTML reader already knows how to read one. */
    private fun parseColor(raw: String?): Long? =
        if (raw == null || raw.equals("none", ignoreCase = true)) null else HtmlImporter.parseColor(raw)
}
