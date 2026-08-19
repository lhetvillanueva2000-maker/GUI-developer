package com.mcguidesigner.android.io

import android.content.Context
import android.net.Uri
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.serialization.LoadResult
import com.mcguidesigner.core.serialization.ProjectSerializer
import java.io.File

/**
 * Keeps the working document alive across process death.
 *
 * Android can and does kill a backgrounded app at any moment. Rotation is
 * already handled by `configChanges` in the manifest, but that does nothing
 * for the case where the user takes a phone call, comes back ten minutes
 * later and finds their design gone. Everything the editor needs to restore
 * itself is written to internal storage whenever the app is backgrounded.
 *
 * Internal storage - not the SAF document - is deliberate: the app must be
 * able to write this without a picker, without permissions, and without
 * touching the user's own file. Re-saving to the real document stays an
 * explicit action.
 */
object SessionStore {

    private const val DOCUMENT = "session.mcgui"
    private const val URI_MARKER = "session-uri.txt"
    private const val NAME_MARKER = "session-name.txt"

    /** Restored session state, as far as it could be recovered. */
    data class Session(
        val project: GuiProject,
        val documentUri: Uri?,
        val documentName: String?,
        val dirty: Boolean,
    )

    private fun directory(context: Context) = File(context.filesDir, "session").apply { mkdirs() }

    /**
     * Writes the current document.
     *
     * [dirty] is recorded alongside it so a restored session knows whether it
     * still owes the user a save - restoring a clean document as dirty would
     * make the save button lie, and the reverse would lose work.
     */
    fun save(context: Context, project: GuiProject, uri: Uri?, name: String?, dirty: Boolean) {
        runCatching {
            val directory = directory(context)
            // Written to a temporary file first: being killed midway through
            // this very write is exactly the scenario the store exists for,
            // and a truncated document would be worse than no document.
            val temporary = File(directory, "$DOCUMENT.tmp")
            temporary.writeText(ProjectSerializer.encode(project))
            val target = File(directory, DOCUMENT)
            if (!temporary.renameTo(target)) {
                target.writeText(temporary.readText())
                temporary.delete()
            }
            File(directory, URI_MARKER).writeText(encodeMarker(dirty, uri?.toString()))
            File(directory, NAME_MARKER).writeText(name.orEmpty())
        }
    }

    /** The document from the previous run, if there is a readable one. */
    fun restore(context: Context): Session? {
        val document = File(directory(context), DOCUMENT).takeIf { it.isFile } ?: return null
        val project = when (val result = runCatching { ProjectSerializer.decode(document.readText()) }.getOrNull()) {
            is LoadResult.Success -> result.project
            else -> {
                clear(context)
                return null
            }
        }

        val marker = decodeMarker(
            runCatching { File(directory(context), URI_MARKER).readText() }.getOrDefault(""),
        )
        val uri = marker.uri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val name = runCatching { File(directory(context), NAME_MARKER).readText() }
            .getOrNull()?.takeIf { it.isNotBlank() }

        return Session(project, uri, name, marker.dirty)
    }

    fun clear(context: Context) {
        runCatching { directory(context).listFiles()?.forEach { it.delete() } }
    }

    // -- Marker encoding ---------------------------------------------------

    /** The non-document half of a session: was it dirty, and where did it come from. */
    internal data class Marker(val dirty: Boolean, val uri: String?)

    /**
     * `"<0|1>\n<uri>"`.
     *
     * Kept as its own pair of pure functions so the format can be tested
     * without an Android runtime. Getting the dirty flag wrong in either
     * direction is silent and costly: false when it should be true loses the
     * user's edits, true when it should be false nags about a saved document.
     */
    internal fun encodeMarker(dirty: Boolean, uri: String?): String =
        (if (dirty) "1" else "0") + "\n" + uri.orEmpty()

    internal fun decodeMarker(text: String): Marker {
        val lines = text.lines()
        return Marker(
            dirty = lines.firstOrNull()?.trim() == "1",
            uri = lines.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
}
