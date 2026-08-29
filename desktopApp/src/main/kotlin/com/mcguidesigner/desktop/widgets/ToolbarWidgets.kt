@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mcguidesigner.desktop.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mcguidesigner.styles.theme.LocalSkinPalette

/**
 * Compact toolbar controls.
 *
 * Deliberately hand-rolled rather than Material buttons: the desktop toolbar
 * has to fit a lot of controls into 46dp, and Material's minimum touch targets
 * would waste most of that height in a mouse-driven app.
 */
@Composable
fun ToolbarButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    hint: String? = null,
    onClick: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    WithTooltip(hint) {
        Box(
            modifier
                .padding(horizontal = 2.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(palette.chromePanelAlt.copy(alpha = if (enabled) 1f else 0.4f))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) palette.chromeText else palette.chromeTextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun IconToggle(
    label: String,
    hint: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    WithTooltip(hint) {
        Box(
            modifier
                .padding(horizontal = 2.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) palette.accentMuted else palette.chromePanelAlt)
                .border(
                    width = 1.dp,
                    color = if (selected) palette.accent else Color.Transparent,
                    shape = RoundedCornerShape(4.dp),
                )
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    !enabled -> palette.chromeTextMuted.copy(alpha = 0.45f)
                    selected -> palette.chromeText
                    else -> palette.chromeTextMuted
                },
            )
        }
    }
}

@Composable
fun ToolbarSeparator() {
    val palette = LocalSkinPalette.current
    Spacer(
        Modifier
            .padding(horizontal = 6.dp)
            .width(1.dp)
            .height(22.dp)
            .background(palette.chromeBorder),
    )
}

/** Wraps [content] in a Material tooltip when [hint] is present. */
@Composable
fun WithTooltip(hint: String?, content: @Composable () -> Unit) {
    if (hint == null) {
        content()
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(hint) } },
        state = rememberTooltipState(),
    ) {
        content()
    }
}
