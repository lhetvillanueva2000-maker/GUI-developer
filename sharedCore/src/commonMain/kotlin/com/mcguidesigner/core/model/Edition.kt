package com.mcguidesigner.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The two Minecraft editions the designer can target.
 *
 * The editions differ in far more than cosmetics: they have different widget
 * vocabularies, different layout units and completely different export
 * formats, so the edition is baked into the project itself rather than being a
 * view-level toggle.
 */
@Serializable
enum class Edition {
    @SerialName("java")
    JAVA,

    @SerialName("bedrock")
    BEDROCK;

    val displayName: String
        get() = when (this) {
            JAVA -> "Java Edition"
            BEDROCK -> "Bedrock Edition"
        }

    /** Short tag used in file names, export folders and style lookups. */
    val slug: String
        get() = when (this) {
            JAVA -> "java"
            BEDROCK -> "bedrock"
        }

    val other: Edition
        get() = when (this) {
            JAVA -> BEDROCK
            BEDROCK -> JAVA
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
