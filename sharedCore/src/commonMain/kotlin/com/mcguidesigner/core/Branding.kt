package com.mcguidesigner.core

/**
 * What the product is called, in one place.
 *
 * Every user-visible name comes from here - window titles, the home wordmark,
 * dialog copy, export headers, installer file names - so a rename is one edit
 * rather than a search across five modules, which is exactly how the previous
 * name ended up spelled three different ways ("Minecraft GUI Designer",
 * "MinecraftGuiDesigner", "MC GUI Designer") in files that were all supposed
 * to agree.
 *
 * What is deliberately *not* here: the Kotlin package names, the Gradle module
 * names and the Android `applicationId`. Those are identifiers, not names -
 * nobody reads them, renaming them touches every file in the repository for no
 * user-visible gain, and changing an `applicationId` makes Android treat the
 * result as a different app rather than an update.
 */
object Branding {

    /** The product. */
    const val NAME = "Surface Studio"

    /**
     * The version shown in the app.
     *
     * Kept in step with `mcgui.version` in gradle.properties by hand. The two
     * cannot simply be one value: Gradle names the artifacts and this names
     * what the running app says about itself, and no shared module has a
     * `project.version` to read. A mismatch is a cosmetic fib rather than a
     * broken build, which is exactly the kind of thing that rots unnoticed -
     * so it lives here, next to the name, with this comment on it.
     */
    const val VERSION = "1.6.0"

    /** The downloadable that installs it. */
    const val INSTALLER_NAME = "Surface Studio Installer"

    /** File-name-safe form, for artifacts and packages. */
    const val SLUG = "surface-studio"

    /** Wordmark form, for the home screen's top bar. */
    const val WORDMARK = "SURFACE"

    /** The second half of the wordmark, drawn inside a slot. */
    const val WORDMARK_SLOT = "STUDIO"

    /** One line, for window titles and store listings. */
    const val TAGLINE = "Design game and app interfaces, visually."

    /**
     * What the app is, for anybody who has just opened it and does not know.
     *
     * Kept as data rather than laid out in the composable that shows it,
     * because the same three answers belong in the store listing and the
     * README too, and three copies of a paragraph is three chances to update
     * two of them.
     */
    val explainer: List<ExplainerPoint> = listOf(
        ExplainerPoint(
            heading = "What this is",
            body = "A visual editor for interfaces. Blockbench does 3D models for Minecraft " +
                "and Blender does 3D for everything else; $NAME is the equivalent for the " +
                "flat side - menus, HUDs, inventories, forms and panels - in Minecraft " +
                "and, through its code exports, well beyond it.",
        ),
        ExplainerPoint(
            heading = "Who it is for",
            body = "Mod developers, app developers and UI designers who would rather place a " +
                "panel than count pixels in a JSON file. You lay the screen out by hand and " +
                "it writes the format the target actually reads, so the tedious half is done " +
                "and the design half is yours.",
        ),
        ExplainerPoint(
            heading = "How to use it",
            body = "Pick an edition above. Drag components onto the canvas, nudge them with " +
                "the arrow keys, and group what repeats into a prefab. When it looks right, " +
                "Export writes a resource pack, a project file, or source in the language " +
                "your target speaks. Everything is saved on your device - no account, no " +
                "upload, no telemetry.",
        ),
    )
}

/** One heading-and-paragraph pair of [Branding.explainer]. */
data class ExplainerPoint(val heading: String, val body: String)
