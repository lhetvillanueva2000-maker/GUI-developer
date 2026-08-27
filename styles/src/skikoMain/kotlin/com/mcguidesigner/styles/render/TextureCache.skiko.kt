package com.mcguidesigner.styles.render

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

/** Desktop and browser decoding both go through Skia, which Compose ships. */
actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = runCatching {
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

actual fun readImageSize(bytes: ByteArray): Pair<Int, Int>? = runCatching {
    val image = Image.makeFromEncoded(bytes)
    image.width to image.height
}.getOrNull()
