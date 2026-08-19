package com.mcguidesigner.desktop.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.validation.Severity
import com.mcguidesigner.desktop.widgets.ToolbarButton
import com.mcguidesigner.styles.theme.ErrorRed
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.WarningAmber

/**
 * The layer tree.
 *
 * Order in this list is paint order: the top entry is the front-most element,
 * matching every other design tool, even though the underlying model stores
 * back-to-front.
 */
@Composable
fun LayersPanel(
    controller: EditorController,
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarButton("▲▲", enabled = state.hasSelection, hint = "Bring to front") { controller.bringToFront() }
            ToolbarButton("▲", enabled = state.hasSelection, hint = "Bring forward") { controller.bringForward() }
            ToolbarButton("▼", enabled = state.hasSelection, hint = "Send backward") { controller.sendBackward() }
            ToolbarButton("▼▼", enabled = state.hasSelection, hint = "Send to back") { controller.sendToBack() }
            Box(Modifier.weight(1f))
            ToolbarButton("⧉", enabled = state.hasSelection, hint = "Duplicate") { controller.duplicateSelection() }
            ToolbarButton("✕", enabled = state.hasSelection, hint = "Delete") { controller.deleteSelection() }
        }
        Divider(color = palette.chromeBorder)

        if (state.project.elements.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No elements yet.\nPick a component on the left.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.chromeTextMuted,
                )
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            // Rendered front-to-back, so reverse the model order.
            layerRows(state.project.elements.reversed(), depth = 0).forEach { row ->
                item(key = row.element.id) {
                    LayerRow(controller, state, row.element, row.depth)
                }
            }
        }
    }
}

private data class LayerRow(val element: GuiElement, val depth: Int)

/** Flattens the tree into display rows, honouring collapsed containers. */
private fun layerRows(nodes: List<GuiElement>, depth: Int): List<LayerRow> = buildList {
    nodes.forEach { node ->
        add(LayerRow(node, depth))
        if (node.children.isNotEmpty()) {
            addAll(layerRows(node.children.reversed(), depth + 1))
        }
    }
}

@Composable
private fun LayerRow(
    controller: EditorController,
    state: EditorState,
    element: GuiElement,
    depth: Int,
) {
    val palette = LocalSkinPalette.current
    val selected = element.id in state.selection
    val severity = state.validation.highestSeverityFor(element.id)
    val definition = ElementCatalog[element.type]

    Row(
        Modifier
            .fillMaxWidth()
            .background(
                when {
                    selected -> palette.selectionFill
                    state.hoveredId == element.id -> palette.chromePanelAlt
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .clickable { controller.select(element.id) }
            .padding(start = (8 + depth * 14).dp, top = 5.dp, bottom = 5.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            definition?.glyph ?: "?",
            style = MaterialTheme.typography.labelMedium,
            color = palette.chromeTextMuted,
            modifier = Modifier.width(16.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                element.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (element.visible) palette.chromeText else palette.chromeTextMuted,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
            Text(
                "${element.bounds.width}x${element.bounds.height}  ·  ${definition?.displayName ?: element.type}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
                maxLines = 1,
            )
        }

        when (severity) {
            Severity.ERROR -> Box(Modifier.size(7.dp).background(ErrorRed))
            Severity.WARNING -> Box(Modifier.size(7.dp).background(WarningAmber))
            else -> Unit
        }

        Text(
            text = if (element.visible) "◉" else "◌",
            style = MaterialTheme.typography.labelMedium,
            color = palette.chromeTextMuted,
            modifier = Modifier
                .clickable { controller.setVisible(element.id, !element.visible) }
                .padding(horizontal = 3.dp),
        )
        Text(
            text = if (element.locked) "🔒" else "🔓",
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
            modifier = Modifier
                .clickable { controller.setLocked(element.id, !element.locked) }
                .padding(horizontal = 3.dp),
        )
    }
}
