package com.mcguidesigner.styles.paint

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcguidesigner.styles.theme.LocalSkinPalette
import kotlin.math.roundToInt

/**
 * The small controls the paint screen is assembled from.
 *
 * They are here rather than in the app's shared widget set on purpose. A paint
 * app's chrome has different rules from a form's: it is dense, it sits over
 * artwork, and every control has to work with a thumb resting on it while the
 * other hand holds the phone. Reusing the settings screen's sliders and buttons
 * would drag those rules into a place they do not fit, and changing them to fit
 * would drag paint-app density into the settings screen.
 */

/** The round icon buttons across the top bar. */
@Composable
fun RoundIconButton(
    selected: Boolean,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    onClick: () -> Unit,
    icon: DrawScope.(tint: Color) -> Unit,
) {
    val palette = LocalSkinPalette.current
    val tint = when {
        !enabled -> palette.chromeTextMuted.copy(alpha = 0.4f)
        selected -> palette.accent
        else -> palette.chromeText
    }
    val fill = when {
        selected -> palette.accent.copy(alpha = 0.18f)
        else -> palette.chromePanelAlt
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .then(
                if (selected) Modifier.border(1.5.dp, palette.accent, CircleShape) else Modifier,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(size * 0.5f)) { icon(tint) }
    }
}

/**
 * The value row: a number, a minus, a track, a plus.
 *
 * The two buttons are not decoration. A slider on a phone is held with the same
 * thumb that is about to draw, and nudging a brush from 3.0 to 3.5 by dragging
 * a 300-pixel track is not possible; the buttons are how a precise value is
 * actually reached, and the track is how a rough one is.
 */
@Composable
fun ValueSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    label: String,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    /** Painted behind the track: the opacity row's checker-to-black ramp. */
    trackBrush: Brush? = null,
    /** Non-linear travel, so a size slider is usable at both 3px and 300px. */
    exponential: Boolean = false,
) {
    val palette = LocalSkinPalette.current

    fun toFraction(v: Float): Float {
        val lo = range.start
        val hi = range.endInclusive
        if (hi <= lo) return 0f
        if (!exponential) return ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
        val a = kotlin.math.ln(lo.coerceAtLeast(0.01f))
        val b = kotlin.math.ln(hi)
        return ((kotlin.math.ln(v.coerceAtLeast(0.01f)) - a) / (b - a)).coerceIn(0f, 1f)
    }

    fun fromFraction(f: Float): Float {
        val lo = range.start
        val hi = range.endInclusive
        if (!exponential) return lo + (hi - lo) * f.coerceIn(0f, 1f)
        val a = kotlin.math.ln(lo.coerceAtLeast(0.01f))
        val b = kotlin.math.ln(hi)
        return kotlin.math.exp(a + (b - a) * f.coerceIn(0f, 1f))
    }

    Row(modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = palette.chromeText,
            modifier = Modifier.width(46.dp),
        )
        StepButton("−", palette.chromeText) { onChange((value - step).coerceIn(range)) }
        Spacer(Modifier.width(8.dp))

        Box(
            Modifier
                .weight(1f)
                .height(36.dp)
                // One gesture handler, not two. Separate tap and drag detectors
                // on the same node both wait for the same touch-down and race to
                // claim it, so a tap that moves a pixel is swallowed by neither
                // and the slider reads as unresponsive. This takes the pointer
                // down, sets the value immediately, and follows it - which is
                // also how a slider should behave: land where you touched, then
                // track.
                .pointerInput(range, exponential) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onChange(fromFraction(down.position.x / size.width.toFloat()).coerceIn(range))
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val active = event.changes.firstOrNull { it.pressed } ?: break
                            onChange(fromFraction(active.position.x / size.width.toFloat()).coerceIn(range))
                            active.consume()
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(36.dp)) {
                val fraction = toFraction(value)
                val trackY = size.height / 2f
                val trackHeight = 6f

                if (trackBrush != null) {
                    drawCheckerRow(trackY, trackHeight, size.width)
                    drawRoundRect(
                        brush = trackBrush,
                        topLeft = Offset(0f, trackY - trackHeight / 2f),
                        size = Size(size.width, trackHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                    )
                } else {
                    drawRoundRect(
                        color = palette.chromeBorder,
                        topLeft = Offset(0f, trackY - trackHeight / 2f),
                        size = Size(size.width, trackHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                    )
                    drawRoundRect(
                        color = palette.accent,
                        topLeft = Offset(0f, trackY - trackHeight / 2f),
                        size = Size(size.width * fraction, trackHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                    )
                }

                val knobX = (size.width * fraction).coerceIn(11f, size.width - 11f)
                drawCircle(Color(0x40000000), radius = 12f, center = Offset(knobX, trackY + 1f))
                drawCircle(Color.White, radius = 11f, center = Offset(knobX, trackY))
                drawCircle(
                    palette.chromeBorder,
                    radius = 11f,
                    center = Offset(knobX, trackY),
                    style = Stroke(width = 1f),
                )
            }
        }

        Spacer(Modifier.width(8.dp))
        StepButton("+", palette.chromeText) { onChange((value + step).coerceIn(range)) }
    }
}

/** The circular checkerboard behind an opacity track. */
private fun DrawScope.drawCheckerRow(centreY: Float, height: Float, width: Float) {
    val cell = height / 2f
    var x = 0f
    var row = 0
    while (x < width) {
        var y = centreY - height / 2f
        var column = row
        while (y < centreY + height / 2f) {
            drawRect(
                color = if (column % 2 == 0) Color(0xFFFFFFFF) else Color(0xFFCCCCCC),
                topLeft = Offset(x, y),
                size = Size(minOf(cell, width - x), cell),
            )
            y += cell
            column++
        }
        x += cell
        row++
    }
}

@Composable
private fun StepButton(glyph: String, tint: Color, onClick: () -> Unit) {
    val palette = LocalSkinPalette.current
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(palette.chromePanelAlt)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = tint, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * A popover hanging from a top-bar button, with the little pointer.
 *
 * Positioned by the caller rather than by an anchor, because Compose's own
 * popup positioning would put it over the canvas the person is trying to see
 * the effect of the setting on. These deliberately sit under the bar and above
 * the artwork, exactly as in a paint app's own chrome.
 */
@Composable
fun PaintPopoverCard(
    pointerFromStart: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = LocalSkinPalette.current
    Column(modifier) {
        androidx.compose.foundation.Canvas(
            Modifier
                .padding(start = pointerFromStart)
                .size(width = 18.dp, height = 9.dp),
        ) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, size.height)
                lineTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, palette.chromePanel)
        }
        Column(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(palette.chromePanel)
                .border(1.dp, palette.chromeBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

/** A labelled switch row, as in the View popover. */
@Composable
fun PaintToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = LocalSkinPalette.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.chromeText,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(width = 46.dp, height = 26.dp)
                .clip(CircleShape)
                .background(if (checked) palette.accent else palette.chromePanelAlt)
                .border(1.dp, palette.chromeBorder, CircleShape),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

/** A full-width dark button, as under "Grid Settings". */
@Composable
fun PaintWideButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val palette = LocalSkinPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(if (enabled) palette.chromeBackground else palette.chromePanelAlt)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) palette.chromeText else palette.chromeTextMuted,
        )
    }
}

/** A two-or-more way segmented picker, as in "Smooth | Pixelated". */
@Composable
fun PaintSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val palette = LocalSkinPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, palette.chromeBorder, RoundedCornerShape(8.dp)),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(if (selected) palette.accent else palette.chromePanelAlt)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) palette.ctaText else palette.chromeText,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A small square swatch showing a colour over a checkerboard. */
@Composable
fun ColourChip(
    colour: Int,
    selected: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 34.dp,
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalSkinPalette.current
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) palette.accent else palette.chromeBorder,
                shape = RoundedCornerShape(6.dp),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(size)) {
            val cell = 6f
            var y = 0f
            var row = 0
            while (y < size.toPx()) {
                var x = 0f
                var column = row
                while (x < size.toPx()) {
                    drawRect(
                        color = if (column % 2 == 0) Color(0xFFFFFFFF) else Color(0xFFCCCCCC),
                        topLeft = Offset(x, y),
                        size = Size(cell, cell),
                    )
                    x += cell
                    column++
                }
                y += cell
                row++
            }
            drawRect(Color(colour))
        }
    }
}

/** The header at the top of a bottom sheet. */
@Composable
fun PaintSheetTitle(title: String, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    val palette = LocalSkinPalette.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = palette.chromeText,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelLarge,
                color = palette.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = onTrailing != null) { onTrailing?.invoke() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** Formats a brush size the way the size bubble does: one decimal place. */
fun formatSize(value: Float): String {
    val tenths = (value * 10f).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}
