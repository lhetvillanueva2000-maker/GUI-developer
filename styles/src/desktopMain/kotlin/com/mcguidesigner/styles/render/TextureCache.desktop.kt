package com.mcguidesigner.styles.render

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

/** Desktop decoding goes through Skia, which Compose already ships with. */
actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = runCatching {
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

actual fun readImageSize(bytes: ByteArray): Pair<Int, Int>? = runCatching {
    val image = Image.makeFromEncoded(bytes)
    image.width to image.height
}.getOrNull()
