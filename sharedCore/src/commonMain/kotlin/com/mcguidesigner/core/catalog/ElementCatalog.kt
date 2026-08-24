package com.mcguidesigner.core.catalog

import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.IntSize
import com.mcguidesigner.core.model.ShapeKind

/**
 * The registry of every widget the designer can place.
 *
 * This is the single source of truth shared by the palette, the property
 * inspector, the validator and both exporters.  Adding a widget means adding
 * one [ElementDefinition] here plus a skin in `styles/java` and/or
 * `styles/bedrock`.
 */
object ElementCatalog {

    // -- Type ids ----------------------------------------------------------
    // Kept as constants so exporters and templates never hard-code strings.

    const val PANEL_CHEST = "panel.chest"
    const val PANEL_FRAME = "panel.frame"
    const val PANEL_TOOLTIP = "panel.tooltip"
    const val CONTAINER_SCROLL = "container.scroll"

    const val SLOT_INVENTORY = "slot.inventory"
    const val STRIP_HOTBAR = "strip.hotbar"

    const val BUTTON_NORMAL = "button.normal"
    const val BUTTON_TOGGLE = "button.toggle"
    const val BUTTON_TAB = "button.tab"
    const val BUTTON_ICON = "button.icon"
    const val JAVA_RECT_BUTTON = "java.rectButton"
    const val BEDROCK_TOUCHPAD = "bedrock.touchpad"
    const val BEDROCK_ACTION_BUTTON = "bedrock.actionButton"

    const val TEXT_LABEL = "text.label"
    const val INPUT_TEXTBOX = "input.textbox"
    const val INPUT_SEARCH = "input.search"
    const val INPUT_CHECKBOX = "input.checkbox"
    const val INPUT_DROPDOWN = "input.dropdown"
    const val INPUT_SLIDER = "input.slider"

    const val PROGRESS_BAR = "progress.bar"

    const val BAR_HEADER = "bar.header"
    const val DECOR_SEPARATOR = "decor.separator"
    const val IMAGE_PLACEHOLDER = "image.placeholder"
    const val IMAGE_ANIMATED = "image.animated"

    const val SHAPE_CUSTOM = "shape.custom"
    const val CUSTOM_ELEMENT = "custom.element"

    // -- Shared property fragments ----------------------------------------

    private val tooltipProp = textProp(
        "tooltip", "Tooltip", group = PropGroup.CONTENT,
        help = "Shown on hover (Java) or long-press (Bedrock).",
    )

    private val enabledProp = boolProp("enabled", "Enabled", true)

    private val skinTextureProps = listOf(
        textureProp(
            "texture", "Custom texture",
            help = "Overrides the built-in skin with an imported image.",
        ),
        enumProp(
            "textureFit", "Texture fit",
            listOf("nine_slice", "stretch", "contain", "cover", "tile"),
            default = "nine_slice",
        ),
        colorProp("tint", "Tint", 0xFFFFFFFF),
    )

    private val alignOptions = listOf("left", "center", "right")

    private val javaFontOptions = listOf("minecraft", "uniform", "alt")
    private val bedrockFontOptions = listOf("default", "smooth", "rune", "mojangles")

    private fun labelStyleProps(stateAware: Boolean = true) = listOf(
        colorProp("textColor", "Text colour", 0xFFE0E0E0, stateAware = stateAware),
        boolProp("shadow", "Text shadow", true, group = PropGroup.APPEARANCE),
        enumProp("align", "Alignment", alignOptions, default = "center", group = PropGroup.LAYOUT),
        enumProp("font", "Font", javaFontOptions, group = PropGroup.JAVA, editions = JAVA_ONLY),
        enumProp("font", "Font", bedrockFontOptions, group = PropGroup.BEDROCK, editions = BEDROCK_ONLY),
    )

    // -- Definitions -------------------------------------------------------

    private val definitions: List<ElementDefinition> = listOf(

        ElementDefinition(
            typeId = PANEL_CHEST,
            displayName = "Chest Background Panel",
            category = ElementCategory.CONTAINERS,
            defaultSize = IntSize(176, 166),
            minSize = IntSize(48, 48),
            acceptsChildren = true,
            glyph = "▤",
            description = "Vanilla container backdrop with the player inventory strip.",
            properties = listOf(
                textProp("title", "Title", "Container"),
                intProp("rows", "Chest rows", 3, min = 1, max = 6, group = PropGroup.LAYOUT),
                boolProp("showPlayerInventory", "Show player inventory", true, group = PropGroup.LAYOUT),
                boolProp("showTitle", "Show title", true, group = PropGroup.LAYOUT),
                colorProp("titleColor", "Title colour", 0xFF404040),
                enumProp(
                    "skin", "Panel skin",
                    listOf("vanilla", "dark", "light", "smithing", "flat"),
                    group = PropGroup.APPEARANCE,
                ),
                enumProp(
                    "bedrockSkin", "Bedrock panel skin",
                    listOf("classic", "pocket", "dark_glass", "flat"),
                    group = PropGroup.BEDROCK, editions = BEDROCK_ONLY,
                ),
            ) + skinTextureProps,
        ),

        ElementDefinition(
            typeId = PANEL_FRAME,
            displayName = "Panel Frame",
            category = ElementCategory.CONTAINERS,
            defaultSize = IntSize(160, 100),
            minSize = IntSize(8, 8),
            acceptsChildren = true,
            glyph = "▣",
            description = "Generic bordered panel with an optional drop shadow.",
            properties = listOf(
                colorProp("background", "Background", 0xF0100010),
                colorProp("borderColor", "Border colour", 0xFF5A5A5A),
                intProp("borderWidth", "Border width", 1, min = 0, max = 8, group = PropGroup.APPEARANCE),
                boolProp("shadow", "Drop shadow", true, group = PropGroup.APPEARANCE),
                intProp("shadowOffset", "Shadow offset", 3, min = 0, max = 16, group = PropGroup.APPEARANCE),
                floatProp("shadowOpacity", "Shadow opacity", 0.45f, 0f, 1f),
                intProp("padding", "Content padding", 6, min = 0, max = 64, group = PropGroup.LAYOUT),
                enumProp(
                    "corner", "Corner style",
                    listOf("square", "beveled", "rounded"),
                    group = PropGroup.APPEARANCE,
                ),
            ) + skinTextureProps,
        ),

        ElementDefinition(
            typeId = PANEL_TOOLTIP,
            displayName = "Tooltip Box",
            category = ElementCategory.FEEDBACK,
            defaultSize = IntSize(120, 40),
            minSize = IntSize(16, 12),
            glyph = "🗨",
            description = "Vanilla item-tooltip frame with a gradient border.",
            properties = listOf(
                textProp("text", "Text", "Tooltip line 1\nTooltip line 2", multiline = true),
                colorProp("background", "Background", 0xF0100010),
                colorProp("borderTop", "Border (top)", 0xFF5000FF),
                colorProp("borderBottom", "Border (bottom)", 0xFF28007F),
                colorProp("textColor", "Text colour", 0xFFFFFFFF),
                boolProp("followPointer", "Follows pointer", false, editions = JAVA_ONLY, group = PropGroup.JAVA),
                boolProp("longPressOnly", "Long-press only", true, editions = BEDROCK_ONLY, group = PropGroup.BEDROCK),
            ),
        ),

        ElementDefinition(
            typeId = CONTAINER_SCROLL,
            displayName = "Scroll Container",
            category = ElementCategory.CONTAINERS,
            defaultSize = IntSize(160, 100),
            minSize = IntSize(24, 24),
            acceptsChildren = true,
            interactive = true,
            glyph = "▥",
            description = "Clipped, scrollable region with a vanilla scrollbar.",
            properties = listOf(
                enumProp("direction", "Scroll direction", listOf("vertical", "horizontal", "both"), group = PropGroup.LAYOUT),
                intProp("contentLength", "Content length", 240, min = 0, max = 8192, group = PropGroup.LAYOUT),
                boolProp("showScrollbar", "Show scrollbar", true, group = PropGroup.APPEARANCE),
                intProp("scrollbarWidth", "Scrollbar width", 6, min = 2, max = 24, group = PropGroup.APPEARANCE),
                colorProp("background", "Background", 0x40000000),
                colorProp("thumbColor", "Scrollbar thumb", 0xFFC6C6C6),
                boolProp("momentum", "Momentum scrolling", true, editions = BEDROCK_ONLY, group = PropGroup.BEDROCK),
                boolProp("mouseWheel", "Mouse wheel", true, editions = JAVA_ONLY, group = PropGroup.JAVA),
            ),
        ),

        ElementDefinition(
            typeId = SLOT_INVENTORY,
            displayName = "Inventory Slot",
            category = ElementCategory.INVENTORY,
            defaultSize = IntSize(18, 18),
            minSize = IntSize(18, 18),
            maxSize = IntSize(18, 18),
            resizable = false,
            interactive = true,
            glyph = "▪",
            description = "Single 18x18 item slot. Fixed size to stay vanilla-accurate.",
            properties = listOf(
                intProp("slotIndex", "Slot index", 0, min = 0, max = 255, group = PropGroup.BEHAVIOUR),
                textureProp("itemIcon", "Item preview", group = PropGroup.CONTENT),
                boolProp("highlight", "Highlighted", false, group = PropGroup.APPEARANCE, stateAware = true),
                colorProp("slotColor", "Slot colour", 0xFF8B8B8B),
                boolProp("acceptsInput", "Accepts input", true),
                textProp("filterTag", "Filter tag", group = PropGroup.BEHAVIOUR, help = "Item tag accepted by this slot."),
            ),
        ),

        ElementDefinition(
            typeId = STRIP_HOTBAR,
            displayName = "Hotbar Strip",
            category = ElementCategory.INVENTORY,
            defaultSize = IntSize(182, 22),
            minSize = IntSize(22, 22),
            glyph = "▭",
            description = "Row of hotbar slots with a selection cursor.",
            properties = listOf(
                intProp("slots", "Slot count", 9, min = 1, max = 9, group = PropGroup.LAYOUT),
                intProp("selectedIndex", "Selected slot", 0, min = 0, max = 8, group = PropGroup.BEHAVIOUR),
                boolProp("showSelector", "Show selector", true, group = PropGroup.APPEARANCE),
                colorProp("background", "Background", 0xC0000000),
            ) + skinTextureProps,
        ),

        ElementDefinition(
            typeId = BUTTON_NORMAL,
            displayName = "Button",
            category = ElementCategory.CONTROLS,
            defaultSize = IntSize(150, 20),
            minSize = IntSize(8, 8),
            interactive = true,
            glyph = "▬",
            description = "Standard clickable button with full state support.",
            properties = listOf(
                textProp("label", "Label", "Done", stateAware = true),
                enabledProp,
                tooltipProp,
                textProp("action", "Action id", group = PropGroup.BEHAVIOUR, help = "Handler key emitted into the export."),
                colorProp("background", "Background", 0xFF6C6C6C),
                colorProp("borderColor", "Border colour", 0xFF000000),
            ) + labelStyleProps() + skinTextureProps,
        ),

        ElementDefinition(
            typeId = BUTTON_TOGGLE,
            displayName = "Toggle Button",
            category = ElementCategory.CONTROLS,
            defaultSize = IntSize(150, 20),
            minSize = IntSize(16, 8),
            interactive = true,
            glyph = "◧",
            description = "Two-state button that swaps its label when toggled.",
            properties = listOf(
                textProp("label", "Label prefix", "Difficulty"),
                textProp("onLabel", "On label", "ON"),
                textProp("offLabel", "Off label", "OFF"),
                boolProp("value", "Toggled on", false),
                enabledProp,
                tooltipProp,
                colorProp("background", "Background", 0xFF6C6C6C),
                colorProp("onColor", "On accent", 0xFF56B84B),
                enumProp(
                    "style", "Toggle style",
                    listOf("label_swap", "switch", "pill"),
                    group = PropGroup.APPEARANCE,
                ),
            ) + labelStyleProps(),
        ),

        ElementDefinition(
            typeId = BUTTON_TAB,
            displayName = "Tab Button",
            category = ElementCategory.CONTROLS,
            defaultSize = IntSize(28, 32),
            minSize = IntSize(12, 12),
            interactive = true,
            glyph = "◤",
            description = "Creative-menu style tab, attachable to any edge.",
            properties = listOf(
                textProp("label", "Label", ""),
                textureProp("icon", "Icon", group = PropGroup.CONTENT),
                boolProp("selected", "Selected", false),
                enumProp("edge", "Attached edge", listOf("top", "bottom", "left", "right"), group = PropGroup.LAYOUT),
                intProp("groupIndex", "Tab group", 0, min = 0, max = 32, group = PropGroup.BEHAVIOUR),
                enabledProp,
                tooltipProp,
                colorProp("background", "Background", 0xFF5A5A5A),
                colorProp("selectedColor", "Selected colour", 0xFF8B8B8B),
            ) + labelStyleProps(),
        ),

        ElementDefinition(
            typeId = BUTTON_ICON,
            displayName = "Icon Button",
            category = ElementCategory.CONTROLS,
            defaultSize = IntSize(20, 20),
            minSize = IntSize(8, 8),
            interactive = true,
            keepAspect = true,
            glyph = "◈",
            description = "Square button holding an icon instead of a label.",
            properties = listOf(
                textureProp("icon", "Icon", group = PropGroup.CONTENT),
                intProp("iconPadding", "Icon padding", 3, min = 0, max = 32, group = PropGroup.LAYOUT),
                enabledProp,
                tooltipProp,
                textProp("action", "Action id", group = PropGroup.BEHAVIOUR),
                colorProp("background", "Background", 0xFF6C6C6C),
                colorProp("iconTint", "Icon tint", 0xFFFFFFFF),
            ) + skinTextureProps,
        ),

        ElementDefinition(
            typeId = JAVA_RECT_BUTTON,
            displayName = "Java Rectangular Button",
            category = ElementCategory.CONTROLS,
            defaultSize = IntSize(200, 20),
            minSize = IntSize(20, 20),
            maxSize = IntSize(400, 20),
            editions = JAVA_ONLY,
            interactive = true,
            glyph = "▭",
            description = "Vanilla widgets.png button, locked to the 20px vanilla height.",
            properties = listOf(
                textProp("label", "Label", "Options...", stateAware = true),
                enumProp("width", "Width preset", listOf("narrow", "wide", "full", "custom"), default = "wide", group = PropGroup.LAYOUT),
                enabledProp,
                tooltipProp,
                textProp("action", "Action id", group = PropGroup.BEHAVIOUR),
                boolProp("useVanillaTexture", "Use widgets.png", true, group = PropGroup.JAVA),
                colorProp("textColor", "Text colour", 0xFFFFFFFF),
            ),
        ),

        ElementDefinition(
            typeId = BEDROCK_TOUCHPAD,
            displayName = "Touchpad Button",
            category = ElementCategory.TOUCH,
            defaultSize = IntSize(90, 90),
            minSize = IntSize(48, 48),
            editions = BEDROCK_ONLY,
            interactive = true,
            keepAspect = true,
            glyph = "✥",
            description = "Bedrock D-pad / virtual joystick cluster.",
            properties = listOf(
                enumProp("layout", "Layout", listOf("dpad", "joystick", "split"), group = PropGroup.LAYOUT),
                floatProp("opacity", "Opacity", 0.75f, 0f, 1f),
                intProp("deadZone", "Dead zone %", 12, min = 0, max = 60, group = PropGroup.BEHAVIOUR),
                boolProp("hapticFeedback", "Haptic feedback", true),
                colorProp("background", "Pad colour", 0x80FFFFFF),
                colorProp("knobColor", "Knob colour", 0xFFEDEDED),
            ) + skinTextureProps,
        ),

        ElementDefinition(
            typeId = BEDROCK_ACTION_BUTTON,
            displayName = "Mobile Action Button",
            category = ElementCategory.TOUCH,
            defaultSize = IntSize(44, 44),
            minSize = IntSize(24, 24),
            editions = BEDROCK_ONLY,
            interactive = true,
            keepAspect = true,
            glyph = "◉",
            description = "Round jump/sneak/fly style touch action button.",
            properties = listOf(
                textureProp("icon", "Icon", group = PropGroup.CONTENT),
                textProp("label", "Label", ""),
                enumProp("shape", "Shape", listOf("circle", "rounded", "square"), group = PropGroup.APPEARANCE),
                floatProp("opacity", "Opacity", 0.85f, 0f, 1f),
                boolProp("repeatOnHold", "Repeat while held", false),
                boolProp("hapticFeedback", "Haptic feedback", true),
                enabledProp,
                textProp("action", "Action id", group = PropGroup.BEHAVIOUR),
                colorProp("background", "Background", 0x99FFFFFF),
                colorProp("iconTint", "Icon tint", 0xFF202020),
            ) + skinTextureProps,
        ),

        ElementDefinition(
            typeId = TEXT_LABEL,
            displayName = "Label",
            category = ElementCategory.TEXT,
            defaultSize = IntSize(80, 10),
            minSize = IntSize(4, 6),
            glyph = "T",
            description = "Single- or multi-line static text.",
            properties = listOf(
                textProp("text", "Text", "Label", multiline = true),
                floatProp("scale", "Scale", 1f, 0.25f, 4f, step = 0.05f),
                boolProp("wrap", "Wrap text", false, group = PropGroup.LAYOUT),
                intProp("lineSpacing", "Line spacing", 2, min = 0, max = 32, group = PropGroup.LAYOUT),
            ) + labelStyleProps(stateAware = false).map {
                if (it.key == "align") it.copy(default = com.mcguidesigner.core.model.EnumValue("left")) else it
            },
        ),

        ElementDefinition(
            typeId = INPUT_TEXTBOX,
            displayName = "Text Box",
            category = ElementCategory.TEXT,
            defaultSize = IntSize(120, 20),
            minSize = IntSize(16, 10),
            interactive = true,
            glyph = "▢",
            description = "Editable single-line text field.",
            properties = listOf(
                textProp("value", "Value", ""),
                textProp("placeholder", "Placeholder", "Type here..."),
                intProp("maxLength", "Max length", 32, min = 1, max = 1024, group = PropGroup.BEHAVIOUR),
                boolProp("editable", "Editable", true),
                boolProp("numericOnly", "Numeric only", false),
                colorProp("background", "Background", 0xFF000000),
                colorProp("borderColor", "Border colour", 0xFFA0A0A0, stateAware = true),
                colorProp("textColor", "Text colour", 0xFFE0E0E0),
                colorProp("placeholderColor", "Placeholder colour", 0xFF707070),
                boolProp("showCursor", "Show caret", true, group = PropGroup.APPEARANCE),
                enumProp(
                    "keyboardType", "Keyboard", listOf("text", "number", "email", "url"),
                    group = PropGroup.BEDROCK, editions = BEDROCK_ONLY,
                    help = "Soft-keyboard hint; ignored on Java.",
                ),
            ),
        ),

        ElementDefinition(
            typeId = INPUT_SEARCH,
            displayName = "Search Field",
            category = ElementCategory.TEXT,
            defaultSize = IntSize(140, 20),
            minSize = IntSize(24, 10),
            interactive = true,
            glyph = "⌕",
            description = "Text field with a leading search glyph and a clear button.",
            properties = listOf(
                textProp("value", "Value", ""),
                textProp("placeholder", "Placeholder", "Search"),
                boolProp("showIcon", "Show search icon", true, group = PropGroup.APPEARANCE),
                boolProp("showClear", "Show clear button", true, group = PropGroup.APPEARANCE),
                boolProp("liveFilter", "Filter as you type", true),
                colorProp("background", "Background", 0xFF000000),
                colorProp("borderColor", "Border colour", 0xFFA0A0A0, stateAware = true),
                colorProp("textColor", "Text colour", 0xFFE0E0E0),
            ),
        ),

        ElementDefinition(
            typeId = INPUT_CHECKBOX,
            displayName = "Checkbox",
            category = ElementCategory.CONTROLS,
            defaultSize = IntSize(80, 12),
            minSize = IntSize(10, 10),
            interactive = true,
            glyph = "☑",
            description = "Checkbox with an optional trailing label.",
            properties = listOf(
                textProp("label", "Label", "Enable feature"),
                boolProp("checked", "Checked", false),
                intProp("boxSize", "Box size", 12, min = 8, max = 48, group = PropGroup.LAYOUT),
                enabledProp,
                tooltipProp,
                colorProp("boxColor", "Box colour", 0xFF2A2A2A, stateAware = true),
                colorProp("checkColor", "Check colour", 0xFF56B84B),
            ) + labelStyleProps(stateAware = false).map {
                if (it.key == "align") it.copy(default = com.mcguidesigner.core.model.EnumValue("left")) else it
            },
        ),

        ElementDefinition(
            typeId = INPUT_DROPDOWN,
            displayName = "Dropdown",
            category = ElementCategory.CONTROLS,
            defaultSize = IntSize(120, 20),
            minSize = IntSize(24, 12),
            interactive = true,
            glyph = "▾",
            description = "Cycling / expanding option selector.",
            properties = listOf(
                stringListProp("items", "Items", listOf("Option A", "Option B", "Option C")),
                intProp("selectedIndex", "Selected index", 0, min = 0, max = 255, group = PropGroup.BEHAVIOUR),
                textProp("placeholder", "Placeholder", "Select..."),
                enabledProp,
                colorProp("background", "Background", 0xFF6C6C6C),
                colorProp("popupBackground", "Popup background", 0xF0100010),
                enumProp(
                    "openMode", "Open mode",
                    listOf("popup", "cycle"),
                    group = PropGroup.BEHAVIOUR,
                    help = "Vanilla Java options screens cycle; Bedrock uses a popup list.",
                ),
                boolProp("fullScreenPicker", "Full-screen picker", true, editions = BEDROCK_ONLY, group = PropGroup.BEDROCK),
            ) + labelStyleProps(stateAware = false),
        ),

        ElementDefinition(
            typeId = INPUT_SLIDER,
            displayName = "Slider",
            category = ElementCategory.CONTROLS,
            defaultSize = IntSize(150, 20),
            minSize = IntSize(24, 10),
            interactive = true,
            glyph = "▬",
            description = "Value slider with a draggable knob.",
            properties = listOf(
                textProp("label", "Label", "Volume"),
                floatProp("value", "Value", 0.5f, 0f, 1f, group = PropGroup.CONTENT),
                floatProp("minValue", "Minimum", 0f, group = PropGroup.CONTENT),
                floatProp("maxValue", "Maximum", 100f, group = PropGroup.CONTENT),
                floatProp("stepSize", "Step", 1f, 0f, 1000f, group = PropGroup.BEHAVIOUR),
                boolProp("showValue", "Show value", true, group = PropGroup.APPEARANCE),
                textProp("suffix", "Value suffix", "%"),
                enabledProp,
                colorProp("trackColor", "Track colour", 0xFF6C6C6C),
                colorProp("knobColor", "Knob colour", 0xFFC6C6C6, stateAware = true),
                intProp("knobWidth", "Knob width", 8, min = 4, max = 64, group = PropGroup.APPEARANCE),
            ) + labelStyleProps(stateAware = false),
        ),

        ElementDefinition(
            typeId = PROGRESS_BAR,
            displayName = "Progress Bar",
            category = ElementCategory.FEEDBACK,
            defaultSize = IntSize(100, 8),
            minSize = IntSize(6, 2),
            glyph = "▰",
            description = "Furnace/brewing style progress indicator.",
            properties = listOf(
                floatProp("progress", "Progress", 0.6f, 0f, 1f, group = PropGroup.CONTENT),
                enumProp(
                    "direction", "Fill direction",
                    listOf("right", "left", "up", "down"),
                    group = PropGroup.LAYOUT,
                ),
                boolProp("showLabel", "Show percentage", false, group = PropGroup.APPEARANCE),
                boolProp("segmented", "Segmented", false, group = PropGroup.APPEARANCE),
                intProp("segments", "Segment count", 10, min = 2, max = 64, group = PropGroup.APPEARANCE),
                colorProp("fillColor", "Fill colour", 0xFF56B84B),
                colorProp("background", "Track colour", 0xFF2A2A2A),
                colorProp("borderColor", "Border colour", 0xFF000000),
            ) + skinTextureProps,
        ),

        ElementDefinition(
            typeId = BAR_HEADER,
            displayName = "Header Bar",
            category = ElementCategory.DECORATION,
            defaultSize = IntSize(176, 18),
            minSize = IntSize(16, 8),
            glyph = "▤",
            description = "Screen title bar with optional icon and divider.",
            properties = listOf(
                textProp("title", "Title", "Inventory"),
                textureProp("icon", "Icon", group = PropGroup.CONTENT),
                boolProp("showDivider", "Show divider", true, group = PropGroup.APPEARANCE),
                boolProp("showCloseButton", "Show close button", false, group = PropGroup.APPEARANCE),
                colorProp("background", "Background", 0x00000000),
                colorProp("dividerColor", "Divider colour", 0xFF373737),
            ) + labelStyleProps(stateAware = false).map {
                if (it.key == "align") it.copy(default = com.mcguidesigner.core.model.EnumValue("left")) else it
            },
        ),

        ElementDefinition(
            typeId = DECOR_SEPARATOR,
            displayName = "Separator",
            category = ElementCategory.DECORATION,
            defaultSize = IntSize(160, 2),
            minSize = IntSize(2, 1),
            glyph = "─",
            description = "Decorative rule with vanilla bevel or notch styles.",
            properties = listOf(
                enumProp("orientation", "Orientation", listOf("horizontal", "vertical"), group = PropGroup.LAYOUT),
                enumProp("style", "Style", listOf("bevel", "line", "dotted", "notched", "gradient")),
                colorProp("color", "Colour", 0xFF373737),
                colorProp("highlightColor", "Highlight", 0xFF6E6E6E),
                intProp("thickness", "Thickness", 2, min = 1, max = 32, group = PropGroup.APPEARANCE),
            ),
        ),

        ElementDefinition(
            typeId = IMAGE_PLACEHOLDER,
            displayName = "Image / Texture Slot",
            category = ElementCategory.DECORATION,
            defaultSize = IntSize(32, 32),
            minSize = IntSize(2, 2),
            glyph = "🖼",
            description = "Drops an imported texture onto the canvas.",
            properties = listOf(
                textureProp("texture", "Texture", group = PropGroup.CONTENT, stateAware = false),
                enumProp(
                    "fit", "Fit mode",
                    listOf("stretch", "contain", "cover", "tile", "nine_slice"),
                    default = "contain",
                ),
                boolProp("keepAspect", "Lock aspect ratio", true, group = PropGroup.LAYOUT),
                boolProp("pixelated", "Nearest-neighbour", true, group = PropGroup.APPEARANCE),
                floatProp("opacity", "Opacity", 1f, 0f, 1f),
                colorProp("tint", "Tint", 0xFFFFFFFF),
                colorProp("placeholderColor", "Empty-slot colour", 0xFF404040),
                intProp(
                    "rotation", "Rotation", 0, min = 0, max = 359, group = PropGroup.LAYOUT,
                    // Was capped at 270 and described as multiples of ninety,
                    // which stopped being true the moment the canvas grew a
                    // rotation knob - and the renderer and every exporter had
                    // always accepted any angle.
                    help = "Any angle, 0 to 359.",
                ),
            ),
        ),

        // -- Animated imagery ---------------------------------------------

        ElementDefinition(
            typeId = IMAGE_ANIMATED,
            displayName = "Animated Image / GIF",
            category = ElementCategory.DECORATION,
            defaultSize = IntSize(32, 32),
            minSize = IntSize(2, 2),
            glyph = "🎞",
            description = "Plays a frame strip. Imported GIFs are converted to one automatically.",
            properties = listOf(
                textureProp(
                    "texture", "Frame strip", group = PropGroup.CONTENT, stateAware = false,
                    help = "A vertical strip of equally tall frames - exactly what Minecraft " +
                        "animated textures use. Importing a GIF builds one for you.",
                ),
                intProp(
                    "frameCount", "Frames", 0, min = 0, max = 1024, group = PropGroup.CONTENT,
                    help = "0 reads the count stored with the imported strip.",
                ),
                intProp(
                    "frameTime", "Ticks per frame", 2, min = 1, max = 600, group = PropGroup.BEHAVIOUR,
                    help = "Minecraft ticks; 20 ticks = 1 second. This is the `frametime` " +
                        "written to the texture's .mcmeta.",
                ),
                boolProp(
                    "interpolate", "Interpolate frames", false, group = PropGroup.BEHAVIOUR,
                    help = "Cross-fades between frames, as vanilla does for prismarine and clocks.",
                ),
                boolProp("loop", "Loop", true, group = PropGroup.BEHAVIOUR),
                enumProp(
                    "playback", "Playback", listOf("forward", "reverse", "ping_pong"),
                    group = PropGroup.BEHAVIOUR,
                ),
                boolProp(
                    "playing", "Play in the editor", true, group = PropGroup.BEHAVIOUR,
                    help = "Preview only - it never affects the export.",
                ),
                enumProp("fit", "Fit mode", listOf("stretch", "contain", "cover"), default = "contain"),
                boolProp("keepAspect", "Lock aspect ratio", true, group = PropGroup.LAYOUT),
                boolProp("pixelated", "Nearest-neighbour", true, group = PropGroup.APPEARANCE),
                floatProp("opacity", "Opacity", 1f, 0f, 1f),
                colorProp("tint", "Tint", 0xFFFFFFFF),
                colorProp("placeholderColor", "Empty-slot colour", 0xFF404040),
            ),
        ),

        // -- Shapes ---------------------------------------------------------

        ElementDefinition(
            typeId = SHAPE_CUSTOM,
            displayName = "Custom Shape",
            category = ElementCategory.SHAPES,
            defaultSize = IntSize(48, 48),
            minSize = IntSize(2, 2),
            glyph = "◆",
            description = "Rectangles, circles, polygons, stars, arrows and more, drawn to any size.",
            properties = listOf(
                enumProp(
                    "shape", "Shape", ShapeKind.ids, default = ShapeKind.RECTANGLE.id,
                    group = PropGroup.CONTENT,
                ),
                intProp(
                    "sides", "Sides / points", 6,
                    min = ShapeKind.MIN_SIDES, max = ShapeKind.MAX_SIDES, group = PropGroup.CONTENT,
                    help = "Used by the regular polygon and the star.",
                ),
                floatProp(
                    "innerRadius", "Star inner radius", 0.5f, 0.05f, 0.95f, group = PropGroup.CONTENT,
                    help = "How deep a star's notches cut in. Only used by the star.",
                ),
                intProp(
                    "cornerRadius", "Corner radius", 6, min = 0, max = 64, group = PropGroup.LAYOUT,
                    help = "Only used by the rounded rectangle.",
                ),
                intProp(
                    "rotation", "Rotation", 0, min = 0, max = 359, group = PropGroup.LAYOUT,
                    help = "Degrees clockwise about the centre.",
                ),
                enumProp("fillMode", "Fill", listOf("solid", "gradient", "none")),
                colorProp("fillColor", "Fill colour", 0xFF56B84B),
                colorProp("gradientColor", "Gradient end", 0xFF1E6F3A),
                intProp("gradientAngle", "Gradient angle", 90, min = 0, max = 359, group = PropGroup.APPEARANCE),
                colorProp("strokeColor", "Outline colour", 0xFF000000),
                intProp("strokeWidth", "Outline width", 1, min = 0, max = 32, group = PropGroup.APPEARANCE),
                floatProp("opacity", "Opacity", 1f, 0f, 1f),
                textProp("label", "Label", "", group = PropGroup.CONTENT),
            ) + labelStyleProps(stateAware = false),
        ),

        // -- Anything the catalog does not cover ----------------------------

        ElementDefinition(
            typeId = CUSTOM_ELEMENT,
            displayName = "Custom Element",
            category = ElementCategory.CUSTOM,
            defaultSize = IntSize(80, 32),
            minSize = IntSize(2, 2),
            acceptsChildren = true,
            glyph = "✦",
            description = "A widget the catalog does not have: name it, give it any properties you like.",
            properties = listOf(
                textProp(
                    "customType", "Type name", "custom_widget", group = PropGroup.CONTENT,
                    help = "Written straight into the export, so use whatever the target expects.",
                ),
                textProp("label", "Label", "Custom", group = PropGroup.CONTENT),
                stringListProp(
                    "attributes", "Extra properties", emptyList(), group = PropGroup.CONTENT,
                    help = "One `key=value` per line. Every exporter passes these through verbatim.",
                ),
                textProp("notes", "Notes", "", group = PropGroup.CONTENT, multiline = true),
                enumProp(
                    "exportAs", "Export as", listOf("panel", "image", "label", "button", "raw"),
                    group = PropGroup.BEHAVIOUR,
                    help = "Which built-in shape the exporters should fall back to. `raw` writes " +
                        "the type name and properties out untouched.",
                ),
                colorProp("background", "Background", 0xC0303030),
                colorProp("borderColor", "Border colour", 0xFF000000),
                intProp("borderWidth", "Border width", 1, min = 0, max = 32, group = PropGroup.APPEARANCE),
                intProp("cornerRadius", "Corner radius", 0, min = 0, max = 64, group = PropGroup.LAYOUT),
                floatProp("opacity", "Opacity", 1f, 0f, 1f),
            ) + skinTextureProps + labelStyleProps(stateAware = false),
        ),
    )

    private val byId: Map<String, ElementDefinition> = definitions.associateBy { it.typeId }

    /** Every registered definition, in palette order. */
    val all: List<ElementDefinition> get() = definitions

    operator fun get(typeId: String): ElementDefinition? = byId[typeId]

    fun require(typeId: String): ElementDefinition =
        byId[typeId] ?: error("Unknown element type '$typeId'. Registered: ${byId.keys.sorted()}")

    fun forEdition(edition: Edition): List<ElementDefinition> = definitions.filter { it.supports(edition) }

    /** Palette contents grouped by category, filtered to [edition]. */
    fun grouped(edition: Edition): Map<ElementCategory, List<ElementDefinition>> =
        forEdition(edition)
            .groupBy { it.category }
            .toSortedMap(compareBy { it.ordinal })

    /** Types that exist in one edition only - used for parity warnings. */
    fun exclusiveTo(edition: Edition): List<ElementDefinition> =
        definitions.filter { it.editions == setOf(edition) }

    fun search(query: String, edition: Edition): List<ElementDefinition> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return forEdition(edition)
        return forEdition(edition).filter {
            needle in it.displayName.lowercase() ||
                needle in it.typeId.lowercase() ||
                needle in it.description.lowercase()
        }
    }
}
