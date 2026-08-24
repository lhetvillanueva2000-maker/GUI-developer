@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mcguidesigner.styles.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.model.ColorPalette
import com.mcguidesigner.core.model.Swatch
import com.mcguidesigner.styles.theme.LocalMotion
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.spec
import kotlin.math.min

/**
 * The colours you can pick without knowing a hex code.
 *
 * Both shells show the same grid, laid out to whatever width they have: the
 * desktop inspector is a narrow column and a phone sheet is the full screen,
 * and a fixed number of swatches per row would waste one and overflow the
 * other.
 *
 * [selected] is matched on RGB only, so fading a colour with the alpha slider
 * does not make the grid forget which swatch it came from.
 */
@Composable
fun SwatchGrid(
    selected: Long,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    swatchSize: Dp = 22.dp,
    includeTransparent: Boolean = true,
) {
    val palette = LocalSkinPalette.current
    val current = ColorPalette.matching(selected)

    Column(modifier) {
        ColorPalette.groups.forEach { group ->
            Text(
                group.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.chromeTextMuted,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                group.swatches.forEach { swatch ->
                    SwatchChip(
                        swatch = swatch,
                        selected = current == swatch,
                        size = swatchSize,
                        // The alpha the user has already chosen is kept: picking
                        // a new colour is a change of hue, not a reset of the
                        // transparency they set on purpose two clicks ago.
                        onPick = { onPick((selected and (0xFFL shl 24)) or (swatch.argb and 0xFFFFFF)) },
                    )
                }
            }
        }

        if (includeTransparent) {
            Row(
                Modifier.padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SwatchChip(
                    swatch = ColorPalette.transparent,
                    selected = (selected ushr 24) == 0L,
                    size = swatchSize,
                    onPick = { onPick(0x00000000) },
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Fully transparent",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.chromeTextMuted,
                )
            }
        }
    }
}

/** One swatch, drawn over a chequerboard so transparency is visible. */
@Composable
private fun SwatchChip(swatch: Swatch, selected: Boolean, size: Dp, onPick: () -> Unit) {
    val palette = LocalSkinPalette.current
    val motion = LocalMotion.current
    val ring by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.spec(150),
        label = "swatch-ring",
    )
    val colour = Color(swatch.argb.toInt())

    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(5.dp))
            .drawBehind {
                // Behind every swatch, not only the transparent one: a colour
                // the user has faded should read as faded here too.
                drawChequerboard(palette.chromePanelAlt, palette.chromePanel)
            }
            .background(colour)
            .clickable(onClick = onPick)
            .drawBehind {
                if (ring > 0f) {
                    // Two rings, light over dark, so the selection is visible
                    // against a white swatch and against a black one.
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.55f * ring),
                        style = Stroke(width = 3f),
                        cornerRadius = CornerRadius(5.dp.toPx()),
                    )
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.9f * ring),
                        style = Stroke(width = 1.5f),
                        cornerRadius = CornerRadius(5.dp.toPx()),
                    )
                }
            },
    )
}

/** The usual "nothing here" pattern. */
private fun DrawScope.drawChequerboard(light: Color, dark: Color) {
    val cell = 5f
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = 0f
        var column = 0
        while (x < size.width) {
            drawRect(
                color = if ((row + column) % 2 == 0) light else dark,
                topLeft = Offset(x, y),
                size = Size(min(cell, size.width - x), min(cell, size.height - y)),
            )
            x += cell
            column++
        }
        y += cell
        row++
    }
}
