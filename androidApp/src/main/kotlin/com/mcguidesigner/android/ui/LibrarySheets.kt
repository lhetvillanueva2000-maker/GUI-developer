@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mcguidesigner.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcguidesigner.android.AndroidAppState
import com.mcguidesigner.android.MobileSheet
import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.catalog.ElementDefinition
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.library.LibraryTexture
import com.mcguidesigner.core.library.Prefab
import com.mcguidesigner.core.model.CanvasBackdrop
import com.mcguidesigner.core.model.CanvasSpec
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.IntPoint
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.packs.PackTexture
import com.mcguidesigner.core.packs.PackTextureRole
import com.mcguidesigner.styles.canvas.GuiPreview
import com.mcguidesigner.styles.render.decodeImageBitmap
import com.mcguidesigner.styles.render.rememberTextureCache
import com.mcguidesigner.styles.theme.ErrorRed
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.SkinRegistry
import com.mcguidesigner.styles.theme.ThemeMode
import com.mcguidesigner.styles.theme.WarningAmber
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// ---------------------------------------------------------------------------
// Prefabs
// ---------------------------------------------------------------------------

/**
 * Saved prefabs, and the control that creates one from the current selection.
 *
 * Prefabs built for the other edition are still listed, marked rather than
 * hidden: a Bedrock settings row is usually still what you want when you are
 * porting a screen to Java.
 */
@Composable
fun PrefabsSheet(app: AndroidAppState, state: EditorState) {
    val context = LocalContext.current
    val palette = LocalSkinPalette.current
    var name by remember { mutableStateOf(app.prefabDraftName) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        LibrarySheetTitle("Prefabs", "${app.prefabs.prefabs.size} saved")

        if (state.hasSelection) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name this prefab") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    app.savePrefab(context, name.ifBlank { "Prefab" })
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Save ${state.selection.size} selected element(s)")
            }
        } else {
            Text(
                "Select elements on the canvas to save them as a reusable piece. " +
                    "Tap any saved prefab below to drop it into this screen.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.chromeTextMuted,
            )
        }

        Spacer(Modifier.height(14.dp))
        Divider(color = palette.chromeBorder)

        if (app.prefabs.prefabs.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Nothing saved yet.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.chromeTextMuted,
            )
            return@Column
        }

        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(app.prefabs.prefabs, key = { it.id }) { prefab ->
                PrefabRow(
                    prefab = prefab,
                    foreign = prefab.edition != state.edition,
                    onInsert = { app.insertPrefab(prefab) },
                    onDelete = { app.deletePrefab(context, prefab.id) },
                )
            }
        }
    }
}

@Composable
private fun PrefabRow(
    prefab: Prefab,
    foreign: Boolean,
    onInsert: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    var confirming by remember(prefab.id) { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.chromePanelAlt)
            .border(
                1.dp,
                if (foreign) WarningAmber.copy(alpha = 0.4f) else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onInsert)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PrefabThumbnail(prefab)
        Column(Modifier.weight(1f)) {
            Text(prefab.name, style = MaterialTheme.typography.labelLarge, color = palette.chromeText, maxLines = 1)
            Text(
                "${prefab.elementCount} element(s)  ·  ${prefab.size.width}x${prefab.size.height}" +
                    if (foreign) "  ·  ${prefab.edition.displayName}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (foreign) WarningAmber else palette.chromeTextMuted,
                maxLines = 1,
            )
        }
        TextButton(onClick = { if (confirming) onDelete() else confirming = true }) {
            Text(
                if (confirming) "Sure?" else "✕",
                color = ErrorRed,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

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
    val textures = rememberTextureCache(sample)
    val zoom = remember(sample) {
        minOf(
            72f / size.width.coerceAtLeast(1),
            48f / size.height.coerceAtLeast(1),
        ).coerceIn(0.1f, 4f)
    }

    Box(
        Modifier
            .size(76.dp, 52.dp)
            .clip(RoundedCornerShape(6.dp))
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

// ---------------------------------------------------------------------------
// Texture library
// ---------------------------------------------------------------------------

/** Every texture ever imported, available to every project. */
@Composable
fun LibrarySheet(
    app: AndroidAppState,
    onImportImages: () -> Unit,
    onImportPack: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalSkinPalette.current
    var query by remember { mutableStateOf("") }
    val results = remember(query, app.textureLibrary) { app.textureLibrary.search(query) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        LibrarySheetTitle("Texture library", "${app.textureLibrary.size} images, kept across projects")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onImportPack,
                modifier = Modifier.weight(1f).height(48.dp),
            ) { Text("Resource pack", style = MaterialTheme.typography.labelMedium) }
            OutlinedButton(
                onClick = onImportImages,
                modifier = Modifier.weight(1f).height(48.dp),
            ) { Text("Images", style = MaterialTheme.typography.labelMedium) }
        }

        if (app.textureLibrary.entries.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Import a Minecraft resource pack to fill this with real GUI art in one go. " +
                    "Anything you import here stays available in every project.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.chromeTextMuted,
            )
            return@Column
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Search") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.heightIn(max = 400.dp)) {
            items(results, key = { it.id }) { entry ->
                LibraryRow(
                    entry = entry,
                    onUse = { app.useLibraryTexture(entry) },
                    onForget = { app.forgetLibraryTexture(context, entry.id) },
                )
            }
        }
    }
}

@Composable
private fun LibraryRow(entry: LibraryTexture, onUse: () -> Unit, onForget: () -> Unit) {
    val palette = LocalSkinPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.chromePanelAlt)
            .clickable(onClick = onUse)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TexturePreview(entry)
        Column(Modifier.weight(1f)) {
            Text(entry.asset.name, style = MaterialTheme.typography.labelLarge, color = palette.chromeText, maxLines = 1)
            Text(
                "${entry.asset.width}x${entry.asset.height} ${entry.asset.format.uppercase()}" +
                    if (entry.source.isNotBlank()) "  ·  ${entry.source}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
                maxLines = 1,
            )
        }
        TextButton(onClick = onForget) {
            Text("✕", color = ErrorRed, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
@Composable
private fun TexturePreview(entry: LibraryTexture) {
    val palette = LocalSkinPalette.current
    val painter = remember(entry.id) {
        runCatching { decodeImageBitmap(Base64.decode(entry.asset.dataBase64)) }
            .getOrNull()
            // Nearest-neighbour: these are pixel-art files, and smoothing them
            // in a thumbnail misrepresents what will actually be drawn.
            ?.let { BitmapPainter(it, filterQuality = FilterQuality.None) }
    }
    Box(
        Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(palette.chromeBackground),
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = entry.asset.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(3.dp),
            )
        } else {
            Text("?", style = MaterialTheme.typography.labelSmall, color = palette.chromeTextMuted)
        }
    }
}

// ---------------------------------------------------------------------------
// Component gallery
// ---------------------------------------------------------------------------

/**
 * Every component, with a live preview and the facts you would otherwise have
 * to learn by placing one and deleting it.
 */
@Composable
fun GallerySheet(app: AndroidAppState, state: EditorState) {
    val palette = LocalSkinPalette.current
    var query by remember { mutableStateOf("") }
    val definitions = remember(query, state.edition) {
        val all = ElementCatalog.forEdition(state.edition)
        if (query.isBlank()) all else all.filter { it.matchesQuery(query) }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        LibrarySheetTitle(
            "Component library",
            "${definitions.size} available in ${state.edition.displayName}",
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Search") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(168.dp),
            modifier = Modifier.heightIn(max = 440.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(definitions, key = { it.typeId }) { definition ->
                GalleryTile(
                    definition = definition,
                    edition = state.edition,
                    onPlace = {
                        val canvas = state.project.canvas
                        app.controller.addElement(
                            definition.typeId,
                            IntPoint(canvas.width / 2, canvas.height / 2),
                            centreOnPoint = true,
                        )
                        app.sheet = MobileSheet.NONE
                        app.status = "Placed ${definition.displayName}."
                    },
                )
            }
        }
    }
}

private fun ElementDefinition.matchesQuery(query: String): Boolean {
    val needle = query.trim().lowercase()
    return displayName.lowercase().contains(needle) ||
        description.lowercase().contains(needle) ||
        typeId.lowercase().contains(needle) ||
        category.displayName.lowercase().contains(needle)
}

@Composable
private fun GalleryTile(definition: ElementDefinition, edition: Edition, onPlace: () -> Unit) {
    val palette = LocalSkinPalette.current
    val size = definition.defaultSizeFor(edition)
    val sample = remember(definition.typeId, edition) {
        GuiProject(
            id = "gallery-${definition.typeId}",
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
                    id = "sample",
                    type = definition.typeId,
                    name = definition.displayName,
                    bounds = IntRect(0, 0, size.width, size.height),
                    props = definition.defaultProps(edition),
                ),
            ),
        )
    }
    val textures = rememberTextureCache(sample)
    val zoom = remember(sample) {
        minOf(140f / size.width.coerceAtLeast(1), 56f / size.height.coerceAtLeast(1)).coerceIn(0.2f, 5f)
    }

    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.chromePanelAlt)
            .clickable(onClick = onPlace)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(palette.chromeBackground),
            contentAlignment = Alignment.Center,
        ) {
            GuiPreview(
                project = sample,
                textures = textures,
                modifier = Modifier.fillMaxWidth().height(62.dp),
                zoom = zoom,
                drawBackdrop = false,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(definition.displayName, style = MaterialTheme.typography.labelMedium, color = palette.chromeText, maxLines = 1)
        Text(
            "${size.width}x${size.height}" + if (definition.resizable) "  ·  resizable" else "  ·  fixed",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = palette.chromeTextMuted,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// Resource pack import
// ---------------------------------------------------------------------------

/**
 * Chooses what to pull out of an opened pack.
 *
 * GUI art is pre-selected and nothing else is: a vanilla pack is thousands of
 * block textures, and importing all of them by default would fill a phone's
 * storage with art nobody asked for.
 */
@Composable
fun PackImportSheet(app: AndroidAppState) {
    val context = LocalContext.current
    val palette = LocalSkinPalette.current
    val pack = app.openedPack ?: return
    val scan = pack.scan

    val selected = remember(pack.uri) {
        mutableStateListOf<String>().apply {
            addAll(scan.textures.filter { it.role == PackTextureRole.GUI }.map { it.path })
        }
    }
    var intoProject by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        LibrarySheetTitle(
            pack.name,
            "${scan.kind.displayName}  ·  ${scan.textures.size} images  ·  " +
                "${scan.countFor(PackTextureRole.GUI)} GUI",
        )

        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            scan.byRole().forEach { (role, textures) ->
                item(key = "role-${role.name}") {
                    val allSelected = textures.all { it.path in selected }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { select ->
                                if (select) {
                                    textures.forEach { if (it.path !in selected) selected += it.path }
                                } else {
                                    selected.removeAll(textures.map { it.path })
                                }
                            },
                        )
                        Text(
                            "${role.displayName.uppercase()}  (${textures.size})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.chromeTextMuted,
                        )
                    }
                }
                // Only the first 120 of each role are listed: the group
                // checkbox above still selects every one of them, and no one
                // scrolls a 4,000-row list on a phone.
                items(textures.take(120), key = { it.path }) { texture ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (texture.path in selected) selected -= texture.path
                                else selected += texture.path
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = texture.path in selected,
                            onCheckedChange = {
                                if (texture.path in selected) selected -= texture.path
                                else selected += texture.path
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                texture.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.chromeText,
                                maxLines = 1,
                            )
                            Text(
                                texture.path,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = palette.chromeTextMuted,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        Divider(color = palette.chromeBorder)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = intoProject, onCheckedChange = { intoProject = it })
            Text(
                "Also add them to this project",
                style = MaterialTheme.typography.bodySmall,
                color = palette.chromeText,
            )
        }

        Button(
            onClick = {
                val wanted: List<PackTexture> = scan.textures.filter { it.path in selected }
                app.importFromPack(context, wanted, intoProject)
            },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Import ${selected.size}") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { app.closePack() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

// ---------------------------------------------------------------------------
// Appearance
// ---------------------------------------------------------------------------

/** Theme and wallpaper settings. */
@Composable
fun AppearanceSheet(app: AndroidAppState) {
    val context = LocalContext.current
    val palette = LocalSkinPalette.current

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
        LibrarySheetTitle("Appearance", "Theme and wallpaper")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEach { mode ->
                val selected = app.themeMode == mode
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) palette.accentMuted else palette.chromePanelAlt)
                        .clickable { app.setTheme(context, mode) }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        mode.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.chromeText,
                        maxLines = 1,
                    )
                }
            }
        }

        Text(
            "The canvas never changes with the theme: a Minecraft widget is the same colour " +
                "whatever the editor around it looks like.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
            modifier = Modifier.padding(top = 10.dp),
        )

        Spacer(Modifier.height(16.dp))
        Divider(color = palette.chromeBorder)
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = app.backdropEnabled,
                onCheckedChange = { app.setBackdrop(context, enabled = it) },
            )
            Text("Show the wallpaper", style = MaterialTheme.typography.bodyMedium, color = palette.chromeText)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = app.backdropMotion,
                enabled = app.backdropEnabled,
                onCheckedChange = { app.setBackdrop(context, enabled = app.backdropEnabled, motion = it) },
            )
            Text("Let it drift slowly", style = MaterialTheme.typography.bodyMedium, color = palette.chromeText)
        }
    }
}

// ---------------------------------------------------------------------------
// Shared
// ---------------------------------------------------------------------------

/** Consistent heading for the sheets in this file. */
@Composable
private fun LibrarySheetTitle(title: String, subtitle: String) {
    val palette = LocalSkinPalette.current
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = palette.chromeTextMuted)
    }
}
