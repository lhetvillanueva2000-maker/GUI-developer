package com.mcguidesigner.styles.paint

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcguidesigner.core.paint.Pixels
import com.mcguidesigner.styles.paint.PaintIcons.arrowDown
import com.mcguidesigner.styles.paint.PaintIcons.arrowLeft
import com.mcguidesigner.styles.paint.PaintIcons.brush
import com.mcguidesigner.styles.paint.PaintIcons.dot
import com.mcguidesigner.styles.paint.PaintIcons.eraser
import com.mcguidesigner.styles.paint.PaintIcons.hand
import com.mcguidesigner.styles.paint.PaintIcons.layers
import com.mcguidesigner.styles.paint.PaintIcons.marquee
import com.mcguidesigner.styles.paint.PaintIcons.picture
import com.mcguidesigner.styles.paint.PaintIcons.bucket
import com.mcguidesigner.styles.paint.PaintIcons.dropper
import com.mcguidesigner.styles.paint.PaintIcons.magicEraser
import com.mcguidesigner.styles.paint.PaintIcons.shapes
import com.mcguidesigner.styles.paint.PaintIcons.smudge
import com.mcguidesigner.styles.paint.PaintIcons.blur
import com.mcguidesigner.styles.paint.PaintIcons.lasso
import com.mcguidesigner.styles.paint.PaintIcons.wand
import com.mcguidesigner.styles.paint.PaintIcons.pan
import com.mcguidesigner.styles.paint.PaintIcons.redo
import com.mcguidesigner.styles.paint.PaintIcons.ruler
import com.mcguidesigner.styles.paint.PaintIcons.sliders
import com.mcguidesigner.styles.paint.PaintIcons.swap
import com.mcguidesigner.styles.paint.PaintIcons.undo
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.MotionLevel

/**
 * The paint editor.
 *
 * The layout is deliberately the one every touch painting app has converged on,
 * and for a reason worth stating: the canvas is the whole screen, and every
 * control is at one of the two edges a thumb can reach without moving the hand
 * holding the device. The top strip is for things you *set* - view options,
 * selection, stroke behaviour, guides, imported material - and the bottom is
 * for things you *do* while drawing: which tool, how big, how strong, what
 * colour, which layer.
 *
 * Nothing floats over the middle. The one place a person is looking is the one
 * place nothing is allowed to be.
 */
@Composable
fun PaintScreen(
    state: PaintState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onExport: ((ByteArray, String) -> Unit)? = null,
    onImportImage: (() -> Unit)? = null,
    motion: MotionLevel = MotionLevel.FULL,
) {
    val palette = LocalSkinPalette.current

    DisposableEffect(state) {
        state.attachSurface()
        onDispose { }
    }
    LaunchedEffect(state.document) { state.attachSurface() }

    // The entrance: chrome first, then the sheet grows into place behind it.
    //
    // Two curves from one clock rather than two animations, so they cannot
    // drift apart. The chrome uses the first half and is therefore already
    // settled while the canvas is still arriving, which is the order the
    // reference moves in - and the right order regardless, because the frame
    // has to exist before the thing inside it means anything.
    val entrance = remember { Animatable(if (motion.scale <= 0f) 1f else 0f) }
    LaunchedEffect(motion.scale) {
        if (motion.scale <= 0f) {
            entrance.snapTo(1f)
        } else {
            entrance.animateTo(
                1f,
                tween(durationMillis = (460 * motion.scale).toInt().coerceAtLeast(1), easing = FastOutSlowInEasing),
            )
        }
    }
    val progress = entrance.value
    val chrome = (progress * 2f).coerceIn(0f, 1f)

    Box(modifier.fillMaxSize().background(palette.chromeBackground)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.graphicsLayer { alpha = chrome; translationY = (1f - chrome) * -24f }) {
                PaintTopBar(state)
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                PaintCanvasView(state, entrance = progress, motion = motion)

                // Popovers hang from the bar and sit over the canvas edge, as
                // in the reference: they belong to the button that opened them,
                // so they are positioned from the same edge that button is on.
                if (state.popover != PaintPopover.NONE) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                indication = null,
                                interactionSource = androidx.compose.runtime.remember {
                                    androidx.compose.foundation.interaction.MutableInteractionSource()
                                },
                            ) { state.popover = PaintPopover.NONE },
                    )
                    Box(Modifier.align(Alignment.TopEnd).padding(horizontal = 6.dp)) {
                        PaintPopoverContent(state, onImportImage)
                    }
                }

                state.busy?.let { message ->
                    Box(
                        Modifier.fillMaxSize().background(Color(0x88000000)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(palette.chromePanel)
                                .padding(horizontal = 22.dp, vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.chromeText,
                            )
                            Text(
                                "This is real arithmetic over every pixel, not a wait.",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.chromeTextMuted,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }

            Box(Modifier.graphicsLayer { alpha = chrome; translationY = (1f - chrome) * 24f }) {
                Column {
                    PaintSliderRows(state)
                    PaintBottomBar(state, onBack = onBack)
                }
            }
        }

        // Sheets rise from the bottom over everything, as they do in the
        // reference screenshots, and dismiss by tapping away from them.
        if (state.sheet != PaintSheet.NONE) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable(
                        indication = null,
                        interactionSource = androidx.compose.runtime.remember {
                            androidx.compose.foundation.interaction.MutableInteractionSource()
                        },
                    ) { state.sheet = PaintSheet.NONE },
            )
            Box(Modifier.align(Alignment.BottomCenter).padding(8.dp)) {
                when (state.sheet) {
                    PaintSheet.LAYERS -> LayerSheet(state)
                    PaintSheet.COLOUR -> ColourSheet(state)
                    PaintSheet.BRUSH -> BrushSheet(state)
                    PaintSheet.TOOLS -> ToolSheet(state, onImportImage = onImportImage, onExport = onExport)
                    PaintSheet.CUTOUT -> CutoutSheet(state)
                    PaintSheet.NONE -> Unit
                }
            }
        }
    }
}

/**
 * Undo and redo on the left, five popover buttons on the right.
 *
 * The gap between them is not empty space, it is the separation between "what I
 * just did" and "how the tool behaves" - two groups that should never be one
 * row of seven identical buttons.
 */
@Composable
private fun PaintTopBar(state: PaintState) {
    val palette = LocalSkinPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.chromePanel)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RoundIconButton(selected = false, enabled = state.canUndo, onClick = { state.undoStep() }) { undo(it) }
        RoundIconButton(selected = false, enabled = state.canRedo, onClick = { state.redoStep() }) { redo(it) }

        Spacer(Modifier.weight(1f))

        fun toggle(target: PaintPopover) {
            state.popover = if (state.popover == target) PaintPopover.NONE else target
        }

        RoundIconButton(state.popover == PaintPopover.VIEW, onClick = { toggle(PaintPopover.VIEW) }) { sliders(it) }
        RoundIconButton(state.popover == PaintPopover.SELECT, onClick = { toggle(PaintPopover.SELECT) }) { marquee(it) }
        RoundIconButton(state.popover == PaintPopover.STROKE, onClick = { toggle(PaintPopover.STROKE) }) { hand(it) }
        RoundIconButton(state.popover == PaintPopover.RULER, onClick = { toggle(PaintPopover.RULER) }) { ruler(it) }
        RoundIconButton(state.popover == PaintPopover.MATERIALS, onClick = { toggle(PaintPopover.MATERIALS) }) { picture(it) }
    }
}

/** The size and opacity rows above the tool bar. */
@Composable
private fun PaintSliderRows(state: PaintState) {
    val palette = LocalSkinPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.chromePanel)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        ValueSlider(
            value = state.activeSize,
            range = 1f..400f,
            step = 1f,
            label = formatSize(state.activeSize),
            onChange = { state.setActiveSize(it) },
            // Exponential, because a linear 1-to-400 track spends its first
            // three pixels on the whole range anybody draws line art at.
            exponential = true,
        )
        ValueSlider(
            value = state.activeOpacity * 100f,
            range = 0f..100f,
            step = 1f,
            label = "${(state.activeOpacity * 100f).toInt()}",
            onChange = { state.setActiveOpacity(it / 100f) },
            trackBrush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                listOf(Color(state.colour).copy(alpha = 0f), Color(state.colour).copy(alpha = 1f)),
            ),
        )
    }
}

/**
 * The tool bar: swap, tool, size, colour, dismiss, layers, back.
 *
 * The size bubble in the middle is the one control that is both a readout and a
 * button, which is why it is drawn as a filled circle with a number in it
 * rather than as another icon - it shows the brush's actual size at a glance
 * and opens the brush picker when pressed.
 */
@Composable
private fun PaintBottomBar(state: PaintState, onBack: () -> Unit) {
    val palette = LocalSkinPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(palette.chromePanelAlt)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Swap between the brush and the eraser without opening anything. The
        // single most used control in a painting app after the brush itself.
        RoundIconButton(selected = false, size = 44.dp, onClick = {
            val held = state.tool
            state.tool = if (held == PaintTool.ERASER) {
                if (state.previousTool == PaintTool.ERASER) PaintTool.BRUSH else state.previousTool
            } else {
                PaintTool.ERASER
            }
            state.previousTool = held
        }) { swap(it) }

        // The current tool's own icon, not just brush-or-eraser. This button
        // opens the tool sheet, and a button that opens a picker should say
        // what is picked - with a dozen tools, "it is a brush unless it is the
        // eraser" is wrong most of the time.
        RoundIconButton(
            selected = state.sheet == PaintSheet.TOOLS,
            size = 44.dp,
            onClick = { state.sheet = if (state.sheet == PaintSheet.TOOLS) PaintSheet.NONE else PaintSheet.TOOLS },
        ) { tint ->
            when (state.tool) {
                PaintTool.ERASER -> eraser(tint)
                PaintTool.BUCKET -> bucket(tint)
                PaintTool.EYEDROPPER -> dropper(tint)
                PaintTool.MAGIC_ERASER -> magicEraser(tint)
                PaintTool.SHAPE -> shapes(tint)
                PaintTool.SMUDGE -> smudge(tint)
                PaintTool.BLUR -> blur(tint)
                PaintTool.MARQUEE -> marquee(tint)
                PaintTool.LASSO -> lasso(tint)
                PaintTool.MAGIC_WAND -> wand(tint)
                PaintTool.RULER -> ruler(tint)
                PaintTool.PAN -> pan(tint)
                PaintTool.BRUSH -> brush(tint)
            }
        }

        // The size bubble.
        Box(
            Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(palette.chromeBackground)
                .clickable { state.sheet = if (state.sheet == PaintSheet.BRUSH) PaintSheet.NONE else PaintSheet.BRUSH },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(46.dp)) {
                dot(palette.chromeTextMuted.copy(alpha = 0.35f), state.activeSize / 400f)
            }
            Text(
                formatSize(state.activeSize),
                color = palette.chromeText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { state.sheet = if (state.sheet == PaintSheet.COLOUR) PaintSheet.NONE else PaintSheet.COLOUR },
            contentAlignment = Alignment.Center,
        ) {
            ColourChip(colour = state.colour, size = 36.dp)
        }

        RoundIconButton(selected = false, size = 44.dp, onClick = { state.sheet = PaintSheet.NONE }) { arrowDown(it) }

        // Layers, with the count on it as in the reference.
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (state.sheet == PaintSheet.LAYERS) palette.accent.copy(alpha = 0.18f)
                    else palette.chromePanelAlt,
                )
                .clickable { state.sheet = if (state.sheet == PaintSheet.LAYERS) PaintSheet.NONE else PaintSheet.LAYERS },
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
                layers(if (state.sheet == PaintSheet.LAYERS) palette.accent else palette.chromeText)
            }
            Text(
                "${state.document.layers.size}",
                color = palette.chromeText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 8.dp),
            )
        }

        RoundIconButton(selected = false, size = 44.dp, onClick = onBack) { arrowLeft(it) }
    }
}

/** A labelled section heading inside a popover or a sheet. */
@Composable
fun PaintSectionLabel(text: String) {
    val palette = LocalSkinPalette.current
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = palette.chromeTextMuted,
        fontWeight = FontWeight.SemiBold,
    )
}

/** The rounded container every sheet sits in. */
@Composable
fun PaintSheetCard(content: @Composable () -> Unit) {
    val palette = LocalSkinPalette.current
    Column(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.chromePanel)
            .border(1.dp, palette.chromeBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

/** A colour as an 0xRRGGBB string, for the hex readouts. */
fun hexOf(colour: Int): String {
    val hex = "0123456789ABCDEF"
    val r = Pixels.red(colour)
    val g = Pixels.green(colour)
    val b = Pixels.blue(colour)
    fun pair(v: Int) = "${hex[v shr 4]}${hex[v and 0xF]}"
    return "#${pair(r)}${pair(g)}${pair(b)}"
}
