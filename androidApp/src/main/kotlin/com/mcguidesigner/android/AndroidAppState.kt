package com.mcguidesigner.android

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mcguidesigner.android.io.AndroidFileIO
import com.mcguidesigner.android.io.AndroidPackImport
import com.mcguidesigner.android.io.AndroidPreferences
import com.mcguidesigner.core.catalog.CustomPresets
import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.editor.EditorSettings
import com.mcguidesigner.core.image.ImageSize
import com.mcguidesigner.core.image.ImageBackground
import com.mcguidesigner.android.io.LibraryStore
import com.mcguidesigner.android.io.SessionStore
import com.mcguidesigner.core.editor.DocumentSet
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.library.LibraryTexture
import com.mcguidesigner.core.library.Prefab
import com.mcguidesigner.core.library.PrefabLibrary
import com.mcguidesigner.core.library.TextureLibrary
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.IntPoint
import com.mcguidesigner.core.model.TextureAsset
import com.mcguidesigner.core.packs.PackTexture
import com.mcguidesigner.core.serialization.ProjectSerializer
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.core.util.Ids
import com.mcguidesigner.exporters.CodeTarget
import com.mcguidesigner.exporters.DesignImporter
import com.mcguidesigner.exporters.ExportManager
import com.mcguidesigner.exporters.ImportFormat
import com.mcguidesigner.exporters.ImportOutcome
import com.mcguidesigner.exporters.ExportTarget
import com.mcguidesigner.styles.render.createAnimatedTextureFromFrames
import com.mcguidesigner.styles.render.createTextureAsset
import com.mcguidesigner.styles.home.HomeOverlay
import com.mcguidesigner.styles.notice.AppNotice
import com.mcguidesigner.styles.notice.Notice
import com.mcguidesigner.styles.notice.Notices
import com.mcguidesigner.styles.settings.AppearanceSettings
import com.mcguidesigner.styles.theme.MotionLevel
import com.mcguidesigner.styles.theme.ThemeMode

/**
 * Which of the app's two screens is showing.
 *
 * Home is where the edition is chosen, so it comes before the editor rather
 * than being reachable from inside it. Moving between them never touches the
 * document, which is what lets the back gesture reach home without an "are
 * you sure" in the way.
 */
enum class AppScreen { HOME, EDITOR }

/** Bottom-navigation destinations. Mobile navigation, not desktop docks. */
enum class MobileSection(val title: String, val glyph: String) {
    DESIGN("Design", "◈"),
    LAYERS("Layers", "≡"),
    PREVIEW("Preview", "▶"),
    CODE("Code", "</>"),
}

/** Which modal bottom sheet is showing, if any. */
enum class MobileSheet {
    NONE,
    COMPONENTS,
    PROPERTIES,
    TEMPLATES,
    ASSETS,
    ISSUES,
    EXPORT,
    IMAGE_EXPORT,
    IMPORT_PREVIEW,
    PROJECT,
    CANVAS,
    PREFABS,
    LIBRARY,
    GALLERY,
    PACK_IMPORT,
    APPEARANCE,
    ARRANGE,
    ADD_CUSTOM,
    EDITOR_SETTINGS,
    CONFIRM_DELETE,
}

/**
 * Shell state for the Android app.
 *
 * Mirrors the desktop `AppState` in responsibility - it owns navigation and
 * file handles, never the document - but models a phone's navigation
 * (one section at a time, modal sheets) instead of a desktop's docks.
 */
class AndroidAppState(initial: GuiProject) {

    // -- Open documents ----------------------------------------------------

    /**
     * Every open document, in tab order.
     *
     * Switching edition used to *convert* the open document, so opening
     * Bedrock while a Java screen was on the canvas rewrote that screen. Java
     * and Bedrock are different enough that having both on the go is the
     * normal case.
     */
    val tabs = mutableStateListOf(AndroidDocumentTab(initial))

    var activeTab by mutableStateOf(0)
        private set

    val active: AndroidDocumentTab
        get() = tabs[activeTab.coerceIn(0, tabs.lastIndex)]

    /**
     * The front document's controller, under the name and shape it had when
     * there was only ever one - so every existing call site follows the front
     * tab without being touched.
     */
    var controller: EditorController
        get() = active.controller
        private set(value) { active.controller = value }

    var documentUri: Uri?
        get() = active.uri
        private set(value) { active.uri = value }

    var documentName: String?
        get() = active.name
        private set(value) { active.name = value }

    fun selectTab(index: Int) {
        if (index in tabs.indices) activeTab = index
    }

    /** Brings [edition]'s tab forward, or opens one if there is not one yet. */
    fun openTabFor(edition: Edition) {
        DocumentSet.existingTabFor(tabs.map { it.edition }, edition)
            ?.let { activeTab = it; return }
        tabs.add(AndroidDocumentTab(DocumentSet.newDocument(edition, tabs.map { it.title })))
        activeTab = tabs.lastIndex
    }

    /** A blank document for [edition], even if one is already open. */
    fun addTab(edition: Edition) {
        tabs.add(AndroidDocumentTab(DocumentSet.newDocument(edition, tabs.map { it.title })))
        activeTab = tabs.lastIndex
        status = "New ${edition.displayName} screen."
    }

    /**
     * Closes a tab. The last one goes home rather than leaving the editor with
     * nothing to show.
     */
    fun closeTab(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        val close = {
            if (tabs.size == 1) {
                goHome()
            } else {
                val next = DocumentSet.activeAfterClose(activeTab, index, tabs.size)
                tabs.removeAt(index)
                activeTab = next
            }
        }
        if (tab.dirty) guardUnsaved("close ${tab.title}", close) else close()
    }

    var screen by mutableStateOf(AppScreen.HOME)
        private set

    /**
     * Whether the what's-new strip is pulled down.
     *
     * Held here rather than inside the strip so it survives every
     * recomposition of the editor around it, and so a rotation does not close
     * something the reader had deliberately opened.
     */
    var noticeExpanded by mutableStateOf(false)

    var section by mutableStateOf(MobileSection.DESIGN)
    var sheet by mutableStateOf(MobileSheet.NONE)
    var codeTarget by mutableStateOf(CodeTarget.HTML_CSS)
    var exportTarget by mutableStateOf(ExportTarget.JAVA_RESOURCE_PACK)
    var status by mutableStateOf<String?>(null)

    /** Set while an export is pending a "create document" result from SAF. */
    var pendingExportTarget: ExportTarget? = null

    /**
     * An import that has been read but not yet accepted.
     *
     * Held rather than applied because these readers translate between formats
     * that disagree about what a layout is, and the caveats matter *before* the
     * decision rather than as a notification afterwards.
     */
    var pendingImport by mutableStateOf<ImportOutcome?>(null)
        private set

    /**
     * What was asked for, and where it is going, while the picker is open.
     *
     * Deliberately *not* the pixels. The first version of this held a rendered
     * PNG here while the storage-access picker was the foreground activity, and
     * Android destroys a backgrounded activity under memory pressure - which
     * took the bytes with it. The document had already been created by then, so
     * what landed on disk was a zero-byte file and no error at all. These two
     * are a handful of bytes and survive being recreated; even if they did not,
     * losing them leaves nothing written rather than something empty.
     */
    var pendingImageSize by mutableStateOf<ImageSize?>(null)
        private set

    var pendingImageBackground by mutableStateOf(ImageBackground.DEFAULT)
        private set

    /** The document the picker created, once it exists. */
    var pendingImageUri by mutableStateOf<Uri?>(null)
        private set

    // -- Navigation --------------------------------------------------------

    /**
     * Opens the editor on [edition], converting the open document if it is
     * currently the other one.
     *
     * Converting rather than replacing: someone who opened the app, had
     * yesterday's session restored and then tapped the wrong card should not
     * lose it. `switchEdition` reports anything the target edition cannot
     * express instead of dropping it silently.
     */
    fun openEditor(edition: Edition) {
        // A tab, not a conversion - see [tabs].
        openTabFor(edition)
        screen = AppScreen.EDITOR
    }

    /**
     * Straight into the editor, without going through the picker.
     *
     * For a session restored after a process kill: the edition was chosen
     * before the app was killed, and asking again on the way back would look
     * like the work had been lost.
     */
    fun resumeEditor() {
        screen = AppScreen.EDITOR
    }

    /** Back to the picker. The document stays loaded exactly as it is. */
    fun goHome() {
        sheet = MobileSheet.NONE
        screen = AppScreen.HOME
    }

    /**
     * The line above home's heading.
     *
     * "New screen" is only true on a fresh launch; saying it over a document
     * someone has already worked on would suggest the cards are about to throw
     * that away, which they are not.
     */
    val homeEyebrow: String
        get() = when {
            documentName != null -> "CONTINUE  ·  ${documentName}"
            controller.current.dirty -> "CONTINUE  ·  UNSAVED CHANGES"
            else -> "NEW SCREEN"
        }

    // -- Support page ------------------------------------------------------

    /** The QR bytes waiting on a "create document" result from SAF. */
    var pendingQrBytes: ByteArray? = null

    fun saveQrCode(context: Context, uri: Uri) {
        val bytes = pendingQrBytes
        pendingQrBytes = null
        if (bytes == null) {
            status = "Nothing to save - try holding the QR code again."
            return
        }
        AndroidFileIO.writeBytes(context, uri, bytes).fold(
            onSuccess = { status = "QR code saved." },
            onFailure = { status = "Could not save the QR code: ${it.message}" },
        )
    }

    // -- Appearance --------------------------------------------------------

    /**
     * Everything the settings screen owns, in one object.
     *
     * The accessors below read this rather than shadowing it, so the settings
     * screen and the editor's older appearance sheet cannot end up holding two
     * different opinions about the theme.
     */
    var appearance by mutableStateOf(AppearanceSettings())
        private set

    val themeMode: ThemeMode get() = appearance.theme
    val backdropEnabled: Boolean get() = appearance.backdropEnabled
    val backdropMotion: Boolean get() = appearance.backdropMoves

    /**
     * Which full-screen page is open over home.
     *
     * Owned here so the system back gesture can close it - a `BackHandler` can
     * only live in the shell, and state kept inside `HomeScreen` would leave
     * back either doing nothing or dropping straight out of the app.
     */
    var homeOverlay by mutableStateOf(HomeOverlay.NONE)

    fun applySettings(settings: AndroidPreferences.Settings) {
        appearance = settings.appearance
        dismissedNoticeId = settings.dismissedNoticeId
        controller.setSettings(settings.editor)
        if (AppNotice.isUnread(dismissedNoticeId)) postNotice(AppNotice.current)
    }

    private fun settings() = AndroidPreferences.Settings(
        appearance = appearance,
        dismissedNoticeId = dismissedNoticeId,
        editor = controller.current.settings,
    )

    /**
     * Hands a link to whatever app the device uses for one.
     *
     * `NEW_TASK` because the launcher may be started from a context that is not
     * an Activity, and wrapped because a device with no browser at all - a
     * stripped ROM, a locked-down work profile - throws rather than no-opping.
     */
    fun openLink(context: Context, url: String) {
        if (url.isBlank()) return
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(url),
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

        status = runCatching { context.startActivity(intent); "Opened $url" }
            .getOrElse { "Nothing on this device can open a link. It is $url" }
    }

    // -- Notifications -----------------------------------------------------

    /**
     * What the notification panel is showing. Empty means there is no panel.
     *
     * This is also where transient messages land now. They used to float up
     * from the bottom of the screen as a snackbar while the panel sat at the
     * top saying something else - two notification systems disagreeing in two
     * corners. One panel, one place to look.
     */
    var notices by mutableStateOf(emptyList<Notice>())
        private set

    private var dismissedNoticeId: String? = null

    fun postNotice(notice: Notice) {
        notices = Notices.post(notices, notice)
    }

    fun dismissNotice(context: Context, notice: Notice) {
        notices = Notices.dismiss(notices, notice.id)
        // Only the release note is remembered as read. A status message is a
        // receipt, and recording that one was seen would be recording noise.
        if (notice.id == AppNotice.current.id) {
            dismissedNoticeId = notice.id
            AndroidPreferences.save(context, settings())
        }
    }

    /** Applies and persists a whole settings object. */
    fun applyAppearance(context: Context, next: AppearanceSettings) {
        appearance = next
        AndroidPreferences.save(context, settings())
    }

    fun setTheme(context: Context, mode: ThemeMode) =
        applyAppearance(context, appearance.copy(theme = mode))

    /**
     * System -> dark -> light -> system, the same order the desktop cycles in.
     *
     * Three states rather than a flip, because "follow the system" is a real
     * choice and a two-way toggle silently takes it away the first time it is
     * pressed.
     */
    fun cycleTheme(context: Context) {
        setTheme(
            context,
            if (themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK,
        )
        status = "Theme: ${themeMode.displayName}."
    }

    /**
     * The editor sheet's two-state backdrop toggle, mapped onto the three-state
     * motion setting. Off means "less motion", not "none", so it lands on
     * REDUCED and leaves transitions alone.
     */
    fun setBackdrop(context: Context, enabled: Boolean, motion: Boolean = backdropMotion) {
        applyAppearance(
            context,
            appearance.copy(
                backdropEnabled = enabled,
                motion = if (motion) MotionLevel.FULL else MotionLevel.REDUCED,
            ),
        )
    }

    // -- Editor settings ---------------------------------------------------

    /** Applies [next] to the live editor and writes it to preferences. */
    fun applyEditorSettings(context: Context, next: EditorSettings) {
        controller.setSettings(next)
        AndroidPreferences.save(context, settings())
    }

    // -- Adding custom content ---------------------------------------------

    /**
     * Drops a preset onto the middle of the canvas and selects it.
     *
     * Identical in behaviour to the desktop's version, down to the follow-up
     * message - the two shells look nothing alike, but adding a shape should
     * do the same thing on both.
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
        sheet = MobileSheet.NONE
        status = if (added == null) {
            "Could not add ${preset.label}."
        } else {
            when (preset.typeId) {
                ElementCatalog.IMAGE_ANIMATED ->
                    "Added ${preset.label}. Import a GIF from the menu, then pick it as the frame strip."

                ElementCatalog.IMAGE_PLACEHOLDER ->
                    "Added ${preset.label}. Choose a texture for it in Properties."

                else -> "Added ${preset.label}. Tap Edit to restyle it."
            }
        }
    }

    // -- Deleting ----------------------------------------------------------

    /** Deletes the selection, asking first when the user has said to ask. */
    fun requestDeleteSelection() {
        if (!controller.current.hasSelection) return
        if (controller.current.settings.confirmBeforeDelete) {
            sheet = MobileSheet.CONFIRM_DELETE
        } else {
            controller.deleteSelection()
        }
    }

    fun confirmDeleteSelection() {
        controller.deleteSelection()
        sheet = MobileSheet.NONE
    }

    // -- Guarding unsaved work ---------------------------------------------

    /** Shown when something would replace a document with unsaved edits. */
    var unsavedPrompt by mutableStateOf<String?>(null)
        private set

    private var pendingAction: (() -> Unit)? = null

    /**
     * Runs [action], first asking about unsaved edits.
     *
     * The phone has no window to close, but it has a back gesture and a New
     * button that are just as capable of throwing away an afternoon's work.
     */
    fun guardUnsaved(prompt: String, action: () -> Unit) {
        if (!controller.current.dirty) {
            action()
            return
        }
        unsavedPrompt = prompt
        pendingAction = action
    }

    /** "Discard" on the unsaved prompt. */
    fun confirmDiscard() {
        val action = pendingAction
        pendingAction = null
        unsavedPrompt = null
        action?.invoke()
    }

    /** "Save first" on the unsaved prompt; returns false when a picker is needed. */
    fun saveThenContinue(context: Context): Boolean {
        if (!saveDocument(context)) return false
        confirmDiscard()
        return true
    }

    fun cancelUnsavedPrompt() {
        pendingAction = null
        unsavedPrompt = null
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

    // -- Session persistence -----------------------------------------------

    /**
     * Writes the working document to internal storage.
     *
     * Called when the app is backgrounded, because Android may never give it
     * another chance.
     */
    fun persistSession(context: Context) {
        SessionStore.save(
            context = context,
            project = controller.project,
            uri = documentUri,
            name = documentName,
            dirty = controller.current.dirty,
        )
    }

    /** Adopts a document written by [persistSession] on a previous run. */
    fun restoreSession(session: SessionStore.Session) {
        controller = freshController(session.project)
        documentUri = session.documentUri
        documentName = session.documentName
        if (session.dirty) {
            controller.markUnsaved()
            status = "Restored unsaved changes to '${session.project.name}'."
        }
    }

    // -- Documents ---------------------------------------------------------

    fun newProject(edition: Edition, name: String) {
        controller = freshController(EditorController.newProject(edition, name))
        documentUri = null
        documentName = null
        sheet = MobileSheet.NONE
        section = MobileSection.DESIGN
        status = "New ${edition.displayName} screen."
    }

    fun newFromTemplate(templateId: String) {
        val template = BuiltInTemplates[templateId] ?: return
        controller = freshController(template.instantiate())
        documentUri = null
        documentName = null
        sheet = MobileSheet.NONE
        section = MobileSection.DESIGN
        status = "Opened '${template.title}'."
    }

    /**
     * Opens anything the app can read a design out of.
     *
     * One entry point for project documents and for the formats
     * [DesignImporter] understands, because a phone has one file picker and
     * making somebody choose "open" versus "import" before they have looked at
     * the file is asking a question they cannot answer yet. What the file
     * turned out to be is reported after the fact instead.
     */
    fun openDocument(context: Context, uri: Uri) {
        val displayName = AndroidFileIO.displayName(context, uri)
        AndroidFileIO.readText(context, uri).fold(
            onSuccess = { text ->
                val outcome = DesignImporter.import(
                    fileName = displayName ?: "document",
                    content = text,
                    edition = controller.current.edition,
                )
                val project = outcome.project
                if (project == null) {
                    status = outcome.notes.firstOrNull() ?: "Could not read that file."
                    return@fold
                }

                if (outcome.format == ImportFormat.PROJECT) {
                    // A project document is the file it came from, opens
                    // straight into the front tab, and needs no preview -
                    // nothing was translated, so there is nothing to warn about.
                    controller = freshController(project)
                    documentUri = uri
                    documentName = displayName
                    AndroidFileIO.persistPermission(context, uri, writable = true)
                    section = MobileSection.DESIGN
                    sheet = MobileSheet.NONE
                    status = "Opened ${displayName ?: "project"}."
                    if (outcome.notes.isNotEmpty()) {
                        postNotice(
                            Notice(id = "import", headline = "Opened with notes", points = outcome.notes),
                        )
                    }
                } else {
                    // Everything else was translated between formats that do
                    // not agree about what a layout is, so it gets looked at
                    // before it lands. See `ImportPreviewPanel`.
                    pendingImport = outcome
                    sheet = MobileSheet.IMPORT_PREVIEW
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
        val assets = mutableListOf<TextureAsset>()
        uris.forEach { uri ->
            AndroidFileIO.readBytes(context, uri).fold(
                onSuccess = { bytes ->
                    assets += createTextureAsset(
                        id = Ids.prefixed("tex"),
                        name = AndroidFileIO.displayName(context, uri),
                        bytes = bytes,
                        sourcePath = uri.toString(),
                    )
                },
                onFailure = { status = "Could not import an image: ${it.message}" },
            )
        }
        if (assets.isEmpty()) return

        val imported = controller.addTextures(assets)
        // Anything imported by hand also joins the library, so the next project
        // does not have to go looking for the same file again.
        rememberInLibrary(context, assets, source = "Imported")
        status = "Imported $imported texture(s). They are in your library too."
        sheet = MobileSheet.ASSETS
    }

    /**
     * Builds one animated texture from several picked images.
     *
     * The route in for anything that is not a GIF - frames exported from a
     * video, a sequence rendered elsewhere. Files are taken in name order,
     * which is how frame sequences are always numbered.
     */
    fun importAnimationFrames(context: Context, uris: List<Uri>) {
        if (uris.size < 2) {
            status = "Pick at least two images - one frame is a still, so import it as one."
            return
        }

        val named = uris
            .mapNotNull { uri ->
                AndroidFileIO.readBytes(context, uri).getOrNull()
                    ?.let { AndroidFileIO.displayName(context, uri) to it }
            }
            .sortedBy { it.first.lowercase() }

        val asset = named.takeIf { it.size >= 2 }?.let { frames ->
            createAnimatedTextureFromFrames(
                id = Ids.prefixed("tex"),
                // The shared stem of "walk_01.png".."walk_12.png" names the
                // animation far better than the first file does.
                name = commonFrameName(frames.map { it.first }),
                frameFiles = frames.map { it.second },
            )
        }

        if (asset == null) {
            status = "Could not read those images as an animation."
            return
        }

        controller.addTextures(listOf(asset))
        rememberInLibrary(context, listOf(asset), source = "Animation")
        status = "Built a ${asset.frameCount}-frame animation. Add an Animated image " +
            "element and pick '${asset.name}' as its frame strip."
        sheet = MobileSheet.ASSETS
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

    // -- Prefabs -----------------------------------------------------------

    var prefabs by mutableStateOf(PrefabLibrary.Empty)
        private set

    /** Pre-filled name for the save-prefab sheet. */
    var prefabDraftName by mutableStateOf("")

    fun loadLibraries(context: Context) {
        prefabs = LibraryStore.loadPrefabs(context)
        textureLibrary = LibraryStore.loadTextures(context)
    }

    /** Opens the prefab sheet with a sensible name already filled in. */
    fun beginSavePrefab() {
        val selected = controller.current.selectedElements
        if (selected.isEmpty()) {
            status = "Select something first - a prefab is a group of elements."
            return
        }
        prefabDraftName = if (selected.size == 1) selected.first().name else "${selected.first().name} group"
        sheet = MobileSheet.PREFABS
    }

    fun savePrefab(context: Context, name: String) {
        val prefab = controller.prefabFromSelection(
            name = name,
            createdAtMillis = System.currentTimeMillis(),
        )
        if (prefab == null) {
            status = "Nothing to save - the selection is empty."
            return
        }
        prefabs = prefabs.with(prefab)
        LibraryStore.savePrefabs(context, prefabs)
        status = "Saved '${prefab.name}' (${prefab.elementCount} element(s))."
    }

    /** Drops a prefab into the middle of the canvas. */
    fun insertPrefab(prefab: Prefab) {
        val canvas = controller.project.canvas
        val inserted = controller.insertPrefab(
            prefab = prefab,
            at = IntPoint(canvas.width / 2, canvas.height / 2),
            centreOnPoint = true,
        )
        sheet = MobileSheet.NONE
        section = MobileSection.DESIGN
        status = if (inserted.isEmpty()) {
            "'${prefab.name}' is empty."
        } else if (prefab.edition != controller.current.edition) {
            "Inserted '${prefab.name}'. It was built for ${prefab.edition.displayName} - check Issues."
        } else {
            "Inserted '${prefab.name}'."
        }
    }

    fun deletePrefab(context: Context, id: String) {
        prefabs = prefabs.without(id)
        LibraryStore.savePrefabs(context, prefabs)
        status = "Deleted prefab."
    }

    // -- Texture library ---------------------------------------------------

    var textureLibrary by mutableStateOf(TextureLibrary.Empty)
        private set

    /** Adds assets to the cross-project library, skipping ones already stored. */
    fun rememberInLibrary(context: Context, assets: List<TextureAsset>, source: String): Int {
        if (assets.isEmpty()) return 0
        val before = textureLibrary.size
        textureLibrary = textureLibrary.withAll(
            assets.map {
                LibraryTexture(asset = it, source = source, addedAtMillis = System.currentTimeMillis())
            },
        )
        LibraryStore.saveTextures(context, textureLibrary)
        return textureLibrary.size - before
    }

    /** Copies a library texture into the open project. */
    fun useLibraryTexture(entry: LibraryTexture) {
        controller.addTexture(entry.asset.copy(id = Ids.prefixed("tex")))
        status = "Added '${entry.asset.name}' to this project."
    }

    fun forgetLibraryTexture(context: Context, id: String) {
        textureLibrary = textureLibrary.without(id)
        LibraryStore.saveTextures(context, textureLibrary)
    }

    // -- Resource packs ----------------------------------------------------

    /** The archive currently open in the pack-import sheet. */
    var openedPack by mutableStateOf<AndroidPackImport.OpenedPack?>(null)
        private set

    fun openPack(context: Context, uri: Uri) {
        AndroidPackImport.open(context, uri).fold(
            onSuccess = { pack ->
                if (pack.scan.isEmpty) {
                    status = "${pack.name} contains no images the designer can read."
                    return
                }
                openedPack = pack
                sheet = MobileSheet.PACK_IMPORT
            },
            onFailure = { status = "Could not read the pack: ${it.message}" },
        )
    }

    fun closePack() {
        openedPack = null
        sheet = MobileSheet.NONE
    }

    /** Reads the chosen entries into the library and, when asked, the project. */
    fun importFromPack(context: Context, selected: List<PackTexture>, intoProject: Boolean) {
        val pack = openedPack ?: return
        val assets = AndroidPackImport.read(context, pack, selected)
        if (assets.isEmpty()) {
            status = "None of the selected files could be decoded."
            return
        }
        val added = rememberInLibrary(context, assets, source = pack.name)
        if (intoProject) {
            // Fresh ids: the same pack texture may be imported into several
            // projects, and each document owns its own copy.
            controller.addTextures(assets.map { it.copy(id = Ids.prefixed("tex")) })
        }
        openedPack = null
        sheet = if (intoProject) MobileSheet.ASSETS else MobileSheet.LIBRARY
        status = buildString {
            append("Imported ${assets.size} texture(s) from ${pack.name}.")
            if (added < assets.size) append(" ${assets.size - added} were already in the library.")
        }
    }

    // -- Export ------------------------------------------------------------

    fun exportFileName(target: ExportTarget): String {
        val bundle = ExportManager.export(controller.project, target, codeTarget)
        return "${bundle.rootName}.zip"
    }

    /**
     * Accepts the previewed import, into a *new tab*.
     *
     * Never over the open document - matching the desktop, and for the same
     * reason: an import is a comparison, and overwriting the canvas destroys
     * the thing being compared against.
     */
    fun confirmImport() {
        val project = pendingImport?.project ?: return
        pendingImport = null

        tabs.add(AndroidDocumentTab(project))
        activeTab = tabs.lastIndex
        // Not the file it came from: saving must not write a project document
        // over somebody's hand-edited JSON.
        documentUri = null
        documentName = null
        section = MobileSection.DESIGN
        sheet = MobileSheet.NONE
        screen = AppScreen.EDITOR
        status = "Imported ${project.elements.size} element(s)."
    }

    fun cancelImport() {
        pendingImport = null
        sheet = MobileSheet.NONE
    }

    /** Remembers what to render, then the picker is opened for it. */
    fun requestImageSave(size: ImageSize, background: ImageBackground) {
        pendingImageSize = size
        pendingImageBackground = background
        pendingImageUri = null
    }

    /**
     * The picker created a document; now - and only now - render into it.
     *
     * The panel watches [pendingImageUri] and does the drawing, because only a
     * live composition can render. Nothing was held while the picker was up.
     */
    fun onImageDocumentCreated(uri: Uri?) {
        if (uri == null) {
            cancelImageSave()
            return
        }
        pendingImageUri = uri
    }

    /** Writes freshly rendered pixels to the document the picker created. */
    fun completeImageSave(context: Context, bytes: ByteArray) {
        val uri = pendingImageUri
        if (uri == null) {
            status = "There was nowhere to save the image. Try again."
            cancelImageSave()
            return
        }
        if (bytes.isEmpty()) {
            // Refused rather than written: an empty write leaves exactly the
            // silent zero-byte file this flow exists to prevent.
            status = "The image came out empty and was not saved."
            cancelImageSave()
            return
        }
        AndroidFileIO.writeBytes(context, uri, bytes).fold(
            onSuccess = {
                status = "Saved the image (${bytes.size / 1024} KB)."
                sheet = MobileSheet.NONE
            },
            onFailure = { status = "Could not save the image: ${it.message}" },
        )
        cancelImageSave()
    }

    fun failImageSave(message: String) {
        status = message
        cancelImageSave()
    }

    fun cancelImageSave() {
        pendingImageSize = null
        pendingImageUri = null
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
