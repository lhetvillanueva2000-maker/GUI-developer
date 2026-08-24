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
     * The formats worth trying for this file, likeliest first.
     *
     * A list rather than one answer, because sniffing a format is a guess and
     * this one used to guess badly: "is it a project?" was `content.contains
     * ("\"formatVersion\"")`, which any HTML page that happened to mention the
     * word would pass, and the file would then fail to import as the one thing
     * it definitely was not.
     *
     * Returning candidates lets a wrong guess correct itself - [import] tries
     * each in turn and keeps the first that actually parses - so the cost of a
     * near-miss is a wasted parse rather than a failed import.
     */
    fun candidatesFor(fileName: String, content: String): List<ImportFormat> {
        val trimmed = content.trimStart()
        val extension = fileName.substringAfterLast('.', "").lowercase()

        return when {
            trimmed.startsWith("{") -> listOf(ImportFormat.PROJECT, ImportFormat.BEDROCK_JSON_UI)
            trimmed.startsWith("<") && trimmed.contains("<svg", ignoreCase = true) ->
                listOf(ImportFormat.SVG, ImportFormat.HTML)

            trimmed.startsWith("<") -> listOf(ImportFormat.HTML, ImportFormat.SVG)
            extension == "mcgui" -> listOf(ImportFormat.PROJECT)
            extension == "svg" -> listOf(ImportFormat.SVG)
            extension == "html" || extension == "htm" -> listOf(ImportFormat.HTML)
            extension == "json" -> listOf(ImportFormat.PROJECT, ImportFormat.BEDROCK_JSON_UI)
            else -> emptyList()
        }
    }

    /** The likeliest format, for a caller that only wants to label a file. */
    fun detect(fileName: String, content: String): ImportFormat? =
        candidatesFor(fileName, content).firstOrNull()

    /**
     * Reads [content] into a project.
     *
     * [edition] is the edition to give the result when the format itself does
     * not say - HTML and SVG have no opinion about Minecraft editions, and a
     * screen imported while the Java editor is open should be a Java screen.
     *
     * When several formats are plausible, each is tried until one produces a
     * project. If none does, the *first* candidate's failure is the one
     * reported: it is the likeliest reading of the file, so its complaint is
     * the one most likely to describe what is actually wrong.
     */
    fun import(
        fileName: String,
        content: String,
        edition: Edition = Edition.JAVA,
        name: String = fileName.substringAfterLast('/').substringBeforeLast('.'),
    ): ImportOutcome {
        val candidates = candidatesFor(fileName, content)
        if (candidates.isEmpty()) {
            return ImportOutcome.failed(null, "Could not tell what sort of file this is.")
        }

        var firstFailure: ImportOutcome? = null
        candidates.forEach { format ->
            val outcome = readAs(format, content, name, edition)
            if (outcome.succeeded) return outcome
            if (firstFailure == null) firstFailure = outcome
        }
        return firstFailure ?: ImportOutcome.failed(null, "Nothing could be read from this file.")
    }

    private fun readAs(
        format: ImportFormat,
        content: String,
        name: String,
        edition: Edition,
    ): ImportOutcome = when (format) {
        ImportFormat.PROJECT -> importProject(content)
        ImportFormat.BEDROCK_JSON_UI -> BedrockUiImporter.read(content, name)
        ImportFormat.HTML -> HtmlImporter.read(content, name, edition)
        ImportFormat.SVG -> SvgImporter.read(content, name, edition)
    }

    private fun importProject(content: String): ImportOutcome =
        when (val result = com.mcguidesigner.core.serialization.ProjectSerializer.decode(content)) {
            is com.mcguidesigner.core.serialization.LoadResult.Success ->
                ImportOutcome(ImportFormat.PROJECT, result.project, result.warnings)

            is com.mcguidesigner.core.serialization.LoadResult.Failure ->
                ImportOutcome.failed(ImportFormat.PROJECT, result.message)
        }
}
