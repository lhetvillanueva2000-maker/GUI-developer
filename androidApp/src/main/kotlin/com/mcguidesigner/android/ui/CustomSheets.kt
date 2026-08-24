package com.mcguidesigner.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import kotlin.math.abs

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
                    "Also the number in the middle of the pad - drag it to change this.",
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
                            "Drag the small grip on the pad's inner corner. " +
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
 * Four arrows that move the selection, floating over the canvas.
 *
 * On a phone this matters more than on the desktop: a one-pixel drag on a
 * touchscreen is not a thing anyone can do reliably, so without these the
 * finest adjustment available is whatever a fingertip can manage.
 *
 * The pad is deliberately *only* the four directions and one number. It used
 * to carry a big/small toggle in the centre and a "Step size" button beneath
 * it, which meant three controls answering one question - how far does this
 * move - and two of the three answers lived in a settings sheet somewhere
 * else. Now the centre is the step: drag it to change it, tap it to come back
 * to one. The corner handle changes how big the whole thing is drawn, because
 * the right size for a thumb control is a property of the thumb.
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

    // Both gestures accumulate in dp so a slow drag and a flick covering the
    // same distance do the same thing. Reset when the gesture starts, not when
    // it ends, so an interrupted drag cannot leak into the next one.
    var resizeFrom by remember { mutableStateOf(scale) }
    var resizeDrag by remember { mutableStateOf(0f) }
    var stepFrom by remember { mutableStateOf(settings.nudgeStep) }
    var stepDrag by remember { mutableStateOf(0f) }
    var adjusting by remember { mutableStateOf(false) }

    val padScale by animateFloatAsState(
        targetValue = if (adjusting) 1.04f else 1f,
        animationSpec = motion.spec(140),
        label = "nudge-pad-lift",
    )

    Surface(
        modifier = modifier.scale(padScale),
        color = palette.chromePanel.copy(alpha = 0.94f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Box {
            Column(
                Modifier.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                PadKey("▲", key) { controller.nudgeSelection(0, -1) }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    PadKey("◀", key) { controller.nudgeSelection(-1, 0) }
                    StepKey(
                        step = settings.nudgeStep,
                        size = key,
                        active = adjusting,
                        onTap = { controller.updateSettings { it.copy(nudgeStep = NudgePad.HOME_STEP) } },
                        onLongPress = onOpenSettings,
                        onDragStart = {
                            stepFrom = settings.nudgeStep
                            stepDrag = 0f
                            adjusting = true
                        },
                        onDrag = { deltaPx ->
                            stepDrag += with(density) { deltaPx.toDp().value }
                            controller.updateSettings {
                                it.copy(nudgeStep = NudgePad.stepAfterDrag(stepFrom, stepDrag))
                            }
                        },
                        onDragEnd = { adjusting = false },
                    )
                    PadKey("▶", key) { controller.nudgeSelection(1, 0) }
                }
                PadKey("▼", key) { controller.nudgeSelection(0, 1) }
            }

            // The handle sits on the corner pointing *into* the canvas, so it
            // is never the corner pressed against the screen edge - which is
            // the one a thumb cannot reach past.
            ResizeHandle(
                modifier = Modifier.align(
                    if (settings.nudgePadCorner.isRight) Alignment.TopStart else Alignment.TopEnd,
                ),
                onDragStart = {
                    resizeFrom = NudgePad.clampScale(settings.nudgePadScale)
                    resizeDrag = 0f
                    adjusting = true
                },
                onDrag = { deltaPx ->
                    // Dragging away from the anchored corner enlarges, whichever
                    // corner that is; the handle is mirrored, so the sign is too.
                    val towards = if (settings.nudgePadCorner.isRight) -deltaPx else deltaPx
                    resizeDrag += with(density) { towards.toDp().value }
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
        targetValue = if (pressed) 0.9f else 1f,
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
                        // Held rather than fired-and-forgotten so the key stays
                        // pressed for as long as the finger is on it, including
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
 * The centre: one number, which is how far a key moves things.
 *
 * Drag right or up for more, left or down for less; tap to come home to 1.
 * Both axes rather than one because the pad can sit in any corner and a thumb
 * has an easier direction in each.
 */
@Composable
private fun StepKey(
    step: Int,
    size: Dp,
    active: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    val motion = LocalMotion.current
    val background by animateColorAsState(
        targetValue = if (active) palette.accentMuted else palette.chromePanelAlt,
        animationSpec = motion.spec(140),
        label = "step-key-background",
    )

    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(background)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() })
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                ) { change, dragAmount ->
                    change.consume()
                    // Right and up both mean "more". Whichever axis moved
                    // further this frame is the one being used.
                    val delta = if (abs(dragAmount.x) >= abs(dragAmount.y)) {
                        dragAmount.x
                    } else {
                        -dragAmount.y
                    }
                    onDrag(delta)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$step",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                fontWeight = if (NudgePad.isLandmark(step)) FontWeight.Bold else FontWeight.Normal,
                color = if (active) palette.chromeText else palette.chromeTextMuted,
            )
            Text("px", style = MaterialTheme.typography.labelSmall, color = palette.chromeTextMuted)
        }
    }
}

/** The little grip that resizes the whole pad. */
@Composable
private fun ResizeHandle(
    modifier: Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    Box(
        modifier
            // A 24dp target around an 8dp mark: the mark has to be small enough
            // not to look like another button, and the target big enough to hit.
            .size(24.dp)
            .pointerInput(Unit) {
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
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(palette.chromeTextMuted.copy(alpha = 0.5f)),
        )
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
