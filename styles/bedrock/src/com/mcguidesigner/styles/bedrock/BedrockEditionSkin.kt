package com.mcguidesigner.styles.bedrock

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.mcguidesigner.styles.render.EditionSkin
import com.mcguidesigner.styles.render.ElementRenderContext
import com.mcguidesigner.styles.render.checkerboard
import com.mcguidesigner.styles.render.drawAnimatedImage
import com.mcguidesigner.styles.render.drawCustomElement
import com.mcguidesigner.styles.render.drawCustomShape
import com.mcguidesigner.styles.render.drawImageFitted
import com.mcguidesigner.styles.render.drawShadowedText
import com.mcguidesigner.styles.render.fillRect
import com.mcguidesigner.styles.render.hatch
import com.mcguidesigner.styles.render.pixelCircle
import com.mcguidesigner.styles.render.pixelRoundRect
import com.mcguidesigner.styles.render.strokeRect
import com.mcguidesigner.styles.theme.SkinPalette
import kotlin.math.roundToInt

/**
 * The Bedrock Edition look: chunky 2px borders, softened corners, translucent
 * sheets, and touch controls sized for a thumb rather than a cursor.
 *
 * Deliberately shares no drawing code with the Java skin beyond the pixel
 * primitives, so the two identities can diverge freely.
 */
object BedrockEditionSkin : EditionSkin {

    override val edition = Edition.BEDROCK
    override val displayName = "Bedrock Edition"
    override val tagline = "Pocket UI - 2px borders, soft corners, thumb-sized targets"
    override val palette: SkinPalette = BedrockPalette.palette
    override val darkChrome = BedrockPalette.DarkChrome
    override val lightChrome = BedrockPalette.LightChrome

    /** Corner radius in GUI pixels, scaled to the current zoom. */
    private fun ElementRenderContext.radius(guiPixels: Int = BedrockPalette.palette.cornerRadius): Float =
        px(guiPixels)

    override fun DrawScope.drawBackdrop(rect: Rect, project: GuiProject, scale: Float) {
        when (project.canvas.backdrop) {
            CanvasBackdrop.NONE -> Unit

            CanvasBackdrop.DIM -> {
                // Bedrock dims with a soft vertical falloff rather than a flat wash.
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xCC0A0A10), Color(0xE605050A)),
                    ),
                    topLeft = rect.topLeft,
                    size = rect.size,
                )
            }

            CanvasBackdrop.SOLID -> fillRect(rect, Color(project.canvas.backdropColor))

            CanvasBackdrop.GAME_WORLD -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color(0xFF79B7E8),
                        0.58f to Color(0xFFC5DFF2),
                        0.59f to Color(0xFF6AA347),
                        1f to Color(0xFF47702F),
                    ),
                    topLeft = rect.topLeft,
                    size = rect.size,
                )
            }

            CanvasBackdrop.DIRT_PANORAMA -> {
                val tile = (16 * scale).coerceAtLeast(4f)
                checkerboard(rect, Color(0xFF5C4227), Color(0xFF533B23), tile)
                fillRect(rect, BedrockPalette.GlassOverlay)
            }
        }
    }

    override fun DrawScope.drawElement(context: ElementRenderContext) {
        if (context.rect.width < 1f || context.rect.height < 1f) return

        when (context.element.type) {
            ElementCatalog.PANEL_CHEST -> drawContainerSheet(context)
            ElementCatalog.PANEL_FRAME -> drawSheet(context)
            ElementCatalog.PANEL_TOOLTIP -> drawToast(context)
            ElementCatalog.CONTAINER_SCROLL -> drawScroll(context)
            ElementCatalog.SLOT_INVENTORY -> drawSlot(context)
            ElementCatalog.STRIP_HOTBAR -> drawHotbar(context)
            ElementCatalog.BUTTON_NORMAL -> drawButton(context)
            ElementCatalog.BUTTON_TOGGLE -> drawToggle(context)
            ElementCatalog.BUTTON_TAB -> drawTab(context)
            ElementCatalog.BUTTON_ICON -> drawIconButton(context)
            ElementCatalog.BEDROCK_TOUCHPAD -> drawTouchpad(context)
            ElementCatalog.BEDROCK_ACTION_BUTTON -> drawActionButton(context)
            ElementCatalog.TEXT_LABEL -> drawLabel(context)
            ElementCatalog.BAR_HEADER -> drawHeader(context)
            ElementCatalog.INPUT_TEXTBOX -> drawField(context, showIcon = false)
            ElementCatalog.INPUT_SEARCH -> drawField(context, showIcon = context.props.bool("showIcon", true))
            ElementCatalog.INPUT_CHECKBOX -> drawCheckbox(context)
            ElementCatalog.INPUT_DROPDOWN -> drawDropdown(context)
            ElementCatalog.INPUT_SLIDER -> drawSlider(context)
            ElementCatalog.PROGRESS_BAR -> drawProgress(context)
            ElementCatalog.DECOR_SEPARATOR -> drawSeparator(context)
            ElementCatalog.IMAGE_PLACEHOLDER -> drawImage(context)
            // User-authored artwork, with no vanilla appearance to imitate -
            // see the note on the shared renderers in `render/CustomDraw.kt`.
            ElementCatalog.IMAGE_ANIMATED -> drawAnimatedImage(context)
            ElementCatalog.SHAPE_CUSTOM -> drawCustomShape(context)
            ElementCatalog.CUSTOM_ELEMENT -> drawCustomElement(context)
            else -> drawUnsupported(context)
        }

        if (context.state == InteractionState.FOCUSED) {
            drawRect(
                color = BedrockPalette.FocusRing,
                topLeft = context.rect.inflate(context.px(2)).topLeft,
                size = context.rect.inflate(context.px(2)).size,
                style = Stroke(width = context.px(2)),
            )
        }
    }

    // -- Shared surface helpers -------------------------------------------

    /** Bedrock's signature sheet: fill, chunky border, softened corners. */
    private fun DrawScope.sheet(
        ctx: ElementRenderContext,
        rect: Rect,
        fill: Color,
        border: Color = BedrockPalette.Outline,
        radius: Float = ctx.radius(),
        borderWidth: Float = ctx.px(BedrockPalette.palette.borderWidth),
    ) {
        pixelRoundRect(rect, border, radius)
        pixelRoundRect(rect.deflate(borderWidth), fill, (radius - borderWidth).coerceAtLeast(0f))
    }

    private fun DrawScope.centeredText(
        ctx: ElementRenderContext,
        rect: Rect,
        text: String,
        color: Color,
        sizeInGuiPixels: Float = 8f,
    ) {
        if (text.isBlank()) return
        val style = ctx.textStyle(color, sizeInGuiPixels).copy(textAlign = TextAlign.Center)
        val flat = text.replace('\n', ' ')
        val layout = ctx.textMeasurer.measure(flat, style)
        drawShadowedText(
            measurer = ctx.textMeasurer,
            text = flat,
            topLeft = Offset(
                rect.left + (rect.width - layout.size.width) / 2f,
                rect.top + (rect.height - layout.size.height) / 2f,
            ),
            style = style,
            shadowColor = BedrockPalette.TextShadow,
            // Bedrock's smooth font uses a soft shadow, not Java's hard offset.
            shadow = ctx.props.bool("shadow", false),
        )
    }

    private fun ElementRenderContext.controlFill(): Color = when {
        !props.bool("enabled", true) || state == InteractionState.DISABLED -> BedrockPalette.ControlDisabled
        state == InteractionState.PRESSED -> BedrockPalette.ControlPressed
        state == InteractionState.HOVER -> BedrockPalette.ControlHover
        else -> Color(props.color("background", 0xFF3F8F3F))
    }

    // -- Containers --------------------------------------------------------

    private fun DrawScope.drawContainerSheet(ctx: ElementRenderContext) {
        val rect = ctx.rect
        ctx.textures.resolve(ctx.props.texture("texture"))?.let { bitmap ->
            val asset = ctx.project.texture(ctx.props.texture("texture"))
            drawImageFitted(bitmap, rect, ctx.props.string("textureFit", "nine_slice"), asset?.nineSlice ?: Insets.Zero)
            return
        }

        val fill = when (ctx.props.string("bedrockSkin", "classic")) {
            "pocket" -> Color(0xF2222229)
            "dark_glass" -> Color(0xCC0D0D12)
            "flat" -> Color(0xFF2A2A31)
            else -> BedrockPalette.SheetBackground
        }
        sheet(ctx, rect, fill, BedrockPalette.Outline, ctx.radius(4))

        if (ctx.props.bool("showTitle", true)) {
            val title = ctx.props.string("title")
            if (title.isNotBlank()) {
                val header = Rect(rect.left, rect.top, rect.right, rect.top + ctx.px(20))
                centeredText(ctx, header, title, Color(ctx.props.color("titleColor", 0xFFF2F2F5)), 9f)
            }
        }
    }

    private fun DrawScope.drawSheet(ctx: ElementRenderContext) {
        val rect = ctx.rect
        if (ctx.props.bool("shadow", true)) {
            // Soft ambient shadow: several decreasing-alpha rings, no blur
            // support in a pixel-exact draw path.
            val steps = 3
            for (index in steps downTo 1) {
                val spread = ctx.px(index * 2)
                pixelRoundRect(
                    rect.inflate(spread),
                    Color.Black.copy(alpha = ctx.props.float("shadowOpacity", 0.45f) / (index * 3f)),
                    ctx.radius() + spread,
                )
            }
        }

        ctx.textures.resolve(ctx.props.texture("texture"))?.let { bitmap ->
            val asset = ctx.project.texture(ctx.props.texture("texture"))
            drawImageFitted(bitmap, rect, ctx.props.string("textureFit", "nine_slice"), asset?.nineSlice ?: Insets.Zero)
            return
        }

        val radius = when (ctx.props.string("corner", "square")) {
            "rounded" -> ctx.radius(6)
            "beveled" -> ctx.radius(2)
            else -> 0f
        }
        sheet(
            ctx, rect,
            Color(ctx.props.color("background", 0xF01B1B1F)),
            Color(ctx.props.color("borderColor", 0xFF3D3D45)),
            radius,
            ctx.px(ctx.props.int("borderWidth", 2).coerceAtLeast(0)),
        )
    }

    private fun DrawScope.drawToast(ctx: ElementRenderContext) {
        val rect = ctx.rect
        // Bedrock long-press tooltips are rounded toasts, not Java's
        // gradient-bordered box.
        sheet(ctx, rect, Color(ctx.props.color("background", 0xF0100010)), BedrockPalette.BorderLight, ctx.radius(4))
        val text = ctx.props.string("text")
        if (text.isNotBlank()) {
            drawShadowedText(
                measurer = ctx.textMeasurer,
                text = text,
                topLeft = Offset(rect.left + ctx.px(6), rect.top + ctx.px(5)),
                style = ctx.textStyle(Color(ctx.props.color("textColor", 0xFFFFFFFF))),
                shadowColor = BedrockPalette.TextShadow,
                shadow = false,
                maxWidth = (rect.width - ctx.px(12)).roundToInt(),
            )
        }
        if (ctx.props.bool("longPressOnly", true)) {
            // A small "hold" pip marks touch-only tooltips in the preview.
            pixelCircle(Offset(rect.right - ctx.px(6), rect.top + ctx.px(6)), ctx.px(2), BedrockPalette.Accent, ctx.pixel)
        }
    }

    private fun DrawScope.drawScroll(ctx: ElementRenderContext) {
        val rect = ctx.rect
        sheet(ctx, rect, Color(ctx.props.color("background", 0x40000000)), BedrockPalette.Outline, ctx.radius())

        if (ctx.props.bool("showScrollbar", true)) {
            val width = ctx.px(ctx.props.int("scrollbarWidth", 6))
            val inset = ctx.px(2)
            val track = Rect(rect.right - width - inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
            // Bedrock scrollbars are a floating pill with no visible track.
            pixelRoundRect(
                ctx.scrollThumb(track),
                Color(ctx.props.color("thumbColor", 0xFFC6C6C6)).copy(alpha = 0.85f),
                width / 2f,
            )
        }
    }

    // -- Inventory ---------------------------------------------------------

    private fun DrawScope.drawSlot(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val fill = Color(ctx.props.color("slotColor", 0xFF3A3A42))
        sheet(ctx, rect, fill, BedrockPalette.SlotShadow, ctx.radius(2), ctx.px(1))

        ctx.textures.resolve(ctx.props.texture("itemIcon"))?.let { icon ->
            drawImageFitted(icon, rect.deflate(ctx.px(2)), "contain")
        }
        if (ctx.props.bool("highlight", false) || ctx.state == InteractionState.HOVER) {
            pixelRoundRect(rect.deflate(ctx.px(1)), BedrockPalette.Accent.copy(alpha = 0.35f), ctx.radius(2))
        }
        if (ctx.state == InteractionState.DISABLED) {
            pixelRoundRect(rect, Color(0x99000000), ctx.radius(2))
        }
    }

    private fun DrawScope.drawHotbar(ctx: ElementRenderContext) {
        val rect = ctx.rect
        ctx.textures.resolve(ctx.props.texture("texture"))?.let { bitmap ->
            drawImageFitted(bitmap, rect, ctx.props.string("textureFit", "stretch"))
            return
        }

        sheet(ctx, rect, Color(ctx.props.color("background", 0xC0000000)), BedrockPalette.Outline, ctx.radius(3))
        val slots = ctx.props.int("slots", 9).coerceIn(1, 9)
        val cell = rect.width / slots
        for (index in 0 until slots) {
            val slot = Rect(
                rect.left + cell * index + ctx.px(2),
                rect.top + ctx.px(2),
                rect.left + cell * (index + 1) - ctx.px(2),
                rect.bottom - ctx.px(2),
            )
            pixelRoundRect(slot, BedrockPalette.SlotFill.copy(alpha = 0.55f), ctx.radius(2))
        }
        if (ctx.props.bool("showSelector", true)) {
            val index = ctx.props.int("selectedIndex", 0).coerceIn(0, slots - 1)
            val selector = Rect(rect.left + cell * index, rect.top, rect.left + cell * (index + 1), rect.bottom)
            // Bedrock highlights the active slot with an accent ring.
            pixelRoundRect(selector, BedrockPalette.Accent, ctx.radius(3))
            pixelRoundRect(selector.deflate(ctx.px(2)), Color.Transparent, ctx.radius(2))
            strokeRect(selector.deflate(ctx.px(2)), BedrockPalette.SlotFill.copy(alpha = 0.55f), ctx.px(1))
        }
    }

    // -- Controls ----------------------------------------------------------

    private fun DrawScope.drawButton(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val enabled = ctx.props.bool("enabled", true) && ctx.state != InteractionState.DISABLED
        val fill = ctx.controlFill()

        ctx.textures.resolve(ctx.props.texture("texture"))?.let { bitmap ->
            val asset = ctx.project.texture(ctx.props.texture("texture"))
            drawImageFitted(bitmap, rect, ctx.props.string("textureFit", "nine_slice"), asset?.nineSlice ?: Insets.Zero)
            drawButtonLabel(ctx, rect, enabled)
            return
        }

        // Pressed touch buttons sink by a pixel instead of changing shade only.
        val body = if (ctx.state == InteractionState.PRESSED) rect.translate(0f, ctx.px(1)) else rect
        sheet(ctx, body, fill, fill.copy(alpha = 0.5f), ctx.radius(4), ctx.px(2))
        // Top sheen: Bedrock buttons have a subtle glossy highlight.
        if (enabled) {
            drawRect(
                brush = Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x00FFFFFF))),
                topLeft = body.deflate(ctx.px(2)).topLeft,
                size = androidx.compose.ui.geometry.Size(body.width - ctx.px(4), body.height * 0.45f),
            )
        }
        drawButtonLabel(ctx, body, enabled)
    }

    private fun DrawScope.drawButtonLabel(ctx: ElementRenderContext, rect: Rect, enabled: Boolean) {
        val label = ctx.props.string("label")
        if (label.isBlank()) return
        centeredText(
            ctx, rect, label,
            if (enabled) Color(ctx.props.color("textColor", 0xFFF2F2F5)) else BedrockPalette.TextDisabled,
        )
    }

    private fun DrawScope.drawToggle(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val on = ctx.props.bool("value", false)
        val enabled = ctx.props.bool("enabled", true) && ctx.state != InteractionState.DISABLED

        when (ctx.props.string("style", "label_swap")) {
            "label_swap" -> {
                sheet(ctx, rect, ctx.controlFill(), BedrockPalette.Outline, ctx.radius(4))
                val suffix = if (on) ctx.props.string("onLabel", "ON") else ctx.props.string("offLabel", "OFF")
                centeredText(
                    ctx, rect,
                    listOf(ctx.props.string("label"), suffix).filter { it.isNotBlank() }.joinToString(": "),
                    if (enabled) BedrockPalette.TextPrimary else BedrockPalette.TextDisabled,
                )
            }

            else -> {
                // Pill switch: the Bedrock settings-screen control.
                val trackWidth = ctx.px(36).coerceAtMost(rect.width * 0.4f)
                val trackHeight = ctx.px(18).coerceAtMost(rect.height)
                val track = Rect(
                    rect.right - trackWidth,
                    rect.center.y - trackHeight / 2,
                    rect.right,
                    rect.center.y + trackHeight / 2,
                )
                pixelRoundRect(
                    track,
                    if (on) Color(ctx.props.color("onColor", 0xFF56B84B)) else BedrockPalette.SlotFill,
                    trackHeight / 2f,
                )
                val knobRadius = trackHeight / 2f - ctx.px(2)
                val knobX = if (on) track.right - knobRadius - ctx.px(2) else track.left + knobRadius + ctx.px(2)
                pixelCircle(Offset(knobX, track.center.y), knobRadius, BedrockPalette.TouchKnob, ctx.pixel)

                val label = ctx.props.string("label")
                if (label.isNotBlank()) {
                    val style = ctx.textStyle(if (enabled) BedrockPalette.TextPrimary else BedrockPalette.TextDisabled)
                    val layout = ctx.textMeasurer.measure(label, style)
                    drawShadowedText(
                        measurer = ctx.textMeasurer,
                        text = label,
                        topLeft = Offset(rect.left + ctx.px(4), rect.center.y - layout.size.height / 2f),
                        style = style,
                        shadowColor = BedrockPalette.TextShadow,
                        shadow = false,
                    )
                }
            }
        }
    }

    private fun DrawScope.drawTab(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val selected = ctx.props.bool("selected", false)
        val fill = if (selected) {
            Color(ctx.props.color("selectedColor", 0xFF8B8B8B))
        } else {
            Color(ctx.props.color("background", 0xFF5A5A5A))
        }
        sheet(ctx, rect, fill, BedrockPalette.Outline, ctx.radius(4))

        // Bedrock marks the active tab with an accent bar on the attached edge.
        if (selected) {
            val thickness = ctx.px(3)
            val bar = when (ctx.props.string("edge", "top")) {
                "bottom" -> Rect(rect.left, rect.top, rect.right, rect.top + thickness)
                "left" -> Rect(rect.right - thickness, rect.top, rect.right, rect.bottom)
                "right" -> Rect(rect.left, rect.top, rect.left + thickness, rect.bottom)
                else -> Rect(rect.left, rect.bottom - thickness, rect.right, rect.bottom)
            }
            fillRect(bar, BedrockPalette.Accent)
        }

        ctx.textures.resolve(ctx.props.texture("icon"))?.let { icon ->
            drawImageFitted(icon, rect.deflate(ctx.px(7)), "contain")
        }
        centeredText(ctx, rect, ctx.props.string("label"), BedrockPalette.TextPrimary)
    }

    private fun DrawScope.drawIconButton(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val enabled = ctx.props.bool("enabled", true) && ctx.state != InteractionState.DISABLED
        sheet(ctx, rect, ctx.controlFill(), BedrockPalette.Outline, ctx.radius(4))
        val padding = ctx.px(ctx.props.int("iconPadding", 3) + 2)
        val icon = ctx.textures.resolve(ctx.props.texture("icon"))
        if (icon != null) {
            drawImageFitted(icon, rect.deflate(padding), "contain", alpha = if (enabled) 1f else 0.4f)
        } else {
            hatch(rect.deflate(padding), Color(0x44FFFFFF), ctx.px(4))
        }
    }

    // -- Touch-only controls ----------------------------------------------

    private fun DrawScope.drawTouchpad(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val alpha = ctx.props.float("opacity", 0.75f)
        val pad = Color(ctx.props.color("background", 0x80FFFFFF)).copy(alpha = alpha)
        val knob = Color(ctx.props.color("knobColor", 0xFFEDEDED)).copy(alpha = alpha)

        when (ctx.props.string("layout", "dpad")) {
            "joystick" -> {
                val radius = minOf(rect.width, rect.height) / 2f
                pixelCircle(rect.center, radius, pad.copy(alpha = alpha * 0.45f), ctx.pixel)
                drawCircle(knob.copy(alpha = alpha * 0.7f), radius, rect.center, style = Stroke(ctx.px(2)))
                pixelCircle(rect.center, radius * 0.38f, knob, ctx.pixel)
                // Dead-zone ring, so the designer can see what they configured.
                val dead = radius * (ctx.props.int("deadZone", 12) / 100f)
                if (dead > 1f) {
                    drawCircle(BedrockPalette.Accent.copy(alpha = 0.5f), dead, rect.center, style = Stroke(ctx.px(1)))
                }
            }

            "split" -> {
                val half = rect.width / 2f
                pixelRoundRect(Rect(rect.left, rect.top, rect.left + half - ctx.px(2), rect.bottom), pad, ctx.radius(6))
                pixelRoundRect(Rect(rect.left + half + ctx.px(2), rect.top, rect.right, rect.bottom), pad, ctx.radius(6))
                drawArrow(ctx, Rect(rect.left, rect.top, rect.left + half, rect.bottom), "left", knob)
                drawArrow(ctx, Rect(rect.left + half, rect.top, rect.right, rect.bottom), "right", knob)
            }

            else -> {
                // Classic D-pad: a plus made of three-by-three cells.
                val cell = minOf(rect.width, rect.height) / 3f
                val cx = rect.center.x
                val cy = rect.center.y
                val keys = listOf(
                    "up" to Rect(cx - cell / 2, cy - cell * 1.5f, cx + cell / 2, cy - cell / 2),
                    "down" to Rect(cx - cell / 2, cy + cell / 2, cx + cell / 2, cy + cell * 1.5f),
                    "left" to Rect(cx - cell * 1.5f, cy - cell / 2, cx - cell / 2, cy + cell / 2),
                    "right" to Rect(cx + cell / 2, cy - cell / 2, cx + cell * 1.5f, cy + cell / 2),
                )
                keys.forEach { (direction, keyRect) ->
                    pixelRoundRect(keyRect, pad, ctx.radius(3))
                    drawArrow(ctx, keyRect, direction, knob)
                }
                pixelRoundRect(
                    Rect(cx - cell / 2, cy - cell / 2, cx + cell / 2, cy + cell / 2),
                    pad.copy(alpha = alpha * 0.6f),
                    ctx.radius(2),
                )
            }
        }
    }

    private fun DrawScope.drawArrow(ctx: ElementRenderContext, rect: Rect, direction: String, color: Color) {
        val size = minOf(rect.width, rect.height) * 0.28f
        val cx = rect.center.x
        val cy = rect.center.y
        val width = ctx.px(2)
        when (direction) {
            "up" -> {
                drawLine(color, Offset(cx - size, cy + size / 2), Offset(cx, cy - size / 2), width)
                drawLine(color, Offset(cx, cy - size / 2), Offset(cx + size, cy + size / 2), width)
            }

            "down" -> {
                drawLine(color, Offset(cx - size, cy - size / 2), Offset(cx, cy + size / 2), width)
                drawLine(color, Offset(cx, cy + size / 2), Offset(cx + size, cy - size / 2), width)
            }

            "left" -> {
                drawLine(color, Offset(cx + size / 2, cy - size), Offset(cx - size / 2, cy), width)
                drawLine(color, Offset(cx - size / 2, cy), Offset(cx + size / 2, cy + size), width)
            }

            else -> {
                drawLine(color, Offset(cx - size / 2, cy - size), Offset(cx + size / 2, cy), width)
                drawLine(color, Offset(cx + size / 2, cy), Offset(cx - size / 2, cy + size), width)
            }
        }
    }

    private fun DrawScope.drawActionButton(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val alpha = ctx.props.float("opacity", 0.85f)
        val enabled = ctx.props.bool("enabled", true) && ctx.state != InteractionState.DISABLED
        val fill = Color(ctx.props.color("background", 0x99FFFFFF))
            .copy(alpha = if (enabled) alpha else alpha * 0.4f)
        val pressed = ctx.state == InteractionState.PRESSED

        when (ctx.props.string("shape", "circle")) {
            "square" -> sheet(ctx, rect, fill, Color(0x33000000), 0f)
            "rounded" -> sheet(ctx, rect, fill, Color(0x33000000), ctx.radius(6))
            else -> {
                val radius = minOf(rect.width, rect.height) / 2f * (if (pressed) 0.94f else 1f)
                pixelCircle(rect.center, radius, fill, ctx.pixel)
                drawCircle(Color(0x44000000), radius, rect.center, style = Stroke(ctx.px(2)))
            }
        }

        val icon = ctx.textures.resolve(ctx.props.texture("icon"))
        if (icon != null) {
            drawImageFitted(icon, rect.deflate(ctx.px(8)), "contain", alpha = if (enabled) 1f else 0.4f)
        }
        val label = ctx.props.string("label")
        if (label.isNotBlank()) {
            centeredText(ctx, rect, label, Color(ctx.props.color("iconTint", 0xFF202020)), 9f)
        } else if (icon == null) {
            hatch(rect.deflate(ctx.px(10)), Color(0x33000000), ctx.px(4))
        }
    }

    // -- Text --------------------------------------------------------------

    private fun DrawScope.drawLabel(ctx: ElementRenderContext) {
        val text = ctx.props.string("text")
        if (text.isBlank()) return
        val style = ctx.textStyle(
            Color(ctx.props.color("textColor", 0xFFE0E0E0)),
            scaleFactor = ctx.props.float("scale", 1f),
        ).copy(
            textAlign = when (ctx.props.string("align", "left")) {
                "center" -> TextAlign.Center
                "right" -> TextAlign.End
                else -> TextAlign.Start
            },
        )
        drawShadowedText(
            measurer = ctx.textMeasurer,
            text = text,
            topLeft = Offset(ctx.rect.left, ctx.rect.top),
            style = style,
            shadowColor = BedrockPalette.TextShadow,
            shadow = ctx.props.bool("shadow", false),
            maxWidth = if (ctx.props.bool("wrap", false)) ctx.rect.width.roundToInt() else Int.MAX_VALUE,
        )
    }

    private fun DrawScope.drawHeader(ctx: ElementRenderContext) {
        val rect = ctx.rect
        fillRect(rect, Color(ctx.props.color("background", 0x00000000)))
        var textLeft = rect.left + ctx.px(4)
        ctx.textures.resolve(ctx.props.texture("icon"))?.let { icon ->
            val iconRect = Rect(rect.left + ctx.px(4), rect.top + ctx.px(3), rect.left + rect.height - ctx.px(3), rect.bottom - ctx.px(3))
            drawImageFitted(icon, iconRect, "contain")
            textLeft = iconRect.right + ctx.px(4)
        }

        val title = ctx.props.string("title")
        if (title.isNotBlank()) {
            val style = ctx.textStyle(Color(ctx.props.color("textColor", 0xFFE0E0E0)), 9f)
            val layout = ctx.textMeasurer.measure(title, style)
            val x = when (ctx.props.string("align", "left")) {
                "center" -> rect.left + (rect.width - layout.size.width) / 2f
                "right" -> rect.right - layout.size.width - ctx.px(4)
                else -> textLeft
            }
            drawShadowedText(
                measurer = ctx.textMeasurer,
                text = title,
                topLeft = Offset(x, rect.top + (rect.height - layout.size.height) / 2f),
                style = style,
                shadowColor = BedrockPalette.TextShadow,
                shadow = false,
            )
        }

        if (ctx.props.bool("showDivider", true)) {
            fillRect(
                Rect(rect.left, rect.bottom - ctx.px(1), rect.right, rect.bottom),
                Color(ctx.props.color("dividerColor", 0xFF373737)),
            )
        }
        if (ctx.props.bool("showCloseButton", false)) {
            // Bedrock's close affordance is a large round X in the corner.
            val radius = (rect.height - ctx.px(6)) / 2f
            val center = Offset(rect.right - radius - ctx.px(3), rect.center.y)
            pixelCircle(center, radius, BedrockPalette.SheetRaised, ctx.pixel)
            val arm = radius * 0.45f
            drawLine(BedrockPalette.TextPrimary, center + Offset(-arm, -arm), center + Offset(arm, arm), ctx.px(2))
            drawLine(BedrockPalette.TextPrimary, center + Offset(arm, -arm), center + Offset(-arm, arm), ctx.px(2))
        }
    }

    private fun DrawScope.drawField(ctx: ElementRenderContext, showIcon: Boolean) {
        val rect = ctx.rect
        sheet(
            ctx, rect,
            Color(ctx.props.color("background", 0xFF000000)),
            Color(ctx.props.color("borderColor", 0xFFA0A0A0)),
            ctx.radius(4),
        )

        var left = rect.left + ctx.px(6)
        if (showIcon) {
            val cx = rect.left + ctx.px(9)
            val cy = rect.center.y
            val r = ctx.px(4)
            drawCircle(BedrockPalette.TextSecondary, r, Offset(cx, cy), style = Stroke(ctx.px(2)))
            drawLine(BedrockPalette.TextSecondary, Offset(cx + r * 0.7f, cy + r * 0.7f), Offset(cx + r * 1.7f, cy + r * 1.7f), ctx.px(2))
            left = cx + r * 2.2f
        }

        val value = ctx.props.string("value")
        val text = value.ifBlank { ctx.props.string("placeholder") }
        if (text.isNotBlank()) {
            val style = ctx.textStyle(
                if (value.isBlank()) BedrockPalette.TextSecondary else Color(ctx.props.color("textColor", 0xFFE0E0E0)),
            )
            val layout = ctx.textMeasurer.measure(text, style)
            drawShadowedText(
                measurer = ctx.textMeasurer,
                text = text,
                topLeft = Offset(left, rect.center.y - layout.size.height / 2f),
                style = style,
                shadowColor = BedrockPalette.TextShadow,
                shadow = false,
            )
        }

        if (ctx.props.bool("showClear", false) && value.isNotBlank()) {
            val radius = ctx.px(5)
            val center = Offset(rect.right - radius - ctx.px(4), rect.center.y)
            pixelCircle(center, radius, BedrockPalette.SlotFill, ctx.pixel)
            val arm = radius * 0.45f
            drawLine(BedrockPalette.TextPrimary, center + Offset(-arm, -arm), center + Offset(arm, arm), ctx.px(1))
            drawLine(BedrockPalette.TextPrimary, center + Offset(arm, -arm), center + Offset(-arm, arm), ctx.px(1))
        }
    }

    private fun DrawScope.drawCheckbox(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val enabled = ctx.props.bool("enabled", true) && ctx.state != InteractionState.DISABLED
        val boxSize = ctx.px(ctx.props.int("boxSize", 12)).coerceAtMost(rect.height)
        val box = Rect(rect.left, rect.center.y - boxSize / 2, rect.left + boxSize, rect.center.y + boxSize / 2)
        val checked = ctx.props.bool("checked", false)

        sheet(
            ctx, box,
            if (checked) Color(ctx.props.color("checkColor", 0xFF56B84B)) else Color(ctx.props.color("boxColor", 0xFF2A2A2A)),
            BedrockPalette.Outline,
            ctx.radius(3),
        )
        if (checked) {
            val p = ctx.px(2)
            drawLine(Color.White, Offset(box.left + p * 1.5f, box.center.y), Offset(box.center.x - p * 0.2f, box.bottom - p * 1.5f), p)
            drawLine(Color.White, Offset(box.center.x - p * 0.2f, box.bottom - p * 1.5f), Offset(box.right - p * 1.5f, box.top + p * 1.5f), p)
        }

        val label = ctx.props.string("label")
        if (label.isNotBlank()) {
            val style = ctx.textStyle(if (enabled) Color(ctx.props.color("textColor", 0xFFE0E0E0)) else BedrockPalette.TextDisabled)
            val layout = ctx.textMeasurer.measure(label, style)
            drawShadowedText(
                measurer = ctx.textMeasurer,
                text = label,
                topLeft = Offset(box.right + ctx.px(6), rect.center.y - layout.size.height / 2f),
                style = style,
                shadowColor = BedrockPalette.TextShadow,
                shadow = false,
            )
        }
    }

    private fun DrawScope.drawDropdown(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val enabled = ctx.props.bool("enabled", true) && ctx.state != InteractionState.DISABLED
        sheet(ctx, rect, Color(ctx.props.color("background", 0xFF3A3A42)), BedrockPalette.Outline, ctx.radius(4))

        val items = ctx.props.stringList("items")
        val label = items.getOrNull(ctx.props.int("selectedIndex", 0)) ?: ctx.props.string("placeholder", "Select...")
        val style = ctx.textStyle(if (enabled) BedrockPalette.TextPrimary else BedrockPalette.TextDisabled)
        val layout = ctx.textMeasurer.measure(label, style)
        drawShadowedText(
            measurer = ctx.textMeasurer,
            text = label,
            topLeft = Offset(rect.left + ctx.px(8), rect.center.y - layout.size.height / 2f),
            style = style,
            shadowColor = BedrockPalette.TextShadow,
            shadow = false,
        )

        val cx = rect.right - ctx.px(10)
        val cy = rect.center.y
        val s = ctx.px(4)
        val arrow = if (enabled) BedrockPalette.TextPrimary else BedrockPalette.TextDisabled
        drawLine(arrow, Offset(cx - s, cy - s / 2), Offset(cx, cy + s / 2), ctx.px(2))
        drawLine(arrow, Offset(cx, cy + s / 2), Offset(cx + s, cy - s / 2), ctx.px(2))
    }

    private fun DrawScope.drawSlider(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val enabled = ctx.props.bool("enabled", true) && ctx.state != InteractionState.DISABLED
        val value = ctx.props.float("value", 0.5f).coerceIn(0f, 1f)

        // Bedrock sliders are a thin pill track with a large round knob - the
        // knob has to be thumb-sized, not the whole control.
        val trackHeight = ctx.px(6)
        val track = Rect(rect.left, rect.center.y - trackHeight / 2, rect.right, rect.center.y + trackHeight / 2)
        pixelRoundRect(track, Color(ctx.props.color("trackColor", 0xFF3A3A42)), trackHeight / 2f)
        pixelRoundRect(
            Rect(track.left, track.top, track.left + track.width * value, track.bottom),
            if (enabled) BedrockPalette.Accent else BedrockPalette.ControlDisabled,
            trackHeight / 2f,
        )

        val knobRadius = (rect.height / 2f).coerceAtMost(ctx.px(10))
        val knobX = rect.left + knobRadius + (rect.width - knobRadius * 2) * value
        pixelCircle(Offset(knobX, rect.center.y), knobRadius, Color(ctx.props.color("knobColor", 0xFFC6C6C6)), ctx.pixel)
        drawCircle(Color(0x55000000), knobRadius, Offset(knobX, rect.center.y), style = Stroke(ctx.px(1)))

        val label = ctx.props.string("label")
        if (label.isNotBlank() || ctx.props.bool("showValue", true)) {
            val min = ctx.props.float("minValue", 0f)
            val max = ctx.props.float("maxValue", 100f)
            val display = (min + (max - min) * value).roundToInt()
            val text = if (ctx.props.bool("showValue", true)) {
                listOf(label, "$display${ctx.props.string("suffix")}").filter { it.isNotBlank() }.joinToString("  ")
            } else {
                label
            }
            val style = ctx.textStyle(BedrockPalette.TextPrimary)
            val layout = ctx.textMeasurer.measure(text, style)
            drawShadowedText(
                measurer = ctx.textMeasurer,
                text = text,
                topLeft = Offset(rect.left, rect.top - layout.size.height - ctx.px(1)),
                style = style,
                shadowColor = BedrockPalette.TextShadow,
                shadow = false,
            )
        }
    }

    // -- Feedback & decoration --------------------------------------------

    private fun DrawScope.drawProgress(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val radius = (rect.height / 2f).coerceAtMost(ctx.px(4))
        pixelRoundRect(rect, Color(ctx.props.color("background", 0xFF2A2A2A)), radius)

        val progress = ctx.props.float("progress", 0f).coerceIn(0f, 1f)
        val fill = Color(ctx.props.color("fillColor", 0xFF56B84B))

        if (ctx.props.bool("segmented", false)) {
            val segments = ctx.props.int("segments", 10).coerceAtLeast(2)
            val filled = (segments * progress).roundToInt()
            val cell = rect.width / segments
            for (index in 0 until filled) {
                pixelRoundRect(
                    Rect(rect.left + cell * index + ctx.px(1), rect.top, rect.left + cell * (index + 1) - ctx.px(1), rect.bottom),
                    fill,
                    radius / 2f,
                )
            }
        } else {
            val filled = when (ctx.props.string("direction", "right")) {
                "left" -> Rect(rect.right - rect.width * progress, rect.top, rect.right, rect.bottom)
                "up" -> Rect(rect.left, rect.bottom - rect.height * progress, rect.right, rect.bottom)
                "down" -> Rect(rect.left, rect.top, rect.right, rect.top + rect.height * progress)
                else -> Rect(rect.left, rect.top, rect.left + rect.width * progress, rect.bottom)
            }
            pixelRoundRect(filled, fill, radius)
        }

        if (ctx.props.bool("showLabel", false)) {
            centeredText(ctx, rect, "${(progress * 100).roundToInt()}%", Color.White, 7f)
        }
    }

    private fun DrawScope.drawSeparator(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val color = Color(ctx.props.color("color", 0xFF373737))
        val vertical = ctx.props.string("orientation", "horizontal") == "vertical"

        when (ctx.props.string("style", "bevel")) {
            "dotted" -> {
                var offset = 0f
                val step = ctx.px(3)
                val dot = ctx.px(2)
                while (offset < (if (vertical) rect.height else rect.width)) {
                    val r = if (vertical) {
                        Rect(rect.left, rect.top + offset, rect.right, rect.top + offset + dot)
                    } else {
                        Rect(rect.left + offset, rect.top, rect.left + offset + dot, rect.bottom)
                    }
                    pixelRoundRect(r, color, dot / 2f)
                    offset += step * 2
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

            "notched" -> {
                pixelRoundRect(rect, color, rect.height / 2f)
                val step = ctx.px(10)
                var offset = step
                while (offset < (if (vertical) rect.height else rect.width)) {
                    val notch = if (vertical) {
                        Rect(rect.left, rect.top + offset, rect.right, rect.top + offset + ctx.px(3))
                    } else {
                        Rect(rect.left + offset, rect.top, rect.left + offset + ctx.px(3), rect.bottom)
                    }
                    fillRect(notch, Color(ctx.props.color("highlightColor", 0xFF6E6E6E)))
                    offset += step
                }
            }

            else -> {
                // Bedrock's rule is a single soft pill, no bevel.
                pixelRoundRect(rect, color, (rect.height / 2f).coerceAtMost(ctx.px(2)))
            }
        }
    }

    private fun DrawScope.drawImage(ctx: ElementRenderContext) {
        val rect = ctx.rect
        val assetId = ctx.props.texture("texture")
        val bitmap = ctx.textures.resolve(assetId)
        if (bitmap == null) {
            checkerboard(rect, Color(ctx.props.color("placeholderColor", 0xFF404040)), Color(0xFF2A2A31), ctx.px(4))
            strokeRect(rect, BedrockPalette.Outline, ctx.px(2))
            return
        }
        drawImageFitted(
            image = bitmap,
            dest = rect,
            fit = ctx.props.string("fit", "contain"),
            insets = ctx.project.texture(assetId)?.nineSlice ?: Insets.Zero,
            alpha = ctx.props.float("opacity", 1f),
            pixelated = ctx.props.bool("pixelated", true),
        )
    }

    private fun DrawScope.drawUnsupported(ctx: ElementRenderContext) {
        fillRect(ctx.rect, Color(0x33FF5252))
        strokeRect(ctx.rect, Color(0xFFFF5252), ctx.px(2))
        hatch(ctx.rect, Color(0x55FF5252), ctx.px(5))
    }
}
