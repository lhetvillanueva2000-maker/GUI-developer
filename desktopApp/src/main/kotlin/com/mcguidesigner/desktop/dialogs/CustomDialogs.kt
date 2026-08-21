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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.mcguidesigner.core.catalog.CustomPresets
import com.mcguidesigner.core.editor.EditorSettings
import com.mcguidesigner.core.editor.NudgePadCorner
import com.mcguidesigner.desktop.ActiveDialog
import com.mcguidesigner.desktop.AppState
import com.mcguidesigner.styles.theme.LocalSkinPalette

/**
 * The "＋ Custom" picker: shapes, animated imagery, and the catch-all element.
 *
 * Everything here lands on the canvas immediately rather than arming a
 * placement tool - the point of the button is that adding a triangle should
 * take one click, not two plus aiming.
 */
@Composable
fun AddCustomDialog(app: AppState) {
    val palette = LocalSkinPalette.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) { CustomPresets.search(query) }

    AlertDialog(
        onDismissRequest = { app.dialog = ActiveDialog.NONE },
        title = { Text("Add anything") },
        text = {
            Column(Modifier.width(560.dp)) {
                Text(
                    "Shapes, animated images and free-form elements. They all behave like any " +
                        "other component: resize them, restyle them, and they export with the rest.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                )
                Box(Modifier.height(10.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.height(10.dp))

                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    if (query.isBlank()) {
                        CustomPresets.Group.entries.forEach { group ->
                            PresetSection(
                                title = group.title,
                                subtitle = group.subtitle,
                                presets = CustomPresets.presetsFor(group),
                                onPick = { app.addCustomPreset(it) },
                            )
                        }
                    } else if (filtered.isEmpty()) {
                        Text(
                            "Nothing matches '$query'.",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.chromeTextMuted,
                        )
                    } else {
                        PresetSection(
                            title = "${filtered.size} match${if (filtered.size == 1) "" else "es"}",
                            subtitle = "",
                            presets = filtered,
                            onPick = { app.addCustomPreset(it) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { app.dialog = ActiveDialog.NONE }) { Text("Done") }
        },
    )
}

@Composable
private fun PresetSection(
    title: String,
    subtitle: String,
    presets: List<CustomPresets.Preset>,
    onPick: (CustomPresets.Preset) -> Unit,
) {
    val palette = LocalSkinPalette.current
    Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    if (subtitle.isNotBlank()) {
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = palette.chromeTextMuted)
    }
    Box(Modifier.height(6.dp))

    // A grid laid out by hand: LazyVerticalGrid inside a scrolling column
    // needs a bounded height, and hard-coding one would clip the last row of
    // whichever section grows.
    presets.chunked(PRESETS_PER_ROW).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { preset ->
                PresetCard(preset, Modifier.weight(1f)) { onPick(preset) }
            }
            // Keeps the last row's cards the same width as every other row's.
            repeat(PRESETS_PER_ROW - row.size) { Box(Modifier.weight(1f)) }
        }
        Box(Modifier.height(6.dp))
    }
    Box(Modifier.height(8.dp))
}

private const val PRESETS_PER_ROW = 4

@Composable
private fun PresetCard(preset: CustomPresets.Preset, modifier: Modifier, onClick: () -> Unit) {
    val palette = LocalSkinPalette.current
    com.mcguidesigner.desktop.widgets.WithTooltip(preset.description.ifBlank { preset.label }) {
        Column(
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(palette.chromePanelAlt)
                .border(1.dp, palette.chromeBorder, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(preset.glyph, style = MaterialTheme.typography.titleLarge)
            Box(Modifier.height(4.dp))
            Text(
                preset.label,
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeText,
                maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Editor settings
// ---------------------------------------------------------------------------

/**
 * Behaviour settings for the editor itself.
 *
 * Kept separate from Project settings (which describe the screen being made)
 * and Appearance (which describes the app's looks): this is the dialog for how
 * the editor *acts*.
 */
@Composable
fun EditorSettingsDialog(app: AppState) {
    val palette = LocalSkinPalette.current
    val controller = app.controller
    var draft by remember { mutableStateOf(controller.current.settings) }

    fun commit(next: EditorSettings) {
        draft = next
        app.applyEditorSettings(next)
    }

    AlertDialog(
        onDismissRequest = { app.dialog = ActiveDialog.NONE },
        title = { Text("Editor settings") },
        text = {
            Column(Modifier.width(560.dp).heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
                SettingsSection("Moving elements")

                StepSetting(
                    label = "Small step",
                    help = "How far one press of a move button - or an arrow key - shifts the selection.",
                    value = draft.nudgeStep,
                    onChange = { commit(draft.copy(nudgeStep = it)) },
                )
                Box(Modifier.height(10.dp))
                StepSetting(
                    label = "Big step",
                    help = "Used while Shift is held, or when the pad's step button is lit.",
                    value = draft.largeNudgeStep,
                    onChange = { commit(draft.copy(largeNudgeStep = it)) },
                )

                Box(Modifier.height(10.dp))
                ToggleSetting(
                    label = "Arrow keys use the same steps",
                    help = "On, so the keyboard and the buttons always move by the same amount. " +
                        "Off restores the fixed 1px / Shift+8px arrow keys.",
                    checked = draft.arrowKeysUseNudgeStep,
                    onChange = { commit(draft.copy(arrowKeysUseNudgeStep = it)) },
                )
                ToggleSetting(
                    label = "Snap moves to the grid",
                    help = "Moves to the next grid line instead of by a fixed number of pixels.",
                    checked = draft.nudgeSnapsToGrid,
                    onChange = { commit(draft.copy(nudgeSnapsToGrid = it)) },
                )
                ToggleSetting(
                    label = "Show the move pad on the canvas",
                    help = "The four arrows that appear over the canvas when something is selected.",
                    checked = draft.showNudgePad,
                    onChange = { commit(draft.copy(showNudgePad = it)) },
                )

                if (draft.showNudgePad) {
                    Box(Modifier.height(8.dp))
                    Text("Move pad corner", style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        NudgePadCorner.entries.forEach { corner ->
                            ChoiceChip(
                                label = corner.displayName,
                                selected = draft.nudgePadCorner == corner,
                                onClick = { commit(draft.copy(nudgePadCorner = corner)) },
                            )
                        }
                    }
                }

                Box(Modifier.height(16.dp))
                Divider(color = palette.chromeBorder)
                Box(Modifier.height(12.dp))

                SettingsSection("Editing")

                StepSetting(
                    label = "Duplicate offset",
                    help = "How far a duplicate lands from its original, so the copy is not hidden " +
                        "exactly on top of what it came from.",
                    value = draft.duplicateOffset,
                    onChange = { commit(draft.copy(duplicateOffset = it)) },
                    allowZero = true,
                )
                Box(Modifier.height(10.dp))
                ToggleSetting(
                    label = "Ask before deleting",
                    help = "Off by default - undo already covers a mistaken delete.",
                    checked = draft.confirmBeforeDelete,
                    onChange = { commit(draft.copy(confirmBeforeDelete = it)) },
                )

                Box(Modifier.height(16.dp))
                Divider(color = palette.chromeBorder)
                Box(Modifier.height(12.dp))

                SettingsSection("Canvas & safety")

                ToggleSetting(
                    label = "Play animated images",
                    help = "Off pins every animated element to its first frame, which makes laying " +
                        "out around one much easier.",
                    checked = draft.playAnimations,
                    onChange = { commit(draft.copy(playAnimations = it)) },
                )

                Box(Modifier.height(10.dp))
                Text("Autosave", style = MaterialTheme.typography.labelMedium)
                Text(
                    if (draft.autosaveSeconds == 0) {
                        "Off. A crash would lose unsaved work."
                    } else {
                        "Snapshots unsaved work every ${draft.autosaveSeconds} seconds, " +
                            "and offers it back after a crash."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                )
                Box(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 5, 10, 30, 60).forEach { seconds ->
                        ChoiceChip(
                            label = if (seconds == 0) "Off" else "${seconds}s",
                            selected = draft.autosaveSeconds == seconds,
                            onClick = { commit(draft.copy(autosaveSeconds = seconds)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { app.dialog = ActiveDialog.NONE }) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = { commit(EditorSettings()) }) { Text("Reset to defaults") }
        },
    )
}

@Composable
private fun SettingsSection(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Box(Modifier.height(8.dp))
}

/**
 * A pixel-count setting: preset chips for the common values, a slider for
 * everything in between.
 *
 * The presets are the numbers that actually come up - 1 for detail work, 8 for
 * the Java container grid, 16 for a block texture - so the usual case is one
 * click and the slider is there for the unusual one.
 */
@Composable
private fun StepSetting(
    label: String,
    help: String,
    value: Int,
    onChange: (Int) -> Unit,
    allowZero: Boolean = false,
) {
    val palette = LocalSkinPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
        Text(
            "${value}px",
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
            color = palette.accent,
        )
    }
    Text(help, style = MaterialTheme.typography.labelSmall, color = palette.chromeTextMuted)
    Box(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val presets = if (allowZero) listOf(0) + EditorSettings.STEP_PRESETS else EditorSettings.STEP_PRESETS
        presets.forEach { preset ->
            ChoiceChip(
                label = if (preset == 0) "None" else "$preset",
                selected = value == preset,
                onClick = { onChange(preset) },
            )
        }
    }
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.toInt()) },
        valueRange = (if (allowZero) 0f else EditorSettings.MIN_STEP.toFloat())..32f,
        steps = 30,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ToggleSetting(label: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalSkinPalette.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(help, style = MaterialTheme.typography.labelSmall, color = palette.chromeTextMuted)
        }
        Box(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalSkinPalette.current
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) palette.accentMuted else palette.chromePanelAlt)
            .border(
                1.dp,
                if (selected) palette.accent else Color.Transparent,
                RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) palette.chromeText else palette.chromeTextMuted,
        )
    }
}

// ---------------------------------------------------------------------------
// Delete confirmation
// ---------------------------------------------------------------------------

/**
 * Shown before a delete, but only when "Ask before deleting" is on.
 *
 * Off by default: undo already covers a mistaken delete, and a dialog on every
 * press of Delete would be far more annoying than the mistake it prevents.
 */
@Composable
fun ConfirmDeleteDialog(app: AppState) {
    val palette = LocalSkinPalette.current
    val selected = app.controller.current.selectedElements
    val summary = when (selected.size) {
        0 -> "Nothing is selected."
        1 -> "'${selected.first().name}' will be removed."
        else -> "${selected.size} elements will be removed."
    }
    val children = selected.sumOf { it.walk().count() } - selected.size

    AlertDialog(
        onDismissRequest = { app.dialog = ActiveDialog.NONE },
        title = { Text("Delete selection?") },
        text = {
            Column(Modifier.width(380.dp)) {
                Text(summary, style = MaterialTheme.typography.bodyMedium)
                if (children > 0) {
                    Box(Modifier.height(6.dp))
                    Text(
                        "$children nested element${if (children == 1) "" else "s"} " +
                            "inside the selection will go with it.",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.chromeTextMuted,
                    )
                }
                Box(Modifier.height(10.dp))
                Text(
                    "Ctrl+Z undoes this. You can turn this prompt off in Editor Settings.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { app.confirmDeleteSelection() }) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = { app.dialog = ActiveDialog.NONE }) { Text("Keep") }
        },
    )
}
