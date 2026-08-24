package com.mcguidesigner.styles.export

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.image.ImageExport
import com.mcguidesigner.core.image.ImageSize
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.util.Ids
import com.mcguidesigner.styles.render.EditionSkin
import com.mcguidesigner.styles.render.TextureResolver
import com.mcguidesigner.styles.render.drawProject
import com.mcguidesigner.styles.render.rememberProjectImageRenderer
import com.mcguidesigner.styles.theme.LocalEditionSkin
import com.mcguidesigner.styles.theme.LocalMotion
import com.mcguidesigner.styles.theme.LocalSkinPalette
import com.mcguidesigner.styles.theme.spec
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * The size list, a preview of what comes out, and the button that writes it.
 *
 * One panel for both shells rather than a dialog on the desktop and a sheet on
 * the phone. The desktop drops it into an `AlertDialog` body and Android into a
 * bottom sheet, but the *choices* - which sizes exist, which is selected, what
 * the file is called - are identical, and two copies of that is two chances for
 * the phone to offer a resolution the desktop does not.
 *
 * [onSave] receives finished PNG bytes and a suggested file name; where those
 * bytes go is the one genuinely platform-specific part, because the desktop has
 * a file system and Android has the storage-access framework.
 */
@Composable
fun ImageExportPanel(
    project: GuiProject,
    textures: TextureResolver,
    onSave: (fileName: String, bytes: ByteArray) -> Unit,
    modifier: Modifier = Modifier,
    skin: EditionSkin = LocalEditionSkin.current,
    onCancel: (() -> Unit)? = null,
) {
    val palette = LocalSkinPalette.current
    val scope = rememberCoroutineScope()
    val renderer = rememberProjectImageRenderer()
    val measurer = rememberTextMeasurer()

    val options = remember(project.canvas) { ImageExport.optionsFor(project.canvas) }
    var selected by remember(project.canvas) { mutableStateOf(ImageExport.defaultFor(project.canvas)) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    Column(modifier) {
        ImagePreview(project, skin, textures, measurer)

        Spacer(Modifier.height(14.dp))
        Text(
            "How big?",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = palette.chromeText,
        )
        Text(
            "Pixel art only survives whole-number scaling, so each name snaps to " +
                "the nearest whole multiple. The size beside it is what you get.",
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        options.forEach { option ->
            SizeRow(
                option = option,
                selected = option.scale == selected.scale,
                enabled = !busy,
                onClick = { selected = option },
            )
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${selected.width} × ${selected.height} pixels",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.chromeText,
                )
                Text(
                    failure ?: if (busy) {
                        "Rendering…"
                    } else {
                        "Transparent wherever the design is."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (failure != null) palette.accent else palette.chromeTextMuted,
                )
            }
            onCancel?.let {
                TextButton(onClick = it, enabled = !busy) { Text("Cancel") }
                Spacer(Modifier.width(6.dp))
            }
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    failure = null
                    // Launched rather than run inline: reading a large layer
                    // back off the GPU takes long enough to drop frames, and a
                    // dialog that freezes while it works looks like a crash.
                    scope.launch {
                        val size = selected
                        runCatching { renderer.encode(project, skin, textures, size) }
                            .onSuccess { onSave(fileNameFor(project, size), it) }
                            .onFailure { failure = "Could not render at ${size.label}: ${it.message}" }
                        busy = false
                    }
                },
            ) { Text(if (busy) "Rendering…" else "Save PNG") }
        }
    }
}

/**
 * `custom_chest_704x664.png`.
 *
 * The dimensions go in the name rather than the multiple because the file
 * outlives the dialog that made it, and "704x664" answers "which one is this"
 * six months later where "4x" does not.
 */
fun fileNameFor(project: GuiProject, size: ImageSize): String =
    "${Ids.slug(project.name)}_${size.width}x${size.height}.png"

/** One row of the size list. */
@Composable
private fun SizeRow(
    option: ImageSize,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalSkinPalette.current
    val motion = LocalMotion.current
    val background by animateColorAsState(
        targetValue = if (selected) palette.accentMuted else palette.chromePanelAlt,
        animationSpec = motion.spec(160),
        label = "size-row-background",
    )
    val mark by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.spec(180),
        label = "size-row-mark",
    )

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A spine rather than a radio button: it grows out of the edge the row
        // is already anchored to, so the selection reads at a glance without a
        // second control to look at.
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(palette.accent.copy(alpha = mark)),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            option.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = palette.chromeText,
            modifier = Modifier.weight(1f),
        )
        Text(
            option.dimensions,
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${option.scale}×",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) palette.accent else palette.chromeTextMuted,
        )
    }
}

/**
 * The design, drawn once at whatever fits.
 *
 * Deliberately the same [drawProject] the export uses, so what is previewed and
 * what is written cannot disagree. It is *not* snapped to a whole multiple -
 * this one is a thumbnail, and a thumbnail that refused to fill its box because
 * 3.4× is not a whole number would be a worse preview, not a more honest one.
 */
@Composable
private fun ImagePreview(
    project: GuiProject,
    skin: EditionSkin,
    textures: TextureResolver,
    measurer: TextMeasurer,
) {
    val palette = LocalSkinPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.chromeBackground)
            .padding(10.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val width = project.canvas.width.toFloat().coerceAtLeast(1f)
            val height = project.canvas.height.toFloat().coerceAtLeast(1f)
            val fit = min(size.width / width, size.height / height)
            translate(
                left = (size.width - width * fit) / 2f,
                top = (size.height - height * fit) / 2f,
            ) {
                drawProject(project, skin, textures, measurer, fit)
            }
        }
    }
}
