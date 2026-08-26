package com.mcguidesigner.styles.paint

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mcguidesigner.styles.paint.PaintIcons.checker
import com.mcguidesigner.styles.paint.PaintIcons.magicEraser
import com.mcguidesigner.styles.theme.LocalSkinPalette

/**
 * What is behind each of the five top-bar buttons.
 *
 * The grouping follows the reference: view options, then selection operations,
 * then how a stroke behaves, then guides, then material. What is *not* copied
 * is the contents - every control here does something this app actually
 * implements. A popover full of greyed-out rows advertising features that do
 * not exist is worse than a shorter popover, and it is the usual result of
 * copying somebody else's menu structure wholesale.
 */
@Composable
fun PaintPopoverContent(state: PaintState, onImportImage: (() -> Unit)? = null) {
    when (state.popover) {
        PaintPopover.VIEW -> ViewPopover(state)
        PaintPopover.SELECT -> SelectPopover(state)
        PaintPopover.STROKE -> StrokePopover(state)
        PaintPopover.RULER -> RulerPopover(state)
        PaintPopover.MATERIALS -> MaterialsPopover(state, onImportImage)
        PaintPopover.NONE -> Unit
    }
}

@Composable
private fun ViewPopover(state: PaintState) {
    PaintPopoverCard(pointerFromStart = 22.dp, modifier = Modifier.widthIn(max = 320.dp)) {
        PaintToggleRow("Grid", state.showGrid) { state.showGrid = it }
        if (state.showGrid) {
            PaintSectionLabel("Grid spacing")
            PaintSegmented(
                options = listOf("16", "32", "64", "128"),
                selectedIndex = listOf(16, 32, 64, 128).indexOf(state.gridStep).coerceAtLeast(0),
                onSelect = { state.gridStep = listOf(16, 32, 64, 128)[it] },
            )
        }

        PaintSectionLabel("Display when zoomed")
        PaintSegmented(
            options = listOf("Smooth", "Pixelated"),
            selectedIndex = if (state.pixelated) 1 else 0,
            onSelect = { state.pixelated = it == 1 },
        )

        PaintSectionLabel("View")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { PaintWideButton("Fit") { state.resetView() } }
            Box(Modifier.weight(1f)) {
                PaintWideButton("Zoom 100%") {
                    state.zoom = 1f
                }
            }
        }
        Text(
            "Zoom ${(state.zoom * 100).toInt()}%  ·  ${state.document.width} × ${state.document.height}",
            style = MaterialTheme.typography.labelSmall,
            color = LocalSkinPalette.current.chromeTextMuted,
        )
    }
}

/**
 * The selection menu.
 *
 * Every entry here operates on the whole active layer, because this build has
 * no marquee selection yet. Rather than show six greyed-out selection rows, the
 * ones that mean something without a selection are offered and the rest are
 * left out until there is something for them to act on.
 */
@Composable
private fun SelectPopover(state: PaintState) {
    PaintPopoverCard(pointerFromStart = 22.dp, modifier = Modifier.widthIn(max = 340.dp)) {
        PaintSectionLabel("This layer")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                PaintWideButton("Clear") {
                    state.clearLayer()
                    state.popover = PaintPopover.NONE
                }
            }
            Box(Modifier.weight(1f)) {
                PaintWideButton("Fill") {
                    state.fillLayer()
                    state.popover = PaintPopover.NONE
                }
            }
        }

        PaintSectionLabel("Colour range")
        Text(
            "How close a colour has to be to count as the same one. Used by the " +
                "bucket, the magic eraser and the cutout.",
            style = MaterialTheme.typography.labelSmall,
            color = LocalSkinPalette.current.chromeTextMuted,
        )
        ValueSlider(
            value = state.tolerance.toFloat(),
            range = 0f..200f,
            step = 1f,
            label = "${state.tolerance}",
            onChange = { state.tolerance = it.toInt() },
        )
        PaintToggleRow("Only the touching area", state.contiguous) { state.contiguous = it }

        PaintSectionLabel("Edge softness")
        ValueSlider(
            value = state.featherEdges.toFloat(),
            range = 0f..8f,
            step = 1f,
            label = "${state.featherEdges}",
            onChange = { state.featherEdges = it.toInt() },
        )
    }
}

@Composable
private fun StrokePopover(state: PaintState) {
    PaintPopoverCard(pointerFromStart = 22.dp, modifier = Modifier.widthIn(max = 360.dp)) {
        PaintSectionLabel("Stabilizer")
        Text(
            if (state.stabilizerStrength <= 0.01f) "Off"
            else "${(state.stabilizerStrength * 100).toInt()}% — the line follows your finger from further behind",
            style = MaterialTheme.typography.labelSmall,
            color = LocalSkinPalette.current.chromeTextMuted,
        )
        ValueSlider(
            value = state.stabilizerStrength * 100f,
            range = 0f..95f,
            step = 5f,
            label = "${(state.stabilizerStrength * 100).toInt()}",
            onChange = { state.stabilizerStrength = it / 100f },
        )

        PaintToggleRow("Taper the ends", state.forceFade) { state.forceFade = it }
        if (state.forceFade) {
            PaintSectionLabel("Start")
            ValueSlider(
                value = state.fadeIn * 100f,
                range = 0f..90f,
                step = 5f,
                label = "${(state.fadeIn * 100).toInt()}%",
                onChange = { state.fadeIn = it / 100f },
            )
            PaintSectionLabel("End")
            ValueSlider(
                value = state.fadeOut * 100f,
                range = 0f..90f,
                step = 5f,
                label = "${(state.fadeOut * 100).toInt()}%",
                onChange = { state.fadeOut = it / 100f },
            )
        }
    }
}

@Composable
private fun RulerPopover(state: PaintState) {
    PaintPopoverCard(pointerFromStart = 22.dp, modifier = Modifier.widthIn(max = 340.dp)) {
        PaintSectionLabel("Symmetry")
        PaintSegmented(
            options = listOf("Off", "Vertical", "Horizontal", "Both"),
            selectedIndex = when (state.symmetry) {
                SymmetryMode.OFF -> 0
                SymmetryMode.VERTICAL -> 1
                SymmetryMode.HORIZONTAL -> 2
                SymmetryMode.QUAD -> 3
                SymmetryMode.RADIAL -> 0
            },
            onSelect = {
                state.symmetry = listOf(
                    SymmetryMode.OFF, SymmetryMode.VERTICAL,
                    SymmetryMode.HORIZONTAL, SymmetryMode.QUAD,
                )[it]
            },
        )
        PaintWideButton(
            if (state.symmetry == SymmetryMode.RADIAL) "Radial · ${state.radialSlices} slices" else "Radial",
        ) {
            state.symmetry = if (state.symmetry == SymmetryMode.RADIAL) SymmetryMode.OFF else SymmetryMode.RADIAL
        }
        if (state.symmetry == SymmetryMode.RADIAL) {
            ValueSlider(
                value = state.radialSlices.toFloat(),
                range = 2f..24f,
                step = 1f,
                label = "${state.radialSlices}",
                onChange = { state.radialSlices = it.toInt() },
            )
        }
        Text(
            "Every mirrored copy shares one stroke, so the crossings are painted " +
                "once and no seam appears down the axis.",
            style = MaterialTheme.typography.labelSmall,
            color = LocalSkinPalette.current.chromeTextMuted,
        )
    }
}

/**
 * Material: bring an image in, and the two operations that act on a whole
 * imported picture.
 *
 * There is no thirty-thousand-item stock library here and pretending otherwise
 * would be dishonest. What this button is for in practice - getting a photo or
 * a scan onto a layer and then getting its background off - is what it does.
 */
@Composable
private fun MaterialsPopover(state: PaintState, onImportImage: (() -> Unit)?) {
    val palette = LocalSkinPalette.current
    val scope = rememberCoroutineScope()
    PaintPopoverCard(pointerFromStart = 22.dp, modifier = Modifier.widthIn(max = 360.dp)) {
        PaintSectionLabel("From this device")
        Text(
            "Import a photo or a scan onto the current layer, then cut it out.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
        )
        // Straight to the picker. This used to open the Tools sheet instead,
        // which is a button that appears to do the wrong thing.
        PaintWideButton("Choose an image", enabled = onImportImage != null) {
            state.popover = PaintPopover.NONE
            onImportImage?.invoke()
        }

        PaintSectionLabel("Remove a background")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(palette.chromePanelAlt)
                .clickable {
                    state.popover = PaintPopover.NONE
                    state.sheet = PaintSheet.CUTOUT
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(26.dp)) { magicEraser(palette.accent) }
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    "Auto cutout",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.chromeText,
                )
                Text(
                    "Finds the subject and mattes the edge",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(palette.chromePanelAlt)
                .clickable {
                    state.popover = PaintPopover.NONE
                    scope.launch { state.liftLineArt() }
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(26.dp)) { checker(palette.accent) }
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    "Lift line art",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.chromeText,
                )
                Text(
                    "Turns the paper of a scan transparent, keeps the pencil's soft edges",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                )
            }
        }
    }
}
