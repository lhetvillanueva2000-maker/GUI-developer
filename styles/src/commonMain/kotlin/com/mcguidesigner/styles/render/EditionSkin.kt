package com.mcguidesigner.styles.render

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.model.PropValue
import com.mcguidesigner.core.model.TargetForm
import com.mcguidesigner.styles.theme.SkinPalette

/**
 * Everything a skin needs to paint one element.
 *
 * [rect] is already in device pixels for the current zoom, so a skin never has
 * to know about the canvas transform; [scale] is exposed only so a skin can
 * decide how much detail is worth drawing at the current size.
 */
class ElementRenderContext(
    val element: GuiElement,
    val rect: Rect,
    val scale: Float,
    val state: InteractionState,
    val project: GuiProject,
    val textures: TextureResolver,
    val textMeasurer: TextMeasurer,
    val form: TargetForm,
    val selected: Boolean = false,
) {
    /** Properties with the current interaction state's overrides applied. */
    val props: Map<String, PropValue> = element.propsFor(state)

    /** One GUI pixel, in device pixels. */
    val pixel: Float get() = scale.coerceAtLeast(0.25f)

    fun px(guiPixels: Number): Float = guiPixels.toFloat() * pixel

    /** Base text style for this element at the current zoom. */
    fun textStyle(color: Color, sizeInGuiPixels: Float = 7f, scaleFactor: Float = 1f): TextStyle =
        TextStyle(
            color = color,
            fontSize = androidx.compose.ui.unit.TextUnit(
                (sizeInGuiPixels * pixel * scaleFactor).coerceAtLeast(6f),
                androidx.compose.ui.unit.TextUnitType.Sp,
            ),
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
}

/**
 * A complete visual identity for one Minecraft edition.
 *
 * Implementations live in `styles/java/src` and `styles/bedrock/src`.  They
 * share nothing but this interface and [PixelDraw], so the two look-and-feels
 * can be changed independently without any risk of cross-contamination.
 */
interface EditionSkin {
    val edition: Edition
    val displayName: String
    val palette: SkinPalette

    /** Short description shown in the edition switcher. */
    val tagline: String

    /** Paints one element. Children are painted by the caller, on top. */
    fun DrawScope.drawElement(context: ElementRenderContext)

    /** Paints the canvas backdrop behind every element. */
    fun DrawScope.drawBackdrop(rect: Rect, project: GuiProject, scale: Float)
}

/** Resolves texture asset ids to decoded, cached bitmaps. */
interface TextureResolver {
    fun resolve(assetId: String?): androidx.compose.ui.graphics.ImageBitmap?
}

/** Convenience so callers can write `skin.draw(scope, ctx)`. */
fun EditionSkin.draw(scope: DrawScope, context: ElementRenderContext) {
    with(this) { with(scope) { drawElement(context) } }
}

fun EditionSkin.drawBackdropOn(scope: DrawScope, rect: Rect, project: GuiProject, scale: Float) {
    with(this) { with(scope) { drawBackdrop(rect, project, scale) } }
}
