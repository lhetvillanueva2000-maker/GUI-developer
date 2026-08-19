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
            mapOf("title" to text("Alloy Smelter"), "rows" to num(3), "skin" to choice("dark")),
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
    )

    fun forEdition(edition: Edition): List<GuiTemplate> = all.filter { it.edition == edition }

    operator fun get(id: String): GuiTemplate? = all.firstOrNull { it.id == id }

    /** The layout opened on first launch and shipped as the sample project. */
    val demo: GuiTemplate get() = all.first { it.id == "java-chest" }
}
