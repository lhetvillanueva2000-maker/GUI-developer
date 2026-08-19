package com.mcguidesigner.desktop.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.model.TargetForm
import com.mcguidesigner.desktop.widgets.IconToggle
import com.mcguidesigner.desktop.widgets.ToolbarSeparator
import com.mcguidesigner.styles.canvas.GuiPreview
import com.mcguidesigner.styles.render.TextureCache
import com.mcguidesigner.styles.theme.LocalSkinPalette

/**
 * Live preview: the screen exactly as the edition's skin will draw it, with no
 * editor chrome at all.
 *
 * The interaction-state selector is the important part - it is the only way to
 * check hover/pressed/disabled skins without running the game, and the two
 * editions expose different state sets (Bedrock has no hover).
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
            states.forEach { candidate ->
                IconToggle(
                    label = candidate.displayName,
                    hint = "Draw every interactive widget in its ${candidate.displayName.lowercase()} state",
                    selected = state.previewState == candidate,
                    onClick = { controller.setPreviewState(candidate) },
                )
            }

            ToolbarSeparator()

            Text(
                "Layout:",
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
                modifier = Modifier.padding(end = 6.dp),
            )
            TargetForm.entries.forEach { form ->
                IconToggle(
                    label = form.displayName,
                    hint = "Preview the ${form.displayName.lowercase()} layout",
                    selected = state.previewForm == form,
                    onClick = { controller.setPreviewForm(form) },
                )
            }

            Box(Modifier.weight(1f))

            Text(
                "${state.edition.displayName}  ·  ${state.project.canvas.guiScale}x GUI scale",
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
            )
        }
        Divider(color = palette.chromeBorder)

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GuiPreview(
                project = state.project,
                textures = textures,
                modifier = Modifier.fillMaxSize(),
                zoom = state.project.canvas.guiScale.toFloat().coerceAtLeast(1f),
                previewState = state.previewState,
                form = state.previewForm,
            )
        }
    }
}
