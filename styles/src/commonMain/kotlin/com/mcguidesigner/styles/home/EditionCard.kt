package com.mcguidesigner.styles.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcguidesigner.styles.layout.AdaptiveMetrics
import com.mcguidesigner.styles.render.EditionSkin
import com.mcguidesigner.styles.theme.SkinPalette

/**
 * One edition, rendered in its own widget language.
 *
 * This is the whole idea of the screen. The card is not a themed surface with
 * an edition name printed on it - it is a real container from that edition,
 * holding real slots and a real button, drawn entirely from that edition's own
 * [SkinPalette]. Java comes out flat, square and hard-edged; Bedrock comes out
 * soft-cornered with a 2px light-over-dark border. They are deliberately not
 * reconciled with each other, because the difference between them is the thing
 * being chosen.
 *
 * Since every value is read from the palette, this composable never names an
 * edition. Adding a third one means registering a skin, not editing this file.
 */
@Composable
fun EditionCard(
    skin: EditionSkin,
    dark: Boolean,
    metrics: AdaptiveMetrics,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Supplied by the caller so the shell behind the card can watch the same
     * hover this card reacts to. A card that owned its own source privately
     * would light up while nothing else noticed.
     */
    interaction: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val p = skin.paletteFor(dark)
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()

    val corner = p.cornerRadius.dp
    val bevel = p.borderWidth.dp

    Column(
        modifier
            .hoverable(interaction)
            .clip(RoundedCornerShape(corner))
            .background(p.surface)
            .bevel(p.bevelLight, p.bevelDark, bevel)
            .padding(metrics.gutter * 0.75f)
    ) {
        Text(
            skin.displayName,
            style = TextStyle(
                color = p.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp,
                shadow = Shadow(p.textShadow, Offset(0f, 2f), 0f),
            ),
        )

        Spacer(Modifier.height(metrics.gap))

        // Three slots: the most recognisable motif in either GUI, and an honest
        // preview of what the canvas is about to look like.
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(corner * 0.35f))
                        .background(p.slot)
                        .bevel(p.slotShadow, p.slotHighlight, bevel)
                )
            }
        }

        Spacer(Modifier.height(metrics.gap * 1.4f))

        EditionButton(
            label = skin.openLabel,
            palette = p,
            height = p.controlHeight.dp * HomeMetrics.widgetScale(metrics),
            hovered = hovered,
            pressed = pressed,
            interaction = interaction,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The button inside the card, at the edition's real controlHeight scaled to a
 * landing target. [SkinPalette.blendCta] already knows how to choose between
 * fill, hover and pressed, so the states are not reimplemented here.
 *
 * The call-to-action pair rather than the plain control one, because this is
 * the only button on the screen and it has to be legible in every edition. It
 * used to fill with `control` and write in `textOnAccent`, which works by luck
 * in both Minecraft skins - their controls are dark stone - and produced white
 * text on a white button the moment a light edition was added.
 */
@Composable
private fun EditionButton(
    label: String,
    palette: SkinPalette,
    height: Dp,
    hovered: Boolean,
    pressed: Boolean,
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = palette.blendCta(pressed = pressed, hovered = hovered, enabled = true)

    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(palette.cornerRadius.dp * 0.6f))
            .background(fill)
            .bevel(palette.bevelLight, palette.bevelDark, palette.borderWidth.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(
                color = palette.ctaText,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.6.sp,
                shadow = Shadow(palette.textShadow, Offset(2f, 2f), 0f),
            ),
        )
    }
}

/**
 * The one drawing primitive both GUIs are built out of: a light edge on the
 * top and left, a dark edge on the bottom and right. Java uses it at 1px with
 * hard corners, Bedrock at 2px with soft ones; the difference is entirely in
 * the palette, not here.
 */
private fun Modifier.bevel(light: androidx.compose.ui.graphics.Color,
                           dark: androidx.compose.ui.graphics.Color,
                           width: Dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRect(light, Offset.Zero, Size(size.width, w))
    drawRect(light, Offset.Zero, Size(w, size.height))
    drawRect(dark, Offset(0f, size.height - w), Size(size.width, w))
    drawRect(dark, Offset(size.width - w, 0f), Size(w, size.height))
}
