package com.mcguidesigner.styles.java

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.style.TextAlign
import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.model.CanvasBackdrop
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.InteractionState
import com.mcguidesigner.core.model.Insets
import com.mcguidesigner.core.model.bool
import com.mcguidesigner.core.model.color
import com.mcguidesigner.core.model.float
import com.mcguidesigner.core.model.int
import com.mcguidesigner.core.model.string
import com.mcguidesigner.core.model.stringList
import com.mcguidesigner.core.model.texture
import com.mcguidesigner.styles.render.ElementRenderContext
import com.mcguidesigner.styles.render.EditionSkin
import com.mcguidesigner.styles.render.bevelBox
import com.mcguidesigner.styles.render.checkerboard
import com.mcguidesigner.styles.render.drawImageFitted
import com.mcguidesigner.styles.render.drawShadowedText
import com.mcguidesigner.styles.render.fillRect
import com.mcguidesigner.styles.render.focusRing
import com.mcguidesigner.styles.render.hatch
import com.mcguidesigner.styles.render.strokeRect
import com.mcguidesigner.styles.theme.SkinPalette
import kotlin.math.roundToInt

/**
 * The Java Edition look: square corners, one-pixel bevels, the grey
 * `widgets.png` ramp and drop-shadowed text.
 *
 * Nothing here is shared with the Bedrock skin - the two files deliberately
 * duplicate structure so either can be redesigned without regressing the
 * other.
 */
object JavaEditionSkin : EditionSkin {

    override val edition = Edition.JAVA
    override val displayName = "Java Edition"
    override val tagline = "Vanilla widgets.png - crisp 1px bevels, 20px controls"
    override val palette: SkinPalette = JavaPalette.palette

    override fun DrawScope.drawBackdrop(rect: Rect, project: GuiProject, scale: Float) {
        when (project.canvas.backdrop) {
            CanvasBackdrop.NONE -> Unit

            CanvasBackdrop.DIM -> fillRect(rect, Color(0xC0101018))

            CanvasBackdrop.SOLID -> fillRect(rect, Color(project.canvas.backdropColor))

            CanvasBackdrop.GAME_WORLD -> {
                // A cheap stand-in for the world behind a container screen:
                // sky gradient over a ground band.
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color(0xFF6FA8DC),
                        0.62f to Color(0xFFB5D3EA),
                        0.63f to Color(0xFF5C8A3A),
                        1f to Color(0xFF3E5F27),
                    ),
                    topLeft = rect.topLeft,
                    size = rect.size,
                )
                fillRect(rect, Color(0x66000000))
            }

            CanvasBackdrop.DIRT_PANORAMA -> {
                // The vanilla "dirt background" used by menus.
                val tile = (16 * scale).coerceAtLeast(4f)
                checkerboard(rect, Color(0xFF6B4E2E), Color(0xFF624527), tile)
                fillRect(rect, Color(0x99000000))
            }
        }
    }

    override fun DrawScope.drawElement(context: ElementRenderContext) {
        val rect = context.rect
        if (rect.width < 1f || rect.height < 1f) return

        when (context.element.type) {
            ElementCatalog.PANEL_CHEST -> drawChestPanel(context)
            ElementCatalog.PANEL_FRAME -> drawPanelFrame(context)
            ElementCatalog.PANEL_TOOLTIP -> drawTooltip(context)
            ElementCatalog.CONTAINER_SCROLL -> drawScrollContainer(context)
            ElementCatalog.SLOT_INVENTORY -> drawSlot(context)
            ElementCatalog.STRIP_HOTBAR -> drawHotbar(context)
            ElementCatalog.BUTTON_NORMAL, ElementCatalog.JAVA_RECT_BUTTON -> drawButton(context)
            ElementCatalog.BUTTON_TOGGLE -> drawToggle(context)
            ElementCatalog.BUTTON_TAB -> drawTab(context)
            ElementCatalog.BUTTON_ICON -> drawIconButton(context)
            ElementCatalog.TEXT_LABEL -> drawLabel(context)
            ElementCatalog.BAR_HEADER -> drawHeader(context)
            ElementCatalog.INPUT_TEXTBOX -> drawTextBox(context, showIcon = false)
            ElementCatalog.INPUT_SEARCH -> drawTextBox(context, showIcon = context.props.bool("showIcon", true))
            ElementCatalog.INPUT_CHECKBOX -> drawCheckbox(context)
            ElementCatalog.INPUT_DROPDOWN -> drawDropdown(context)
            ElementCatalog.INPUT_SLIDER -> drawSlider(context)
            ElementCatalog.PROGRESS_BAR -> drawProgress(context)
            ElementCatalog.DECOR_SEPARATOR -> drawSeparator(context)
            ElementCatalog.IMAGE_PLACEHOLDER -> drawImage(context)
            else -> drawUnknown(context)
        }

        if (context.state == InteractionState.FOCUSED) {
            focusRing(rect, JavaPalette.FocusRing, context.px(1))
        }
    }

    // -- Containers --------------------------------------------------------

    private fun DrawScope.drawChestPanel(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val custom = ctx.textures.resolve(ctx.props.texture("texture"))
        if (custom != null) {
            val asset = ctx.project.texture(ctx.props.texture("texture"))
            drawImageFitted(custom, rect, ctx.props.string("textureFit", "nine_slice"), asset?.nineSlice ?: Insets.Zero)
            return
        }

        val body = when (ctx.props.string("skin", "vanilla")) {
            "dark" -> Color(0xFF3B3B3B)
            "light" -> Color(0xFFE4E4E4)
            "smithing" -> Color(0xFFBFB9AE)
            "flat" -> Color(0xFF9C9C9C)
            else -> JavaPalette.ContainerBody
        }
        val light = body.lighten(0.32f)
        val dark = body.darken(0.38f)

        bevelBox(rect, body, light, dark, ctx.px(1) * 2)
        // The vanilla panel has a second, inner one-pixel bevel.
        bevelBox(rect.deflate(ctx.px(2)), body, light.copy(alpha = 0.5f), dark.copy(alpha = 0.5f), ctx.px(1))

        if (ctx.props.bool("showTitle", true)) {
            val title = ctx.props.string("title")
            if (title.isNotBlank()) {
                drawShadowedText(
                    measurer = ctx.textMeasurer,
                    text = title,
                    topLeft = Offset(rect.left + ctx.px(8), rect.top + ctx.px(5)),
                    style = ctx.textStyle(Color(ctx.props.color("titleColor", 0xFF404040))),
                    shadowColor = Color.Transparent,
                    shadow = false,
                )
            }
        }
    }

    private fun DrawScope.drawPanelFrame(ctx: ElementRenderContext) {
        val rect = ctx.rect
        if (ctx.props.bool("shadow", true)) {
            val offset = ctx.px(ctx.props.int("shadowOffset", 3))
            fillRect(
                rect.translate(offset, offset),
                Color.Black.copy(alpha = ctx.props.float("shadowOpacity", 0.45f)),
            )
        }

        val custom = ctx.textures.resolve(ctx.props.texture("texture"))
        if (custom != null) {
            val asset = ctx.project.texture(ctx.props.texture("texture"))
            drawImageFitted(custom, rect, ctx.props.string("textureFit", "nine_slice"), asset?.nineSlice ?: Insets.Zero)
            return
        }

        fillRect(rect, Color(ctx.props.color("background", 0xF0100010)))
        val border = ctx.props.int("borderWidth", 1)
        if (border > 0) {
            strokeRect(rect, Color(ctx.props.color("borderColor", 0xFF5A5A5A)), ctx.px(border))
            // Java's inner highlight line.
            strokeRect(rect.deflate(ctx.px(border)), Color(0x22FFFFFF), ctx.px(1))
        }
    }

    private fun DrawScope.drawTooltip(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val p = ctx.px(1)
        fillRect(rect.deflate(p), Color(ctx.props.color("background", 0xF0100010)))

        // Vanilla draws the tooltip border as a vertical gradient between two
        // purples, inset by one pixel on every side.
        val top = Color(ctx.props.color("borderTop", 0xFF5000FF))
        val bottom = Color(ctx.props.color("borderBottom", 0xFF28007F))
        val inner = rect.deflate(p)
        drawRect(
            brush = Brush.verticalGradient(listOf(top, bottom)),
            topLeft = Offset(inner.left, inner.top),
            size = androidx.compose.ui.geometry.Size(p, inner.height),
        )
        drawRect(
            brush = Brush.verticalGradient(listOf(top, bottom)),
            topLeft = Offset(inner.right - p, inner.top),
            size = androidx.compose.ui.geometry.Size(p, inner.height),
        )
        fillRect(Rect(inner.left, inner.top, inner.right, inner.top + p), top)
        fillRect(Rect(inner.left, inner.bottom - p, inner.right, inner.bottom), bottom)

        val text = ctx.props.string("text")
        if (text.isNotBlank()) {
            drawShadowedText(
                measurer = ctx.textMeasurer,
                text = text,
                topLeft = Offset(rect.left + ctx.px(4), rect.top + ctx.px(4)),
                style = ctx.textStyle(Color(ctx.props.color("textColor", 0xFFFFFFFF))),
                shadowColor = JavaPalette.TextShadow,
                shadow = true,
                maxWidth = (rect.width - ctx.px(8)).roundToInt(),
            )
        }
    }

    private fun DrawScope.drawScrollContainer(ctx: ElementRenderContext) {
        val rect = ctx.rect
        fillRect(rect, Color(ctx.props.color("background", 0x40000000)))
        strokeRect(rect, JavaPalette.SlotShadow, ctx.px(1))

        if (ctx.props.bool("showScrollbar", true)) {
            val width = ctx.px(ctx.props.int("scrollbarWidth", 6))
            val track = Rect(rect.right - width, rect.top, rect.right, rect.bottom)
            fillRect(track, Color(0xFF000000))
            val content = ctx.props.int("contentLength", 240).coerceAtLeast(1)
            val visible = ctx.element.bounds.height.coerceAtLeast(1)
            val ratio = (visible.toFloat() / content).coerceIn(0.08f, 1f)
            val thumb = Rect(track.left, track.top, track.right, track.top + track.height * ratio)
            bevelBox(
                thumb,
                Color(ctx.props.color("thumbColor", 0xFFC6C6C6)),
                JavaPalette.ContainerHighlight,
                JavaPalette.ContainerShadow,
                ctx.px(1),
            )
        }
    }

    // -- Inventory ---------------------------------------------------------

    private fun DrawScope.drawSlot(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val p = ctx.px(1)
        val fill = Color(ctx.props.color("slotColor", 0xFF8B8B8B))

        // Vanilla slot: dark on the top/left, white on the bottom/right.
        bevelBox(rect, fill, JavaPalette.SlotHighlight, JavaPalette.SlotShadow, p, inverted = true)

        ctx.textures.resolve(ctx.props.texture("itemIcon"))?.let { icon ->
            drawImageFitted(icon, rect.deflate(p), "contain")
        }

        if (ctx.props.bool("highlight", false) || ctx.state == InteractionState.HOVER) {
            fillRect(rect.deflate(p), Color(0x80FFFFFF))
        }
        if (ctx.state == InteractionState.DISABLED) {
            fillRect(rect, Color(0x80000000))
        }
    }

    private fun DrawScope.drawHotbar(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val custom = ctx.textures.resolve(ctx.props.texture("texture"))
        if (custom != null) {
            drawImageFitted(custom, rect, ctx.props.string("textureFit", "stretch"))
            return
        }

        fillRect(rect, Color(ctx.props.color("background", 0xC0000000)))
        strokeRect(rect, Color(0xFF373737), ctx.px(1))

        val slots = ctx.props.int("slots", 9).coerceIn(1, 9)
        val cell = rect.width / slots
        for (index in 0 until slots) {
            val slot = Rect(
                rect.left + cell * index + ctx.px(1),
                rect.top + ctx.px(1),
                rect.left + cell * (index + 1) - ctx.px(1),
                rect.bottom - ctx.px(1),
            )
            bevelBox(slot, JavaPalette.SlotFill.copy(alpha = 0.6f), JavaPalette.SlotHighlight, JavaPalette.SlotShadow, ctx.px(1), inverted = true)
        }

        if (ctx.props.bool("showSelector", true)) {
            val index = ctx.props.int("selectedIndex", 0).coerceIn(0, slots - 1)
            val selector = Rect(
                rect.left + cell * index - ctx.px(1),
                rect.top - ctx.px(1),
                rect.left + cell * (index + 1) + ctx.px(1),
                rect.bottom + ctx.px(1),
            )
            strokeRect(selector, Color(0xFFFFFFFF), ctx.px(2))
        }
    }

    // -- Controls ----------------------------------------------------------

    private fun DrawScope.drawButton(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val enabled = ctx.props.bool("enabled", true) && ctx.state != InteractionState.DISABLED
        drawButtonSurface(ctx, rect, enabled)

        val label = ctx.props.string("label")
        if (label.isNotBlank()) {
            drawCenteredLabel(ctx, rect, label, enabled)
        }
    }

    private fun DrawScope.drawButtonSurface(ctx: ElementRenderContext, rect: Rect, enabled: Boolean) {
        val custom = ctx.textures.resolve(ctx.props.texture("texture"))
        if (custom != null) {
            val asset = ctx.project.texture(ctx.props.texture("texture"))
            drawImageFitted(custom, rect, ctx.props.string("textureFit", "nine_slice"), asset?.nineSlice ?: Insets.Zero)
            return
        }

        val base = when {
            !enabled -> JavaPalette.ButtonFillDisabled
            ctx.state == InteractionState.PRESSED -> JavaPalette.ButtonFillPressed
            ctx.state == InteractionState.HOVER -> JavaPalette.ButtonFillHover
            else -> Color(ctx.props.color("background", 0xFF6C6C6C))
        }
        val p = ctx.px(1)

        // Vanilla widgets.png is a vertical ramp with a black outline.
        drawRect(
            brush = Brush.verticalGradient(
                listOf(base.lighten(0.18f), base, base.darken(0.20f)),
            ),
            topLeft = rect.topLeft,
            size = rect.size,
        )
        fillRect(Rect(rect.left, rect.top, rect.right, rect.top + p), base.lighten(0.42f))
        fillRect(Rect(rect.left, rect.bottom - p, rect.right, rect.bottom), base.darken(0.42f))
        strokeRect(rect, Color(ctx.props.color("borderColor", 0xFF000000)), p)

        if (ctx.state == InteractionState.HOVER && enabled) {
            strokeRect(rect.deflate(p), Color(0x66FFFFFF), p)
        }
    }

    private fun DrawScope.drawCenteredLabel(
        ctx: ElementRenderContext,
        rect: Rect,
        label: String,
        enabled: Boolean,
        colorOverride: Color? = null,
    ) {
        val color = colorOverride ?: when {
            !enabled -> JavaPalette.TextDisabled
            ctx.state == InteractionState.HOVER -> Color(0xFFFFFFA0)
            else -> Color(ctx.props.color("textColor", 0xFFFFFFFF))
        }
        val style = ctx.textStyle(color).copy(textAlign = TextAlign.Center)
        val layout = ctx.textMeasurer.measure(label.replace('\n', ' '), style)
        val x = rect.left + (rect.width - layout.size.width) / 2f
        val y = rect.top + (rect.height - layout.size.height) / 2f
        drawShadowedText(
            measurer = ctx.textMeasurer,
            text = label.replace('\n', ' '),
            topLeft = Offset(x, y),
            style = style,
            shadowColor = JavaPalette.TextShadow,
            shadow = ctx.props.bool("shadow", true),
        )
    }

    private fun DrawScope.drawToggle(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val enabled = ctx.props.bool("enabled", true) && ctx.state != InteractionState.DISABLED
        val on = ctx.props.bool("value", false)

        when (ctx.props.string("style", "label_swap")) {
            "switch" -> {
                val trackWidth = ctx.px(24)
                val track = Rect(rect.right - trackWidth, rect.top + rect.height * 0.2f, rect.right, rect.bottom - rect.height * 0.2f)
                fillRect(rect, Color(0x33000000))
                bevelBox(
                    track,
                    if (on) Color(ctx.props.color("onColor", 0xFF56B84B)) else JavaPalette.ButtonFillPressed,
                    JavaPalette.ContainerHighlight, JavaPalette.ContainerShadow, ctx.px(1), inverted = true,
                )
                val knobWidth = trackWidth / 2f
                val knob = if (on) {
                    Rect(track.right - knobWidth, track.top, track.right, track.bottom)
                } else {
                    Rect(track.left, track.top, track.left + knobWidth, track.bottom)
                }
                bevelBox(knob, JavaPalette.ContainerBody, JavaPalette.ContainerHighlight, JavaPalette.ContainerShadow, ctx.px(1))
                drawShadowedText(
                    measurer = ctx.textMeasurer,
                    text = ctx.props.string("label"),
                    topLeft = Offset(rect.left + ctx.px(2), rect.top + (rect.height - ctx.px(7)) / 2f),
                    style = ctx.textStyle(if (enabled) JavaPalette.TextPrimary else JavaPalette.TextDisabled),
                    shadowColor = JavaPalette.TextShadow,
                    shadow = true,
                )
            }

            else -> {
                drawButtonSurface(ctx, rect, enabled)
                val suffix = if (on) ctx.props.string("onLabel", "ON") else ctx.props.string("offLabel", "OFF")
                val label = listOf(ctx.props.string("label"), suffix).filter { it.isNotBlank() }.joinToString(": ")
                drawCenteredLabel(
                    ctx, rect, label, enabled,
                    colorOverride = if (on) Color(ctx.props.color("onColor", 0xFF56B84B)) else null,
                )
            }
        }
    }

    private fun DrawScope.drawTab(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val selected = ctx.props.bool("selected", false)
        val base = if (selected) {
            Color(ctx.props.color("selectedColor", 0xFF8B8B8B))
        } else {
            Color(ctx.props.color("background", 0xFF5A5A5A))
        }

        // Selected tabs sit one pixel proud of the panel edge they attach to.
        val edge = ctx.props.string("edge", "top")
        val body = when (edge) {
            "bottom" -> if (selected) rect.copy(top = rect.top - ctx.px(2)) else rect
            "left" -> if (selected) rect.copy(right = rect.right + ctx.px(2)) else rect
            "right" -> if (selected) rect.copy(left = rect.left - ctx.px(2)) else rect
            else -> if (selected) rect.copy(bottom = rect.bottom + ctx.px(2)) else rect
        }

        bevelBox(body, base, base.lighten(0.35f), base.darken(0.35f), ctx.px(1))
        ctx.textures.resolve(ctx.props.texture("icon"))?.let { icon ->
            drawImageFitted(icon, body.deflate(ctx.px(6)), "contain")
        }
        val label = ctx.props.string("label")
        if (label.isNotBlank()) {
            drawCenteredLabel(ctx, body, label, enabled = true)
        }
    }

    private fun DrawScope.drawIconButton(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val enabled = ctx.props.bool("enabled", true) && ctx.state != InteractionState.DISABLED
        drawButtonSurface(ctx, rect, enabled)
        val padding = ctx.px(ctx.props.int("iconPadding", 3))
        val icon = ctx.textures.resolve(ctx.props.texture("icon"))
        if (icon != null) {
            drawImageFitted(icon, rect.deflate(padding), "contain", alpha = if (enabled) 1f else 0.4f)
        } else {
            // Empty icon buttons show a hatch so they read as "needs a texture".
            hatch(rect.deflate(padding), Color(0x55FFFFFF), ctx.px(3))
        }
    }

    // -- Text --------------------------------------------------------------

    private fun DrawScope.drawLabel(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val text = ctx.props.string("text")
        if (text.isBlank()) return
        val scaleFactor = ctx.props.float("scale", 1f)
        val style = ctx.textStyle(
            Color(ctx.props.color("textColor", 0xFFE0E0E0)),
            scaleFactor = scaleFactor,
        ).copy(
            textAlign = when (ctx.props.string("align", "left")) {
                "center" -> TextAlign.Center
                "right" -> TextAlign.End
                else -> TextAlign.Start
            },
        )
        val wrap = ctx.props.bool("wrap", false)
        drawShadowedText(
            measurer = ctx.textMeasurer,
            text = text,
            topLeft = Offset(rect.left, rect.top),
            style = style,
            shadowColor = JavaPalette.TextShadow,
            shadow = ctx.props.bool("shadow", true),
            maxWidth = if (wrap) rect.width.roundToInt() else Int.MAX_VALUE,
        )
    }

    private fun DrawScope.drawHeader(ctx: ElementRenderContext) {
        val rect = ctx.rect
        fillRect(rect, Color(ctx.props.color("background", 0x00000000)))
        var textLeft = rect.left + ctx.px(2)
        ctx.textures.resolve(ctx.props.texture("icon"))?.let { icon ->
            val iconRect = Rect(rect.left + ctx.px(2), rect.top + ctx.px(2), rect.left + rect.height - ctx.px(2), rect.bottom - ctx.px(2))
            drawImageFitted(icon, iconRect, "contain")
            textLeft = iconRect.right + ctx.px(3)
        }

        val title = ctx.props.string("title")
        val style = ctx.textStyle(Color(ctx.props.color("textColor", 0xFFE0E0E0)))
        if (title.isNotBlank()) {
            val layout = ctx.textMeasurer.measure(title, style)
            val x = when (ctx.props.string("align", "left")) {
                "center" -> rect.left + (rect.width - layout.size.width) / 2f
                "right" -> rect.right - layout.size.width - ctx.px(2)
                else -> textLeft
            }
            drawShadowedText(
                measurer = ctx.textMeasurer,
                text = title,
                topLeft = Offset(x, rect.top + (rect.height - layout.size.height) / 2f),
                style = style,
                shadowColor = JavaPalette.TextShadow,
                shadow = ctx.props.bool("shadow", true),
            )
        }

        if (ctx.props.bool("showDivider", true)) {
            fillRect(Rect(rect.left, rect.bottom - ctx.px(1), rect.right, rect.bottom), Color(ctx.props.color("dividerColor", 0xFF373737)))
        }
        if (ctx.props.bool("showCloseButton", false)) {
            val size = rect.height - ctx.px(4)
            val closeRect = Rect(rect.right - size - ctx.px(2), rect.top + ctx.px(2), rect.right - ctx.px(2), rect.bottom - ctx.px(2))
            bevelBox(closeRect, JavaPalette.ButtonFill, JavaPalette.ButtonTop, JavaPalette.ButtonBottom, ctx.px(1))
            drawLine(Color.White, closeRect.topLeft + Offset(ctx.px(2), ctx.px(2)), closeRect.bottomRight - Offset(ctx.px(2), ctx.px(2)), ctx.px(1))
            drawLine(Color.White, Offset(closeRect.right - ctx.px(2), closeRect.top + ctx.px(2)), Offset(closeRect.left + ctx.px(2), closeRect.bottom - ctx.px(2)), ctx.px(1))
        }
    }

    private fun DrawScope.drawTextBox(ctx: ElementRenderContext, showIcon: Boolean) {
        val rect = ctx.rect
        val p = ctx.px(1)
        strokeRect(rect, Color(ctx.props.color("borderColor", 0xFFA0A0A0)), p)
        fillRect(rect.deflate(p), Color(ctx.props.color("background", 0xFF000000)))

        var left = rect.left + ctx.px(4)
        if (showIcon) {
            // Magnifier glyph drawn from primitives - no icon font needed.
            val cx = rect.left + ctx.px(6)
            val cy = rect.center.y
            val r = ctx.px(3)
            drawCircle(Color(0xFFB0B0B0), r, Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(p))
            drawLine(Color(0xFFB0B0B0), Offset(cx + r * 0.7f, cy + r * 0.7f), Offset(cx + r * 1.6f, cy + r * 1.6f), p)
            left = cx + r * 2f
        }

        val value = ctx.props.string("value")
        val text = value.ifBlank { ctx.props.string("placeholder") }
        val color = if (value.isBlank()) {
            Color(ctx.props.color("placeholderColor", 0xFF707070))
        } else {
            Color(ctx.props.color("textColor", 0xFFE0E0E0))
        }
        if (text.isNotBlank()) {
            val style = ctx.textStyle(color)
            val layout = ctx.textMeasurer.measure(text, style)
            drawShadowedText(
                measurer = ctx.textMeasurer,
                text = text,
                topLeft = Offset(left, rect.top + (rect.height - layout.size.height) / 2f),
                style = style,
                shadowColor = JavaPalette.TextShadow,
                shadow = false,
            )
            if (ctx.props.bool("showCursor", true) && ctx.state == InteractionState.FOCUSED) {
                val cursorX = left + layout.size.width + ctx.px(1)
                fillRect(Rect(cursorX, rect.top + ctx.px(3), cursorX + p, rect.bottom - ctx.px(3)), Color.White)
            }
        }

        if (ctx.props.bool("showClear", false) && value.isNotBlank()) {
            val size = ctx.px(6)
            val cx = rect.right - ctx.px(6)
            val cy = rect.center.y
            drawLine(Color(0xFFB0B0B0), Offset(cx - size / 2, cy - size / 2), Offset(cx + size / 2, cy + size / 2), p)
            drawLine(Color(0xFFB0B0B0), Offset(cx + size / 2, cy - size / 2), Offset(cx - size / 2, cy + size / 2), p)
        }
    }

    private fun DrawScope.drawCheckbox(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val boxSize = ctx.px(ctx.props.int("boxSize", 12)).coerceAtMost(rect.height)
        val box = Rect(rect.left, rect.center.y - boxSize / 2, rect.left + boxSize, rect.center.y + boxSize / 2)
        val enabled = ctx.props.bool("enabled", true) && ctx.state != InteractionState.DISABLED

        bevelBox(
            box,
            Color(ctx.props.color("boxColor", 0xFF2A2A2A)),
            JavaPalette.ContainerHighlight, JavaPalette.SlotShadow,
            ctx.px(1), inverted = true,
        )

        if (ctx.props.bool("checked", false)) {
            val check = Color(ctx.props.color("checkColor", 0xFF56B84B))
            val p = ctx.px(2)
            drawLine(check, Offset(box.left + p, box.center.y), Offset(box.center.x - p * 0.2f, box.bottom - p), p)
            drawLine(check, Offset(box.center.x - p * 0.2f, box.bottom - p), Offset(box.right - p, box.top + p), p)
        }

        val label = ctx.props.string("label")
        if (label.isNotBlank()) {
            val style = ctx.textStyle(if (enabled) Color(ctx.props.color("textColor", 0xFFE0E0E0)) else JavaPalette.TextDisabled)
            val layout = ctx.textMeasurer.measure(label, style)
            drawShadowedText(
                measurer = ctx.textMeasurer,
                text = label,
                topLeft = Offset(box.right + ctx.px(4), rect.center.y - layout.size.height / 2f),
                style = style,
                shadowColor = JavaPalette.TextShadow,
                shadow = ctx.props.bool("shadow", true),
            )
        }
    }

    private fun DrawScope.drawDropdown(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val enabled = ctx.props.bool("enabled", true) && ctx.state != InteractionState.DISABLED
        drawButtonSurface(ctx, rect, enabled)

        val items = ctx.props.stringList("items")
        val index = ctx.props.int("selectedIndex", 0)
        val label = items.getOrNull(index) ?: ctx.props.string("placeholder", "Select...")
        drawCenteredLabel(ctx, rect.copy(right = rect.right - ctx.px(10)), label, enabled)

        // Chevron.
        val cx = rect.right - ctx.px(6)
        val cy = rect.center.y
        val s = ctx.px(3)
        val arrow = if (enabled) JavaPalette.TextPrimary else JavaPalette.TextDisabled
        drawLine(arrow, Offset(cx - s, cy - s / 2), Offset(cx, cy + s / 2), ctx.px(1))
        drawLine(arrow, Offset(cx, cy + s / 2), Offset(cx + s, cy - s / 2), ctx.px(1))
    }

    private fun DrawScope.drawSlider(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val enabled = ctx.props.bool("enabled", true) && ctx.state != InteractionState.DISABLED
        val p = ctx.px(1)

        // Track uses the sunken variant of the vanilla button ramp.
        bevelBox(
            rect,
            Color(ctx.props.color("trackColor", 0xFF6C6C6C)).darken(0.25f),
            JavaPalette.ButtonTop, JavaPalette.ButtonBottom, p, inverted = true,
        )
        strokeRect(rect, JavaPalette.ButtonOutline, p)

        val value = ctx.props.float("value", 0.5f).coerceIn(0f, 1f)
        val knobWidth = ctx.px(ctx.props.int("knobWidth", 8))
        val travel = (rect.width - knobWidth).coerceAtLeast(0f)
        val knob = Rect(rect.left + travel * value, rect.top, rect.left + travel * value + knobWidth, rect.bottom)
        bevelBox(
            knob,
            if (enabled) Color(ctx.props.color("knobColor", 0xFFC6C6C6)) else JavaPalette.ButtonFillDisabled,
            JavaPalette.ContainerHighlight, JavaPalette.ContainerShadow, p,
        )

        val label = ctx.props.string("label")
        val text = if (ctx.props.bool("showValue", true)) {
            val min = ctx.props.float("minValue", 0f)
            val max = ctx.props.float("maxValue", 100f)
            val display = (min + (max - min) * value).roundToInt()
            listOf(label, "$display${ctx.props.string("suffix")}").filter { it.isNotBlank() }.joinToString(": ")
        } else {
            label
        }
        if (text.isNotBlank()) drawCenteredLabel(ctx, rect, text, enabled)
    }

    // -- Feedback ----------------------------------------------------------

    private fun DrawScope.drawProgress(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val p = ctx.px(1)
        fillRect(rect, Color(ctx.props.color("background", 0xFF2A2A2A)))
        strokeRect(rect, Color(ctx.props.color("borderColor", 0xFF000000)), p)

        val progress = ctx.props.float("progress", 0f).coerceIn(0f, 1f)
        val fill = Color(ctx.props.color("fillColor", 0xFF56B84B))
        val inner = rect.deflate(p)

        if (ctx.props.bool("segmented", false)) {
            val segments = ctx.props.int("segments", 10).coerceAtLeast(2)
            val filled = (segments * progress).roundToInt()
            val cell = inner.width / segments
            for (index in 0 until filled) {
                fillRect(
                    Rect(inner.left + cell * index + p, inner.top, inner.left + cell * (index + 1) - p, inner.bottom),
                    fill,
                )
            }
        } else {
            val filled = when (ctx.props.string("direction", "right")) {
                "left" -> Rect(inner.right - inner.width * progress, inner.top, inner.right, inner.bottom)
                "up" -> Rect(inner.left, inner.bottom - inner.height * progress, inner.right, inner.bottom)
                "down" -> Rect(inner.left, inner.top, inner.right, inner.top + inner.height * progress)
                else -> Rect(inner.left, inner.top, inner.left + inner.width * progress, inner.bottom)
            }
            fillRect(filled, fill)
        }

        if (ctx.props.bool("showLabel", false)) {
            drawCenteredLabel(ctx, rect, "${(progress * 100).roundToInt()}%", enabled = true, colorOverride = Color.White)
        }
    }

    // -- Decoration --------------------------------------------------------

    private fun DrawScope.drawSeparator(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val color = Color(ctx.props.color("color", 0xFF373737))
        val highlight = Color(ctx.props.color("highlightColor", 0xFF6E6E6E))
        val vertical = ctx.props.string("orientation", "horizontal") == "vertical"
        val p = ctx.px(1)

        when (ctx.props.string("style", "bevel")) {
            "line" -> fillRect(rect, color)

            "dotted" -> {
                var offset = 0f
                val step = ctx.px(2)
                while (offset < (if (vertical) rect.height else rect.width)) {
                    val dot = if (vertical) {
                        Rect(rect.left, rect.top + offset, rect.right, rect.top + offset + p)
                    } else {
                        Rect(rect.left + offset, rect.top, rect.left + offset + p, rect.bottom)
                    }
                    fillRect(dot, color)
                    offset += step * 2
                }
            }

            "notched" -> {
                fillRect(rect, color)
                val step = ctx.px(8)
                var offset = step
                while (offset < (if (vertical) rect.height else rect.width)) {
                    val notch = if (vertical) {
                        Rect(rect.left - p, rect.top + offset, rect.right + p, rect.top + offset + p * 2)
                    } else {
                        Rect(rect.left + offset, rect.top - p, rect.left + offset + p * 2, rect.bottom + p)
                    }
                    fillRect(notch, highlight)
                    offset += step
                }
            }

            "gradient" -> drawRect(
                brush = if (vertical) {
                    Brush.verticalGradient(listOf(Color.Transparent, color, Color.Transparent))
                } else {
                    Brush.horizontalGradient(listOf(Color.Transparent, color, Color.Transparent))
                },
                topLeft = rect.topLeft,
                size = rect.size,
            )

            else -> {
                // Vanilla bevel: one dark line with a lighter line beneath.
                if (vertical) {
                    fillRect(Rect(rect.left, rect.top, rect.left + p, rect.bottom), color)
                    fillRect(Rect(rect.left + p, rect.top, rect.left + p * 2, rect.bottom), highlight)
                } else {
                    fillRect(Rect(rect.left, rect.top, rect.right, rect.top + p), color)
                    fillRect(Rect(rect.left, rect.top + p, rect.right, rect.top + p * 2), highlight)
                }
            }
        }
    }

    private fun DrawScope.drawImage(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val assetId = ctx.props.texture("texture")
        val bitmap = ctx.textures.resolve(assetId)
        if (bitmap == null) {
            checkerboard(rect, Color(ctx.props.color("placeholderColor", 0xFF404040)), Color(0xFF2E2E2E), ctx.px(4))
            strokeRect(rect, Color(0x66FFFFFF), ctx.px(1))
            return
        }
        val asset = ctx.project.texture(assetId)
        drawImageFitted(
            image = bitmap,
            dest = rect,
            fit = ctx.props.string("fit", "contain"),
            insets = asset?.nineSlice ?: Insets.Zero,
            alpha = ctx.props.float("opacity", 1f),
            pixelated = ctx.props.bool("pixelated", true),
        )
    }

    private fun DrawScope.drawUnknown(ctx: ElementRenderContext) {
        fillRect(ctx.rect, Color(0x33FF5252))
        strokeRect(ctx.rect, Color(0xFFFF5252), ctx.px(1))
        hatch(ctx.rect, Color(0x55FF5252), ctx.px(4))
    }
}

// -- Small colour helpers, local to the Java skin ---------------------------

internal fun Color.lighten(amount: Float): Color = Color(
    red = (red + (1f - red) * amount).coerceIn(0f, 1f),
    green = (green + (1f - green) * amount).coerceIn(0f, 1f),
    blue = (blue + (1f - blue) * amount).coerceIn(0f, 1f),
    alpha = alpha,
)

internal fun Color.darken(amount: Float): Color = Color(
    red = (red * (1f - amount)).coerceIn(0f, 1f),
    green = (green * (1f - amount)).coerceIn(0f, 1f),
    blue = (blue * (1f - amount)).coerceIn(0f, 1f),
    alpha = alpha,
)
