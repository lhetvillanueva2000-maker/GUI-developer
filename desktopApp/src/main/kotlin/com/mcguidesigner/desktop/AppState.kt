package com.mcguidesigner.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mcguidesigner.core.catalog.CustomPresets
import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorSettings
import com.mcguidesigner.core.library.LibraryTexture
import com.mcguidesigner.core.library.Prefab
import com.mcguidesigner.core.library.PrefabLibrary
import com.mcguidesigner.core.library.TextureLibrary
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.IntPoint
import com.mcguidesigner.core.model.TextureAsset
import com.mcguidesigner.core.packs.PackTexture
import com.mcguidesigner.core.serialization.LoadResult
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.core.util.Ids
import com.mcguidesigner.desktop.io.DesktopFileIO
import com.mcguidesigner.desktop.io.DesktopPreferences
import com.mcguidesigner.desktop.io.LibraryStore
import com.mcguidesigner.desktop.io.PackImport
import com.mcguidesigner.desktop.io.Workspace
import com.mcguidesigner.exporters.CodeTarget
import com.mcguidesigner.exporters.ExportManager
import com.mcguidesigner.exporters.ExportTarget
import com.mcguidesigner.styles.render.createAnimatedTextureFromFrames
import com.mcguidesigner.styles.render.createTextureAsset
import com.mcguidesigner.styles.theme.ThemeMode
import java.awt.Frame
import java.io.File

/**
 * Which of the app's two screens is showing.
 *
 * Home is where the edition is chosen, so it comes before the editor rather
 * than being reachable from inside it. Moving between them never touches the
 * document - the editor is still holding whatever was open - which is what
 * makes going back cheap enough not to need an "are you sure".
 */
enum class AppScreen { HOME, EDITOR }

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
    SAVE_PREFAB,
    COMPONENT_GALLERY,
    PACK_IMPORT,
    APPEARANCE,
    EDITOR_SETTINGS,
    ADD_CUSTOM,
    CONFIRM_DELETE,
}

/** Right-hand dock tab. */
enum class InspectorTab(val title: String) { PROPERTIES("Properties"), ASSETS("Assets"), ISSUES("Issues") }

/** Left-hand dock tab. */
enum class ToolboxTab(val title: String) {
    PALETTE("Palette"),
    PREFABS("Prefabs"),
    LIBRARY("Library"),
    LAYERS("Layers"),
    TEMPLATES("Templates"),
}

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

    /**
     * Whether the what's-new strip is pulled down.
     *
     * Held by the shell rather than by the strip so it survives every
     * recomposition of the editor around it, and so it stays open across a
     * view-mode switch - closing itself the moment you looked at the canvas
     * would make it feel broken.
     */
    var noticeExpanded by mutableStateOf(false)

    // -- Navigation --------------------------------------------------------

    var screen by mutableStateOf(AppScreen.HOME)
        private set

    /**
     * Opens the editor on [edition], converting the open document if it is
     * currently the other one.
     *
     * Converting rather than replacing: someone who opened the app, restored
     * yesterday's session and then tapped the wrong card should not lose it.
     * `switchEdition` already reports anything the target edition cannot
     * express instead of dropping it silently.
     */
    fun openEditor(edition: Edition) {
        if (controller.current.edition != edition) {
            controller.switchEdition(edition)
            status = "Now designing for ${edition.displayName}. " +
                "The palette, the skin and the export format all followed."
        }
        screen = AppScreen.EDITOR
        persistPreferences()
    }

    /** Back to the picker. The document stays loaded exactly as it is. */
    fun goHome() {
        screen = AppScreen.HOME
    }

    /**
     * The line above home's heading.
     *
     * "New screen" is a promise, and it is only true on a fresh launch. Once
     * there is a file open or an edit to lose, saying it anyway would suggest
     * the cards are about to throw that away - which they are not.
     */
    val homeEyebrow: String
        get() = when {
            currentFile != null -> "CONTINUE  ·  ${currentFile?.name}"
            controller.current.dirty -> "CONTINUE  ·  UNSAVED CHANGES"
            else -> "NEW SCREEN"
        }

    /**
     * Writes the donation QR wherever the user points the save dialog.
     *
     * The bytes are the file that shipped, copied straight through: the code
     * someone saves is byte-for-byte the code the app displays.
     */
    fun saveDonationQr(bytes: ByteArray) {
        val target = DesktopFileIO.saveFileDialog(
            owner = frameProvider(),
            title = "Save the donation QR code",
            suggestedName = com.mcguidesigner.core.support.Donation.QR_FILE_NAME,
        ) ?: return
        runCatching { target.writeBytes(bytes) }.fold(
            onSuccess = { status = "QR code saved to ${target.name}." },
            onFailure = { status = "Could not save the QR code: ${it.message}" },
        )
    }

    val recentFiles = mutableStateListOf<File>()

    // -- Appearance --------------------------------------------------------

    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    var backdropEnabled by mutableStateOf(true)
    var backdropMotion by mutableStateOf(true)

    /**
     * Whether the chrome is currently painted dark.
     *
     * Desktop toolkits have no portable "is the OS in dark mode" answer, so
     * [ThemeMode.SYSTEM] resolves to dark here - which is what this app has
     * always looked like, and therefore the least surprising default.
     */
    val darkChrome: Boolean get() = themeMode.isDark(systemIsDark = true)

    fun cycleTheme() {
        themeMode = when (themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
        persistPreferences()
        status = "Theme: ${themeMode.displayName}."
    }

    fun setTheme(mode: ThemeMode) {
        themeMode = mode
        persistPreferences()
    }

    fun setBackdrop(enabled: Boolean, motion: Boolean = backdropMotion) {
        backdropEnabled = enabled
        backdropMotion = motion
        persistPreferences()
    }

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
        controller.setSettings(loaded.editorSettings)
        loaded.codeTarget?.let { id -> CodeTarget.entries.firstOrNull { it.name == id }?.let { codeTarget = it } }
        loaded.exportTarget?.let { id -> ExportTarget.entries.firstOrNull { it.name == id }?.let { exportTarget = it } }
        themeMode = loaded.theme
        backdropEnabled = loaded.backdropEnabled
        backdropMotion = loaded.backdropMotion
    }

    /** Reads the prefab and texture libraries. Called once, before the first frame. */
    fun loadLibraries() {
        prefabs = LibraryStore.loadPrefabs()
        textureLibrary = LibraryStore.loadTextures()
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
            themeMode = themeMode.name,
            backdropEnabled = backdropEnabled,
            backdropMotion = backdropMotion,
            editorSettings = controller.current.settings,
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
        // Past the picker: whatever was being worked on already chose an
        // edition, and being asked to choose again before getting it back
        // would be a strange way to hand someone their work.
        screen = AppScreen.EDITOR
        controller = freshController(recovery.project)
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
        // Straight to home rather than on to another dialog: the recovery
        // prompt was covering the picker, and dismissing it should reveal it.
        dialog = ActiveDialog.NONE
        status = "Discarded the recovered document."
    }

    // -- Document lifecycle ------------------------------------------------

    fun newProject(edition: Edition, name: String) {
        controller = freshController(EditorController.newProject(edition, name))
        currentFile = null
        dialog = ActiveDialog.NONE
        Workspace.clearRecovery()
        status = "Created a new ${edition.displayName} screen."
    }

    fun newFromTemplate(templateId: String) {
        val template = BuiltInTemplates[templateId] ?: return
        controller = freshController(template.instantiate())
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
                controller = freshController(result.project)
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

    /**
     * A controller for [project] that keeps the user's editor settings.
     *
     * Opening a document replaces the whole controller, and settings live in
     * the editor state - so without this, choosing a 4px move step would last
     * exactly until the next File > Open.
     */
    private fun freshController(project: GuiProject) =
        EditorController(project).apply { setSettings(controller.current.settings) }

    // -- Deleting ----------------------------------------------------------

    /**
     * Deletes the selection, asking first when the user has said to ask.
     *
     * Every delete path - the key, the menu, the toolbar - comes through here
     * so the setting cannot be honoured in some places and skipped in others.
     */
    fun requestDeleteSelection() {
        if (!controller.current.hasSelection) return
        if (controller.current.settings.confirmBeforeDelete) {
            dialog = ActiveDialog.CONFIRM_DELETE
        } else {
            controller.deleteSelection()
        }
    }

    fun confirmDeleteSelection() {
        controller.deleteSelection()
        dialog = ActiveDialog.NONE
    }

    // -- Editor settings ---------------------------------------------------

    /** Applies [settings] to the live editor and writes them to disk. */
    fun applyEditorSettings(settings: EditorSettings) {
        controller.setSettings(settings)
        persistPreferences()
    }

    // -- Adding custom content ---------------------------------------------

    /**
     * Drops a preset onto the middle of the canvas and selects it.
     *
     * The centre rather than the origin because that is where the user is
     * looking, and selected because the next thing anyone does after adding a
     * shape is resize or recolour it.
     */
    fun addCustomPreset(preset: CustomPresets.Preset) {
        val canvas = controller.project.canvas
        val added = controller.addElement(
            typeId = preset.typeId,
            at = IntPoint(canvas.width / 2, canvas.height / 2),
            centreOnPoint = true,
            initialProps = preset.props,
            nameHint = preset.label,
            sizeOverride = preset.size,
        )
        status = if (added == null) {
            "Could not add ${preset.label}."
        } else {
            // Anything that needs a texture is useless until it has one, so the
            // status line says what to do next rather than just "added".
            when (preset.typeId) {
                ElementCatalog.IMAGE_ANIMATED ->
                    "Added ${preset.label}. Import a GIF (File > Import Textures) and pick it " +
                        "as the frame strip in the Properties panel."

                ElementCatalog.IMAGE_PLACEHOLDER ->
                    "Added ${preset.label}. Choose a texture for it in the Properties panel."

                else -> "Added ${preset.label}. Resize and restyle it in the Properties panel."
            }
        }
    }

    // -- Textures ----------------------------------------------------------

    /**
     * Builds one animated texture from several image files.
     *
     * The route in for anything that is not a GIF - frames exported from a
     * video, a sequence rendered elsewhere. Files are taken in name order,
     * which is how frame sequences are always numbered.
     */
    fun importAnimationFrames() {
        val files = DesktopFileIO.importImageDialog(frameProvider())
            .sortedBy { it.name.lowercase() }
        if (files.isEmpty()) return
        if (files.size < 2) {
            status = "Pick at least two images - one frame is a still, so import it as one."
            return
        }

        val asset = runCatching {
            createAnimatedTextureFromFrames(
                id = Ids.prefixed("tex"),
                // The shared stem of "walk_01.png".."walk_12.png" names the
                // animation far better than the first file does.
                name = commonFrameName(files.map { it.name }),
                frameFiles = files.map { it.readBytes() },
                sourcePath = files.first().parentFile?.absolutePath,
            )
        }.getOrNull()

        if (asset == null) {
            status = "Could not read those images as an animation."
            return
        }

        controller.addTextures(listOf(asset))
        rememberInLibrary(listOf(asset), source = "Animation")
        inspectorTab = InspectorTab.ASSETS
        status = "Built a ${asset.frameCount}-frame animation from ${files.size} images. " +
            "Add an Animated image element and pick '${asset.name}' as its frame strip."
    }

    /**
     * The shared prefix of a numbered frame sequence.
     *
     * `walk_01.png`, `walk_02.png` -> `walk`. Falls back to the first name
     * when the files share nothing, which is the best guess available.
     */
    private fun commonFrameName(names: List<String>): String {
        val stems = names.map { it.substringBeforeLast('.') }
        val first = stems.first()
        var shared = first.length
        stems.drop(1).forEach { other ->
            shared = minOf(shared, other.commonPrefixWith(first).length)
        }
        return first.take(shared).trimEnd('_', '-', ' ', '.').ifBlank { first }
    }

    fun importTextures() {
        val files = DesktopFileIO.importImageDialog(frameProvider())
        if (files.isEmpty()) return
        val assets = mutableListOf<TextureAsset>()
        files.forEach { file ->
            runCatching {
                assets += createTextureAsset(
                    id = Ids.prefixed("tex"),
                    name = file.name,
                    bytes = file.readBytes(),
                    sourcePath = file.absolutePath,
                )
            }.onFailure { status = "Could not import ${file.name}: ${it.message}" }
        }
        if (assets.isEmpty()) return

        val imported = controller.addTextures(assets)
        // Anything imported by hand also joins the library, so the next project
        // does not have to go looking for the same file again.
        rememberInLibrary(assets, source = "Imported")
        inspectorTab = InspectorTab.ASSETS
        status = "Imported $imported texture(s). They are in your library too."
    }

    // -- Prefabs -----------------------------------------------------------

    var prefabs by mutableStateOf(PrefabLibrary.Empty)
        private set

    /** Pre-filled name for the "save prefab" dialog. */
    var prefabDraftName by mutableStateOf("")

    /** Opens the save dialog with a sensible name already in the field. */
    fun beginSavePrefab() {
        val selected = controller.current.selectedElements
        if (selected.isEmpty()) {
            status = "Select something first - a prefab is a group of elements."
            return
        }
        prefabDraftName = when (selected.size) {
            1 -> selected.first().name
            else -> "${selected.first().name} group"
        }
        dialog = ActiveDialog.SAVE_PREFAB
    }

    fun savePrefab(name: String, description: String, tags: List<String>) {
        val prefab = controller.prefabFromSelection(
            name = name,
            description = description,
            tags = tags,
            createdAtMillis = System.currentTimeMillis(),
        )
        if (prefab == null) {
            status = "Nothing to save - the selection is empty."
            dialog = ActiveDialog.NONE
            return
        }
        prefabs = prefabs.with(prefab)
        val written = LibraryStore.savePrefabs(prefabs)
        dialog = ActiveDialog.NONE
        toolboxTab = ToolboxTab.PREFABS
        status = if (written) {
            "Saved '${prefab.name}' (${prefab.elementCount} element(s)) to your prefabs."
        } else {
            "Saved '${prefab.name}' for this session, but it could not be written to disk."
        }
    }

    /** Drops a prefab into the middle of the canvas. */
    fun insertPrefab(prefab: Prefab) {
        val canvas = controller.project.canvas
        val inserted = controller.insertPrefab(
            prefab = prefab,
            at = IntPoint(canvas.width / 2, canvas.height / 2),
            centreOnPoint = true,
        )
        status = if (inserted.isEmpty()) {
            "'${prefab.name}' is empty."
        } else {
            buildString {
                append("Inserted '${prefab.name}'.")
                if (prefab.edition != controller.current.edition) {
                    append(" It was built for ${prefab.edition.displayName} - check the Issues tab.")
                }
            }
        }
    }

    fun deletePrefab(id: String) {
        val name = prefabs[id]?.name
        prefabs = prefabs.without(id)
        LibraryStore.savePrefabs(prefabs)
        status = name?.let { "Deleted prefab '$it'." } ?: "Deleted prefab."
    }

    fun renamePrefab(id: String, name: String) {
        prefabs = prefabs.renamed(id, name)
        LibraryStore.savePrefabs(prefabs)
    }

    // -- Texture library ---------------------------------------------------

    var textureLibrary by mutableStateOf(TextureLibrary.Empty)
        private set

    /** Adds assets to the cross-project library, skipping ones already stored. */
    fun rememberInLibrary(assets: List<TextureAsset>, source: String): Int {
        if (assets.isEmpty()) return 0
        val before = textureLibrary.size
        textureLibrary = textureLibrary.withAll(
            assets.map {
                LibraryTexture(asset = it, source = source, addedAtMillis = System.currentTimeMillis())
            },
        )
        LibraryStore.saveTextures(textureLibrary)
        return textureLibrary.size - before
    }

    /** Copies a library texture into the open project. */
    fun useLibraryTexture(entry: LibraryTexture) {
        val fresh = entry.asset.copy(id = Ids.prefixed("tex"))
        controller.addTexture(fresh)
        inspectorTab = InspectorTab.ASSETS
        status = "Added '${fresh.name}' to this project."
    }

    fun forgetLibraryTexture(id: String) {
        textureLibrary = textureLibrary.without(id)
        LibraryStore.saveTextures(textureLibrary)
        status = "Removed it from the library. Projects already using it keep their copy."
    }

    /** Puts every texture in the open project into the library. */
    fun rememberProjectTextures() {
        val added = rememberInLibrary(controller.project.textures, source = controller.project.name)
        status = when {
            controller.project.textures.isEmpty() -> "This project has no textures yet."
            added == 0 -> "Every texture in this project was already in the library."
            else -> "Added $added texture(s) to the library."
        }
    }

    // -- Resource packs ----------------------------------------------------

    /** The archive currently open in the pack-import dialog. */
    var openedPack by mutableStateOf<PackImport.OpenedPack?>(null)
        private set

    fun browsePack() {
        val file = DesktopFileIO.openPackDialog(frameProvider()) ?: return
        PackImport.open(file).fold(
            onSuccess = { pack ->
                if (pack.scan.isEmpty) {
                    status = "${file.name} contains no images the designer can read."
                    return
                }
                openedPack = pack
                dialog = ActiveDialog.PACK_IMPORT
                status = "${pack.name}: ${pack.scan.kind.displayName}, ${pack.scan.textures.size} texture(s)."
            },
            onFailure = { status = "Could not read ${file.name}: ${it.message}" },
        )
    }

    fun closePack() {
        openedPack = null
        dialog = ActiveDialog.NONE
    }

    /**
     * Reads the chosen entries and puts them in both the library and, when
     * asked, the open project.
     */
    fun importFromPack(selected: List<PackTexture>, intoProject: Boolean) {
        val pack = openedPack ?: return
        val assets = PackImport.read(pack, selected)
        if (assets.isEmpty()) {
            status = "None of the selected files could be decoded."
            return
        }
        val added = rememberInLibrary(assets, source = pack.name)
        if (intoProject) {
            // Fresh ids: the same pack texture may legitimately be imported
            // into several projects, and each document owns its own copy.
            controller.addTextures(assets.map { it.copy(id = Ids.prefixed("tex")) })
            inspectorTab = InspectorTab.ASSETS
        }
        openedPack = null
        dialog = ActiveDialog.NONE
        toolboxTab = if (intoProject) toolboxTab else ToolboxTab.LIBRARY
        status = buildString {
            append("Imported ${assets.size} texture(s) from ${pack.name}.")
            if (added < assets.size) append(" ${assets.size - added} were already in the library.")
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
