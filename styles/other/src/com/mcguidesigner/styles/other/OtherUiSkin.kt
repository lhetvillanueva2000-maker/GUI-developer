package com.mcguidesigner.styles.other

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.model.CanvasBackdrop
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.model.bool
import com.mcguidesigner.core.model.color
import com.mcguidesigner.core.model.float
import com.mcguidesigner.core.model.int
import com.mcguidesigner.core.model.string
import com.mcguidesigner.core.model.stringList
import com.mcguidesigner.core.model.texture
import com.mcguidesigner.styles.render.EditionSkin
import com.mcguidesigner.styles.render.ElementRenderContext
import com.mcguidesigner.styles.render.checkerboard
import com.mcguidesigner.styles.render.drawAnimatedImage
import com.mcguidesigner.styles.render.drawCustomElement
import com.mcguidesigner.styles.render.drawCustomShape
import com.mcguidesigner.styles.render.drawImageFitted
import com.mcguidesigner.styles.render.drawShadowedText
import com.mcguidesigner.styles.render.fillRect
import com.mcguidesigner.styles.theme.ChromeColors
import com.mcguidesigner.styles.theme.SkinPalette

/**
 * A flat, contemporary interface skin for everything that is not Minecraft.
 *
 * The other two skins are careful reconstructions of a game's own art, and
 * their correctness is measurable: a pixel is right or it is wrong. This one
 * has no original to be measured against, so it is built on the handful of
 * conventions every current platform agrees on - rounded corners, one-pixel
 * borders, a filled accent for the primary action, generous control heights,
 * and no text shadow.
 *
 * That last one matters more than it sounds. Both Minecraft skins drop-shadow
 * their text because the game does; carrying that habit over would be the
 * clearest possible tell that a mock-up came out of a Minecraft tool, on a
 * screen that is meant to look like an app.
 *
 * Nothing here is shared with either Minecraft skin, deliberately, so this one
 * can be redesigned freely without any risk of regressing art that is supposed
 * to match the game exactly.
 */
object OtherUiSkin : EditionSkin {

    override val edition = Edition.OTHER
    override val displayName = "Other UIs"
    override val tagline = "Flat and neutral - rounded corners, 36px controls, no game to imitate"
    override val palette: SkinPalette = OtherUiPalette.palette
    override val darkChrome: ChromeColors = OtherUiPalette.DarkChrome
    override val lightChrome: ChromeColors = OtherUiPalette.LightChrome

    override fun DrawScope.drawBackdrop(rect: Rect, project: GuiProject, scale: Float) {
        when (project.canvas.backdrop) {
            CanvasBackdrop.NONE -> Unit

            CanvasBackdrop.DIM -> fillRect(rect, Color(0x14000000))

            CanvasBackdrop.SOLID -> fillRect(rect, Color(project.canvas.backdropColor))

            // The two Minecraft-world backdrops have no meaning here. Rather
            // than draw a sky behind a settings screen, both fall back to the
            // page grey an app would actually sit on.
            CanvasBackdrop.GAME_WORLD, CanvasBackdrop.DIRT_PANORAMA ->
                fillRect(rect, OtherUiPalette.SurfaceSunken)
        }
    }

    override fun DrawScope.drawElement(context: ElementRenderContext) {
        val rect = context.rect
        if (rect.width < 1f || rect.height < 1f) return

        when (context.element.type) {
            ElementCatalog.PANEL_FRAME -> drawCard(context)
            ElementCatalog.CONTAINER_SCROLL -> drawScrollArea(context)
            ElementCatalog.BUTTON_NORMAL, ElementCatalog.JAVA_RECT_BUTTON -> drawButton(context)
            ElementCatalog.BUTTON_TOGGLE -> drawSwitch(context)
            ElementCatalog.BUTTON_TAB -> drawTab(context)
            ElementCatalog.BUTTON_ICON -> drawIconButton(context)
            ElementCatalog.TEXT_LABEL -> drawLabel(context)
            ElementCatalog.BAR_HEADER -> drawAppBar(context)
            ElementCatalog.INPUT_TEXTBOX -> drawField(context, showIcon = false)
            ElementCatalog.INPUT_SEARCH -> drawField(context, showIcon = true)
            ElementCatalog.INPUT_CHECKBOX -> drawCheckbox(context)
            ElementCatalog.INPUT_DROPDOWN -> drawSelect(context)
            ElementCatalog.INPUT_SLIDER -> drawSlider(context)
            ElementCatalog.PROGRESS_BAR -> drawProgress(context)
            ElementCatalog.DECOR_SEPARATOR -> drawDivider(context)
            ElementCatalog.IMAGE_PLACEHOLDER -> drawImage(context)
            ElementCatalog.IMAGE_ANIMATED -> drawAnimatedImage(context)
            ElementCatalog.SHAPE_CUSTOM -> drawCustomShape(context)
            ElementCatalog.CUSTOM_ELEMENT -> drawCustomElement(context)
            else -> drawCard(context)
        }
    }

    // -- Building blocks ---------------------------------------------------

    /** A rounded rectangle, filled and optionally outlined. */
    private fun DrawScope.panel(
        rect: Rect,
        fill: Color,
        border: Color? = OtherUiPalette.Divider,
        radius: Float,
        borderWidth: Float = 1f,
    ) {
        val corner = CornerRadius(radius, radius)
        drawPath(Path().apply { addRoundRect(RoundRect(rect, corner)) }, fill)
        border?.let {
            drawPath(
                Path().apply { addRoundRect(RoundRect(rect.deflate(borderWidth / 2f), corner)) },
                it,
                style = Stroke(width = borderWidth),
            )
        }
    }

    /** The radius for [rect], never more than half its shortest side. */
    private fun ElementRenderContext.radiusFor(rect: Rect, guiPixels: Int = 6): Float =
        px(guiPixels).coerceAtMost(minOf(rect.width, rect.height) / 2f)

    private fun DrawScope.centredText(
        ctx: ElementRenderContext,
        text: String,
        rect: Rect,
        color: Color,
        sizeInGuiPixels: Float = 8f,
        align: TextAlign = TextAlign.Center,
    ) {
        if (text.isBlank()) return
        val style = ctx.textStyle(color, sizeInGuiPixels).copy(textAlign = align)
        val layout = ctx.textMeasurer.measure(text = text, style = style, maxLines = 1)
        val x = when (align) {
            TextAlign.Start, TextAlign.Left -> rect.left + ctx.px(10)
            TextAlign.End, TextAlign.Right -> rect.right - ctx.px(10) - layout.size.width
            else -> rect.center.x - layout.size.width / 2f
        }
        drawShadowedText(
            measurer = ctx.textMeasurer,
            text = text,
            topLeft = Offset(x, rect.center.y - layout.size.height / 2f),
            style = style,
            shadowColor = OtherUiPalette.TextShadow,
            // Flat interfaces do not shadow their text; see OtherUiPalette.
            shadow = false,
        )
    }

    // -- Elements ----------------------------------------------------------

    private fun DrawScope.drawCard(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val fill = Color(ctx.props.color("background", OtherUiPalette.Surface.value.toLong()))
        panel(rect, fill, OtherUiPalette.Divider, ctx.radiusFor(rect))
        centredText(ctx, ctx.props.string("label", ""), rect, OtherUiPalette.TextPrimary)
    }

    private fun DrawScope.drawScrollArea(ctx: ElementRenderContext) {
        val rect = ctx.rect
        panel(rect, OtherUiPalette.SurfaceRaised, OtherUiPalette.Divider, ctx.radiusFor(rect))

        // A scrollbar track along the inside edge, which is what tells you this
        // is a scrolling region rather than a plain card.
        val trackWidth = ctx.px(4)
        val inset = ctx.px(4)
        val track = Rect(
            left = rect.right - inset - trackWidth,
            top = rect.top + inset,
            right = rect.right - inset,
            bottom = rect.bottom - inset,
        )
        if (track.height > 0f) {
            panel(track, OtherUiPalette.SurfaceSunken, null, trackWidth / 2f)
            val thumb = Rect(track.left, track.top, track.right, track.top + track.height * 0.4f)
            panel(thumb, OtherUiPalette.ControlOutline, null, trackWidth / 2f)
        }
    }

    private fun DrawScope.drawButton(ctx: ElementRenderContext) {
        val rect = ctx.rect
        // BUTTON_NORMAL has no "primary" flag in the catalog, and inventing one
        // here would be a property the inspector never shows and no exporter
        // ever writes. A button with its own background is the filled variant;
        // one without is the quiet one.
        val custom = ctx.props.color("background", 0L)
        val primary = custom == 0L
        val enabled = ctx.state != InteractionState.DISABLED

        val fill = when {
            !enabled -> OtherUiPalette.ControlDisabled
            primary -> when (ctx.state) {
                InteractionState.PRESSED -> OtherUiPalette.Accent.copy(alpha = 0.82f)
                InteractionState.HOVER -> OtherUiPalette.Accent.copy(alpha = 0.92f)
                else -> OtherUiPalette.Accent
            }
            else -> Color(custom)
        }

        panel(
            rect,
            fill,
            if (primary) null else OtherUiPalette.ControlOutline,
            ctx.radiusFor(rect),
        )

        if (ctx.state == InteractionState.FOCUSED) {
            val corner = CornerRadius(ctx.radiusFor(rect) + ctx.px(2), ctx.radiusFor(rect) + ctx.px(2))
            drawPath(
                Path().apply { addRoundRect(RoundRect(rect.inflate(ctx.px(2)), corner)) },
                OtherUiPalette.Accent.copy(alpha = 0.45f),
                style = Stroke(width = ctx.px(2)),
            )
        }

        val label = ctx.props.string("label", "Button")
        val textColour = when {
            !enabled -> OtherUiPalette.TextDisabled
            primary -> Color.White
            else -> OtherUiPalette.TextPrimary
        }
        centredText(ctx, label, rect, textColour)
    }

    /**
     * A switch, not a two-state button.
     *
     * The Minecraft skins draw a toggle as a button that stays pressed, because
     * that is what the game does. Every flat platform draws a sliding track,
     * and getting this one wrong makes a mock-up read as the wrong platform
     * immediately.
     */
    private fun DrawScope.drawSwitch(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val on = ctx.props.bool("value", false)
        val trackHeight = minOf(rect.height, ctx.px(20))
        val track = Rect(
            left = rect.left,
            top = rect.center.y - trackHeight / 2f,
            right = rect.left + trackHeight * 1.8f,
            bottom = rect.center.y + trackHeight / 2f,
        )
        panel(
            track,
            if (on) OtherUiPalette.Accent else OtherUiPalette.SurfaceSunken,
            if (on) null else OtherUiPalette.ControlOutline,
            track.height / 2f,
        )

        val knobRadius = track.height / 2f - ctx.px(2)
        val knobX = if (on) track.right - knobRadius - ctx.px(2) else track.left + knobRadius + ctx.px(2)
        drawCircle(Color.White, radius = knobRadius, center = Offset(knobX, track.center.y))

        val label = ctx.props.string("label", "")
        if (label.isNotBlank() && rect.right > track.right) {
            centredText(
                ctx,
                label,
                Rect(track.right + ctx.px(2), rect.top, rect.right, rect.bottom),
                OtherUiPalette.TextPrimary,
                align = TextAlign.Start,
            )
        }
    }

    private fun DrawScope.drawTab(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val selected = ctx.props.bool("selected", false)
        centredText(
            ctx,
            ctx.props.string("label", "Tab"),
            rect,
            if (selected) OtherUiPalette.Accent else OtherUiPalette.TextSecondary,
        )
        // An underline rather than a raised tab shape - the flat convention.
        if (selected) {
            val thickness = ctx.px(2)
            fillRect(
                Rect(rect.left, rect.bottom - thickness, rect.right, rect.bottom),
                OtherUiPalette.Accent,
            )
        }
    }

    private fun DrawScope.drawIconButton(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val fill = when (ctx.state) {
            InteractionState.PRESSED -> OtherUiPalette.ControlPressed
            InteractionState.HOVER -> OtherUiPalette.ControlHover
            else -> Color.Transparent
        }
        if (fill != Color.Transparent) {
            panel(rect, fill, null, minOf(rect.width, rect.height) / 2f)
        }
        centredText(ctx, ctx.props.string("glyph", "●"), rect, OtherUiPalette.TextSecondary, 10f)
    }

    private fun DrawScope.drawLabel(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val text = ctx.props.string("text", "Label")
        val colour = Color(ctx.props.color("textColor", OtherUiPalette.TextPrimary.value.toLong()))
        val align = when (ctx.props.string("align", "left")) {
            "center" -> TextAlign.Center
            "right" -> TextAlign.End
            else -> TextAlign.Start
        }
        val size = ctx.props.float("scale", 1f) * 8f
        val style = ctx.textStyle(colour, size).copy(textAlign = align)
        drawShadowedText(
            measurer = ctx.textMeasurer,
            text = text,
            topLeft = Offset(rect.left, rect.top),
            style = style,
            shadowColor = OtherUiPalette.TextShadow,
            shadow = false,
            maxWidth = rect.width.toInt(),
        )
    }

    private fun DrawScope.drawAppBar(ctx: ElementRenderContext) {
        val rect = ctx.rect
        fillRect(rect, OtherUiPalette.Surface)
        // A hairline under the bar rather than a bevel: the flat way of saying
        // "this sits above the content".
        fillRect(Rect(rect.left, rect.bottom - 1f, rect.right, rect.bottom), OtherUiPalette.Divider)
        centredText(
            ctx,
            ctx.props.string("title", "Title"),
            rect,
            OtherUiPalette.TextPrimary,
            9f,
            TextAlign.Start,
        )
    }

    private fun DrawScope.drawField(ctx: ElementRenderContext, showIcon: Boolean) {
        val rect = ctx.rect
        val focused = ctx.state == InteractionState.FOCUSED
        panel(
            rect,
            OtherUiPalette.Surface,
            if (focused) OtherUiPalette.Accent else OtherUiPalette.ControlOutline,
            ctx.radiusFor(rect),
            borderWidth = if (focused) 2f else 1f,
        )

        var textLeft = rect.left
        if (showIcon) {
            val r = ctx.px(4)
            val centre = Offset(rect.left + ctx.px(11), rect.center.y)
            drawCircle(OtherUiPalette.TextDisabled, radius = r, center = centre, style = Stroke(width = ctx.px(1.5f)))
            drawLine(
                OtherUiPalette.TextDisabled,
                start = Offset(centre.x + r * 0.7f, centre.y + r * 0.7f),
                end = Offset(centre.x + r * 1.6f, centre.y + r * 1.6f),
                strokeWidth = ctx.px(1.5f),
            )
            textLeft = rect.left + ctx.px(12)
        }

        val value = ctx.props.string("value", "")
        val placeholder = ctx.props.string("placeholder", "")
        val text = value.ifBlank { placeholder }
        centredText(
            ctx,
            text,
            Rect(textLeft, rect.top, rect.right, rect.bottom),
            if (value.isBlank()) OtherUiPalette.TextDisabled else OtherUiPalette.TextPrimary,
            align = TextAlign.Start,
        )
    }

    private fun DrawScope.drawCheckbox(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val checked = ctx.props.bool("checked", false)
        val side = minOf(rect.height, ctx.px(18))
        val box = Rect(rect.left, rect.center.y - side / 2f, rect.left + side, rect.center.y + side / 2f)

        panel(
            box,
            if (checked) OtherUiPalette.Accent else OtherUiPalette.Surface,
            if (checked) null else OtherUiPalette.ControlOutline,
            ctx.px(3),
        )
        if (checked) {
            val w = ctx.px(1.8f)
            drawLine(
                Color.White,
                Offset(box.left + side * 0.24f, box.center.y),
                Offset(box.left + side * 0.44f, box.bottom - side * 0.28f),
                strokeWidth = w,
            )
            drawLine(
                Color.White,
                Offset(box.left + side * 0.44f, box.bottom - side * 0.28f),
                Offset(box.right - side * 0.22f, box.top + side * 0.3f),
                strokeWidth = w,
            )
        }

        val label = ctx.props.string("label", "")
        if (label.isNotBlank()) {
            centredText(
                ctx,
                label,
                Rect(box.right + ctx.px(2), rect.top, rect.right, rect.bottom),
                OtherUiPalette.TextPrimary,
                align = TextAlign.Start,
            )
        }
    }

    private fun DrawScope.drawSelect(ctx: ElementRenderContext) {
        val rect = ctx.rect
        panel(rect, OtherUiPalette.Surface, OtherUiPalette.ControlOutline, ctx.radiusFor(rect))

        val options = ctx.props.stringList("items")
        val index = ctx.props.int("selectedIndex", 0)
        val label = options.getOrNull(index) ?: ctx.props.string("value", "Select")
        centredText(
            ctx,
            label,
            Rect(rect.left, rect.top, rect.right - ctx.px(16), rect.bottom),
            OtherUiPalette.TextPrimary,
            align = TextAlign.Start,
        )

        // A chevron, drawn rather than typed, so it does not depend on the font.
        val cx = rect.right - ctx.px(11)
        val cy = rect.center.y
        val s = ctx.px(3.5f)
        val w = ctx.px(1.6f)
        drawLine(OtherUiPalette.TextSecondary, Offset(cx - s, cy - s / 2f), Offset(cx, cy + s / 2f), strokeWidth = w)
        drawLine(OtherUiPalette.TextSecondary, Offset(cx, cy + s / 2f), Offset(cx + s, cy - s / 2f), strokeWidth = w)
    }

    private fun DrawScope.drawSlider(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val fraction = ctx.props.float("value", 0.5f).coerceIn(0f, 1f)
        val trackHeight = ctx.px(4)
        val track = Rect(
            rect.left,
            rect.center.y - trackHeight / 2f,
            rect.right,
            rect.center.y + trackHeight / 2f,
        )
        panel(track, OtherUiPalette.SurfaceSunken, null, trackHeight / 2f)
        if (fraction > 0f) {
            panel(
                Rect(track.left, track.top, track.left + track.width * fraction, track.bottom),
                OtherUiPalette.Accent,
                null,
                trackHeight / 2f,
            )
        }

        val knob = ctx.px(8)
        val cx = track.left + track.width * fraction
        drawCircle(Color.White, radius = knob, center = Offset(cx, track.center.y))
        drawCircle(
            OtherUiPalette.Accent,
            radius = knob,
            center = Offset(cx, track.center.y),
            style = Stroke(width = ctx.px(2)),
        )
    }

    private fun DrawScope.drawProgress(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val fraction = ctx.props.float("progress", 0.4f).coerceIn(0f, 1f)
        val radius = rect.height / 2f
        panel(rect, OtherUiPalette.SurfaceSunken, null, radius)
        if (fraction > 0f) {
            panel(
                Rect(rect.left, rect.top, rect.left + rect.width * fraction, rect.bottom),
                Color(ctx.props.color("fillColor", OtherUiPalette.Accent.value.toLong())),
                null,
                radius,
            )
        }
    }

    private fun DrawScope.drawDivider(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val colour = Color(ctx.props.color("color", OtherUiPalette.Divider.value.toLong()))
        if (rect.width >= rect.height) {
            fillRect(Rect(rect.left, rect.center.y - 0.5f, rect.right, rect.center.y + 0.5f), colour)
        } else {
            fillRect(Rect(rect.center.x - 0.5f, rect.top, rect.center.x + 0.5f, rect.bottom), colour)
        }
    }

    private fun DrawScope.drawImage(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val bitmap = ctx.textures.resolve(ctx.props.texture("texture"))
        if (bitmap != null) {
            drawImageFitted(
                image = bitmap,
                dest = rect,
                fit = ctx.props.string("fit", "contain"),
                alpha = ctx.props.float("opacity", 1f).coerceIn(0f, 1f),
                // Smoothed rather than nearest-neighbour: this target is not
                // pixel art, and a photograph in a mock-up should look like one.
                pixelated = ctx.props.bool("pixelated", false),
            )
            return
        }

        // The empty state: a soft placeholder rather than a chequerboard, which
        // in a UI mock-up reads as transparency rather than as "no image yet".
        panel(rect, OtherUiPalette.SurfaceSunken, OtherUiPalette.Divider, ctx.radiusFor(rect))
        val s = minOf(rect.width, rect.height) * 0.22f
        val c = rect.center
        drawCircle(OtherUiPalette.ControlOutline, radius = s * 0.42f, center = Offset(c.x - s * 0.5f, c.y - s * 0.35f))
        val hill = Path().apply {
            moveTo(c.x - s, c.y + s * 0.7f)
            lineTo(c.x - s * 0.1f, c.y - s * 0.2f)
            lineTo(c.x + s * 0.55f, c.y + s * 0.35f)
            lineTo(c.x + s, c.y - s * 0.05f)
            lineTo(c.x + s, c.y + s * 0.7f)
            close()
        }
        drawPath(hill, OtherUiPalette.ControlOutline)
    }
}
