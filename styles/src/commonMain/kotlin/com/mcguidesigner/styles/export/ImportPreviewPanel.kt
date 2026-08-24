package com.mcguidesigner.styles.export

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.image.ImageBackground
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.walkAll
import com.mcguidesigner.styles.render.TextureResolver
import com.mcguidesigner.styles.render.drawProject
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.SkinRegistry
import com.mcguidesigner.styles.theme.WarningAmber
import kotlin.math.min

/**
 * What an import produced, shown before it is allowed to land.
 *
 * The first cut of import simply *did* it - the file was read and the result
 * appeared, with the caveats delivered afterwards as a notification you could
 * miss. That is the wrong order for an operation whose whole nature is
 * approximate: these readers translate between formats that do not agree about
 * what a layout is, and "twelve of the nineteen elements came across" is
 * something to know *before* deciding, not after.
 *
 * So: the picture, the numbers, the caveats, and then a button. Cancelling
 * costs nothing, because nothing has happened yet.
 */
@Composable
fun ImportPreviewPanel(
    project: GuiProject,
    formatName: String,
    notes: List<String>,
    textures: TextureResolver,
    onImport: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalSkinPalette.current
    val measurer = rememberTextMeasurer()
    val skin = SkinRegistry.forEdition(project.edition)
    val elementCount = project.elements.walkAll().count()

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.chromeBackground)
                .padding(10.dp),
        ) {
            if (elementCount == 0) {
                Text(
                    "Nothing to show",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.chromeTextMuted,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                // Drawn with the target edition's own skin, because that is
                // what the design will look like once it is in - not what it
                // looked like in whatever wrote the file.
                Canvas(Modifier.fillMaxSize()) {
                    val width = project.canvas.width.toFloat().coerceAtLeast(1f)
                    val height = project.canvas.height.toFloat().coerceAtLeast(1f)
                    val fit = min(size.width / width, size.height / height)
                    translate(
                        left = (size.width - width * fit) / 2f,
                        top = (size.height - height * fit) / 2f,
                    ) {
                        drawProject(project, skin, textures, measurer, fit, ImageBackground.CANVAS)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Read as $formatName",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = palette.chromeText,
        )
        Text(
            "$elementCount element(s) · ${project.canvas.width} × ${project.canvas.height} · " +
                project.edition.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
            modifier = Modifier.padding(top = 2.dp),
        )

        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.chromePanelAlt)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "Worth knowing",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = WarningAmber,
                )
                notes.forEach { note ->
                    Text(
                        "• $note",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.chromeTextMuted,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onImport, enabled = elementCount > 0) { Text("Open as a new tab") }
        }
    }
}
