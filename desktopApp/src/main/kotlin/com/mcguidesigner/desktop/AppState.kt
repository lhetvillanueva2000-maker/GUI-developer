package com.mcguidesigner.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.serialization.LoadResult
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.core.util.Ids
import com.mcguidesigner.desktop.io.DesktopFileIO
import com.mcguidesigner.exporters.CodeTarget
import com.mcguidesigner.exporters.ExportManager
import com.mcguidesigner.exporters.ExportTarget
import com.mcguidesigner.styles.render.createTextureAsset
import java.awt.Frame
import java.io.File

/** Which auxiliary window is currently open. */
enum class ActiveDialog { NONE, NEW_PROJECT, TEMPLATES, EXPORT, PROJECT_SETTINGS, ABOUT, SHORTCUTS }

/** Right-hand dock tab. */
enum class InspectorTab(val title: String) { PROPERTIES("Properties"), ASSETS("Assets"), ISSUES("Issues") }

/** Left-hand dock tab. */
enum class ToolboxTab(val title: String) { PALETTE("Palette"), LAYERS("Layers"), TEMPLATES("Templates") }

/**
 * Everything the desktop shell owns on top of the shared editor: which
 * dialogs are open, which docks are showing, the current file, and the
 * transient status line.
 *
 * The document itself lives entirely in [controller]; this class never
 * duplicates editor state.
 */
class AppState(initial: GuiProject) {

    var controller by mutableStateOf(EditorController(initial))
        private set

    var currentFile by mutableStateOf<File?>(null)
        private set

    var dialog by mutableStateOf(ActiveDialog.NONE)
    var inspectorTab by mutableStateOf(InspectorTab.PROPERTIES)
    var toolboxTab by mutableStateOf(ToolboxTab.PALETTE)
    var codeTarget by mutableStateOf(CodeTarget.HTML_CSS)
    var exportTarget by mutableStateOf(ExportTarget.JAVA_RESOURCE_PACK)
    var showLeftDock by mutableStateOf(true)
    var showRightDock by mutableStateOf(true)
    var showBottomDock by mutableStateOf(false)
    var status by mutableStateOf("Ready")
    var pendingExit by mutableStateOf(false)

    val recentFiles = mutableStateListOf<File>()

    /** Provider for the AWT frame the file dialogs should parent to. */
    var frameProvider: () -> Frame? = { null }

    val windowTitle: String
        get() = buildString {
            append(controller.current.documentTitle)
            append("  -  ")
            append(controller.current.edition.displayName)
            currentFile?.let { append("  -  ").append(it.name) }
            append("  |  Minecraft GUI Designer")
        }

    // -- Document lifecycle ------------------------------------------------

    fun newProject(edition: Edition, name: String) {
        controller = EditorController(EditorController.newProject(edition, name))
        currentFile = null
        dialog = ActiveDialog.NONE
        status = "Created a new ${edition.displayName} screen."
    }

    fun newFromTemplate(templateId: String) {
        val template = BuiltInTemplates[templateId] ?: return
        controller = EditorController(template.instantiate())
        currentFile = null
        dialog = ActiveDialog.NONE
        status = "Loaded template '${template.title}'."
    }

    fun open() {
        val file = DesktopFileIO.openProjectDialog(frameProvider()) ?: return
        openFile(file)
    }

    fun openFile(file: File) {
        when (val result = DesktopFileIO.readProject(file)) {
            is LoadResult.Success -> {
                controller = EditorController(result.project)
                currentFile = file
                rememberRecent(file)
                status = buildString {
                    append("Opened ${file.name}.")
                    if (result.warnings.isNotEmpty()) append(" ").append(result.warnings.joinToString(" "))
                }
            }

            is LoadResult.Failure -> status = "Open failed: ${result.message}"
        }
    }

    /** Returns true when the document was actually written. */
    fun save(): Boolean {
        val file = currentFile ?: return saveAs()
        return DesktopFileIO.writeProject(file, controller.project).fold(
            onSuccess = {
                controller.markSaved(it.absolutePath)
                rememberRecent(it)
                status = "Saved ${it.name}."
                true
            },
            onFailure = {
                status = "Save failed: ${it.message}"
                false
            },
        )
    }

    fun saveAs(): Boolean {
        val suggested = Ids.slug(controller.project.name)
        val file = DesktopFileIO.saveProjectDialog(frameProvider(), suggested) ?: return false
        currentFile = file
        return save()
    }

    private fun rememberRecent(file: File) {
        recentFiles.remove(file)
        recentFiles.add(0, file)
        while (recentFiles.size > 8) recentFiles.removeLast()
    }

    // -- Textures ----------------------------------------------------------

    fun importTextures() {
        val files = DesktopFileIO.importImageDialog(frameProvider())
        if (files.isEmpty()) return
        var imported = 0
        files.forEach { file ->
            runCatching {
                val asset = createTextureAsset(
                    id = Ids.prefixed("tex"),
                    name = file.name,
                    bytes = file.readBytes(),
                    sourcePath = file.absolutePath,
                )
                controller.addTexture(asset)
                imported++
            }.onFailure { status = "Could not import ${file.name}: ${it.message}" }
        }
        if (imported > 0) {
            inspectorTab = InspectorTab.ASSETS
            status = "Imported $imported texture(s)."
        }
    }

    // -- Export ------------------------------------------------------------

    fun runExport(target: ExportTarget, asZip: Boolean) {
        val bundle = ExportManager.export(controller.project, target, codeTarget)
        val frame = frameProvider()
        val result = if (asZip) {
            val file = DesktopFileIO.saveFileDialog(frame, "Export as ZIP", "${bundle.rootName}.zip") ?: return
            DesktopFileIO.writeBundleAsZip(bundle, file)
        } else {
            val directory = DesktopFileIO.chooseDirectoryDialog(frame, "Choose export folder") ?: return
            DesktopFileIO.writeBundle(bundle, directory)
        }
        status = result.fold(
            onSuccess = { path ->
                val warnings = bundle.warnings.size
                buildString {
                    append("Exported ${bundle.fileCount} file(s) to ${path.name}.")
                    if (warnings > 0) append(" $warnings warning(s) - see the Issues tab.")
                }
            },
            onFailure = { "Export failed: ${it.message}" },
        )
        dialog = ActiveDialog.NONE
    }

    /** Saves the current Code tab's output to a single file. */
    fun saveGeneratedCode(fileName: String, source: String) {
        val file = DesktopFileIO.saveFileDialog(frameProvider(), "Save generated code", fileName) ?: return
        status = DesktopFileIO.writeText(file, source).fold(
            onSuccess = { "Wrote ${it.name}." },
            onFailure = { "Could not write file: ${it.message}" },
        )
    }
}
