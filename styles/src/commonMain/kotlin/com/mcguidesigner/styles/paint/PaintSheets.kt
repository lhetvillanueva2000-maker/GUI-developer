package com.mcguidesigner.styles.paint

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcguidesigner.core.paint.BlendMode
import com.mcguidesigner.core.paint.BrushShape
import com.mcguidesigner.core.paint.Pixels
import com.mcguidesigner.styles.paint.PaintIcons.alphaLock
import com.mcguidesigner.styles.paint.PaintIcons.bin
import com.mcguidesigner.styles.paint.PaintIcons.blur
import com.mcguidesigner.styles.paint.PaintIcons.brush
import com.mcguidesigner.styles.paint.PaintIcons.bucket
import com.mcguidesigner.styles.paint.PaintIcons.clip as clipIcon
import com.mcguidesigner.styles.paint.PaintIcons.dropper
import com.mcguidesigner.styles.paint.PaintIcons.duplicate
import com.mcguidesigner.styles.paint.PaintIcons.eraser
import com.mcguidesigner.styles.paint.PaintIcons.exportImage
import com.mcguidesigner.styles.paint.PaintIcons.eye
import com.mcguidesigner.styles.paint.PaintIcons.importImage
import com.mcguidesigner.styles.paint.PaintIcons.magicEraser
import com.mcguidesigner.styles.paint.PaintIcons.mergeDown
import com.mcguidesigner.styles.paint.PaintIcons.pan
import com.mcguidesigner.styles.paint.PaintIcons.plus
import com.mcguidesigner.styles.paint.PaintIcons.shapes
import com.mcguidesigner.styles.paint.PaintIcons.smudge
import com.mcguidesigner.styles.theme.LocalSkinPalette
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// -- Layers ---------------------------------------------------------------

/**
 * The layer panel.
 *
 * Top of the list is top of the stack, which is the opposite of how the
 * document stores them and the same as how everybody thinks about them. The
 * reversal happens here, once, rather than in every reader.
 */
@Composable
fun LayerSheet(state: PaintState) {
    val palette = LocalSkinPalette.current

    @Suppress("UNUSED_EXPRESSION")
    state.layerRevision

    @Suppress("UNUSED_EXPRESSION")
    state.revision

    val document = state.document
    val active = document.active

    PaintSheetCard {
        PaintSheetTitle("Layers", trailing = "Done") { state.sheet = PaintSheet.NONE }

        Column(
            Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            document.layers.indices.reversed().forEach { index ->
                val layer = document.layers[index]
                val selected = index == document.activeIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) palette.accent.copy(alpha = 0.16f) else palette.chromePanelAlt)
                        .then(
                            if (selected) Modifier.border(1.5.dp, palette.accent, RoundedCornerShape(10.dp))
                            else Modifier,
                        )
                        .clickable { state.selectLayer(index) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LayerThumbnail(state, index)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            layer.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.chromeText,
                            maxLines = 1,
                        )
                        Text(
                            "${(layer.opacity * 100 / 255)}%  ·  ${layer.blendMode.label}" +
                                if (layer.clippedToBelow) "  ·  clipped" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.chromeTextMuted,
                            maxLines = 1,
                        )
                    }
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { state.toggleVisible(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
                            eye(
                                if (layer.visible) palette.chromeText else palette.chromeTextMuted,
                                layer.visible,
                            )
                        }
                    }
                }
            }
        }

        // Actions.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RoundIconButton(selected = false, onClick = { state.addLayer() }) { plus(it) }
            RoundIconButton(selected = false, onClick = { state.duplicateLayer() }) { duplicate(it) }
            RoundIconButton(
                selected = false,
                enabled = document.activeIndex > 0,
                onClick = { state.mergeDown() },
            ) { mergeDown(it) }
            RoundIconButton(
                selected = active?.alphaLocked == true,
                onClick = { state.toggleAlphaLock() },
            ) { alphaLock(it) }
            RoundIconButton(
                selected = active?.clippedToBelow == true,
                enabled = document.activeIndex > 0,
                onClick = { state.toggleClipping() },
            ) { clipIcon(it) }
            Spacer(Modifier.weight(1f))
            RoundIconButton(selected = false, onClick = { state.deleteLayer() }) { bin(it) }
        }

        if (active != null) {
            PaintSectionLabel("Blend mode")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BlendMode.entries.forEach { mode ->
                    val selected = active.blendMode == mode
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) palette.accent else palette.chromePanelAlt)
                            .clickable { state.setLayerBlend(mode) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) palette.ctaText else palette.chromeText,
                            maxLines = 1,
                        )
                    }
                }
            }

            PaintSectionLabel("Layer opacity")
            ValueSlider(
                value = active.opacity * 100f / 255f,
                range = 0f..100f,
                step = 1f,
                label = "${active.opacity * 100 / 255}",
                onChange = { state.setLayerOpacity((it * 255f / 100f).roundToInt()) },
            )
        }
    }
}

/**
 * A layer's contents in miniature, over a checkerboard.
 *
 * Sampled rather than scaled properly: a thumbnail is 40 pixels across and
 * nobody is judging its quality, whereas a box-filtered reduction of every
 * layer on every recomposition of the panel is real work on a phone.
 */
@Composable
private fun LayerThumbnail(state: PaintState, index: Int) {
    val palette = LocalSkinPalette.current
    val layer = state.document.layers.getOrNull(index) ?: return
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, palette.chromeBorder, RoundedCornerShape(6.dp)),
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(40.dp)) {
            val cell = size.width / 6f
            for (row in 0 until 6) {
                for (column in 0 until 6) {
                    drawRect(
                        color = if ((row + column) % 2 == 0) Color(0xFFFFFFFF) else Color(0xFFD8D8D8),
                        topLeft = Offset(column * cell, row * cell),
                        size = androidx.compose.ui.geometry.Size(cell, cell),
                    )
                }
            }
            val steps = 16
            val stepX = size.width / steps
            val stepY = size.height / steps
            for (row in 0 until steps) {
                val sy = (row * layer.height / steps).coerceIn(0, layer.height - 1)
                for (column in 0 until steps) {
                    val sx = (column * layer.width / steps).coerceIn(0, layer.width - 1)
                    val p = layer.pixels[sy * layer.width + sx]
                    if (Pixels.alpha(p) == 0) continue
                    drawRect(
                        color = Color(p),
                        topLeft = Offset(column * stepX, row * stepY),
                        size = androidx.compose.ui.geometry.Size(stepX + 0.5f, stepY + 0.5f),
                    )
                }
            }
        }
    }
}

// -- Colour ---------------------------------------------------------------

/** Which way of choosing a colour is showing. */
private enum class ColourTab { PALETTE, WHEEL, RGB }

/**
 * The colour panel: a wheel, sliders, and a palette, as three tabs.
 *
 * Three because they are genuinely different jobs. The wheel is for finding a
 * colour you cannot name, the sliders are for matching one you can, and the
 * palette is for coming back to one you already used. An app with only the
 * wheel makes the second and third jobs tedious, which is why every painting
 * app that started with only a wheel has since grown the other two.
 */
@Composable
fun ColourSheet(state: PaintState) {
    val palette = LocalSkinPalette.current
    var tab by remember { mutableStateOf(ColourTab.WHEEL) }

    PaintSheetCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Color",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.chromeText,
                modifier = Modifier.weight(1f),
            )
            ColourChip(state.colour, size = 32.dp)
            Spacer(Modifier.width(6.dp))
            ColourChip(state.secondaryColour, size = 32.dp) { state.swapColours() }
        }

        when (tab) {
            ColourTab.WHEEL -> ColourWheel(state)
            ColourTab.RGB -> RgbSliders(state)
            ColourTab.PALETTE -> PaletteGrid(state)
        }

        Text(
            hexOf(state.colour),
            style = MaterialTheme.typography.labelMedium,
            color = palette.chromeTextMuted,
        )

        PaintSegmented(
            options = listOf("Palette", "Wheel", "RGB"),
            selectedIndex = tab.ordinal,
            onSelect = { tab = ColourTab.entries[it] },
        )
    }
}

/**
 * A hue ring around a saturation-brightness square.
 *
 * The ring-and-square arrangement rather than a single wheel because hue is the
 * choice a person makes first and independently, and putting it on its own
 * control means adjusting brightness never nudges it. That is the reason this
 * layout has outlasted every alternative on touchscreens.
 */
@Composable
private fun ColourWheel(state: PaintState) {
    val hsb = remember(state.colour) { rgbToHsb(state.colour) }
    var hue by remember(state.colour) { mutableStateOf(hsb[0]) }
    var saturation by remember(state.colour) { mutableStateOf(hsb[1]) }
    var brightness by remember(state.colour) { mutableStateOf(hsb[2]) }

    fun commit() {
        state.colour = hsbToRgb(hue, saturation, brightness)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            // One handler, for the reason ValueSlider has one: a tap detector
            // and a drag detector on the same node race for the touch-down and
            // a quick tap on the wheel gets lost between them.
            .pointerInput(Unit) {
                fun apply(offset: Offset) {
                    handleWheel(offset, size.width.toFloat(), size.height.toFloat()) { h, s, b ->
                        h?.let { hue = it }
                        s?.let { saturation = it }
                        b?.let { brightness = it }
                        commit()
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    apply(down.position)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val active = event.changes.firstOrNull { it.pressed } ?: break
                        apply(active.position)
                        active.consume()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(220.dp)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val outer = size.minDimension / 2f
            val inner = outer * 0.74f

            // The hue ring, as a swept set of thin arcs.
            val steps = 180
            for (i in 0 until steps) {
                val a0 = i * 360f / steps
                drawArc(
                    color = Color(hsbToRgb(a0, 1f, 1f)),
                    startAngle = a0 - 90f,
                    sweepAngle = 360f / steps + 1f,
                    useCenter = false,
                    topLeft = Offset(centre.x - (outer + inner) / 2f, centre.y - (outer + inner) / 2f),
                    size = androidx.compose.ui.geometry.Size(outer + inner, outer + inner),
                    style = Stroke(width = outer - inner),
                )
            }

            // The saturation/brightness square, inscribed in the ring.
            val half = inner * 0.70f
            val squareTopLeft = Offset(centre.x - half, centre.y - half)
            val squareSize = androidx.compose.ui.geometry.Size(half * 2f, half * 2f)
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color.White, Color(hsbToRgb(hue, 1f, 1f))),
                    startX = squareTopLeft.x,
                    endX = squareTopLeft.x + squareSize.width,
                ),
                topLeft = squareTopLeft,
                size = squareSize,
            )
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black),
                    startY = squareTopLeft.y,
                    endY = squareTopLeft.y + squareSize.height,
                ),
                topLeft = squareTopLeft,
                size = squareSize,
            )

            // The two markers.
            val hueAngle = (hue - 90f) * kotlin.math.PI.toFloat() / 180f
            val hueRadius = (outer + inner) / 2f
            drawCircle(
                Color.White,
                radius = (outer - inner) / 2f,
                center = Offset(centre.x + cos(hueAngle) * hueRadius, centre.y + sin(hueAngle) * hueRadius),
                style = Stroke(width = 2.5f),
            )
            drawCircle(
                Color.White,
                radius = 7f,
                center = Offset(
                    squareTopLeft.x + squareSize.width * saturation,
                    squareTopLeft.y + squareSize.height * (1f - brightness),
                ),
                style = Stroke(width = 2.5f),
            )
        }
    }
}

/** Maps a touch to either the hue ring or the square, whichever it landed on. */
private inline fun handleWheel(
    offset: Offset,
    width: Float,
    height: Float,
    apply: (hue: Float?, saturation: Float?, brightness: Float?) -> Unit,
) {
    val centre = Offset(width / 2f, height / 2f)
    val outer = minOf(width, height) / 2f
    val inner = outer * 0.74f
    val dx = offset.x - centre.x
    val dy = offset.y - centre.y
    val distance = sqrt(dx * dx + dy * dy)

    if (distance in (inner * 0.9f)..(outer * 1.1f)) {
        var degrees = atan2(dy, dx) * 180f / kotlin.math.PI.toFloat() + 90f
        while (degrees < 0f) degrees += 360f
        apply(degrees % 360f, null, null)
        return
    }
    val half = inner * 0.70f
    val sx = ((offset.x - (centre.x - half)) / (half * 2f)).coerceIn(0f, 1f)
    val sy = ((offset.y - (centre.y - half)) / (half * 2f)).coerceIn(0f, 1f)
    apply(null, sx, 1f - sy)
}

@Composable
private fun RgbSliders(state: PaintState) {
    val colour = state.colour
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ChannelSlider("R", Pixels.red(colour), Color(0xFFE53935)) {
            state.colour = Pixels.argb(255, it, Pixels.green(colour), Pixels.blue(colour))
        }
        ChannelSlider("G", Pixels.green(colour), Color(0xFF43A047)) {
            state.colour = Pixels.argb(255, Pixels.red(colour), it, Pixels.blue(colour))
        }
        ChannelSlider("B", Pixels.blue(colour), Color(0xFF1E88E5)) {
            state.colour = Pixels.argb(255, Pixels.red(colour), Pixels.green(colour), it)
        }
    }
}

@Composable
private fun ChannelSlider(label: String, value: Int, tint: Color, onChange: (Int) -> Unit) {
    ValueSlider(
        value = value.toFloat(),
        range = 0f..255f,
        step = 1f,
        label = "$label $value",
        onChange = { onChange(it.roundToInt().coerceIn(0, 255)) },
        trackBrush = Brush.horizontalGradient(listOf(Color.Black, tint)),
    )
}

/**
 * A fixed palette plus the colours actually used recently.
 *
 * The recents are the half people use. A fixed palette is a starting point once;
 * the colour used four strokes ago is wanted constantly, and hunting for it on
 * a wheel every time is how a drawing ends up with six slightly different
 * versions of the same colour in it.
 */
@Composable
private fun PaletteGrid(state: PaintState) {
    val palette = LocalSkinPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.recentColours.isNotEmpty()) {
            PaintSectionLabel("Recent")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.recentColours.forEach { swatch ->
                    ColourChip(swatch, selected = swatch == state.colour, size = 32.dp) {
                        state.colour = swatch
                    }
                }
            }
        }

        PaintSectionLabel("Palette")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PALETTE.chunked(9).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { swatch ->
                        ColourChip(swatch, selected = swatch == state.colour, size = 30.dp) {
                            state.colour = swatch
                        }
                    }
                }
            }
        }
    }
}

/**
 * A palette built as a grid rather than a list of favourite colours.
 *
 * Nine hues across, five steps of lightness down, plus a greyscale row. A
 * hand-picked set of "nice" colours looks better in a screenshot and is worse
 * to work with, because the one thing a painter needs constantly is a *darker
 * version of what they are already using*, and that is a move down a column
 * rather than a hunt through a list.
 */
private val PALETTE: List<Int> = buildList {
    val hues = listOf(0f, 25f, 45f, 90f, 160f, 195f, 220f, 265f, 315f)
    listOf(1f to 0.35f, 1f to 0.6f, 0.95f to 0.85f, 0.6f to 1f, 0.3f to 1f).forEach { (s, b) ->
        hues.forEach { hue -> add(hsbToRgb(hue, s, b)) }
    }
    listOf(0f, 0.15f, 0.3f, 0.45f, 0.6f, 0.72f, 0.84f, 0.93f, 1f).forEach { level ->
        val v = (level * 255).roundToInt()
        add(Pixels.argb(255, v, v, v))
    }
}

// -- Brush ----------------------------------------------------------------

/** Picking a nib, and the two numbers that go with it. */
@Composable
fun BrushSheet(state: PaintState) {
    val palette = LocalSkinPalette.current
    val erasing = state.tool == PaintTool.ERASER

    PaintSheetCard {
        PaintSheetTitle(if (erasing) "Eraser" else "Brush", trailing = "Done") { state.sheet = PaintSheet.NONE }

        Column(
            Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BrushShape.entries.forEach { shape ->
                val selected = shape == state.activeShape
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) palette.accent.copy(alpha = 0.16f) else palette.chromePanelAlt)
                        .clickable {
                            if (erasing) state.eraserShape = shape else state.brushShape = shape
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrushPreview(shape, if (selected) palette.accent else palette.chromeText)
                    Text(
                        shape.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.chromeText,
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                    )
                    if (selected) {
                        Text(
                            formatSize(state.activeSize),
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.accent,
                        )
                    }
                }
            }
        }

        PaintSectionLabel("Thickness")
        ValueSlider(
            value = state.activeSize,
            range = 1f..400f,
            step = 1f,
            label = formatSize(state.activeSize),
            onChange = { state.setActiveSize(it) },
            exponential = true,
        )
        PaintSectionLabel("Opacity")
        ValueSlider(
            value = state.activeOpacity * 100f,
            range = 0f..100f,
            step = 1f,
            label = "${(state.activeOpacity * 100).toInt()}%",
            onChange = { state.setActiveOpacity(it / 100f) },
        )
    }
}

/** A stroke of the nib, drawn with the real falloff curve. */
@Composable
private fun BrushPreview(shape: BrushShape, tint: Color) {
    androidx.compose.foundation.Canvas(Modifier.size(width = 74.dp, height = 26.dp)) {
        val steps = 34
        for (i in 0 until steps) {
            val t = i / (steps - 1f)
            val x = 6f + t * (size.width - 12f)
            val y = size.height / 2f + sin(t * 6f) * size.height * 0.22f
            val radius = size.height * 0.30f
            // The same smoothstep the stamp uses, so the preview is honest
            // about how soft the nib actually is.
            val rings = 5
            for (r in rings downTo 1) {
                val f = r / rings.toFloat()
                val inner = shape.hardness.coerceIn(0f, 1f)
                val alpha = if (f <= inner) 1f else {
                    val u = ((f - inner) / (1f - inner).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
                    1f - (u * u * (3f - 2f * u))
                }
                drawCircle(tint.copy(alpha = alpha * 0.30f), radius = radius * f, center = Offset(x, y))
            }
        }
    }
}

// -- Tools ----------------------------------------------------------------

/** The tool wheel, plus import and export. */
@Composable
fun ToolSheet(
    state: PaintState,
    onImportImage: (() -> Unit)?,
    onExport: ((ByteArray, String) -> Unit)?,
) {
    val palette = LocalSkinPalette.current

    PaintSheetCard {
        PaintSheetTitle("Tools", trailing = "Done") { state.sheet = PaintSheet.NONE }

        val tools = listOf(
            PaintTool.BRUSH, PaintTool.ERASER, PaintTool.BUCKET,
            PaintTool.EYEDROPPER, PaintTool.MAGIC_ERASER, PaintTool.SHAPE,
            PaintTool.SMUDGE, PaintTool.BLUR, PaintTool.PAN,
        )
        tools.chunked(4).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { tool ->
                    val selected = tool == state.tool
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) palette.accent.copy(alpha = 0.16f) else palette.chromePanelAlt)
                            .clickable {
                                state.previousTool = state.tool
                                state.tool = tool
                                if (tool == PaintTool.MAGIC_ERASER || tool == PaintTool.BUCKET) {
                                    state.sheet = PaintSheet.NONE
                                }
                            }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
                            val tint = if (selected) palette.accent else palette.chromeText
                            when (tool) {
                                PaintTool.BRUSH -> brush(tint)
                                PaintTool.ERASER -> eraser(tint)
                                PaintTool.BUCKET -> bucket(tint)
                                PaintTool.EYEDROPPER -> dropper(tint)
                                PaintTool.MAGIC_ERASER -> magicEraser(tint)
                                PaintTool.SHAPE -> shapes(tint)
                                PaintTool.SMUDGE -> smudge(tint)
                                PaintTool.BLUR -> blur(tint)
                                PaintTool.PAN -> pan(tint)
                            }
                        }
                        Text(
                            tool.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) palette.accent else palette.chromeTextMuted,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }

        // A line of guidance for the tools whose slider means something
        // different from "how much paint".
        when (state.tool) {
            PaintTool.SMUDGE -> ToolHint("Opacity is how far colour is dragged. Size is the smear's width.")
            PaintTool.BLUR -> ToolHint("Opacity is how much softening each pass applies. Size sets the reach.")
            PaintTool.BUCKET ->
                ToolHint("Tap the canvas. Colour range and edge softness are in the selection panel.")
            PaintTool.MAGIC_ERASER ->
                ToolHint("Scribble over what you want gone. Everything the scribble touches goes with it.")
            PaintTool.SHAPE ->
                ToolHint("Draw it roughly and let go — it becomes the shape you drew, tidied up.")
            PaintTool.EYEDROPPER -> ToolHint("Tap to take a colour, then it hands you back to the brush.")
            PaintTool.PAN -> ToolHint("Drag to move the canvas. Two fingers do this from any tool.")
            else -> Unit
        }

        PaintSectionLabel("Image")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                ToolAction("Import", enabled = onImportImage != null, icon = { importImage(it) }) {
                    state.sheet = PaintSheet.NONE
                    onImportImage?.invoke()
                }
            }
            Box(Modifier.weight(1f)) {
                ToolAction("Cut out", icon = { magicEraser(it) }) { state.sheet = PaintSheet.CUTOUT }
            }
            Box(Modifier.weight(1f)) {
                ToolAction("Export", enabled = onExport != null, icon = { exportImage(it) }) {
                    state.sheet = PaintSheet.NONE
                    onExport?.invoke(state.exportPng(), "${state.document.name}.png")
                }
            }
        }
    }
}

@Composable
private fun ToolHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = LocalSkinPalette.current.chromeTextMuted,
    )
}

@Composable
private fun ToolAction(
    label: String,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit,
    onClick: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.chromePanelAlt)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
            icon(if (enabled) palette.chromeText else palette.chromeTextMuted.copy(alpha = 0.5f))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) palette.chromeText else palette.chromeTextMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// -- Cutout ---------------------------------------------------------------

/**
 * The auto cutout panel.
 *
 * The honesty here is deliberate and load-bearing. This is not a learned model
 * and saying so where somebody will read it is what stops "AI" from becoming an
 * excuse when a hard photograph comes out badly. It also tells them what to do
 * instead, which a label reading "AI Remove Background" never does.
 */
@Composable
fun CutoutSheet(state: PaintState) {
    val palette = LocalSkinPalette.current
    val scope = rememberCoroutineScope()

    PaintSheetCard {
        PaintSheetTitle("Cut out the background", trailing = "Done") { state.sheet = PaintSheet.NONE }

        Text(
            "Finds the subject by building a colour model of the edges of the image, " +
                "cleaning up the boundary so it follows real edges, then working out a " +
                "partial transparency for every pixel along it — so hair and soft edges " +
                "come out soft instead of cut with scissors.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
        )

        PaintSectionLabel("How much to keep")
        ValueSlider(
            value = state.cutoutKeep.toFloat(),
            range = 10f..90f,
            step = 5f,
            label = "${state.cutoutKeep}",
            onChange = { state.cutoutKeep = it.toInt() },
        )

        PaintSectionLabel("Edge softness")
        ValueSlider(
            value = state.featherEdges.toFloat(),
            range = 0f..8f,
            step = 1f,
            label = "${state.featherEdges}",
            onChange = { state.featherEdges = it.toInt() },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                PaintWideButton("Remove background") {
                    scope.launch { state.autoCutout(invert = false) }
                }
            }
            Box(Modifier.weight(1f)) {
                PaintWideButton("Remove subject") {
                    scope.launch { state.autoCutout(invert = true) }
                }
            }
        }

        state.cutoutConfidence?.let { confidence ->
            val verdict = when {
                confidence >= 60 -> "Clean edge."
                confidence >= 35 -> "Reasonable. Undo and nudge \"how much to keep\" if it took too much."
                else -> "Not confident — the subject and the background are probably similar colours. " +
                    "The magic eraser or the eraser brush will do better here."
            }
            Text(
                "Confidence $confidence%. $verdict",
                style = MaterialTheme.typography.labelSmall,
                color = if (confidence >= 35) palette.chromeTextMuted else palette.accent,
            )
        }

        Text(
            "No model is downloaded and nothing was trained — this is ordinary image " +
                "processing running on your device, which is why it works with no " +
                "network and no waiting on a server.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
        )
    }
}

// -- Colour maths ---------------------------------------------------------

/** Hue 0..360, saturation and brightness 0..1, to opaque ARGB. */
fun hsbToRgb(hue: Float, saturation: Float, brightness: Float): Int {
    val h = ((hue % 360f) + 360f) % 360f / 60f
    val s = saturation.coerceIn(0f, 1f)
    val v = brightness.coerceIn(0f, 1f)
    val c = v * s
    val x = c * (1f - kotlin.math.abs(h % 2f - 1f))
    val m = v - c
    val (r, g, b) = when (h.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Pixels.argb(
        255,
        ((r + m) * 255f).roundToInt().coerceIn(0, 255),
        ((g + m) * 255f).roundToInt().coerceIn(0, 255),
        ((b + m) * 255f).roundToInt().coerceIn(0, 255),
    )
}

/** The inverse: ARGB to [hue, saturation, brightness]. */
fun rgbToHsb(colour: Int): FloatArray {
    val r = Pixels.red(colour) / 255f
    val g = Pixels.green(colour) / 255f
    val b = Pixels.blue(colour) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val hue = when {
        delta < 0.0001f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }
    return floatArrayOf(
        ((hue % 360f) + 360f) % 360f,
        if (max <= 0f) 0f else delta / max,
        max,
    )
}
