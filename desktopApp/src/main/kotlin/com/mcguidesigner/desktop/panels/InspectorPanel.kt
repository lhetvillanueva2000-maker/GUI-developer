@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.catalog.PropType
import com.mcguidesigner.core.catalog.PropertySpec
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.model.Anchor
import com.mcguidesigner.core.model.BoolValue
import com.mcguidesigner.core.model.CanvasBackdrop
import com.mcguidesigner.core.model.ColorValue
import com.mcguidesigner.core.model.EnumValue
import com.mcguidesigner.core.model.FloatValue
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.model.Insets
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.IntValue
import com.mcguidesigner.core.model.ListValue
import com.mcguidesigner.core.model.PropValue
import com.mcguidesigner.core.model.StringValue
import com.mcguidesigner.core.model.TargetForm
import com.mcguidesigner.core.model.TextureValue
import com.mcguidesigner.styles.theme.LocalSkinPalette

/**
 * The property inspector.
 *
 * Entirely data-driven: it renders whatever the catalog declares for the
 * selected element's type and the project's edition, so a new widget property
 * shows up here without any UI change.
 */
@Composable
fun InspectorPanel(
    controller: EditorController,
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    val element = state.primaryElement

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        if (element == null) {
            CanvasSection(controller, state)
            return@Column
        }

        val definition = ElementCatalog[element.type]
        if (definition == null) {
            Text(
                "Unknown element type '${element.type}'.",
                style = MaterialTheme.typography.bodySmall,
                color = com.mcguidesigner.styles.theme.ErrorRed,
            )
            return@Column
        }

        Text(
            definition.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = palette.chromeText,
        )
        Text(
            "${element.type}  ·  ${element.id}",
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
            fontFamily = FontFamily.Monospace,
        )
        if (state.selection.size > 1) {
            Text(
                "${state.selection.size} elements selected - edits apply to the highlighted one; " +
                    "use the align tools for the whole set.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.accent,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        SectionSpacer()
        LabelledField("Name", element.name) { controller.rename(element.id, it) }

        SectionSpacer()
        SectionHeader("Transform")
        TransformEditors(controller, element)

        SectionSpacer()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ToggleRow("Visible", element.visible) { controller.setVisible(element.id, it) }
            ToggleRow("Locked", element.locked) { controller.setLocked(element.id, it) }
        }

        // Interaction-state editing: pick a state, then every edit below is
        // stored as an override for that state only.
        var editedState by remember(element.id) { mutableStateOf(InteractionState.NORMAL) }
        if (definition.interactive) {
            SectionSpacer()
            SectionHeader("Interaction state")
            StateSelector(
                states = if (state.edition == com.mcguidesigner.core.model.Edition.BEDROCK) {
                    InteractionState.touchStates
                } else {
                    InteractionState.entries
                },
                selected = editedState,
                overridden = element.stateOverrides.keys,
                onSelect = { editedState = it },
            )
            if (editedState != InteractionState.NORMAL) {
                Text(
                    "Editing the ${editedState.displayName.lowercase()} state. " +
                        "Only changed properties are stored as overrides.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        val groups = definition.propertiesFor(state.edition).groupBy { it.group }
        groups.forEach { (group, specs) ->
            SectionSpacer()
            SectionHeader(group)
            specs.forEach { spec ->
                PropertyEditor(controller, state, element, spec, editedState)
            }
        }

        Box(Modifier.height(48.dp))
    }
}

// ---------------------------------------------------------------------------
// Transform
// ---------------------------------------------------------------------------

@Composable
private fun TransformEditors(controller: EditorController, element: GuiElement) {
    val bounds = element.bounds
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("X", bounds.x, Modifier.weight(1f)) {
            controller.setBounds(element.id, bounds.copy(x = it), label = "Move")
        }
        NumberField("Y", bounds.y, Modifier.weight(1f)) {
            controller.setBounds(element.id, bounds.copy(y = it), label = "Move")
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        NumberField("Width", bounds.width, Modifier.weight(1f), min = 1) {
            controller.setBounds(element.id, bounds.copy(width = it))
        }
        NumberField("Height", bounds.height, Modifier.weight(1f), min = 1) {
            controller.setBounds(element.id, bounds.copy(height = it))
        }
    }
    EnumDropdown(
        label = "Anchor",
        options = Anchor.entries.map { it.name },
        selected = element.anchor.name,
        display = { Anchor.valueOf(it).displayName },
        modifier = Modifier.padding(top = 8.dp),
    ) { controller.setAnchor(element.id, Anchor.valueOf(it)) }
}

// ---------------------------------------------------------------------------
// Property editors
// ---------------------------------------------------------------------------

@Composable
private fun PropertyEditor(
    controller: EditorController,
    state: EditorState,
    element: GuiElement,
    spec: PropertySpec,
    editedState: InteractionState,
) {
    val palette = LocalSkinPalette.current
    val effective = element.propsFor(editedState)
    val value = effective[spec.key] ?: spec.default
    val isOverride = editedState != InteractionState.NORMAL &&
        element.stateOverrides[editedState]?.containsKey(spec.key) == true

    fun write(next: PropValue) {
        val target = if (spec.stateAware) editedState else InteractionState.NORMAL
        controller.setProp(element.id, spec.key, next, target)
    }

    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                spec.label,
                style = MaterialTheme.typography.labelMedium,
                color = palette.chromeText,
                modifier = Modifier.weight(1f),
            )
            if (isOverride) {
                Text(
                    "override ✕",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent,
                    modifier = Modifier
                        .clickable { controller.clearStateOverride(element.id, spec.key, editedState) }
                        .padding(horizontal = 4.dp),
                )
            }
            if (!spec.stateAware && editedState != InteractionState.NORMAL) {
                Text(
                    "shared",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                )
            }
        }

        when (spec.type) {
            PropType.TEXT -> PlainField(value.asText(), singleLine = true) { write(StringValue(it)) }
            PropType.MULTILINE_TEXT -> PlainField(value.asText(), singleLine = false) { write(StringValue(it)) }

            PropType.INT -> NumberField(
                label = null,
                value = (value as? IntValue)?.value ?: 0,
                min = spec.min?.toInt(),
                max = spec.max?.toInt(),
            ) { write(IntValue(it)) }

            PropType.FLOAT -> FloatEditor(
                value = (value as? FloatValue)?.value ?: (value as? IntValue)?.value?.toFloat() ?: 0f,
                spec = spec,
            ) { write(FloatValue(it)) }

            PropType.BOOL -> ToggleRow(
                label = if ((value as? BoolValue)?.value == true) "On" else "Off",
                checked = (value as? BoolValue)?.value ?: false,
            ) { write(BoolValue(it)) }

            PropType.COLOR -> ColorEditor(value as? ColorValue ?: ColorValue(0xFF000000)) { write(it) }

            PropType.ENUM -> EnumDropdown(
                label = null,
                options = spec.options,
                selected = value.asText(),
            ) { write(EnumValue(it)) }

            PropType.TEXTURE -> TexturePicker(
                state = state,
                selected = (value as? TextureValue)?.assetId,
            ) { write(TextureValue(it)) }

            PropType.STRING_LIST -> PlainField(
                value = (value as? ListValue)?.values?.joinToString("\n") { it.asText() } ?: "",
                singleLine = false,
            ) { text ->
                write(ListValue(text.split('\n').filter { it.isNotBlank() }.map { StringValue(it.trim()) }))
            }
        }

        if (spec.help.isNotBlank()) {
            Text(
                spec.help,
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun FloatEditor(value: Float, spec: PropertySpec, onChange: (Float) -> Unit) {
    val palette = LocalSkinPalette.current
    val min = spec.min ?: 0f
    val max = spec.max ?: 1f
    Column {
        if (spec.min != null && spec.max != null) {
            Slider(
                value = value.coerceIn(min, max),
                onValueChange = onChange,
                valueRange = min..max,
                modifier = Modifier.fillMaxWidth().height(28.dp),
            )
        }
        DecimalField(value) { onChange(it) }
        if (spec.min != null && spec.max != null) {
            Text(
                "range $min .. $max",
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
            )
        }
    }
}

@Composable
private fun ColorEditor(value: ColorValue, onChange: (ColorValue) -> Unit) {
    val palette = LocalSkinPalette.current
    var text by remember(value.argb) { mutableStateOf(value.toHex()) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(value.argb))
                .border(1.dp, palette.chromeBorder, RoundedCornerShape(4.dp)),
        )
        OutlinedTextField(
            value = text,
            onValueChange = { entered ->
                text = entered
                ColorValue.parse(entered)?.let { onChange(ColorValue(it.argb)) }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f),
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Alpha", style = MaterialTheme.typography.labelSmall, color = palette.chromeTextMuted)
        Slider(
            value = value.alphaFraction,
            onValueChange = { alpha ->
                val a = (alpha * 255).toInt().coerceIn(0, 255).toLong()
                onChange(ColorValue((value.argb and 0xFFFFFF) or (a shl 24)))
            },
            modifier = Modifier.weight(1f).height(24.dp).padding(start = 8.dp),
        )
    }
}

@Composable
private fun TexturePicker(state: EditorState, selected: String?, onChange: (String?) -> Unit) {
    val palette = LocalSkinPalette.current
    val options = listOf<String?>(null) + state.project.textures.map { it.id }
    var expanded by remember { mutableStateOf(false) }
    val label = selected?.let { id -> state.project.texture(id)?.name ?: "(missing: $id)" } ?: "None"

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(palette.chromePanelAlt)
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = palette.chromeText, modifier = Modifier.weight(1f))
            Text("▾", color = palette.chromeTextMuted)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { id ->
                val name = id?.let { key -> state.project.texture(key)?.name ?: key } ?: "None"
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onChange(id)
                        expanded = false
                    },
                )
            }
            if (state.project.textures.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("Import images from the Assets tab", color = palette.chromeTextMuted) },
                    onClick = { expanded = false },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Canvas / project section (shown when nothing is selected)
// ---------------------------------------------------------------------------

@Composable
private fun CanvasSection(controller: EditorController, state: EditorState) {
    val palette = LocalSkinPalette.current
    val canvas = state.project.canvas

    Text("Screen", style = MaterialTheme.typography.titleMedium, color = palette.chromeText)
    Text(
        "Nothing selected - editing the canvas itself.",
        style = MaterialTheme.typography.labelSmall,
        color = palette.chromeTextMuted,
    )

    SectionSpacer()
    LabelledField("Project name", state.project.name) { controller.renameProject(it) }

    SectionSpacer()
    SectionHeader("Canvas")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField("Width", canvas.width, Modifier.weight(1f), min = 16) {
            controller.updateCanvas { spec -> spec.copy(width = it) }
        }
        NumberField("Height", canvas.height, Modifier.weight(1f), min = 16) {
            controller.updateCanvas { spec -> spec.copy(height = it) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        NumberField("Grid", canvas.gridSize, Modifier.weight(1f), min = 0, max = 64) {
            controller.updateCanvas { spec -> spec.copy(gridSize = it) }
        }
        NumberField("GUI scale", canvas.guiScale, Modifier.weight(1f), min = 1, max = 6) {
            controller.updateCanvas { spec -> spec.copy(guiScale = it) }
        }
    }

    SectionSpacer()
    EnumDropdown(
        label = "Backdrop",
        options = CanvasBackdrop.entries.map { it.name },
        selected = canvas.backdrop.name,
        display = { CanvasBackdrop.valueOf(it).displayName },
    ) { controller.updateCanvas { spec -> spec.copy(backdrop = CanvasBackdrop.valueOf(it)) } }

    EnumDropdown(
        label = "Target form",
        options = TargetForm.entries.map { it.name },
        selected = canvas.targetForm.name,
        display = { TargetForm.valueOf(it).displayName },
        modifier = Modifier.padding(top = 8.dp),
    ) { controller.updateCanvas { spec -> spec.copy(targetForm = TargetForm.valueOf(it)) } }

    if (canvas.targetForm == TargetForm.MOBILE) {
        SectionSpacer()
        SectionHeader("Safe area")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Left", canvas.safeArea.left, Modifier.weight(1f), min = 0) {
                controller.updateCanvas { spec -> spec.copy(safeArea = spec.safeArea.copy(left = it)) }
            }
            NumberField("Top", canvas.safeArea.top, Modifier.weight(1f), min = 0) {
                controller.updateCanvas { spec -> spec.copy(safeArea = spec.safeArea.copy(top = it)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            NumberField("Right", canvas.safeArea.right, Modifier.weight(1f), min = 0) {
                controller.updateCanvas { spec -> spec.copy(safeArea = spec.safeArea.copy(right = it)) }
            }
            NumberField("Bottom", canvas.safeArea.bottom, Modifier.weight(1f), min = 0) {
                controller.updateCanvas { spec -> spec.copy(safeArea = spec.safeArea.copy(bottom = it)) }
            }
        }
    }

    SectionSpacer()
    SectionHeader("Export identity")
    LabelledField("Namespace", state.project.meta.namespace) { value ->
        controller.updateMeta { it.copy(namespace = value) }
    }
    LabelledField("Screen id", state.project.meta.screenId, Modifier.padding(top = 8.dp)) { value ->
        controller.updateMeta { it.copy(screenId = value) }
    }
    LabelledField("Author", state.project.meta.author, Modifier.padding(top = 8.dp)) { value ->
        controller.updateMeta { it.copy(author = value) }
    }
    LabelledField("Description", state.project.meta.description, Modifier.padding(top = 8.dp), singleLine = false) { value ->
        controller.updateMeta { it.copy(description = value) }
    }
    Box(Modifier.height(48.dp))
}

// ---------------------------------------------------------------------------
// Small reusable pieces
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String) {
    val palette = LocalSkinPalette.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
            fontWeight = FontWeight.Bold,
        )
        Divider(Modifier.padding(top = 3.dp, bottom = 4.dp), color = palette.chromeBorder)
    }
}

@Composable
private fun SectionSpacer() = Box(Modifier.height(14.dp))

@Composable
private fun StateSelector(
    states: List<InteractionState>,
    selected: InteractionState,
    overridden: Set<InteractionState>,
    onSelect: (InteractionState) -> Unit,
) {
    val palette = LocalSkinPalette.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        states.forEach { candidate ->
            val active = candidate == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (active) palette.accentMuted else palette.chromePanelAlt)
                    .border(
                        1.dp,
                        if (candidate in overridden) palette.accent else Color.Transparent,
                        RoundedCornerShape(4.dp),
                    )
                    .clickable { onSelect(candidate) }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text(
                    candidate.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) palette.chromeText else palette.chromeTextMuted,
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalSkinPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Switch(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.labelMedium, color = palette.chromeText)
    }
}

@Composable
private fun LabelledField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        modifier = modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun PlainField(value: String, singleLine: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.labelMedium,
    )
}

/**
 * Integer field that tolerates transient invalid text (an empty box or a lone
 * minus sign) instead of snapping the value back while the user is typing.
 */
@Composable
private fun NumberField(
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
            text.toIntOrNull()?.let { parsed ->
                val clamped = parsed.coerceIn(min ?: Int.MIN_VALUE, max ?: Int.MAX_VALUE)
                onChange(clamped)
            }
        },
        label = label?.let { { Text(it) } },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
    )
}

@Composable
private fun DecimalField(value: Float, onChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(formatFloat(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { entered ->
            text = entered.filter { it.isDigit() || it == '.' || it == '-' }
            text.toFloatOrNull()?.let(onChange)
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
    )
}

private fun formatFloat(value: Float): String {
    val rounded = kotlin.math.round(value * 1000f) / 1000f
    return if (rounded == rounded.toInt().toFloat()) rounded.toInt().toString() else rounded.toString()
}

@Composable
private fun EnumDropdown(
    label: String?,
    options: List<String>,
    selected: String,
    modifier: Modifier = Modifier,
    display: (String) -> String = { it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() } },
    onSelect: (String) -> Unit,
) {
    val palette = LocalSkinPalette.current
    var expanded by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = palette.chromeText)
        }
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.chromePanelAlt)
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    display(selected),
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.chromeText,
                    modifier = Modifier.weight(1f),
                )
                Text("▾", color = palette.chromeTextMuted)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(display(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
