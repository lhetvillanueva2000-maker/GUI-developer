package com.mcguidesigner.desktop.dialogs

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.core.validation.Severity
import com.mcguidesigner.desktop.ActiveDialog
import com.mcguidesigner.desktop.AppState
import com.mcguidesigner.desktop.panels.TemplateCard
import com.mcguidesigner.exporters.CodeGenerator
import com.mcguidesigner.exporters.CodeTarget
import com.mcguidesigner.exporters.ExportManager
import com.mcguidesigner.exporters.ExportTarget
import com.mcguidesigner.styles.render.rememberTextureCache
import com.mcguidesigner.styles.theme.ErrorRed
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.WarningAmber

@Composable
fun NewProjectDialog(app: AppState) {
    val palette = LocalSkinPalette.current
    var name by remember { mutableStateOf("Untitled Screen") }
    var edition by remember { mutableStateOf(app.controller.current.edition) }

    AlertDialog(
        onDismissRequest = { app.dialog = ActiveDialog.NONE },
        title = { Text("New project") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Screen name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.height(14.dp))
                Text("Edition", style = MaterialTheme.typography.labelMedium)
                Text(
                    "This decides the widget set, the canvas defaults, the visual style " +
                        "and the export format. It can be changed later.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                )
                Box(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Edition.entries.forEach { candidate ->
                        val selected = candidate == edition
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) palette.accentMuted else palette.chromePanelAlt)
                                .border(
                                    1.dp,
                                    if (selected) palette.accent else Color.Transparent,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { edition = candidate }
                                .padding(12.dp),
                        ) {
                            Text(
                                candidate.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                com.mcguidesigner.styles.theme.SkinRegistry.forEdition(candidate).tagline,
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.chromeTextMuted,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { app.newProject(edition, name.ifBlank { "Untitled Screen" }) }) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = { app.dialog = ActiveDialog.NONE }) { Text("Cancel") }
        },
    )
}

@Composable
fun TemplateGalleryDialog(app: AppState) {
    val state = app.controller.current
    val textures = rememberTextureCache(state.project)

    AlertDialog(
        onDismissRequest = { app.dialog = ActiveDialog.NONE },
        title = { Text("Start from a template") },
        text = {
            LazyColumn(Modifier.width(420.dp).heightIn(max = 520.dp)) {
                items(BuiltInTemplates.all, key = { it.id }) { template ->
                    TemplateCard(template, textures, template.edition == state.edition) {
                        app.newFromTemplate(template.id)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { app.dialog = ActiveDialog.NONE }) { Text("Close") }
        },
    )
}

@Composable
fun ExportDialog(app: AppState, state: EditorState) {
    val palette = LocalSkinPalette.current
    var target by remember { mutableStateOf(app.exportTarget) }
    val bundle = remember(state.project, target, app.codeTarget) {
        ExportManager.export(state.project, target, app.codeTarget)
    }
    val available = remember(state.edition) { ExportManager.availableTargets(state.edition) }

    AlertDialog(
        onDismissRequest = { app.dialog = ActiveDialog.NONE },
        title = { Text("Export") },
        text = {
            Column(Modifier.width(580.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
                Text("What are you exporting to?", style = MaterialTheme.typography.labelMedium)
                Box(Modifier.height(6.dp))

                available.distinct().forEach { candidate ->
                    val selected = candidate == target
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) palette.accentMuted else palette.chromePanelAlt)
                            .clickable { target = candidate }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(candidate.displayName, style = MaterialTheme.typography.labelLarge)
                            candidate.edition?.let {
                                Text(
                                    if (it == state.edition) "matches this project" else "cross-edition port",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (it == state.edition) palette.accent else WarningAmber,
                                )
                            }
                        }
                    }
                }

                if (target == ExportTarget.CODE) {
                    Box(Modifier.height(14.dp))
                    CodeTargetPicker(
                        edition = state.edition,
                        selected = app.codeTarget,
                        onSelect = { app.codeTarget = it },
                    )
                }

                Box(Modifier.height(12.dp))
                Divider(color = palette.chromeBorder)
                Box(Modifier.height(8.dp))

                Text(
                    "${bundle.fileCount} file(s), about ${bundle.totalBytes / 1024} KB",
                    style = MaterialTheme.typography.labelMedium,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 170.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.chromeBackground)
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                ) {
                    Text(
                        bundle.tree(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = palette.chromeTextMuted,
                    )
                }

                if (bundle.warnings.isNotEmpty()) {
                    Box(Modifier.height(10.dp))
                    Text(
                        "${bundle.warnings.count { it.severity == Severity.ERROR }} error(s), " +
                            "${bundle.warnings.count { it.severity == Severity.WARNING }} warning(s)",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (bundle.hasBlockingErrors) ErrorRed else WarningAmber,
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 130.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        bundle.warnings.take(30).forEach { issue ->
                            Text(
                                "• ${issue.message}",
                                style = MaterialTheme.typography.labelSmall,
                                color = when (issue.severity) {
                                    Severity.ERROR -> ErrorRed
                                    Severity.WARNING -> WarningAmber
                                    Severity.INFO -> palette.chromeTextMuted
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    app.exportTarget = target
                    app.runExport(target, asZip = false)
                }) { Text("Export to folder") }
                TextButton(onClick = {
                    app.exportTarget = target
                    app.runExport(target, asZip = true)
                }) { Text("Export as ZIP") }
            }
        },
        dismissButton = {
            TextButton(onClick = { app.dialog = ActiveDialog.NONE }) { Text("Cancel") }
        },
    )
}

/**
 * The list of languages a design can be rendered into.
 *
 * Split in two, because the distinction decides what someone actually does
 * with the file: the first group is read by Minecraft itself, so it goes into
 * a resource pack and works; the second is source and artwork for use
 * somewhere else.  Knowing which is which is otherwise a thing you have to
 * already know.
 */
@Composable
fun CodeTargetPicker(
    edition: Edition,
    selected: CodeTarget,
    onSelect: (CodeTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    val all = remember(edition) { CodeGenerator.targetsFor(edition) }
    val native = all.filter { it.readByMinecraft }
    val rest = all.filterNot { it.readByMinecraft }

    @Composable
    fun section(title: String, blurb: String, targets: List<CodeTarget>) {
        if (targets.isEmpty()) return
        Text(title, style = MaterialTheme.typography.labelMedium)
        Text(blurb, style = MaterialTheme.typography.labelSmall, color = palette.chromeTextMuted)
        Box(Modifier.height(6.dp))
        targets.forEach { code ->
            val isSelected = code == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) palette.accentMuted else palette.chromePanelAlt)
                    .clickable { onSelect(code) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(code.displayName, style = MaterialTheme.typography.labelLarge)
                        if (code.readByMinecraft) {
                            Box(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(palette.accent.copy(alpha = 0.22f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    "the game reads this",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.accent,
                                )
                            }
                        }
                    }
                    if (code.description.isNotBlank()) {
                        Text(
                            code.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.chromeTextMuted,
                        )
                    }
                }
                Text(
                    ".${code.fileExtension}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = palette.chromeTextMuted,
                )
            }
        }
        Box(Modifier.height(12.dp))
    }

    Column(modifier) {
        section(
            "Minecraft's own formats",
            "Read by ${edition.displayName} directly - no mod, no build step.",
            native,
        )
        section(
            "Code & artwork",
            "For use outside the game: mod source, web mock-ups, vector art.",
            rest,
        )
    }
}

@Composable
fun ProjectSettingsDialog(app: AppState, controller: EditorController, state: EditorState) {
    var name by remember { mutableStateOf(state.project.name) }
    var namespace by remember { mutableStateOf(state.project.meta.namespace) }
    var screenId by remember { mutableStateOf(state.project.meta.screenId) }
    var author by remember { mutableStateOf(state.project.meta.author) }
    var description by remember { mutableStateOf(state.project.meta.description) }

    AlertDialog(
        onDismissRequest = { app.dialog = ActiveDialog.NONE },
        title = { Text("Project settings") },
        text = {
            Column(Modifier.width(440.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box(Modifier.height(8.dp))
                OutlinedTextField(
                    namespace, { namespace = it },
                    label = { Text("Namespace") },
                    supportingText = { Text("Used as assets/<namespace>/… on Java and the JSON-UI namespace on Bedrock.") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.height(8.dp))
                OutlinedTextField(screenId, { screenId = it }, label = { Text("Screen id") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box(Modifier.height(8.dp))
                OutlinedTextField(author, { author = it }, label = { Text("Author") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Box(Modifier.height(8.dp))
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                controller.renameProject(name)
                controller.updateMeta {
                    it.copy(
                        namespace = namespace,
                        screenId = screenId,
                        author = author,
                        description = description,
                    )
                }
                app.dialog = ActiveDialog.NONE
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = { app.dialog = ActiveDialog.NONE }) { Text("Cancel") } },
    )
}

@Composable
fun AboutDialog(app: AppState) {
    AlertDialog(
        onDismissRequest = { app.dialog = ActiveDialog.NONE },
        title = { Text("Minecraft GUI Designer") },
        text = {
            Column(Modifier.width(400.dp)) {
                Text(
                    "A standalone visual designer for Minecraft screens, covering both " +
                        "Java Edition and Bedrock Edition with separate widget sets, " +
                        "visual styles and export pipelines.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Box(Modifier.height(10.dp))
                Text("Not affiliated with Mojang or Microsoft.", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { TextButton(onClick = { app.dialog = ActiveDialog.NONE }) { Text("Close") } },
    )
}

@Composable
fun ShortcutsDialog(app: AppState) {
    val shortcuts = listOf(
        "Ctrl+N" to "New project",
        "Ctrl+O" to "Open project",
        "Ctrl+S" to "Save",
        "Ctrl+Shift+S / Ctrl+E" to "Export (every format, including the ones Minecraft reads)",
        "Ctrl+Shift+E" to "Export everything at once",
        "Ctrl+Z / Ctrl+Y" to "Undo / Redo",
        "Ctrl+C / Ctrl+V / Ctrl+X" to "Copy / Paste / Cut elements",
        "Ctrl+D" to "Duplicate selection",
        "Ctrl+A" to "Select all",
        "Delete" to "Delete selection",
        "Escape" to "Deselect / cancel placement",
        "V / H / M" to "Select / Pan / Marquee tool",
        "Arrow keys" to "Move the selection by the step in Editor Settings (Shift: the big step)",
        "Ctrl+] / Ctrl+[" to "Bring forward / send backward",
        "Ctrl+Shift+] / Ctrl+Shift+[" to "Bring to front / send to back",
        "Ctrl+G" to "Toggle grid",
        "Ctrl+R" to "Toggle rulers",
        "Ctrl++ / Ctrl+-" to "Zoom in / out",
        "Ctrl+0" to "Reset zoom and pan",
        "Ctrl+wheel" to "Zoom towards the pointer",
        "Middle / right drag" to "Pan the canvas",
    )

    AlertDialog(
        onDismissRequest = { app.dialog = ActiveDialog.NONE },
        title = { Text("Keyboard shortcuts") },
        text = {
            LazyColumn(Modifier.width(460.dp).heightIn(max = 480.dp)) {
                items(shortcuts) { (keys, description) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            keys,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.width(190.dp),
                        )
                        Text(description, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { app.dialog = ActiveDialog.NONE }) { Text("Close") } },
    )
}
