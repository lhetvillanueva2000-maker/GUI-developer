package com.mcguidesigner.core.editor

import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiProject

/**
 * Which of several open documents is in front, and what happens when one opens
 * or closes.
 *
 * The rules live here rather than in either shell because getting them subtly
 * different on desktop and Android is exactly the kind of divergence nobody
 * notices until somebody loses a document. They are pure functions over an
 * index and a size, so the awkward cases - closing the last tab, closing the
 * one in front, closing something to the left of it - are testable without a
 * window.
 */
object DocumentSet {

    /**
     * Which tab should be in front after the one at [closing] is removed.
     *
     * Closing the tab in front moves to its neighbour on the *right*, which is
     * what every editor does and what the hand expects: you close a document
     * and carry on down the row. Closing a tab to the left of the active one
     * has to shift the index down by one or the selection silently jumps to a
     * different document - the bug this function exists to make impossible.
     */
    fun activeAfterClose(active: Int, closing: Int, size: Int): Int {
        require(size > 0) { "cannot close a tab from an empty set" }
        val remaining = size - 1
        if (remaining == 0) return 0
        return when {
            closing < active -> active - 1
            closing > active -> active
            // The one in front went: stay at the same index, which is now the
            // tab that was to its right, unless it was the last.
            else -> active.coerceAtMost(remaining - 1)
        }
    }

    /**
     * The tab an edition card should land on, or null to open a new one.
     *
     * Tapping "Java" repeatedly should not litter the row with empty Java
     * documents, so an existing tab for that edition is brought forward
     * instead. The *first* one, not the nearest: "the Java tab" has to mean
     * the same tab every time or the button is a lottery.
     */
    fun existingTabFor(editions: List<Edition>, edition: Edition): Int? =
        editions.indexOfFirst { it == edition }.takeIf { it >= 0 }

    /** A name for a document that has never been saved. */
    fun untitledName(edition: Edition, existing: List<String>): String {
        val base = "Untitled ${edition.displayName}"
        if (base !in existing) return base
        var n = 2
        while ("$base $n" in existing) n++
        return "$base $n"
    }

    /** A blank project for [edition], named so it does not collide. */
    fun newDocument(edition: Edition, existingNames: List<String>): GuiProject =
        EditorController.newProject(edition, untitledName(edition, existingNames))
}
