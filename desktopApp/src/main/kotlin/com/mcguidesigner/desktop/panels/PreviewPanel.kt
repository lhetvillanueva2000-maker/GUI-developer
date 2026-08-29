package com.mcguidesigner.desktop.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.desktop.widgets.IconToggle
import com.mcguidesigner.desktop.widgets.ToolbarSeparator
import com.mcguidesigner.styles.canvas.GuiDemoView
import com.mcguidesigner.styles.render.TextureCache
import com.mcguidesigner.styles.theme.LocalSkinPalette

/**
 * Preview / Demo: the screen exactly as the edition's skin will draw it, with
 * no editor chrome at all - and running.
 *
 * Two modes share the pane, and the toolbar is the switch between them. "Live"
 * hands every widget its own state and lets the pointer reach it: press a
 * button, tick a box, drag a slider, scroll a list, wheel or pinch to zoom.
 * The other five pin *every* widget to one interaction state at once, which is
 * the only way to inspect a hover or disabled skin you drew - no amount of
 * pointing at one widget will show you the rest.
 */
@Composable
fun PreviewPanel(
    controller: EditorController,
    state: EditorState,
    textures: TextureCache,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    val states = if (state.edition == Edition.BEDROCK) {
        InteractionState.touchStates
    } else {
        InteractionState.entries
    }

    Column(modifier.background(palette.chromeBackground)) {
        Row(
            Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "State:",
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
                modifier = Modifier.padding(end = 6.dp),
            )
            IconToggle(
                label = "Live",
                hint = "Let every widget answer for itself - press, toggle, drag, scroll and zoom",
                selected = state.previewState == null,
                onClick = { controller.setPreviewState(null) },
            )
            states.forEach { candidate ->
                IconToggle(
                    label = candidate.displayName,
                    hint = "Draw every interactive widget in its ${candidate.displayName.lowercase()} state",
                    selected = state.previewState == candidate,
                    onClick = { controller.setPreviewState(candidate) },
                )
            }

            ToolbarSeparator()

            IconToggle(
                label = "Reset",
                hint = "Put every widget back the way the document has it",
                selected = false,
                enabled = !state.demo.isClean,
                onClick = { controller.resetDemo() },
            )

            Box(Modifier.weight(1f))

            // What the demo just did, so a press that changes something
            // off-screen - a tab three panels over, a value inside a list - is
            // still visible as having happened.
            state.demo.lastAction?.takeIf { state.previewState == null }?.let { action ->
                Text(
                    action,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 10.dp),
                )
            }
            Text(
                "${state.edition.displayName}  ·  ${state.project.canvas.guiScale}x GUI scale",
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
            )
        }
        Divider(color = palette.chromeBorder)

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GuiDemoView(
                project = state.project,
                textures = textures,
                demo = state.demo,
                onDemo = controller::setDemo,
                modifier = Modifier.fillMaxSize(),
                forcedState = state.previewState,
                baseZoom = state.project.canvas.guiScale.toFloat().coerceAtLeast(1f),
                playAnimations = state.settings.playAnimations,
            )
        }
    }
}
