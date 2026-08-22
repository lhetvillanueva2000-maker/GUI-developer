package com.mcguidesigner.exporters

import com.mcguidesigner.core.image.AnimatedTextureImport
import com.mcguidesigner.core.model.GuiProject
import com.mcguidesigner.core.model.TextureAsset

/**
 * The sidecar files Minecraft itself reads to understand a GUI image.
 *
 * A resource pack that contains only PNGs tells the game *what* the pixels
 * are, never *how to draw them*: stretched, tiled, or nine-sliced so the
 * corners of a button stay square when it is resized; still, or animated at
 * some rate.  That information lives in `.mcmeta` sidecars and in the atlas
 * source list, and those files are as much a part of the export as the images.
 *
 * Everything here is vanilla format - nothing needs a mod to read it:
 *
 *  - `<texture>.png.mcmeta` carries `gui.scaling` (since the 1.20.2 GUI sprite
 *    system) and `animation` (which predates it by a decade).
 *  - `assets/<namespace>/atlases/gui.json` adds the pack's own directory to the
 *    GUI atlas, which is what makes the sprites addressable at all.
 *
 * Bedrock needs no equivalent: its JSON-UI documents describe drawing rules
 * inline, and [BedrockEditionExporter] already emits them.
 */
object NativeAssets {

    /**
     * The `.mcmeta` for one texture, or null when the defaults already
     * describe it.
     *
     * Writing a sidecar that says nothing would be noise in the pack, so a
     * plain stretched still gets none.
     */
    fun mcmetaFor(texture: TextureAsset): String? {
        val scaling = scalingBlock(texture)
        val animation = if (texture.isAnimated) animationBlock(texture) else null
        if (scaling == null && animation == null) return null

        val blocks = listOfNotNull(scaling, animation)
        return buildString {
            appendLine("{")
            blocks.forEachIndexed { index, block ->
                append(block)
                appendLine(if (index == blocks.lastIndex) "" else ",")
            }
            append("}")
        }
    }

    /**
     * `gui.scaling`, which decides what happens to the image when the widget
     * it skins is not the image's own size.
     *
     * Only nine-sliced textures get one: `stretch` is the game's default, so
     * spelling it out would add a file that changes nothing.
     */
    private fun scalingBlock(texture: TextureAsset): String? {
        if (!texture.hasNineSlice) return null
        val insets = texture.nineSlice
        return """
        |  "gui": {
        |    "scaling": {
        |      "type": "nine_slice",
        |      "width": ${texture.width},
        |      "height": ${texture.frameHeight},
        |      "border": {
        |        "left": ${insets.left},
        |        "top": ${insets.top},
        |        "right": ${insets.right},
        |        "bottom": ${insets.bottom}
        |      }
        |    }
        |  }
        """.trimMargin()
    }

    /** `animation`, reusing the writer the import path already agrees with. */
    private fun animationBlock(texture: TextureAsset): String =
        AnimatedTextureImport.mcmetaFor(texture)
            .removePrefix("{")
            .removeSuffix("}")
            .trim('\n')
            .trimEnd()

    /**
     * `assets/<namespace>/atlases/gui.json`.
     *
     * Atlas files from every namespace are merged, so contributing the pack's
     * own `textures/gui` directory here is what lets the screen reference its
     * sprites as `<namespace>:gui/<name>` rather than as raw file paths.
     */
    fun guiAtlasJson(namespace: String): String = """
    |{
    |  "sources": [
    |    {
    |      "type": "directory",
    |      "source": "gui",
    |      "prefix": "gui/"
    |    }
    |  ]
    |}
    """.trimMargin()

    /**
     * Every native Java definition for [project] as one annotated document.
     *
     * This is what the Export dialog shows for the "what the game reads"
     * target: the same content the pack ships, laid out with the path each
     * block belongs at, so it can be read top to bottom or copied file by
     * file.
     */
    fun javaDefinitionsDocument(project: GuiProject): String {
        val namespace = project.meta.namespace.ifBlank { "mcgui" }
        val described = project.textures.mapNotNull { texture ->
            mcmetaFor(texture)?.let { texture to it }
        }

        return buildString {
            appendLine("// Java Edition resource-pack GUI definitions for '${project.name}'.")
            appendLine("//")
            appendLine("// These are the files vanilla Minecraft reads to understand the images in")
            appendLine("// this pack: how to scale them, and how to animate them. No mod required.")
            appendLine("// Each block below is one file; the path above it is where it goes.")
            appendLine()
            appendLine("// ===== assets/$namespace/atlases/gui.json =====")
            appendLine("// Adds this pack's textures/gui folder to the GUI sprite atlas.")
            appendLine(guiAtlasJson(namespace))

            if (described.isEmpty()) {
                appendLine()
                appendLine("// No texture in this project needs a .mcmeta sidecar.")
                appendLine("//")
                appendLine("// A sidecar is only written for a texture that is nine-sliced or")
                appendLine("// animated. Import a GIF, or set nine-slice insets on a texture in the")
                appendLine("// Assets panel, and its definition appears here.")
                return@buildString
            }

            described.forEach { (texture, mcmeta) ->
                appendLine()
                appendLine("// ===== assets/$namespace/textures/gui/${texture.mcmetaFileName()} =====")
                append("// ")
                appendLine(describe(texture))
                appendLine(mcmeta)
            }
        }
    }

    /** One-line plain-English summary of what a sidecar does. */
    private fun describe(texture: TextureAsset): String = buildString {
        val parts = mutableListOf<String>()
        if (texture.hasNineSlice) {
            parts += "Nine-sliced, so the border stays crisp at any widget size."
        }
        if (texture.isAnimated) {
            val seconds = texture.frameCount * texture.frameTimeTicks / 20.0
            parts += "${texture.frameCount} frames stacked vertically, " +
                "${texture.frameTimeTicks} tick(s) each (~${format(seconds)}s per loop)."
            if (texture.hasVariableFrameTiming) {
                parts += "The source's uneven frame delays are preserved per frame."
            }
        }
        append(parts.joinToString(" "))
    }

    /** One decimal place, without depending on a platform formatter. */
    private fun format(value: Double): String {
        val tenths = ((value * 10) + 0.5).toInt()
        return "${tenths / 10}.${tenths % 10}"
    }
}
