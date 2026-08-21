package com.mcguidesigner.desktop.panels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.library.Prefab
import com.mcguidesigner.core.model.CanvasBackdrop
import com.mcguidesigner.core.model.CanvasSpec
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.desktop.AppState
import com.mcguidesigner.styles.canvas.GuiPreview
import com.mcguidesigner.styles.render.TextureCache
import com.mcguidesigner.styles.render.rememberTextureCache
import com.mcguidesigner.styles.theme.ErrorRed
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.SkinRegistry
import com.mcguidesigner.styles.theme.WarningAmber

/**
 * Saved prefabs: groups of elements the user built once and can drop into any
 * later design.
 *
 * Prefabs for the *other* edition are still listed, greyed and labelled, rather
 * than hidden.  Someone who built a settings row for Bedrock and is now working
 * in Java usually wants it - they just need to be told it will need checking.
 */
@Composable
fun PrefabsPanel(
    app: AppState,
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    var query by remember { mutableStateOf("") }
    val library = app.prefabs
    val results = remember(query, library) { library.search(query) }
    val (native, foreign) = results.partition { it.edition == state.edition }

    Column(modifier.fillMaxSize()) {
        Button(
            onClick = { app.beginSavePrefab() },
            enabled = state.hasSelection,
            modifier = Modifier.fillMaxWidth().padding(10.dp),
        ) {
            Text(
                if (state.selection.size > 1) {
                    "Save ${state.selection.size} elements as a prefab"
                } else {
                    "Save selection as a prefab"
                },
            )
        }

        if (library.prefabs.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Search prefabs") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            )
        }

        if (library.prefabs.isEmpty()) {
            EmptyPrefabs(Modifier.fillMaxSize().padding(20.dp))
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            if (native.isNotEmpty()) {
                item(key = "header-native") { SectionHeader(state.edition.displayName.uppercase()) }
                items(native, key = { it.id }) { prefab ->
                    PrefabCard(app, prefab, foreign = false)
                }
            }
            if (foreign.isNotEmpty()) {
                item(key = "header-foreign") { SectionHeader("BUILT FOR THE OTHER EDITION") }
                items(foreign, key = { it.id }) { prefab ->
                    PrefabCard(app, prefab, foreign = true)
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val palette = LocalSkinPalette.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = palette.chromeTextMuted,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun EmptyPrefabs(modifier: Modifier) {
    val palette = LocalSkinPalette.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "No prefabs yet",
            style = MaterialTheme.typography.titleSmall,
            color = palette.chromeText,
        )
        Text(
            "Select a few elements on the canvas - a header and its buttons, a row of " +
                "slots - and save them here. They come back as one piece, textures included, " +
                "in any project.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.chromeTextMuted,
        )
    }
}

@Composable
private fun PrefabCard(app: AppState, prefab: Prefab, foreign: Boolean) {
    val palette = LocalSkinPalette.current
    var confirmingDelete by remember(prefab.id) { mutableStateOf(false) }
    val interactions = remember(prefab.id) { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.015f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "prefabCard",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .scale(scale)
            .hoverable(interactions)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.chromePanelAlt)
            .border(
                1.dp,
                if (foreign) WarningAmber.copy(alpha = 0.4f) else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable { app.insertPrefab(prefab) }
            .padding(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrefabThumbnail(prefab)
            Column(Modifier.weight(1f)) {
                Text(
                    prefab.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.chromeText,
                    maxLines = 1,
                )
                Text(
                    buildString {
                        append("${prefab.elementCount} element(s)")
                        append("  ·  ${prefab.size.width}x${prefab.size.height}")
                        if (prefab.textures.isNotEmpty()) append("  ·  ${prefab.textures.size} texture(s)")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                    maxLines = 1,
                )
                if (foreign) {
                    Text(
                        "Built for ${prefab.edition.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber,
                        maxLines = 1,
                    )
                }
            }
            TextButton(onClick = { confirmingDelete = !confirmingDelete }) {
                Text(if (confirmingDelete) "Keep" else "Delete", style = MaterialTheme.typography.labelSmall)
            }
        }

        if (prefab.description.isNotBlank()) {
            Text(
                prefab.description,
                style = MaterialTheme.typography.bodySmall,
                color = palette.chromeTextMuted,
                maxLines = 2,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        AnimatedVisibility(
            visible = confirmingDelete,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Delete '${prefab.name}' permanently?",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    confirmingDelete = false
                    app.deletePrefab(prefab.id)
                }) {
                    Text("Delete", color = ErrorRed, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * Live thumbnail of a prefab, drawn with the skin it was built for.
 *
 * Rendering it in its *own* edition rather than the current one is deliberate:
 * the card is telling you what you saved, not what it will look like after a
 * port.
 */
@Composable
private fun PrefabThumbnail(prefab: Prefab) {
    val palette = LocalSkinPalette.current
    val size = prefab.size
    val sample = remember(prefab.id) {
        GuiProject(
            id = "prefab-${prefab.id}",
            name = prefab.name,
            edition = prefab.edition,
            canvas = CanvasSpec(
                width = size.width.coerceAtLeast(1),
                height = size.height.coerceAtLeast(1),
                gridSize = 0,
                backdrop = CanvasBackdrop.NONE,
            ),
            elements = prefab.elements,
            textures = prefab.textures,
        )
    }
    val textures: TextureCache = rememberTextureCache(sample)
    val zoom = remember(sample) {
        minOf(
            64f / size.width.coerceAtLeast(1),
            44f / size.height.coerceAtLeast(1),
        ).coerceIn(0.1f, 4f)
    }

    Box(
        Modifier
            .size(68.dp, 48.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(palette.chromeBackground),
        contentAlignment = Alignment.Center,
    ) {
        GuiPreview(
            project = sample,
            textures = textures,
            modifier = Modifier.fillMaxSize(),
            zoom = zoom,
            drawBackdrop = false,
            skin = SkinRegistry.forEdition(prefab.edition),
        )
    }
}
