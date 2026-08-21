package com.mcguidesigner.core

import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.packs.PackKind
import com.mcguidesigner.core.packs.PackTextureRole
import com.mcguidesigner.core.packs.ResourcePackScan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Classifying the contents of a Minecraft resource pack.
 *
 * This runs against real archives full of other people's files, so the rules
 * have to be forgiving about layout and strict about what counts as art: a
 * misfiled `.json` imported as a texture is a blank slot the user then has to
 * hunt down.
 */
class ResourcePackScanTest {

    private val javaPack = listOf(
        "pack.mcmeta",
        "pack.png",
        "assets/minecraft/textures/gui/widgets.png",
        "assets/minecraft/textures/gui/container/generic_54.png",
        "assets/minecraft/textures/item/diamond.png",
        "assets/minecraft/textures/block/stone.png",
        "assets/minecraft/textures/font/ascii.png",
        "assets/minecraft/lang/en_us.json",
        "assets/minecraft/sounds/random/click.ogg",
    )

    private val bedrockPack = listOf(
        "manifest.json",
        "pack_icon.png",
        "textures/ui/button_borderless_light.png",
        "textures/ui/hotbar_start_cap.png",
        "textures/items/apple.png",
        "textures/blocks/dirt.png",
        "ui/_ui_defs.json",
    )

    // -- Kind detection ----------------------------------------------------

    @Test
    fun aJavaPackIsRecognisedByItsLayout() {
        assertEquals(PackKind.JAVA_RESOURCE_PACK, ResourcePackScan.detectKind(javaPack))
        assertEquals(Edition.JAVA, ResourcePackScan.detectKind(javaPack).edition)
    }

    @Test
    fun aBedrockPackIsRecognisedByItsLayout() {
        assertEquals(PackKind.BEDROCK_RESOURCE_PACK, ResourcePackScan.detectKind(bedrockPack))
        assertEquals(Edition.BEDROCK, ResourcePackScan.detectKind(bedrockPack).edition)
    }

    @Test
    fun aPackNestedInsideAFolderIsStillRecognised() {
        // Zipping the folder rather than its contents is the single most common
        // way people package a pack, and rejecting it would be indefensible.
        val nested = javaPack.map { "My Pack v2/$it" }
        assertEquals(PackKind.JAVA_RESOURCE_PACK, ResourcePackScan.detectKind(nested))
    }

    @Test
    fun aPlainFolderOfImagesIsAcceptedRatherThanRejected() {
        val kind = ResourcePackScan.detectKind(listOf("button.png", "panel.png", "notes.txt"))
        assertEquals(PackKind.PLAIN_ARCHIVE, kind)
        assertNull(kind.edition)
    }

    @Test
    fun aJavaPackCarryingAStrayManifestIsStillAJavaPack() {
        assertEquals(
            PackKind.JAVA_RESOURCE_PACK,
            ResourcePackScan.detectKind(javaPack + "manifest.json"),
        )
    }

    // -- Classification ----------------------------------------------------

    @Test
    fun onlyImagesAreImportable() {
        assertNull(ResourcePackScan.classify("assets/minecraft/lang/en_us.json"))
        assertNull(ResourcePackScan.classify("assets/minecraft/sounds/random/click.ogg"))
        assertNull(ResourcePackScan.classify("pack.mcmeta"))
        assertNotNull(ResourcePackScan.classify("assets/minecraft/textures/gui/widgets.png"))
    }

    @Test
    fun directoryEntriesAreSkipped() {
        assertNull(ResourcePackScan.classify("assets/minecraft/textures/gui/"))
        assertNull(ResourcePackScan.classify(""))
    }

    @Test
    fun macOsMetadataTwinsAreSkipped() {
        // Zips made on macOS carry a shadow copy of every file; importing them
        // yields several hundred entries that decode to nothing.
        assertNull(ResourcePackScan.classify("__MACOSX/assets/textures/gui/widgets.png"))
        assertNull(ResourcePackScan.classify("assets/textures/gui/._widgets.png"))
    }

    @Test
    fun guiArtIsRecognisedInBothLayouts() {
        assertEquals(
            PackTextureRole.GUI,
            ResourcePackScan.classify("assets/minecraft/textures/gui/widgets.png")?.role,
        )
        assertEquals(
            PackTextureRole.GUI,
            ResourcePackScan.classify("textures/ui/button_borderless_light.png")?.role,
        )
    }

    @Test
    fun otherRolesAreSeparatedFromGuiArt() {
        assertEquals(PackTextureRole.ITEM, ResourcePackScan.classify("textures/items/apple.png")?.role)
        assertEquals(PackTextureRole.BLOCK, ResourcePackScan.classify("textures/blocks/dirt.png")?.role)
        assertEquals(PackTextureRole.FONT, ResourcePackScan.classify("assets/minecraft/textures/font/ascii.png")?.role)
        assertEquals(PackTextureRole.OTHER, ResourcePackScan.classify("pack.png")?.role)
    }

    @Test
    fun theJavaNamespaceIsRead() {
        assertEquals("minecraft", ResourcePackScan.classify("assets/minecraft/textures/gui/widgets.png")?.namespace)
        assertEquals("mymod", ResourcePackScan.classify("assets/mymod/textures/gui/panel.png")?.namespace)
        assertNull(ResourcePackScan.classify("textures/ui/panel.png")?.namespace)
    }

    @Test
    fun theDisplayNameIsTheFileNameWithoutItsExtension() {
        assertEquals(
            "widgets",
            ResourcePackScan.classify("assets/minecraft/textures/gui/widgets.png")?.displayName,
        )
    }

    @Test
    fun caseDoesNotDecideWhetherSomethingIsAnImage() {
        assertTrue(ResourcePackScan.isImage("Widgets.PNG"))
        assertFalse(ResourcePackScan.isImage("readme.PNG.txt"))
    }

    // -- Whole-archive scan ------------------------------------------------

    @Test
    fun guiArtComesFirstInTheScan() {
        val scan = ResourcePackScan.scan(javaPack)
        assertEquals(PackTextureRole.GUI, scan.textures.first().role)
        assertEquals(2, scan.countFor(PackTextureRole.GUI))
    }

    @Test
    fun theScanIsStableBetweenRuns() {
        // The importer's list must not reshuffle between two views of the same
        // archive, or the checkboxes the user ticked would move under them.
        assertEquals(
            ResourcePackScan.scan(javaPack).textures,
            ResourcePackScan.scan(javaPack.shuffled()).textures,
        )
    }

    @Test
    fun duplicatePathsAreCollapsed() {
        val scan = ResourcePackScan.scan(javaPack + javaPack)
        assertEquals(scan.textures.size, scan.textures.map { it.path }.toSet().size)
    }

    @Test
    fun rolesAreGroupedInDeclarationOrderAndEmptyOnesAreDropped() {
        val roles = ResourcePackScan.scan(javaPack).byRole().map { it.first }
        assertEquals(roles.sortedBy { it.ordinal }, roles)
        assertFalse(PackTextureRole.ENTITY in roles)
    }

    @Test
    fun anArchiveWithNoImagesScansEmpty() {
        assertTrue(ResourcePackScan.scan(listOf("readme.txt", "pack.mcmeta")).isEmpty)
    }

    // -- Naming ------------------------------------------------------------

    @Test
    fun theFallbackPackNameIsReadableRatherThanAFileName() {
        assertEquals("Faithful 32x", ResourcePackScan.packNameFromFileName("Faithful_32x.zip"))
        assertEquals("john smith gui", ResourcePackScan.packNameFromFileName("/downloads/john-smith-gui.mcpack"))
        assertEquals("Imported pack", ResourcePackScan.packNameFromFileName(".zip"))
    }
}
