package com.mcguidesigner.desktop

import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.desktop.io.DesktopPreferences
import com.mcguidesigner.desktop.io.Workspace
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers what the shell remembers between runs.
 *
 * These are the bits of an editor nobody notices until they break, and when
 * they do break they lose someone's work, so they are worth pinning down.
 */
class WorkspaceTest {

    private lateinit var temporary: File

    @BeforeTest
    fun redirectWorkspace() {
        temporary = File(System.getProperty("java.io.tmpdir"), "mcgui-test-${System.nanoTime()}")
        Workspace.useDirectoryForTesting(temporary)
    }

    @AfterTest
    fun restoreWorkspace() {
        Workspace.useDirectoryForTesting(null)
        temporary.deleteRecursively()
    }

    @Test
    fun preferencesSurviveARoundTrip() {
        val written = DesktopPreferences(
            recentFiles = listOf("/tmp/a.mcgui", "/tmp/b.mcgui"),
            windowWidth = 1234,
            windowHeight = 777,
            windowX = 40,
            windowY = 60,
            maximized = true,
            showBottomDock = true,
            lastEdition = Edition.BEDROCK.name,
            showWelcomeOnStart = false,
        )
        Workspace.savePreferences(written)

        assertEquals(written, Workspace.loadPreferences())
        assertEquals(Edition.BEDROCK, Workspace.loadPreferences().edition)
    }

    @Test
    fun aMissingPreferencesFileReadsAsDefaults() {
        assertEquals(DesktopPreferences(), Workspace.loadPreferences())
    }

    /**
     * Preferences outlive the build that wrote them. A file from a newer
     * version - or one that was truncated by a hard shutdown - has to degrade
     * to "first run" rather than stop the app from starting.
     */
    @Test
    fun aCorruptOrForeignPreferencesFileFallsBackToDefaults() {
        temporary.mkdirs()
        File(temporary, "preferences.json").writeText("{ this is not json")
        assertEquals(DesktopPreferences(), Workspace.loadPreferences())

        File(temporary, "preferences.json").writeText("""{"windowWidth":900,"somethingNew":true}""")
        val loaded = Workspace.loadPreferences()
        assertEquals(900, loaded.windowWidth, "known keys should still be read")
        assertEquals(DesktopPreferences().windowHeight, loaded.windowHeight, "unknown keys must not fail the load")
    }

    @Test
    fun anUnknownEditionNameFallsBackToJava() {
        assertEquals(Edition.JAVA, DesktopPreferences(lastEdition = "SOMETHING_ELSE").edition)
    }

    @Test
    fun recentFilesAreNewestFirstDeduplicatedAndCapped() {
        var recents = emptyList<File>()
        repeat(14) { index -> recents = Workspace.withRecent(recents, File("/tmp/project$index.mcgui")) }

        assertEquals(10, recents.size, "the list must stay capped")
        assertEquals("project13.mcgui", recents.first().name, "most recent first")

        // Re-opening something already in the list moves it to the front
        // instead of adding a second entry.
        val existing = recents[4]
        recents = Workspace.withRecent(recents, existing)
        assertEquals(existing.absolutePath, recents.first().absolutePath)
        assertEquals(10, recents.size)
        assertEquals(1, recents.count { it.absolutePath == existing.absolutePath })
    }

    @Test
    fun recentEntriesThatNoLongerExistAreNotOffered() {
        val real = File(temporary, "real.mcgui").apply { parentFile.mkdirs(); writeText("{}") }
        val preferences = DesktopPreferences(
            recentFiles = listOf(real.absolutePath, "/definitely/not/here.mcgui"),
        )
        assertEquals(listOf(real.absolutePath), preferences.existingRecents().map { it.absolutePath })
    }

    // -- Crash recovery ----------------------------------------------------

    @Test
    fun aRecoverySnapshotRoundTripsWithItsMetadata() {
        val project = BuiltInTemplates.demo.instantiate("Half Finished Screen")
        Workspace.writeRecoverySnapshot(project, "/home/someone/work.mcgui")

        val recovered = Workspace.pendingRecovery()
        assertNotNull(recovered)
        assertEquals(project, recovered.project)
        assertEquals("Half Finished Screen", recovered.marker.projectName)
        assertEquals("/home/someone/work.mcgui", recovered.originalFile?.path)
        assertTrue(recovered.marker.savedAtMillis > 0L)
    }

    /**
     * The snapshot's presence is the only signal that the last session was
     * killed, so a clean save or exit has to remove it - otherwise the app
     * offers to recover work the user already has on disk.
     */
    @Test
    fun clearingRemovesTheSnapshotSoStartupDoesNotOfferIt() {
        Workspace.writeRecoverySnapshot(BuiltInTemplates.demo.instantiate(), null)
        assertNotNull(Workspace.pendingRecovery())

        Workspace.clearRecovery()
        assertNull(Workspace.pendingRecovery())
    }

    @Test
    fun noSnapshotMeansNothingToRecover() {
        assertNull(Workspace.pendingRecovery())
    }

    @Test
    fun anUnreadableSnapshotIsDiscardedRatherThanOffered() {
        temporary.mkdirs()
        File(temporary, "recovery.mcgui").writeText("not a project")
        assertNull(Workspace.pendingRecovery())
        assertFalse(File(temporary, "recovery.mcgui").exists(), "a broken snapshot should be cleaned up")
    }
}

/**
 * The unsaved-changes guard.
 *
 * Every path that replaces the document runs through [AppState.guardUnsaved],
 * so a regression here silently throws away work.
 */
class UnsavedChangesGuardTest {

    private fun appState() = AppState(BuiltInTemplates.demo.instantiate())

    @Test
    fun aCleanDocumentRunsTheActionImmediately() {
        val app = appState()
        var ran = false

        app.guardUnsaved("start a new project") { ran = true }

        assertTrue(ran, "nothing is at stake, so there is nothing to ask about")
        assertEquals(ActiveDialog.NONE, app.dialog)
    }

    @Test
    fun aDirtyDocumentPromptsInsteadOfRunning() {
        val app = appState()
        app.controller.renameProject("Edited")
        var ran = false

        app.guardUnsaved("start a new project") { ran = true }

        assertFalse(ran, "the action must wait for the user's answer")
        assertEquals(ActiveDialog.UNSAVED_CHANGES, app.dialog)
        assertEquals("start a new project", app.pendingActionLabel)
    }

    @Test
    fun cancellingLeavesTheDocumentAloneAndDropsTheAction() {
        val app = appState()
        app.controller.renameProject("Edited")
        var ran = false
        app.guardUnsaved("open another project") { ran = true }

        app.cancelPendingAction()

        assertFalse(ran)
        assertEquals(ActiveDialog.NONE, app.dialog)
        assertTrue(app.controller.current.dirty, "cancelling must not quietly mark the document saved")
    }

    @Test
    fun discardingRunsTheActionThatWasWaiting() {
        val app = appState()
        app.controller.renameProject("Edited")
        var ran = false
        app.guardUnsaved("load a template") { ran = true }

        app.resolveUnsavedByDiscarding()

        assertTrue(ran)
        assertEquals(ActiveDialog.NONE, app.dialog)
    }

    /**
     * A second prompt must not inherit the first one's pending action - that
     * would run the wrong thing after the user answered a different question.
     */
    @Test
    fun aCancelledActionIsNotReplayedByTheNextPrompt() {
        val app = appState()
        app.controller.renameProject("Edited")
        var first = false
        var second = false

        app.guardUnsaved("open another project") { first = true }
        app.cancelPendingAction()
        app.guardUnsaved("quit the designer") { second = true }
        app.resolveUnsavedByDiscarding()

        assertFalse(first, "the cancelled action must be forgotten")
        assertTrue(second)
    }

    @Test
    fun recoveredWorkIsRestoredAsUnsaved() {
        val app = appState()
        val project = BuiltInTemplates["java-options"]!!.instantiate("Recovered")
        val marker = Workspace.RecoveryMarker(null, "Recovered", System.currentTimeMillis())

        app.adoptRecovery(Workspace.Recovery(project, marker))

        assertEquals("Recovered", app.controller.project.name)
        assertTrue(
            app.controller.current.dirty,
            "recovered work was never written to the user's file, so it still needs saving",
        )
    }

    @Test
    fun markUnsavedAndMarkSavedAreOpposites() {
        val controller = EditorController(BuiltInTemplates.demo.instantiate())
        assertFalse(controller.current.dirty)

        controller.markUnsaved()
        assertTrue(controller.current.dirty)

        controller.markSaved("/tmp/x.mcgui")
        assertFalse(controller.current.dirty)
        assertEquals("/tmp/x.mcgui", controller.current.filePath)
    }
}
