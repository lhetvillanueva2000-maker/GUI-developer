package com.mcguidesigner.desktop

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
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
import com.mcguidesigner.core.catalog.CustomPresets
import com.mcguidesigner.core.editor.EditorTool
import com.mcguidesigner.core.editor.ViewMode
import com.mcguidesigner.core.model.AlignMode
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.exporters.ExportTarget
import com.mcguidesigner.core.support.Donation
import com.mcguidesigner.styles.home.HomeScreen
import com.mcguidesigner.styles.layout.AdaptiveMetrics
import com.mcguidesigner.styles.layout.DeviceClass
import com.mcguidesigner.styles.layout.LocalAdaptive
import com.mcguidesigner.styles.support.DonationQr
import com.mcguidesigner.styles.support.LocalDonationQr
import com.mcguidesigner.styles.theme.BackdropArtwork
import com.mcguidesigner.styles.theme.DesignerTheme
import com.mcguidesigner.styles.theme.LocalBackdropArtwork
import com.mcguidesigner.styles.theme.LocalBackdropMotion
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
            loadLibraries()
            // A leftover autosave means the last session was killed, and that
            // is the one thing worth interrupting the home screen for. The
            // welcome dialog no longer opens on startup: home already asks
            // which edition you want, and two front doors in a row is one
            // front door too many. It is still on the Help menu.
            dialog = if (recovery != null) ActiveDialog.RECOVERY else ActiveDialog.NONE
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
    LaunchedEffect(editorState.settings.autosaveSeconds) {
        val seconds = editorState.settings.autosaveSeconds
        if (seconds <= 0) return@LaunchedEffect
        while (true) {
            delay(seconds * 1000L)
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

        val donationQr = rememberDonationQr()

        DesignerMenuBar(appState)

        DesignerTheme(
            edition = editorState.edition,
            touchMode = false,
            dark = appState.darkChrome,
            chromeTheme = appState.appearance.chromeTheme,
            motion = appState.appearance.motion,
        ) {
            CompositionLocalProvider(
                LocalBackdropArtwork provides if (appState.backdropEnabled) DesktopBackdrops else BackdropArtwork.None,
                // One derived answer rather than two flags to keep in step:
                // the artwork only drifts when it is drawn at all *and* the
                // motion level allows anything to loop.
                LocalBackdropMotion provides appState.appearance.backdropMoves,
                LocalDonationQr provides donationQr,
            ) {
                // Crossfade rather than a hard cut: home and the editor share
                // a palette, and a swap with no transition reads as a repaint
                // glitch rather than a navigation.
                Crossfade(targetState = appState.screen, label = "screen") { screen ->
                    when (screen) {
                        AppScreen.HOME -> BoxWithConstraints(Modifier.fillMaxSize()) {
                            val metrics = AdaptiveMetrics.of(
                                widthDp = maxWidth,
                                heightDp = maxHeight,
                                touchMode = false,
                                device = DeviceClass.DESKTOP,
                            )
                            CompositionLocalProvider(LocalAdaptive provides metrics) {
                                HomeScreen(
                                    lastUsed = editorState.edition,
                                    dark = appState.darkChrome,
                                    settings = appState.appearance,
                                    onSettingsChange = appState::applyAppearance,
                                    overlay = appState.homeOverlay,
                                    onOverlayChange = { appState.homeOverlay = it },
                                    systemIsDark = appState.systemIsDark,
                                    eyebrow = appState.homeEyebrow,
                                    onOpen = appState::openEditor,
                                    onSaveQr = appState::saveDonationQr,
                                    onToggleTheme = appState::cycleTheme,
                                    onCopied = { appState.status = it },
                                )
                            }
                        }

                        AppScreen.EDITOR ->
                            DesktopEditor(appState, controller, editorState, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/**
 * The wallpaper artwork, read once from the classpath.
 *
 * The images are generated at build time from `build-scripts/backdrop` and
 * copied onto the classpath, so `gradle run`, the portable jar and every
 * installer all find them the same way.  A missing file is not an error -
 * [DesignerBackdrop] draws its own scene instead.
 */
private object DesktopBackdrops : BackdropArtwork {

    private val cache = mutableMapOf<String, ImageBitmap?>()

    override fun imageFor(edition: Edition, dark: Boolean): ImageBitmap? {
        val name = "backdrop-${edition.slug}-${if (dark) "dark" else "light"}"
        return cache.getOrPut(name) {
            runCatching {
                val bytes = javaClass.getResourceAsStream("/$name.png")?.use { it.readBytes() }
                    ?: return@runCatching null
                decodeImageBitmap(bytes)
            }.getOrNull()
        }
    }
}

/**
 * The donation QR, read off the classpath once per window.
 *
 * The build copies it there from `assets/donate`, the same file the Android
 * build packages, so both apps show and hand out the identical code.
 */
@Composable
private fun rememberDonationQr(): DonationQr? = remember {
    DonationQr.from(
        runCatching {
            object {}.javaClass.getResourceAsStream("/${Donation.QR_ASSET_NAME}")?.use { it.readBytes() }
        }.getOrNull(),
    )
}

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
 * Whether the editor's global shortcuts should see this key press at all.
 *
 * They are installed as `onPreviewKeyEvent`, which fires *before* whatever has
 * focus, so anything they claim is a key that no text field, no dialog and no
 * other screen will ever receive. That is exactly what a design tool wants
 * while you are looking at a canvas, and exactly what it does not want
 * anywhere else. Two things went wrong without this gate:
 *
 *  - **On the home screen.** Pressing Delete there ran `requestDeleteSelection`
 *    and opened a confirmation about elements you could not see, and V, H and
 *    M silently changed the tool in an editor that was not on screen.
 *  - **In a dialog.** Backspace in any text field deleted the *selected
 *    element* instead of a character, because the canvas got the key first.
 *
 * So: the editor screen only, and a modal dialog owns the keyboard while it is
 * open, apart from Escape - which is how modality is supposed to work.
 */
internal fun editorShortcutsApply(screen: AppScreen, dialog: ActiveDialog, key: Key): Boolean = when {
    screen != AppScreen.EDITOR -> false
    dialog != ActiveDialog.NONE -> key == Key.Escape
    else -> true
}

/**
 * Global shortcuts.
 *
 * Handled in `onPreviewKeyEvent` rather than per-widget so they work no matter
 * which panel currently has focus - the behaviour people expect from a
 * desktop design tool. See [editorShortcutsApply] for where that stops.
 */
private fun handleShortcut(app: AppState, key: Key, ctrl: Boolean, shift: Boolean): Boolean {
    if (!editorShortcutsApply(app.screen, app.dialog, key)) return false
    val controller = app.controller
    return when {
        ctrl && key == Key.Z && !shift -> { controller.undo(); true }
        ctrl && (key == Key.Y || (key == Key.Z && shift)) -> { controller.redo(); true }
        ctrl && key == Key.S && !shift -> { app.save(); true }
        ctrl && key == Key.S && shift -> { app.dialog = ActiveDialog.EXPORT; true }
        ctrl && key == Key.O -> { app.guardUnsaved("open another project") { app.open() }; true }
        ctrl && key == Key.N -> {
            app.guardUnsaved("start a new project") { app.dialog = ActiveDialog.NEW_PROJECT }
            true
        }
        ctrl && shift && key == Key.E -> {
            app.exportTarget = ExportTarget.EVERYTHING
            app.dialog = ActiveDialog.EXPORT
            true
        }
        ctrl && key == Key.E -> { app.dialog = ActiveDialog.EXPORT; true }
        ctrl && shift && key == Key.P -> { app.beginSavePrefab(); true }
        key == Key.F1 -> { app.dialog = ActiveDialog.COMPONENT_GALLERY; true }
        ctrl && key == Key.D -> { controller.duplicateSelection(); true }
        ctrl && key == Key.A -> { controller.selectAll(); true }
        ctrl && key == Key.C -> { controller.copySelectionToText()?.let(Clipboard::write); true }
        ctrl && key == Key.V -> { Clipboard.read()?.let { controller.pasteFromText(it) }; true }
        ctrl && key == Key.X -> {
            // Cut keeps the content on the clipboard, so there is nothing to
            // confirm - it is a move, not a loss.
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

        key == Key.Delete || key == Key.Backspace -> { app.requestDeleteSelection(); true }
        // Innermost first, the same order the Android back gesture uses: a
        // press that cancels a placement must not also throw you out of the
        // editor, and one with nothing left to cancel should.
        key == Key.Escape -> {
            val state = controller.current
            when {
                state.pendingPlacementType != null -> controller.armPlacement(null)
                state.hasSelection -> controller.clearSelection()
                app.dialog == ActiveDialog.NONE -> app.goHome()
                else -> Unit
            }
            true
        }

        key == Key.V && !ctrl -> { controller.setTool(EditorTool.SELECT); true }
        key == Key.H && !ctrl -> { controller.setTool(EditorTool.PAN); true }
        key == Key.M && !ctrl -> { controller.setTool(EditorTool.MARQUEE); true }

        // The arrow keys go through the same call the on-screen move pad does,
        // so Windows, Linux and macOS keyboards all move by exactly the step
        // the buttons show - see EditorSettings.arrowKeysUseNudgeStep.
        key == Key.DirectionLeft -> { arrowNudge(app, -1, 0, shift); true }
        key == Key.DirectionRight -> { arrowNudge(app, 1, 0, shift); true }
        key == Key.DirectionUp -> { arrowNudge(app, 0, -1, shift); true }
        key == Key.DirectionDown -> { arrowNudge(app, 0, 1, shift); true }

        else -> false
    }
}

/**
 * Moves the selection for one arrow-key press.
 *
 * With the setting on this is the move pad's step exactly. With it off the
 * arrows keep their classic fixed 1px / Shift+8px behaviour, for anyone who
 * wants coarse buttons and fine keys.
 */
private fun arrowNudge(app: AppState, dirX: Int, dirY: Int, shift: Boolean) {
    val settings = app.controller.current.settings
    if (settings.arrowKeysUseNudgeStep) {
        app.controller.nudgeSelection(dirX, dirY, large = shift)
    } else {
        val step = if (shift) LEGACY_LARGE_ARROW_STEP else 1
        app.controller.moveSelection(dirX * step, dirY * step, coalesceKey = "nudge")
    }
}

/** The fixed Shift+arrow step used before the step became configurable. */
private const val LEGACY_LARGE_ARROW_STEP = 8

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
            // What used to be "Save As" is now "Export": saving the document
            // under a new name is one of the things the export dialog does
            // (its "Project document" target), and it also offers every code
            // and resource-pack format, including the ones Minecraft reads
            // directly. One door instead of two.
            Item("Export...", shortcut = KeyShortcut(Key.S, ctrl = true, shift = true)) {
                app.dialog = ActiveDialog.EXPORT
            }
            Item("Export Everything...", shortcut = KeyShortcut(Key.E, ctrl = true, shift = true)) {
                app.exportTarget = ExportTarget.EVERYTHING
                app.dialog = ActiveDialog.EXPORT
            }
            Item("Save a Copy...") { app.saveAs() }
            Separator()
            Item("Import Textures, Images or GIFs...") { app.importTextures() }
            Item("Build an Animation from Images...") { app.importAnimationFrames() }
            Item("Import Resource Pack...") { app.browsePack() }
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
                app.requestDeleteSelection()
            }
            Separator()
            Item("Select All", shortcut = KeyShortcut(Key.A, ctrl = true)) { controller.selectAll() }
            Item("Deselect", shortcut = KeyShortcut(Key.Escape)) { controller.clearSelection() }
            Separator()
            Item(
                "Save Selection as Prefab...",
                shortcut = KeyShortcut(Key.P, ctrl = true, shift = true),
                enabled = state.hasSelection,
            ) { app.beginSavePrefab() }
        }

        Menu("Insert", mnemonic = 'I') {
            Item("Add Anything...") { app.dialog = ActiveDialog.ADD_CUSTOM }
            Separator()
            Menu("Shape") {
                CustomPresets.shapes.forEach { preset ->
                    Item("${preset.glyph}  ${preset.label}") { app.addCustomPreset(preset) }
                }
            }
            Menu("Image") {
                CustomPresets.media.forEach { preset ->
                    Item("${preset.glyph}  ${preset.label}") { app.addCustomPreset(preset) }
                }
            }
            Menu("Custom") {
                CustomPresets.anything.forEach { preset ->
                    Item("${preset.glyph}  ${preset.label}") { app.addCustomPreset(preset) }
                }
            }
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
            Menu("Move") {
                val step = state.settings.nudgeStep
                val big = state.settings.largeNudgeStep
                Item("Up  (${step}px)", shortcut = KeyShortcut(Key.DirectionUp), enabled = state.hasSelection) {
                    controller.nudgeSelection(0, -1)
                }
                Item("Down  (${step}px)", shortcut = KeyShortcut(Key.DirectionDown), enabled = state.hasSelection) {
                    controller.nudgeSelection(0, 1)
                }
                Item("Left  (${step}px)", shortcut = KeyShortcut(Key.DirectionLeft), enabled = state.hasSelection) {
                    controller.nudgeSelection(-1, 0)
                }
                Item("Right  (${step}px)", shortcut = KeyShortcut(Key.DirectionRight), enabled = state.hasSelection) {
                    controller.nudgeSelection(1, 0)
                }
                Separator()
                Item("Up  (${big}px)", enabled = state.hasSelection) { controller.nudgeSelection(0, -1, large = true) }
                Item("Down  (${big}px)", enabled = state.hasSelection) { controller.nudgeSelection(0, 1, large = true) }
                Item("Left  (${big}px)", enabled = state.hasSelection) { controller.nudgeSelection(-1, 0, large = true) }
                Item("Right  (${big}px)", enabled = state.hasSelection) { controller.nudgeSelection(1, 0, large = true) }
                Separator()
                Item("Change the step size...") { app.dialog = ActiveDialog.EDITOR_SETTINGS }
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
            Menu("Grid Size") {
                // The pitches Minecraft's own art is built on, plus "off".
                listOf(0, 1, 2, 4, 8, 16).forEach { size ->
                    Item(
                        if (size == 0) "Off" else "$size px",
                        enabled = state.project.canvas.gridSize != size,
                    ) { controller.updateCanvas { it.copy(gridSize = size) } }
                }
            }
            Separator()
            Item("Appearance...") { app.dialog = ActiveDialog.APPEARANCE }
            Item("Editor Settings...") { app.dialog = ActiveDialog.EDITOR_SETTINGS }
            Item("Switch Theme (${app.themeMode.displayName})") { app.cycleTheme() }
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
            Item("Component Library...", shortcut = KeyShortcut(Key.F1)) {
                app.dialog = ActiveDialog.COMPONENT_GALLERY
            }
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
