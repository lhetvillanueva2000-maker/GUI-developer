package com.mcguidesigner.core.image

/**
 * A GIF87a/GIF89a decoder that produces fully composed frames.
 *
 * GIF frames are stored as patches: each one may cover only part of the canvas
 * and carries a disposal rule saying what the *next* frame sees underneath it.
 * A decoder that ignored that would hand back torn fragments, so every frame
 * here is composed against the running canvas and returned whole - which is
 * also exactly the form the frame strip needs.
 *
 * Lives in common code so GIF import behaves identically on desktop and
 * Android and can be tested without either.
 */
object GifDecoder {

    /** One fully composed frame, [delayMillis] after the one before it. */
    data class Frame(
        val pixels: IntArray,
        val delayMillis: Int,
    ) {
        // IntArray uses identity equals, so the generated ones would be wrong.
        override fun equals(other: Any?): Boolean =
            this === other ||
                (other is Frame && delayMillis == other.delayMillis && pixels.contentEquals(other.pixels))

        override fun hashCode(): Int = 31 * pixels.contentHashCode() + delayMillis
    }

    data class Image(
        val width: Int,
        val height: Int,
        val frames: List<Frame>,
        /** 0 means "forever", matching the NETSCAPE looping extension. */
        val loopCount: Int = 0,
    )

    /**
     * Browsers and image viewers clamp very short delays, because a 0 or 10ms
     * GIF is almost always authored expecting the historical 100ms floor.
     * Matching that keeps an imported GIF running at the speed it looks like
     * it runs at everywhere else.
     */
    private const val MINIMUM_SANE_DELAY_MILLIS = 20
    private const val FALLBACK_DELAY_MILLIS = 100

    // Disposal methods from the Graphic Control Extension.
    private const val DISPOSE_BACKGROUND = 2
    private const val DISPOSE_PREVIOUS = 3

    /** True when [bytes] starts with a GIF signature. */
    fun isGif(bytes: ByteArray): Boolean =
        bytes.size >= 6 &&
            bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == '8'.code.toByte()

    /** Decodes [bytes], or returns null if they are not a GIF this can read. */
    fun decode(bytes: ByteArray): Image? = runCatching { decodeOrThrow(bytes) }.getOrNull()

    private fun decodeOrThrow(bytes: ByteArray): Image {
        val reader = Reader(bytes)
        require(isGif(bytes)) { "not a GIF" }
        reader.skip(6)

        val canvasWidth = reader.readShort()
        val canvasHeight = reader.readShort()
        require(canvasWidth > 0 && canvasHeight > 0) { "GIF has no canvas" }

        val packed = reader.readByte()
        val hasGlobalTable = packed and 0x80 != 0
        val globalTableSize = 2 shl (packed and 0x07)
        reader.skip(2) // background colour index, pixel aspect ratio

        val globalTable = if (hasGlobalTable) reader.readColorTable(globalTableSize) else IntArray(0)

        val frames = mutableListOf<Frame>()
        var loopCount = 0

        // Running canvas the patches compose onto.
        var canvas = IntArray(canvasWidth * canvasHeight)

        // Graphic Control Extension state; it applies to the next image only.
        var delayMillis = FALLBACK_DELAY_MILLIS
        var transparentIndex = -1
        var disposal = 0

        loop@ while (reader.hasMore()) {
            when (reader.readByte()) {
                0x2C -> { // Image Descriptor
                    val left = reader.readShort()
                    val top = reader.readShort()
                    val width = reader.readShort()
                    val height = reader.readShort()
                    val imagePacked = reader.readByte()
                    val hasLocalTable = imagePacked and 0x80 != 0
                    val interlaced = imagePacked and 0x40 != 0
                    val localTableSize = 2 shl (imagePacked and 0x07)

                    val table = if (hasLocalTable) reader.readColorTable(localTableSize) else globalTable
                    require(table.isNotEmpty()) { "GIF frame has no colour table" }

                    val indices = reader.readLzwImage(width * height)

                    // "Restore to previous" means the frame after this one sees
                    // what was here *before* it, so keep a copy to restore.
                    val restorePoint = if (disposal == DISPOSE_PREVIOUS) canvas.copyOf() else null

                    var source = 0
                    for (row in 0 until height) {
                        val targetRow = top + interlacedRow(row, height, interlaced)
                        if (targetRow >= canvasHeight) {
                            source += width
                            continue
                        }
                        for (column in 0 until width) {
                            val index = indices[source++]
                            val targetColumn = left + column
                            if (targetColumn >= canvasWidth) continue
                            // A transparent index means "leave what is here",
                            // which is how GIFs encode partial updates.
                            if (index == transparentIndex) continue
                            if (index < table.size) {
                                canvas[targetRow * canvasWidth + targetColumn] = table[index]
                            }
                        }
                    }

                    frames += Frame(canvas.copyOf(), delayMillis)

                    canvas = when {
                        restorePoint != null -> restorePoint
                        disposal == DISPOSE_BACKGROUND -> canvas.copyOf().also { cleared ->
                            for (row in top until minOf(top + height, canvasHeight)) {
                                for (column in left until minOf(left + width, canvasWidth)) {
                                    cleared[row * canvasWidth + column] = 0
                                }
                            }
                        }
                        else -> canvas
                    }

                    // The control extension governs one image only.
                    delayMillis = FALLBACK_DELAY_MILLIS
                    transparentIndex = -1
                    disposal = 0
                }

                0x21 -> when (reader.readByte()) { // Extension
                    0xF9 -> { // Graphic Control
                        reader.readByte() // block size, always 4
                        val flags = reader.readByte()
                        disposal = (flags shr 2) and 0x07
                        val rawDelay = reader.readShort() * 10 // stored in 1/100 s
                        delayMillis = if (rawDelay < MINIMUM_SANE_DELAY_MILLIS) {
                            FALLBACK_DELAY_MILLIS
                        } else {
                            rawDelay
                        }
                        val transparent = reader.readByte()
                        transparentIndex = if (flags and 0x01 != 0) transparent else -1
                        reader.skipBlocks()
                    }

                    0xFF -> { // Application - only NETSCAPE2.0 looping matters
                        val size = reader.readByte()
                        val identifier = reader.readString(size)
                        if (identifier.startsWith("NETSCAPE")) {
                            while (true) {
                                val blockSize = reader.readByte()
                                if (blockSize == 0) break
                                if (blockSize >= 3 && reader.peek() == 1) {
                                    reader.readByte()
                                    loopCount = reader.readShort()
                                    reader.skip(blockSize - 3)
                                } else {
                                    reader.skip(blockSize)
                                }
                            }
                        } else {
                            reader.skipBlocks()
                        }
                    }

                    else -> reader.skipBlocks() // comment, plain text, anything else
                }

                0x3B -> break@loop // trailer
                else -> break@loop // corrupt; keep the frames decoded so far
            }
        }

        require(frames.isNotEmpty()) { "GIF contained no frames" }
        return Image(canvasWidth, canvasHeight, frames, loopCount)
    }

    /**
     * Maps a stored row to its display row.
     *
     * Interlaced GIFs write rows in four passes - every 8th from 0, every 8th
     * from 4, every 4th from 2, every 2nd from 1 - so a viewer could show a
     * coarse version before the whole image arrived.
     */
    private fun interlacedRow(row: Int, height: Int, interlaced: Boolean): Int {
        if (!interlaced) return row
        val pass1 = (height + 7) / 8
        val pass2 = (height + 3) / 8
        val pass3 = (height + 1) / 4
        return when {
            row < pass1 -> row * 8
            row < pass1 + pass2 -> (row - pass1) * 8 + 4
            row < pass1 + pass2 + pass3 -> (row - pass1 - pass2) * 4 + 2
            else -> (row - pass1 - pass2 - pass3) * 2 + 1
        }
    }

    private class Reader(private val bytes: ByteArray) {
        private var at = 0

        fun hasMore(): Boolean = at < bytes.size

        fun readByte(): Int {
            require(at < bytes.size) { "GIF ended mid-structure" }
            return bytes[at++].toInt() and 0xFF
        }

        fun peek(): Int = if (at < bytes.size) bytes[at].toInt() and 0xFF else -1

        /** GIF stores 16-bit values little-endian. */
        fun readShort(): Int = readByte() or (readByte() shl 8)

        fun skip(count: Int) {
            at = minOf(at + count, bytes.size)
        }

        fun readString(length: Int): String =
            buildString { repeat(length) { append(readByte().toChar()) } }

        /** Reads [size] RGB triples into opaque 0xFFRRGGBB values. */
        fun readColorTable(size: Int): IntArray = IntArray(size) {
            val r = readByte()
            val g = readByte()
            val b = readByte()
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        /** Skips a chain of sub-blocks up to its zero-length terminator. */
        fun skipBlocks() {
            while (true) {
                val size = readByte()
                if (size == 0) return
                skip(size)
            }
        }

        /** Concatenates a sub-block chain into one buffer. */
        private fun readBlocks(): ByteArray {
            var out = ByteArray(512)
            var size = 0
            while (true) {
                val blockSize = readByte()
                if (blockSize == 0) return out.copyOf(size)
                while (size + blockSize > out.size) out = out.copyOf(out.size * 2)
                for (index in 0 until blockSize) out[size + index] = bytes[at + index]
                size += blockSize
                skip(blockSize)
            }
        }

        /**
         * LZW-decodes one image into [pixelCount] palette indices.
         *
         * GIF's variant of LZW packs codes least-significant bit first, widens
         * the code as the dictionary fills, and resets on an explicit clear
         * code.  The dictionary is held as prefix/suffix chains - each entry is
         * "some earlier entry, plus one byte" - so expanding a code walks the
         * chain backwards onto a stack and then pops it, which is why output
         * comes out reversed from the walk.
         */
        fun readLzwImage(pixelCount: Int): IntArray {
            val minimumCodeSize = readByte()
            val data = readBlocks()
            val out = IntArray(pixelCount)

            val clearCode = 1 shl minimumCodeSize
            val endCode = clearCode + 1
            val maxEntries = 4096

            val prefix = IntArray(maxEntries)
            val suffix = IntArray(maxEntries)
            val stack = IntArray(maxEntries)

            var codeSize = minimumCodeSize + 1
            var nextEntry = clearCode + 2
            var previousCode = -1

            var bitBuffer = 0
            var bitCount = 0
            var dataAt = 0
            var written = 0

            while (written < pixelCount) {
                while (bitCount < codeSize) {
                    // Truncated stream: return the pixels decoded so far rather
                    // than losing the whole frame.
                    if (dataAt >= data.size) return out
                    bitBuffer = bitBuffer or ((data[dataAt++].toInt() and 0xFF) shl bitCount)
                    bitCount += 8
                }
                val code = bitBuffer and ((1 shl codeSize) - 1)
                bitBuffer = bitBuffer ushr codeSize
                bitCount -= codeSize

                if (code == endCode) return out
                if (code == clearCode) {
                    codeSize = minimumCodeSize + 1
                    nextEntry = clearCode + 2
                    previousCode = -1
                    continue
                }

                if (previousCode < 0) {
                    // The first code after a clear is always a root byte, and
                    // there is nothing to extend the dictionary with yet.
                    if (code >= clearCode) return out
                    out[written++] = code
                    previousCode = code
                    continue
                }

                // Every step defines exactly one new entry: the previous
                // string plus one byte. Which byte depends on whether the
                // encoder just used an entry in the very step that defines it
                // - the self-referential case - where the code expands to the
                // previous string followed by *its own* first byte. That entry
                // has to exist before the expansion walk below can follow it.
                val selfReferential = code >= nextEntry
                if (selfReferential && code > nextEntry) return out // corrupt

                fun addEntry(byte: Int) {
                    if (nextEntry >= maxEntries) return
                    prefix[nextEntry] = previousCode
                    suffix[nextEntry] = byte
                    nextEntry++
                    if (nextEntry == (1 shl codeSize) && codeSize < 12) codeSize++
                }

                if (selfReferential) {
                    var walk = previousCode
                    while (walk >= clearCode) walk = prefix[walk]
                    addEntry(walk)
                    if (code >= nextEntry) return out // dictionary was full
                }

                var current = code
                var depth = 0
                while (current >= clearCode) {
                    stack[depth++] = suffix[current]
                    current = prefix[current]
                }
                val firstByte = current
                stack[depth++] = firstByte

                while (depth > 0 && written < pixelCount) {
                    out[written++] = stack[--depth]
                }

                if (!selfReferential) addEntry(firstByte)
                previousCode = code
            }
            return out
        }
    }
}
