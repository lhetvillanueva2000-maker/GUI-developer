package com.mcguidesigner.core

import com.mcguidesigner.core.image.AnimatedTextureImport
import com.mcguidesigner.core.image.Deflate
import com.mcguidesigner.core.image.GifDecoder
import com.mcguidesigner.core.image.PngWriter
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The GIF -> frame-strip pipeline, checked against the JDK's own codecs.
 *
 * These live in `desktopTest` rather than `commonTest` on purpose: verifying a
 * compressor by asserting on its output bytes only proves it is consistent with
 * itself.  Round-tripping through `java.util.zip` and `javax.imageio` instead
 * proves the streams are the real formats, which is what actually matters -
 * Minecraft will not be reading them with our decoder.
 */
class ImagePipelineTest {

    // -- DEFLATE ------------------------------------------------------------

    private fun inflate(compressed: ByteArray, expectedSize: Int): ByteArray {
        val inflater = Inflater()
        inflater.setInput(compressed)
        val out = ByteArray(expectedSize)
        val produced = inflater.inflate(out)
        inflater.end()
        return out.copyOf(produced)
    }

    @Test
    fun `zlib output inflates back to the original bytes`() {
        val samples = listOf(
            ByteArray(0),
            byteArrayOf(42),
            ByteArray(1000) { 0 },
            ByteArray(5000) { (it % 251).toByte() },
            "the quick brown fox ".repeat(200).encodeToByteArray(),
            // Long runs plus a repeated block: the two cases the LZ77 matcher
            // and the self-referential length/distance path have to get right.
            (ByteArray(300) { 7 } + ByteArray(300) { (it % 13).toByte() }).let { it + it },
        )

        for (sample in samples) {
            val round = inflate(Deflate.zlib(sample), sample.size + 16)
            assertContentEquals(sample, round, "round trip failed for ${sample.size} bytes")
        }
    }

    @Test
    fun `zlib actually compresses repetitive data`() {
        val repetitive = "minecraft gui designer ".repeat(500).encodeToByteArray()
        val compressed = Deflate.zlib(repetitive)
        assertTrue(
            compressed.size < repetitive.size / 4,
            "expected real compression, got ${compressed.size} from ${repetitive.size}",
        )
    }

    // -- PNG ----------------------------------------------------------------

    @Test
    fun `written PNGs are readable by ImageIO and pixel-exact`() {
        val width = 37
        val height = 19
        val pixels = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            val alpha = if (x < 4) 0x40 else 0xFF
            (alpha shl 24) or ((x * 7 % 256) shl 16) or ((y * 13 % 256) shl 8) or ((x + y) % 256)
        }

        val png = PngWriter.encode(width, height, pixels)
        val image = ImageIO.read(ByteArrayInputStream(png))

        assertNotNull(image, "ImageIO could not read the PNG we wrote")
        assertEquals(width, image.width)
        assertEquals(height, image.height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertEquals(
                    pixels[y * width + x],
                    image.getRGB(x, y),
                    "pixel mismatch at $x,$y",
                )
            }
        }
    }

    @Test
    fun `fully transparent pixels survive the round trip`() {
        val png = PngWriter.encode(2, 2, intArrayOf(0, 0, -1, -1))
        val image = ImageIO.read(ByteArrayInputStream(png))
        assertEquals(0, image.getRGB(0, 0) ushr 24, "top-left should be fully transparent")
        assertEquals(0xFF, image.getRGB(0, 1) ushr 24 and 0xFF, "bottom-left should be opaque")
    }

    // -- GIF ----------------------------------------------------------------

    /** Encodes [frames] as one GIF using the JDK's writer. */
    private fun writeGif(frames: List<BufferedImage>, delayCentiseconds: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        val writer = ImageIO.getImageWritersByFormatName("gif").next()
        MemoryCacheImageOutputStream(bytes).use { stream ->
            writer.output = stream
            val params: ImageWriteParam = writer.defaultWriteParam
            writer.prepareWriteSequence(null)
            for (frame in frames) {
                val type = javax.imageio.ImageTypeSpecifier.createFromRenderedImage(frame)
                val metadata = writer.getDefaultImageMetadata(type, params)
                val format = metadata.nativeMetadataFormatName
                val root = metadata.getAsTree(format) as IIOMetadataNode

                val control = IIOMetadataNode("GraphicControlExtension").apply {
                    setAttribute("disposalMethod", "none")
                    setAttribute("userInputFlag", "FALSE")
                    setAttribute("transparentColorFlag", "FALSE")
                    setAttribute("delayTime", delayCentiseconds.toString())
                    setAttribute("transparentColorIndex", "0")
                }
                // Replace the default control block so the delay sticks.
                var existing: org.w3c.dom.Node? = root.firstChild
                while (existing != null) {
                    if (existing.nodeName == "GraphicControlExtension") {
                        root.removeChild(existing)
                        break
                    }
                    existing = existing.nextSibling
                }
                root.appendChild(control)
                metadata.setFromTree(format, root)
                writer.writeToSequence(IIOImage(frame, null, metadata), params)
            }
            writer.endWriteSequence()
        }
        writer.dispose()
        return bytes.toByteArray()
    }

    private fun solidFrame(width: Int, height: Int, rgb: Int): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).apply {
            for (y in 0 until height) for (x in 0 until width) setRGB(x, y, rgb)
        }

    @Test
    fun `a single-frame GIF decodes to the right pixels`() {
        val gif = writeGif(listOf(solidFrame(8, 5, 0xFF8800)), delayCentiseconds = 0)
        val decoded = GifDecoder.decode(gif)

        assertNotNull(decoded)
        assertEquals(8, decoded.width)
        assertEquals(5, decoded.height)
        assertEquals(1, decoded.frames.size)
        assertEquals(
            0xFFFF8800.toInt(),
            decoded.frames[0].pixels[0],
            "GIF palettes are exact, so the colour should survive unchanged",
        )
    }

    @Test
    fun `a multi-frame GIF decodes every frame in order`() {
        val gif = writeGif(
            listOf(
                solidFrame(6, 6, 0xFF0000),
                solidFrame(6, 6, 0x00FF00),
                solidFrame(6, 6, 0x0000FF),
            ),
            delayCentiseconds = 10,
        )
        val decoded = GifDecoder.decode(gif)

        assertNotNull(decoded)
        assertEquals(3, decoded.frames.size)
        assertEquals(0xFFFF0000.toInt(), decoded.frames[0].pixels[0])
        assertEquals(0xFF00FF00.toInt(), decoded.frames[1].pixels[0])
        assertEquals(0xFF0000FF.toInt(), decoded.frames[2].pixels[0])
        assertTrue(
            decoded.frames.all { it.delayMillis == 100 },
            "10 centiseconds is 100 ms, got ${decoded.frames.map { it.delayMillis }}",
        )
    }

    @Test
    fun `larger GIFs with real detail decode pixel-exact`() {
        // A gradient forces the LZW dictionary to actually grow and the
        // self-referential code path to fire, which a solid fill never does.
        val width = 64
        val height = 48
        val frame = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).apply {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    // Kept inside a 216-colour cube so the GIF's 256-entry
                    // palette is exact and the comparison is meaningful.
                    val r = (x / 13) * 51
                    val g = (y / 10) * 51
                    val b = ((x + y) / 23) * 51
                    setRGB(x, y, (r shl 16) or (g shl 8) or b)
                }
            }
        }

        val decoded = GifDecoder.decode(writeGif(listOf(frame), 5))
        assertNotNull(decoded)
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertEquals(
                    frame.getRGB(x, y) or (0xFF shl 24),
                    decoded.frames[0].pixels[y * width + x],
                    "pixel mismatch at $x,$y",
                )
            }
        }
    }

    @Test
    fun `non-GIF bytes are rejected rather than throwing`() {
        assertNull(GifDecoder.decode(PngWriter.encode(2, 2, IntArray(4))))
        assertNull(GifDecoder.decode(ByteArray(0)))
        assertNull(GifDecoder.decode("not an image at all".encodeToByteArray()))
    }

    // -- Import -------------------------------------------------------------

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `importing an animated GIF produces a readable vertical frame strip`() {
        val gif = writeGif(
            listOf(
                solidFrame(10, 4, 0xFF0000),
                solidFrame(10, 4, 0x00FF00),
            ),
            delayCentiseconds = 4,
        )

        val texture = AnimatedTextureImport.fromBytes(gif, id = "tex1", name = "spinner")
        assertNotNull(texture)
        assertEquals(2, texture.frameCount)
        assertEquals(10, texture.width)
        assertEquals(8, texture.height, "two 4px frames stacked vertically")
        assertEquals(4, texture.frameHeight)
        assertTrue(texture.isAnimated)
        // 40 ms rounds to one 50 ms tick.
        assertEquals(1, texture.frameTimeTicks)

        val strip = ImageIO.read(ByteArrayInputStream(Base64.decode(texture.dataBase64)))
        assertNotNull(strip, "the strip should be a valid PNG")
        assertEquals(10, strip.width)
        assertEquals(8, strip.height)
        assertEquals(0xFFFF0000.toInt(), strip.getRGB(0, 0), "frame 1 sits at the top")
        assertEquals(0xFF00FF00.toInt(), strip.getRGB(0, 4), "frame 2 sits below it")
    }

    @Test
    fun `a still image is not treated as an animation`() {
        val gif = writeGif(listOf(solidFrame(4, 4, 0x123456)), delayCentiseconds = 0)
        val texture = AnimatedTextureImport.fromBytes(gif, id = "tex2", name = "still")

        assertNotNull(texture)
        assertEquals(1, texture.frameCount)
        assertTrue(!texture.isAnimated)
        assertTrue(texture.frameDelaysMillis.isEmpty())
    }

    @Test
    fun `a PNG is not an animated source`() {
        val png = PngWriter.encode(2, 2, IntArray(4) { -1 })
        assertTrue(!AnimatedTextureImport.isAnimatedSource(png))
        assertNull(AnimatedTextureImport.fromBytes(png, "x", "x"))
    }

    // -- Frame sequences ----------------------------------------------------

    private fun frame(width: Int, height: Int, argb: Int) = AnimatedTextureImport.FramePixels(
        pixels = IntArray(width * height) { argb },
        width = width,
        height = height,
    )

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `separate images stack into one frame strip`() {
        val texture = assertNotNull(
            AnimatedTextureImport.fromFrameImages(
                frames = listOf(
                    frame(8, 6, 0xFFFF0000.toInt()),
                    frame(8, 6, 0xFF00FF00.toInt()),
                    frame(8, 6, 0xFF0000FF.toInt()),
                ),
                id = "seq",
                name = "walk",
                frameDelayMillis = 150,
            ),
        )

        assertEquals(3, texture.frameCount)
        assertEquals(8, texture.width)
        assertEquals(18, texture.height, "three 6px frames stacked")
        assertEquals(3, texture.frameTimeTicks, "150ms is three 50ms ticks")

        val strip = ImageIO.read(ByteArrayInputStream(Base64.decode(texture.dataBase64)))
        assertEquals(0xFFFF0000.toInt(), strip.getRGB(0, 0))
        assertEquals(0xFF00FF00.toInt(), strip.getRGB(0, 6))
        assertEquals(0xFF0000FF.toInt(), strip.getRGB(0, 12))
    }

    @Test
    fun `mismatched frame sizes are scaled to the first frame`() {
        val texture = assertNotNull(
            AnimatedTextureImport.fromFrameImages(
                frames = listOf(
                    frame(10, 10, 0xFFFFFFFF.toInt()),
                    frame(40, 40, 0xFF000000.toInt()),
                ),
                id = "seq",
                name = "mixed",
            ),
        )

        assertEquals(10, texture.width, "the first frame sets the size")
        assertEquals(20, texture.height)
        assertEquals(2, texture.frameCount)
    }

    @Test
    fun `an empty or single-frame sequence is not an animation`() {
        assertNull(AnimatedTextureImport.fromFrameImages(emptyList(), "x", "x"))

        val single = assertNotNull(
            AnimatedTextureImport.fromFrameImages(listOf(frame(4, 4, -1)), "x", "one"),
        )
        assertEquals(1, single.frameCount)
        assertTrue(!single.isAnimated, "one frame is a still")
    }

    @Test
    fun `mcmeta describes square frames without redundant dimensions`() {
        val gif = writeGif(List(3) { solidFrame(8, 8, 0x00FF00) }, delayCentiseconds = 10)
        val texture = assertNotNull(AnimatedTextureImport.fromBytes(gif, "t", "square"))

        val mcmeta = AnimatedTextureImport.mcmetaFor(texture)
        assertTrue("\"frametime\": 2" in mcmeta, mcmeta)
        assertTrue("\"frames\": [0, 1, 2]" in mcmeta, mcmeta)
        assertTrue("\"width\"" !in mcmeta, "square frames need no explicit width:\n$mcmeta")
    }

    @Test
    fun `mcmeta spells out dimensions for non-square frames`() {
        val gif = writeGif(List(2) { solidFrame(16, 4, 0x00FF00) }, delayCentiseconds = 10)
        val texture = assertNotNull(AnimatedTextureImport.fromBytes(gif, "t", "wide"))

        val mcmeta = AnimatedTextureImport.mcmetaFor(texture)
        assertTrue("\"width\": 16" in mcmeta, mcmeta)
        assertTrue("\"height\": 4" in mcmeta, mcmeta)
    }
}
