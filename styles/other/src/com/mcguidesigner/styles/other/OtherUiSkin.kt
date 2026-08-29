package com.mcguidesigner.styles.other

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.pow
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
import com.mcguidesigner.styles.render.drawAnimatedImage
import com.mcguidesigner.styles.render.drawCustomElement
import com.mcguidesigner.styles.render.drawCustomShape
import com.mcguidesigner.styles.render.drawImageFitted
import com.mcguidesigner.styles.render.drawShadowedText
import com.mcguidesigner.styles.render.fillRect
import com.mcguidesigner.styles.theme.ChromeColors
import com.mcguidesigner.styles.theme.SkinPalette
import com.mcguidesigner.styles.theme.readableTextOn

/**
 * A flat, contemporary interface skin for everything that is not Minecraft.
 *
 * The other two skins are careful reconstructions of a game's own art, and
 * their correctness is measurable: a pixel is right or it is wrong. This one
 * has no original to be measured against, so it is held to two rules instead.
 *
 * **Everything is legible.** Not "the palette was chosen carefully" - legible
 * as an invariant, including on backgrounds this file has never seen. Element
 * colours come from whoever is holding the tool, so every label here picks
 * black or white from what it is actually being drawn on rather than from a
 * token that was right for the default. A design tool that can produce an
 * invisible button label will produce one.
 *
 * **It looks like an app.** Which is mostly type: proportional, with a real
 * weight difference between a heading and a caption. The first version of this
 * skin typeset an entire settings screen in the monospace face the Minecraft
 * skins use, because that is what the shared text helper defaults to, and the
 * result read as a terminal emulator pretending to be a phone.
 *
 * The two token sets in [OtherUiPalette] are chosen per element from the canvas
 * backdrop, so a dark screen is a dark screen throughout - dark fields, dark
 * dividers, light placeholder text - rather than light widgets with their fills
 * overridden one at a time.
 *
 * Nothing here is shared with either Minecraft skin, deliberately, so this one
 * can be redesigned freely - as it has been - without any risk of regressing
 * art that is supposed to match the game exactly.
 */
object OtherUiSkin : EditionSkin {

    override val edition = Edition.OTHER
    override val displayName = "Other UIs"
    override val tagline = "A paint canvas - layers, brushes, an eraser that mattes its edges"
    override val openLabel = "Open canvas"
    override val palette: SkinPalette = OtherUiPalette.palette
    override val darkChrome: ChromeColors = OtherUiPalette.DarkChrome
    override val lightChrome: ChromeColors = OtherUiPalette.LightChrome

    // -- Theme resolution --------------------------------------------------

    /**
     * Which token set this project's widgets are drawn from.
     *
     * Taken from the canvas rather than from a property on every element. A
     * per-element switch is a per-element mistake: one control left on the
     * wrong setting is a white row in the middle of a dark list, and there is
     * no reason a single screen would ever want both. The backdrop is already
     * the thing being designed *on*, so it is already the answer.
     */
    private fun tokensFor(project: GuiProject): OtherUiPalette.Tokens {
        val backdrop = project.canvas.backdrop
        if (backdrop != CanvasBackdrop.SOLID) return OtherUiPalette.Light
        val colour = Color(project.canvas.backdropColor)
        val luminance = 0.2126f * colour.red + 0.7152f * colour.green + 0.0722f * colour.blue
        return if (luminance < 0.45f) OtherUiPalette.Dark else OtherUiPalette.Light
    }

    private val ElementRenderContext.tokens: OtherUiPalette.Tokens get() = tokensFor(project)

    override fun DrawScope.drawBackdrop(rect: Rect, project: GuiProject, scale: Float) {
        when (project.canvas.backdrop) {
            CanvasBackdrop.NONE -> Unit

            CanvasBackdrop.DIM -> fillRect(rect, Color(0x14000000))

            CanvasBackdrop.SOLID -> fillRect(rect, Color(project.canvas.backdropColor))

            // The two Minecraft-world backdrops have no meaning here. Rather
            // than draw a sky behind a settings screen, both fall back to the
            // page grey an app would actually sit on.
            CanvasBackdrop.GAME_WORLD, CanvasBackdrop.DIRT_PANORAMA ->
                fillRect(rect, OtherUiPalette.Light.page)
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
        border: Color?,
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

    /**
     * A soft shadow under [rect], drawn as a few offset copies at low alpha.
     *
     * A real blur is not available in a DrawScope without allocating a layer
     * per element, and at the sizes a mock-up is viewed at nobody can tell the
     * difference between a blur and four stacked rounded rectangles. Without
     * one, a white card on a grey page has nothing but a hairline holding it
     * off the background, and the whole layout goes flat.
     */
    private fun DrawScope.softShadow(rect: Rect, radius: Float, tone: Color, spread: Float) {
        if (spread < 0.5f) return
        val steps = 4
        for (i in steps downTo 1) {
            val grow = spread * i / steps
            val r = Rect(
                rect.left - grow * 0.6f,
                rect.top - grow * 0.15f,
                rect.right + grow * 0.6f,
                rect.bottom + grow * 0.85f,
            )
            drawPath(
                Path().apply { addRoundRect(RoundRect(r, CornerRadius(radius + grow, radius + grow))) },
                tone.copy(alpha = tone.alpha * 0.3f / steps),
            )
        }
    }

    /** The radius for [rect], never more than half its shortest side. */
    private fun ElementRenderContext.radiusFor(rect: Rect, guiPixels: Int = 8): Float =
        px(guiPixels).coerceAtMost(minOf(rect.width, rect.height) / 2f)

    /**
     * The typeface family for this element.
     *
     * `font` is an Other-UIs-only property with four generic families on it.
     * Honouring it costs one `when` and is the difference between a property
     * the inspector offers and a property the inspector offers that does
     * nothing.
     */
    private fun ElementRenderContext.family(): FontFamily = when (props.string("font", "sans")) {
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        // No display face is bundled; the weight below is what carries it.
        "display" -> FontFamily.SansSerif
        else -> FontFamily.SansSerif
    }

    private fun ElementRenderContext.displayFace(): Boolean = props.string("font", "sans") == "display"

    /**
     * Text, positioned within [rect] and never drawn in a colour that cannot be
     * seen on [on].
     *
     * [colour] is a request, not an instruction. When it would land within a
     * hair of the background - which is what happens the moment somebody sets a
     * white background on a control whose label token is white - it is dropped
     * in favour of whichever of black or white actually reads there. Losing the
     * requested tint is a small cost; losing the label is not a cost, it is a
     * bug report.
     */
    private fun DrawScope.text(
        ctx: ElementRenderContext,
        text: String,
        rect: Rect,
        colour: Color,
        on: Color,
        sizeInGuiPixels: Float = 9f,
        align: TextAlign = TextAlign.Center,
        weight: FontWeight = FontWeight.Medium,
        inset: Float = ctx.px(12),
    ) {
        if (text.isBlank() || rect.width <= 0f) return
        val style = ctx.textStyle(
            color = legible(colour, on),
            sizeInGuiPixels = sizeInGuiPixels,
            family = ctx.family(),
            weight = if (ctx.displayFace()) heavier(weight) else weight,
        ).copy(textAlign = align)

        val available = (rect.width - inset * 2f).coerceAtLeast(1f)
        val layout = ctx.textMeasurer.measure(
            text = text,
            style = style,
            maxLines = 1,
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = available.toInt().coerceAtLeast(1)),
        )
        val x = when (align) {
            TextAlign.Start, TextAlign.Left -> rect.left + inset
            TextAlign.End, TextAlign.Right -> rect.right - inset - layout.size.width
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
            maxWidth = available.toInt().coerceAtLeast(1),
        )
    }

    private fun heavier(weight: FontWeight): FontWeight =
        FontWeight((weight.weight + 200).coerceAtMost(900))

    /**
     * [wanted] if it can be read on [background], otherwise black or white.
     *
     * The threshold is deliberately generous. Text one step off its background
     * is not a style choice anybody made on purpose; it is a colour that was
     * chosen against a different background and then reused.
     */
    private fun legible(wanted: Color, background: Color): Color {
        val a = relativeLuminance(wanted)
        val b = relativeLuminance(background)
        val contrast = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
        return if (contrast >= 2.6f) wanted else readableTextOn(background)
    }

    /** WCAG relative luminance, gamma-expanded per channel. */
    private fun relativeLuminance(c: Color): Float {
        fun channel(v: Float): Float =
            if (v <= 0.03928f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        return 0.2126f * channel(c.red) + 0.7152f * channel(c.green) + 0.0722f * channel(c.blue)
    }

    // -- Elements ----------------------------------------------------------

    private fun DrawScope.drawCard(ctx: ElementRenderContext) {
        val t = ctx.tokens
        val rect = ctx.rect
        val fill = Color(ctx.props.color("background", t.surface.value.toLong()))
        val radius = ctx.radiusFor(rect, 10)
        softShadow(rect, radius, t.shadow, ctx.px(5))
        panel(rect, fill, t.divider, radius)
        text(ctx, ctx.props.string("label", ""), rect, t.textPrimary, fill, weight = FontWeight.SemiBold)
    }

    private fun DrawScope.drawScrollArea(ctx: ElementRenderContext) {
        val t = ctx.tokens
        val rect = ctx.rect
        val radius = ctx.radiusFor(rect, 10)
        panel(rect, t.surfaceRaised, t.divider, radius)

        // A scrollbar track along the inside edge, which is what tells you this
        // is a scrolling region rather than a plain card.
        val trackWidth = ctx.px(5)
        val inset = ctx.px(5)
        val track = Rect(
            left = rect.right - inset - trackWidth,
            top = rect.top + inset,
            right = rect.right - inset,
            bottom = rect.bottom - inset,
        )
        if (track.height > 0f) {
            panel(track, t.surfaceSunken, null, trackWidth / 2f)
            panel(ctx.scrollThumb(track), t.controlOutline, null, trackWidth / 2f)
        }
    }

    /**
     * A button.
     *
     * Filled with the accent unless the element carries its own `background`,
     * in which case that is the fill and the label takes whatever colour can be
     * read on it. That last clause is the whole reason this renderer was
     * rewritten: it previously decided *primary or not* from whether a
     * background had been set, then wrote white on it either way, so setting a
     * button's background to white - the obvious thing to do on a white card -
     * produced a button with no visible label and no visible edge.
     */
    private fun DrawScope.drawButton(ctx: ElementRenderContext) {
        val t = ctx.tokens
        val rect = ctx.rect
        val custom = ctx.props.color("background", 0L)
        val enabled = ctx.state != InteractionState.DISABLED
        val radius = ctx.radiusFor(rect, 8)

        val fill = when {
            !enabled -> t.controlDisabled
            custom != 0L -> Color(custom).let {
                when (ctx.state) {
                    InteractionState.PRESSED -> it.darken(0.12f)
                    InteractionState.HOVER -> it.darken(0.05f)
                    else -> it
                }
            }
            else -> when (ctx.state) {
                InteractionState.PRESSED -> OtherUiPalette.AccentPressed
                InteractionState.HOVER -> OtherUiPalette.AccentHover
                else -> OtherUiPalette.Accent
            }
        }

        // An outline only where the fill would otherwise blend into what is
        // behind it - a pale custom fill on a card. The accent never needs one.
        val needsOutline = custom != 0L || !enabled
        softShadow(rect, radius, t.shadow, if (needsOutline) 0f else ctx.px(3))
        panel(rect, fill, if (needsOutline) t.controlOutline else null, radius)

        if (ctx.state == InteractionState.FOCUSED) {
            val ring = radius + ctx.px(3)
            drawPath(
                Path().apply { addRoundRect(RoundRect(rect.inflate(ctx.px(3)), CornerRadius(ring, ring))) },
                OtherUiPalette.Accent.copy(alpha = 0.5f),
                style = Stroke(width = ctx.px(2)),
            )
        }

        val wanted = when {
            !enabled -> t.textDisabled
            custom != 0L -> t.textPrimary
            else -> OtherUiPalette.OnAccent
        }
        text(ctx, ctx.props.string("label", "Button"), rect, wanted, fill, weight = FontWeight.SemiBold)
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
        val t = ctx.tokens
        val rect = ctx.rect
        val on = ctx.props.bool("value", false)
        val trackHeight = minOf(rect.height, ctx.px(22))
        val track = Rect(
            left = rect.left,
            top = rect.center.y - trackHeight / 2f,
            right = rect.left + trackHeight * 1.75f,
            bottom = rect.center.y + trackHeight / 2f,
        )
        panel(
            track,
            if (on) OtherUiPalette.Accent else t.surfaceSunken,
            if (on) null else t.controlOutline,
            track.height / 2f,
        )

        val knobRadius = track.height / 2f - ctx.px(2.5f)
        val knobX = if (on) track.right - knobRadius - ctx.px(2.5f) else track.left + knobRadius + ctx.px(2.5f)
        val knobCentre = Offset(knobX, track.center.y)
        drawCircle(t.shadow.copy(alpha = 0.25f), radius = knobRadius, center = knobCentre.copy(y = knobCentre.y + ctx.px(1)))
        drawCircle(Color.White, radius = knobRadius, center = knobCentre)

        val label = ctx.props.string("label", "")
        if (label.isNotBlank() && rect.right > track.right) {
            text(
                ctx,
                label,
                Rect(track.right, rect.top, rect.right, rect.bottom),
                t.textPrimary,
                t.surface,
                align = TextAlign.Start,
                weight = FontWeight.Normal,
            )
        }
    }

    private fun DrawScope.drawTab(ctx: ElementRenderContext) {
        val t = ctx.tokens
        val rect = ctx.rect
        val selected = ctx.props.bool("selected", false)
        text(
            ctx,
            ctx.props.string("label", "Tab"),
            rect,
            if (selected) OtherUiPalette.Accent else t.textSecondary,
            t.surface,
            weight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            inset = ctx.px(6),
        )
        // An underline rather than a raised tab shape - the flat convention -
        // inset a little and rounded, which is what stops it reading as a
        // bottom border on a box.
        if (selected) {
            val thickness = ctx.px(2.5f)
            val inset = rect.width * 0.14f
            panel(
                Rect(rect.left + inset, rect.bottom - thickness, rect.right - inset, rect.bottom),
                OtherUiPalette.Accent,
                null,
                thickness / 2f,
            )
        }
    }

    /**
     * An icon button: a round hit area, a glyph, and nothing else at rest.
     *
     * `background` is honoured rather than ignored, and defaults to transparent
     * in this edition - see the two declarations of it in the catalog. Both the
     * icon and its tint come from the properties that actually exist on the
     * widget; an earlier version of this renderer read a `glyph` key that no
     * definition declares, so every icon button in every Other UIs project drew
     * the same fallback dot no matter what was set on it.
     */
    private fun DrawScope.drawIconButton(ctx: ElementRenderContext) {
        val t = ctx.tokens
        val rect = ctx.rect
        val declared = Color(ctx.props.color("background", 0L))
        val base = if (declared.alpha == 0f) Color.Transparent else declared
        val fill = when (ctx.state) {
            InteractionState.DISABLED -> base
            InteractionState.PRESSED -> if (base == Color.Transparent) t.controlPressed else base.darken(0.12f)
            InteractionState.HOVER -> if (base == Color.Transparent) t.controlHover else base.darken(0.05f)
            else -> base
        }
        if (fill != Color.Transparent) {
            panel(rect, fill, null, minOf(rect.width, rect.height) / 2f)
        }

        val behind = if (fill == Color.Transparent) t.surface else fill
        val bitmap = ctx.textures.resolve(ctx.props.texture("icon"))
        if (bitmap != null) {
            val pad = ctx.px(ctx.props.int("iconPadding", 3))
            val box = rect.deflate(pad.coerceAtMost(minOf(rect.width, rect.height) / 2.5f))
            drawImageFitted(
                image = bitmap,
                dest = box,
                fit = "contain",
                alpha = if (ctx.state == InteractionState.DISABLED) 0.4f else 1f,
                pixelated = false,
            )
            return
        }

        // No icon assigned: a filled dot at the icon's own tint, which is at
        // least an honest placeholder for "an icon goes here".
        val tint = Color(ctx.props.color("iconTint", t.textSecondary.value.toLong()))
        drawCircle(
            legible(tint, behind),
            radius = minOf(rect.width, rect.height) * 0.18f,
            center = rect.center,
        )
    }

    private fun DrawScope.drawLabel(ctx: ElementRenderContext) {
        val t = ctx.tokens
        val rect = ctx.rect
        val body = ctx.props.string("text", "Label")
        val wanted = Color(ctx.props.color("textColor", t.textPrimary.value.toLong()))
        val align = when (ctx.props.string("align", "left")) {
            "center" -> TextAlign.Center
            "right" -> TextAlign.End
            else -> TextAlign.Start
        }
        val scale = ctx.props.float("scale", 1f)
        // Bigger text is heavier text. A 2x label at the same weight as the
        // caption under it is a bigger caption, not a heading, and a screen
        // full of those has no hierarchy at all.
        val weight = when {
            scale >= 1.6f -> FontWeight.Bold
            scale >= 1.25f -> FontWeight.SemiBold
            else -> FontWeight.Normal
        }
        val style = ctx.textStyle(
            color = legible(wanted, t.surface),
            sizeInGuiPixels = scale * 9f,
            family = ctx.family(),
            weight = if (ctx.displayFace()) heavier(weight) else weight,
        ).copy(textAlign = align)
        drawShadowedText(
            measurer = ctx.textMeasurer,
            text = body,
            topLeft = Offset(rect.left, rect.top),
            style = style,
            shadowColor = OtherUiPalette.TextShadow,
            shadow = false,
            maxWidth = rect.width.toInt().coerceAtLeast(1),
        )
    }

    /**
     * The bar across the top of a screen.
     *
     * With a back chevron and an overflow affordance when there is room for
     * them, because a bare title on a strip is a strip with a title on it - the
     * two glyphs at the ends are most of what makes it read as an app bar.
     */
    private fun DrawScope.drawAppBar(ctx: ElementRenderContext) {
        val t = ctx.tokens
        val rect = ctx.rect
        fillRect(rect, t.surface)
        // A hairline under the bar rather than a bevel: the flat way of saying
        // "this sits above the content".
        fillRect(Rect(rect.left, rect.bottom - ctx.px(1), rect.right, rect.bottom), t.divider)

        val roomy = rect.width > ctx.px(120)
        var textLeft = rect.left
        if (roomy) {
            val cx = rect.left + ctx.px(18)
            val cy = rect.center.y
            val s = ctx.px(5)
            val w = ctx.px(2)
            drawLine(t.textPrimary, Offset(cx + s * 0.5f, cy - s), Offset(cx - s * 0.5f, cy), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(t.textPrimary, Offset(cx - s * 0.5f, cy), Offset(cx + s * 0.5f, cy + s), strokeWidth = w, cap = StrokeCap.Round)
            textLeft = rect.left + ctx.px(20)

            val dot = ctx.px(1.6f)
            repeat(3) { i ->
                drawCircle(
                    t.textSecondary,
                    radius = dot,
                    center = Offset(rect.right - ctx.px(18), cy + (i - 1) * ctx.px(6)),
                )
            }
        }

        text(
            ctx,
            ctx.props.string("title", "Title"),
            Rect(textLeft, rect.top, rect.right - if (roomy) ctx.px(30) else 0f, rect.bottom),
            t.textPrimary,
            t.surface,
            sizeInGuiPixels = 11f,
            align = TextAlign.Start,
            weight = FontWeight.SemiBold,
        )
    }

    private fun DrawScope.drawField(ctx: ElementRenderContext, showIcon: Boolean) {
        val t = ctx.tokens
        val rect = ctx.rect
        val focused = ctx.state == InteractionState.FOCUSED
        // Filled rather than outlined-on-white. An empty outlined box on a white
        // card is four hairlines; a filled one is unmistakably somewhere to
        // type, which is the entire job of the control in a mock-up.
        val fill = if (t.dark) t.surfaceSunken else t.surfaceRaised
        panel(
            rect,
            fill,
            if (focused) OtherUiPalette.Accent else t.controlOutline,
            ctx.radiusFor(rect, 8),
            borderWidth = if (focused) ctx.px(2) else ctx.px(1),
        )

        var textLeft = rect.left
        if (showIcon) {
            val r = ctx.px(5)
            val centre = Offset(rect.left + ctx.px(14), rect.center.y)
            drawCircle(t.textSecondary, radius = r, center = centre, style = Stroke(width = ctx.px(1.8f)))
            drawLine(
                t.textSecondary,
                start = Offset(centre.x + r * 0.7f, centre.y + r * 0.7f),
                end = Offset(centre.x + r * 1.7f, centre.y + r * 1.7f),
                strokeWidth = ctx.px(1.8f),
                cap = StrokeCap.Round,
            )
            textLeft = rect.left + ctx.px(12)
        }

        val value = ctx.props.string("value", "")
        val placeholder = ctx.props.string("placeholder", "")
        val body = value.ifBlank { placeholder }
        text(
            ctx,
            body,
            Rect(textLeft, rect.top, rect.right, rect.bottom),
            if (value.isBlank()) t.textDisabled else t.textPrimary,
            fill,
            align = TextAlign.Start,
            weight = FontWeight.Normal,
        )
    }

    private fun DrawScope.drawCheckbox(ctx: ElementRenderContext) {
        val t = ctx.tokens
        val rect = ctx.rect
        val checked = ctx.props.bool("checked", false)
        val side = minOf(rect.height, ctx.px(20))
        val box = Rect(rect.left, rect.center.y - side / 2f, rect.left + side, rect.center.y + side / 2f)

        panel(
            box,
            if (checked) OtherUiPalette.Accent else if (t.dark) t.surfaceSunken else t.surface,
            if (checked) null else t.controlOutline,
            ctx.px(5),
            borderWidth = ctx.px(1.5f),
        )
        if (checked) {
            val w = ctx.px(2.2f)
            drawLine(
                Color.White,
                Offset(box.left + side * 0.26f, box.center.y + side * 0.02f),
                Offset(box.left + side * 0.44f, box.bottom - side * 0.28f),
                strokeWidth = w,
                cap = StrokeCap.Round,
            )
            drawLine(
                Color.White,
                Offset(box.left + side * 0.44f, box.bottom - side * 0.28f),
                Offset(box.right - side * 0.22f, box.top + side * 0.3f),
                strokeWidth = w,
                cap = StrokeCap.Round,
            )
        }

        val label = ctx.props.string("label", "")
        if (label.isNotBlank()) {
            text(
                ctx,
                label,
                Rect(box.right, rect.top, rect.right, rect.bottom),
                t.textPrimary,
                t.surface,
                align = TextAlign.Start,
                weight = FontWeight.Normal,
                inset = ctx.px(9),
            )
        }
    }

    private fun DrawScope.drawSelect(ctx: ElementRenderContext) {
        val t = ctx.tokens
        val rect = ctx.rect
        val fill = if (t.dark) t.surfaceSunken else t.surfaceRaised
        panel(rect, fill, t.controlOutline, ctx.radiusFor(rect, 8))

        val options = ctx.props.stringList("items")
        val index = ctx.props.int("selectedIndex", 0)
        val label = options.getOrNull(index) ?: ctx.props.string("value", "Select")
        text(
            ctx,
            label,
            Rect(rect.left, rect.top, rect.right - ctx.px(18), rect.bottom),
            t.textPrimary,
            fill,
            align = TextAlign.Start,
            weight = FontWeight.Normal,
        )

        // A chevron, drawn rather than typed, so it does not depend on the font.
        val cx = rect.right - ctx.px(13)
        val cy = rect.center.y
        val s = ctx.px(4)
        val w = ctx.px(1.8f)
        drawLine(t.textSecondary, Offset(cx - s, cy - s / 2f), Offset(cx, cy + s / 2f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(t.textSecondary, Offset(cx, cy + s / 2f), Offset(cx + s, cy - s / 2f), strokeWidth = w, cap = StrokeCap.Round)
    }

    private fun DrawScope.drawSlider(ctx: ElementRenderContext) {
        val t = ctx.tokens
        val rect = ctx.rect
        val fraction = ctx.props.float("value", 0.5f).coerceIn(0f, 1f)
        val knob = minOf(ctx.px(9), rect.height / 2f)
        val trackHeight = ctx.px(5)
        // Inset by the knob radius so the handle stays inside the element at
        // both ends; a knob half outside its own bounds is the first thing that
        // goes wrong when a slider is exported.
        val track = Rect(
            rect.left + knob,
            rect.center.y - trackHeight / 2f,
            rect.right - knob,
            rect.center.y + trackHeight / 2f,
        )
        if (track.width <= 0f) return
        panel(track, t.surfaceSunken, null, trackHeight / 2f)
        if (fraction > 0f) {
            panel(
                Rect(track.left, track.top, track.left + track.width * fraction, track.bottom),
                OtherUiPalette.Accent,
                null,
                trackHeight / 2f,
            )
        }

        val cx = track.left + track.width * fraction
        val centre = Offset(cx, track.center.y)
        drawCircle(t.shadow.copy(alpha = 0.3f), radius = knob, center = centre.copy(y = centre.y + ctx.px(1)))
        drawCircle(Color.White, radius = knob, center = centre)
        drawCircle(OtherUiPalette.Accent, radius = knob - ctx.px(1), center = centre, style = Stroke(width = ctx.px(2.5f)))
    }

    private fun DrawScope.drawProgress(ctx: ElementRenderContext) {
        val t = ctx.tokens
        val rect = ctx.rect
        val fraction = ctx.props.float("progress", 0.4f).coerceIn(0f, 1f)
        val radius = rect.height / 2f
        panel(rect, t.surfaceSunken, null, radius)
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
        val t = ctx.tokens
        val rect = ctx.rect
        val colour = Color(ctx.props.color("color", t.divider.value.toLong()))
        val thickness = ctx.px(1).coerceAtLeast(1f)
        if (rect.width >= rect.height) {
            fillRect(
                Rect(rect.left, rect.center.y - thickness / 2f, rect.right, rect.center.y + thickness / 2f),
                colour,
            )
        } else {
            fillRect(
                Rect(rect.center.x - thickness / 2f, rect.top, rect.center.x + thickness / 2f, rect.bottom),
                colour,
            )
        }
    }

    private fun DrawScope.drawImage(ctx: ElementRenderContext) {
        val t = ctx.tokens
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
        panel(rect, t.surfaceSunken, t.divider, ctx.radiusFor(rect, 10))
        val s = minOf(rect.width, rect.height) * 0.22f
        val c = rect.center
        drawCircle(t.controlOutline, radius = s * 0.42f, center = Offset(c.x - s * 0.5f, c.y - s * 0.35f))
        val hill = Path().apply {
            moveTo(c.x - s, c.y + s * 0.7f)
            lineTo(c.x - s * 0.1f, c.y - s * 0.2f)
            lineTo(c.x + s * 0.55f, c.y + s * 0.35f)
            lineTo(c.x + s, c.y - s * 0.05f)
            lineTo(c.x + s, c.y + s * 0.7f)
            close()
        }
        drawPath(hill, t.controlOutline)
    }

    /** Towards black by [amount], for pressed and hovered states. */
    private fun Color.darken(amount: Float): Color = Color(
        red = red * (1f - amount),
        green = green * (1f - amount),
        blue = blue * (1f - amount),
        alpha = alpha,
    )
}
