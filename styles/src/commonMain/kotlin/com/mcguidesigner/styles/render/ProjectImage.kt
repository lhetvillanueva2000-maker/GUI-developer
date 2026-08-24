package com.mcguidesigner.styles.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import com.mcguidesigner.core.image.ImageBackground
import com.mcguidesigner.core.image.ImageSize
import com.mcguidesigner.core.image.PngWriter
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.model.TargetForm
import com.mcguidesigner.core.model.absoluteBounds
import com.mcguidesigner.core.model.walkAll

/**
 * Renders a design to a PNG at a chosen size.
 *
 * Recorded straight into a [GraphicsLayer] rather than composed into the
 * layout: a 16x export of a full screen is several thousand pixels square, and
 * putting that in the tree - even hidden - would make the editor lay it out and
 * pay for it on every frame. `record` draws at any size without the layout
 * system being involved at all.
 *
 * The drawing goes through the *same* [EditionSkin.drawElement] the canvas
 * uses, scaled up. That is the whole point: an export that used its own
 * renderer would be a second opinion about what the design looks like, and the
 * two would drift the first time either was touched.
 */
class ProjectImageRenderer internal constructor(
    private val layer: GraphicsLayer,
    private val measurer: TextMeasurer,
    private val density: androidx.compose.ui.unit.Density,
    private val layoutDirection: androidx.compose.ui.unit.LayoutDirection,
) {

    /**
     * The design as PNG bytes at [size].
     *
     * Suspending because reading a layer back off the GPU is not instant, and
     * pretending it is would block a frame on a large export.
     */
    suspend fun encode(
        project: GuiProject,
        skin: EditionSkin,
        textures: TextureResolver,
        size: ImageSize,
        background: ImageBackground = ImageBackground.DEFAULT,
    ): ByteArray {
        layer.record(density, layoutDirection, IntSize(size.width, size.height)) {
            drawProject(project, skin, textures, measurer, size.scale.toFloat(), background)
        }
        val bitmap = layer.toImageBitmap()

        // Read at the bitmap's own size rather than the size that was asked
        // for. They should agree, and if a platform ever rounds one of them the
        // mismatch is an out-of-bounds read inside readPixels rather than a
        // slightly wrong picture - so trust what came back, not what was
        // requested.
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) {
            error("The renderer produced a ${width}x$height image for ${size.label}.")
        }

        val pixels = IntArray(width * height)
        bitmap.readPixels(pixels, 0, 0, width, height)
        return PngWriter.encode(width, height, pixels)
    }
}

/**
 * A renderer bound to this composition.
 *
 * Composable because it needs three things only a composition has: a text
 * measurer with the real font resolver behind it, the density, and the layout
 * direction. Building a `TextMeasurer` by hand outside one is possible on each
 * platform separately and identical on neither.
 */
@Composable
fun rememberProjectImageRenderer(): ProjectImageRenderer {
    val layer = rememberGraphicsLayer()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    return ProjectImageRenderer(layer, measurer, density, layoutDirection)
}

/**
 * Paints a whole project at [scale], with no editor chrome.
 *
 * No grid, no rulers, no selection handles, no safe-area guides: those are the
 * editor telling you about the design, and none of them is part of it. What
 * comes out is what the game or the browser would show.
 */
fun DrawScope.drawProject(
    project: GuiProject,
    skin: EditionSkin,
    textures: TextureResolver,
    measurer: TextMeasurer,
    scale: Float,
    background: ImageBackground = ImageBackground.DEFAULT,
) {
    val bounds = project.absoluteBounds()

    scale(scale, pivot = Offset.Zero) {
        // The canvas colour first, so a design with transparent gaps exports
        // over its own backdrop rather than over nothing - unless the export
        // was asked for with no backdrop at all, which is what makes a PNG you
        // can lay over a screenshot of the game.
        if (background == ImageBackground.CANVAS) {
            drawRect(
                androidx.compose.ui.graphics.Color(project.canvas.backdropColor.toInt()),
                size = androidx.compose.ui.geometry.Size(
                    project.canvas.width.toFloat(),
                    project.canvas.height.toFloat(),
                ),
            )
        }

        project.elements.walkAll().forEach { element ->
            if (!element.visible) return@forEach
            val rect = bounds[element.id] ?: return@forEach
            with(skin) {
                drawElement(
                    ElementRenderContext(
                        element = element,
                        rect = androidx.compose.ui.geometry.Rect(
                            left = rect.x.toFloat(),
                            top = rect.y.toFloat(),
                            right = (rect.x + rect.width).toFloat(),
                            bottom = (rect.y + rect.height).toFloat(),
                        ),
                        scale = 1f,
                        state = InteractionState.NORMAL,
                        project = project,
                        textures = textures,
                        textMeasurer = measurer,
                        form = project.canvas.targetForm,
                        selected = false,
                    ),
                )
            }
        }
    }
}
