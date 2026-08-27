package com.mcguidesigner.web

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mcguidesigner.core.editor.DocumentSet
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.paint.PaintBackground
import com.mcguidesigner.core.serialization.LoadResult
import com.mcguidesigner.core.serialization.ProjectSerializer
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.exporters.CodeTarget
import com.mcguidesigner.exporters.ExportBundle
import com.mcguidesigner.exporters.ExportManager
import com.mcguidesigner.exporters.ExportTarget
import com.mcguidesigner.exporters.bytes
import com.mcguidesigner.styles.home.HomeOverlay
import com.mcguidesigner.styles.notice.AppNotice
import com.mcguidesigner.styles.notice.Notice
import com.mcguidesigner.styles.notice.Notices
import com.mcguidesigner.styles.paint.PaintState
import com.mcguidesigner.styles.render.decodeImageBitmap
import com.mcguidesigner.styles.render.readImageSize
import com.mcguidesigner.styles.settings.AppearanceSettings
import com.mcguidesigner.styles.theme.ThemeMode
import com.mcguidesigner.web.io.BrowserFiles
import com.mcguidesigner.web.io.WebPreferences
import com.mcguidesigner.web.io.ZipWriter

/** Which of the three screens is up. Named as the Android shell names them. */
enum class WebScreen { HOME, EDITOR, PAINT }

/** One open document, and the controller that owns its history. */
class WebTab(project: GuiProject) {
    val controller = EditorController(project)

    /**
     * The name the next download will carry, without an extension.
     *
     * A browser has no file path, so this stands in for one. It comes from the
     * project name, or from the file that was opened, and it is what makes a
     * second save of the same design land next to the first rather than as
     * "download (3)".
     */
    var fileStem: String = project.name.ifBlank { "untitled" }
}

/**
 * Everything the browser shell holds that is not the document itself.
 *
 * The third of three: the desktop has `AppState`, Android has `AndroidAppState`
 * and this is the same shape of object for a page. Where they differ is
 * entirely in what the platform can do - files, preferences, going back - and
 * that difference is why there are three of these rather than one.
 */
class WebAppState(initial: GuiProject) {

    val tabs = mutableStateListOf(WebTab(initial))

    var activeTab by mutableStateOf(0)
        private set

    val active: WebTab get() = tabs[activeTab.coerceIn(0, tabs.lastIndex)]

    val controller: EditorController get() = active.controller

    var screen by mutableStateOf(WebScreen.HOME)
        private set

    var homeOverlay by mutableStateOf(HomeOverlay.NONE)

    var appearance by mutableStateOf(WebPreferences.load())
        private set

    val themeMode: ThemeMode get() = appearance.theme

    /** The transient line along the bottom: what just happened. */
    var status by mutableStateOf<String?>(null)

    var notices = mutableStateListOf<Notice>()
        private set

    var noticeExpanded by mutableStateOf(false)

    /** Which export the dialog is offering, or null when it is closed. */
    var exportOpen by mutableStateOf(false)
    var exportTarget by mutableStateOf(ExportTarget.PROJECT_JSON)
    var codeTarget by mutableStateOf(CodeTarget.HTML_CSS)

    var paint by mutableStateOf<PaintState?>(null)
        private set

    init {
        if (AppNotice.isUnread(WebPreferences.dismissedNotice())) {
            notices.add(AppNotice.current)
        }
    }

    // ------------------------------------------------------------------ tabs

    fun selectTab(index: Int) {
        if (index in tabs.indices) activeTab = index
    }

    /**
     * Shows the tab for [edition], opening one if there is not already one.
     *
     * A tab per edition rather than converting the open document, which is
     * what the Android shell settled on for the same reason: switching edition
     * on a finished Java screen and finding every element rewritten as a
     * Bedrock one is a destructive surprise, and the undo for it is one step
     * that changes everything.
     */
    fun openTabFor(edition: Edition) {
        val existing = tabs.indexOfFirst { it.controller.project.edition == edition }
        if (existing >= 0) {
            activeTab = existing
            return
        }
        addTab(edition)
    }

    fun addTab(edition: Edition) {
        val project = (BuiltInTemplates.forEdition(edition).firstOrNull() ?: BuiltInTemplates.demo)
            .instantiate()
        tabs.add(WebTab(project))
        activeTab = tabs.lastIndex
    }

    fun closeTab(index: Int) {
        if (tabs.size <= 1 || index !in tabs.indices) return
        // Worked out before the removal, because that is the count the rule is
        // written against - it is deciding which tab to land on given how many
        // there were and which one went.
        val next = DocumentSet.activeAfterClose(activeTab, index, tabs.size)
        tabs.removeAt(index)
        activeTab = next.coerceIn(0, tabs.lastIndex)
    }

    // --------------------------------------------------------------- screens

    fun goHome() {
        screen = WebScreen.HOME
    }

    fun openEditor(edition: Edition) {
        if (edition == Edition.OTHER) {
            openPaint()
            return
        }
        openTabFor(edition)
        homeOverlay = HomeOverlay.NONE
        screen = WebScreen.EDITOR
    }

    /**
     * The paint canvas, created on first use and kept afterwards.
     *
     * 1536 square for the same reason the Android shell picked it: nine
     * megabytes a layer against the twenty-four that 2048 by 3072 would cost,
     * and this is running inside a tab that has to share its memory with the
     * whole browser.
     */
    fun openPaint() {
        if (paint == null) paint = PaintState(1536, 1536, PaintBackground.WHITE)
        homeOverlay = HomeOverlay.NONE
        screen = WebScreen.PAINT
    }

    // -------------------------------------------------------------- settings

    fun applyAppearance(settings: AppearanceSettings) {
        appearance = settings
        WebPreferences.save(settings)
    }

    /** Light and dark, which is all there is - see [ThemeMode]. */
    fun cycleTheme() {
        val next = if (appearance.theme == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
        applyAppearance(appearance.copy(theme = next))
    }

    fun dismissNotice(id: String) {
        notices.removeAll { it.id == id }
        WebPreferences.rememberNotice(id)
    }

    fun post(notice: Notice) {
        val next = Notices.post(notices.toList(), notice)
        notices.clear()
        notices.addAll(next)
    }

    // ------------------------------------------------------------------- I/O

    /** Saves the open document as a `.mcgui` download. */
    fun saveProject() {
        val tab = active
        val json = ProjectSerializer.encode(tab.controller.project)
        BrowserFiles.saveText("${tab.fileStem}.mcgui", json, "application/json;charset=utf-8")
        // No path to mark it saved *to*, but the work has left the tab, which
        // is what `dirty` is actually asking about.
        tab.controller.markSaved(null)
        status = "Saved ${tab.fileStem}.mcgui to your downloads."
    }

    /** Opens a `.mcgui` from the picker into the active tab. */
    fun openProject() {
        BrowserFiles.open(".mcgui,.json,application/json") { name, bytes ->
            when (val result = ProjectSerializer.decode(bytes.decodeToString())) {
                is LoadResult.Success -> {
                    val tab = active
                    tab.controller.replaceProject(result.project, path = null)
                    tab.fileStem = name.removeSuffix(".mcgui").removeSuffix(".json").ifBlank { "untitled" }
                    screen = WebScreen.EDITOR
                    status = "Opened $name."
                    if (result.warnings.isNotEmpty()) {
                        post(
                            Notice(
                                id = "opened-with-warnings",
                                headline = "$name opened with ${result.warnings.size} note(s).",
                                points = result.warnings,
                            ),
                        )
                    }
                }

                is LoadResult.Failure -> status = "Could not open $name: ${result.message}"
            }
        }
    }

    /**
     * Runs an export and hands the result to the browser.
     *
     * One file goes out as itself; anything with more than one is zipped,
     * because the alternative is a burst of downloads that arrive in no order
     * and lose their folder structure on the way - a resource pack is its
     * layout as much as its contents.
     */
    fun runExport(target: ExportTarget): ExportBundle {
        val bundle = ExportManager.export(active.controller.project, target, codeTarget)
        val single = bundle.files.singleOrNull()
        if (single != null) {
            BrowserFiles.save(single.path.substringAfterLast('/'), single.bytes(), mimeFor(single.path))
            status = "Exported ${single.path.substringAfterLast('/')}."
        } else {
            val zip = ZipWriter.archive(
                bundle.files.map { ZipWriter.Entry("${bundle.rootName}/${it.path}", it.bytes()) },
            )
            BrowserFiles.save("${bundle.rootName}.zip", zip, "application/zip")
            status = "Exported ${bundle.fileCount} files as ${bundle.rootName}.zip."
        }
        return bundle
    }

    /** Saves the paint canvas as a PNG download. */
    fun savePaintPng(bytes: ByteArray, name: String) {
        if (bytes.isEmpty()) {
            status = "There was nothing to save."
            return
        }
        BrowserFiles.save(name, bytes, "image/png")
        status = "Saved $name to your downloads."
    }

    /**
     * Reads an image from the picker onto the paint canvas.
     *
     * The header is measured before the pixels are decoded, for the same
     * reason the Android shell does it: a fifty-megapixel photograph is two
     * hundred megabytes as ARGB, and a tab that asks for that is a tab the
     * browser kills. Refusing with a sentence beats dying without one.
     */
    fun importPaintImage() {
        val target = paint ?: return
        BrowserFiles.open("image/*") { name, bytes ->
            val size = readImageSize(bytes)
            if (size != null && size.first.toLong() * size.second / 1_000_000 > 40) {
                status = "$name is too large to open here (${size.first}x${size.second})."
                return@open
            }
            val bitmap = decodeImageBitmap(bytes)
            if (bitmap == null) {
                status = "That file is not an image this browser can read."
                return@open
            }
            // Read out into a plain IntArray, because that is what the paint
            // engine works in - an ImageBitmap is the renderer's type, not the
            // compositor's.
            val pixels = IntArray(bitmap.width * bitmap.height)
            runCatching { bitmap.readPixels(pixels, 0, 0, bitmap.width, bitmap.height) }.fold(
                onSuccess = {
                    target.placeImage(pixels, bitmap.width, bitmap.height)
                    status = "Placed $name on ${target.document.active?.name ?: "the layer"}."
                },
                onFailure = { status = "Could not read that image's pixels: ${it.message}" },
            )
        }
    }

    // ------------------------------------------------------------- session

    /** Writes the open document to `localStorage`, so a refresh keeps it. */
    fun persistSession() {
        if (screen == WebScreen.HOME) return
        val ok = WebPreferences.saveSession(ProjectSerializer.encode(active.controller.project, prettyPrint = false))
        if (!ok) status = "This design is too large to keep in the tab - export it to keep it."
    }

    private fun mimeFor(path: String): String = when (path.substringAfterLast('.', "")) {
        "json", "mcgui" -> "application/json;charset=utf-8"
        "png" -> "image/png"
        "html" -> "text/html;charset=utf-8"
        "css" -> "text/css;charset=utf-8"
        "xml" -> "text/xml;charset=utf-8"
        else -> "text/plain;charset=utf-8"
    }
}
