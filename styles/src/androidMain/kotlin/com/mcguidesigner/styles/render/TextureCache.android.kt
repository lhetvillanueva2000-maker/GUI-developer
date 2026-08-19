package com.mcguidesigner.styles.render

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Android decoding uses `BitmapFactory` with scaling disabled so a 16x16
 * Minecraft texture stays 16x16 regardless of the device's density bucket.
 */
actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = runCatching {
    val options = BitmapFactory.Options().apply {
        inScaled = false
        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}.getOrNull()

/** Reads the header only - no pixels are allocated. */
actual fun readImageSize(bytes: ByteArray): Pair<Int, Int>? = runCatching {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
}.getOrNull()
