package com.mcguidesigner.core.templates

import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.model.BoolValue
import com.mcguidesigner.core.model.CanvasBackdrop
import com.mcguidesigner.core.model.CanvasSpec
import com.mcguidesigner.core.model.ColorValue
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.EnumValue
import com.mcguidesigner.core.model.FloatValue
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.Insets
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.IntValue
import com.mcguidesigner.core.model.ListValue
import com.mcguidesigner.core.model.ProjectMeta
import com.mcguidesigner.core.model.PropValue
import com.mcguidesigner.core.model.StringValue
import com.mcguidesigner.core.model.TargetForm
import com.mcguidesigner.core.util.Ids

/** Metadata shown in the "New from template" gallery. */
data class GuiTemplate(
    val id: String,
    val title: String,
    val edition: Edition,
    val form: TargetForm,
    val description: String,
    val tags: List<String>,
    private val factory: () -> GuiProject,
) {
    /** Builds a fresh project with new ids so two instances never collide. */
    fun instantiate(name: String = title): GuiProject {
        val base = factory()
        return base.copy(
            id = Ids.prefixed("project"),
            name = name,
            meta = base.meta.copy(screenId = Ids.slug(name)),
            elements = base.elements.map { regenerate(it) },
        )
    }

    /**
     * The same project with **deterministic** ids.
     *
     * This is what gets written to `/templates`. Random ids would make the
     * committed `.mcgui` files churn on every regeneration, which would turn
     * the CI drift check into noise and every template update into an
     * unreviewable diff.
     */
    fun canonical(name: String = title): GuiProject {
        val base = factory()
        var counter = 0

        fun assign(element: GuiElement): GuiElement {
            val ordinal = counter++
            return element.copy(
                id = "${id}_${ordinal}_${element.type.substringAfterLast('.')}",
                children = element.children.map { assign(it) },
            )
        }

        return base.copy(
            id = "template_$id",
            name = name,
            meta = base.meta.copy(screenId = Ids.slug(name)),
            elements = base.elements.map { assign(it) },
        )
    }

    private fun regenerate(element: GuiElement): GuiElement = element.copy(
        id = Ids.prefixed(element.type.substringAfterLast('.')),
        children = element.children.map { regenerate(it) },
    )
}

/**
 * Ready-to-use starting points, available from the first launch.
 *
 * Templates are built in code rather than shipped as data files so they can
 * never drift out of sync with the catalog - if a property is renamed the
 * templates stop compiling.  `./gradlew exportTemplates` writes them out to
 * `/templates` as `.mcgui` documents for anyone who wants the raw JSON.
 */
object BuiltInTemplates {

    // -- Tiny element builders -------------------------------------------

    private fun node(
        type: String,
        name: String,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        props: Map<String, PropValue> = emptyMap(),
        children: List<GuiElement> = emptyList(),
        /**
         * Which edition's defaults to seed with.
         *
         * It matters more than it looks. A few keys - `font` above all - are
         * declared once per edition with different option sets, so seeding an
         * Other UIs element from Java's defaults gives it a font value that is
         * not a legal option in its own edition, and that reads as a type error
         * on every text-bearing element in the document.
         */
        edition: Edition = Edition.JAVA,
    ): GuiElement {
        val definition = ElementCatalog.require(type)
        return GuiElement(
            id = Ids.prefixed(type.substringAfterLast('.')),
            type = type,
            name = name,
            bounds = IntRect(x, y, w, h),
            props = definition.defaultProps(edition) + props,
            children = children,
        )
    }

    /** [node], seeded for Other UIs. See the `edition` parameter for why. */
    private fun uiNode(
        type: String,
        name: String,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        props: Map<String, PropValue> = emptyMap(),
        children: List<GuiElement> = emptyList(),
    ): GuiElement = node(type, name, x, y, w, h, props, children, Edition.OTHER)

    private fun text(value: String) = StringValue(value)
    private fun num(value: Int) = IntValue(value)
    private fun dec(value: Float) = FloatValue(value)
    private fun flag(value: Boolean) = BoolValue(value)
    private fun choice(value: String) = EnumValue(value)
    private fun rgb(argb: Long) = ColorValue(argb)
    private fun items(vararg values: String) = ListValue(values.map { StringValue(it) })

    /** Grid of vanilla 18x18 slots on the standard 18px pitch. */
    private fun slotGrid(
        originX: Int,
        originY: Int,
        columns: Int,
        rows: Int,
        firstIndex: Int = 0,
        namePrefix: String = "Slot",
        edition: Edition = Edition.JAVA,
    ): List<GuiElement> = buildList {
        var index = firstIndex
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                add(
                    node(
                        ElementCatalog.SLOT_INVENTORY,
                        "$namePrefix $index",
                        originX + column * 18,
                        originY + row * 18,
                        18, 18,
                        mapOf("slotIndex" to num(index)),
                        edition = edition,
                    ),
                )
                index++
            }
        }
    }

    // -- Java templates ---------------------------------------------------

    private fun javaChest(): GuiProject {
        val rows = 3
        val panel = node(
            ElementCatalog.PANEL_CHEST, "Chest Background", 0, 0, 176, 166,
            mapOf(
                "title" to text("Custom Chest"),
                "rows" to num(rows),
                "showPlayerInventory" to flag(true),
                "skin" to choice("vanilla"),
                // The Header Bar below already draws the title; letting the
                // panel draw its own as well would print it twice.
                "showTitle" to flag(false),
            ),
            children = buildList {
                add(node(ElementCatalog.BAR_HEADER, "Title Bar", 8, 6, 160, 12, mapOf("title" to text("Custom Chest"), "showDivider" to flag(false))))
                addAll(slotGrid(8, 18, 9, rows, firstIndex = 0, namePrefix = "Chest Slot"))
                addAll(slotGrid(8, 84, 9, 3, firstIndex = 27, namePrefix = "Inventory Slot"))
                addAll(slotGrid(8, 142, 9, 1, firstIndex = 54, namePrefix = "Hotbar Slot"))
            },
        )
        return GuiProject(
            id = Ids.prefixed("project"),
            name = "Java Chest Container",
            edition = Edition.JAVA,
            canvas = CanvasSpec(176, 166, guiScale = 3, gridSize = 18, backdrop = CanvasBackdrop.DIM),
            elements = listOf(panel),
            meta = ProjectMeta(
                description = "A vanilla-accurate three-row chest with the full player inventory.",
                namespace = "mcgui",
                screenId = "custom_chest",
                tags = listOf("java", "container", "chest"),
            ),
        )
    }

    private fun javaOptionsMenu(): GuiProject {
        val elements = buildList {
            add(node(ElementCatalog.BAR_HEADER, "Screen Title", 28, 12, 200, 18, mapOf("title" to text("Options"), "align" to choice("center"))))
            add(
                node(
                    ElementCatalog.PANEL_FRAME, "Settings Panel", 20, 34, 216, 128,
                    mapOf(
                        "background" to rgb(0xC0101010),
                        "borderColor" to rgb(0xFF4E4E4E),
                        "shadow" to flag(true),
                        "padding" to num(8),
                    ),
                    children = listOf(
                        node(ElementCatalog.INPUT_SLIDER, "Music Volume", 8, 8, 200, 20, mapOf("label" to text("Music"), "value" to dec(0.7f))),
                        node(ElementCatalog.INPUT_SLIDER, "FOV", 8, 32, 200, 20, mapOf("label" to text("FOV"), "value" to dec(0.45f), "suffix" to text(""))),
                        node(
                            ElementCatalog.INPUT_DROPDOWN, "Graphics", 8, 56, 200, 20,
                            mapOf("items" to items("Fast", "Fancy", "Fabulous"), "openMode" to choice("cycle")),
                        ),
                        node(ElementCatalog.INPUT_CHECKBOX, "VSync", 8, 80, 200, 12, mapOf("label" to text("Enable VSync"), "checked" to flag(true))),
                        node(ElementCatalog.DECOR_SEPARATOR, "Divider", 8, 98, 200, 2, mapOf("style" to choice("bevel"))),
                        node(ElementCatalog.TEXT_LABEL, "Hint", 8, 104, 200, 10, mapOf("text" to text("Changes apply immediately."), "align" to choice("center"), "textColor" to rgb(0xFFA0A0A0))),
                    ),
                ),
            )
            add(node(ElementCatalog.JAVA_RECT_BUTTON, "Done", 78, 172, 100, 20, mapOf("label" to text("Done"), "width" to choice("custom"), "action" to text("close_screen"))))
            add(node(ElementCatalog.BUTTON_ICON, "Help", 220, 12, 20, 20, mapOf("tooltip" to text("Open the wiki"))))
        }
        return GuiProject(
            id = Ids.prefixed("project"),
            name = "Java Options Menu",
            edition = Edition.JAVA,
            canvas = CanvasSpec(256, 200, guiScale = 3, gridSize = 4, backdrop = CanvasBackdrop.DIRT_PANORAMA),
            elements = elements,
            meta = ProjectMeta(
                description = "Full-screen settings menu using vanilla sliders, toggles and buttons.",
                namespace = "mcgui",
                screenId = "options_menu",
                tags = listOf("java", "menu", "settings"),
            ),
        )
    }

    private fun javaMachine(): GuiProject {
        val panel = node(
            ElementCatalog.PANEL_CHEST, "Machine Background", 0, 0, 176, 166,
            mapOf(
                "title" to text("Alloy Smelter"),
                "rows" to num(3),
                "skin" to choice("dark"),
                "showTitle" to flag(false),
            ),
            children = buildList {
                add(node(ElementCatalog.BAR_HEADER, "Title", 8, 5, 160, 12, mapOf("title" to text("Alloy Smelter"), "showDivider" to flag(false))))
                add(node(ElementCatalog.SLOT_INVENTORY, "Input A", 34, 22, 18, 18, mapOf("slotIndex" to num(0))))
                add(node(ElementCatalog.SLOT_INVENTORY, "Input B", 34, 44, 18, 18, mapOf("slotIndex" to num(1))))
                add(node(ElementCatalog.SLOT_INVENTORY, "Fuel", 34, 66, 18, 18, mapOf("slotIndex" to num(2))))
                add(node(ElementCatalog.SLOT_INVENTORY, "Output", 116, 44, 18, 18, mapOf("slotIndex" to num(3))))
                add(
                    node(
                        ElementCatalog.PROGRESS_BAR, "Smelt Progress", 62, 48, 48, 10,
                        mapOf("progress" to dec(0.55f), "fillColor" to rgb(0xFFFFA726), "direction" to choice("right")),
                    ),
                )
                add(
                    node(
                        ElementCatalog.PROGRESS_BAR, "Fuel Gauge", 36, 88, 14, 4,
                        mapOf("progress" to dec(0.3f), "fillColor" to rgb(0xFFEF5350)),
                    ),
                )
                add(node(ElementCatalog.PANEL_TOOLTIP, "Recipe Tooltip", 100, 12, 70, 28, mapOf("text" to text("Iron + Coal\n= Steel Ingot"))))
                addAll(slotGrid(8, 100, 9, 3, firstIndex = 10, namePrefix = "Inventory Slot"))
                addAll(slotGrid(8, 158, 9, 1, firstIndex = 37, namePrefix = "Hotbar Slot"))
            },
        )
        return GuiProject(
            id = Ids.prefixed("project"),
            name = "Java Machine UI",
            edition = Edition.JAVA,
            canvas = CanvasSpec(176, 182, guiScale = 3, gridSize = 2, backdrop = CanvasBackdrop.DIM),
            elements = listOf(panel),
            meta = ProjectMeta(
                description = "Furnace-style machine screen with progress and fuel gauges.",
                namespace = "mcgui",
                screenId = "alloy_smelter",
                tags = listOf("java", "machine", "modded"),
            ),
        )
    }

    // -- Bedrock templates ------------------------------------------------

    private fun bedrockTouchHud(): GuiProject {
        val e = Edition.BEDROCK
        val elements = buildList {
            add(
                node(
                    ElementCatalog.BEDROCK_TOUCHPAD, "Movement Pad", 16, 92, 84, 84,
                    mapOf("layout" to choice("dpad"), "opacity" to dec(0.7f)), edition = e,
                ),
            )
            add(
                node(
                    ElementCatalog.BEDROCK_ACTION_BUTTON, "Jump", 262, 132, 44, 44,
                    mapOf("label" to text(""), "shape" to choice("circle"), "action" to text("jump")), edition = e,
                ),
            )
            add(
                node(
                    ElementCatalog.BEDROCK_ACTION_BUTTON, "Sneak", 262, 84, 44, 44,
                    mapOf("shape" to choice("rounded"), "action" to text("sneak")), edition = e,
                ),
            )
            add(
                node(
                    ElementCatalog.BEDROCK_ACTION_BUTTON, "Inventory", 214, 132, 44, 44,
                    mapOf("shape" to choice("rounded"), "action" to text("open_inventory")), edition = e,
                ),
            )
            add(node(ElementCatalog.STRIP_HOTBAR, "Hotbar", 69, 156, 182, 22, edition = e))
            add(
                node(
                    ElementCatalog.PROGRESS_BAR, "Health", 69, 140, 88, 8,
                    mapOf("progress" to dec(0.8f), "fillColor" to rgb(0xFFE53935), "segmented" to flag(true), "segments" to num(10)),
                    edition = e,
                ),
            )
            add(
                node(
                    ElementCatalog.PROGRESS_BAR, "Hunger", 163, 140, 88, 8,
                    mapOf("progress" to dec(0.6f), "fillColor" to rgb(0xFF8D6E63), "segmented" to flag(true), "segments" to num(10)),
                    edition = e,
                ),
            )
            add(
                node(
                    ElementCatalog.BUTTON_ICON, "Chat", 8, 8, 32, 32,
                    mapOf("tooltip" to text("Open chat")), edition = e,
                ),
            )
        }
        return GuiProject(
            id = Ids.prefixed("project"),
            name = "Bedrock Touch HUD",
            edition = Edition.BEDROCK,
            canvas = CanvasSpec(
                320, 180, guiScale = 3, gridSize = 4,
                backdrop = CanvasBackdrop.GAME_WORLD,
                targetForm = TargetForm.MOBILE,
                safeArea = Insets.symmetric(12, 8),
            ),
            elements = elements,
            meta = ProjectMeta(
                description = "Landscape touch HUD with a D-pad, action buttons and status bars.",
                namespace = "mcgui",
                screenId = "touch_hud",
                tags = listOf("bedrock", "hud", "touch"),
            ),
        )
    }

    private fun bedrockSettings(): GuiProject {
        val e = Edition.BEDROCK
        val panel = node(
            ElementCatalog.PANEL_FRAME, "Settings Sheet", 20, 16, 280, 148,
            mapOf(
                "background" to rgb(0xF01B1B1F),
                "borderColor" to rgb(0xFF3D3D45),
                "corner" to choice("rounded"),
                "shadow" to flag(true),
                "padding" to num(10),
            ),
            edition = e,
            children = listOf(
                node(ElementCatalog.BAR_HEADER, "Sheet Title", 10, 8, 260, 20, mapOf("title" to text("Settings"), "showCloseButton" to flag(true)), edition = e),
                node(ElementCatalog.INPUT_SEARCH, "Search Settings", 10, 32, 260, 28, mapOf("placeholder" to text("Search settings")), edition = e),
                node(
                    ElementCatalog.CONTAINER_SCROLL, "Settings List", 10, 66, 260, 70,
                    mapOf("direction" to choice("vertical"), "contentLength" to num(200), "momentum" to flag(true)),
                    edition = e,
                    children = listOf(
                        node(ElementCatalog.BUTTON_TOGGLE, "Vibration", 4, 4, 248, 28, mapOf("label" to text("Vibration"), "style" to choice("switch"), "value" to flag(true)), edition = e),
                        node(ElementCatalog.BUTTON_TOGGLE, "Auto Jump", 4, 36, 248, 28, mapOf("label" to text("Auto Jump"), "style" to choice("switch")), edition = e),
                        node(ElementCatalog.INPUT_SLIDER, "Sensitivity", 4, 68, 248, 28, mapOf("label" to text("Sensitivity"), "value" to dec(0.55f)), edition = e),
                        node(ElementCatalog.INPUT_CHECKBOX, "Show Coordinates", 4, 100, 248, 24, mapOf("label" to text("Show coordinates")), edition = e),
                    ),
                ),
            ),
        )
        return GuiProject(
            id = Ids.prefixed("project"),
            name = "Bedrock Settings Sheet",
            edition = Edition.BEDROCK,
            canvas = CanvasSpec(
                320, 180, guiScale = 3, gridSize = 4,
                backdrop = CanvasBackdrop.DIM,
                targetForm = TargetForm.MOBILE,
                safeArea = Insets.symmetric(12, 8),
            ),
            elements = listOf(panel),
            meta = ProjectMeta(
                description = "Bottom-sheet style settings screen with a scrollable option list.",
                namespace = "mcgui",
                screenId = "settings_sheet",
                tags = listOf("bedrock", "settings", "touch"),
            ),
        )
    }

    private fun bedrockContainer(): GuiProject {
        val e = Edition.BEDROCK
        val panel = node(
            ElementCatalog.PANEL_CHEST, "Pocket Container", 0, 0, 320, 180,
            mapOf(
                "title" to text("Chest"),
                "rows" to num(3),
                "bedrockSkin" to choice("pocket"),
                "showPlayerInventory" to flag(true),
                "showTitle" to flag(false),
            ),
            edition = e,
            children = buildList {
                add(node(ElementCatalog.BAR_HEADER, "Title", 16, 10, 288, 20, mapOf("title" to text("Chest"), "showCloseButton" to flag(true)), edition = e))
                addAll(slotGrid(66, 36, 9, 3, firstIndex = 0, namePrefix = "Chest Slot", edition = e))
                add(node(ElementCatalog.DECOR_SEPARATOR, "Divider", 66, 96, 188, 2, mapOf("style" to choice("line")), edition = e))
                addAll(slotGrid(66, 104, 9, 3, firstIndex = 27, namePrefix = "Inventory Slot", edition = e))
                add(node(ElementCatalog.STRIP_HOTBAR, "Hotbar", 66, 158, 182, 22, edition = e))
                add(
                    node(
                        ElementCatalog.BEDROCK_ACTION_BUTTON, "Sort", 276, 40, 32, 32,
                        mapOf("shape" to choice("rounded"), "action" to text("sort_container")), edition = e,
                    ),
                )
            },
        )
        return GuiProject(
            id = Ids.prefixed("project"),
            name = "Bedrock Pocket Container",
            edition = Edition.BEDROCK,
            canvas = CanvasSpec(
                320, 180, guiScale = 3, gridSize = 2,
                backdrop = CanvasBackdrop.DIM,
                targetForm = TargetForm.MOBILE,
                safeArea = Insets.symmetric(8, 4),
            ),
            elements = listOf(panel),
            meta = ProjectMeta(
                description = "Pocket-edition chest layout sized for a 320x180 touch canvas.",
                namespace = "mcgui",
                screenId = "pocket_container",
                tags = listOf("bedrock", "container", "touch"),
            ),
        )
    }

    private fun bedrockActionForm(): GuiProject {
        val e = Edition.BEDROCK
        val panel = node(
            ElementCatalog.PANEL_FRAME, "Form Body", 60, 18, 200, 144,
            mapOf("background" to rgb(0xF0202028), "corner" to choice("rounded"), "padding" to num(10)),
            edition = e,
            children = listOf(
                node(ElementCatalog.BAR_HEADER, "Form Title", 8, 6, 184, 20, mapOf("title" to text("Village Shop"), "align" to choice("center")), edition = e),
                node(ElementCatalog.TEXT_LABEL, "Form Body Text", 8, 30, 184, 20, mapOf("text" to text("Pick something to trade."), "align" to choice("center"), "wrap" to flag(true)), edition = e),
                node(ElementCatalog.BUTTON_NORMAL, "Buy Button", 8, 56, 184, 30, mapOf("label" to text("Buy items"), "action" to text("form_button_0")), edition = e),
                node(ElementCatalog.BUTTON_NORMAL, "Sell Button", 8, 92, 184, 30, mapOf("label" to text("Sell items"), "action" to text("form_button_1")), edition = e),
                node(ElementCatalog.IMAGE_PLACEHOLDER, "Shop Icon", 84, 126, 32, 12, mapOf("fit" to choice("contain")), edition = e),
            ),
        )
        return GuiProject(
            id = Ids.prefixed("project"),
            name = "Bedrock Action Form",
            edition = Edition.BEDROCK,
            canvas = CanvasSpec(
                320, 180, guiScale = 3, gridSize = 4,
                backdrop = CanvasBackdrop.DIM,
                targetForm = TargetForm.MOBILE,
                safeArea = Insets.symmetric(12, 8),
            ),
            elements = listOf(panel),
            meta = ProjectMeta(
                description = "ActionForm-style dialog with stacked full-width touch buttons.",
                namespace = "mcgui",
                screenId = "action_form",
                tags = listOf("bedrock", "form", "dialog"),
            ),
        )
    }

    // -- Registry ---------------------------------------------------------

    val all: List<GuiTemplate> = listOf(
        GuiTemplate(
            "java-chest", "Java Chest Container", Edition.JAVA, TargetForm.DESKTOP,
            "Vanilla 3-row chest with the full player inventory grid.",
            listOf("container", "vanilla"), ::javaChest,
        ),
        GuiTemplate(
            "java-options", "Java Options Menu", Edition.JAVA, TargetForm.DESKTOP,
            "Settings screen with sliders, a cycling dropdown and a checkbox.",
            listOf("menu", "settings"), ::javaOptionsMenu,
        ),
        GuiTemplate(
            "java-machine", "Java Machine UI", Edition.JAVA, TargetForm.DESKTOP,
            "Furnace-style machine with progress arrow and fuel gauge.",
            listOf("machine", "modded"), ::javaMachine,
        ),
        GuiTemplate(
            "bedrock-hud", "Bedrock Touch HUD", Edition.BEDROCK, TargetForm.MOBILE,
            "Landscape HUD with D-pad, action buttons, hotbar and status bars.",
            listOf("hud", "touch"), ::bedrockTouchHud,
        ),
        GuiTemplate(
            "bedrock-settings", "Bedrock Settings Sheet", Edition.BEDROCK, TargetForm.MOBILE,
            "Scrollable settings sheet with switches and a search field.",
            listOf("settings", "touch"), ::bedrockSettings,
        ),
        GuiTemplate(
            "bedrock-container", "Bedrock Pocket Container", Edition.BEDROCK, TargetForm.MOBILE,
            "Pocket-edition chest sized for a touch canvas.",
            listOf("container", "touch"), ::bedrockContainer,
        ),
        GuiTemplate(
            "bedrock-form", "Bedrock Action Form", Edition.BEDROCK, TargetForm.MOBILE,
            "ActionForm dialog with stacked full-width buttons.",
            listOf("form", "dialog"), ::bedrockActionForm,
        ),
        GuiTemplate(
            "other-signin", "Sign-in Screen", Edition.OTHER, TargetForm.MOBILE,
            "A phone-sized sign-in form: card, two fields and a primary action.",
            listOf("app", "form"), ::otherSignIn,
        ),
        GuiTemplate(
            "other-settings", "Settings Screen", Edition.OTHER, TargetForm.MOBILE,
            "Grouped settings rows: switches, a divider, a select and a slider.",
            listOf("app", "settings"), ::otherSettings,
        ),
        GuiTemplate(
            "other-dashboard", "Dashboard", Edition.OTHER, TargetForm.DESKTOP,
            "A wide layout: search, tabs and a row of stat cards.",
            listOf("app", "dashboard"), ::otherDashboard,
        ),
        GuiTemplate(
            "other-studio", "Dark Studio Panel", Edition.OTHER, TargetForm.MOBILE,
            "A dark creative-tool screen: toolbar, layer rows, sliders and a swatch row.",
            listOf("app", "dark", "tool"), ::otherStudio,
        ),
    )

    fun forEdition(edition: Edition): List<GuiTemplate> = all.filter { it.edition == edition }

    operator fun get(id: String): GuiTemplate? = all.firstOrNull { it.id == id }

    /** The layout opened on first launch and shipped as the sample project. */
    val demo: GuiTemplate get() = all.first { it.id == "java-chest" }

    // -- Other UIs ---------------------------------------------------------
    //
    // Deliberately ordinary app screens rather than anything clever. A starter
    // template's job is to put something on the canvas that can be taken apart
    // to see how the pieces fit, and a sign-in form is the most legible example
    // of that there is.
    //
    // Every node goes through `uiNode`, which seeds Other UIs defaults rather
    // than Java's - see the `edition` parameter on `node`.

    private fun otherSignIn(): GuiProject {
        val elements = buildList {
            add(uiNode(ElementCatalog.BAR_HEADER, "App Bar", 0, 0, 360, 56, mapOf("title" to text("Sign in"))))
            add(
                uiNode(
                    ElementCatalog.PANEL_FRAME, "Card", 24, 88, 312, 296,
                    mapOf("background" to rgb(0xFFFFFFFF), "padding" to num(20)),
                    children = listOf(
                        uiNode(
                            ElementCatalog.TEXT_LABEL, "Heading", 20, 20, 272, 26,
                            mapOf("text" to text("Welcome back"), "scale" to dec(1.75f)),
                        ),
                        uiNode(
                            ElementCatalog.TEXT_LABEL, "Subheading", 20, 48, 272, 20,
                            mapOf("text" to text("Sign in to continue"), "textColor" to rgb(0xFF4B5665)),
                        ),
                        uiNode(
                            ElementCatalog.INPUT_TEXTBOX, "Email", 20, 84, 272, 44,
                            mapOf("placeholder" to text("Email address")),
                        ),
                        uiNode(
                            ElementCatalog.INPUT_TEXTBOX, "Password", 20, 140, 272, 44,
                            mapOf("placeholder" to text("Password")),
                        ),
                        uiNode(
                            ElementCatalog.INPUT_CHECKBOX, "Remember", 20, 196, 160, 24,
                            mapOf("label" to text("Remember me"), "checked" to flag(true)),
                        ),
                        uiNode(
                            ElementCatalog.BUTTON_NORMAL, "Sign in", 20, 236, 272, 44,
                            mapOf("label" to text("Sign in")),
                        ),
                    ),
                ),
            )
            add(
                uiNode(
                    ElementCatalog.TEXT_LABEL, "Footer", 24, 400, 312, 20,
                    mapOf("text" to text("No account? Create one"), "textColor" to rgb(0xFF2F6BE0)),
                ),
            )
        }
        return GuiProject(
            id = Ids.prefixed("project"),
            name = "Sign-in Screen",
            edition = Edition.OTHER,
            canvas = CanvasSpec(360, 440, guiScale = 2, gridSize = 8, backdrop = CanvasBackdrop.SOLID, backdropColor = 0xFFEEF1F5),
            elements = elements,
            meta = ProjectMeta(
                description = "A phone-sized sign-in form: card, two fields and a primary action.",
                namespace = "app",
                screenId = "sign_in",
                tags = listOf("app", "form", "mobile"),
            ),
        )
    }

    private fun otherSettings(): GuiProject {
        val elements = buildList {
            add(uiNode(ElementCatalog.BAR_HEADER, "App Bar", 0, 0, 360, 56, mapOf("title" to text("Settings"))))
            add(
                uiNode(
                    ElementCatalog.PANEL_FRAME, "Group", 16, 72, 328, 200,
                    mapOf("background" to rgb(0xFFFFFFFF), "padding" to num(16)),
                    children = listOf(
                        uiNode(
                            ElementCatalog.BUTTON_TOGGLE, "Notifications", 16, 16, 296, 32,
                            mapOf("label" to text("Notifications"), "value" to flag(true)),
                        ),
                        uiNode(ElementCatalog.DECOR_SEPARATOR, "Rule 1", 16, 60, 296, 2),
                        uiNode(
                            ElementCatalog.BUTTON_TOGGLE, "Dark mode", 16, 76, 296, 32,
                            mapOf("label" to text("Dark mode"), "value" to flag(false)),
                        ),
                        uiNode(ElementCatalog.DECOR_SEPARATOR, "Rule 2", 16, 120, 296, 2),
                        uiNode(
                            ElementCatalog.INPUT_DROPDOWN, "Language", 16, 136, 296, 44,
                            mapOf("items" to items("English", "Filipino", "Espanol"), "selectedIndex" to num(0)),
                        ),
                    ),
                ),
            )
            add(
                uiNode(
                    ElementCatalog.INPUT_SLIDER, "Volume", 32, 296, 296, 32,
                    mapOf("label" to text("Volume"), "value" to dec(0.6f)),
                ),
            )
            add(
                uiNode(
                    ElementCatalog.BUTTON_NORMAL, "Save", 32, 352, 296, 44,
                    mapOf("label" to text("Save changes")),
                ),
            )
        }
        return GuiProject(
            id = Ids.prefixed("project"),
            name = "Settings Screen",
            edition = Edition.OTHER,
            canvas = CanvasSpec(360, 420, guiScale = 2, gridSize = 8, backdrop = CanvasBackdrop.SOLID, backdropColor = 0xFFEEF1F5),
            elements = elements,
            meta = ProjectMeta(
                description = "Grouped settings rows: switches, a divider, a select and a slider.",
                namespace = "app",
                screenId = "settings",
                tags = listOf("app", "settings", "mobile"),
            ),
        )
    }

    /**
     * A dark creative-tool screen.
     *
     * Here to prove one thing about this target that the three light templates
     * cannot: the skin reads its whole token set from the canvas backdrop, so a
     * dark screen is a dark screen throughout - dark fields, dark dividers,
     * light placeholder text - rather than light widgets with their backgrounds
     * individually overridden, which is how a dark mock-up usually ends up
     * being built and why it usually ends up half-light.
     *
     * Nothing here sets a background except the two panels and the swatches.
     * That is the point: the darkness comes from `backdropColor`.
     */
    private fun otherStudio(): GuiProject {
        val swatches = listOf(0xFFF87171L, 0xFFFBBF24L, 0xFF34D399L, 0xFF60A5FA, 0xFFA78BFA, 0xFFF472B6)

        val elements = buildList {
            add(uiNode(ElementCatalog.BAR_HEADER, "Top Bar", 0, 0, 360, 56, mapOf("title" to text("Untitled canvas"))))

            // The artboard the tools act on.
            add(uiNode(ElementCatalog.IMAGE_PLACEHOLDER, "Artboard", 16, 68, 328, 216))

            // A tool strip. Icon buttons are transparent in this edition, so
            // these read as a row of glyphs rather than a row of grey plates.
            listOf("Brush", "Eraser", "Fill", "Pick", "Undo").forEachIndexed { index, name ->
                add(uiNode(ElementCatalog.BUTTON_ICON, name, 20 + index * 66, 296, 40, 40))
            }

            add(
                uiNode(
                    ElementCatalog.PANEL_FRAME, "Brush Panel", 16, 348, 328, 116,
                    mapOf("background" to rgb(0xFF1D2128), "padding" to num(16)),
                    children = listOf(
                        uiNode(
                            ElementCatalog.TEXT_LABEL, "Size Label", 16, 14, 120, 16,
                            mapOf("text" to text("Size"), "textColor" to rgb(0xFFA6B1BF)),
                        ),
                        uiNode(
                            ElementCatalog.INPUT_SLIDER, "Size", 16, 34, 296, 24,
                            mapOf("label" to text("Size"), "value" to dec(0.35f)),
                        ),
                        uiNode(
                            ElementCatalog.TEXT_LABEL, "Opacity Label", 16, 62, 120, 16,
                            mapOf("text" to text("Opacity"), "textColor" to rgb(0xFFA6B1BF)),
                        ),
                        uiNode(
                            ElementCatalog.INPUT_SLIDER, "Opacity", 16, 82, 296, 24,
                            mapOf("label" to text("Opacity"), "value" to dec(0.8f)),
                        ),
                    ),
                ),
            )

            swatches.forEachIndexed { index, colour ->
                add(uiNode(ElementCatalog.PANEL_FRAME, "Swatch ${index + 1}", 20 + index * 56, 480, 40, 40, mapOf("background" to rgb(colour))))
            }

            add(
                uiNode(
                    ElementCatalog.PANEL_FRAME, "Layers", 16, 540, 328, 128,
                    mapOf("background" to rgb(0xFF1D2128), "padding" to num(16)),
                    children = listOf(
                        uiNode(
                            ElementCatalog.TEXT_LABEL, "Layers Heading", 16, 12, 200, 22,
                            mapOf("text" to text("Layers"), "scale" to dec(1.4f)),
                        ),
                        uiNode(
                            ElementCatalog.BUTTON_TOGGLE, "Layer 2", 16, 46, 296, 28,
                            mapOf("label" to text("Sketch"), "value" to flag(true)),
                        ),
                        uiNode(ElementCatalog.DECOR_SEPARATOR, "Layer Rule", 16, 82, 296, 2, mapOf("color" to rgb(0xFF333A45))),
                        uiNode(
                            ElementCatalog.BUTTON_TOGGLE, "Layer 1", 16, 90, 296, 28,
                            mapOf("label" to text("Background"), "value" to flag(false)),
                        ),
                    ),
                ),
            )

            add(
                uiNode(
                    ElementCatalog.BUTTON_NORMAL, "Export", 16, 684, 328, 48,
                    mapOf("label" to text("Export image")),
                ),
            )
        }

        return GuiProject(
            id = Ids.prefixed("project"),
            name = "Dark Studio Panel",
            edition = Edition.OTHER,
            canvas = CanvasSpec(360, 752, guiScale = 2, gridSize = 8, backdrop = CanvasBackdrop.SOLID, backdropColor = 0xFF14171C),
            elements = elements,
            meta = ProjectMeta(
                description = "A dark creative-tool screen: toolbar, layer rows, sliders and a swatch row.",
                namespace = "app",
                screenId = "studio",
                tags = listOf("app", "dark", "tool", "mobile"),
            ),
        )
    }

    private fun otherDashboard(): GuiProject {
        val labels = listOf("Visitors", "Signups", "Revenue")
        val values = listOf("12,480", "318", "12,900")
        val fractions = listOf(0.72f, 0.41f, 0.58f)

        val elements = buildList {
            add(uiNode(ElementCatalog.BAR_HEADER, "App Bar", 0, 0, 720, 56, mapOf("title" to text("Dashboard"))))
            add(
                uiNode(
                    ElementCatalog.INPUT_SEARCH, "Search", 24, 76, 320, 40,
                    mapOf("placeholder" to text("Search anything")),
                ),
            )
            listOf("Today", "This week", "All time").forEachIndexed { index, label ->
                add(
                    uiNode(
                        ElementCatalog.BUTTON_TAB, "Tab ${index + 1}", 24 + index * 96, 132, 88, 36,
                        mapOf("label" to text(label), "selected" to flag(index == 0)),
                    ),
                )
            }
            labels.forEachIndexed { index, label ->
                add(
                    uiNode(
                        ElementCatalog.PANEL_FRAME, "Card ${index + 1}", 24 + index * 232, 188, 216, 120,
                        mapOf("background" to rgb(0xFFFFFFFF), "padding" to num(16)),
                        children = listOf(
                            uiNode(
                                ElementCatalog.TEXT_LABEL, "Card label $label", 16, 16, 184, 18,
                                mapOf("text" to text(label), "textColor" to rgb(0xFF4B5665)),
                            ),
                            uiNode(
                                ElementCatalog.TEXT_LABEL, "Card value $label", 16, 40, 184, 30,
                                mapOf("text" to text(values[index]), "scale" to dec(2f)),
                            ),
                            uiNode(
                                ElementCatalog.PROGRESS_BAR, "Card bar $label", 16, 84, 184, 8,
                                mapOf("progress" to dec(fractions[index])),
                            ),
                        ),
                    ),
                )
            }
        }
        return GuiProject(
            id = Ids.prefixed("project"),
            name = "Dashboard",
            edition = Edition.OTHER,
            canvas = CanvasSpec(720, 340, guiScale = 1, gridSize = 8, backdrop = CanvasBackdrop.SOLID, backdropColor = 0xFFEEF1F5),
            elements = elements,
            meta = ProjectMeta(
                description = "A wide layout: search, tabs and a row of stat cards.",
                namespace = "app",
                screenId = "dashboard",
                tags = listOf("app", "dashboard", "desktop"),
            ),
        )
    }
}
