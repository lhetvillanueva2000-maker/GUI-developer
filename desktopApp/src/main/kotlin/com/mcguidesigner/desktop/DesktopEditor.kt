package com.mcguidesigner.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Divider
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.catalog.CustomPresets
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.editor.EditorTool
import com.mcguidesigner.core.editor.ViewMode
import com.mcguidesigner.core.model.AlignMode
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.model.TargetForm
import com.mcguidesigner.desktop.dialogs.AboutDialog
import com.mcguidesigner.desktop.dialogs.AppearanceDialog
import com.mcguidesigner.desktop.dialogs.AddCustomDialog
import com.mcguidesigner.desktop.dialogs.ComponentGalleryDialog
import com.mcguidesigner.desktop.dialogs.ConfirmDeleteDialog
import com.mcguidesigner.desktop.dialogs.EditorSettingsDialog
import com.mcguidesigner.desktop.dialogs.ExportDialog
import com.mcguidesigner.desktop.dialogs.NewProjectDialog
import com.mcguidesigner.desktop.dialogs.PackImportDialog
import com.mcguidesigner.desktop.dialogs.ProjectSettingsDialog
import com.mcguidesigner.desktop.dialogs.RecoveryDialog
import com.mcguidesigner.desktop.dialogs.SavePrefabDialog
import com.mcguidesigner.desktop.dialogs.ShortcutsDialog
import com.mcguidesigner.desktop.dialogs.TemplateGalleryDialog
import com.mcguidesigner.desktop.dialogs.UnsavedChangesDialog
import com.mcguidesigner.desktop.dialogs.WelcomeDialog
import com.mcguidesigner.desktop.panels.AssetsPanel
import com.mcguidesigner.desktop.panels.CodePanel
import com.mcguidesigner.desktop.panels.InspectorPanel
import com.mcguidesigner.desktop.panels.IssuesPanel
import com.mcguidesigner.desktop.panels.LayersPanel
import com.mcguidesigner.desktop.panels.LibraryPanel
import com.mcguidesigner.desktop.panels.PalettePanel
import com.mcguidesigner.desktop.panels.PrefabsPanel
import com.mcguidesigner.desktop.panels.PreviewPanel
import com.mcguidesigner.desktop.panels.TemplatesPanel
import com.mcguidesigner.desktop.widgets.IconToggle
import com.mcguidesigner.desktop.widgets.NudgePad
import com.mcguidesigner.desktop.widgets.ToolbarButton
import com.mcguidesigner.desktop.widgets.ToolbarSeparator
import com.mcguidesigner.styles.render.rememberTextureCache
import com.mcguidesigner.styles.theme.DesignerBackdrop
import com.mcguidesigner.styles.theme.EditionTabs
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.ThemeMode

/**
 * The desktop shell: a fixed multi-dock layout built for a mouse and a large
 * display.
 *
 * Left dock = creation (components, layers, templates).
 * Centre    = the work surface, switchable between design, preview and code.
 * Right dock = inspection (properties, assets, validation).
 */
@Composable
fun DesktopEditor(
    app: AppState,
    controller: EditorController,
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    val textures = rememberTextureCache(state.project)

    // The wallpaper sits behind everything; every dock and the status bar are
    // opaque on top of it, so it only ever shows around the canvas.
    DesignerBackdrop(
        edition = state.edition,
        modifier = modifier.background(
            if (app.backdropEnabled) Color.Transparent else palette.chromeBackground,
        ),
    ) {
        Column(Modifier.fillMaxSize()) {
            EditionHeader(app, controller, state)
            EditorToolbar(app, controller, state)
            Divider(color = palette.chromeBorder)

            Row(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedVisibility(
                    visible = app.showLeftDock,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut(),
                ) {
                    Row {
                        LeftDock(app, controller, state, textures, Modifier.width(320.dp).fillMaxHeight())
                        Divider(Modifier.fillMaxHeight().width(1.dp), color = palette.chromeBorder)
                    }
                }

                Box(Modifier.weight(1f).fillMaxHeight()) {
                    // Crossfade rather than a hard switch: design, preview and
                    // code are three views of one document, not three screens.
                    Crossfade(targetState = state.viewMode, label = "viewMode") { mode ->
                        when (mode) {
                            ViewMode.DESIGN -> DesignCanvasArea(
                                controller = controller,
                                state = state,
                                textures = textures,
                                modifier = Modifier.fillMaxSize(),
                                opaqueWorkspace = !app.backdropEnabled,
                            )
                            ViewMode.PREVIEW -> PreviewPanel(controller, state, textures, Modifier.fillMaxSize())
                            ViewMode.CODE -> CodePanel(app, state, Modifier.fillMaxSize())
                        }
                    }

                    // The move pad floats over the canvas, only while there is
                    // something to move. Fading it rather than snapping it in
                    // stops it flickering as the selection changes.
                    //
                    // Boxed first so the alignment comes from this Box rather
                    // than the Row above it, which would otherwise win.
                    Box(Modifier.align(state.settings.nudgePadCorner.alignment())) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = state.viewMode == ViewMode.DESIGN &&
                                state.hasSelection &&
                                state.settings.showNudgePad,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            NudgePad(
                                controller = controller,
                                settings = state.settings,
                                modifier = Modifier.padding(16.dp),
                                onOpenSettings = { app.dialog = ActiveDialog.EDITOR_SETTINGS },
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = app.showRightDock,
                    enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
                ) {
                    Row {
                        Divider(Modifier.fillMaxHeight().width(1.dp), color = palette.chromeBorder)
                        RightDock(app, controller, state, Modifier.width(340.dp).fillMaxHeight())
                    }
                }
            }

            AnimatedVisibility(
                visible = app.showBottomDock,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Divider(color = palette.chromeBorder)
                    IssuesPanel(controller, state, Modifier.fillMaxWidth().height(180.dp))
                }
            }

            Divider(color = palette.chromeBorder)
            BottomBar(app, state)
            Divider(color = palette.chromeBorder)
            StatusBar(app, state)
        }
    }

    when (app.dialog) {
        ActiveDialog.NEW_PROJECT -> NewProjectDialog(app)
        ActiveDialog.TEMPLATES -> TemplateGalleryDialog(app)
        ActiveDialog.EXPORT -> ExportDialog(app, state)
        ActiveDialog.PROJECT_SETTINGS -> ProjectSettingsDialog(app, controller, state)
        ActiveDialog.ABOUT -> AboutDialog(app)
        ActiveDialog.SHORTCUTS -> ShortcutsDialog(app)
        ActiveDialog.WELCOME -> WelcomeDialog(app)
        ActiveDialog.UNSAVED_CHANGES -> UnsavedChangesDialog(app)
        ActiveDialog.RECOVERY -> RecoveryDialog(app)
        ActiveDialog.SAVE_PREFAB -> SavePrefabDialog(app, state)
        ActiveDialog.COMPONENT_GALLERY -> ComponentGalleryDialog(app, state)
        ActiveDialog.PACK_IMPORT -> PackImportDialog(app)
        ActiveDialog.APPEARANCE -> AppearanceDialog(app)
        ActiveDialog.EDITOR_SETTINGS -> EditorSettingsDialog(app)
        ActiveDialog.ADD_CUSTOM -> AddCustomDialog(app)
        ActiveDialog.CONFIRM_DELETE -> ConfirmDeleteDialog(app)
        ActiveDialog.NONE -> Unit
    }
}

/** Where the move pad sits, as a Compose alignment. */
private fun com.mcguidesigner.core.editor.NudgePadCorner.alignment(): Alignment = when {
    isBottom && isRight -> Alignment.BottomEnd
    isBottom -> Alignment.BottomStart
    isRight -> Alignment.TopEnd
    else -> Alignment.TopStart
}

// ---------------------------------------------------------------------------
// Edition header
// ---------------------------------------------------------------------------

/**
 * The edition switcher, given the whole top strip of the app.
 *
 * Which edition you are in decides the component set, the skin, the validation
 * rules and the export pipeline, so it is the first thing on screen and always
 * visible - not a menu item you have to remember to check.  Switching keeps the
 * document and re-runs validation, so anything the new edition cannot express
 * is reported rather than silently dropped.
 */
@Composable
private fun EditionHeader(app: AppState, controller: EditorController, state: EditorState) {
    val palette = LocalSkinPalette.current

    Surface(color = palette.chromePanel, contentColor = palette.chromeText) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EditionTabs(
                selected = state.edition,
                onSelect = { edition ->
                    controller.switchEdition(edition)
                    app.status = "Now designing for ${edition.displayName}. " +
                        "The palette, the skin and the export format all followed."
                },
                modifier = Modifier.width(420.dp),
            )

            Text(
                text = state.documentTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            ToolbarButton("Components", hint = "Browse every component  (F1)") {
                app.dialog = ActiveDialog.COMPONENT_GALLERY
            }
            ToolbarButton("Import pack", hint = "Import textures from a Minecraft resource pack") {
                app.browsePack()
            }
            ToolbarButton(
                label = when (app.themeMode) {
                    ThemeMode.LIGHT -> "☀"
                    ThemeMode.DARK -> "☾"
                    ThemeMode.SYSTEM -> "◑"
                },
                hint = "Theme: ${app.themeMode.displayName}  ·  click to change, right-click for options",
            ) { app.cycleTheme() }
            ToolbarButton("⚙", hint = "Appearance settings") { app.dialog = ActiveDialog.APPEARANCE }
        }
    }
}

// ---------------------------------------------------------------------------
// Toolbar
// ---------------------------------------------------------------------------

@Composable
private fun EditorToolbar(app: AppState, controller: EditorController, state: EditorState) {
    val palette = LocalSkinPalette.current
    Surface(color = palette.chromePanel, contentColor = palette.chromeText) {
        Row(
            Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorTool.entries.filter { it != EditorTool.PLACE }.forEach { tool ->
                IconToggle(
                    label = tool.displayName,
                    hint = "${tool.displayName}  (${tool.shortcut})",
                    selected = state.tool == tool,
                    onClick = { controller.setTool(tool) },
                )
            }

            ToolbarSeparator()

            ToolbarButton("Undo", enabled = state.canUndo, hint = state.undoLabel?.let { "Undo $it" } ?: "Undo") {
                controller.undo()
            }
            ToolbarButton("Redo", enabled = state.canRedo, hint = state.redoLabel?.let { "Redo $it" } ?: "Redo") {
                controller.redo()
            }

            ToolbarSeparator()

            IconToggle("Grid", "Show grid  (Ctrl+G)", state.showGrid) { controller.toggleGrid() }
            GridSizeControl(controller, state)
            IconToggle("Snap", "Snap to grid", state.snapToGrid) { controller.toggleSnapToGrid() }
            IconToggle("Guides", "Smart alignment guides", state.snapToElements) { controller.toggleSnapToElements() }
            IconToggle("Rulers", "Show rulers  (Ctrl+R)", state.showRulers) { controller.toggleRulers() }

            ToolbarSeparator()

            listOf(
                AlignMode.LEFT to "⇤",
                AlignMode.HORIZONTAL_CENTER to "⇔",
                AlignMode.RIGHT to "⇥",
                AlignMode.TOP to "⤒",
                AlignMode.VERTICAL_CENTER to "⇕",
                AlignMode.BOTTOM to "⤓",
                AlignMode.DISTRIBUTE_HORIZONTAL to "⇹",
                AlignMode.DISTRIBUTE_VERTICAL to "⇳",
            ).forEach { (mode, glyph) ->
                ToolbarButton(glyph, enabled = state.hasSelection, hint = "Align: ${mode.displayName}") {
                    controller.align(mode)
                }
            }

            Spacer(Modifier.weight(1f))

            ZoomControls(controller, state)
            ToolbarSeparator()
            ViewModeTabs(controller, state)
        }
    }
}

/**
 * Grid pitch, in GUI pixels, right next to the toggle that shows it.
 *
 * The useful values are the ones Minecraft's own art is built on: 16 for a
 * block texture, 8 for the Java container grid, 4 for Bedrock's finer layout,
 * 2 and 1 for detail work.  0 turns the grid off entirely without touching the
 * snap setting.
 */
@Composable
private fun GridSizeControl(controller: EditorController, state: EditorState) {
    val palette = LocalSkinPalette.current
    val steps = listOf(0, 1, 2, 4, 8, 16)
    val current = state.project.canvas.gridSize

    fun step(direction: Int) {
        // A project may carry a pitch that is not on this ladder (a template's
        // 18, say). Treating that as "past the end" means the first step down
        // lands on the largest listed value rather than skipping over it.
        val index = steps.indexOfFirst { it >= current }.let { if (it < 0) steps.size else it }
        val next = steps[(index + direction).coerceIn(0, steps.lastIndex)]
        if (next != current) controller.updateCanvas { it.copy(gridSize = next) }
    }

    ToolbarButton("−", enabled = current > steps.first(), hint = "Finer grid") { step(-1) }
    Text(
        text = if (current <= 0) "off" else "${current}px",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = palette.chromeTextMuted,
        modifier = Modifier.width(34.dp).padding(horizontal = 2.dp),
    )
    ToolbarButton("+", enabled = current < steps.last(), hint = "Coarser grid") { step(1) }
}

@Composable
private fun ZoomControls(controller: EditorController, state: EditorState) {
    ToolbarButton("−", hint = "Zoom out  (Ctrl+-)") { controller.zoomBy(0.8f) }
    Text(
        text = "${(state.zoom * 100).toInt()}%",
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.width(52.dp).padding(horizontal = 4.dp),
    )
    ToolbarButton("+", hint = "Zoom in  (Ctrl++)") { controller.zoomBy(1.25f) }
    ToolbarButton("Reset", hint = "Reset zoom and pan  (Ctrl+0)") { controller.resetView() }
}

@Composable
private fun ViewModeTabs(controller: EditorController, state: EditorState) {
    Row {
        ViewMode.entries.forEach { mode ->
            IconToggle(
                label = mode.displayName,
                hint = when (mode) {
                    ViewMode.DESIGN -> "Edit the layout"
                    ViewMode.PREVIEW -> "See it as the game would draw it"
                    ViewMode.CODE -> "Turn this design into code"
                },
                selected = state.viewMode == mode,
                onClick = { controller.setViewMode(mode) },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Docks
// ---------------------------------------------------------------------------

@Composable
private fun LeftDock(
    app: AppState,
    controller: EditorController,
    state: EditorState,
    textures: com.mcguidesigner.styles.render.TextureCache,
    modifier: Modifier,
) {
    val palette = LocalSkinPalette.current
    Column(modifier.background(palette.chromePanel)) {
        // Scrollable rather than fixed: five tabs in a 320dp dock leaves an
        // even split too narrow for "Templates", and a wrapped tab label is
        // the sort of thing that makes a tool look unfinished.
        ScrollableTabRow(
            selectedTabIndex = app.toolboxTab.ordinal,
            containerColor = palette.chromePanel,
            contentColor = palette.chromeText,
            edgePadding = 4.dp,
        ) {
            ToolboxTab.entries.forEach { tab ->
                Tab(
                    selected = app.toolboxTab == tab,
                    onClick = { app.toolboxTab = tab },
                    text = {
                        val suffix = when (tab) {
                            ToolboxTab.PREFABS -> app.prefabs.prefabs.size.takeIf { it > 0 }?.let { " ($it)" } ?: ""
                            ToolboxTab.LIBRARY -> app.textureLibrary.size.takeIf { it > 0 }?.let { " ($it)" } ?: ""
                            else -> ""
                        }
                        Text(
                            tab.title + suffix,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                )
            }
        }
        Box(Modifier.weight(1f)) {
            Crossfade(targetState = app.toolboxTab, label = "toolboxTab") { tab ->
                when (tab) {
                    ToolboxTab.PALETTE -> PalettePanel(controller, state, textures)
                    ToolboxTab.PREFABS -> PrefabsPanel(app, state)
                    ToolboxTab.LIBRARY -> LibraryPanel(app, state)
                    ToolboxTab.LAYERS -> LayersPanel(controller, state)
                    ToolboxTab.TEMPLATES -> TemplatesPanel(app, state, textures)
                }
            }
        }
    }
}

@Composable
private fun RightDock(app: AppState, controller: EditorController, state: EditorState, modifier: Modifier) {
    val palette = LocalSkinPalette.current
    Column(modifier.background(palette.chromePanel)) {
        TabRow(
            selectedTabIndex = app.inspectorTab.ordinal,
            containerColor = palette.chromePanel,
            contentColor = palette.chromeText,
        ) {
            InspectorTab.entries.forEach { tab ->
                Tab(
                    selected = app.inspectorTab == tab,
                    onClick = { app.inspectorTab = tab },
                    text = {
                        val suffix = when (tab) {
                            InspectorTab.ISSUES -> state.validation.issues.size.takeIf { it > 0 }?.let { " ($it)" } ?: ""
                            InspectorTab.ASSETS -> state.project.textures.size.takeIf { it > 0 }?.let { " ($it)" } ?: ""
                            else -> ""
                        }
                        Text(tab.title + suffix, style = MaterialTheme.typography.labelMedium)
                    },
                )
            }
        }
        Box(Modifier.weight(1f)) {
            Crossfade(targetState = app.inspectorTab, label = "inspectorTab") { tab ->
                when (tab) {
                    InspectorTab.PROPERTIES -> InspectorPanel(controller, state)
                    InspectorTab.ASSETS -> AssetsPanel(app, controller, state)
                    InspectorTab.ISSUES -> IssuesPanel(controller, state, Modifier.fillMaxSize())
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Status bar
// ---------------------------------------------------------------------------

/**
 * The bar along the bottom of the window: adding things on the left, the
 * document's own numbers on the right.
 *
 * "Add anything" lives here rather than in the palette dock because the dock
 * can be hidden and because a shape is not a Minecraft component - it is a
 * drawing primitive, and putting it in the vanilla widget list would misfile
 * it.
 */
@Composable
private fun BottomBar(app: AppState, state: EditorState) {
    val palette = LocalSkinPalette.current
    Surface(color = palette.chromePanel, contentColor = palette.chromeText) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToolbarButton(
                label = "＋  Add anything",
                hint = "Shapes, animated images, GIFs and free-form custom elements",
            ) { app.dialog = ActiveDialog.ADD_CUSTOM }

            ToolbarSeparator()

            // The four shapes people reach for most, one click away; the rest
            // are behind the button above.
            CustomPresets.shapes.take(6).forEach { preset ->
                ToolbarButton(preset.glyph, hint = "Add a ${preset.label.lowercase()}") {
                    app.addCustomPreset(preset)
                }
            }

            ToolbarSeparator()

            ToolbarButton("🎞", hint = "Add an animated image or GIF") {
                CustomPresets.media.firstOrNull()?.let(app::addCustomPreset)
            }
            ToolbarButton("✦", hint = "Add a custom element of your own") {
                CustomPresets.anything.firstOrNull()?.let(app::addCustomPreset)
            }

            Spacer(Modifier.weight(1f))

            ToolbarButton("⋯", hint = "Editor settings - move steps, autosave and more") {
                app.dialog = ActiveDialog.EDITOR_SETTINGS
            }
        }
    }
}

@Composable
private fun StatusBar(app: AppState, state: EditorState) {
    val palette = LocalSkinPalette.current
    Surface(color = palette.chromePanelAlt, contentColor = palette.chromeTextMuted) {
        Row(
            Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(app.status, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))

            val errors = state.validation.errorCount
            val warnings = state.validation.warningCount
            if (errors > 0 || warnings > 0) {
                Text(
                    text = buildString {
                        if (errors > 0) append("$errors error${if (errors == 1) "" else "s"}")
                        if (errors > 0 && warnings > 0) append(" · ")
                        if (warnings > 0) append("$warnings warning${if (warnings == 1) "" else "s"}")
                    },
                    color = if (errors > 0) com.mcguidesigner.styles.theme.ErrorRed else com.mcguidesigner.styles.theme.WarningAmber,
                    style = MaterialTheme.typography.labelSmall,
                )
            } else {
                Text("No issues", style = MaterialTheme.typography.labelSmall, color = palette.accent)
            }

            Text(
                "${state.project.canvas.width} x ${state.project.canvas.height}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "${state.selection.size} selected",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
            )
            state.primaryElement?.let { element ->
                Text(
                    "${element.bounds.x}, ${element.bounds.y}  ${element.bounds.width}x${element.bounds.height}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
