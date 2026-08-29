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
import com.mcguidesigner.core.paint.MarqueeShape
import com.mcguidesigner.core.paint.RulerKind
import com.mcguidesigner.core.paint.SelectMode
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
 * The selection panel.
 *
 * A selection is the answer to "do this, but only here", and the reason it is
 * worth the machinery is that the alternative - being careful with the brush -
 * is the thing that makes editing a picture slow. So everything is confined to
 * it: the brush, the eraser, the bucket, blur, smudge, fill, clear and the
 * cutout. A selection that only some tools respect is worse than none, because
 * it teaches you not to trust it.
 *
 * The three ways to make one are tools; what is here is what you do to one once
 * you have it, and the two settings - how close a colour counts as the same,
 * and how soft the edge is - that the wand and the bucket share.
 */
@Composable
private fun SelectPopover(state: PaintState) {
    val palette = LocalSkinPalette.current
    val scope = rememberCoroutineScope()
    PaintPopoverCard(pointerFromStart = 22.dp, modifier = Modifier.widthIn(max = 360.dp)) {
        PaintSectionLabel("Select with")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                PaintTool.MARQUEE to "Box",
                PaintTool.LASSO to "Lasso",
                PaintTool.MAGIC_WAND to "Wand",
            ).forEach { (tool, label) ->
                Box(Modifier.weight(1f)) {
                    PaintWideButton(label, selected = state.tool == tool) {
                        state.previousTool = state.tool
                        state.tool = tool
                        state.popover = PaintPopover.NONE
                    }
                }
            }
        }
        if (state.tool == PaintTool.MARQUEE) {
            PaintSegmented(
                options = MarqueeShape.entries.map { it.label },
                selectedIndex = MarqueeShape.entries.indexOf(state.marqueeShape),
                onSelect = { state.marqueeShape = MarqueeShape.entries[it] },
            )
        }

        // How the next selection meets the one already there. A modifier key
        // would be the desktop answer and there is no modifier key on a phone,
        // so it is a setting that both can reach.
        PaintSectionLabel("Next selection")
        PaintSegmented(
            options = SelectMode.entries.map { it.label },
            selectedIndex = SelectMode.entries.indexOf(state.selectMode),
            onSelect = { state.selectMode = SelectMode.entries[it] },
        )

        PaintSectionLabel("The selection")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                PaintWideButton("All") { scope.launch { state.selectAll() } }
            }
            Box(Modifier.weight(1f)) {
                PaintWideButton("None", enabled = state.hasSelection) { state.deselect() }
            }
            Box(Modifier.weight(1f)) {
                PaintWideButton("Invert") { scope.launch { state.invertSelection() } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                PaintWideButton("Expand", enabled = state.hasSelection) {
                    scope.launch { state.growSelection(state.selectionStep) }
                }
            }
            Box(Modifier.weight(1f)) {
                PaintWideButton("Contract", enabled = state.hasSelection) {
                    scope.launch { state.growSelection(-state.selectionStep) }
                }
            }
            Box(Modifier.weight(1f)) {
                PaintWideButton("Soften", enabled = state.hasSelection) {
                    scope.launch { state.featherSelection(state.selectionStep) }
                }
            }
        }
        ValueSlider(
            value = state.selectionStep.toFloat(),
            range = 1f..32f,
            step = 1f,
            label = "${state.selectionStep} px",
            onChange = { state.selectionStep = it.toInt() },
        )
        PaintWideButton("Everything on this layer") { scope.launch { state.selectOpaque() } }

        PaintSectionLabel(if (state.hasSelection) "The selection" else "This layer")
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
                "magic wand, the bucket, the magic eraser and the cutout.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
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

/**
 * The ruler.
 *
 * A ruler is not a tool you draw with - it is a thing you put down and then
 * draw *against*, and everything drawn while it is there comes out straight, or
 * round, or converging on a vanishing point, without any of the care that would
 * otherwise take. On a touchscreen that is the difference between line art
 * being possible and not.
 *
 * Nine of them, and they are the nine that do different jobs rather than nine
 * variations on one. A straight edge for a single line; parallels for hatching
 * and for anything with a grain; a cross for boxes; circles and ellipses for
 * anything turned; a radial for spokes and starbursts; and one, two and three
 * point perspective, which is the whole reason anybody learns to draw a room.
 *
 * Symmetry is here too, because it is the other thing that constrains a stroke,
 * but it is a mirror rather than a guide and is kept visibly separate.
 */
@Composable
private fun RulerPopover(state: PaintState) {
    val palette = LocalSkinPalette.current
    PaintPopoverCard(pointerFromStart = 22.dp, modifier = Modifier.widthIn(max = 360.dp)) {
        PaintSectionLabel("Ruler")
        // Three rows of three rather than a long list: nine names in a column
        // is a menu, and a menu is the wrong shape for a thing you switch
        // between while drawing.
        RulerKind.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { kind ->
                    Box(Modifier.weight(1f)) {
                        PaintWideButton(kind.label, selected = state.ruler.kind == kind) {
                            state.setRulerKind(kind)
                            if (kind != RulerKind.OFF && state.tool == PaintTool.PAN) {
                                state.tool = PaintTool.BRUSH
                            }
                        }
                    }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) {} }
            }
        }

        Text(
            state.ruler.kind.hint,
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
        )

        if (state.ruler.isOn) {
            if (state.ruler.kind.usesAngle) {
                PaintSectionLabel("Angle")
                ValueSlider(
                    value = state.ruler.angle,
                    range = -180f..180f,
                    step = 1f,
                    label = "${state.ruler.angle.toInt()}°",
                    onChange = { state.setRulerAngle(it) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0f, 30f, 45f, 90f).forEach { degrees ->
                        Box(Modifier.weight(1f)) {
                            PaintWideButton("${degrees.toInt()}°") { state.setRulerAngle(degrees) }
                        }
                    }
                }
            }

            if (state.ruler.kind == RulerKind.RADIAL) {
                PaintSectionLabel("Spokes")
                ValueSlider(
                    value = state.ruler.slices.toFloat(),
                    range = 2f..48f,
                    step = 1f,
                    label = "${state.ruler.slices}",
                    onChange = { state.setRulerSlices(it.toInt()) },
                )
            }

            if (state.ruler.kind == RulerKind.ELLIPSE) {
                PaintSectionLabel("Squash")
                ValueSlider(
                    value = state.ruler.flatten * 100f,
                    range = 5f..100f,
                    step = 1f,
                    label = "${(state.ruler.flatten * 100).toInt()}%",
                    onChange = { state.setRulerFlatten(it / 100f) },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    PaintWideButton("Move it", selected = state.tool == PaintTool.RULER) {
                        state.previousTool = state.tool
                        state.tool = PaintTool.RULER
                        state.popover = PaintPopover.NONE
                    }
                }
                Box(Modifier.weight(1f)) {
                    PaintWideButton("Recentre") { state.centreRuler() }
                }
            }
        }

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
            selected = state.symmetry == SymmetryMode.RADIAL,
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
            color = palette.chromeTextMuted,
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
