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
                HelpEntry("Move pad", "", "The four arrows on the canvas. Its corner and step are set in Editor Settings."),
                HelpEntry("Rotate", "", "The rotate buttons in the toolbar, or the Rotation property."),
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
                HelpEntry("Toggle the grid", "Ctrl+G"),
                HelpEntry("Toggle rulers", "Ctrl+R"),
            ),
        ),

        HelpSection(
            "Building blocks",
            listOf(
                HelpEntry("Components", "F1", "The gallery: every component with a live preview."),
                HelpEntry("Shapes", "", "Rectangles through to stars, each with fill, outline and rotation."),
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
                HelpEntry("Everything at once", "Ctrl+Shift+E", "Every format above into one folder."),
                HelpEntry("Import", "", "Projects via File ▸ Open; Java and Bedrock resource packs via Import pack."),
            ),
        ),
    )

    /** Every entry that actually has a key, for a compact shortcut list. */
    val shortcuts: List<HelpEntry>
        get() = sections.flatMap { it.entries }.filter { it.keys.isNotBlank() }
}
