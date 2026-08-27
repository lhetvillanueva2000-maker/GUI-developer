package com.mcguidesigner.web

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.model.walkAll
import com.mcguidesigner.exporters.ExportManager
import com.mcguidesigner.exporters.ExportTarget
import com.mcguidesigner.styles.canvas.DesignSurface
import com.mcguidesigner.styles.editor.DocumentTabs
import com.mcguidesigner.styles.editor.TabInfo
import com.mcguidesigner.styles.layout.AdaptiveMetrics
import com.mcguidesigner.styles.layout.LocalAdaptive
import com.mcguidesigner.styles.notice.NoticeStrip
import com.mcguidesigner.styles.render.TextureCache
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.SkinPalette

/**
 * The editor, as a browser page.
 *
 * The canvas itself is [DesignSurface] out of `:styles` - the same component,
 * with the same gestures, that the desktop build uses - so what is written
 * here is only the furniture around it: the palette of things to place, the
 * inspector for what is selected, the toolbar, and the export sheet.
 *
 * That split is the same one the other two shells make. A desktop window has
 * dockable panels and a phone has bottom sheets, and neither arrangement can
 * be reused by the other; what they all share is the surface in the middle,
 * which is the part that would be a disaster to have three copies of.
 */
@Composable
fun WebEditor(
    app: WebAppState,
    controller: EditorController,
    state: EditorState,
    textures: TextureCache,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    val metrics = LocalAdaptive.current
    val wide = metrics.widthDp >= 900.dp

    Column(modifier.background(palette.chromeBackground)) {

        EditorToolbar(app, controller, state, palette)

        if (app.notices.isNotEmpty()) {
            NoticeStrip(
                notices = app.notices.toList(),
                expanded = app.noticeExpanded,
                onExpandedChange = { app.noticeExpanded = it },
                onDismiss = { app.dismissNotice(it.id) },
                metrics = metrics,
            )
        }

        DocumentTabs(
            tabs = app.tabs.map {
                val s = it.controller.current
                TabInfo(title = s.documentTitle, edition = s.edition, dirty = s.dirty)
            },
            active = app.activeTab,
            onSelect = app::selectTab,
            onClose = app::closeTab,
            onAdd = { app.addTab(state.edition) },
            metrics = metrics,
        )

        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (wide) {
                PalettePane(controller, state, palette, Modifier.width(188.dp).fillMaxHeight())
            }

            DesignSurface(
                controller = controller,
                state = state,
                textures = textures,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )

            if (wide) {
                InspectorPane(controller, state, palette, Modifier.width(232.dp).fillMaxHeight())
            }
        }

        // Narrow window: the two panes become one scrolling strip under the
        // canvas rather than being dropped. A 700px browser window is a real
        // way to use this, and an editor with no way to add an element is not
        // a smaller editor, it is a viewer.
        if (!wide) {
            NarrowPanes(controller, state, palette, Modifier.height(196.dp).fillMaxWidth())
        }

        StatusBar(app, state, palette)
    }

    if (app.exportOpen) {
        ExportSheet(app, state, palette) { app.exportOpen = false }
    }
}

/* -------------------------------------------------------------------------- */

@Composable
private fun EditorToolbar(
    app: WebAppState,
    controller: EditorController,
    state: EditorState,
    palette: SkinPalette,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.chromePanel)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ChromeButton("Home", palette) { app.goHome() }
        Divider(palette)
        ChromeButton("Open", palette) { app.openProject() }
        ChromeButton("Save", palette) { app.saveProject() }
        ChromeButton("Export", palette) { app.exportOpen = true }
        Divider(palette)
        ChromeButton("Undo", palette, enabled = state.canUndo) { controller.undo() }
        ChromeButton("Redo", palette, enabled = state.canRedo) { controller.redo() }
        Divider(palette)
        ChromeButton("Duplicate", palette, enabled = state.hasSelection) { controller.duplicateSelection() }
        ChromeButton("Delete", palette, enabled = state.hasSelection) { controller.deleteSelection() }
        Divider(palette)
        ChromeButton(if (state.showGrid) "Grid on" else "Grid off", palette, active = state.showGrid) {
            controller.toggleGrid()
        }
        ChromeButton("Fit", palette) { controller.resetView() }

        Spacer(Modifier.weight(1f))

        Text(
            "${(state.zoom * 100).toInt()}%",
            style = TextStyle(color = palette.chromeTextMuted, fontSize = 12.sp),
        )
        ChromeButton("−", palette) { controller.zoomBy(0.8f) }
        ChromeButton("+", palette) { controller.zoomBy(1.25f) }
    }
}

/**
 * Everything that can be placed, for this edition.
 *
 * Reads straight from [ElementCatalog], grouped as the catalog groups it, so a
 * new element type appears here by being registered and nowhere else - which is
 * the same contract the desktop palette and the Android sheet both honour.
 */
@Composable
private fun PalettePane(
    controller: EditorController,
    state: EditorState,
    palette: SkinPalette,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(palette.chromePanel)
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PaneHeading("Add", palette)

        for ((category, definitions) in ElementCatalog.grouped(state.edition)) {
            Text(
                category.displayName.uppercase(),
                style = TextStyle(
                    color = palette.chromeTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
            for (definition in definitions) {
                val pending = state.pendingPlacementType == definition.typeId
                ChromeButton(
                    label = definition.displayName,
                    palette = palette,
                    active = pending,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Arm it rather than dropping it at a guessed position:
                    // the next click on the canvas says where, which is how
                    // both other shells place things too.
                    controller.armPlacement(if (pending) null else definition.typeId)
                }
            }
        }
    }
}

/** Position, size and name for whatever is selected. */
@Composable
private fun InspectorPane(
    controller: EditorController,
    state: EditorState,
    palette: SkinPalette,
    modifier: Modifier = Modifier,
) {
    val element = state.primaryElement
    // The element's own bounds, not `absoluteBounds`. `setBounds` stores what
    // it is given verbatim, and an element's stored bounds are relative to its
    // parent's content box - so writing an absolute rectangle back would move
    // anything nested by its parent's origin every time a field was touched.
    val bounds = element?.bounds

    Column(
        modifier
            .background(palette.chromePanel)
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PaneHeading("Inspector", palette)

        if (element == null || bounds == null) {
            Text(
                "Nothing selected. Click an element on the canvas, or pick one " +
                    "from Add and then click where it goes.",
                style = TextStyle(color = palette.chromeTextMuted, fontSize = 12.sp),
            )
            return@Column
        }

        Text(
            element.name,
            style = TextStyle(color = palette.chromeText, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            ElementCatalog[element.type]?.displayName ?: element.type,
            style = TextStyle(color = palette.chromeTextMuted, fontSize = 11.sp),
        )

        Spacer(Modifier.height(4.dp))

        NumberField("X", bounds.x, palette) {
            controller.setBounds(element.id, bounds.copy(x = it), label = "Move")
        }
        NumberField("Y", bounds.y, palette) {
            controller.setBounds(element.id, bounds.copy(y = it), label = "Move")
        }
        NumberField("Width", bounds.width, palette) {
            controller.setBounds(element.id, bounds.copy(width = it.coerceAtLeast(1)))
        }
        NumberField("Height", bounds.height, palette) {
            controller.setBounds(element.id, bounds.copy(height = it.coerceAtLeast(1)))
        }

        Spacer(Modifier.height(6.dp))
        PaneHeading("Nudge", palette)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ChromeButton("←", palette) { controller.nudgeSelection(-1, 0) }
            ChromeButton("↑", palette) { controller.nudgeSelection(0, -1) }
            ChromeButton("↓", palette) { controller.nudgeSelection(0, 1) }
            ChromeButton("→", palette) { controller.nudgeSelection(1, 0) }
        }

        if (state.validation.issues.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            PaneHeading("Issues", palette)
            for (issue in state.validation.issues.take(6)) {
                Text(
                    "• ${issue.message}",
                    style = TextStyle(color = palette.chromeTextMuted, fontSize = 11.sp),
                )
            }
        }
    }
}

/** The palette and the inspector side by side, for a narrow window. */
@Composable
private fun NarrowPanes(
    controller: EditorController,
    state: EditorState,
    palette: SkinPalette,
    modifier: Modifier = Modifier,
) {
    Row(modifier.background(palette.chromePanel)) {
        PalettePane(controller, state, palette, Modifier.weight(1f).fillMaxHeight())
        Box(Modifier.width(1.dp).fillMaxHeight().background(palette.chromeBorder))
        InspectorPane(controller, state, palette, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun StatusBar(app: WebAppState, state: EditorState, palette: SkinPalette) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.chromePanelAlt)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            app.status ?: state.statusMessage ?: "${state.project.elements.walkAll().count()} elements",
            style = TextStyle(color = palette.chromeTextMuted, fontSize = 11.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${state.project.canvas.size.width} × ${state.project.canvas.size.height}",
            style = TextStyle(color = palette.chromeTextMuted, fontSize = 11.sp),
        )
    }
}

/**
 * The export sheet.
 *
 * Offers exactly what [ExportManager] says is available for this edition, and
 * nothing else - so the list cannot promise a format the exporter does not
 * actually produce.
 */
@Composable
private fun ExportSheet(
    app: WebAppState,
    state: EditorState,
    palette: SkinPalette,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.chromePanel)
                .border(1.dp, palette.chromeBorder, RoundedCornerShape(14.dp))
                // Swallows the click so tapping the sheet does not close it
                // through the scrim underneath. Enabled, with an empty lambda:
                // a *disabled* clickable does not consume pointer input at all,
                // so it would have looked like a guard and been none.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Export",
                style = TextStyle(color = palette.chromeText, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                "One file downloads as itself. Anything with more than one file " +
                    "downloads as a zip, keeping its folder layout.",
                style = TextStyle(color = palette.chromeTextMuted, fontSize = 12.sp),
            )

            Spacer(Modifier.height(4.dp))

            for (target in ExportManager.availableTargets(state.edition)) {
                ChromeButton(
                    label = target.displayName,
                    palette = palette,
                    active = target == app.exportTarget,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    app.exportTarget = target
                    app.runExport(target)
                    onClose()
                }
            }

            Spacer(Modifier.height(4.dp))
            ChromeButton("Cancel", palette, modifier = Modifier.fillMaxWidth(), onClick = onClose)
        }
    }
}

/* ----------------------------- small pieces ------------------------------- */

@Composable
private fun PaneHeading(text: String, palette: SkinPalette) {
    Text(
        text.uppercase(),
        style = TextStyle(
            color = palette.chromeText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        ),
    )
}

@Composable
private fun Divider(palette: SkinPalette) {
    Box(Modifier.width(1.dp).height(20.dp).background(palette.chromeBorder))
}

@Composable
private fun ChromeButton(
    label: String,
    palette: SkinPalette,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val fill = when {
        !enabled -> palette.chromePanelAlt
        active -> palette.accent
        else -> palette.chromePanelAlt
    }
    val ink = when {
        !enabled -> palette.chromeTextMuted.copy(alpha = 0.45f)
        active -> palette.textOnAccent
        else -> palette.chromeText
    }
    Box(
        modifier
            .clip(RoundedCornerShape(7.dp))
            .background(fill)
            .border(1.dp, if (active) palette.accent else palette.chromeBorder, RoundedCornerShape(7.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(color = ink, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A number you can type into.
 *
 * The text is held locally while it is being edited and only committed when it
 * parses, so clearing the field to retype it does not momentarily set the
 * value to zero and move the element - which is what a naive
 * `onValueChange { commit(it.toIntOrNull() ?: 0) }` does, and it makes the
 * field unusable with a keyboard.
 */
@Composable
private fun NumberField(
    label: String,
    value: Int,
    palette: SkinPalette,
    onCommit: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = TextStyle(color = palette.chromeTextMuted, fontSize = 11.sp),
            modifier = Modifier.width(52.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(palette.chromeBackground)
                .border(1.dp, palette.chromeBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { next ->
                    text = next
                    next.trim().toIntOrNull()?.let(onCommit)
                },
                singleLine = true,
                textStyle = TextStyle(color = palette.chromeText, fontSize = 12.sp),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
