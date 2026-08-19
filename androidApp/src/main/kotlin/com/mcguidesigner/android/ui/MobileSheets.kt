@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mcguidesigner.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcguidesigner.android.AndroidAppState
import com.mcguidesigner.android.MobileSection
import com.mcguidesigner.android.MobileSheet
import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.catalog.ElementDefinition
import com.mcguidesigner.core.catalog.PropType
import com.mcguidesigner.core.catalog.PropertySpec
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.model.Anchor
import com.mcguidesigner.core.model.BoolValue
import com.mcguidesigner.core.model.CanvasBackdrop
import com.mcguidesigner.core.model.ColorValue
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.EnumValue
import com.mcguidesigner.core.model.FloatValue
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.IntValue
import com.mcguidesigner.core.model.ListValue
import com.mcguidesigner.core.model.PropValue
import com.mcguidesigner.core.model.StringValue
import com.mcguidesigner.core.model.TargetForm
import com.mcguidesigner.core.model.TextureValue
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.core.validation.Severity
import com.mcguidesigner.exporters.ExportManager
import com.mcguidesigner.exporters.ExportTarget
import com.mcguidesigner.styles.canvas.GuiPreview
import com.mcguidesigner.styles.render.TextureCache
import com.mcguidesigner.styles.theme.ErrorRed
import com.mcguidesigner.styles.theme.InfoBlue
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.WarningAmber

/**
 * Every modal bottom sheet the Android app can show.
 *
 * Sheets are the mobile counterpart of the desktop docks: one purpose at a
 * time, dismissible with a swipe, and always leaving the canvas visible behind
 * them so the user keeps their bearings.
 */
@Composable
fun MobileSheets(
    app: AndroidAppState,
    controller: EditorController,
    state: EditorState,
    textures: TextureCache,
    onImportImages: () -> Unit,
    onExport: (ExportTarget) -> Unit,
) {
    if (app.sheet == MobileSheet.NONE) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val palette = LocalSkinPalette.current

    ModalBottomSheet(
        onDismissRequest = { app.sheet = MobileSheet.NONE },
        sheetState = sheetState,
        containerColor = palette.chromePanel,
        contentColor = palette.chromeText,
    ) {
        Box(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            when (app.sheet) {
                MobileSheet.COMPONENTS -> ComponentSheet(app, controller, state, textures)
                MobileSheet.PROPERTIES -> PropertiesSheet(controller, state)
                MobileSheet.TEMPLATES -> TemplatesSheet(app, state, textures)
                MobileSheet.ASSETS -> AssetsSheet(app, controller, state, textures, onImportImages)
                MobileSheet.ISSUES -> IssuesSheet(controller, state)
                MobileSheet.EXPORT -> ExportSheet(app, state, onExport)
                MobileSheet.PROJECT -> ProjectSheet(controller, state)
                MobileSheet.CANVAS -> CanvasSheet(controller, state)
                MobileSheet.NONE -> Unit
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Components
// ---------------------------------------------------------------------------

@Composable
private fun ComponentSheet(
    app: AndroidAppState,
    controller: EditorController,
    state: EditorState,
    textures: TextureCache,
) {
    val palette = LocalSkinPalette.current
    var query by remember { mutableStateOf("") }
    val results = remember(query, state.edition) { ElementCatalog.search(query, state.edition) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        SheetTitle("Add a component", "${results.size} available for ${state.edition.displayName}")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Search") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(results, key = { it.typeId }) { definition ->
                ComponentTile(definition, state.edition, textures) {
                    // Drop it straight into the middle of the canvas: on a
                    // phone, "arm and then tap" is one gesture too many.
                    controller.addElement(
                        definition.typeId,
                        com.mcguidesigner.core.model.IntPoint(
                            state.project.canvas.width / 2,
                            state.project.canvas.height / 2,
                        ),
                        centreOnPoint = true,
                    )
                    app.sheet = MobileSheet.NONE
                    app.section = MobileSection.DESIGN
                }
            }
        }
    }
}

@Composable
private fun ComponentTile(
    definition: ElementDefinition,
    edition: Edition,
    textures: TextureCache,
    onPick: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    val size = definition.defaultSizeFor(edition)
    val sample = remember(definition.typeId, edition) {
        com.mcguidesigner.core.model.GuiProject(
            id = "tile-${definition.typeId}",
            name = definition.displayName,
            edition = edition,
            canvas = com.mcguidesigner.core.model.CanvasSpec(
                width = size.width,
                height = size.height,
                gridSize = 0,
                backdrop = CanvasBackdrop.NONE,
            ),
            elements = listOf(
                GuiElement(
                    id = "tile",
                    type = definition.typeId,
                    name = definition.displayName,
                    bounds = IntRect(0, 0, size.width, size.height),
                    props = definition.defaultProps(edition),
                ),
            ),
        )
    }
    val zoom = remember(sample) {
        minOf(130f / size.width.coerceAtLeast(1), 62f / size.height.coerceAtLeast(1)).coerceIn(0.15f, 3f)
    }

    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.chromePanelAlt)
            .clickable(onClick = onPick)
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(palette.chromeBackground),
            contentAlignment = Alignment.Center,
        ) {
            GuiPreview(sample, textures, Modifier.fillMaxSize(), zoom = zoom, drawBackdrop = false)
        }
        Text(
            definition.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = palette.chromeText,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Properties
// ---------------------------------------------------------------------------

@Composable
private fun PropertiesSheet(controller: EditorController, state: EditorState) {
    val palette = LocalSkinPalette.current
    val element = state.primaryElement
    if (element == null) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Select an element first.", color = palette.chromeTextMuted)
        }
        return
    }
    val definition = ElementCatalog[element.type] ?: return
    var editedState by remember(element.id) { mutableStateOf(InteractionState.NORMAL) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SheetTitle(element.name, definition.displayName)

        MobileTextField("Name", element.name) { controller.rename(element.id, it) }

        SheetSection("Position & size")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MobileNumberField("X", element.bounds.x, Modifier.weight(1f)) {
                controller.setBounds(element.id, element.bounds.copy(x = it), label = "Move")
            }
            MobileNumberField("Y", element.bounds.y, Modifier.weight(1f)) {
                controller.setBounds(element.id, element.bounds.copy(y = it), label = "Move")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
            MobileNumberField("Width", element.bounds.width, Modifier.weight(1f), min = 1) {
                controller.setBounds(element.id, element.bounds.copy(width = it))
            }
            MobileNumberField("Height", element.bounds.height, Modifier.weight(1f), min = 1) {
                controller.setBounds(element.id, element.bounds.copy(height = it))
            }
        }
        MobileChoiceChips(
            label = "Anchor",
            options = Anchor.entries.map { it.name },
            selected = element.anchor.name,
            display = { Anchor.valueOf(it).displayName },
        ) { controller.setAnchor(element.id, Anchor.valueOf(it)) }

        if (definition.interactive) {
            SheetSection("Interaction state")
            val states = if (state.edition == Edition.BEDROCK) {
                InteractionState.touchStates
            } else {
                InteractionState.entries
            }
            MobileChoiceChips(
                label = null,
                options = states.map { it.name },
                selected = editedState.name,
                display = { InteractionState.valueOf(it).displayName },
                highlighted = element.stateOverrides.keys.map { it.name }.toSet(),
            ) { editedState = InteractionState.valueOf(it) }
        }

        definition.propertiesFor(state.edition).groupBy { it.group }.forEach { (group, specs) ->
            SheetSection(group)
            specs.forEach { spec ->
                MobilePropertyEditor(controller, state, element, spec, editedState)
            }
        }

        Box(Modifier.height(40.dp))
    }
}

@Composable
private fun MobilePropertyEditor(
    controller: EditorController,
    state: EditorState,
    element: GuiElement,
    spec: PropertySpec,
    editedState: InteractionState,
) {
    val palette = LocalSkinPalette.current
    val value = element.propsFor(editedState)[spec.key] ?: spec.default

    fun write(next: PropValue) {
        controller.setProp(
            element.id,
            spec.key,
            next,
            if (spec.stateAware) editedState else InteractionState.NORMAL,
        )
    }

    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(spec.label, style = MaterialTheme.typography.labelLarge, color = palette.chromeText)

        when (spec.type) {
            PropType.TEXT -> MobilePlainField(value.asText(), true) { write(StringValue(it)) }
            PropType.MULTILINE_TEXT -> MobilePlainField(value.asText(), false) { write(StringValue(it)) }

            PropType.INT -> MobileNumberField(
                null,
                (value as? IntValue)?.value ?: 0,
                min = spec.min?.toInt(),
                max = spec.max?.toInt(),
            ) { write(IntValue(it)) }

            PropType.FLOAT -> {
                val current = (value as? FloatValue)?.value ?: 0f
                if (spec.min != null && spec.max != null) {
                    Slider(
                        value = current.coerceIn(spec.min!!, spec.max!!),
                        onValueChange = { write(FloatValue(it)) },
                        valueRange = spec.min!!..spec.max!!,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        formatFloat(current),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.chromeTextMuted,
                        fontFamily = FontFamily.Monospace,
                    )
                } else {
                    MobilePlainField(formatFloat(current), true) { text ->
                        text.toFloatOrNull()?.let { write(FloatValue(it)) }
                    }
                }
            }

            PropType.BOOL -> Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = (value as? BoolValue)?.value ?: false,
                    onCheckedChange = { write(BoolValue(it)) },
                )
                Text(
                    if ((value as? BoolValue)?.value == true) "On" else "Off",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.chromeTextMuted,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }

            PropType.COLOR -> MobileColorField(value as? ColorValue ?: ColorValue(0xFF000000)) { write(it) }

            PropType.ENUM -> MobileChoiceChips(
                label = null,
                options = spec.options,
                selected = value.asText(),
            ) { write(EnumValue(it)) }

            PropType.TEXTURE -> MobileChoiceChips(
                label = null,
                options = listOf("") + state.project.textures.map { it.id },
                selected = (value as? TextureValue)?.assetId ?: "",
                display = { id ->
                    if (id.isEmpty()) "None" else state.project.texture(id)?.name ?: id
                },
            ) { write(TextureValue(it.ifEmpty { null })) }

            PropType.STRING_LIST -> MobilePlainField(
                (value as? ListValue)?.values?.joinToString("\n") { it.asText() } ?: "",
                singleLine = false,
            ) { text ->
                write(ListValue(text.split('\n').filter { it.isNotBlank() }.map { StringValue(it.trim()) }))
            }
        }

        if (spec.help.isNotBlank()) {
            Text(spec.help, style = MaterialTheme.typography.labelSmall, color = palette.chromeTextMuted)
        }
    }
}

// ---------------------------------------------------------------------------
// Templates / assets / issues / export / project / canvas
// ---------------------------------------------------------------------------

@Composable
private fun TemplatesSheet(app: AndroidAppState, state: EditorState, textures: TextureCache) {
    val palette = LocalSkinPalette.current
    val ordered = remember(state.edition) {
        BuiltInTemplates.all.sortedByDescending { it.edition == state.edition }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SheetTitle("Templates", "Opening one replaces the current project")
        LazyColumn(Modifier.fillMaxSize()) {
            items(ordered, key = { it.id }) { template ->
                val project = remember(template.id) { template.instantiate() }
                val zoom = remember(project) {
                    minOf(320f / project.canvas.width, 150f / project.canvas.height).coerceIn(0.2f, 4f)
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.chromePanelAlt)
                        .clickable {
                            app.guardUnsaved("load the '${template.title}' template") {
                                app.newFromTemplate(template.id)
                            }
                        },
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(150.dp).background(palette.chromeBackground),
                        contentAlignment = Alignment.Center,
                    ) {
                        GuiPreview(project, textures, Modifier.fillMaxSize(), zoom = zoom)
                    }
                    Column(Modifier.padding(12.dp)) {
                        Text(template.title, style = MaterialTheme.typography.bodyMedium, color = palette.chromeText)
                        Text(
                            template.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.chromeTextMuted,
                        )
                        Text(
                            "${template.edition.displayName}  ·  ${template.form.displayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (template.edition == state.edition) palette.accent else palette.chromeTextMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssetsSheet(
    app: AndroidAppState,
    controller: EditorController,
    state: EditorState,
    textures: TextureCache,
    onImportImages: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SheetTitle("Textures", "Imported images are stored inside the project file")
        Button(onClick = onImportImages, modifier = Modifier.fillMaxWidth()) { Text("Import images...") }

        if (state.project.textures.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing imported yet.\nAny PNG, JPG, WebP or GIF can be used as a skin, icon or background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.chromeTextMuted,
                )
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(state.project.textures, key = { it.id }) { asset ->
                val bitmap = textures.resolve(asset.id)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.chromeBackground)
                            .border(1.dp, palette.chromeBorder, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap,
                                contentDescription = asset.name,
                                contentScale = ContentScale.Fit,
                                filterQuality = FilterQuality.None,
                                modifier = Modifier.fillMaxSize().padding(3.dp),
                            )
                        } else {
                            Text("?", color = palette.chromeTextMuted)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(asset.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        Text(
                            "${asset.width}x${asset.height}  ·  ${asset.approximateBytes / 1024} KB",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.chromeTextMuted,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val id = controller.addElement(
                                ElementCatalog.IMAGE_PLACEHOLDER,
                                com.mcguidesigner.core.model.IntPoint(
                                    state.project.canvas.width / 2,
                                    state.project.canvas.height / 2,
                                ),
                                centreOnPoint = true,
                            )
                            if (id != null) {
                                controller.setProp(id, "texture", TextureValue(asset.id))
                            }
                            app.sheet = MobileSheet.NONE
                            app.section = MobileSection.DESIGN
                        },
                    ) { Text("Place") }
                    Text(
                        "✕",
                        color = ErrorRed,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { controller.removeTexture(asset.id) }
                            .padding(12.dp),
                    )
                }
                Divider(color = palette.chromeBorder)
            }
        }
    }
}

@Composable
private fun IssuesSheet(controller: EditorController, state: EditorState) {
    val palette = LocalSkinPalette.current
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SheetTitle(
            "Validation",
            "${state.validation.errorCount} error(s), ${state.validation.warningCount} warning(s)",
        )
        if (state.validation.issues.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("Everything checks out.", color = palette.accent)
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.validation.issues) { issue ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = issue.elementId != null) {
                            issue.elementId?.let { controller.select(it) }
                        }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier.size(10.dp).background(
                            when (issue.severity) {
                                Severity.ERROR -> ErrorRed
                                Severity.WARNING -> WarningAmber
                                Severity.INFO -> InfoBlue
                            },
                        ),
                    )
                    Column {
                        Text(issue.message, style = MaterialTheme.typography.bodySmall)
                        issue.fixHint?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = palette.chromeTextMuted)
                        }
                    }
                }
                Divider(color = palette.chromeBorder)
            }
        }
    }
}

@Composable
private fun ExportSheet(app: AndroidAppState, state: EditorState, onExport: (ExportTarget) -> Unit) {
    val palette = LocalSkinPalette.current
    var target by remember { mutableStateOf(app.exportTarget) }
    val bundle = remember(state.project, target, app.codeTarget) {
        ExportManager.export(state.project, target, app.codeTarget)
    }
    val available = remember(state.edition) { ExportManager.availableTargets(state.edition).distinct() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SheetTitle("Export", "Mobile exports are written as a single .zip")

        available.forEach { candidate ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (candidate == target) palette.accentMuted else palette.chromePanelAlt)
                    .clickable { target = candidate }
                    .padding(14.dp),
            ) {
                Column {
                    Text(candidate.displayName, style = MaterialTheme.typography.bodyMedium)
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

        Text(
            "${bundle.fileCount} file(s), about ${bundle.totalBytes / 1024} KB",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(palette.chromeBackground)
                .verticalScroll(rememberScrollState())
                .padding(10.dp),
        ) {
            Text(
                bundle.tree(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = palette.chromeTextMuted,
            )
        }

        if (bundle.warnings.isNotEmpty()) {
            Text(
                "${bundle.warnings.size} note(s) - check the Issues sheet",
                style = MaterialTheme.typography.labelSmall,
                color = if (bundle.hasBlockingErrors) ErrorRed else WarningAmber,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Button(
            onClick = {
                app.exportTarget = target
                onExport(target)
            },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        ) { Text("Export as .zip") }

        Box(Modifier.height(24.dp))
    }
}

@Composable
private fun ProjectSheet(controller: EditorController, state: EditorState) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SheetTitle("Project", "Identity used by both exporters")
        MobileTextField("Name", state.project.name) { controller.renameProject(it) }
        MobileTextField("Namespace", state.project.meta.namespace) { value ->
            controller.updateMeta { it.copy(namespace = value) }
        }
        MobileTextField("Screen id", state.project.meta.screenId) { value ->
            controller.updateMeta { it.copy(screenId = value) }
        }
        MobileTextField("Author", state.project.meta.author) { value ->
            controller.updateMeta { it.copy(author = value) }
        }
        MobileTextField("Description", state.project.meta.description, singleLine = false) { value ->
            controller.updateMeta { it.copy(description = value) }
        }
        Box(Modifier.height(40.dp))
    }
}

@Composable
private fun CanvasSheet(controller: EditorController, state: EditorState) {
    val canvas = state.project.canvas
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        SheetTitle("Canvas", "${canvas.width} x ${canvas.height} GUI pixels")

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MobileNumberField("Width", canvas.width, Modifier.weight(1f), min = 16) {
                controller.updateCanvas { spec -> spec.copy(width = it) }
            }
            MobileNumberField("Height", canvas.height, Modifier.weight(1f), min = 16) {
                controller.updateCanvas { spec -> spec.copy(height = it) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
            MobileNumberField("Grid", canvas.gridSize, Modifier.weight(1f), min = 0, max = 64) {
                controller.updateCanvas { spec -> spec.copy(gridSize = it) }
            }
            MobileNumberField("GUI scale", canvas.guiScale, Modifier.weight(1f), min = 1, max = 6) {
                controller.updateCanvas { spec -> spec.copy(guiScale = it) }
            }
        }

        SheetSection("Helpers")
        SwitchRow("Show grid", state.showGrid) { controller.toggleGrid() }
        SwitchRow("Snap to grid", state.snapToGrid) { controller.toggleSnapToGrid() }
        SwitchRow("Smart guides", state.snapToElements) { controller.toggleSnapToElements() }
        SwitchRow("Show safe area", state.showSafeArea) { controller.toggleSafeArea() }

        SheetSection("Presentation")
        MobileChoiceChips(
            label = "Backdrop",
            options = CanvasBackdrop.entries.map { it.name },
            selected = canvas.backdrop.name,
            display = { CanvasBackdrop.valueOf(it).displayName },
        ) { controller.updateCanvas { spec -> spec.copy(backdrop = CanvasBackdrop.valueOf(it)) } }

        MobileChoiceChips(
            label = "Target form",
            options = TargetForm.entries.map { it.name },
            selected = canvas.targetForm.name,
            display = { TargetForm.valueOf(it).displayName },
        ) { controller.updateCanvas { spec -> spec.copy(targetForm = TargetForm.valueOf(it)) } }

        if (canvas.targetForm == TargetForm.MOBILE) {
            SheetSection("Safe area")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MobileNumberField("L", canvas.safeArea.left, Modifier.weight(1f), min = 0) {
                    controller.updateCanvas { spec -> spec.copy(safeArea = spec.safeArea.copy(left = it)) }
                }
                MobileNumberField("T", canvas.safeArea.top, Modifier.weight(1f), min = 0) {
                    controller.updateCanvas { spec -> spec.copy(safeArea = spec.safeArea.copy(top = it)) }
                }
                MobileNumberField("R", canvas.safeArea.right, Modifier.weight(1f), min = 0) {
                    controller.updateCanvas { spec -> spec.copy(safeArea = spec.safeArea.copy(right = it)) }
                }
                MobileNumberField("B", canvas.safeArea.bottom, Modifier.weight(1f), min = 0) {
                    controller.updateCanvas { spec -> spec.copy(safeArea = spec.safeArea.copy(bottom = it)) }
                }
            }
        }
        Box(Modifier.height(40.dp))
    }
}

// ---------------------------------------------------------------------------
// Shared mobile widgets
// ---------------------------------------------------------------------------

@Composable
private fun SheetTitle(title: String, subtitle: String) {
    val palette = LocalSkinPalette.current
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = palette.chromeText)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = palette.chromeTextMuted)
    }
}

@Composable
private fun SheetSection(title: String) {
    val palette = LocalSkinPalette.current
    Column(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
            fontWeight = FontWeight.Bold,
        )
        Divider(Modifier.padding(top = 4.dp), color = palette.chromeBorder)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun MobileTextField(
    label: String,
    value: String,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    )
}

@Composable
private fun MobilePlainField(value: String, singleLine: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MobileNumberField(
    label: String?,
    value: Int,
    modifier: Modifier = Modifier,
    min: Int? = null,
    max: Int? = null,
    onChange: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { entered ->
            text = entered.filter { it.isDigit() || it == '-' }
            text.toIntOrNull()?.let {
                onChange(it.coerceIn(min ?: Int.MIN_VALUE, max ?: Int.MAX_VALUE))
            }
        },
        label = label?.let { { Text(it) } },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun MobileColorField(value: ColorValue, onChange: (ColorValue) -> Unit) {
    val palette = LocalSkinPalette.current
    var text by remember(value.argb) { mutableStateOf(value.toHex()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(value.argb))
                .border(1.dp, palette.chromeBorder, RoundedCornerShape(8.dp)),
        )
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                ColorValue.parse(it)?.let { parsed -> onChange(parsed) }
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Slider(
        value = value.alphaFraction,
        onValueChange = { alpha ->
            val a = (alpha * 255).toInt().coerceIn(0, 255).toLong()
            onChange(ColorValue((value.argb and 0xFFFFFF) or (a shl 24)))
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Wrapping chip row.
 *
 * Chips replace the desktop dropdowns throughout the mobile UI: every option
 * is one tap away instead of two, which matters a lot on a phone.
 */
@Composable
private fun MobileChoiceChips(
    label: String?,
    options: List<String>,
    selected: String,
    display: (String) -> String = { it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() } },
    highlighted: Set<String> = emptySet(),
    onSelect: (String) -> Unit,
) {
    val palette = LocalSkinPalette.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = palette.chromeText)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = {
                        Text(
                            display(option) + if (option in highlighted) " •" else "",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
        }
    }
}

private fun formatFloat(value: Float): String {
    val rounded = kotlin.math.round(value * 1000f) / 1000f
    return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
}
