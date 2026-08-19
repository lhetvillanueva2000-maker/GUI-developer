package com.mcguidesigner.desktop.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.model.Insets
import com.mcguidesigner.core.model.IntPoint
import com.mcguidesigner.core.model.TextureAsset
import com.mcguidesigner.desktop.AppState
import com.mcguidesigner.styles.render.rememberTextureCache
import com.mcguidesigner.styles.theme.LocalSkinPalette

/**
 * Imported-texture manager.
 *
 * Textures live inside the project file as base64, so a `.mcgui` document is
 * always self-contained; this panel is where they are brought in, given
 * nine-slice insets, and dropped onto the canvas.
 */
@Composable
fun AssetsPanel(
    app: AppState,
    controller: EditorController,
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    val cache = rememberTextureCache(state.project)
    var selectedId by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { app.importTextures() }) { Text("Import images...") }
            Text(
                "${state.project.textures.size} in project",
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
            )
        }
        Divider(color = palette.chromeBorder)

        if (state.project.textures.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No textures yet.\n\nImport PNG, JPG, GIF or WebP files and use them " +
                        "as button skins, panel backgrounds, icons or item previews.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.chromeTextMuted,
                )
            }
            return@Column
        }

        LazyColumn(Modifier.weight(1f)) {
            items(state.project.textures, key = { it.id }) { asset ->
                TextureRow(
                    asset = asset,
                    bitmap = cache.resolve(asset.id),
                    selected = selectedId == asset.id,
                    usageCount = usageCount(state, asset.id),
                    onSelect = { selectedId = if (selectedId == asset.id) null else asset.id },
                    onPlace = {
                        val id = controller.addElement(
                            ElementCatalog.IMAGE_PLACEHOLDER,
                            IntPoint(state.project.canvas.width / 2, state.project.canvas.height / 2),
                            centreOnPoint = true,
                        )
                        if (id != null) {
                            controller.setProp(
                                id, "texture",
                                com.mcguidesigner.core.model.TextureValue(asset.id),
                            )
                            controller.setBounds(
                                id,
                                com.mcguidesigner.core.model.IntRect(
                                    state.project.canvas.width / 2 - asset.width / 2,
                                    state.project.canvas.height / 2 - asset.height / 2,
                                    asset.width.coerceAtLeast(2),
                                    asset.height.coerceAtLeast(2),
                                ),
                                label = "Place texture",
                            )
                        }
                    },
                    onDelete = { controller.removeTexture(asset.id) },
                )
            }
        }

        selectedId?.let { id ->
            state.project.texture(id)?.let { asset ->
                Divider(color = palette.chromeBorder)
                NineSliceEditor(controller, asset)
            }
        }
    }
}

private fun usageCount(state: EditorState, assetId: String): Int =
    state.project.allElements.count { element ->
        element.props.values.any { it is com.mcguidesigner.core.model.TextureValue && it.assetId == assetId } ||
            element.stateOverrides.values.any { overrides ->
                overrides.values.any { it is com.mcguidesigner.core.model.TextureValue && it.assetId == assetId }
            }
    }

@Composable
private fun TextureRow(
    asset: TextureAsset,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    selected: Boolean,
    usageCount: Int,
    onSelect: () -> Unit,
    onPlace: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) palette.selectionFill else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(palette.chromeBackground)
                .border(1.dp, palette.chromeBorder, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = asset.name,
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize().padding(2.dp),
                )
            } else {
                Text("?", color = palette.chromeTextMuted)
            }
        }

        Column(Modifier.weight(1f)) {
            Text(asset.name, style = MaterialTheme.typography.labelMedium, color = palette.chromeText, maxLines = 1)
            Text(
                "${asset.width}x${asset.height} ${asset.format.uppercase()}  ·  " +
                    "${asset.approximateBytes / 1024} KB  ·  used ${usageCount}x",
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
                fontFamily = FontFamily.Monospace,
            )
        }

        Text(
            "＋",
            style = MaterialTheme.typography.labelLarge,
            color = palette.accent,
            modifier = Modifier.clickable(onClick = onPlace).padding(6.dp),
        )
        Text(
            "✕",
            style = MaterialTheme.typography.labelLarge,
            color = com.mcguidesigner.styles.theme.ErrorRed,
            modifier = Modifier.clickable(onClick = onDelete).padding(6.dp),
        )
    }
}

/**
 * Nine-slice inset editor.
 *
 * Setting insets is what turns a small imported PNG into a stretchable panel
 * or button skin; without it, custom art can only be used at its native size.
 */
@Composable
private fun NineSliceEditor(controller: EditorController, asset: TextureAsset) {
    val palette = LocalSkinPalette.current
    Column(Modifier.fillMaxWidth().padding(10.dp)) {
        Text("Nine-slice insets", style = MaterialTheme.typography.labelMedium, color = palette.chromeText)
        Text(
            "Corners stay pixel-exact; the edges and centre stretch. " +
                "Leave all four at 0 to stretch the whole image.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
        )
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InsetField("L", asset.nineSlice.left, asset.width / 2, Modifier.weight(1f)) { value ->
                controller.updateTexture(asset.id) { it.copy(nineSlice = it.nineSlice.copy(left = value)) }
            }
            InsetField("T", asset.nineSlice.top, asset.height / 2, Modifier.weight(1f)) { value ->
                controller.updateTexture(asset.id) { it.copy(nineSlice = it.nineSlice.copy(top = value)) }
            }
            InsetField("R", asset.nineSlice.right, asset.width / 2, Modifier.weight(1f)) { value ->
                controller.updateTexture(asset.id) { it.copy(nineSlice = it.nineSlice.copy(right = value)) }
            }
            InsetField("B", asset.nineSlice.bottom, asset.height / 2, Modifier.weight(1f)) { value ->
                controller.updateTexture(asset.id) { it.copy(nineSlice = it.nineSlice.copy(bottom = value)) }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val quarterW = (asset.width / 4).coerceAtLeast(1)
                    val quarterH = (asset.height / 4).coerceAtLeast(1)
                    controller.updateTexture(asset.id) {
                        it.copy(nineSlice = Insets(quarterW, quarterH, quarterW, quarterH))
                    }
                },
            ) { Text("Auto (quarter)") }
            Button(
                onClick = {
                    controller.updateTexture(asset.id) { it.copy(nineSlice = Insets.Zero) }
                },
            ) { Text("Clear") }
        }
        Box(Modifier.height(6.dp))
    }
}

@Composable
private fun InsetField(label: String, value: Int, max: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { entered ->
            text = entered.filter { it.isDigit() }
            text.toIntOrNull()?.let { onChange(it.coerceIn(0, max.coerceAtLeast(0))) }
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
    )
}
