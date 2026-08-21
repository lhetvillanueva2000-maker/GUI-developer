package com.mcguidesigner.desktop.panels

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.library.LibraryTexture
import com.mcguidesigner.desktop.AppState
import com.mcguidesigner.styles.render.decodeImageBitmap
import com.mcguidesigner.styles.theme.ErrorRed
import com.mcguidesigner.styles.theme.LocalSkinPalette
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The texture library: every image the user has ever imported, kept outside any
 * one project.
 *
 * Clicking an entry copies it into the open document.  It is a copy on purpose
 * - a `.mcgui` stays a single self-contained file, so a project can never break
 * because the library moved on without it.
 */
@Composable
fun LibraryPanel(
    app: AppState,
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    var query by remember { mutableStateOf("") }
    var activeSource by remember { mutableStateOf<String?>(null) }

    val library = app.textureLibrary
    val results = remember(query, activeSource, library) {
        library.search(query = query, source = activeSource)
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { app.browsePack() }, modifier = Modifier.weight(1f)) {
                Text("Import a pack...", style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(onClick = { app.importTextures() }, modifier = Modifier.weight(1f)) {
                Text("Import images...", style = MaterialTheme.typography.labelMedium)
            }
        }

        if (library.entries.isEmpty()) {
            EmptyLibrary(Modifier.fillMaxSize().padding(horizontal = 20.dp))
            return@Column
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Search ${library.size} textures") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
        )

        val sources = library.allSources
        if (sources.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SourceChip("All", activeSource == null) { activeSource = null }
                sources.take(3).forEach { source ->
                    SourceChip(source, activeSource == source) {
                        activeSource = if (activeSource == source) null else source
                    }
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            items(results, key = { it.id }) { entry ->
                LibraryEntryRow(
                    entry = entry,
                    inProject = state.project.textures.any { it.name == entry.asset.name },
                    onUse = { app.useLibraryTexture(entry) },
                    onForget = { app.forgetLibraryTexture(entry.id) },
                )
            }
            if (results.isEmpty()) {
                item {
                    Text(
                        "Nothing matches.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.chromeTextMuted,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            item {
                TextButton(
                    onClick = { app.rememberProjectTextures() },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    Text("Add this project's textures to the library", style = MaterialTheme.typography.labelSmall)
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalSkinPalette.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) palette.accentMuted else palette.chromePanelAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) palette.chromeText else palette.chromeTextMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier) {
    val palette = LocalSkinPalette.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("The library is empty", style = MaterialTheme.typography.titleSmall, color = palette.chromeText)
        Text(
            "Anything you import lands here as well as in the project, and stays here for " +
                "every project after it. Import a Minecraft resource pack to fill it with real " +
                "GUI art in one go.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.chromeTextMuted,
        )
    }
}

@Composable
private fun LibraryEntryRow(
    entry: LibraryTexture,
    inProject: Boolean,
    onUse: () -> Unit,
    onForget: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(palette.chromePanelAlt)
            .border(
                1.dp,
                if (inProject) palette.accent.copy(alpha = 0.35f) else Color.Transparent,
                RoundedCornerShape(6.dp),
            )
            .clickable(onClick = onUse)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TexturePreview(entry)
        Column(Modifier.weight(1f)) {
            Text(
                entry.asset.name,
                style = MaterialTheme.typography.labelLarge,
                color = palette.chromeText,
                maxLines = 1,
            )
            Text(
                buildString {
                    append("${entry.asset.width}x${entry.asset.height} ${entry.asset.format.uppercase()}")
                    if (entry.source.isNotBlank()) append("  ·  ${entry.source}")
                },
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

/**
 * Decoded thumbnail on a checkerboard, so transparent art reads as transparent
 * rather than as the panel colour.
 */
@OptIn(ExperimentalEncodingApi::class)
@Composable
private fun TexturePreview(entry: LibraryTexture) {
    val palette = LocalSkinPalette.current
    val painter = remember(entry.id) {
        runCatching { decodeImageBitmap(Base64.decode(entry.asset.dataBase64)) }
            .getOrNull()
            // Nearest-neighbour: these are 16x16 pixel-art files, and smoothing
            // them in a thumbnail misrepresents what will be drawn.
            ?.let { BitmapPainter(it, filterQuality = FilterQuality.None) }
    }

    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(palette.chromeBackground),
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null) {
            androidx.compose.foundation.Image(
                painter = painter,
                contentDescription = entry.asset.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(2.dp),
            )
        } else {
            Text(
                "?",
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
