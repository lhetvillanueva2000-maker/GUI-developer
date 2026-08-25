package com.mcguidesigner.core

/**
 * One thing the app can do, and how to ask it to.
 *
 * [keys] is empty for anything reachable only by pointing at it, which is most
 * of the app on a phone. That is not a gap to be filled with invented
 * shortcuts - it is the honest answer, and a help page that lists a key
 * binding which does not exist is worse than one that says there isn't one.
 */
data class HelpEntry(
    val action: String,
    val keys: String = "",
    val note: String = "",
)

/** A named group of [HelpEntry]s. */
data class HelpSection(val title: String, val entries: List<HelpEntry>)

/**
 * Everything the app does, in one list.
 *
 * Data rather than a laid-out screen, for the reason every other list in
 * `Branding` is: the same content belongs in the in-app help, in the docs and
 * in the release notes, and three copies is three chances to update two.
 *
 * Kept deliberately complete rather than curated. A help page that lists the
 * interesting half is one you stop trusting the moment you look something up
 * and it is not there.
 */
object HelpBook {

    val sections: List<HelpSection> = listOf(
        HelpSection(
            "Getting around",
            listOf(
                HelpEntry("Open the Java editor", "J", "From the home screen."),
                HelpEntry("Open the Bedrock editor", "B", "From the home screen."),
                HelpEntry(
                    "Open the Other UIs editor",
                    "",
                    "The third card on the home screen. For apps, websites and tools - " +
                        "anything that is not Minecraft.",
                ),
                HelpEntry("Open settings", "S", "From the home screen, or the gear at the top right."),
                HelpEntry("Switch theme", "T", "Dark and light."),
                HelpEntry(
                    "Back to the home screen",
                    "Esc",
                    "Or the arrow at the top left. On Android the system back " +
                        "gesture does the same thing.",
                ),
                HelpEntry("Close a page over home", "Esc", "Settings and the support page."),
            ),
        ),

        HelpSection(
            "Documents and tabs",
            listOf(
                HelpEntry(
                    "Open a second document",
                    "",
                    "Go home and pick the other edition. Java and Bedrock each " +
                        "get their own tab, and both stay open.",
                ),
                HelpEntry("New document", "Ctrl+N", "Asks before discarding unsaved work."),
                HelpEntry("New tab in this edition", "", "The + at the end of the tab strip."),
                HelpEntry("Close a tab", "", "The ✕ on the tab. A dot instead of a ✕ means unsaved."),
                HelpEntry("Open a project", "Ctrl+O"),
                HelpEntry("Save", "Ctrl+S"),
                HelpEntry("Export", "Ctrl+Shift+S", "Or Ctrl+E."),
                HelpEntry("Export everything at once", "Ctrl+Shift+E"),
            ),
        ),

        HelpSection(
            "Editing",
            listOf(
                HelpEntry("Undo", "Ctrl+Z"),
                HelpEntry("Redo", "Ctrl+Y", "Or Ctrl+Shift+Z."),
                HelpEntry("Copy", "Ctrl+C"),
                HelpEntry("Paste", "Ctrl+V"),
                HelpEntry("Cut", "Ctrl+X", "Stays on the clipboard, so nothing is lost."),
                HelpEntry("Duplicate", "Ctrl+D"),
                HelpEntry("Select all", "Ctrl+A"),
                HelpEntry("Delete", "Delete", "Or Backspace."),
                HelpEntry("Deselect", "Esc", "Escape cancels a placement first, then clears the selection."),
                HelpEntry("Save the selection as a prefab", "Ctrl+Shift+P"),
            ),
        ),

        HelpSection(
            "Moving and arranging",
            listOf(
                HelpEntry("Nudge", "Arrow keys", "By the small step set in Settings."),
                HelpEntry("Nudge further", "Shift + arrows", "By the big step."),
                HelpEntry(
                    "Move pad",
                    "",
                    "Four arrows on the canvas, and nothing in the middle of them - " +
                        "the centre is the space your thumb crosses to reach the four.",
                ),
                HelpEntry(
                    "Change the step",
                    "",
                    "The bar under the pad. Touch it anywhere and that position is the " +
                        "value; tap it to come back to 1; long press for the settings. " +
                        "The scale is exponential, so 1, 2, 4, 8, 16, 32, 64 and 128 " +
                        "are evenly spaced.",
                ),
                HelpEntry(
                    "Resize the move pad",
                    "",
                    "Drag the grip on the pad's inner corner. Reset it from Editor Settings.",
                ),
                HelpEntry(
                    "Rotate by 90°",
                    "",
                    "The rotate buttons in the toolbar, or on the Arrange sheet.",
                ),
                HelpEntry(
                    "Rotate to any angle",
                    "",
                    "Drag the round knob above the selection. Hold Shift while dragging to " +
                        "snap to 15°; on a touchscreen it snaps on its own. The Rotation " +
                        "field in the inspector takes any whole angle from 0 to 359.",
                ),
                HelpEntry(
                    "Resize something turned",
                    "",
                    "The handles sit on the element's own corners and the drag follows its " +
                        "own axes, so a turned element resizes the way it looks like it should.",
                ),
                HelpEntry("Bring forward", "Ctrl+]"),
                HelpEntry("Send backward", "Ctrl+["),
                HelpEntry("Bring to front", "Ctrl+Shift+]"),
                HelpEntry("Send to back", "Ctrl+Shift+["),
                HelpEntry("Align and distribute", "", "The toolbar row, or the Arrange sheet on a phone."),
            ),
        ),

        HelpSection(
            "Tools",
            listOf(
                HelpEntry("Select", "V"),
                HelpEntry("Pan", "H", "Or drag with the middle or right button."),
                HelpEntry("Marquee", "M"),
                HelpEntry("Multi-select", "Shift + click", "Long press on a touchscreen."),
            ),
        ),

        HelpSection(
            "The canvas",
            listOf(
                HelpEntry("Zoom", "Ctrl + wheel", "Zooms towards the pointer. Pinch on a touchscreen."),
                HelpEntry("Zoom in", "Ctrl+="),
                HelpEntry("Zoom out", "Ctrl+-"),
                HelpEntry("Reset the view", "Ctrl+0"),
                HelpEntry(
                    "Toggle the grid",
                    "Ctrl+G",
                    "Off by default. Snapping does not need it to be visible; on a phone " +
                        "the toggle is in the three-dots menu.",
                ),
                HelpEntry("Toggle rulers", "Ctrl+R"),
            ),
        ),

        HelpSection(
            "Building blocks",
            listOf(
                HelpEntry("Components", "F1", "The gallery: every component with a live preview."),
                HelpEntry("Shapes", "", "Rectangles through to stars, each with fill, outline and rotation."),
                HelpEntry(
                    "Colours",
                    "",
                    "Every colour field has a palette under it: vanilla Minecraft greys and " +
                        "browns measured off the real widgets, a neutral ramp, and fourteen " +
                        "hues at three lightnesses. The hex field still takes anything.",
                ),
                HelpEntry("Custom elements", "", "Any type name and any key=value properties, passed through to every export."),
                HelpEntry("Prefabs", "", "A saved selection, reusable in any later project."),
                HelpEntry("Texture library", "", "Everything imported, de-duplicated by content and kept across projects."),
                HelpEntry("Import a resource pack", "", "Reads Java and Bedrock packs and pre-selects the GUI art."),
                HelpEntry("Animated images", "", "GIFs, or any set of frames, become the vertical strip both editions animate."),
            ),
        ),

        HelpSection(
            "Exporting",
            listOf(
                HelpEntry("Minecraft's own formats", "", "Bedrock JSON UI and the Java GUI definition sidecars."),
                HelpEntry("Source code", "", "Java Screen, Kotlin Compose, HTML+CSS, CSS, React JSX, SwiftUI, Flutter and Android XML."),
                HelpEntry("Artwork", "", "SVG vector drawing."),
                HelpEntry(
                    "A PNG of the design",
                    "Ctrl+Shift+I",
                    "In the export list, alongside the packs. 144p through 2160p or a " +
                        "whole multiple; pixel art only survives whole-number scaling, " +
                        "so each named height snaps to the nearest one and shows what " +
                        "you actually get. A name that cannot be met is not offered.",
                ),
                HelpEntry(
                    "A PNG with no background",
                    "",
                    "Set Background to Transparent in the image export, for laying the " +
                        "screen over a screenshot of the game.",
                ),
                HelpEntry("Everything at once", "Ctrl+Shift+E", "Every format above into one folder."),
            ),
        ),

        HelpSection(
            "Importing",
            listOf(
                HelpEntry("Open a project", "Ctrl+O", "A .mcgui document, exactly as it was saved."),
                HelpEntry(
                    "Import a design",
                    "Ctrl+I",
                    "Bedrock JSON UI, HTML + CSS, or SVG. Anything exported from here " +
                        "comes back with its own element types; anything else is mapped " +
                        "to the nearest match and the differences are listed.",
                ),
                HelpEntry(
                    "Check an import before it lands",
                    "",
                    "Every import shows what it found - a picture, the element count " +
                        "and what it could not read - and opens in a new tab only once " +
                        "you accept it. Nothing you have open is ever overwritten.",
                ),
                HelpEntry("Import a resource pack", "", "Java and Bedrock packs, with the GUI art pre-selected."),
                HelpEntry("Import textures", "", "Images and GIFs, straight into the library."),
                HelpEntry(
                    "What import cannot do",
                    "",
                    "Percentage sizes, SVG paths and CSS layout have no fixed pixel " +
                        "position, so they are left out rather than guessed at. Every " +
                        "import says what it skipped.",
                ),
            ),
        ),
    )

    /** Every entry that actually has a key, for a compact shortcut list. */
    val shortcuts: List<HelpEntry>
        get() = sections.flatMap { it.entries }.filter { it.keys.isNotBlank() }
}
