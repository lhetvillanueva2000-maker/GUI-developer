package com.mcguidesigner.desktop.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.core.templates.GuiTemplate
import com.mcguidesigner.desktop.AppState
import com.mcguidesigner.styles.canvas.GuiPreview
import com.mcguidesigner.styles.render.TextureCache
import com.mcguidesigner.styles.theme.LocalSkinPalette

/**
 * Ready-to-use starting layouts.
 *
 * Templates for the *other* edition are shown too (greyed apart by their
 * badge) because switching edition is a legitimate way to start a port.
 */
@Composable
fun TemplatesPanel(
    app: AppState,
    state: EditorState,
    textures: TextureCache,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    val ordered = remember(state.edition) {
        BuiltInTemplates.all.sortedByDescending { it.edition == state.edition }
    }

    Column(modifier.fillMaxSize()) {
        Text(
            "Open a template as a new project. Your current project is replaced, " +
                "so save first if you need it.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
            modifier = Modifier.padding(10.dp),
        )
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            items(ordered, key = { it.id }) { template ->
                TemplateCard(template, textures, template.edition == state.edition) {
                    app.guardUnsaved("load the '${template.title}' template") {
                        app.newFromTemplate(template.id)
                    }
                }
            }
            item { Box(Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun TemplateCard(
    template: GuiTemplate,
    textures: TextureCache,
    matchesCurrentEdition: Boolean,
    onOpen: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    // Instantiating is cheap and gives an exact preview of what will open.
    val project = remember(template.id) { template.instantiate() }
    val zoom = remember(project) {
        minOf(250f / project.canvas.width, 130f / project.canvas.height).coerceIn(0.2f, 4f)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.chromePanelAlt)
            .border(
                1.dp,
                if (matchesCurrentEdition) palette.accentMuted else palette.chromeBorder,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onOpen),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(130.dp)
                .background(palette.chromeBackground),
            contentAlignment = Alignment.Center,
        ) {
            GuiPreview(
                project = project,
                textures = textures,
                modifier = Modifier.fillMaxSize(),
                zoom = zoom,
            )
        }
        Column(Modifier.padding(10.dp)) {
            Text(template.title, style = MaterialTheme.typography.labelLarge, color = palette.chromeText)
            Text(
                template.description,
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
            )
            Text(
                "${template.edition.displayName}  ·  ${template.form.displayName}  ·  " +
                    template.tags.joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = if (matchesCurrentEdition) palette.accent else palette.chromeTextMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
