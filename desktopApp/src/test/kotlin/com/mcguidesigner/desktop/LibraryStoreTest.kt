package com.mcguidesigner.desktop

import com.mcguidesigner.core.library.LibraryTexture
import com.mcguidesigner.core.library.Prefab
import com.mcguidesigner.core.library.PrefabLibrary
import com.mcguidesigner.core.library.TextureLibrary
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.StringValue
import com.mcguidesigner.core.model.TextureAsset
import com.mcguidesigner.desktop.io.LibraryStore
import com.mcguidesigner.desktop.io.Workspace
import com.mcguidesigner.styles.theme.ThemeMode
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The on-disk prefab and texture libraries.
 *
 * These files accumulate for months and are never explicitly saved by the user,
 * so the behaviour that matters is what happens to them when something goes
 * wrong: a corrupt file, a half-written one, a directory that is not there yet.
 * None of those may cost the user the ability to start the app.
 */
class LibraryStoreTest {

    private lateinit var directory: File

    @BeforeTest
    fun redirectWorkspace() {
        directory = Files.createTempDirectory("mcgui-library-test").toFile()
        Workspace.useDirectoryForTesting(directory)
    }

    @AfterTest
    fun restoreWorkspace() {
        Workspace.useDirectoryForTesting(null)
        directory.deleteRecursively()
    }

    private fun samplePrefab() = Prefab(
        id = "prefab_1",
        name = "Header row",
        edition = Edition.JAVA,
        description = "A title and two buttons",
        tags = listOf("chrome"),
        elements = listOf(
            GuiElement(
                id = "el_1",
                type = "label.text",
                name = "Title",
                bounds = IntRect(0, 0, 60, 10),
                props = mapOf("text" to StringValue("Inventory")),
            ),
        ),
        textures = listOf(TextureAsset("tex_1", "panel", "png", 16, 16, "AAAA")),
        createdAtMillis = 1_700_000_000_000,
    )

    // -- Prefabs -----------------------------------------------------------

    @Test
    fun prefabsRoundTripThroughDisk() {
        val library = PrefabLibrary.Empty.with(samplePrefab())
        assertTrue(LibraryStore.savePrefabs(library))

        assertEquals(library, LibraryStore.loadPrefabs())
    }

    @Test
    fun aMissingPrefabFileIsAnEmptyLibraryRatherThanAFailure() {
        assertEquals(PrefabLibrary.Empty, LibraryStore.loadPrefabs())
    }

    @Test
    fun aCorruptPrefabFileDegradesToEmpty() {
        File(directory, "prefabs.json").writeText("{ this was truncated mid-w")
        assertEquals(PrefabLibrary.Empty, LibraryStore.loadPrefabs())
    }

    @Test
    fun savingLeavesNoTemporaryFileBehind() {
        LibraryStore.savePrefabs(PrefabLibrary.Empty.with(samplePrefab()))
        val leftovers = directory.listFiles()?.filter { it.name.endsWith(".tmp") }.orEmpty()
        assertTrue(leftovers.isEmpty(), "found ${leftovers.map { it.name }}")
    }

    @Test
    fun savingTwiceReplacesRatherThanAppends() {
        LibraryStore.savePrefabs(PrefabLibrary.Empty.with(samplePrefab()))
        LibraryStore.savePrefabs(PrefabLibrary.Empty.with(samplePrefab().copy(id = "prefab_2", name = "Other")))

        val loaded = LibraryStore.loadPrefabs()
        assertEquals(1, loaded.prefabs.size)
        assertEquals("Other", loaded.prefabs.single().name)
    }

    // -- Textures ----------------------------------------------------------

    @Test
    fun texturesRoundTripThroughDisk() {
        val library = TextureLibrary.Empty.with(
            LibraryTexture(
                asset = TextureAsset("tex_1", "widgets", "png", 256, 256, "AAAA"),
                tags = listOf("gui"),
                source = "Vanilla",
                addedAtMillis = 1_700_000_000_000,
            ),
        )
        assertTrue(LibraryStore.saveTextures(library))

        assertEquals(library, LibraryStore.loadTextures())
    }

    @Test
    fun aCorruptTextureFileDegradesToEmpty() {
        File(directory, "texture-library.json").writeText("not json at all")
        assertEquals(TextureLibrary.Empty, LibraryStore.loadTextures())
    }

    @Test
    fun theLibrarySizeOnDiskIsReportable() {
        assertEquals(0L, LibraryStore.textureLibraryBytes())
        LibraryStore.saveTextures(
            TextureLibrary.Empty.with(
                LibraryTexture(TextureAsset("tex_1", "widgets", "png", 16, 16, "AAAA")),
            ),
        )
        assertTrue(LibraryStore.textureLibraryBytes() > 0)
    }
}

/**
 * The appearance settings added to the preferences file.
 *
 * Preferences outlive the build that wrote them, so an unknown or missing value
 * has to land on a sane default rather than failing the read.
 */
class AppearancePreferencesTest {

    private lateinit var directory: File

    @BeforeTest
    fun redirectWorkspace() {
        directory = Files.createTempDirectory("mcgui-appearance-test").toFile()
        Workspace.useDirectoryForTesting(directory)
    }

    @AfterTest
    fun restoreWorkspace() {
        Workspace.useDirectoryForTesting(null)
        directory.deleteRecursively()
    }

    @Test
    fun theThemeChoiceSurvivesARestart() {
        Workspace.savePreferences(Workspace.loadPreferences().copy(themeMode = ThemeMode.LIGHT.name))
        assertEquals(ThemeMode.LIGHT, Workspace.loadPreferences().theme)
    }

    @Test
    fun anUnknownThemeNameFallsBackToDark() {
        // A preferences file written by a future build that renamed the enum
        // must not leave the app with no theme at all.
        Workspace.savePreferences(Workspace.loadPreferences().copy(themeMode = "HIGH_CONTRAST_PURPLE"))
        assertEquals(ThemeMode.DARK, Workspace.loadPreferences().theme)
    }

    @Test
    fun aFileFromBeforeTheSystemThemeWasRemovedStillReads() {
        // 1.6.0 and earlier wrote "SYSTEM", which no longer exists. It has to
        // land on dark - what SYSTEM resolved to on desktop anyway - rather
        // than leaving the app themeless on the first launch after upgrading.
        Workspace.savePreferences(Workspace.loadPreferences().copy(themeMode = "SYSTEM"))
        assertEquals(ThemeMode.DARK, Workspace.loadPreferences().theme)
    }

    @Test
    fun theBackdropDefaultsToOnAndAnimated() {
        val defaults = Workspace.loadPreferences()
        assertTrue(defaults.backdropEnabled)
        assertTrue(defaults.backdropMotion)
        assertEquals(ThemeMode.DARK, defaults.theme)
    }

    @Test
    fun turningTheBackdropOffSticks() {
        Workspace.savePreferences(
            Workspace.loadPreferences().copy(backdropEnabled = false, backdropMotion = false),
        )
        val loaded = Workspace.loadPreferences()
        assertTrue(!loaded.backdropEnabled)
        assertTrue(!loaded.backdropMotion)
    }

    @Test
    fun aPreferencesFileFromAnOlderBuildStillReads() {
        // No appearance keys at all - exactly what the previous release wrote.
        File(directory, "preferences.json").writeText(
            """{ "windowWidth": 1280, "windowHeight": 800, "showLeftDock": false }""",
        )
        val loaded = Workspace.loadPreferences()

        assertEquals(1280, loaded.windowWidth)
        assertTrue(!loaded.showLeftDock)
        assertEquals(ThemeMode.DARK, loaded.theme, "a missing key must take the default, not fail the read")
        assertTrue(loaded.backdropEnabled)
    }
}
