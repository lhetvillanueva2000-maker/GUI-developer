package com.mcguidesigner.exporters

import com.mcguidesigner.core.catalog.ElementCatalog
import com.mcguidesigner.core.model.ColorValue
import com.mcguidesigner.core.model.CanvasSpec
import com.mcguidesigner.core.model.Edition
import com.mcguidesigner.core.model.EnumValue
import com.mcguidesigner.core.model.GuiElement
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.Insets
import com.mcguidesigner.core.model.IntRect
import com.mcguidesigner.core.model.IntValue
import com.mcguidesigner.core.model.ListValue
import com.mcguidesigner.core.model.ShapeKind
import com.mcguidesigner.core.model.StringValue
import com.mcguidesigner.core.model.TextureAsset
import com.mcguidesigner.core.model.TextureValue
import com.mcguidesigner.core.templates.BuiltInTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exporting shapes, animated images and custom elements.
 *
 * The point of these is that the new element types must not fall out of the
 * pipeline silently: a design that exports without its shapes is worse than
 * one that refuses to export, because nothing tells you they went missing.
 */
class CustomContentExportTest {

    /** A one-pixel-wide four-frame strip; the pixels do not matter here. */
    private fun animatedTexture(
        id: String = "anim1",
        frames: Int = 4,
        width: Int = 16,
        frameHeight: Int = 16,
        nineSlice: Insets = Insets.Zero,
        delays: List<Int> = emptyList(),
    ) = TextureAsset(
        id = id,
        name = "spinner",
        format = "png",
        width = width,
        height = frameHeight * frames,
        dataBase64 = "",
        nineSlice = nineSlice,
        frameCount = frames,
        frameTimeTicks = 3,
        frameDelaysMillis = delays,
    )

    private fun projectWithCustomContent(edition: Edition = Edition.JAVA): GuiProject {
        val base = BuiltInTemplates.forEdition(edition).first().instantiate()
        return base.copy(
            textures = base.textures + animatedTexture(),
            elements = base.elements + listOf(
                GuiElement(
                    id = "shape1",
                    type = ElementCatalog.SHAPE_CUSTOM,
                    name = "Badge",
                    bounds = IntRect(10, 10, 40, 40),
                    props = ElementCatalog.require(ElementCatalog.SHAPE_CUSTOM).defaultProps(edition) +
                        mapOf(
                            "shape" to EnumValue(ShapeKind.STAR.id),
                            "sides" to IntValue(5),
                            "fillColor" to ColorValue(0xFFFFCC00),
                            "rotation" to IntValue(30),
                        ),
                ),
                GuiElement(
                    id = "anim1el",
                    type = ElementCatalog.IMAGE_ANIMATED,
                    name = "Spinner",
                    bounds = IntRect(60, 10, 32, 32),
                    props = ElementCatalog.require(ElementCatalog.IMAGE_ANIMATED).defaultProps(edition) +
                        mapOf("texture" to TextureValue("anim1")),
                ),
                GuiElement(
                    id = "custom1",
                    type = ElementCatalog.CUSTOM_ELEMENT,
                    name = "Widget",
                    bounds = IntRect(10, 60, 80, 24),
                    props = ElementCatalog.require(ElementCatalog.CUSTOM_ELEMENT).defaultProps(edition) +
                        mapOf(
                            "customType" to StringValue("my_gauge"),
                            "attributes" to ListValue(
                                listOf(StringValue("needle_color=red"), StringValue("max=100")),
                            ),
                        ),
                ),
            ),
        )
    }

    // -- Every target keeps the new elements --------------------------------

    @Test
    fun `every code target survives a project full of custom content`() {
        Edition.entries.forEach { edition ->
            val project = projectWithCustomContent(edition)
            CodeGenerator.targetsFor(edition).forEach { target ->
                val generated = CodeGenerator.generate(project, target)
                assertTrue(
                    generated.source.length > 200,
                    "${target.id} produced almost nothing for a ${edition.slug} project",
                )
            }
        }
    }

    @Test
    fun `the SVG export draws a star as a real polygon`() {
        val svg = CodeGenerator.generate(projectWithCustomContent(), CodeTarget.SVG).source

        assertTrue(svg.startsWith("<?xml"), svg.take(80))
        assertTrue("<polygon points=" in svg, "a star should export as a polygon:\n$svg")
        assertTrue("rotate(30" in svg, "the shape's rotation should survive")
        assertTrue(svg.trimEnd().endsWith("</svg>"))
    }

    @Test
    fun `the SVG export uses ellipse and rect for the curved shapes`() {
        fun svgFor(kind: ShapeKind): String {
            val base = BuiltInTemplates.demo.instantiate()
            return CodeGenerator.generate(
                base.copy(
                    elements = listOf(
                        GuiElement(
                            id = "s",
                            type = ElementCatalog.SHAPE_CUSTOM,
                            name = "S",
                            bounds = IntRect(0, 0, 20, 20),
                            props = mapOf("shape" to EnumValue(kind.id)),
                        ),
                    ),
                ),
                CodeTarget.SVG,
            ).source
        }

        assertTrue("<ellipse" in svgFor(ShapeKind.ELLIPSE))
        assertTrue("rx=" in svgFor(ShapeKind.ROUNDED_RECTANGLE), "rounded corners need a radius")
    }

    @Test
    fun `the CSS export clips polygons and rounds the curved shapes`() {
        val css = CodeGenerator.generate(projectWithCustomContent(), CodeTarget.CSS).source
        assertTrue("clip-path: polygon(" in css, "a star needs a clip-path:\n$css")
    }

    @Test
    fun `the HTML export animates a frame strip with steps`() {
        val html = CodeGenerator.generate(projectWithCustomContent(), CodeTarget.HTML_CSS).source

        assertTrue("@keyframes" in html, "an animated image needs keyframes")
        assertTrue("background-size: 100% 400%" in html, "the strip is four frames tall")

        // The two things that decide whether frames land squarely or smear,
        // both consequences of background-position percentages being relative
        // to (container - image) rather than absolute offsets.
        assertTrue(
            "steps(4, jump-none)" in html,
            "plain steps(N) lands between frames; jump-none lands on them:\n$html",
        )
        assertTrue(
            "to { background-position: 0 100%; }" in html,
            "100% is the last frame for any strip length; N*100% overshoots:\n$html",
        )
    }

    // -- Native sidecars -----------------------------------------------------

    @Test
    fun `an animated texture gets an mcmeta sidecar in the Java pack`() {
        val bundle = ExportManager.export(projectWithCustomContent(), ExportTarget.JAVA_RESOURCE_PACK)
        val paths = bundle.files.map { it.path }

        assertTrue(
            paths.any { it.endsWith("spinner.png.mcmeta") },
            "the animation has to travel with the texture:\n${paths.joinToString("\n")}",
        )
        assertTrue(paths.any { it.endsWith("atlases/gui.json") }, "the sprites need an atlas source")
    }

    @Test
    fun `an animated texture gets an mcmeta sidecar in the Bedrock pack`() {
        val bundle = ExportManager.export(
            projectWithCustomContent(Edition.BEDROCK),
            ExportTarget.BEDROCK_UI_PACK,
        )
        assertTrue(bundle.files.map { it.path }.any { it.endsWith("spinner.png.mcmeta") })
    }

    @Test
    fun `a plain still texture gets no pointless sidecar`() {
        val still = TextureAsset(
            id = "still",
            name = "plain",
            format = "png",
            width = 16,
            height = 16,
            dataBase64 = "",
        )
        assertEquals(null, NativeAssets.mcmetaFor(still))
    }

    @Test
    fun `a nine-sliced texture describes its scaling`() {
        val skin = TextureAsset(
            id = "skin",
            name = "button",
            format = "png",
            width = 200,
            height = 20,
            dataBase64 = "",
            nineSlice = Insets(2, 3, 4, 5),
        )
        val mcmeta = assertNotNull(NativeAssets.mcmetaFor(skin))

        assertTrue("\"type\": \"nine_slice\"" in mcmeta, mcmeta)
        assertTrue("\"left\": 2" in mcmeta, mcmeta)
        assertTrue("\"bottom\": 5" in mcmeta, mcmeta)
        assertTrue("\"animation\"" !in mcmeta, "a still is not an animation:\n$mcmeta")
    }

    @Test
    fun `a texture that is both sliced and animated describes both`() {
        val mcmeta = assertNotNull(
            NativeAssets.mcmetaFor(animatedTexture(nineSlice = Insets.all(2))),
        )
        assertTrue("\"gui\"" in mcmeta, mcmeta)
        assertTrue("\"animation\"" in mcmeta, mcmeta)
        assertTrue("\"frametime\": 3" in mcmeta, mcmeta)
    }

    @Test
    fun `uneven source timing is preserved per frame`() {
        val mcmeta = assertNotNull(
            NativeAssets.mcmetaFor(animatedTexture(frames = 3, delays = listOf(100, 250, 100))),
        )
        assertTrue("\"index\": 0" in mcmeta, mcmeta)
        assertTrue("\"time\": 5" in mcmeta, "250ms is five ticks:\n$mcmeta")
        assertTrue("\"time\": 2" in mcmeta, "100ms is two ticks:\n$mcmeta")
    }

    @Test
    fun `the Java definitions document explains itself even with no textures`() {
        val bare = BuiltInTemplates["java-options"]!!.instantiate().copy(textures = emptyList())
        val document = NativeAssets.javaDefinitionsDocument(bare)

        assertTrue("atlases/gui.json" in document)
        assertTrue("No texture in this project needs a .mcmeta sidecar" in document, document)
    }

    @Test
    fun `the Java definitions document names the file each block belongs in`() {
        val document = NativeAssets.javaDefinitionsDocument(projectWithCustomContent())

        assertTrue("textures/gui/spinner.png.mcmeta" in document, document)
        assertTrue("4 frames stacked vertically" in document, document)
    }

    // -- Bedrock JSON UI -----------------------------------------------------

    @Test
    fun `custom content reaches the Bedrock screen JSON`() {
        val project = projectWithCustomContent(Edition.BEDROCK)
        val json = BedrockEditionExporter.screenJson(project, "mcgui_test")

        assertTrue("\$designer_shape" in json, "the shape's own description should survive:\n$json")
        assertTrue("my_gauge" in json, "a custom element's type name must reach the export")
        assertTrue("needle_color" in json, "its extra properties must be passed through")
        assertTrue("\$designer_frames" in json, "the animation's frame count should be recorded")
    }

    @Test
    fun `custom element attributes without a key are skipped rather than written blank`() {
        val base = BuiltInTemplates.forEdition(Edition.BEDROCK).first().instantiate()
        val project = base.copy(
            elements = listOf(
                GuiElement(
                    id = "c",
                    type = ElementCatalog.CUSTOM_ELEMENT,
                    name = "Widget",
                    bounds = IntRect(0, 0, 20, 20),
                    props = mapOf(
                        "customType" to StringValue("thing"),
                        "attributes" to ListValue(
                            listOf(StringValue("no-equals-sign"), StringValue("good=yes")),
                        ),
                    ),
                ),
            ),
        )

        val json = BedrockEditionExporter.screenJson(project, "ns")
        assertTrue("\"good\": \"yes\"" in json, json)
        assertTrue("no-equals-sign" !in json, "a line with no key has nowhere to go:\n$json")
    }

    // -- Everything ----------------------------------------------------------

    @Test
    fun `the everything bundle carries every code target plus both packs`() {
        val bundle = ExportManager.export(projectWithCustomContent(), ExportTarget.EVERYTHING)
        val paths = bundle.files.map { it.path }

        CodeTarget.entries.forEach { target ->
            assertTrue(
                paths.any { it.contains("/code/") && it.endsWith(".${target.fileExtension}") },
                "no ${target.id} file in the everything bundle:\n${paths.joinToString("\n")}",
            )
        }
        assertTrue(paths.any { it.contains("/java-edition/") })
        assertTrue(paths.any { it.contains("/bedrock-edition/") })
    }

    @Test
    fun `a turned button is turned in every code export, not only a shape`() {
        // Rotation used to be applied by exactly one renderer and, in SVG, by
        // exactly one branch of one generator - so turning a button showed up
        // in CSS and React and vanished from the vector export. Now that the
        // canvas turns everything, every target has to agree with it.
        val project = GuiProject(
            id = "proj_turn",
            name = "Turned",
            edition = Edition.JAVA,
            canvas = CanvasSpec(width = 100, height = 60),
            elements = listOf(
                GuiElement(
                    id = "el_btn",
                    type = ElementCatalog.BUTTON_NORMAL,
                    name = "Tilted",
                    bounds = IntRect(10, 10, 40, 20),
                    props = mapOf("rotation" to IntValue(37), "label" to StringValue("Go")),
                ),
            ),
        )

        val svg = CodeGenerator.generate(project, CodeTarget.SVG).source
        assertTrue("rotate(37" in svg, "the SVG export dropped the rotation:\n$svg")

        val css = CodeGenerator.generate(project, CodeTarget.HTML_CSS).source
        assertTrue("rotate(37deg)" in css, "the HTML export dropped the rotation")

        val react = CodeGenerator.generate(project, CodeTarget.REACT_JSX).source
        assertTrue("rotate(37deg)" in react, "the React export dropped the rotation")

        val swift = CodeGenerator.generate(project, CodeTarget.SWIFTUI).source
        assertTrue("degrees(37)" in swift, "the SwiftUI export dropped the rotation")

        val xml = CodeGenerator.generate(project, CodeTarget.ANDROID_XML).source
        assertTrue("android:rotation=\"37\"" in xml, "the Android XML export dropped the rotation")
    }
}
