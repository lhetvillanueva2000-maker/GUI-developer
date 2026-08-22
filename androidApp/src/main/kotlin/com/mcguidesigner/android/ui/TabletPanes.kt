package com.mcguidesigner.android.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcguidesigner.android.AndroidAppState
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.styles.layout.AdaptiveMetrics
import com.mcguidesigner.styles.theme.ErrorRed
import com.mcguidesigner.styles.theme.LocalSkinPalette

/** Which pane the docked tablet inspector is showing. */
enum class TabletPane(val title: String) {
    PROPERTIES("Properties"),
    LAYERS("Layers"),
    ISSUES("Issues"),
}

/**
 * The inspector, docked beside the canvas instead of covering it.
 *
 * This is the one structural difference between the phone layout and the
 * tablet one, and it is the whole reason a tablet layout is worth having: on a
 * phone the properties have to be a sheet, so every nudge of a value hides the
 * thing being nudged.  Given 600dp of width there is room to keep both, and
 * watching the canvas while you drag a number is most of what designing a
 * screen actually is.
 *
 * It reuses the phone's own panels rather than reimplementing them - same
 * fields, same behaviour, same code - and only the container changes.
 */
@Composable
fun TabletInspector(
    app: AndroidAppState,
    controller: EditorController,
    state: EditorState,
    metrics: AdaptiveMetrics,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    var pane by remember { mutableStateOf(TabletPane.PROPERTIES) }

    // Selecting something is a request to look at it. Jumping to Properties
    // saves a tap that would otherwise be needed every single time.
    LaunchedEffect(state.primaryElement?.id) {
        if (state.hasSelection && pane == TabletPane.LAYERS) pane = TabletPane.PROPERTIES
    }

    Column(modifier.background(palette.chromePanel)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(palette.chromePanelAlt)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TabletPane.entries.forEach { entry ->
                val badge = when (entry) {
                    TabletPane.ISSUES -> state.validation.issues.size.takeIf { it > 0 }
                    else -> null
                }
                PaneTab(
                    title = entry.title,
                    badge = badge,
                    badgeIsError = entry == TabletPane.ISSUES && state.validation.errorCount > 0,
                    selected = pane == entry,
                    metrics = metrics,
                    modifier = Modifier.weight(1f),
                ) { pane = entry }
            }
        }

        Box(Modifier.weight(1f)) {
            Crossfade(targetState = pane, label = "tabletPane") { current ->
                when (current) {
                    TabletPane.PROPERTIES -> PropertiesSheet(controller, state)
                    TabletPane.LAYERS -> MobileLayersSection(controller, state, Modifier.fillMaxSize())
                    TabletPane.ISSUES -> IssuesSheet(controller, state)
                }
            }
        }

        // The actions that would otherwise only exist in the floating bar over
        // the canvas. On a tablet there is room to spell them out.
        if (state.hasSelection) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(palette.chromePanelAlt)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaneAction("Duplicate", metrics, Modifier.weight(1f)) { controller.duplicateSelection() }
                    PaneAction("Prefab", metrics, Modifier.weight(1f)) { app.beginSavePrefab() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaneAction("Forward", metrics, Modifier.weight(1f)) { controller.bringForward() }
                    PaneAction("Backward", metrics, Modifier.weight(1f)) { controller.sendBackward() }
                    PaneAction("Delete", metrics, Modifier.weight(1f), tint = ErrorRed) {
                        app.requestDeleteSelection()
                    }
                }
            }
        }
    }
}

@Composable
private fun PaneTab(
    title: String,
    badge: Int?,
    badgeIsError: Boolean,
    selected: Boolean,
    metrics: AdaptiveMetrics,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(metrics.corner / 2))
            .background(if (selected) palette.accent.copy(alpha = 0.22f) else palette.chromePanel)
            .clickable(onClick = onClick)
            .heightIn(min = metrics.minTarget),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) palette.chromeText else palette.chromeTextMuted,
                maxLines = 1,
            )
            badge?.let {
                Spacer(Modifier.height(0.dp))
                Text(
                    "  $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (badgeIsError) ErrorRed else palette.chromeTextMuted,
                )
            }
        }
    }
}

@Composable
private fun PaneAction(
    label: String,
    metrics: AdaptiveMetrics,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(metrics.corner / 2))
            .background(palette.chromePanel)
            .clickable(onClick = onClick)
            .heightIn(min = metrics.minTarget),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = tint ?: palette.chromeText,
            maxLines = 1,
        )
    }
}
