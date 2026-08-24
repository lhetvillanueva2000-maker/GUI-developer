package com.mcguidesigner.android.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mcguidesigner.android.AndroidAppState
import com.mcguidesigner.android.MobileSheet
import com.mcguidesigner.core.catalog.CustomPresets
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorSettings
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.editor.NudgePad
import com.mcguidesigner.core.editor.NudgePadCorner
import com.mcguidesigner.exporters.CodeGenerator
import com.mcguidesigner.exporters.CodeTarget
import com.mcguidesigner.styles.theme.LocalMotion
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.spec

/**
 * The phone's "＋ Custom" sheet, the editor settings sheet, the delete
 * confirmation, and the on-canvas move pad.
 *
 * All four are the mobile presentation of things the desktop shell also has.
 * They share the *content* with it - the preset list and the settings type
 * both live in `:sharedCore` - and nothing else: a bottom sheet with 56dp rows
 * and a dialog with a 4-across grid are not the same UI, and pretending they
 * were is what makes a phone app feel like a shrunk desktop one.
 */

// ---------------------------------------------------------------------------
// Add anything
// ---------------------------------------------------------------------------

@Composable
fun AddCustomSheet(app: AndroidAppState) {
    val palette = LocalSkinPalette.current

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        MobileSheetTitle(
            "Add anything",
            "Shapes, animated images and free-form elements - all resizable and all exported.",
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            CustomPresets.Group.entries.forEach { group ->
                MobileSheetSection(group.title)
                Text(
                    group.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                // Three across: a 48dp glyph and a two-line label is the widest
                // a card can be and still fit three on the narrowest phone.
                CustomPresets.presetsFor(group).chunked(PRESETS_PER_ROW).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { preset ->
                            PresetTile(preset, Modifier.weight(1f)) { app.addCustomPreset(preset) }
                        }
                        repeat(PRESETS_PER_ROW - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
            Box(Modifier.height(24.dp))
        }
    }
}

private const val PRESETS_PER_ROW = 3

@Composable
private fun PresetTile(preset: CustomPresets.Preset, modifier: Modifier, onClick: () -> Unit) {
    val palette = LocalSkinPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(palette.chromePanelAlt)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(preset.glyph, style = MaterialTheme.typography.headlineSmall)
        Box(Modifier.height(6.dp))
        Text(
            preset.label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeText,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

// ---------------------------------------------------------------------------
// Editor settings
// ---------------------------------------------------------------------------

@Composable
fun EditorSettingsSheet(app: AndroidAppState) {
    val palette = LocalSkinPalette.current
    val context = LocalContext.current
    var draft by remember { mutableStateOf(app.controller.current.settings) }

    fun commit(next: EditorSettings) {
        draft = next
        app.applyEditorSettings(context, next)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        MobileSheetTitle("Editor settings", "How the editor behaves. Changes apply straight away.")

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            MobileSheetSection("Moving elements")

            StepRow(
                label = "Step",
                help = "How far one tap of a move arrow shifts the selection. " +
                    "This is the bar under the move pad; touch it anywhere to set it.",
                value = draft.nudgeStep,
                onChange = { commit(draft.copy(nudgeStep = it)) },
            )
            StepRow(
                label = "Big step",
                help = "For Shift + arrow keys on a connected keyboard.",
                value = draft.largeNudgeStep,
                onChange = { commit(draft.copy(largeNudgeStep = it)) },
            )

            SettingSwitch(
                label = "Snap moves to the grid",
                help = "Moves to the next grid line instead of by a fixed number of pixels.",
                checked = draft.nudgeSnapsToGrid,
                onChange = { commit(draft.copy(nudgeSnapsToGrid = it)) },
            )
            SettingSwitch(
                label = "Show the move pad",
                help = "The four arrows over the canvas when something is selected.",
                checked = draft.showNudgePad,
                onChange = { commit(draft.copy(showNudgePad = it)) },
            )
            SettingSwitch(
                label = "Arrow keys match the pad",
                help = "For a connected keyboard. Off restores the fixed 1px arrows.",
                checked = draft.arrowKeysUseNudgeStep,
                onChange = { commit(draft.copy(arrowKeysUseNudgeStep = it)) },
            )

            if (draft.showNudgePad) {
                Text(
                    "Move pad corner",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NudgePadCorner.entries.take(2).forEach { corner ->
                        MobileChip(
                            label = corner.displayName,
                            selected = draft.nudgePadCorner == corner,
                            modifier = Modifier.weight(1f),
                        ) { commit(draft.copy(nudgePadCorner = corner)) }
                    }
                }
                Row(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    NudgePadCorner.entries.drop(2).forEach { corner ->
                        MobileChip(
                            label = corner.displayName,
                            selected = draft.nudgePadCorner == corner,
                            modifier = Modifier.weight(1f),
                        ) { commit(draft.copy(nudgePadCorner = corner)) }
                    }
                }

                // The pad's own grab handle is the natural way to do this, and
                // it is also invisible until somebody notices it. A row here
                // makes the setting findable and, more usefully, undoable.
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Move pad size", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "Drag the grip on the pad's inner corner. " +
                                "Currently ${(NudgePad.clampScale(draft.nudgePadScale) * 100).toInt()}%.",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalSkinPalette.current.chromeTextMuted,
                        )
                    }
                    TextButton(
                        onClick = { commit(draft.copy(nudgePadScale = 1f)) },
                        enabled = NudgePad.clampScale(draft.nudgePadScale) != 1f,
                    ) { Text("Reset") }
                }
            }

            MobileSheetSection("Editing")

            StepRow(
                label = "Duplicate offset",
                help = "How far a copy lands from its original, so it is not hidden underneath it.",
                value = draft.duplicateOffset,
                onChange = { commit(draft.copy(duplicateOffset = it)) },
                allowZero = true,
            )
            SettingSwitch(
                label = "Ask before deleting",
                help = "Off by default - undo already covers a mistaken delete.",
                checked = draft.confirmBeforeDelete,
                onChange = { commit(draft.copy(confirmBeforeDelete = it)) },
            )

            MobileSheetSection("Canvas & safety")

            SettingSwitch(
                label = "Play animated images",
                help = "Off pins every animation to its first frame, which makes laying out " +
                    "around one much easier.",
                checked = draft.playAnimations,
                onChange = { commit(draft.copy(playAnimations = it)) },
            )

            Text(
                "Autosave",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                if (draft.autosaveSeconds == 0) {
                    "Off. Closing the app would lose unsaved work."
                } else {
                    "Keeps the working document every ${draft.autosaveSeconds} seconds."
                },
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
            )
            Row(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(0, 5, 10, 30, 60).forEach { seconds ->
                    MobileChip(
                        label = if (seconds == 0) "Off" else "${seconds}s",
                        selected = draft.autosaveSeconds == seconds,
                        modifier = Modifier.weight(1f),
                    ) { commit(draft.copy(autosaveSeconds = seconds)) }
                }
            }

            OutlinedButton(
                onClick = { commit(EditorSettings()) },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) { Text("Reset to defaults") }

            Box(Modifier.height(24.dp))
        }
    }
}

/**
 * A pixel-count setting: chips for the values that actually come up, a slider
 * for anything else.
 */
@Composable
private fun StepRow(
    label: String,
    help: String,
    value: Int,
    onChange: (Int) -> Unit,
    allowZero: Boolean = false,
) {
    val palette = LocalSkinPalette.current
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                "${value}px",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = palette.accent,
            )
        }
        Text(help, style = MaterialTheme.typography.labelSmall, color = palette.chromeTextMuted)
        Row(
            Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val presets = if (allowZero) listOf(0) + EditorSettings.STEP_PRESETS else EditorSettings.STEP_PRESETS
            presets.forEach { preset ->
                MobileChip(
                    label = if (preset == 0) "None" else "$preset",
                    selected = value == preset,
                    modifier = Modifier.weight(1f),
                ) { onChange(preset) }
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = (if (allowZero) 0f else EditorSettings.MIN_STEP.toFloat())..32f,
            steps = 30,
        )
    }
}

@Composable
private fun SettingSwitch(label: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalSkinPalette.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(help, style = MaterialTheme.typography.labelSmall, color = palette.chromeTextMuted)
        }
        Box(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun MobileChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) palette.accentMuted else palette.chromePanelAlt)
            .border(
                1.dp,
                if (selected) palette.accent else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) palette.chromeText else palette.chromeTextMuted,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------
// Delete confirmation
// ---------------------------------------------------------------------------

@Composable
fun ConfirmDeleteSheet(app: AndroidAppState, state: EditorState) {
    val palette = LocalSkinPalette.current
    val selected = state.selectedElements
    val nested = selected.sumOf { it.walk().count() } - selected.size

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        MobileSheetTitle(
            "Delete selection?",
            when (selected.size) {
                0 -> "Nothing is selected."
                1 -> "'${selected.first().name}' will be removed."
                else -> "${selected.size} elements will be removed."
            },
        )
        if (nested > 0) {
            Text(
                "$nested nested element${if (nested == 1) "" else "s"} will go with it.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
            )
        }
        Text(
            "Undo brings it back. You can turn this prompt off in Editor settings.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
            modifier = Modifier.padding(top = 8.dp),
        )

        Button(
            onClick = { app.confirmDeleteSelection() },
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(52.dp),
        ) { Text("Delete") }
        TextButton(
            onClick = { app.sheet = MobileSheet.NONE },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(52.dp),
        ) { Text("Keep") }

        Box(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------
// The move pad
// ---------------------------------------------------------------------------

/**
 * The move pad: four arrows over the canvas, and one bar under them.
 *
 * On a phone this matters more than on the desktop - a one-pixel drag on a
 * touchscreen is not a thing anyone can do reliably, so without these the
 * finest adjustment available is whatever a fingertip can manage.
 *
 * The layout is the design. **The middle is only up, down, left and right**,
 * with a hole where a fifth button would be: everything that has ever lived in
 * the centre of this cross was something people pressed by accident while
 * reaching for an arrow. **Under the middle is one control** - the step - and
 * it is a bar you touch at the value you want rather than a number you nudge
 * towards it. Before, that one question ("how far does this move?") had three
 * answers: a toggle in the centre, and two numbers in a settings sheet on
 * another screen.
 *
 * The grip on the inner corner resizes the whole thing, because the right size
 * for a thumb control is a property of the thumb.
 */
@Composable
fun MobileNudgePad(
    controller: EditorController,
    settings: EditorSettings,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    val density = LocalDensity.current
    val motion = LocalMotion.current

    val scale = NudgePad.clampScale(settings.nudgePadScale)
    val key = NudgePad.keyDp(scale).dp
    val gap = NudgePad.gapDp(scale).dp
    val crossWidth = key * 3 + gap * 2

    var resizeFrom by remember { mutableStateOf(scale) }
    var resizeDrag by remember { mutableStateOf(0f) }
    var adjusting by remember { mutableStateOf(false) }

    val lift by animateFloatAsState(
        targetValue = if (adjusting) 1.03f else 1f,
        animationSpec = motion.spec(140),
        label = "nudge-pad-lift",
    )

    Surface(
        modifier = modifier.scale(lift),
        color = palette.chromePanel.copy(alpha = 0.94f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box {
            Column(
                Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                PadKey("▲", key) { controller.nudgeSelection(0, -1) }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    PadKey("◀", key) { controller.nudgeSelection(-1, 0) }
                    // The hole in the middle of the cross. Deliberately not a
                    // button: it is the space your thumb crosses on the way to
                    // every one of the four, and anything here gets hit.
                    Spacer(Modifier.size(key))
                    PadKey("▶", key) { controller.nudgeSelection(1, 0) }
                }
                PadKey("▼", key) { controller.nudgeSelection(0, 1) }

                Spacer(Modifier.height(gap + 3.dp))

                StepBar(
                    step = settings.nudgeStep,
                    width = crossWidth,
                    height = (key.value * 0.62f).dp,
                    onStep = { controller.updateSettings { current -> current.copy(nudgeStep = it) } },
                    onOpenSettings = onOpenSettings,
                    onAdjustingChange = { adjusting = it },
                )
            }

            // The handle sits on the corner pointing *into* the canvas, so it
            // is never the corner pressed against the screen edge - the one a
            // thumb cannot reach past.
            ResizeHandle(
                modifier = Modifier.align(
                    if (settings.nudgePadCorner.isRight) Alignment.TopStart else Alignment.TopEnd,
                ),
                mirrored = !settings.nudgePadCorner.isRight,
                onDragStart = {
                    resizeFrom = NudgePad.clampScale(settings.nudgePadScale)
                    resizeDrag = 0f
                    adjusting = true
                },
                onDrag = { deltaPx ->
                    // Dragging away from the anchored corner enlarges, whichever
                    // corner that is; the handle is mirrored, so the sign is too.
                    val outwards = if (settings.nudgePadCorner.isRight) -deltaPx else deltaPx
                    resizeDrag += with(density) { outwards.toDp().value }
                    controller.updateSettings {
                        it.copy(nudgePadScale = NudgePad.scaleAfterDrag(resizeFrom, resizeDrag))
                    }
                },
                onDragEnd = { adjusting = false },
            )
        }
    }
}

/** One direction key. */
@Composable
private fun PadKey(glyph: String, size: Dp, onClick: () -> Unit) {
    val palette = LocalSkinPalette.current
    val motion = LocalMotion.current
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = motion.spec(90),
        label = "pad-key-press",
    )

    Box(
        Modifier
            .size(size)
            .scale(press)
            .clip(RoundedCornerShape(size / 4))
            .background(palette.chromePanelAlt)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        // Held rather than fired-and-forgotten, so the key stays
                        // lit for as long as the finger is on it - including
                        // when the gesture is cancelled by a scroll elsewhere.
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = MaterialTheme.typography.titleMedium, color = palette.chromeText)
    }
}

/**
 * The one control under the cross: how far an arrow moves things.
 *
 * Touch it anywhere and that position *is* the value - a slider, not a nudger.
 * Getting from 1 to 16 on a relative control is a long push, and a short push
 * back undoes an amount you cannot see; here every value in the range is one
 * touch away. The scale is exponential, so 1, 2, 4, 8, 16, 32, 64 and 128 are
 * evenly spread instead of 1-to-8 sharing the first few millimetres.
 *
 * A tap without a drag returns to 1, which is where this control spends most
 * of its life. A long press opens the settings, for the numbers this bar does
 * not own.
 */
@Composable
private fun StepBar(
    step: Int,
    width: Dp,
    height: Dp,
    onStep: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onAdjustingChange: (Boolean) -> Unit,
) {
    val palette = LocalSkinPalette.current
    val motion = LocalMotion.current
    var dragging by remember { mutableStateOf(false) }

    // Animated so the fill glides when the value is set from the settings
    // sheet, and tracks the finger one-to-one while dragging.
    val fill by animateFloatAsState(
        targetValue = NudgePad.fractionForStep(step),
        animationSpec = if (dragging) snap() else motion.spec(180),
        label = "step-bar-fill",
    )

    Box(
        Modifier
            .size(width, height)
            .clip(RoundedCornerShape(height / 2))
            .background(palette.chromePanelAlt)
            .pointerInput(width) {
                detectTapGestures(
                    onLongPress = { onOpenSettings() },
                    onTap = { onStep(NudgePad.HOME_STEP) },
                )
            }
            .pointerInput(width) {
                // Positional: the value comes from where the finger *is*, not
                // from how far it has travelled, so the first touch already
                // sets it.
                detectDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        onAdjustingChange(true)
                        onStep(NudgePad.stepAtFraction(offset.x / size.width.toFloat()))
                    },
                    onDragEnd = { dragging = false; onAdjustingChange(false) },
                    onDragCancel = { dragging = false; onAdjustingChange(false) },
                ) { change, _ ->
                    change.consume()
                    onStep(NudgePad.stepAtFraction(change.position.x / size.width.toFloat()))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fill.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(height / 2))
                .background(palette.accentMuted),
        )

        // The landmarks, drawn on top of the fill: 1 at the left end, then a
        // vanilla container's 8px grid and a 16px texture tile.
        NudgePad.LANDMARKS.forEach { landmark ->
            Box(
                Modifier
                    .padding(start = width * NudgePad.fractionForStep(landmark))
                    .size(2.dp, height / 3)
                    .background(palette.chromeTextMuted.copy(alpha = 0.45f)),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "step",
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
            )
            Text(
                "$step px",
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                fontWeight = if (NudgePad.isLandmark(step)) FontWeight.Bold else FontWeight.Normal,
                color = palette.chromeText,
            )
        }
    }
}

/**
 * The grip that resizes the whole pad.
 *
 * Three diagonal strokes rather than a dot: a dot reads as another button, and
 * this is the one thing on the pad that must not be pressed by mistake. The
 * touch target is 28dp around a 12dp mark, because the mark has to stay small
 * and the target has to be hittable.
 */
@Composable
private fun ResizeHandle(
    modifier: Modifier,
    mirrored: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    Box(
        modifier
            .size(28.dp)
            .pointerInput(mirrored) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) {
            val stroke = palette.chromeTextMuted.copy(alpha = 0.55f)
            val step = size.width / 3f
            repeat(3) { index ->
                val offset = step * (index + 0.5f)
                // Mirrored so the strokes always lean the way the pad grows.
                if (mirrored) {
                    drawLine(stroke, Offset(0f, offset), Offset(offset, 0f), strokeWidth = 1.5f)
                } else {
                    drawLine(
                        stroke,
                        Offset(size.width - offset, 0f),
                        Offset(size.width, offset),
                        strokeWidth = 1.5f,
                    )
                }
            }
        }
    }
}


// ---------------------------------------------------------------------------
// Local copies of the sheet chrome
// ---------------------------------------------------------------------------

@Composable
private fun MobileSheetTitle(title: String, subtitle: String) {
    val palette = LocalSkinPalette.current
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = palette.chromeText)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = palette.chromeTextMuted)
    }
}

@Composable
private fun MobileSheetSection(title: String) {
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

// ---------------------------------------------------------------------------
// Export language picker
// ---------------------------------------------------------------------------

/**
 * Which language the Code export produces.
 *
 * Split into "the game reads this" and everything else, for the same reason
 * the desktop dialog is: it is the difference between a file you drop into a
 * resource pack and one you open in an editor, and nothing about a file
 * extension makes that obvious.
 */
@Composable
fun MobileCodeTargetPicker(
    edition: com.mcguidesigner.core.model.Edition,
    selected: CodeTarget,
    onSelect: (CodeTarget) -> Unit,
) {
    val palette = LocalSkinPalette.current
    val all = remember(edition) { CodeGenerator.targetsFor(edition) }

    @Composable
    fun group(title: String, targets: List<CodeTarget>) {
        if (targets.isEmpty()) return
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        targets.forEach { target ->
            val isSelected = target == selected
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) palette.accentMuted else palette.chromePanelAlt)
                    .clickable { onSelect(target) }
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        target.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        ".${target.fileExtension}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = palette.chromeTextMuted,
                    )
                }
                if (target.description.isNotBlank()) {
                    Text(
                        target.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.chromeTextMuted,
                    )
                }
            }
        }
    }

    group("Minecraft reads these directly", all.filter { it.readByMinecraft })
    group("Code & artwork", all.filterNot { it.readByMinecraft })
}
