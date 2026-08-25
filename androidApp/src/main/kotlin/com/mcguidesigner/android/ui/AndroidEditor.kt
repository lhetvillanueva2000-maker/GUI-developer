@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mcguidesigner.android.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcguidesigner.android.AndroidAppState
import com.mcguidesigner.android.AppScreen
import com.mcguidesigner.android.MobileSection
import com.mcguidesigner.android.MobileSheet
import com.mcguidesigner.android.io.AndroidFileIO
import com.mcguidesigner.android.io.AndroidPackImport
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.NudgePadCorner
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.editor.ViewMode
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.styles.editor.DocumentTabs
import com.mcguidesigner.styles.editor.TabInfo
import com.mcguidesigner.styles.layout.AdaptiveMetrics
import com.mcguidesigner.styles.layout.DeviceClass
import com.mcguidesigner.styles.layout.LocalAdaptive
import com.mcguidesigner.styles.render.rememberTextureCache
import com.mcguidesigner.styles.notice.Notice
import com.mcguidesigner.styles.notice.NoticeStrip
import com.mcguidesigner.styles.theme.DesignerBackdrop
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.WarningAmber

/**
 * The Android shell.
 *
 * Structurally different from the desktop editor on purpose: one section at a
 * time, modal bottom sheets instead of docks, a thumb-reachable action bar for
 * the current selection, and a navigation rail instead of a bottom bar once
 * the window is wide enough (landscape phones and tablets).
 */
@Composable
fun AndroidEditor(
    app: AndroidAppState,
    controller: EditorController,
    state: EditorState,
    /**
     * Phone or tablet, decided from the screen rather than the window.
     *
     * Passed in rather than read here so home and the editor cannot disagree
     * about what the device is halfway through a rotation.
     */
    device: DeviceClass,
) {
    val context = LocalContext.current
    val palette = LocalSkinPalette.current
    val textures = rememberTextureCache(state.project)

    // --- Storage Access Framework launchers ------------------------------

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { app.openDocument(context, it) } }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AndroidFileIO.PROJECT_MIME),
    ) { uri -> uri?.let { app.onDocumentCreated(context, it) } }

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) app.importTextures(context, uris) }

    // A second picker rather than a mode on the first: picking five button
    // skins and picking five frames of an animation are different intents, and
    // guessing between them would get it wrong half the time.
    val frameLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> if (uris.isNotEmpty()) app.importAnimationFrames(context, uris) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AndroidFileIO.ZIP_MIME),
    ) { uri -> uri?.let { app.performExport(context, it) } }

    // Its own launcher rather than a mode on the export one: the contract
    // carries the MIME type, and offering to create a .zip when the thing
    // being saved is a .png would have the picker suggest the wrong extension.
    // The document is created *first* and rendered into afterwards. Rendering
    // first meant holding a PNG in memory while this picker was in front, and a
    // phone short on memory destroys the backgrounded activity - which left the
    // freshly created document at zero bytes with nothing reported.
    val imageSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(AndroidFileIO.PNG_MIME),
    ) { uri -> app.onImageDocumentCreated(uri) }

    val packLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { app.openPack(context, it) } }

    // Status messages go to the notification panel at the top rather than a
    // snackbar at the bottom. A floating bar over the tool row was covering
    // the controls that produced the message, and it meant the app had two
    // notification systems talking over each other in opposite corners.
    //
    // One id for all of them, so a message that fires on every nudge replaces
    // the last rather than stacking.
    LaunchedEffect(app.status) {
        app.status?.let { message ->
            app.postNotice(Notice(id = "status", headline = message, transient = true))
            app.status = null
        }
    }

    // A timed snapshot on top of the one taken in onStop. Android can kill a
    // backgrounded process without warning, and a crash never reaches onStop
    // at all, so the interval is what actually protects a long session.
    LaunchedEffect(state.settings.autosaveSeconds) {
        val seconds = state.settings.autosaveSeconds
        if (seconds <= 0) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(seconds * 1000L)
            if (controller.current.dirty) app.persistSession(context)
        }
    }

    // Keep the shared view mode in step with the mobile section so the canvas
    // renders design chrome only while the Design section is showing.
    LaunchedEffect(app.section) {
        controller.setViewMode(
            when (app.section) {
                MobileSection.PREVIEW -> ViewMode.PREVIEW
                MobileSection.CODE -> ViewMode.CODE
                else -> ViewMode.DESIGN
            },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // One place decides which of the three layouts this window gets, and
        // every measurement below is read off it. Deriving "is this a tablet?"
        // separately at each call site is how a rail and a bottom bar end up on
        // screen at the same time.
        val metrics = AdaptiveMetrics.of(
            widthDp = maxWidth,
            heightDp = maxHeight,
            touchMode = true,
            device = device,
        )
        val dockInspector = metrics.usesDockedInspector && app.section == MobileSection.DESIGN

        CompositionLocalProvider(LocalAdaptive provides metrics) {
            // The wallpaper, as the bottom layer of this Box rather than a wrapper
            // around the scaffold: the scaffold is transparent so the artwork shows
            // through around the canvas, and every bar and sheet stays opaque.
            if (app.backdropEnabled) {
                DesignerBackdrop(state.edition, Modifier.fillMaxSize()) {}
            } else {
                Box(Modifier.fillMaxSize().background(palette.chromeBackground))
            }

            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    Column {
                        MobileTopBar(
                            app = app,
                            controller = controller,
                            state = state,
                            onOpen = {
                                app.guardUnsaved("open another project") { openLauncher.launch(arrayOf("*/*")) }
                            },
                            onSave = { if (!app.saveDocument(context)) createLauncher.launch(app.suggestedFileName()) },
                            onSaveAs = { createLauncher.launch(app.suggestedFileName()) },
                            onImportImages = { imageLauncher.launch(AndroidFileIO.IMAGE_MIME_TYPES) },
                            onImportFrames = { frameLauncher.launch(AndroidFileIO.IMAGE_MIME_TYPES) },
                            onImportPack = { packLauncher.launch(AndroidPackImport.PACK_MIME_TYPES) },
                            metrics = metrics,
                        )
                        ToolStrip(app, state, metrics)
                        DocumentTabs(
                            tabs = app.tabs.map { TabInfo(it.title, it.edition, it.dirty) },
                            active = app.activeTab,
                            onSelect = app::selectTab,
                            onClose = app::closeTab,
                            onAdd = { app.addTab(state.edition) },
                            metrics = metrics,
                        )
                        NoticeStrip(
                            notices = app.notices,
                            expanded = app.noticeExpanded,
                            onExpandedChange = { app.noticeExpanded = it },
                            onDismiss = { app.dismissNotice(context, it) },
                            metrics = metrics,
                        )
                    }
                },
                bottomBar = {
                    if (metrics.usesBottomNav) {
                        MobileNavBar(app, metrics)
                    }
                },
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    if (metrics.usesRail) {
                        MobileNavRail(app, metrics)
                    }

                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        when (app.section) {
                            MobileSection.DESIGN -> TouchDesignSurface(
                                controller = controller,
                                state = state,
                                textures = textures,
                                modifier = Modifier.fillMaxSize(),
                                opaqueWorkspace = !app.backdropEnabled,
                            )

                            MobileSection.LAYERS -> MobileLayersSection(
                                controller, state, Modifier.fillMaxSize(),
                            )

                            MobileSection.PREVIEW -> MobilePreviewSection(
                                controller, state, textures, Modifier.fillMaxSize(),
                            )

                            MobileSection.CODE -> MobileCodeSection(
                                app, state, Modifier.fillMaxSize(),
                            )
                        }

                        // Floating selection actions: the mobile answer to the
                        // desktop's arrange menu and right-click. On a tablet the
                        // docked inspector already spells these out, so the bar
                        // stays out of the way of the canvas.
                        if (app.section == MobileSection.DESIGN) {
                            if (!dockInspector) {
                                SelectionActionBar(
                                    app = app,
                                    controller = controller,
                                    state = state,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 12.dp),
                                )
                            }

                            // The move pad sits above the action bar, in whichever
                            // corner the settings put it - a left-handed grip and a
                            // right-handed one want different sides.
                            if (state.hasSelection && state.settings.showNudgePad) {
                                MobileNudgePad(
                                    controller = controller,
                                    settings = state.settings,
                                    onOpenSettings = { app.sheet = MobileSheet.EDITOR_SETTINGS },
                                    modifier = Modifier
                                        .align(state.settings.nudgePadCorner.toAlignment())
                                        .padding(horizontal = 10.dp)
                                        .padding(
                                            top = 10.dp,
                                            bottom = if (state.settings.nudgePadCorner.isBottom) {
                                                if (dockInspector) 16.dp else 84.dp
                                            } else {
                                                10.dp
                                            },
                                        ),
                                )
                            }
                        }
                    }

                    // The tablet's second pane. Only on the design surface: the
                    // layers, preview and code sections are already the whole of
                    // what they show, and squeezing them would gain nothing.
                    if (dockInspector) {
                        Divider(Modifier.fillMaxHeight().width(1.dp), color = palette.chromeBorder)
                        TabletInspector(
                            app = app,
                            controller = controller,
                            state = state,
                            metrics = metrics,
                            modifier = Modifier.width(metrics.panelWidth).fillMaxHeight(),
                        )
                    }
                }
            }

            MobileSheets(
                app = app,
                controller = controller,
                state = state,
                textures = textures,
                onImportImages = { imageLauncher.launch(AndroidFileIO.IMAGE_MIME_TYPES) },
                onImportPack = { packLauncher.launch(AndroidPackImport.PACK_MIME_TYPES) },
                onExport = { target ->
                    app.pendingExportTarget = target
                    exportLauncher.launch(app.exportFileName(target))
                },
                onPickImageDestination = { fileName -> imageSaveLauncher.launch(fileName) },
            )

            app.unsavedPrompt?.let { prompt ->
                UnsavedChangesSheet(
                    prompt = prompt,
                    projectName = state.project.name,
                    onSave = { if (!app.saveThenContinue(context)) createLauncher.launch(app.suggestedFileName()) },
                    onDiscard = { app.confirmDiscard() },
                    onCancel = { app.cancelUnsavedPrompt() },
                )
            }
        }
    }

    // Back navigation, innermost first: close a sheet, then leave a secondary
    // section, then offer to save. Only when there is nothing left to unwind
    // does back actually leave the app - and never silently on unsaved work.
    val activity = LocalContext.current as? Activity
    // Gated on the screen so it can never race the host's own handler during
    // the crossfade between home and here.
    BackHandler(enabled = app.screen == AppScreen.EDITOR) {
        when {
            app.unsavedPrompt != null -> app.cancelUnsavedPrompt()
            app.sheet == MobileSheet.PACK_IMPORT -> app.closePack()
            app.sheet != MobileSheet.NONE -> app.sheet = MobileSheet.NONE
            state.hasSelection -> controller.clearSelection()
            app.section != MobileSection.DESIGN -> app.section = MobileSection.DESIGN
            // Home is a screen behind this one, so back reaches it before it
            // reaches the launcher. Nothing is discarded on the way - the
            // document is still open when you come back in.
            else -> app.goHome()
        }
    }
}

/**
 * The mobile equivalent of the desktop's Save / Discard / Cancel prompt.
 *
 * A bottom sheet rather than a dialog so the buttons land under the thumb
 * instead of in the middle of the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnsavedChangesSheet(
    prompt: String,
    projectName: String,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    val palette = LocalSkinPalette.current

    ModalBottomSheet(
        onDismissRequest = onCancel,
        containerColor = palette.chromePanel,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(
                "Save '$projectName'?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "There are changes that have not been written to a file. " +
                    "Discarding them will $prompt and the edits will be lost.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.chromeTextMuted,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Keep editing") }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDiscard,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Discard changes", color = WarningAmber) }
        }
    }
}

// ---------------------------------------------------------------------------
// Chrome
// ---------------------------------------------------------------------------

/**
 * The tool row directly under the title bar.
 *
 * This is where the edition tabs used to be. The edition is chosen on the home
 * screen now, so the row carries the shortcuts that were sharing it - and the
 * space the tabs left is where the what's-new strip goes, immediately below.
 */
@Composable
private fun ToolStrip(
    app: AndroidAppState,
    state: EditorState,
    metrics: AdaptiveMetrics,
) {
    val palette = LocalSkinPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.chromePanel)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // On every device, not just the wide ones. The phone used to rely on
        // the system gesture alone, which is real but invisible: a back
        // control you cannot see is one a first-time user does not know is
        // there, and the gesture still works alongside it.
        TopBarAction("←") { app.goHome() }
        Text(
            state.edition.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = palette.accent,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        TopBarAction("⌗") { app.sheet = MobileSheet.ARRANGE }
        TopBarAction("◫") { app.sheet = MobileSheet.LIBRARY }
        // Wide windows have the room to name the tools the phone reduces to
        // glyphs, so the strip earns its extra height instead of just
        // stretching.
        if (metrics.sizeClass.isExpanded) {
            TopBarAction("Gallery") { app.sheet = MobileSheet.GALLERY }
        }
    }
}

@Composable
private fun MobileTopBar(
    app: AndroidAppState,
    controller: EditorController,
    state: EditorState,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onImportImages: () -> Unit,
    onImportFrames: () -> Unit,
    onImportPack: () -> Unit,
    metrics: AdaptiveMetrics,
) {
    val palette = LocalSkinPalette.current
    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = palette.chromePanel,
            titleContentColor = palette.chromeText,
        ),
        title = {
            Column {
                Text(
                    state.documentTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                Text(
                    "${state.edition.displayName}  ·  ${state.project.canvas.width}x${state.project.canvas.height}" +
                        if (state.validation.errorCount > 0) "  ·  ${state.validation.errorCount} errors" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.validation.errorCount > 0) {
                        com.mcguidesigner.styles.theme.ErrorRed
                    } else {
                        palette.chromeTextMuted
                    },
                    maxLines = 1,
                )
            }
        },
        actions = {
            TopBarAction("↶", enabled = state.canUndo) { controller.undo() }
            TopBarAction("↷", enabled = state.canRedo) { controller.redo() }
            TopBarAction("⋮") { menuOpen = true }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Save") }, onClick = { menuOpen = false; onSave() })
                // "Save as" became "Export": writing the document to a new file
                // is one of the things the export sheet does, alongside every
                // pack and code format - including the ones Minecraft reads.
                DropdownMenuItem(
                    text = { Text("Export...") },
                    onClick = { menuOpen = false; app.sheet = MobileSheet.EXPORT },
                )
                DropdownMenuItem(text = { Text("Save a copy...") }, onClick = { menuOpen = false; onSaveAs() })
                DropdownMenuItem(text = { Text("Open or import...") }, onClick = { menuOpen = false; onOpen() })
                DropdownMenuItem(
                    text = { Text("New from template...") },
                    onClick = { menuOpen = false; app.sheet = MobileSheet.TEMPLATES },
                )
                DropdownMenuItem(
                    text = { Text("Import images or GIFs...") },
                    onClick = { menuOpen = false; onImportImages() },
                )
                DropdownMenuItem(
                    text = { Text("Build an animation from images...") },
                    onClick = { menuOpen = false; onImportFrames() },
                )
                // The grid lives here as well as in the Canvas sheet: it is
                // off by default now, and a setting that is off by default has
                // to be findable without knowing which sheet it is filed under.
                DropdownMenuItem(
                    text = { Text(if (state.showGrid) "Hide grid" else "Show grid") },
                    onClick = { menuOpen = false; controller.toggleGrid() },
                )
                DropdownMenuItem(
                    text = { Text("Diagnostics...") },
                    onClick = { menuOpen = false; app.sheet = MobileSheet.DIAGNOSTICS },
                )
                DropdownMenuItem(
                    text = { Text("Import resource pack...") },
                    onClick = { menuOpen = false; onImportPack() },
                )
                DropdownMenuItem(
                    text = { Text("Texture library (${app.textureLibrary.size})") },
                    onClick = { menuOpen = false; app.sheet = MobileSheet.LIBRARY },
                )
                DropdownMenuItem(
                    text = { Text("Prefabs (${app.prefabs.prefabs.size})") },
                    onClick = { menuOpen = false; app.sheet = MobileSheet.PREFABS },
                )
                DropdownMenuItem(
                    text = { Text("Save selection as prefab") },
                    enabled = state.hasSelection,
                    onClick = { menuOpen = false; app.beginSavePrefab() },
                )
                DropdownMenuItem(
                    text = { Text("Component library") },
                    onClick = { menuOpen = false; app.sheet = MobileSheet.GALLERY },
                )
                DropdownMenuItem(
                    text = { Text("Textures in this project (${state.project.textures.size})") },
                    onClick = { menuOpen = false; app.sheet = MobileSheet.ASSETS },
                )
                DropdownMenuItem(
                    text = { Text("Arrange & align") },
                    onClick = { menuOpen = false; app.sheet = MobileSheet.ARRANGE },
                )
                DropdownMenuItem(
                    text = { Text("Canvas & grid") },
                    onClick = { menuOpen = false; app.sheet = MobileSheet.CANVAS },
                )
                DropdownMenuItem(
                    text = { Text("Add shapes & custom elements") },
                    onClick = { menuOpen = false; app.sheet = MobileSheet.ADD_CUSTOM },
                )
                DropdownMenuItem(
                    text = { Text("Editor settings") },
                    onClick = { menuOpen = false; app.sheet = MobileSheet.EDITOR_SETTINGS },
                )
                DropdownMenuItem(
                    text = { Text("Appearance (${app.themeMode.displayName})") },
                    onClick = { menuOpen = false; app.sheet = MobileSheet.APPEARANCE },
                )
                DropdownMenuItem(
                    text = { Text("Project settings") },
                    onClick = { menuOpen = false; app.sheet = MobileSheet.PROJECT },
                )
                DropdownMenuItem(
                    text = { Text("Issues (${state.validation.issues.size})") },
                    onClick = { menuOpen = false; app.sheet = MobileSheet.ISSUES },
                )
                // No "switch edition" item: the tabs under this bar own that,
                // and offering it in two places invites the two to disagree.
            }
        },
    )
}

/** Where the move pad sits, as a Compose alignment. */
private fun NudgePadCorner.toAlignment(): Alignment = when {
    isBottom && isRight -> Alignment.BottomEnd
    isBottom -> Alignment.BottomStart
    isRight -> Alignment.TopEnd
    else -> Alignment.TopStart
}

@Composable
private fun TopBarAction(glyph: String, enabled: Boolean = true, onClick: () -> Unit) {
    val palette = LocalSkinPalette.current
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(50))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) palette.chromeText else palette.chromeTextMuted,
        )
    }
}

/**
 * Bottom navigation, phones only.
 *
 * Six destinations is the most this pattern carries before the labels start
 * eliding, which is why anything beyond these lives in the overflow menu.
 */
@Composable
private fun MobileNavBar(app: AndroidAppState, metrics: AdaptiveMetrics) {
    val palette = LocalSkinPalette.current
    NavigationBar(containerColor = palette.chromePanel) {
        MobileSection.entries.forEach { section ->
            NavigationBarItem(
                selected = app.section == section,
                onClick = { app.section = section },
                icon = { Text(section.glyph, style = MaterialTheme.typography.titleMedium) },
                label = if (metrics.showsNavLabels) {
                    { Text(section.title, style = MaterialTheme.typography.labelSmall) }
                } else {
                    null
                },
            )
        }
        NavigationBarItem(
            selected = app.sheet == MobileSheet.COMPONENTS,
            onClick = { app.sheet = MobileSheet.COMPONENTS },
            icon = { Text("＋", style = MaterialTheme.typography.titleMedium) },
            label = if (metrics.showsNavLabels) {
                { Text("Add", style = MaterialTheme.typography.labelSmall) }
            } else {
                null
            },
        )
        // Separate from "Add" on purpose: that one lists Minecraft's own
        // widgets, this one lists shapes, GIFs and anything the catalog does
        // not have. Mixing them would bury both.
        NavigationBarItem(
            selected = app.sheet == MobileSheet.ADD_CUSTOM,
            onClick = { app.sheet = MobileSheet.ADD_CUSTOM },
            icon = { Text("◆", style = MaterialTheme.typography.titleMedium) },
            label = if (metrics.showsNavLabels) {
                { Text("Custom", style = MaterialTheme.typography.labelSmall) }
            } else {
                null
            },
        )
    }
}

/**
 * The rail: tablets, and phones turned sideways.
 *
 * It carries the same destinations as the bottom bar, laid out vertically so
 * the whole width of the window is left for the canvas.
 */
@Composable
private fun MobileNavRail(app: AndroidAppState, metrics: AdaptiveMetrics) {
    val palette = LocalSkinPalette.current
    NavigationRail(
        containerColor = palette.chromePanel,
        modifier = Modifier
            .width(metrics.railWidth)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val label: @Composable (String) -> (@Composable () -> Unit)? = { title ->
            if (metrics.showsNavLabels) {
                { Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            } else {
                null
            }
        }

        MobileSection.entries.forEach { section ->
            NavigationRailItem(
                selected = app.section == section,
                onClick = { app.section = section },
                icon = { Text(section.glyph, style = MaterialTheme.typography.titleMedium) },
                label = label(section.title),
            )
        }
        NavigationRailItem(
            selected = app.sheet == MobileSheet.COMPONENTS,
            onClick = { app.sheet = MobileSheet.COMPONENTS },
            icon = { Text("＋", style = MaterialTheme.typography.titleMedium) },
            label = label("Add"),
        )
        NavigationRailItem(
            selected = app.sheet == MobileSheet.ADD_CUSTOM,
            onClick = { app.sheet = MobileSheet.ADD_CUSTOM },
            icon = { Text("◆", style = MaterialTheme.typography.titleMedium) },
            label = label("Custom"),
        )

    }
}

/**
 * Actions for the current selection, docked at the bottom of the canvas.
 *
 * On desktop these live in menus and the keyboard; on a phone they have to be
 * one thumb-tap away, which is why they float over the canvas instead.
 */
@Composable
private fun SelectionActionBar(
    app: AndroidAppState,
    controller: EditorController,
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    if (!state.hasSelection) {
        Surface(
            modifier = modifier,
            color = palette.chromePanel.copy(alpha = 0.94f),
            shape = RoundedCornerShape(50),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Tap to select  ·  drag to pan  ·  pinch to zoom",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                )
            }
        }
        return
    }

    Surface(
        modifier = modifier,
        color = palette.chromePanel.copy(alpha = 0.96f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionChip("Edit") { app.sheet = MobileSheet.PROPERTIES }
            ActionChip("⌗") { app.sheet = MobileSheet.ARRANGE }
            ActionChip("⧉") { controller.duplicateSelection() }
            ActionChip("★") { app.beginSavePrefab() }
            ActionChip("▲") { controller.bringForward() }
            ActionChip("▼") { controller.sendBackward() }
            state.primaryElement?.let { element ->
                ActionChip(if (element.locked) "🔒" else "🔓") {
                    controller.setLocked(element.id, !element.locked)
                }
            }
            ActionChip("✕", tint = com.mcguidesigner.styles.theme.ErrorRed) { app.requestDeleteSelection() }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = tint ?: palette.chromeText,
        )
    }
}
