package com.mcguidesigner.core.util

import kotlin.random.Random

/**
 * Deterministic-length, collision-resistant identifiers.
 *
 * `kotlin.uuid.Uuid` is still experimental across all targets we build for, so
 * the core generates its own short ids: 12 base-36 characters seeded from the
 * platform RNG, which is far more than enough for a single document.
 */
object Ids {
    private const val ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz"

    fun random(length: Int = 12): String = buildString(length) {
        repeat(length) { append(ALPHABET[Random.nextInt(ALPHABET.length)]) }
    }

    /** `prefix_ab12cd34ef56` - readable in exported JSON and diffs. */
    fun prefixed(prefix: String): String = "${slug(prefix)}_${random()}"

    /** Lower-cases and strips anything that is not `[a-z0-9_]`. */
    fun slug(raw: String): String =
        raw.lowercase()
            .map { if (it.isLetterOrDigit()) it else '_' }
            .joinToString("")
            .trim('_')
            .replace(Regex("_+"), "_")
            .ifEmpty { "item" }

    /**
     * Returns [desired] if it is unused, otherwise appends the first free
     * ` 2`, ` 3`, ... suffix. Used for element names and duplicate handling.
     */
    fun uniqueName(desired: String, taken: Set<String>): String {
        if (desired !in taken) return desired
        val base = desired.trimEnd { it.isDigit() || it == ' ' }.ifEmpty { desired }
        var counter = 2
        while ("$base $counter" in taken) counter++
        return "$base $counter"
    }
}
