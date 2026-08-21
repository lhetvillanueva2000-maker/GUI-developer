package com.mcguidesigner.desktop.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.catalog.ElementDefinition
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.model.CanvasBackdrop
import com.mcguidesigner.core.model.CanvasSpec
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.IntPoint
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.packs.PackTexture
import com.mcguidesigner.core.packs.PackTextureRole
import com.mcguidesigner.desktop.ActiveDialog
import com.mcguidesigner.desktop.AppState
import com.mcguidesigner.styles.canvas.GuiPreview
import com.mcguidesigner.styles.render.rememberTextureCache
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.SkinRegistry
import com.mcguidesigner.styles.theme.ThemeMode
import com.mcguidesigner.styles.theme.WarningAmber

// ---------------------------------------------------------------------------
// Component gallery
// ---------------------------------------------------------------------------

/**
 * Every component in the catalog, shown at once with a live preview and the
 * facts you would otherwise have to discover by placing one and deleting it:
 * default size, whether it resizes, whether it takes children, and which
 * editions it exists in.
 *
 * The palette in the dock is for *working*; this is for *finding out what
 * exists*, which is a different job and deserves the room.
 */
@Composable
fun ComponentGalleryDialog(app: AppState, state: EditorState) {
    val palette = LocalSkinPalette.current
    var query by remember { mutableStateOf("") }
    var showBothEditions by remember { mutableStateOf(false) }

    val definitions = remember(query, state.edition, showBothEditions) {
        val all = if (showBothEditions) ElementCatalog.all else ElementCatalog.forEdition(state.edition)
        if (query.isBlank()) all else all.filter { it.matchesQuery(query) }
    }

    AlertDialog(
        onDismissRequest = { app.dialog = ActiveDialog.NONE },
        title = {
            Column {
                Text("Component library", fontWeight = FontWeight.SemiBold)
                Text(
                    "${definitions.size} of ${ElementCatalog.all.size} components  ·  " +
                        "click one to drop it in the middle of the canvas",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                )
            }
        },
        text = {
            Column(Modifier.width(880.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text("Search") },
                        modifier = Modifier.weight(1f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showBothEditions, onCheckedChange = { showBothEditions = it })
                        Text("Show both editions", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Box(Modifier.height(10.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(272.dp),
                    modifier = Modifier.heightIn(max = 520.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(definitions, key = { it.typeId }) { definition ->
                        GalleryCard(
                            definition = definition,
                            edition = if (definition.supports(state.edition)) state.edition else definition.editions.first(),
                            available = definition.supports(state.edition),
                            onPlace = {
                                if (definition.supports(state.edition)) {
                                    val canvas = state.project.canvas
                                    app.controller.addElement(
                                        definition.typeId,
                                        IntPoint(canvas.width / 2, canvas.height / 2),
                                        centreOnPoint = true,
                                    )
                                    app.dialog = ActiveDialog.NONE
                                    app.status = "Placed ${definition.displayName}."
                                }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { app.dialog = ActiveDialog.NONE }) { Text("Close") }
        },
    )
}

private fun ElementDefinition.matchesQuery(query: String): Boolean {
    val needle = query.trim().lowercase()
    return displayName.lowercase().contains(needle) ||
        description.lowercase().contains(needle) ||
        typeId.lowercase().contains(needle) ||
        category.displayName.lowercase().contains(needle)
}

@Composable
private fun GalleryCard(
    definition: ElementDefinition,
    edition: Edition,
    available: Boolean,
    onPlace: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    val size = definition.defaultSizeFor(edition)

    Column(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.chromePanelAlt)
            .border(
                1.dp,
                if (available) Color.Transparent else WarningAmber.copy(alpha = 0.45f),
                RoundedCornerShape(8.dp),
            )
            .clickable(enabled = available, onClick = onPlace)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                definition.displayName,
                style = MaterialTheme.typography.labelLarge,
                color = palette.chromeText,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                definition.category.displayName,
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
                maxLines = 1,
            )
        }

        Box(Modifier.height(8.dp))

        GalleryPreview(definition, edition)

        Box(Modifier.height(8.dp))

        Text(
            definition.description.ifBlank { definition.typeId },
            style = MaterialTheme.typography.bodySmall,
            color = palette.chromeTextMuted,
            maxLines = 3,
        )

        Box(Modifier.height(8.dp))

        Text(
            text = buildString {
                append("${size.width}x${size.height}")
                append("  ·  ")
                append(if (definition.resizable) "resizable" else "fixed size")
                if (definition.acceptsChildren) append("  ·  container")
                if (definition.interactive) append("  ·  ${definition.states.size} states")
            },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = palette.chromeTextMuted,
            maxLines = 1,
        )
        Text(
            text = if (definition.editions.size == 2) {
                "Java and Bedrock"
            } else {
                "${definition.editions.first().displayName} only"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (available) palette.accent else WarningAmber,
            maxLines = 1,
        )
    }
}

/** One component drawn at a readable size with its own edition's skin. */
@Composable
private fun GalleryPreview(definition: ElementDefinition, edition: Edition) {
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
        minOf(230f / size.width.coerceAtLeast(1), 76f / size.height.coerceAtLeast(1)).coerceIn(0.2f, 5f)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(palette.chromeBackground),
        contentAlignment = Alignment.Center,
    ) {
        GuiPreview(
            project = sample,
            textures = textures,
            modifier = Modifier.fillMaxWidth().height(84.dp),
            zoom = zoom,
            drawBackdrop = false,
            skin = SkinRegistry.forEdition(edition),
        )
    }
}

// ---------------------------------------------------------------------------
// Save prefab
// ---------------------------------------------------------------------------

/** Names a prefab before it joins the library. */
@Composable
fun SavePrefabDialog(app: AppState, state: EditorState) {
    val palette = LocalSkinPalette.current
    var name by remember { mutableStateOf(app.prefabDraftName) }
    var description by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }

    val roots = state.selectedElements

    AlertDialog(
        onDismissRequest = { app.dialog = ActiveDialog.NONE },
        title = { Text("Save as a prefab") },
        text = {
            Column(Modifier.width(460.dp)) {
                Text(
                    "${roots.size} selected element(s) will be saved as one reusable piece, " +
                        "for ${state.edition.displayName}. Any textures they use travel with them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.chromeTextMuted,
                )
                Box(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("What is it for? (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.height(10.dp))
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags, comma separated (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && roots.isNotEmpty(),
                onClick = {
                    app.savePrefab(
                        name = name.trim(),
                        description = description.trim(),
                        tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    )
                },
            ) { Text("Save prefab") }
        },
        dismissButton = {
            TextButton(onClick = { app.dialog = ActiveDialog.NONE }) { Text("Cancel") }
        },
    )
}

// ---------------------------------------------------------------------------
// Resource pack import
// ---------------------------------------------------------------------------

/**
 * Chooses what to pull out of an opened resource pack.
 *
 * GUI art is pre-selected and everything else is not: a vanilla pack is
 * thousands of block textures, and importing all of them by default would turn
 * the library into a haystack on the very first import.
 */
@Composable
fun PackImportDialog(app: AppState) {
    val palette = LocalSkinPalette.current
    val pack = app.openedPack ?: return
    val scan = pack.scan

    val selected = remember(pack.file.path) {
        mutableStateListOf<String>().apply {
            addAll(scan.textures.filter { it.role == PackTextureRole.GUI }.map { it.path })
        }
    }
    var intoProject by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }

    // Path set rather than a list: a vanilla pack is several thousand entries
    // and the role sections below test membership once per row.
    val visiblePaths = remember(query, scan) {
        if (query.isBlank()) scan.textures.map { it.path }.toSet()
        else scan.textures.filter { it.path.contains(query, ignoreCase = true) }.map { it.path }.toSet()
    }

    AlertDialog(
        onDismissRequest = { app.closePack() },
        title = {
            Column {
                Text("Import from ${pack.name}", fontWeight = FontWeight.SemiBold)
                Text(
                    "${scan.kind.displayName}  ·  ${scan.textures.size} images  ·  " +
                        "${scan.countFor(PackTextureRole.GUI)} of them GUI art",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                )
            }
        },
        text = {
            Column(Modifier.width(720.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        label = { Text("Filter by path") },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        selected.clear()
                        selected.addAll(visiblePaths)
                    }) { Text("Select shown") }
                    TextButton(onClick = { selected.clear() }) { Text("Clear") }
                }

                Box(Modifier.height(8.dp))
                Divider(color = palette.chromeBorder)

                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    scan.byRole().forEach { (role, textures) ->
                        val shown = textures.filter { it.path in visiblePaths }
                        if (shown.isEmpty()) return@forEach
                        item(key = "role-${role.name}") {
                            RoleHeader(
                                role = role,
                                count = shown.size,
                                allSelected = shown.all { it.path in selected },
                                onToggleAll = { select ->
                                    if (select) {
                                        shown.forEach { if (it.path !in selected) selected += it.path }
                                    } else {
                                        selected.removeAll(shown.map { it.path })
                                    }
                                },
                            )
                        }
                        items(shown, key = { it.path }) { texture ->
                            PackRow(
                                texture = texture,
                                checked = texture.path in selected,
                                onToggle = {
                                    if (texture.path in selected) selected -= texture.path
                                    else selected += texture.path
                                },
                            )
                        }
                    }
                }

                Divider(color = palette.chromeBorder)
                Box(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = intoProject, onCheckedChange = { intoProject = it })
                    Column {
                        Text("Also add them to the open project", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "Everything imported joins your texture library either way.",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.chromeTextMuted,
                        )
                    }
                }

                AnimatedVisibility(visible = selected.size > 200, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        "${selected.size} images selected - large imports make the project file " +
                            "big and slow to save.",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = {
                    val wanted: List<PackTexture> = scan.textures.filter { it.path in selected }
                    app.importFromPack(wanted, intoProject)
                },
            ) { Text("Import ${selected.size}") }
        },
        dismissButton = {
            TextButton(onClick = { app.closePack() }) { Text("Cancel") }
        },
    )
}

@Composable
private fun RoleHeader(
    role: PackTextureRole,
    count: Int,
    allSelected: Boolean,
    onToggleAll: (Boolean) -> Unit,
) {
    val palette = LocalSkinPalette.current
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = allSelected, onCheckedChange = onToggleAll)
        Text(
            "${role.displayName.uppercase()}  ($count)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = palette.chromeTextMuted,
        )
    }
}

@Composable
private fun PackRow(texture: PackTexture, checked: Boolean, onToggle: () -> Unit) {
    val palette = LocalSkinPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
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

// ---------------------------------------------------------------------------
// Appearance
// ---------------------------------------------------------------------------

/** Theme and wallpaper settings. */
@Composable
fun AppearanceDialog(app: AppState) {
    val palette = LocalSkinPalette.current

    AlertDialog(
        onDismissRequest = { app.dialog = ActiveDialog.NONE },
        title = { Text("Appearance") },
        text = {
            Column(Modifier.width(460.dp)) {
                Text("Theme", style = MaterialTheme.typography.labelMedium)
                Box(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val selected = app.themeMode == mode
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) palette.accentMuted else palette.chromePanelAlt)
                                .border(
                                    1.dp,
                                    if (selected) palette.accent else Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { app.setTheme(mode) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                mode.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.chromeText,
                            )
                        }
                    }
                }
                Text(
                    "The canvas never changes with the theme: a Minecraft widget is the same " +
                        "colour whatever the editor around it looks like.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Box(Modifier.height(18.dp))
                Divider(color = palette.chromeBorder)
                Box(Modifier.height(14.dp))

                Text("Wallpaper", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = app.backdropEnabled,
                        onCheckedChange = { app.setBackdrop(enabled = it) },
                    )
                    Text("Show the artwork behind the editor", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = app.backdropMotion,
                        enabled = app.backdropEnabled,
                        onCheckedChange = { app.setBackdrop(enabled = app.backdropEnabled, motion = it) },
                    )
                    Text("Let it drift slowly", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { app.dialog = ActiveDialog.NONE }) { Text("Done") }
        },
    )
}
