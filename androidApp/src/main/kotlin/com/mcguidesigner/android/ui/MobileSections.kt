package com.mcguidesigner.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcguidesigner.android.AndroidAppState
import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.validation.Severity
import com.mcguidesigner.exporters.CodeGenerator
import com.mcguidesigner.exporters.CodeTarget
import com.mcguidesigner.styles.canvas.GuiDemoView
import com.mcguidesigner.styles.render.TextureCache
import com.mcguidesigner.styles.theme.ErrorRed
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.WarningAmber

/**
 * Layer list, sized for fingers.
 *
 * Same data as the desktop layers dock, but every row is a full-height touch
 * target with its controls spaced out, and reordering is done with large
 * buttons rather than drag-and-drop.
 */
@Composable
fun MobileLayersSection(
    controller: EditorController,
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    val rows = remember(state.project.elements) { flatten(state.project.elements.reversed(), 0) }

    Column(modifier.background(palette.chromeBackground)) {
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No elements yet.\nTap Add to place your first component.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.chromeTextMuted,
                )
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.element.id }) { row ->
                MobileLayerRow(controller, state, row.element, row.depth)
                Divider(color = palette.chromeBorder)
            }
        }
    }
}

private data class LayerEntry(val element: GuiElement, val depth: Int)

private fun flatten(nodes: List<GuiElement>, depth: Int): List<LayerEntry> = buildList {
    nodes.forEach { node ->
        add(LayerEntry(node, depth))
        if (node.children.isNotEmpty()) addAll(flatten(node.children.reversed(), depth + 1))
    }
}

@Composable
private fun MobileLayerRow(
    controller: EditorController,
    state: EditorState,
    element: GuiElement,
    depth: Int,
) {
    val palette = LocalSkinPalette.current
    val selected = element.id in state.selection
    val definition = ElementCatalog[element.type]
    val severity = state.validation.highestSeverityFor(element.id)

    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) palette.selectionFill else Color.Transparent)
            .clickable { controller.select(element.id) }
            .padding(start = (14 + depth * 16).dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(definition?.glyph ?: "?", style = MaterialTheme.typography.titleMedium, color = palette.chromeTextMuted)
        Column(Modifier.weight(1f)) {
            Text(element.name, style = MaterialTheme.typography.bodyMedium, color = palette.chromeText, maxLines = 1)
            Text(
                "${definition?.displayName ?: element.type}  ·  " +
                    "${element.bounds.x},${element.bounds.y}  ${element.bounds.width}x${element.bounds.height}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        when (severity) {
            Severity.ERROR -> Box(Modifier.size(10.dp).background(ErrorRed))
            Severity.WARNING -> Box(Modifier.size(10.dp).background(WarningAmber))
            else -> Unit
        }
        Text(
            if (element.visible) "◉" else "◌",
            style = MaterialTheme.typography.titleMedium,
            color = palette.chromeTextMuted,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { controller.setVisible(element.id, !element.visible) }
                .padding(12.dp),
        )
        Text(
            if (element.locked) "🔒" else "🔓",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.chromeTextMuted,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { controller.setLocked(element.id, !element.locked) }
                .padding(12.dp),
        )
    }
}

/**
 * Preview section.
 *
 * The mobile preview leads with the desktop/mobile layout switch, because the
 * whole point of authoring a Bedrock screen on a phone is checking it against
 * the device it will actually run on.
 */
@Composable
fun MobilePreviewSection(
    controller: EditorController,
    state: EditorState,
    textures: TextureCache,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    val states = if (state.edition == Edition.BEDROCK) InteractionState.touchStates else InteractionState.entries

    Column(modifier.background(palette.chromeBackground)) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.previewState == null,
                onClick = { controller.setPreviewState(null) },
                label = { Text("Live") },
            )
            states.forEach { candidate ->
                FilterChip(
                    selected = state.previewState == candidate,
                    onClick = { controller.setPreviewState(candidate) },
                    label = { Text(candidate.displayName) },
                )
            }
            FilterChip(
                selected = false,
                enabled = !state.demo.isClean,
                onClick = { controller.resetDemo() },
                label = { Text("Reset") },
            )
        }

        // What the demo just did. On a phone the toolbar is the only place with
        // room for it, and without it a press that changes something off the
        // visible part of the screen looks like a press that did nothing.
        state.demo.lastAction?.takeIf { state.previewState == null }?.let { action ->
            Text(
                action,
                style = MaterialTheme.typography.labelSmall,
                color = palette.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }

        Box(Modifier.fillMaxSize().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            GuiDemoView(
                project = state.project,
                textures = textures,
                demo = state.demo,
                onDemo = controller::setDemo,
                modifier = Modifier.fillMaxSize(),
                forcedState = state.previewState,
                baseZoom = state.project.canvas.guiScale.toFloat().coerceAtLeast(1f),
                playAnimations = state.settings.playAnimations,
            )
        }
    }
}

/**
 * The code section: the same generators the desktop uses, presented as a
 * scrollable, selectable listing with a chip row for the language.
 */
@Composable
fun MobileCodeSection(
    app: AndroidAppState,
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    val targets = remember(state.edition) { CodeGenerator.targetsFor(state.edition) }
    val generated = remember(state.project, app.codeTarget) {
        val target = if (app.codeTarget in targets) app.codeTarget else CodeTarget.HTML_CSS
        CodeGenerator.generate(state.project, target)
    }

    Column(modifier.background(palette.chromeBackground)) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            targets.forEach { target ->
                FilterChip(
                    selected = generated.target == target,
                    onClick = { app.codeTarget = target },
                    label = { Text(target.language.uppercase()) },
                )
            }
        }

        Text(
            "${generated.fileName}  ·  ${generated.lineCount} lines",
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        Text(
            generated.target.description,
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
        )
        Divider(color = palette.chromeBorder)

        SelectionContainer(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp),
            ) {
                Text(
                    generated.source,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = palette.chromeText,
                )
            }
        }
    }
}
