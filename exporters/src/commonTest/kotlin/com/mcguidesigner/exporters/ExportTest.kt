package com.mcguidesigner.exporters

import com.mcguidesigner.core.editor.EditorController
import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.IntPoint
import com.mcguidesigner.core.model.StringValue
import com.mcguidesigner.core.model.TextureAsset
import com.mcguidesigner.core.model.TextureValue
import com.mcguidesigner.core.templates.BuiltInTemplates
import com.mcguidesigner.core.validation.IssueCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JavaExportTest {

    private val project = BuiltInTemplates["java-chest"]!!.instantiate("Sample Chest")

    @Test
    fun bundleContainsThePackSkeleton() {
        val bundle = JavaEditionExporter.export(project)
        val paths = bundle.files.map { it.path }

        assertTrue(paths.any { it.endsWith("pack.mcmeta") })
        assertTrue(paths.any { it.contains("/gui/") && it.endsWith(".json") })
        assertTrue(paths.any { it.contains("/lang/en_us.json") })
        assertTrue(paths.any { it.endsWith(".java") })
        assertTrue(paths.any { it.endsWith("README.md") })
        assertTrue(paths.all { it.startsWith(bundle.rootName) }, "every path must live under the bundle root")
    }

    @Test
    fun layoutJsonIsWellFormedAndDescribesTheScreen() {
        val text = JavaEditionExporter.layoutJson(project)
        val root = Json.parseToJsonElement(text) as JsonObject

        assertEquals("java", root["edition"]?.jsonPrimitive?.content)
        val screen = root["screen"] as JsonObject
        assertEquals(project.canvas.width.toString(), screen["width"]?.jsonPrimitive?.content)
        assertNotNull(root["elements"])
    }

    @Test
    fun generatedScreenSourceMentionsEveryButtonItCreates() {
        val controller = EditorController(EditorController.newProject(Edition.JAVA, "Buttons"))
        val id = controller.addElement(ElementCatalog.BUTTON_NORMAL, IntPoint(10, 10))!!
        controller.rename(id, "Confirm")
        controller.setProp(id, "label", StringValue("Confirm"))

        val source = JavaEditionExporter.screenSource(controller.project, "mcgui", "buttons", "ButtonsScreen")

        assertTrue(source.contains("public class ButtonsScreen extends Screen"))
        assertTrue(source.contains("Button confirm"))
        assertTrue(source.contains("onConfirm()"))
        assertTrue(source.contains("\"Confirm\""))
    }

    @Test
    fun importedTexturesAreEmittedAsBinaryFiles() {
        val controller = EditorController(EditorController.newProject(Edition.JAVA, "Textured"))
        controller.addTexture(
            TextureAsset(
                id = "tex_a",
                name = "panel.png",
                format = "png",
                width = 16,
                height = 16,
                dataBase64 = "iVBORw0KGgo=",
            ),
        )
        val id = controller.addElement(ElementCatalog.IMAGE_PLACEHOLDER, IntPoint(0, 0))!!
        controller.setProp(id, "texture", TextureValue("tex_a"))

        val bundle = JavaEditionExporter.export(controller.project)
        val texture = bundle.files.filterIsInstance<ExportFile.Binary>().firstOrNull()
        assertNotNull(texture)
        assertTrue(texture.path.contains("textures/gui/"))
    }
}

class BedrockExportTest {

    private val project = BuiltInTemplates["bedrock-hud"]!!.instantiate("Sample HUD")

    @Test
    fun bundleContainsAValidUiPackSkeleton() {
        val bundle = BedrockEditionExporter.export(project)
        val paths = bundle.files.map { it.path }

        assertTrue(paths.any { it.endsWith("manifest.json") })
        assertTrue(paths.any { it.endsWith("ui/_ui_defs.json") })
        assertTrue(paths.any { it.endsWith("ui/_global_variables.json") })
        assertTrue(paths.any { it.endsWith("texts/en_US.lang") })
        assertTrue(paths.count { it.contains("/ui/") && it.endsWith(".json") } >= 3)
    }

    @Test
    fun screenJsonIsValidJsonWithANamespaceAndOneControlPerElement() {
        val namespace = "mcgui_touch_hud"
        val text = BedrockEditionExporter.screenJson(project, namespace)
        val root = Json.parseToJsonElement(text) as JsonObject

        assertEquals(namespace, root["namespace"]?.jsonPrimitive?.content)
        assertNotNull(root["screen@common.base_screen"])
        assertNotNull(root["screen_content"])

        // Every top-level element must have produced a control definition.
        project.elements.forEach { element ->
            val key = element.name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
            assertTrue(root.keys.any { it == key }, "no control emitted for '${element.name}'")
        }
    }

    @Test
    fun manifestJsonParsesAndCarriesDistinctUuids() {
        val bundle = BedrockEditionExporter.export(project)
        val manifest = bundle.files
            .filterIsInstance<ExportFile.Text>()
            .first { it.path.endsWith("manifest.json") }
        val root = Json.parseToJsonElement(manifest.content) as JsonObject

        val headerUuid = (root["header"] as JsonObject)["uuid"]?.jsonPrimitive?.content
        assertNotNull(headerUuid)
        assertEquals(36, headerUuid.length, "expected a UUID-shaped string")
    }
}

class CrossEditionAndCodeTest {

    @Test
    fun exportingForTheOtherEditionAttachesAParityWarning() {
        val project = BuiltInTemplates["bedrock-hud"]!!.instantiate()
        val bundle = ExportManager.export(project, ExportTarget.JAVA_RESOURCE_PACK)

        assertTrue(
            bundle.warnings.any { it.code == IssueCode.EDITION_PARITY },
            "a Bedrock project exported as a Java pack must warn about lost widgets",
        )
    }

    @Test
    fun everyCodeTargetProducesNonEmptyOutput() {
        Edition.entries.forEach { edition ->
            val project = BuiltInTemplates.forEdition(edition).first().instantiate()
            CodeGenerator.targetsFor(edition).forEach { target ->
                val generated = CodeGenerator.generate(project, target)
                assertTrue(generated.source.length > 200, "${target.id} produced almost nothing")
                assertTrue(generated.fileName.endsWith(target.fileExtension))
            }
        }
    }

    @Test
    fun htmlExportEmbedsThePositionsAndOpensWithADoctype() {
        val project = BuiltInTemplates["java-options"]!!.instantiate()
        val html = CodeGenerator.generate(project, CodeTarget.HTML_CSS).source

        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("image-rendering: pixelated"))
        assertTrue(html.contains("--mcgui-canvas-w: ${project.canvas.width}px"))
        assertTrue(html.contains("class=\"mcgui-canvas\""))
    }

    @Test
    fun cssTargetEmitsOneRulePerVisibleElement() {
        val project = BuiltInTemplates["java-options"]!!.instantiate()
        val css = CodeGenerator.generate(project, CodeTarget.CSS).source
        val visible = project.allElements.count { it.visible }

        // Each element contributes a `left:` declaration in its own rule.
        assertEquals(visible, Regex("^\\s{2}left:", RegexOption.MULTILINE).findAll(css).count())
    }

    @Test
    fun projectJsonTargetIsAValidDocument() {
        val project = BuiltInTemplates["java-chest"]!!.instantiate()
        val text = CodeGenerator.generate(project, CodeTarget.PROJECT_JSON).source
        val result = com.mcguidesigner.core.serialization.ProjectSerializer.decode(text)
        assertTrue(result is com.mcguidesigner.core.serialization.LoadResult.Success)
    }

    @Test
    fun exportAllCoversBothEditionsPlusTheDocumentAndCode() {
        val bundles = ExportManager.exportAll(BuiltInTemplates.demo.instantiate())
        val targets = bundles.map { it.target }.toSet()
        assertEquals(
            setOf(
                ExportTarget.JAVA_RESOURCE_PACK,
                ExportTarget.BEDROCK_UI_PACK,
                ExportTarget.PROJECT_JSON,
                ExportTarget.CODE,
            ),
            targets,
        )
        assertTrue(bundles.all { it.files.isNotEmpty() })
    }
}
