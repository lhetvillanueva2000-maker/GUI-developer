package com.mcguidesigner.styles.export

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.mcguidesigner.core.image.ImageBackground
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
import kotlin.math.min

/**
 * A destination the shell has obtained, waiting for pixels to fill it.
 *
 * The whole reason this type exists: the pixels must be rendered *after* the
 * destination is chosen, never before. Rendering first meant holding a PNG in
 * memory while the system file picker was the foreground activity - and Android
 * destroys a backgrounded activity under memory pressure, taking the bytes with
 * it. The file had already been created by then, so what landed on disk was
 * zero bytes and no error.
 */
data class ImageSaveRequest(
    val size: ImageSize,
    val background: ImageBackground,
)

/**
 * A preview of what comes out, a size to pick, and the button that writes it.
 *
 * One panel for both shells rather than a dialog on the desktop and a sheet on
 * the phone. The desktop drops it into an `AlertDialog` body and Android into a
 * bottom sheet, but the *choices* - which sizes exist, what the background is,
 * what the file is called - are identical, and two copies of that is two
 * chances for the phone to offer a resolution the desktop does not.
 *
 * The sizes are chips rather than a list of rows because there can be a dozen
 * of them, and a dozen full-width rows is a scrolling wall in front of a
 * decision that is really "roughly how big?".
 *
 * Saving is deliberately in two steps. [onRequestDestination] asks the shell
 * for somewhere to put the file; when the shell has one it hands back a
 * [pending] request, and only then is anything rendered. See [ImageSaveRequest]
 * for why that order is not negotiable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageExportPanel(
    project: GuiProject,
    textures: TextureResolver,
    onRequestDestination: (fileName: String, size: ImageSize, background: ImageBackground) -> Unit,
    modifier: Modifier = Modifier,
    skin: EditionSkin = LocalEditionSkin.current,
    pending: ImageSaveRequest? = null,
    onRendered: (ByteArray) -> Unit = {},
    onRenderFailed: (String) -> Unit = {},
    onCancel: (() -> Unit)? = null,
) {
    val palette = LocalSkinPalette.current
    val renderer = rememberProjectImageRenderer()
    val measurer = rememberTextMeasurer()

    val options = remember(project.canvas) { ImageExport.optionsFor(project.canvas) }
    var selected by remember(project.canvas) { mutableStateOf(ImageExport.defaultFor(project.canvas)) }
    var background by remember { mutableStateOf(ImageBackground.DEFAULT) }
    var failure by remember { mutableStateOf<String?>(null) }

    // Rendering happens here, keyed on the destination the shell obtained -
    // never on the button press. Nothing is held across the picker.
    LaunchedEffect(pending) {
        val request = pending ?: return@LaunchedEffect
        failure = null
        runCatching { renderer.encode(project, skin, textures, request.size, request.background) }
            .onSuccess { bytes ->
                if (bytes.isEmpty()) {
                    // Writing an empty array would produce exactly the silent
                    // zero-byte file this whole flow was rebuilt to prevent.
                    val message = "The renderer produced nothing at ${request.size.label}."
                    failure = message
                    onRenderFailed(message)
                } else {
                    onRendered(bytes)
                }
            }
            .onFailure {
                val message = "Could not render at ${request.size.label}: ${it.message}"
                failure = message
                onRenderFailed(message)
            }
    }

    val busy = pending != null

    Column(modifier) {
        ImagePreview(project, skin, textures, measurer, background)

        Spacer(Modifier.height(16.dp))
        FieldLabel(
            "Size",
            "Pixel art only survives whole-number scaling, so each name snaps to the " +
                "nearest whole multiple. The number below is what you actually get.",
        )
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            options.forEach { option ->
                Chip(
                    label = option.label,
                    selected = option.scale == selected.scale,
                    enabled = !busy,
                ) { selected = option }
            }
        }

        Spacer(Modifier.height(16.dp))
        FieldLabel("Background", "What fills the space the design does not cover.")
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ImageBackground.entries.forEach { candidate ->
                Chip(
                    label = candidate.displayName,
                    selected = candidate == background,
                    enabled = !busy,
                ) { background = candidate }
            }
        }
        Text(
            background.blurb,
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    selected.dimensions,
                    style = MaterialTheme.typography.titleSmall,
                    color = palette.chromeText,
                )
                Text(
                    failure ?: when {
                        busy -> "Rendering…"
                        selected.pixels > 2_000_000L -> "${selected.scale}× · ${selected.megapixels}"
                        else -> "${selected.scale}× the design"
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
                    failure = null
                    onRequestDestination(fileNameFor(project, selected), selected, background)
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

@Composable
private fun FieldLabel(title: String, help: String) {
    val palette = LocalSkinPalette.current
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = palette.chromeText,
        )
        Text(
            help,
            style = MaterialTheme.typography.labelSmall,
            color = palette.chromeTextMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** One choice, small enough that a dozen of them still read as a row. */
@Composable
private fun Chip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val palette = LocalSkinPalette.current
    val motion = LocalMotion.current
    val background by animateColorAsState(
        targetValue = if (selected) palette.accentMuted else palette.chromePanelAlt,
        animationSpec = motion.spec(160),
        label = "chip-background",
    )
    val border by animateColorAsState(
        targetValue = if (selected) palette.accent else palette.chromePanelAlt,
        animationSpec = motion.spec(160),
        label = "chip-border",
    )
    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motion.spec(160),
        label = "chip-lift",
    )

    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            // Drawn as a ring rather than a border modifier so it can fade in
            // without the chip changing size and shuffling the whole row.
            .drawBehind {
                if (lift > 0f) {
                    drawRoundRect(
                        color = border.copy(alpha = border.alpha * lift),
                        style = Stroke(width = 1.5f),
                        cornerRadius = CornerRadius(9.dp.toPx()),
                    )
                }
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) palette.chromeText else palette.chromeTextMuted,
        )
    }
}

/**
 * The design, drawn once at whatever fits.
 *
 * Deliberately the same [drawProject] the export uses, so what is previewed and
 * what is written cannot disagree - including the background choice, which is
 * the whole reason to look at a preview at all. It is *not* snapped to a whole
 * multiple: this one is a thumbnail, and a thumbnail that refused to fill its
 * box because 3.4× is not a whole number would be a worse preview, not a more
 * honest one.
 */
@Composable
private fun ImagePreview(
    project: GuiProject,
    skin: EditionSkin,
    textures: TextureResolver,
    measurer: TextMeasurer,
    background: ImageBackground,
) {
    val palette = LocalSkinPalette.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
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
                // The chequerboard is how a transparent export is shown to be
                // transparent; without it "Transparent" and a dark canvas
                // backdrop look identical against a dark panel.
                if (background == ImageBackground.TRANSPARENT) {
                    drawChequerboard(width * fit, height * fit, palette.chromePanelAlt, palette.chromePanel)
                }
                drawProject(project, skin, textures, measurer, fit, background)
            }
        }
    }
}

/** The usual "this is nothing" pattern, in two theme colours. */
private fun DrawScope.drawChequerboard(
    width: Float,
    height: Float,
    light: Color,
    dark: Color,
) {
    val cell = 8f
    var y = 0f
    var row = 0
    while (y < height) {
        var x = 0f
        var column = 0
        while (x < width) {
            drawRect(
                color = if ((row + column) % 2 == 0) light else dark,
                topLeft = Offset(x, y),
                size = Size(min(cell, width - x), min(cell, height - y)),
            )
            x += cell
            column++
        }
        y += cell
        row++
    }
}
