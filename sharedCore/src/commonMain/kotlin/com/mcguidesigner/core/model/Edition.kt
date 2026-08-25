package com.mcguidesigner.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a document is being designed *for*.
 *
 * The targets differ in far more than cosmetics: they have different widget
 * vocabularies, different layout units and completely different export
 * formats, so this is baked into the project itself rather than being a
 * view-level toggle.
 *
 * Still called "edition" because two of the three are Minecraft editions and
 * that is what the project format, the file on disk and every existing
 * document already call the field. [OTHER] is the odd one out, and renaming a
 * serialised key to make one enum entry read better is not a trade worth
 * making.
 */
@Serializable
enum class Edition {
    @SerialName("java")
    JAVA,

    @SerialName("bedrock")
    BEDROCK,

    /**
     * Interfaces that are not Minecraft at all.
     *
     * Apps, websites, tools - anything with buttons and text fields rather
     * than inventory slots. It has no resource pack to export and no game to
     * imitate, so what it offers instead is the code exports that were already
     * there: HTML, React, SwiftUI, Flutter and Android XML.
     */
    @SerialName("other")
    OTHER;

    val displayName: String
        get() = when (this) {
            JAVA -> "Java Edition"
            BEDROCK -> "Bedrock Edition"
            OTHER -> "Other UIs"
        }

    /** Short tag used in file names, export folders and style lookups. */
    val slug: String
        get() = when (this) {
            JAVA -> "java"
            BEDROCK -> "bedrock"
            OTHER -> "other"
        }

    /** Whether this target is a Minecraft edition with a pack format. */
    val isMinecraft: Boolean get() = this != OTHER

    /**
     * The edition a screen would be ported *to*, or null.
     *
     * Only meaningful between the two Minecraft editions, which share a widget
     * vocabulary and differ in how they express it - porting between them is a
     * normal thing to want. There is no counterpart for [OTHER]: a login form
     * is not a version of an inventory screen, and the parity warnings that
     * read this would be nonsense.
     */
    val counterpart: Edition?
        get() = when (this) {
            JAVA -> BEDROCK
            BEDROCK -> JAVA
            OTHER -> null
        }
}

/**
 * Which device class a layout is authored for.  Bedrock projects frequently
 * ship two variants of the same screen; Java projects are practically always
 * [DESKTOP].
 */
@Serializable
enum class TargetForm {
    @SerialName("desktop")
    DESKTOP,

    @SerialName("mobile")
    MOBILE;

    val displayName: String get() = if (this == DESKTOP) "Desktop" else "Mobile / Touch"
}

/**
 * Visual states an interactive element can be rendered in.  Every interactive
 * definition in the catalog declares support for these so the preview and the
 * exporters can emit per-state skins.
 */
@Serializable
enum class InteractionState {
    @SerialName("normal")
    NORMAL,

    @SerialName("hover")
    HOVER,

    @SerialName("pressed")
    PRESSED,

    @SerialName("focused")
    FOCUSED,

    @SerialName("disabled")
    DISABLED;

    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }

    companion object {
        /** States that are meaningful for touch-only Bedrock layouts. */
        val touchStates = listOf(NORMAL, PRESSED, DISABLED)
    }
}
