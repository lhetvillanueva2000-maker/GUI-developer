package com.mcguidesigner.desktop.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.editor.EditorState
import com.mcguidesigner.desktop.AppState
import com.mcguidesigner.desktop.Clipboard
import com.mcguidesigner.desktop.widgets.IconToggle
import com.mcguidesigner.desktop.widgets.ToolbarButton
import com.mcguidesigner.desktop.widgets.ToolbarSeparator
import com.mcguidesigner.exporters.CodeGenerator
import com.mcguidesigner.exporters.CodeTarget
import com.mcguidesigner.styles.theme.LocalSkinPalette

/**
 * The Code tab: turns the current design into source you can paste elsewhere.
 *
 * HTML/CSS is the default because it is the fastest way to get a design in
 * front of someone who does not have the game open; the Compose, Java and
 * Bedrock targets are for taking it into a real project.
 */
@Composable
fun CodePanel(
    app: AppState,
    state: EditorState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    val targets = remember(state.edition) { CodeGenerator.targetsFor(state.edition) }

    // Regenerate only when the document or the chosen language actually
    // changes - these generators walk the whole tree.
    val generated = remember(state.project, app.codeTarget) {
        val target = if (app.codeTarget in targets) app.codeTarget else CodeTarget.HTML_CSS
        CodeGenerator.generate(state.project, target)
    }

    Column(modifier.background(palette.chromeBackground)) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            targets.forEach { target ->
                IconToggle(
                    // A dot marks the formats Minecraft reads directly, so the
                    // strip says which tabs produce something the game can use.
                    label = if (target.readByMinecraft) "● ${target.tabLabel}" else target.tabLabel,
                    hint = buildString {
                        append(target.displayName)
                        if (target.readByMinecraft) append("  -  read by Minecraft directly")
                        append("\n")
                        append(target.description)
                    },
                    selected = generated.target == target,
                    onClick = { app.codeTarget = target },
                )
            }

            ToolbarSeparator()

            Text(
                generated.fileName,
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeText,
                fontFamily = FontFamily.Monospace,
            )

            Box(Modifier.weight(1f))

            Text(
                "${generated.lineCount} lines",
                style = MaterialTheme.typography.labelSmall,
                color = palette.chromeTextMuted,
                modifier = Modifier.padding(end = 8.dp),
            )
            ToolbarButton("Copy", hint = "Copy the generated source to the clipboard") {
                Clipboard.write(generated.source)
                app.status = "Copied ${generated.fileName} to the clipboard."
            }
            ToolbarButton("Export to file...", hint = "Write the generated source to a file") {
                app.saveGeneratedCode(generated.fileName, generated.source)
            }
        }
        Divider(color = palette.chromeBorder)

        Text(
            generated.target.description,
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Divider(color = palette.chromeBorder)

        SelectionContainer(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp),
            ) {
                Text(
                    text = generated.source,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = palette.chromeText,
                )
            }
        }
    }
}
