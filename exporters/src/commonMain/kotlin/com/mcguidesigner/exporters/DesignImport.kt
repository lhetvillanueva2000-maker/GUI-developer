package com.mcguidesigner.exporters

import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiProject

/**
 * A format the designer can read a design *back* out of.
 *
 * The app has always been able to write eleven things and read one - its own
 * `.mcgui` document. That is fine right up until somebody exports a screen,
 * hand-edits the JSON the game actually reads, and wants the change back in the
 * editor; at that point the export is a one-way door and the editor is the
 * least convenient place to work.
 *
 * These importers are deliberately scoped: each one round-trips what this app
 * writes, and makes a decent, *reported* attempt at anything else. An importer
 * that silently produced half a screen from a file it did not really understand
 * would be worse than one that says what it could not read - so every one of
 * them returns its notes alongside the project.
 */
enum class ImportFormat(
    val id: String,
    val displayName: String,
    /** Lower-case, no dot. */
    val extensions: List<String>,
    val blurb: String,
) {
    PROJECT(
        "project", "Project document", listOf("mcgui", "json"),
        "A document this app saved. Everything comes back exactly as it was.",
    ),
    BEDROCK_JSON_UI(
        "bedrock-json-ui", "Bedrock JSON UI", listOf("json"),
        "A ui/<screen>.json from a Bedrock resource pack. Controls, sizes, " +
            "offsets, anchors and text come across.",
    ),
    HTML(
        "html", "HTML + CSS", listOf("html", "htm"),
        "A page of absolutely positioned elements - including the one this " +
            "app exports. Geometry, colours and text come across.",
    ),
    SVG(
        "svg", "SVG drawing", listOf("svg"),
        "Rectangles and text from a vector drawing, placed at their own " +
            "coordinates.",
    ),
    ;

    companion object {
        /** Every format that can be offered in a file picker. */
        val all: List<ImportFormat> get() = entries

        /** The distinct file extensions worth showing in an "open" dialog. */
        val allExtensions: List<String> get() = entries.flatMap { it.extensions }.distinct()
    }
}

/**
 * What came of reading a file.
 *
 * [project] is null only when nothing usable was found; [notes] is populated
 * either way and is the part that matters. An import that quietly drops the
 * half of a file it did not understand is how somebody loses work without
 * noticing, so anything skipped is said out loud.
 */
data class ImportOutcome(
    val format: ImportFormat?,
    val project: GuiProject?,
    val notes: List<String> = emptyList(),
) {
    val succeeded: Boolean get() = project != null

    companion object {
        fun failed(format: ImportFormat?, note: String) = ImportOutcome(format, null, listOf(note))
    }
}

/**
 * Works out what a file is and hands it to the right reader.
 *
 * Sniffs the *content* rather than trusting the extension, because the three
 * JSON-shaped formats all arrive as `.json` and telling a Bedrock screen from
 * one of this app's documents by its name alone is guesswork. The extension is
 * used only to break ties the content cannot.
 */
object DesignImporter {

    /**
     * The format [content] appears to be, or null.
     *
     * Ordered most-specific first: a project document is also valid JSON, and
     * a Bedrock screen is also valid JSON, so whichever test is narrowest has
     * to run before the ones that would also match.
     */
    fun detect(fileName: String, content: String): ImportFormat? {
        val trimmed = content.trimStart()
        val extension = fileName.substringAfterLast('.', "").lowercase()

        return when {
            // Our own document names itself in its first few hundred bytes.
            trimmed.startsWith("{") && content.contains("\"formatVersion\"") -> ImportFormat.PROJECT
            trimmed.startsWith("{") && looksLikeBedrockUi(content) -> ImportFormat.BEDROCK_JSON_UI
            trimmed.startsWith("<") && trimmed.contains("<svg", ignoreCase = true) -> ImportFormat.SVG
            trimmed.startsWith("<") -> ImportFormat.HTML
            extension == "mcgui" -> ImportFormat.PROJECT
            extension == "svg" -> ImportFormat.SVG
            extension == "html" || extension == "htm" -> ImportFormat.HTML
            extension == "json" -> ImportFormat.BEDROCK_JSON_UI
            else -> null
        }
    }

    /**
     * A Bedrock screen has controls, and a control is an object with a `type`.
     *
     * Checked as text rather than by parsing, because this runs on files that
     * may not be JSON at all and the answer only decides which parser to try.
     */
    private fun looksLikeBedrockUi(content: String): Boolean =
        content.contains("\"type\"") &&
            (
                content.contains("\"controls\"") ||
                    content.contains("\"anchor_from\"") ||
                    content.contains("\"stack_panel\"") ||
                    content.contains("\"$")
                )

    /**
     * Reads [content] into a project.
     *
     * [edition] is the edition to give the result when the format itself does
     * not say - HTML and SVG have no opinion about Minecraft editions, and a
     * screen imported while the Java editor is open should be a Java screen.
     */
    fun import(
        fileName: String,
        content: String,
        edition: Edition = Edition.JAVA,
        name: String = fileName.substringAfterLast('/').substringBeforeLast('.'),
    ): ImportOutcome {
        val format = detect(fileName, content)
            ?: return ImportOutcome.failed(null, "Could not tell what sort of file this is.")

        return when (format) {
            ImportFormat.PROJECT -> importProject(content)
            ImportFormat.BEDROCK_JSON_UI -> BedrockUiImporter.read(content, name)
            ImportFormat.HTML -> HtmlImporter.read(content, name, edition)
            ImportFormat.SVG -> SvgImporter.read(content, name, edition)
        }
    }

    private fun importProject(content: String): ImportOutcome =
        when (val result = com.mcguidesigner.core.serialization.ProjectSerializer.decode(content)) {
            is com.mcguidesigner.core.serialization.LoadResult.Success ->
                ImportOutcome(ImportFormat.PROJECT, result.project, result.warnings)

            is com.mcguidesigner.core.serialization.LoadResult.Failure ->
                ImportOutcome.failed(ImportFormat.PROJECT, result.message)
        }
}
