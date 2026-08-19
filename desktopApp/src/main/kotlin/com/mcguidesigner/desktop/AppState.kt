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
import com.mcguidesigner.desktop.io.DesktopPreferences
import com.mcguidesigner.desktop.io.Workspace
import com.mcguidesigner.exporters.CodeTarget
import com.mcguidesigner.exporters.ExportManager
import com.mcguidesigner.exporters.ExportTarget
import com.mcguidesigner.styles.render.createTextureAsset
import java.awt.Frame
import java.io.File

/** Which auxiliary window is currently open. */
enum class ActiveDialog {
    NONE,
    NEW_PROJECT,
    TEMPLATES,
    EXPORT,
    PROJECT_SETTINGS,
    ABOUT,
    SHORTCUTS,
    WELCOME,
    UNSAVED_CHANGES,
    RECOVERY,
}

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

    // -- Persisted preferences ---------------------------------------------

    /**
     * Last-known preferences.
     *
     * Held rather than re-read because the window geometry is written back on
     * every resize; [persistPreferences] merges the live shell state into it.
     */
    var preferences: DesktopPreferences = DesktopPreferences()
        private set

    /** Applied once at startup, before the first frame is composed. */
    fun applyPreferences(loaded: DesktopPreferences) {
        preferences = loaded
        showLeftDock = loaded.showLeftDock
        showRightDock = loaded.showRightDock
        showBottomDock = loaded.showBottomDock
        recentFiles.clear()
        recentFiles.addAll(loaded.existingRecents())
        loaded.lastDirectory?.let { path ->
            File(path).takeIf { it.isDirectory }?.let { DesktopFileIO.lastDirectory = it }
        }
        loaded.codeTarget?.let { id -> CodeTarget.entries.firstOrNull { it.name == id }?.let { codeTarget = it } }
        loaded.exportTarget?.let { id -> ExportTarget.entries.firstOrNull { it.name == id }?.let { exportTarget = it } }
    }

    /** Folds the current shell state into [preferences] and writes it out. */
    fun persistPreferences(
        windowWidth: Int = preferences.windowWidth,
        windowHeight: Int = preferences.windowHeight,
        windowX: Int = preferences.windowX,
        windowY: Int = preferences.windowY,
        maximized: Boolean = preferences.maximized,
        showWelcomeOnStart: Boolean = preferences.showWelcomeOnStart,
    ) {
        preferences = preferences.copy(
            recentFiles = recentFiles.map { it.absolutePath },
            lastDirectory = DesktopFileIO.lastDirectory.absolutePath,
            windowWidth = windowWidth,
            windowHeight = windowHeight,
            windowX = windowX,
            windowY = windowY,
            maximized = maximized,
            showLeftDock = showLeftDock,
            showRightDock = showRightDock,
            showBottomDock = showBottomDock,
            lastEdition = controller.current.edition.name,
            codeTarget = codeTarget.name,
            exportTarget = exportTarget.name,
            showWelcomeOnStart = showWelcomeOnStart,
        )
        Workspace.savePreferences(preferences)
    }

    val windowTitle: String
        get() = buildString {
            append(controller.current.documentTitle)
            append("  -  ")
            append(controller.current.edition.displayName)
            currentFile?.let { append("  -  ").append(it.name) }
            append("  |  Minecraft GUI Designer")
        }

    // -- Guarding unsaved work ---------------------------------------------

    /**
     * The thing to do once the user has decided what to do about unsaved
     * changes. Held here rather than passed through the dialog so the dialog
     * stays a pure function of [dialog].
     */
    private var pendingAction: (() -> Unit)? = null

    /** Human-readable name of what the pending action will do, for the prompt. */
    var pendingActionLabel by mutableStateOf("continue")
        private set

    /**
     * Runs [action], first offering to save if the document has unsaved edits.
     *
     * Every path that replaces or discards the document goes through here -
     * New, Open, Open Recent, templates and quitting - because silently
     * throwing away someone's work is the fastest way to lose their trust in
     * a design tool.
     */
    fun guardUnsaved(label: String, action: () -> Unit) {
        if (!controller.current.dirty) {
            action()
            return
        }
        pendingActionLabel = label
        pendingAction = action
        dialog = ActiveDialog.UNSAVED_CHANGES
    }

    /** "Save" on the unsaved-changes prompt: save, then continue if it worked. */
    fun resolveUnsavedBySaving() {
        if (!save()) {
            // The save dialog was cancelled or the write failed - stay put
            // rather than discarding the document anyway.
            dialog = ActiveDialog.NONE
            pendingAction = null
            return
        }
        continuePendingAction()
    }

    /** "Discard" on the unsaved-changes prompt. */
    fun resolveUnsavedByDiscarding() {
        Workspace.clearRecovery()
        continuePendingAction()
    }

    /** "Cancel" on the unsaved-changes prompt. */
    fun cancelPendingAction() {
        pendingAction = null
        dialog = ActiveDialog.NONE
    }

    private fun continuePendingAction() {
        val action = pendingAction
        pendingAction = null
        dialog = ActiveDialog.NONE
        action?.invoke()
    }

    // -- Autosave and recovery ---------------------------------------------

    /**
     * Snapshot of the working document, taken on a timer while it is dirty.
     *
     * Cleared on a clean save and on a clean exit, so a snapshot that is still
     * present at startup means the previous session was killed.
     */
    fun autosaveIfDirty() {
        if (!preferences.autosaveEnabled) return
        if (!controller.current.dirty) return
        Workspace.writeRecoverySnapshot(controller.project, currentFile?.absolutePath)
    }

    /** Adopts a document recovered from a previous session's snapshot. */
    fun adoptRecovery(recovery: Workspace.Recovery) {
        controller = EditorController(recovery.project)
        currentFile = recovery.originalFile?.takeIf { it.isFile }
        // Recovered work is by definition unsaved: it was never written to the
        // user's own file, so the editor has to keep asking about it.
        controller.markUnsaved()
        dialog = ActiveDialog.NONE
        status = buildString {
            append("Recovered '${recovery.project.name}' from the previous session.")
            if (currentFile != null) append(" Save to write it back to ${currentFile?.name}.")
        }
    }

    fun discardRecovery() {
        Workspace.clearRecovery()
        dialog = if (preferences.showWelcomeOnStart) ActiveDialog.WELCOME else ActiveDialog.NONE
        status = "Discarded the recovered document."
    }

    // -- Document lifecycle ------------------------------------------------

    fun newProject(edition: Edition, name: String) {
        controller = EditorController(EditorController.newProject(edition, name))
        currentFile = null
        dialog = ActiveDialog.NONE
        Workspace.clearRecovery()
        status = "Created a new ${edition.displayName} screen."
    }

    fun newFromTemplate(templateId: String) {
        val template = BuiltInTemplates[templateId] ?: return
        controller = EditorController(template.instantiate())
        currentFile = null
        dialog = ActiveDialog.NONE
        Workspace.clearRecovery()
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
                dialog = ActiveDialog.NONE
                Workspace.clearRecovery()
                status = buildString {
                    append("Opened ${file.name}.")
                    if (result.warnings.isNotEmpty()) append(" ").append(result.warnings.joinToString(" "))
                }
            }

            is LoadResult.Failure -> {
                // A recent entry pointing at a file that has been moved or
                // deleted should not stay in the menu offering to fail again.
                if (!file.isFile) recentFiles.removeAll { it.absolutePath == file.absolutePath }
                status = "Open failed: ${result.message}"
            }
        }
    }

    /** Returns true when the document was actually written. */
    fun save(): Boolean {
        val file = currentFile ?: return saveAs()
        return DesktopFileIO.writeProject(file, controller.project).fold(
            onSuccess = {
                controller.markSaved(it.absolutePath)
                rememberRecent(it)
                Workspace.clearRecovery()
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
        val updated = Workspace.withRecent(recentFiles.toList(), file)
        recentFiles.clear()
        recentFiles.addAll(updated)
        persistPreferences()
    }

    fun clearRecentFiles() {
        recentFiles.clear()
        persistPreferences()
        status = "Cleared the recent files list."
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
        persistPreferences()
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
