package com.mcguidesigner.android

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mcguidesigner.android.io.AndroidFileIO
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.serialization.LoadResult
import com.mcguidesigner.core.serialization.ProjectSerializer
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.core.util.Ids
import com.mcguidesigner.exporters.CodeTarget
import com.mcguidesigner.exporters.ExportManager
import com.mcguidesigner.exporters.ExportTarget
import com.mcguidesigner.styles.render.createTextureAsset

/** Bottom-navigation destinations. Mobile navigation, not desktop docks. */
enum class MobileSection(val title: String, val glyph: String) {
    DESIGN("Design", "◈"),
    LAYERS("Layers", "≡"),
    PREVIEW("Preview", "▶"),
    CODE("Code", "</>"),
}

/** Which modal bottom sheet is showing, if any. */
enum class MobileSheet { NONE, COMPONENTS, PROPERTIES, TEMPLATES, ASSETS, ISSUES, EXPORT, PROJECT, CANVAS }

/**
 * Shell state for the Android app.
 *
 * Mirrors the desktop `AppState` in responsibility - it owns navigation and
 * file handles, never the document - but models a phone's navigation
 * (one section at a time, modal sheets) instead of a desktop's docks.
 */
class AndroidAppState(initial: GuiProject) {

    var controller by mutableStateOf(EditorController(initial))
        private set

    var documentUri by mutableStateOf<Uri?>(null)
        private set

    var documentName by mutableStateOf<String?>(null)
        private set

    var section by mutableStateOf(MobileSection.DESIGN)
    var sheet by mutableStateOf(MobileSheet.NONE)
    var codeTarget by mutableStateOf(CodeTarget.HTML_CSS)
    var exportTarget by mutableStateOf(ExportTarget.JAVA_RESOURCE_PACK)
    var status by mutableStateOf<String?>(null)

    /** Set while an export is pending a "create document" result from SAF. */
    var pendingExportTarget: ExportTarget? = null

    // -- Documents ---------------------------------------------------------

    fun newProject(edition: Edition, name: String) {
        controller = EditorController(EditorController.newProject(edition, name))
        documentUri = null
        documentName = null
        sheet = MobileSheet.NONE
        section = MobileSection.DESIGN
        status = "New ${edition.displayName} screen."
    }

    fun newFromTemplate(templateId: String) {
        val template = BuiltInTemplates[templateId] ?: return
        controller = EditorController(template.instantiate())
        documentUri = null
        documentName = null
        sheet = MobileSheet.NONE
        section = MobileSection.DESIGN
        status = "Opened '${template.title}'."
    }

    fun openDocument(context: Context, uri: Uri) {
        AndroidFileIO.readText(context, uri).fold(
            onSuccess = { text ->
                when (val result = ProjectSerializer.decode(text)) {
                    is LoadResult.Success -> {
                        controller = EditorController(result.project)
                        documentUri = uri
                        documentName = AndroidFileIO.displayName(context, uri)
                        AndroidFileIO.persistPermission(context, uri, writable = true)
                        status = "Opened $documentName."
                        section = MobileSection.DESIGN
                        sheet = MobileSheet.NONE
                    }

                    is LoadResult.Failure -> status = "Could not open: ${result.message}"
                }
            },
            onFailure = { status = "Could not open: ${it.message}" },
        )
    }

    /** Saves in place when a document Uri is already known. */
    fun saveDocument(context: Context): Boolean {
        val uri = documentUri ?: return false
        AndroidFileIO.writeText(context, uri, ProjectSerializer.encode(controller.project)).fold(
            onSuccess = {
                controller.markSaved(uri.toString())
                status = "Saved ${documentName ?: "project"}."
            },
            onFailure = { status = "Save failed: ${it.message}" },
        )
        return true
    }

    fun onDocumentCreated(context: Context, uri: Uri) {
        documentUri = uri
        documentName = AndroidFileIO.displayName(context, uri)
        AndroidFileIO.persistPermission(context, uri, writable = true)
        saveDocument(context)
    }

    fun suggestedFileName(): String = "${Ids.slug(controller.project.name)}.mcgui"

    // -- Textures ----------------------------------------------------------

    fun importTextures(context: Context, uris: List<Uri>) {
        var imported = 0
        uris.forEach { uri ->
            AndroidFileIO.readBytes(context, uri).fold(
                onSuccess = { bytes ->
                    val asset = createTextureAsset(
                        id = Ids.prefixed("tex"),
                        name = AndroidFileIO.displayName(context, uri),
                        bytes = bytes,
                        sourcePath = uri.toString(),
                    )
                    controller.addTexture(asset)
                    imported++
                },
                onFailure = { status = "Could not import an image: ${it.message}" },
            )
        }
        if (imported > 0) {
            status = "Imported $imported texture(s)."
            sheet = MobileSheet.ASSETS
        }
    }

    // -- Export ------------------------------------------------------------

    fun exportFileName(target: ExportTarget): String {
        val bundle = ExportManager.export(controller.project, target, codeTarget)
        return "${bundle.rootName}.zip"
    }

    fun performExport(context: Context, uri: Uri) {
        val target = pendingExportTarget ?: exportTarget
        pendingExportTarget = null
        val bundle = ExportManager.export(controller.project, target, codeTarget)
        AndroidFileIO.writeBundleZip(context, uri, bundle).fold(
            onSuccess = { count ->
                status = buildString {
                    append("Exported $count file(s).")
                    if (bundle.warnings.isNotEmpty()) append(" ${bundle.warnings.size} warning(s).")
                }
                sheet = MobileSheet.NONE
            },
            onFailure = { status = "Export failed: ${it.message}" },
        )
    }
}
