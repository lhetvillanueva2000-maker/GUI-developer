package com.mcguidesigner.core.image

/**
 * Writes 8-bit RGBA PNGs from raw ARGB pixels.
 *
 * The designer needs this so an imported GIF can be turned into the vertical
 * frame strip Minecraft animates, entirely in shared code - the strip is then
 * an ordinary PNG that the project stores, the canvas draws and the exporters
 * write out with no special cases anywhere downstream.
 */
object PngWriter {

    private val SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    /** Bytes per pixel in the colour type this writer emits (RGBA). */
    private const val CHANNELS = 4

    /**
     * Encodes [pixels] - one packed 0xAARRGGBB value per pixel, row-major -
     * as a PNG.
     */
    fun encode(width: Int, height: Int, pixels: IntArray): ByteArray {
        require(width > 0 && height > 0) { "PNG dimensions must be positive, got ${width}x$height" }
        require(pixels.size == width * height) {
            "Expected ${width * height} pixels for a ${width}x$height image, got ${pixels.size}"
        }

        val out = ByteWriter(width * height * CHANNELS / 2 + 256)
        out.write(SIGNATURE)
        out.chunk("IHDR") {
            writeInt(width)
            writeInt(height)
            write(8)  // bit depth
            write(6)  // colour type 6 = truecolour with alpha
            write(0)  // compression: deflate, the only defined value
            write(0)  // filter method 0, the only defined value
            write(0)  // not interlaced
        }
        out.chunk("IDAT") { write(Deflate.zlib(filter(width, height, pixels))) }
        out.chunk("IEND") {}
        return out.toByteArray()
    }

    /**
     * Applies PNG's per-scanline filters, choosing one row at a time.
     *
     * Each row may use a different filter, and picking well matters: filtering
     * turns absolute pixel values into small deltas, which is what gives the
     * deflate pass something compressible.  The heuristic - lowest sum of
     * absolute filtered bytes, treated as signed - is the one from the PNG
     * specification's own guidance and is what libpng uses.
     */
    private fun filter(width: Int, height: Int, pixels: IntArray): ByteArray {
        val stride = width * CHANNELS
        val out = ByteArray(height * (stride + 1))
        val raw = ByteArray(stride)
        val previous = ByteArray(stride)
        val candidate = ByteArray(stride)
        val best = ByteArray(stride)

        var target = 0
        for (y in 0 until height) {
            var at = 0
            for (x in 0 until width) {
                val argb = pixels[y * width + x]
                raw[at++] = (argb ushr 16).toByte() // R
                raw[at++] = (argb ushr 8).toByte()  // G
                raw[at++] = argb.toByte()           // B
                raw[at++] = (argb ushr 24).toByte() // A
            }

            var bestFilter = 0
            var bestScore = Int.MAX_VALUE

            for (type in 0..4) {
                var score = 0
                for (index in 0 until stride) {
                    val left = if (index >= CHANNELS) raw[index - CHANNELS].toInt() and 0xFF else 0
                    val up = previous[index].toInt() and 0xFF
                    val upLeft = if (index >= CHANNELS) previous[index - CHANNELS].toInt() and 0xFF else 0
                    val value = raw[index].toInt() and 0xFF
                    val filtered = when (type) {
                        0 -> value
                        1 -> value - left
                        2 -> value - up
                        3 -> value - ((left + up) / 2)
                        else -> value - paeth(left, up, upLeft)
                    } and 0xFF
                    candidate[index] = filtered.toByte()
                    // Signed magnitude: 200 as a delta is really -56, and a
                    // small negative delta compresses as well as a small
                    // positive one.
                    score += if (filtered < 128) filtered else 256 - filtered
                }
                if (score < bestScore) {
                    bestScore = score
                    bestFilter = type
                    candidate.copyInto(best)
                }
            }

            out[target++] = bestFilter.toByte()
            best.copyInto(out, target)
            target += stride

            // This row is the next row's "up" neighbour.
            raw.copyInto(previous)
        }
        return out
    }

    /** The PNG/PNG-spec Paeth predictor: whichever neighbour is closest. */
    private fun paeth(left: Int, up: Int, upLeft: Int): Int {
        val estimate = left + up - upLeft
        val dLeft = kotlin.math.abs(estimate - left)
        val dUp = kotlin.math.abs(estimate - up)
        val dUpLeft = kotlin.math.abs(estimate - upLeft)
        return when {
            dLeft <= dUp && dLeft <= dUpLeft -> left
            dUp <= dUpLeft -> up
            else -> upLeft
        }
    }

    /** Growable big-endian byte sink with PNG's length/type/data/CRC framing. */
    private class ByteWriter(initialCapacity: Int) {
        private var buffer = ByteArray(maxOf(initialCapacity, 128))
        private var size = 0

        fun write(byte: Int) {
            if (size == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
            buffer[size++] = byte.toByte()
        }

        fun write(bytes: ByteArray) {
            while (size + bytes.size > buffer.size) buffer = buffer.copyOf(buffer.size * 2)
            bytes.copyInto(buffer, size)
            size += bytes.size
        }

        fun writeInt(value: Int) {
            write(value ushr 24)
            write(value ushr 16)
            write(value ushr 8)
            write(value)
        }

        /**
         * Writes one chunk. The CRC covers the type and the data but not the
         * length, so the body is built separately and then framed.
         */
        fun chunk(type: String, body: ByteWriter.() -> Unit) {
            val inner = ByteWriter(64)
            inner.body()
            val data = inner.toByteArray()
            val typed = ByteArray(4 + data.size)
            type.forEachIndexed { index, ch -> typed[index] = ch.code.toByte() }
            data.copyInto(typed, 4)

            writeInt(data.size)
            write(typed)
            writeInt(Deflate.crc32(typed))
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)
    }
}
