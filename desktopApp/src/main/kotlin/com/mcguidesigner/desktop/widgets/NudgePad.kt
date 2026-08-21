package com.mcguidesigner.desktop.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorSettings
import com.mcguidesigner.styles.theme.LocalSkinPalette

/**
 * Four arrows that move the selection, plus the step they move by.
 *
 * A design tool needs a way to move something one pixel that does not depend
 * on landing a one-pixel drag, and the keyboard is not always the answer -
 * a laptop with no arrow cluster, a tablet, or simply having a hand on the
 * mouse.  The pad and the arrow keys go through [EditorController.nudgeSelection],
 * so they always agree with each other and with the setting.
 */
@Composable
fun NudgePad(
    controller: EditorController,
    settings: EditorSettings,
    modifier: Modifier = Modifier,
    onOpenSettings: (() -> Unit)? = null,
) {
    val palette = LocalSkinPalette.current
    var large by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        color = palette.chromePanel.copy(alpha = 0.95f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, palette.chromeBorder),
    ) {
        Column(
            Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            NudgeKey("▲", "Move up  (Up arrow)") { controller.nudgeSelection(0, -1, large) }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                NudgeKey("◀", "Move left  (Left arrow)") { controller.nudgeSelection(-1, 0, large) }
                // The centre key is the step readout: the number you are about
                // to move by belongs where you are looking, not in a dialog.
                StepKey(
                    step = settings.stepFor(large),
                    large = large,
                    onToggle = { large = !large },
                    onOpenSettings = onOpenSettings,
                )
                NudgeKey("▶", "Move right  (Right arrow)") { controller.nudgeSelection(1, 0, large) }
            }
            NudgeKey("▼", "Move down  (Down arrow)") { controller.nudgeSelection(0, 1, large) }
        }
    }
}

@Composable
private fun NudgeKey(glyph: String, hint: String, onClick: () -> Unit) {
    val palette = LocalSkinPalette.current
    WithTooltip(hint) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(palette.chromePanelAlt)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyph, style = MaterialTheme.typography.labelLarge, color = palette.chromeText)
        }
    }
}

/**
 * The step readout at the centre of the pad.
 *
 * Click switches between the small and large step; right-clicking is not
 * discoverable enough for the settings, so the caller supplies a way in and
 * the tooltip says so.
 */
@Composable
private fun StepKey(
    step: Int,
    large: Boolean,
    onToggle: () -> Unit,
    onOpenSettings: (() -> Unit)?,
) {
    val palette = LocalSkinPalette.current
    val hint = buildString {
        append(if (large) "Big steps: ${step}px per press." else "Small steps: ${step}px per press.")
        append("  Click to switch.")
        if (onOpenSettings != null) append("  Change both in Editor Settings.")
    }

    WithTooltip(hint) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (large) palette.accentMuted else palette.chromePanelAlt)
                .border(
                    1.dp,
                    if (large) palette.accent else Color.Transparent,
                    RoundedCornerShape(6.dp),
                )
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = step.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = if (large) palette.chromeText else palette.chromeTextMuted,
            )
        }
    }
}
