package com.mcguidesigner.exporters

import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.templates.BuiltInTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The "everything" target: both edition packs, every code target and the
 * project document, produced in one pass.
 *
 * The risk here is not that a file is missing but that two sub-exports collide
 * - they all describe the same screen and several of them want to write a
 * `README.md` - so most of what is worth asserting is about paths.
 */
class BatchExportTest {

    private val javaProject = BuiltInTemplates["java-chest"]!!.instantiate("Sample Chest")
    private val bedrockProject = BuiltInTemplates["bedrock-hud"]!!.instantiate("Sample HUD")

    @Test
    fun everythingIsOfferedForBothEditions() {
        Edition.entries.forEach { edition ->
            assertTrue(
                ExportTarget.EVERYTHING in ExportManager.availableTargets(edition),
                "the batch target must be reachable from ${edition.displayName}",
            )
        }
    }

    @Test
    fun theBundleCarriesBothPacksTheCodeAndTheDocument() {
        val bundle = ExportManager.export(javaProject, ExportTarget.EVERYTHING)
        val paths = bundle.files.map { it.path }

        assertTrue(paths.any { it.contains("/java-edition/") && it.endsWith("pack.mcmeta") })
        assertTrue(paths.any { it.contains("/bedrock-edition/") && it.endsWith("manifest.json") })
        assertTrue(paths.any { it.contains("/project/") && it.endsWith(".mcgui") })
        assertTrue(paths.any { it == "${bundle.rootName}/README.md" })
    }

    @Test
    fun everyCodeTargetIsWrittenExactlyOnce() {
        val bundle = ExportManager.export(javaProject, ExportTarget.EVERYTHING)
        val code = bundle.files.map { it.path }.filter { it.contains("/code/") }

        assertEquals(CodeTarget.entries.size, code.size, "one file per language, no more and no fewer")
        assertEquals(code.size, code.toSet().size)
    }

    @Test
    fun everyPathLivesUnderTheBundleRoot() {
        val bundle = ExportManager.export(javaProject, ExportTarget.EVERYTHING)
        assertTrue(bundle.files.all { it.path.startsWith("${bundle.rootName}/") })
    }

    @Test
    fun theSubExportsDoNotCollide() {
        // Both packs and the batch bundle itself write a README; if the folder
        // prefixes were wrong they would overwrite each other in the zip.
        val bundle = ExportManager.export(javaProject, ExportTarget.EVERYTHING)
        val paths = bundle.files.map { it.path }

        assertEquals(paths.size, paths.toSet().size, "a duplicate path silently loses a file")
        assertTrue(paths.count { it.endsWith("README.md") } >= 3)
    }

    @Test
    fun itCarriesTheCrossEditionWarningRatherThanHidingIt() {
        // Exporting a Bedrock screen as a Java pack loses things. "Everything"
        // does that on purpose, so it has to say so.
        val bundle = ExportManager.export(bedrockProject, ExportTarget.EVERYTHING)
        assertTrue(
            bundle.warnings.any { it.code == com.mcguidesigner.core.validation.IssueCode.EDITION_PARITY },
            "a batch export that ports across editions must report the parity loss",
        )
    }

    @Test
    fun warningsAreNotRepeatedOncePerSubExport() {
        val bundle = ExportManager.export(javaProject, ExportTarget.EVERYTHING)
        assertEquals(
            bundle.warnings.size,
            bundle.warnings.toSet().size,
            "the same issue reported four times reads as four problems",
        )
    }

    @Test
    fun theOutputIsDeterministic() {
        // The templates are regenerated and diffed on every build; a batch
        // export that reordered itself would make that diff useless.
        val first = ExportManager.export(javaProject, ExportTarget.EVERYTHING)
        val second = ExportManager.export(javaProject, ExportTarget.EVERYTHING)

        assertEquals(first.files.map { it.path }, second.files.map { it.path })
        assertEquals(first.rootName, second.rootName)
    }

    @Test
    fun theReadmeNamesWhatIsInEachFolder() {
        val bundle = ExportManager.export(javaProject, ExportTarget.EVERYTHING)
        val readme = bundle.files
            .filterIsInstance<ExportFile.Text>()
            .first { it.path == "${bundle.rootName}/README.md" }
            .content

        listOf("java-edition/", "bedrock-edition/", "code/", "project/").forEach { folder ->
            assertTrue(folder in readme, "the README has to explain `$folder`")
        }
        assertFalse(readme.isBlank())
    }

    @Test
    fun movingAFileKeepsItsPayload() {
        val text = ExportFile.Text("a/b.txt", "hello")
        assertEquals("hello", (text.movedTo("c/b.txt") as ExportFile.Text).content)

        val binary = ExportFile.Binary("a/b.png", "AAAA")
        assertEquals("AAAA", (binary.movedTo("c/b.png") as ExportFile.Binary).base64)
    }
}
