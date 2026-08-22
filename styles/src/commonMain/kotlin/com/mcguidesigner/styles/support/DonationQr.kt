package com.mcguidesigner.styles.support

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import com.mcguidesigner.styles.render.decodeImageBitmap

/**
 * The donation QR code, decoded once and kept with the bytes it came from.
 *
 * Both halves are needed: the bitmap to show it, and the original bytes to
 * write out when someone saves it.  Re-encoding a decoded bitmap would work
 * but would be a strictly worse copy of a payment code, and there is no reason
 * to hand anyone a lossier QR than the one that shipped.
 */
class DonationQr(val bytes: ByteArray, val image: ImageBitmap) {
    companion object {
        /** Decodes [bytes], or returns null when they are not a readable image. */
        fun from(bytes: ByteArray?): DonationQr? {
            if (bytes == null || bytes.isEmpty()) return null
            val image = runCatching { decodeImageBitmap(bytes) }.getOrNull() ?: return null
            return DonationQr(bytes, image)
        }
    }
}

/**
 * The QR for the current app, supplied by whichever shell is hosting the UI.
 *
 * Null is a supported state, not a bug: the screen falls back to the account
 * numbers, which are the same payment details in a form anyone can type.
 */
val LocalDonationQr = staticCompositionLocalOf<DonationQr?> { null }
