package com.mcguidesigner.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.mcguidesigner.desktop.io.Workspace
import com.mcguidesigner.styles.render.decodeImageBitmap
import kotlinx.coroutines.delay
import com.mcguidesigner.core.editor.EditorTool
import com.mcguidesigner.core.editor.ViewMode
import com.mcguidesigner.core.model.AlignMode
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.styles.theme.DesignerTheme
import androidx.compose.ui.input.key.KeyShortcut

/**
 * Desktop entry point.
 *
 * The desktop shell is deliberately mouse-and-keyboard first: a real menu bar,
 * a full shortcut set, and a multi-dock layout that assumes a large screen.
 * The Android app in `:androidApp` presents the same editor completely
 * differently.
 */
fun main() = application {
    // Preferences are read before the first frame so the window opens at the
    // size and position it was last closed at, rather than jumping.
    val preferences = remember { Workspace.loadPreferences() }
    val recovery = remember { Workspace.pendingRecovery() }

    val appState = remember {
        AppState(BuiltInTemplates.demo.instantiate()).apply {
            applyPreferences(preferences)
            dialog = when {
                // A leftover autosave means the last session was killed. That
                // takes priority over everything else on screen.
                recovery != null -> ActiveDialog.RECOVERY
                preferences.showWelcomeOnStart -> ActiveDialog.WELCOME
                else -> ActiveDialog.NONE
            }
        }
    }

    DesignerWindow(appState)
}

@Composable
private fun ApplicationScope.DesignerWindow(appState: AppState) {
    val preferences = appState.preferences
    val windowState = rememberWindowState(
        size = DpSize(preferences.windowWidth.dp, preferences.windowHeight.dp),
        position = if (preferences.windowX >= 0 && preferences.windowY >= 0) {
            WindowPosition(preferences.windowX.dp, preferences.windowY.dp)
        } else {
            WindowPosition(Alignment.Center)
        },
        placement = if (preferences.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
    )
    val controller = appState.controller
    val editorState by controller.state.collectAsState()

    /** Writes the shell state out, including the live window geometry. */
    fun persist() {
        val floating = windowState.placement == WindowPlacement.Floating
        appState.persistPreferences(
            windowWidth = if (floating) windowState.size.width.value.toInt() else preferences.windowWidth,
            windowHeight = if (floating) windowState.size.height.value.toInt() else preferences.windowHeight,
            windowX = if (floating) windowState.position.x.value.toInt() else preferences.windowX,
            windowY = if (floating) windowState.position.y.value.toInt() else preferences.windowY,
            maximized = windowState.placement == WindowPlacement.Maximized,
        )
    }

    /** Save preferences and drop the autosave, then quit for real. */
    fun quit() {
        persist()
        Workspace.clearRecovery()
        exitApplication()
    }

    LaunchedEffect(appState.pendingExit) {
        if (appState.pendingExit) {
            appState.pendingExit = false
            appState.guardUnsaved("quit the designer") { quit() }
        }
    }

    // Autosave ticks while the document is dirty. Ten seconds is short enough
    // that a crash costs almost nothing and long enough that it never lands in
    // the middle of a drag.
    LaunchedEffect(Unit) {
        while (true) {
            delay(AUTOSAVE_INTERVAL_MILLIS)
            appState.autosaveIfDirty()
        }
    }

    // Window geometry and dock visibility are cheap to write and annoying to
    // lose, so they are persisted whenever they settle rather than only on exit.
    LaunchedEffect(
        windowState.size,
        windowState.position,
        windowState.placement,
        appState.showLeftDock,
        appState.showRightDock,
        appState.showBottomDock,
    ) {
        delay(PERSIST_DEBOUNCE_MILLIS)
        persist()
    }

    Window(
        onCloseRequest = { appState.guardUnsaved("close the designer") { quit() } },
        state = windowState,
        title = appState.windowTitle,
        icon = rememberAppIcon(),
        onPreviewKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) return@Window false
            handleShortcut(appState, event.key, event.isCtrlPressed, event.isShiftPressed)
        },
    ) {
        LaunchedEffect(window) { appState.frameProvider = { window } }

        DesignerMenuBar(appState)

        DesignerTheme(edition = editorState.edition, touchMode = false) {
            DesktopEditor(appState, controller, editorState, Modifier.fillMaxSize())
        }
    }
}

/** How often the working document is snapshotted while it has unsaved edits. */
private const val AUTOSAVE_INTERVAL_MILLIS = 10_000L

/** Settle time before window geometry changes are written to disk. */
private const val PERSIST_DEBOUNCE_MILLIS = 700L

/**
 * The window and taskbar icon.
 *
 * jpackage stamps the executable itself, but a window opened from `gradle run`
 * or from the portable jar has no icon unless it is set here.
 */
@Composable
private fun rememberAppIcon(): Painter? = remember {
    runCatching {
        val bytes = object {}.javaClass.getResourceAsStream("/app-icon.png")
            ?.use { it.readBytes() } ?: return@runCatching null
        decodeImageBitmap(bytes)?.let(::BitmapPainter)
    }.getOrNull()
}

/**
 * Global shortcuts.
 *
 * Handled in `onPreviewKeyEvent` rather than per-widget so they work no matter
 * which panel currently has focus - the behaviour people expect from a
 * desktop design tool.
 */
private fun handleShortcut(app: AppState, key: Key, ctrl: Boolean, shift: Boolean): Boolean {
    val controller = app.controller
    return when {
        ctrl && key == Key.Z && !shift -> { controller.undo(); true }
        ctrl && (key == Key.Y || (key == Key.Z && shift)) -> { controller.redo(); true }
        ctrl && key == Key.S && !shift -> { app.save(); true }
        ctrl && key == Key.S && shift -> { app.saveAs(); true }
        ctrl && key == Key.O -> { app.guardUnsaved("open another project") { app.open() }; true }
        ctrl && key == Key.N -> {
            app.guardUnsaved("start a new project") { app.dialog = ActiveDialog.NEW_PROJECT }
            true
        }
        ctrl && key == Key.E -> { app.dialog = ActiveDialog.EXPORT; true }
        ctrl && key == Key.D -> { controller.duplicateSelection(); true }
        ctrl && key == Key.A -> { controller.selectAll(); true }
        ctrl && key == Key.C -> { controller.copySelectionToText()?.let(Clipboard::write); true }
        ctrl && key == Key.V -> { Clipboard.read()?.let { controller.pasteFromText(it) }; true }
        ctrl && key == Key.X -> {
            controller.copySelectionToText()?.let(Clipboard::write)
            controller.deleteSelection()
            true
        }

        ctrl && key == Key.G -> { controller.toggleGrid(); true }
        ctrl && key == Key.R -> { controller.toggleRulers(); true }
        ctrl && key == Key.Equals || (ctrl && key == Key.Plus) -> { controller.zoomBy(1.25f); true }
        ctrl && key == Key.Minus -> { controller.zoomBy(0.8f); true }
        ctrl && key == Key.NumPad0 || (ctrl && key == Key.Zero) -> { controller.resetView(); true }

        ctrl && shift && key == Key.RightBracket -> { controller.bringToFront(); true }
        ctrl && shift && key == Key.LeftBracket -> { controller.sendToBack(); true }
        ctrl && key == Key.RightBracket -> { controller.bringForward(); true }
        ctrl && key == Key.LeftBracket -> { controller.sendBackward(); true }

        key == Key.Delete || key == Key.Backspace -> { controller.deleteSelection(); true }
        key == Key.Escape -> { controller.clearSelection(); controller.armPlacement(null); true }

        key == Key.V && !ctrl -> { controller.setTool(EditorTool.SELECT); true }
        key == Key.H && !ctrl -> { controller.setTool(EditorTool.PAN); true }
        key == Key.M && !ctrl -> { controller.setTool(EditorTool.MARQUEE); true }

        key == Key.DirectionLeft -> { controller.moveSelection(-nudge(shift), 0, null); true }
        key == Key.DirectionRight -> { controller.moveSelection(nudge(shift), 0, null); true }
        key == Key.DirectionUp -> { controller.moveSelection(0, -nudge(shift), null); true }
        key == Key.DirectionDown -> { controller.moveSelection(0, nudge(shift), null); true }

        else -> false
    }
}

/** Shift makes arrow-key nudges jump by a grid cell instead of one pixel. */
private fun nudge(shift: Boolean) = if (shift) 8 else 1

@Composable
private fun FrameWindowScope.DesignerMenuBar(app: AppState) {
    val controller = app.controller
    val state by controller.state.collectAsState()

    MenuBar {
        Menu("File", mnemonic = 'F') {
            Item("New Project...", shortcut = KeyShortcut(Key.N, ctrl = true)) {
                app.guardUnsaved("start a new project") { app.dialog = ActiveDialog.NEW_PROJECT }
            }
            Item("New from Template...") {
                app.guardUnsaved("load a template") { app.dialog = ActiveDialog.TEMPLATES }
            }
            Separator()
            Item("Open...", shortcut = KeyShortcut(Key.O, ctrl = true)) {
                app.guardUnsaved("open another project") { app.open() }
            }
            Menu("Open Recent") {
                if (app.recentFiles.isEmpty()) {
                    Item("(nothing yet)", enabled = false) {}
                } else {
                    app.recentFiles.forEach { file ->
                        Item(file.name) {
                            app.guardUnsaved("open ${file.name}") { app.openFile(file) }
                        }
                    }
                    Separator()
                    Item("Clear Recent") { app.clearRecentFiles() }
                }
            }
            Separator()
            Item("Save", shortcut = KeyShortcut(Key.S, ctrl = true)) { app.save() }
            Item("Save As...", shortcut = KeyShortcut(Key.S, ctrl = true, shift = true)) { app.saveAs() }
            Separator()
            Item("Import Textures...") { app.importTextures() }
            Item("Export...", shortcut = KeyShortcut(Key.E, ctrl = true)) { app.dialog = ActiveDialog.EXPORT }
            Separator()
            Item("Exit") { app.pendingExit = true }
        }

        Menu("Edit", mnemonic = 'E') {
            Item(
                state.undoLabel?.let { "Undo $it" } ?: "Undo",
                shortcut = KeyShortcut(Key.Z, ctrl = true),
                enabled = state.canUndo,
            ) { controller.undo() }
            Item(
                state.redoLabel?.let { "Redo $it" } ?: "Redo",
                shortcut = KeyShortcut(Key.Y, ctrl = true),
                enabled = state.canRedo,
            ) { controller.redo() }
            Separator()
            Item("Cut", shortcut = KeyShortcut(Key.X, ctrl = true), enabled = state.hasSelection) {
                controller.copySelectionToText()?.let(Clipboard::write)
                controller.deleteSelection()
            }
            Item("Copy", shortcut = KeyShortcut(Key.C, ctrl = true), enabled = state.hasSelection) {
                controller.copySelectionToText()?.let(Clipboard::write)
            }
            Item("Paste", shortcut = KeyShortcut(Key.V, ctrl = true)) {
                Clipboard.read()?.let { controller.pasteFromText(it) }
            }
            Item("Duplicate", shortcut = KeyShortcut(Key.D, ctrl = true), enabled = state.hasSelection) {
                controller.duplicateSelection()
            }
            Item("Delete", shortcut = KeyShortcut(Key.Delete), enabled = state.hasSelection) {
                controller.deleteSelection()
            }
            Separator()
            Item("Select All", shortcut = KeyShortcut(Key.A, ctrl = true)) { controller.selectAll() }
            Item("Deselect", shortcut = KeyShortcut(Key.Escape)) { controller.clearSelection() }
        }

        Menu("Arrange", mnemonic = 'A') {
            Item("Bring to Front", shortcut = KeyShortcut(Key.RightBracket, ctrl = true, shift = true)) {
                controller.bringToFront()
            }
            Item("Bring Forward", shortcut = KeyShortcut(Key.RightBracket, ctrl = true)) { controller.bringForward() }
            Item("Send Backward", shortcut = KeyShortcut(Key.LeftBracket, ctrl = true)) { controller.sendBackward() }
            Item("Send to Back", shortcut = KeyShortcut(Key.LeftBracket, ctrl = true, shift = true)) {
                controller.sendToBack()
            }
            Separator()
            AlignMode.entries.forEach { mode ->
                Item("Align: ${mode.displayName}", enabled = state.hasSelection) { controller.align(mode) }
            }
        }

        Menu("View", mnemonic = 'V') {
            Item("Design", enabled = state.viewMode != ViewMode.DESIGN) { controller.setViewMode(ViewMode.DESIGN) }
            Item("Live Preview", enabled = state.viewMode != ViewMode.PREVIEW) { controller.setViewMode(ViewMode.PREVIEW) }
            Item("Code", enabled = state.viewMode != ViewMode.CODE) { controller.setViewMode(ViewMode.CODE) }
            Separator()
            Item(if (state.showGrid) "Hide Grid" else "Show Grid", shortcut = KeyShortcut(Key.G, ctrl = true)) {
                controller.toggleGrid()
            }
            Item(if (state.showRulers) "Hide Rulers" else "Show Rulers", shortcut = KeyShortcut(Key.R, ctrl = true)) {
                controller.toggleRulers()
            }
            Item(if (state.snapToGrid) "Disable Snap to Grid" else "Enable Snap to Grid") { controller.toggleSnapToGrid() }
            Item(if (state.snapToElements) "Disable Smart Guides" else "Enable Smart Guides") {
                controller.toggleSnapToElements()
            }
            Item("Clear Guides") { controller.clearGuides() }
            Separator()
            Item("Zoom In", shortcut = KeyShortcut(Key.Equals, ctrl = true)) { controller.zoomBy(1.25f) }
            Item("Zoom Out", shortcut = KeyShortcut(Key.Minus, ctrl = true)) { controller.zoomBy(0.8f) }
            Item("Reset View", shortcut = KeyShortcut(Key.Zero, ctrl = true)) { controller.resetView() }
            Separator()
            Item(if (app.showLeftDock) "Hide Toolbox" else "Show Toolbox") { app.showLeftDock = !app.showLeftDock }
            Item(if (app.showRightDock) "Hide Inspector" else "Show Inspector") { app.showRightDock = !app.showRightDock }
            Item(if (app.showBottomDock) "Hide Issues Dock" else "Show Issues Dock") {
                app.showBottomDock = !app.showBottomDock
            }
        }

        Menu("Project", mnemonic = 'P') {
            Item("Project Settings...") { app.dialog = ActiveDialog.PROJECT_SETTINGS }
            Separator()
            Edition.entries.forEach { edition ->
                Item(
                    "Switch to ${edition.displayName}",
                    enabled = state.edition != edition,
                ) { controller.switchEdition(edition) }
            }
            Separator()
            Item("Re-run Validation") { controller.revalidate() }
        }

        Menu("Help", mnemonic = 'H') {
            Item("Welcome Screen") { app.dialog = ActiveDialog.WELCOME }
            Item("Keyboard Shortcuts") { app.dialog = ActiveDialog.SHORTCUTS }
            Separator()
            Item("About") { app.dialog = ActiveDialog.ABOUT }
        }
    }
}

/** Thin wrapper over the AWT system clipboard for element copy/paste. */
object Clipboard {
    private val clipboard get() = java.awt.Toolkit.getDefaultToolkit().systemClipboard

    fun write(text: String) = runCatching {
        clipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
    }.let { }

    fun read(): String? = runCatching {
        clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
    }.getOrNull()
}
