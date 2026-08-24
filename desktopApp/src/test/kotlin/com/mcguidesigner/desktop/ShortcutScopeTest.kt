package com.mcguidesigner.desktop

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The editor's shortcuts are installed as `onPreviewKeyEvent`, so they see
 * every key press *before* whatever has focus. Anything they claim is a key
 * that no text field and no other screen will ever receive - which is what a
 * design tool wants over a canvas and nothing else.
 */
class ShortcutScopeTest {

    @Test
    fun `shortcuts work in the editor with nothing else open`() {
        assertTrue(editorShortcutsApply(AppScreen.EDITOR, ActiveDialog.NONE, Key.Delete))
        assertTrue(editorShortcutsApply(AppScreen.EDITOR, ActiveDialog.NONE, Key.V))
        assertTrue(editorShortcutsApply(AppScreen.EDITOR, ActiveDialog.NONE, Key.Escape))
    }

    @Test
    fun `no editor shortcut fires on the home screen`() {
        // The bug: Delete on home ran requestDeleteSelection and opened a
        // confirmation about elements that were not on screen, and V, H and M
        // changed the tool in an editor that was not showing.
        listOf(Key.Delete, Key.Backspace, Key.V, Key.H, Key.M, Key.F1, Key.Escape).forEach { key ->
            assertFalse(
                editorShortcutsApply(AppScreen.HOME, ActiveDialog.NONE, key),
                "$key must not reach the editor from home",
            )
        }
    }

    @Test
    fun `a modal dialog keeps the keyboard apart from Escape`() {
        // The bug: Backspace in any dialog text field reached the canvas first
        // and deleted the selected element instead of a character.
        assertFalse(editorShortcutsApply(AppScreen.EDITOR, ActiveDialog.PROJECT_SETTINGS, Key.Backspace))
        assertFalse(editorShortcutsApply(AppScreen.EDITOR, ActiveDialog.PROJECT_SETTINGS, Key.Delete))
        assertFalse(editorShortcutsApply(AppScreen.EDITOR, ActiveDialog.SAVE_PREFAB, Key.V))
        assertFalse(editorShortcutsApply(AppScreen.EDITOR, ActiveDialog.EXPORT, Key.A))
    }

    @Test
    fun `Escape still reaches the ladder while a dialog is open`() {
        assertTrue(editorShortcutsApply(AppScreen.EDITOR, ActiveDialog.EXPORT, Key.Escape))
    }

    @Test
    fun `every dialog blocks typing keys`() {
        ActiveDialog.entries.filter { it != ActiveDialog.NONE }.forEach { dialog ->
            assertFalse(
                editorShortcutsApply(AppScreen.EDITOR, dialog, Key.Backspace),
                "$dialog let Backspace through to the canvas",
            )
        }
    }
}
