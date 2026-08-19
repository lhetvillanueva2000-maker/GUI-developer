package com.mcguidesigner.core.serialization

import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.PROJECT_FORMAT_VERSION
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Canonical file extension of a design document. */
const val PROJECT_EXTENSION = "mcgui"

/** Outcome of reading a `.mcgui` document. */
sealed interface LoadResult {
    data class Success(val project: GuiProject, val warnings: List<String> = emptyList()) : LoadResult
    data class Failure(val message: String, val cause: Throwable? = null) : LoadResult
}

/**
 * Reads and writes `.mcgui` documents.
 *
 * The format is plain JSON so projects diff well in git and can be generated
 * by external tooling.  [migrate] gives us a place to upgrade older documents
 * without ever failing the load outright.
 */
@OptIn(ExperimentalSerializationApi::class)
object ProjectSerializer {

    /** Human-readable output - projects are meant to be committed to git. */
    val pretty: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
        explicitNulls = false
    }

    /** Compact output for clipboard payloads and undo snapshots. */
    val compact: Json = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
        explicitNulls = false
    }

    fun encode(project: GuiProject, prettyPrint: Boolean = true): String =
        (if (prettyPrint) pretty else compact).encodeToString(GuiProject.serializer(), project)

    fun decode(text: String): LoadResult = try {
        val warnings = mutableListOf<String>()
        val version = peekFormatVersion(text)
        if (version != null && version > PROJECT_FORMAT_VERSION) {
            warnings += "This project was saved by a newer version of the designer " +
                "(format $version, this build understands $PROJECT_FORMAT_VERSION). " +
                "Unknown fields were dropped."
        }
        val raw = pretty.decodeFromString(GuiProject.serializer(), text)
        LoadResult.Success(migrate(raw), warnings)
    } catch (t: Throwable) {
        LoadResult.Failure(t.message ?: "Could not parse project file.", t)
    }

    /** Reads only the format version, without committing to a full parse. */
    fun peekFormatVersion(text: String): Int? = runCatching {
        (pretty.parseToJsonElement(text) as? JsonObject)
            ?.get("formatVersion")?.jsonPrimitive?.content?.toIntOrNull()
    }.getOrNull()

    /**
     * Brings older documents up to the current format.  Today every field has
     * a default so v0 documents load unchanged; the hook exists so future
     * format changes stay backwards compatible.
     */
    fun migrate(project: GuiProject): GuiProject =
        if (project.formatVersion >= PROJECT_FORMAT_VERSION) {
            project
        } else {
            project.copy(formatVersion = PROJECT_FORMAT_VERSION)
        }

    /** Deep copy via a serialization round-trip - used by duplicate/paste. */
    fun clone(project: GuiProject): GuiProject =
        compact.decodeFromString(GuiProject.serializer(), encode(project, prettyPrint = false))
}
