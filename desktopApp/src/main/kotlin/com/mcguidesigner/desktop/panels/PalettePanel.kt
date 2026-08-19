@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.mcguidesigner.desktop.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.catalog.ElementDefinition
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.model.CanvasBackdrop
import com.mcguidesigner.core.model.CanvasSpec
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.IntPoint
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.styles.canvas.GuiPreview
import com.mcguidesigner.styles.render.TextureCache
import com.mcguidesigner.styles.theme.LocalSkinPalette

/**
 * The component library.
 *
 * Clicking a component arms it for placement (the next canvas click drops it
 * exactly where the pointer is); double-clicking drops it straight into the
 * middle of the canvas for quick assembly.  Each entry renders a live
 * thumbnail using the same skin as the canvas, so what you pick is literally
 * what you get.
 */
@Composable
fun PalettePanel(
    controller: EditorController,
    state: EditorState,
    textures: TextureCache,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    var query by remember { mutableStateOf("") }
    val results = remember(query, state.edition) { ElementCatalog.search(query, state.edition) }
    val grouped = remember(results) { results.groupBy { it.category } }

    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Search components") },
            modifier = Modifier.fillMaxWidth().padding(10.dp),
        )

        state.pendingPlacementType?.let { pending ->
            val definition = ElementCatalog[pending]
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.accentMuted)
                    .clickable { controller.armPlacement(null) }
                    .padding(8.dp),
            ) {
                Text(
                    "Click the canvas to place '${definition?.displayName ?: pending}'  ·  click here to cancel",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeText,
                )
            }
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            grouped.forEach { (category, definitions) ->
                item(key = "header-${category.name}") {
                    Text(
                        text = category.displayName.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.chromeTextMuted,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 6.dp),
                    )
                }
                items(definitions, key = { it.typeId }) { definition ->
                    PaletteEntry(
                        definition = definition,
                        edition = state.edition,
                        textures = textures,
                        armed = state.pendingPlacementType == definition.typeId,
                        onArm = { controller.armPlacement(definition.typeId) },
                        onDropInCentre = {
                            controller.addElement(
                                definition.typeId,
                                IntPoint(state.project.canvas.width / 2, state.project.canvas.height / 2),
                                centreOnPoint = true,
                            )
                        },
                    )
                }
            }
            item { Box(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun PaletteEntry(
    definition: ElementDefinition,
    edition: Edition,
    textures: TextureCache,
    armed: Boolean,
    onArm: () -> Unit,
    onDropInCentre: () -> Unit,
) {
    val palette = LocalSkinPalette.current

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (armed) palette.accentMuted else palette.chromePanelAlt)
            .border(
                1.dp,
                if (armed) palette.accent else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .combinedClickable(onClick = onArm, onDoubleClick = onDropInCentre)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ComponentThumbnail(definition, edition, textures)
        Column(Modifier.weight(1f)) {
            Text(
                definition.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = palette.chromeText,
            )
            Text(
                definition.description.ifBlank { definition.typeId },
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
                maxLines = 2,
            )
        }
    }
}

/**
 * Live thumbnail of one component, drawn with the real skin.
 *
 * The single-element throwaway project is remembered per definition so this
 * costs one allocation per component, not one per frame.
 */
@Composable
private fun ComponentThumbnail(
    definition: ElementDefinition,
    edition: Edition,
    textures: TextureCache,
) {
    val palette = LocalSkinPalette.current
    val size = definition.defaultSizeFor(edition)
    val sample = remember(definition.typeId, edition) {
        GuiProject(
            id = "thumb-${definition.typeId}",
            name = definition.displayName,
            edition = edition,
            canvas = CanvasSpec(
                width = size.width,
                height = size.height,
                gridSize = 0,
                backdrop = CanvasBackdrop.NONE,
            ),
            elements = listOf(
                GuiElement(
                    id = "thumb",
                    type = definition.typeId,
                    name = definition.displayName,
                    bounds = IntRect(0, 0, size.width, size.height),
                    props = definition.defaultProps(edition),
                ),
            ),
        )
    }

    // Fit the component into a fixed 54x34 chip.
    val zoom = remember(sample) {
        minOf(54f / size.width.coerceAtLeast(1), 34f / size.height.coerceAtLeast(1)).coerceIn(0.15f, 3f)
    }

    Box(
        Modifier
            .size(58.dp, 38.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(palette.chromeBackground),
        contentAlignment = Alignment.Center,
    ) {
        GuiPreview(
            project = sample,
            textures = textures,
            modifier = Modifier.fillMaxSize(),
            zoom = zoom,
            drawBackdrop = false,
        )
    }
}
