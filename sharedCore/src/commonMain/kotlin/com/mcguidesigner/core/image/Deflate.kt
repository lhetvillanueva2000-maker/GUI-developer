package com.mcguidesigner.core.image

/**
 * A minimal DEFLATE (RFC 1951) compressor and the two checksums PNG needs.
 *
 * Kotlin's common standard library has no compression, and the alternative -
 * an `expect`/`actual` pair around `java.util.zip` - would put the code that
 * builds a texture out of reach of the shared tests.  Writing the ~200 lines
 * here instead keeps GIF import, PNG writing and their tests entirely in
 * common code, so desktop and Android cannot drift apart on it.
 *
 * Only fixed-Huffman blocks are produced.  Dynamic Huffman would compress
 * perhaps 10-20% better on top of this, at the cost of the code-length-code
 * machinery; for pixel art, where LZ77 back-references already do nearly all
 * the work, that is not a trade worth making here.  The output is ordinary
 * DEFLATE either way and every decoder reads it.
 */
internal object Deflate {

    /** Windows larger than this cannot be addressed by a distance code. */
    private const val WINDOW_SIZE = 32_768

    /** Longest back-reference DEFLATE can encode. */
    private const val MAX_MATCH = 258

    /** Shorter than this and the match costs more bits than the literals. */
    private const val MIN_MATCH = 3

    /**
     * How far back the matcher will walk one hash chain.
     *
     * Caps the worst case on inputs with long runs of one value - a solid
     * background, which is most of a GUI texture - where the chain for that
     * hash holds effectively every position in the file.
     */
    private const val MAX_CHAIN = 128

    private const val HASH_BITS = 15
    private const val HASH_SIZE = 1 shl HASH_BITS

    // Length codes 257..285: the smallest length each encodes, and its extras.
    private val LENGTH_BASE = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31,
        35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258,
    )
    private val LENGTH_EXTRA = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2,
        3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0,
    )

    // Distance codes 0..29.
    private val DIST_BASE = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193,
        257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577,
    )
    private val DIST_EXTRA = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6,
        7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13,
    )

    /** Wraps [deflate] in a zlib (RFC 1950) container, which is what PNG stores. */
    fun zlib(data: ByteArray): ByteArray {
        val compressed = deflate(data)
        val out = ByteArray(compressed.size + 6)
        // CMF: deflate, 32K window. FLG: no preset dictionary, default level,
        // chosen so (CMF << 8 | FLG) is a multiple of 31.
        out[0] = 0x78
        out[1] = 0x01
        compressed.copyInto(out, 2)
        val adler = adler32(data)
        out[out.size - 4] = (adler ushr 24).toByte()
        out[out.size - 3] = (adler ushr 16).toByte()
        out[out.size - 2] = (adler ushr 8).toByte()
        out[out.size - 1] = adler.toByte()
        return out
    }

    /** Raw DEFLATE stream: one final fixed-Huffman block. */
    fun deflate(data: ByteArray): ByteArray {
        val bits = BitWriter(data.size / 2 + 64)
        bits.writeBits(1, 1) // BFINAL
        bits.writeBits(1, 2) // BTYPE = 01, fixed Huffman

        // head[h] is the most recent position hashing to h; prev[i] chains
        // backwards from position i to the one before it with the same hash.
        val head = IntArray(HASH_SIZE) { -1 }
        val prev = IntArray(data.size)

        var position = 0
        while (position < data.size) {
            var matchLength = 0
            var matchDistance = 0

            if (position + MIN_MATCH <= data.size) {
                val hash = hashAt(data, position)
                var candidate = head[hash]
                var chain = 0
                val floor = if (position > WINDOW_SIZE) position - WINDOW_SIZE else 0

                while (candidate >= floor && chain < MAX_CHAIN) {
                    val length = matchLengthAt(data, candidate, position)
                    if (length > matchLength) {
                        matchLength = length
                        matchDistance = position - candidate
                        if (length >= MAX_MATCH) break
                    }
                    candidate = prev[candidate]
                    chain++
                }
            }

            if (matchLength >= MIN_MATCH) {
                writeLength(bits, matchLength)
                writeDistance(bits, matchDistance)
                // Every position the match covers still has to be registered,
                // or later matches lose the chain through this stretch.
                repeat(matchLength) {
                    if (position + MIN_MATCH <= data.size) {
                        val hash = hashAt(data, position)
                        prev[position] = head[hash]
                        head[hash] = position
                    }
                    position++
                }
            } else {
                writeLiteral(bits, data[position].toInt() and 0xFF)
                if (position + MIN_MATCH <= data.size) {
                    val hash = hashAt(data, position)
                    prev[position] = head[hash]
                    head[hash] = position
                }
                position++
            }
        }

        writeLiteral(bits, 256) // end of block
        return bits.toByteArray()
    }

    private fun hashAt(data: ByteArray, at: Int): Int {
        val a = data[at].toInt() and 0xFF
        val b = data[at + 1].toInt() and 0xFF
        val c = data[at + 2].toInt() and 0xFF
        return ((a shl 10) xor (b shl 5) xor c) and (HASH_SIZE - 1)
    }

    /** Length of the common prefix of [candidate] and [position], capped. */
    private fun matchLengthAt(data: ByteArray, candidate: Int, position: Int): Int {
        val limit = minOf(MAX_MATCH, data.size - position)
        var length = 0
        while (length < limit && data[candidate + length] == data[position + length]) length++
        return length
    }

    /**
     * A literal or the end-of-block symbol, in the fixed code.
     *
     * The four ranges below are the fixed Huffman table from RFC 1951 3.2.6.
     * Codes are Huffman codes, so they go out most-significant bit first,
     * which is the opposite order to everything else in the stream.
     */
    private fun writeLiteral(bits: BitWriter, symbol: Int) = when {
        symbol <= 143 -> bits.writeCode(0x30 + symbol, 8)
        symbol <= 255 -> bits.writeCode(0x190 + symbol - 144, 9)
        symbol <= 279 -> bits.writeCode(symbol - 256, 7)
        else -> bits.writeCode(0xC0 + symbol - 280, 8)
    }

    private fun writeLength(bits: BitWriter, length: Int) {
        var code = LENGTH_BASE.size - 1
        while (code > 0 && LENGTH_BASE[code] > length) code--
        writeLiteral(bits, 257 + code)
        if (LENGTH_EXTRA[code] > 0) {
            bits.writeBits(length - LENGTH_BASE[code], LENGTH_EXTRA[code])
        }
    }

    private fun writeDistance(bits: BitWriter, distance: Int) {
        var code = DIST_BASE.size - 1
        while (code > 0 && DIST_BASE[code] > distance) code--
        // Distances use a fixed 5-bit code, again most-significant bit first.
        bits.writeCode(code, 5)
        if (DIST_EXTRA[code] > 0) {
            bits.writeBits(distance - DIST_BASE[code], DIST_EXTRA[code])
        }
    }

    /** Adler-32 over [data], as zlib's trailing checksum. */
    fun adler32(data: ByteArray): Int {
        var a = 1
        var b = 0
        for (byte in data) {
            a = (a + (byte.toInt() and 0xFF)) % 65_521
            b = (b + a) % 65_521
        }
        return (b shl 16) or a
    }

    private val crcTable = IntArray(256) { index ->
        var c = index
        repeat(8) { c = if (c and 1 != 0) (0xEDB88320.toInt() xor (c ushr 1)) else (c ushr 1) }
        c
    }

    /** CRC-32 as PNG uses it for every chunk. */
    fun crc32(data: ByteArray, from: Int = 0, until: Int = data.size): Int {
        var crc = -1
        for (index in from until until) {
            crc = crcTable[(crc xor data[index].toInt()) and 0xFF] xor (crc ushr 8)
        }
        return crc.inv()
    }

    /**
     * DEFLATE packs bits into bytes low bit first, so a byte fills from the
     * bottom up and a value that straddles a byte boundary continues in the
     * next byte's low bits.
     */
    private class BitWriter(initialCapacity: Int) {
        private var buffer = ByteArray(maxOf(initialCapacity, 64))
        private var size = 0
        private var bitBuffer = 0
        private var bitCount = 0

        /** Writes [count] low bits of [value], least-significant bit first. */
        fun writeBits(value: Int, count: Int) {
            bitBuffer = bitBuffer or ((value and ((1 shl count) - 1)) shl bitCount)
            bitCount += count
            while (bitCount >= 8) {
                append(bitBuffer.toByte())
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
        }

        /** Writes a Huffman [code] of [count] bits, most-significant bit first. */
        fun writeCode(code: Int, count: Int) {
            for (shift in count - 1 downTo 0) {
                writeBits((code ushr shift) and 1, 1)
            }
        }

        private fun append(byte: Byte) {
            if (size == buffer.size) buffer = buffer.copyOf(buffer.size * 2)
            buffer[size++] = byte
        }

        /** Flushes the partial byte, padding with zeros, and returns the stream. */
        fun toByteArray(): ByteArray {
            if (bitCount > 0) {
                append(bitBuffer.toByte())
                bitBuffer = 0
                bitCount = 0
            }
            return buffer.copyOf(size)
        }
    }
}
