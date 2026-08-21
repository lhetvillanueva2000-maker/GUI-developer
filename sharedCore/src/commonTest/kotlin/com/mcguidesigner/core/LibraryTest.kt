package com.mcguidesigner.core

import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.library.LibraryTexture
import com.mcguidesigner.core.library.Prefab
import com.mcguidesigner.core.library.PrefabLibrary
import com.mcguidesigner.core.library.TextureLibrary
import com.mcguidesigner.core.library.contentKey
import com.mcguidesigner.core.model.Anchor
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.IntPoint
import com.mcguidesigner.core.model.TextureAsset
import com.mcguidesigner.core.model.TextureValue
import com.mcguidesigner.core.model.walkAll
import com.mcguidesigner.core.serialization.LibrarySerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Prefabs: a group of elements saved once and dropped into later designs.
 *
 * The properties that matter are geometric and they are easy to get subtly
 * wrong: the group has to keep its internal spacing, land where it was asked
 * to, and never collide with ids already in the document.
 */
class PrefabTest {

    private fun controllerWithRow(): EditorController {
        val controller = EditorController.blank(Edition.JAVA, "Prefab source")
        controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(20, 40))
        controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(20, 70))
        return controller
    }

    /** Vertical gap between the two buttons, however the grid snapped them. */
    private fun gapOf(ys: List<Int>): Int = ys.sorted().let { it[1] - it[0] }

    @Test
    fun capturingTheSelectionKeepsTheRelativeLayout() {
        val controller = controllerWithRow()
        controller.selectAll()
        val sourceGap = gapOf(controller.project.elements.map { it.bounds.y })

        val prefab = assertNotNull(controller.prefabFromSelection("Button row"))

        assertEquals(2, prefab.elements.size)
        assertEquals(Edition.JAVA, prefab.edition)
        // Rebased to its own origin: the top-left of the group is (0, 0)...
        assertEquals(0, prefab.elements.minOf { it.bounds.x })
        assertEquals(0, prefab.elements.minOf { it.bounds.y })
        // ...and the spacing the user actually laid out is untouched.
        assertEquals(sourceGap, gapOf(prefab.elements.map { it.bounds.y }))
    }

    @Test
    fun capturedRootsAreFlattenedToTopLeft() {
        val controller = controllerWithRow()
        controller.selectAll()
        val prefab = assertNotNull(controller.prefabFromSelection("Row"))

        // The anchor described a relationship to a parent the prefab no longer
        // has; keeping it would move the parts on insert.
        assertTrue(prefab.elements.all { it.anchor == Anchor.TOP_LEFT })
    }

    @Test
    fun insertingPlacesTheGroupWhereItWasAsked() {
        val controller = controllerWithRow()
        controller.selectAll()
        val prefab = assertNotNull(controller.prefabFromSelection("Row"))
        val sourceGap = gapOf(prefab.elements.map { it.bounds.y })

        val target = EditorController.blank(Edition.JAVA, "Target")
        val inserted = target.insertPrefab(prefab, IntPoint(64, 96))

        assertEquals(2, inserted.size)
        val bounds = inserted.mapNotNull { target.project.element(it)?.bounds }
        // Lands exactly where it was asked - insert does not re-snap, or a
        // prefab would arrive subtly different every time.
        assertEquals(64, bounds.minOf { it.x })
        assertEquals(96, bounds.minOf { it.y })
        assertEquals(sourceGap, gapOf(bounds.map { it.y }))
    }

    @Test
    fun insertingTwiceDoesNotReuseIds() {
        val controller = controllerWithRow()
        controller.selectAll()
        val prefab = assertNotNull(controller.prefabFromSelection("Row"))

        val target = EditorController.blank(Edition.JAVA, "Target")
        val first = target.insertPrefab(prefab, IntPoint(0, 0))
        val second = target.insertPrefab(prefab, IntPoint(40, 0))

        assertEquals(emptySet(), first intersect second, "a second insert must mint fresh ids")
        val ids = target.project.elements.walkAll().map { it.id }.toList()
        assertEquals(ids.size, ids.toSet().size, "duplicate ids would break selection and export")
    }

    @Test
    fun insertingBringsTheTexturesItNeeds() {
        val controller = EditorController.blank(Edition.JAVA, "Skinned")
        val asset = TextureAsset(
            id = "tex_button",
            name = "button",
            format = "png",
            width = 16,
            height = 16,
            dataBase64 = "AAAA",
        )
        controller.addTexture(asset)
        val id = assertNotNull(controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(10, 10)))
        controller.setProp(id, "texture", TextureValue(asset.id))
        controller.select(id)

        val prefab = assertNotNull(controller.prefabFromSelection("Skinned button"))
        assertEquals(1, prefab.textures.size, "the referenced texture has to travel with the prefab")

        val target = EditorController.blank(Edition.JAVA, "Fresh")
        target.insertPrefab(prefab, IntPoint(0, 0))
        assertNotNull(
            target.project.texture("tex_button"),
            "a prefab used in another project must arrive skinned, not blank",
        )
    }

    @Test
    fun unreferencedTexturesAreLeftBehind() {
        val controller = EditorController.blank(Edition.JAVA, "Heavy")
        controller.addTexture(
            TextureAsset("tex_unused", "unused", "png", 16, 16, "AAAA"),
        )
        controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(10, 10))
        controller.selectAll()

        val prefab = assertNotNull(controller.prefabFromSelection("Plain button"))
        assertTrue(prefab.textures.isEmpty(), "a prefab must not carry art it never uses")
    }

    @Test
    fun capturingNothingReturnsNothing() {
        val controller = EditorController.blank(Edition.JAVA)
        controller.clearSelection()
        assertNull(controller.prefabFromSelection("Empty"))
    }

    @Test
    fun aChildSelectedWithItsParentIsNotCapturedTwice() {
        val controller = EditorController.blank(Edition.JAVA, "Nested")
        val panel = assertNotNull(controller.addElement(ElementCatalog.PANEL_FRAME, IntPoint(10, 10)))
        val button = assertNotNull(controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(20, 20), parentId = panel))
        controller.select(panel)
        controller.select(button, additive = true)

        val prefab = assertNotNull(controller.prefabFromSelection("Panel"))
        // The button already travels inside the panel's subtree.
        assertEquals(1, prefab.elements.size)
        assertEquals(2, prefab.elementCount)
    }

    @Test
    fun theLibraryReplacesRatherThanDuplicatesById() {
        val prefab = Prefab(id = "p1", name = "One", edition = Edition.JAVA)
        val library = PrefabLibrary.Empty.with(prefab).with(prefab.copy(name = "Renamed"))

        assertEquals(1, library.prefabs.size)
        assertEquals("Renamed", library["p1"]?.name)
    }

    @Test
    fun searchingCoversNameDescriptionAndTags() {
        val library = PrefabLibrary.Empty.with(
            Prefab(
                id = "p1",
                name = "Hotbar",
                edition = Edition.BEDROCK,
                description = "Nine slots along the bottom",
                tags = listOf("hud", "touch"),
            ),
        )
        assertEquals(1, library.search("hotbar").size)
        assertEquals(1, library.search("slots").size)
        assertEquals(1, library.search("touch").size)
        assertEquals(0, library.search("chest").size)
        assertEquals(0, library.search("hotbar", edition = Edition.JAVA).size)
    }

    @Test
    fun theLibraryRoundTripsThroughJson() {
        val controller = controllerWithRow()
        controller.selectAll()
        val prefab = assertNotNull(controller.prefabFromSelection("Row"))
        val library = PrefabLibrary.Empty.with(prefab)

        val restored = LibrarySerializer.decodePrefabs(LibrarySerializer.encodePrefabs(library))

        assertEquals(library, restored, "prefabs carry polymorphic property values; the codec has to keep them")
    }

    @Test
    fun aCorruptLibraryFileReadsAsEmptyRatherThanThrowing() {
        // Losing saved prefabs is bad; failing to start the app is worse.
        assertEquals(PrefabLibrary.Empty, LibrarySerializer.decodePrefabs("{ not json"))
        assertEquals(PrefabLibrary.Empty, LibrarySerializer.decodePrefabs(""))
    }
}

/**
 * The cross-project texture library.
 *
 * Its whole value is not re-importing the same art, so the dedup rule - same
 * bytes means same texture, whatever it was called - is the part worth pinning
 * down.
 */
class TextureLibraryTest {

    private fun asset(id: String, name: String, data: String = "AAAA") =
        TextureAsset(id = id, name = name, format = "png", width = 16, height = 16, dataBase64 = data)

    private fun entry(id: String, name: String, data: String = "AAAA", source: String = "", tags: List<String> = emptyList()) =
        LibraryTexture(asset = asset(id, name, data), source = source, tags = tags)

    @Test
    fun theSameBytesUnderADifferentNameAreNotAddedTwice() {
        val library = TextureLibrary.Empty
            .with(entry("a", "widgets.png"))
            .with(entry("b", "widgets-copy.png"))

        assertEquals(1, library.size)
        assertEquals("widgets.png", library.entries.first().asset.name, "the first name wins")
    }

    @Test
    fun differentBytesAreDifferentTextures() {
        val library = TextureLibrary.Empty
            .with(entry("a", "one", data = "AAAA"))
            .with(entry("b", "two", data = "BBBB"))

        assertEquals(2, library.size)
    }

    @Test
    fun aDuplicateImportMergesItsTags() {
        val library = TextureLibrary.Empty
            .with(entry("a", "widgets", tags = listOf("gui")))
            .with(entry("b", "widgets", tags = listOf("vanilla", "gui")))

        assertEquals(1, library.size)
        assertEquals(listOf("gui", "vanilla"), library.entries.first().tags.sorted())
    }

    @Test
    fun aDuplicateImportDoesNotBlankAnExistingSource() {
        val library = TextureLibrary.Empty
            .with(entry("a", "widgets", source = "Faithful 32x"))
            .with(entry("b", "widgets", source = ""))

        assertEquals("Faithful 32x", library.entries.first().source)
    }

    @Test
    fun contentKeyIgnoresIdAndName() {
        assertEquals(asset("a", "one").contentKey(), asset("b", "two").contentKey())
        assertNotEquals(asset("a", "one").contentKey(), asset("a", "one", data = "ZZZZ").contentKey())
    }

    @Test
    fun containsAnswersFromTheBytes() {
        val library = TextureLibrary.Empty.with(entry("a", "widgets"))
        assertTrue(library.contains(asset("totally-different-id", "renamed")))
        assertFalse(library.contains(asset("c", "other", data = "CCCC")))
    }

    @Test
    fun filtersCombine() {
        val library = TextureLibrary.Empty
            .with(entry("a", "button", data = "AAAA", source = "Vanilla", tags = listOf("gui")))
            .with(entry("b", "stone", data = "BBBB", source = "Faithful", tags = listOf("block")))

        assertEquals(1, library.search(query = "button").size)
        assertEquals(1, library.search(source = "Faithful").size)
        assertEquals(0, library.search(query = "button", source = "Faithful").size)
        assertEquals(2, library.search().size)
    }

    @Test
    fun removingOneLeavesTheRest() {
        val library = TextureLibrary.Empty
            .with(entry("a", "one", data = "AAAA"))
            .with(entry("b", "two", data = "BBBB"))

        val id = library.entries.first().id
        assertEquals(1, library.without(id).size)
    }

    @Test
    fun theLibraryRoundTripsThroughJson() {
        val library = TextureLibrary.Empty.with(entry("a", "widgets", source = "Vanilla", tags = listOf("gui")))
        assertEquals(library, LibrarySerializer.decodeTextures(LibrarySerializer.encodeTextures(library)))
    }

    @Test
    fun aCorruptLibraryFileReadsAsEmpty() {
        assertEquals(TextureLibrary.Empty, LibrarySerializer.decodeTextures("<html>404</html>"))
    }
}
