package com.mcguidesigner.exporters

import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.validation.Severity
import com.mcguidesigner.core.validation.ValidationIssue
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** One file produced by an exporter, addressed by a bundle-relative path. */
sealed interface ExportFile {
    val path: String

    data class Text(override val path: String, val content: String) : ExportFile

    /** Binary payload carried as base64 so the model stays platform-neutral. */
    data class Binary(override val path: String, val base64: String) : ExportFile
}

@OptIn(ExperimentalEncodingApi::class)
fun ExportFile.bytes(): ByteArray = when (this) {
    is ExportFile.Text -> content.encodeToByteArray()
    is ExportFile.Binary -> Base64.decode(base64)
}

/** What an export run produces. */
enum class ExportTarget(val id: String, val displayName: String, val edition: Edition?) {
    JAVA_RESOURCE_PACK("java-pack", "Java resource pack + screen source", Edition.JAVA),
    BEDROCK_UI_PACK("bedrock-pack", "Bedrock JSON-UI resource pack", Edition.BEDROCK),
    PROJECT_JSON("project-json", "Project document (.mcgui)", null),
    CODE("code", "Generated code", null),
}

/**
 * Result of one export.  Files are returned in memory rather than written
 * directly so the same pipeline serves the desktop file system, Android's
 * storage-access framework and the unit tests.
 */
data class ExportBundle(
    val target: ExportTarget,
    /** Root folder name suggested to the user, e.g. `custom_chest_java_pack`. */
    val rootName: String,
    val files: List<ExportFile>,
    val warnings: List<ValidationIssue> = emptyList(),
) {
    val fileCount: Int get() = files.size

    val totalBytes: Int
        get() = files.sumOf {
            when (it) {
                is ExportFile.Text -> it.content.length
                is ExportFile.Binary -> (it.base64.length / 4) * 3
            }
        }

    val hasBlockingErrors: Boolean get() = warnings.any { it.severity == Severity.ERROR }

    /** Preview of the folder layout, used by the export dialog. */
    fun tree(): String {
        val sorted = files.map { it.path }.sorted()
        return sorted.joinToString("\n") { path ->
            val depth = path.count { it == '/' }
            "  ".repeat(depth) + path.substringAfterLast('/')
        }
    }
}

/** Shared helpers for the concrete exporters. */
internal object ExportUtil {

    /** JSON string escaping, including the control characters Minecraft rejects. */
    fun escape(text: String): String = buildString(text.length + 8) {
        for (ch in text) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) {
                    append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
    }

    /** `#RRGGBB` ignoring alpha - Java lang files and CSS hex both want this. */
    fun hex(argb: Long): String {
        val rgb = (argb and 0xFFFFFF).toString(16).padStart(6, '0')
        return "#$rgb"
    }

    /** `rgba(r, g, b, a)` for CSS. */
    fun cssRgba(argb: Long): String {
        val a = ((argb shr 24) and 0xFF) / 255.0
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val alpha = ((a * 1000).toInt() / 1000.0)
        return "rgba($r, $g, $b, $alpha)"
    }

    /** Minecraft's `[r, g, b]` float triples used throughout Bedrock JSON UI. */
    fun bedrockColor(argb: Long): String {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        fun f(v: Double) = ((v * 1000).toInt() / 1000.0).toString()
        return "[ ${f(r)}, ${f(g)}, ${f(b)} ]"
    }

    fun bedrockAlpha(argb: Long): Double = (((argb shr 24) and 0xFF) / 255.0 * 1000).toInt() / 1000.0

    /** Java's packed 0xAARRGGBB literal, as written in mod source. */
    fun javaColorLiteral(argb: Long): String =
        "0x" + argb.toString(16).uppercase().padStart(8, '0')

    fun identifier(raw: String): String {
        val cleaned = raw.filter { it.isLetterOrDigit() || it == '_' || it == ' ' }
            .split(' ')
            .filter { it.isNotBlank() }
        if (cleaned.isEmpty()) return "element"
        return cleaned.first().replaceFirstChar { it.lowercase() } +
            cleaned.drop(1).joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }
    }

    fun className(raw: String): String = identifier(raw).replaceFirstChar { it.uppercase() }

    fun cssClass(raw: String): String {
        val slug = raw.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
            .trim('-')
            .replace(Regex("-+"), "-")
        return slug.ifEmpty { "element" }
    }

    fun indent(level: Int): String = "  ".repeat(level)
}
