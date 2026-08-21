package com.mcguidesigner.styles.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.model.Edition

/**
 * The edition switcher: two tabs, always both visible, at the top of the app.
 *
 * This is the most consequential control in the editor - it decides which
 * component vocabulary, which skin, which export pipeline and which validation
 * rules are in play - so it is a permanent, obvious pair of tabs rather than a
 * menu item.  Whichever tab is lit, everything below it is that edition's.
 *
 * The selected pill slides between the two rather than cutting, and each tab
 * is painted in *its own* edition's accent even while inactive, so the two are
 * distinguishable before you read the labels.
 */
@Composable
fun EditionTabs(
    selected: Edition,
    onSelect: (Edition) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    enabled: Boolean = true,
) {
    val palette = LocalSkinPalette.current
    val height: Dp = if (compact) 38.dp else 52.dp

    BoxWithConstraints(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(palette.chromePanelAlt)
            .border(1.dp, palette.chromeBorder, RoundedCornerShape(height / 2)),
    ) {
        val tabWidth = maxWidth / 2
        val indicatorOffset by animateDpAsState(
            targetValue = if (selected == Edition.JAVA) 0.dp else tabWidth,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "editionIndicator",
        )

        // The pill that travels. Drawn first so labels sit on top of it.
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .padding(3.dp)
                .clip(RoundedCornerShape(height / 2))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            palette.accentMuted.copy(alpha = 0.95f),
                            palette.accent.copy(alpha = 0.35f),
                        ),
                    ),
                ),
        )

        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            Edition.entries.forEach { edition ->
                EditionTab(
                    edition = edition,
                    active = edition == selected,
                    compact = compact,
                    enabled = enabled,
                    modifier = Modifier.width(tabWidth).fillMaxHeight(),
                    onClick = { if (enabled && edition != selected) onSelect(edition) },
                )
            }
        }
    }
}

@Composable
private fun EditionTab(
    edition: Edition,
    active: Boolean,
    compact: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    val skin = SkinRegistry.forEdition(edition)

    val labelColor by animateColorAsState(
        targetValue = if (active) palette.chromeText else palette.chromeTextMuted,
        animationSpec = tween(CHROME_TRANSITION_MILLIS / 2),
        label = "editionLabel",
    )
    // The inactive tab keeps a hint of its own edition's accent, so the two
    // sides never look like the same button twice.
    val dotColor by animateColorAsState(
        targetValue = if (active) skin.palette.accent else skin.palette.accent.copy(alpha = 0.45f),
        animationSpec = tween(CHROME_TRANSITION_MILLIS / 2),
        label = "editionDot",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.94f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "editionScale",
    )

    Box(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .width(if (compact) 8.dp else 10.dp)
                    .height(if (compact) 8.dp else 10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(dotColor),
            )
            Column {
                Text(
                    text = if (compact) edition.displayName.substringBefore(' ') else edition.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(scale),
                )
                if (!compact) {
                    Text(
                        text = skin.tagline.substringBefore(" -"),
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.chromeTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
