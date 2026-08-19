package com.mcguidesigner.desktop

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
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.editor.EditorTool
import com.mcguidesigner.core.editor.ViewMode
import com.mcguidesigner.core.model.AlignMode
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.model.TargetForm
import com.mcguidesigner.desktop.dialogs.AboutDialog
import com.mcguidesigner.desktop.dialogs.ExportDialog
import com.mcguidesigner.desktop.dialogs.NewProjectDialog
import com.mcguidesigner.desktop.dialogs.ProjectSettingsDialog
import com.mcguidesigner.desktop.dialogs.RecoveryDialog
import com.mcguidesigner.desktop.dialogs.ShortcutsDialog
import com.mcguidesigner.desktop.dialogs.TemplateGalleryDialog
import com.mcguidesigner.desktop.dialogs.UnsavedChangesDialog
import com.mcguidesigner.desktop.dialogs.WelcomeDialog
import com.mcguidesigner.desktop.panels.AssetsPanel
import com.mcguidesigner.desktop.panels.CodePanel
import com.mcguidesigner.desktop.panels.InspectorPanel
import com.mcguidesigner.desktop.panels.IssuesPanel
import com.mcguidesigner.desktop.panels.LayersPanel
import com.mcguidesigner.desktop.panels.PalettePanel
import com.mcguidesigner.desktop.panels.PreviewPanel
import com.mcguidesigner.desktop.panels.TemplatesPanel
import com.mcguidesigner.desktop.widgets.IconToggle
import com.mcguidesigner.desktop.widgets.ToolbarButton
import com.mcguidesigner.desktop.widgets.ToolbarSeparator
import com.mcguidesigner.styles.render.rememberTextureCache
import com.mcguidesigner.styles.theme.LocalSkinPalette

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

    Column(modifier.background(palette.chromeBackground)) {
        EditorToolbar(app, controller, state)
        Divider(color = palette.chromeBorder)

        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (app.showLeftDock) {
                LeftDock(app, controller, state, textures, Modifier.width(320.dp).fillMaxHeight())
                Divider(Modifier.fillMaxHeight().width(1.dp), color = palette.chromeBorder)
            }

            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (state.viewMode) {
                    ViewMode.DESIGN -> DesignCanvasArea(controller, state, textures, Modifier.fillMaxSize())
                    ViewMode.PREVIEW -> PreviewPanel(controller, state, textures, Modifier.fillMaxSize())
                    ViewMode.CODE -> CodePanel(app, state, Modifier.fillMaxSize())
                }
            }

            if (app.showRightDock) {
                Divider(Modifier.fillMaxHeight().width(1.dp), color = palette.chromeBorder)
                RightDock(app, controller, state, Modifier.width(340.dp).fillMaxHeight())
            }
        }

        if (app.showBottomDock) {
            Divider(color = palette.chromeBorder)
            IssuesPanel(controller, state, Modifier.fillMaxWidth().height(180.dp))
        }

        Divider(color = palette.chromeBorder)
        StatusBar(app, state)
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
        ActiveDialog.NONE -> Unit
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
            EditionBadge(state)
            ToolbarSeparator()

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

@Composable
private fun EditionBadge(state: EditorState) {
    val palette = LocalSkinPalette.current
    Surface(
        color = palette.accentMuted,
        contentColor = palette.chromeText,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = state.edition.displayName.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
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
        TabRow(
            selectedTabIndex = app.toolboxTab.ordinal,
            containerColor = palette.chromePanel,
            contentColor = palette.chromeText,
        ) {
            ToolboxTab.entries.forEach { tab ->
                Tab(
                    selected = app.toolboxTab == tab,
                    onClick = { app.toolboxTab = tab },
                    text = { Text(tab.title, style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
        Box(Modifier.weight(1f)) {
            when (app.toolboxTab) {
                ToolboxTab.PALETTE -> PalettePanel(controller, state, textures)
                ToolboxTab.LAYERS -> LayersPanel(controller, state)
                ToolboxTab.TEMPLATES -> TemplatesPanel(app, state, textures)
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
            when (app.inspectorTab) {
                InspectorTab.PROPERTIES -> InspectorPanel(controller, state)
                InspectorTab.ASSETS -> AssetsPanel(app, controller, state)
                InspectorTab.ISSUES -> IssuesPanel(controller, state, Modifier.fillMaxSize())
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Status bar
// ---------------------------------------------------------------------------

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
