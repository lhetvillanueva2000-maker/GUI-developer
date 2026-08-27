package com.mcguidesigner.web.io

/**
 * A ZIP archive, built in memory, with no compression.
 *
 * The desktop shell writes an export as a folder tree and Android writes it
 * into a document tree; a browser can do neither, and offering fourteen
 * consecutive downloads for one resource pack is not an export, it is a
 * punishment. So the browser's export is one archive.
 *
 * Stored rather than deflated on purpose. There is no deflate in the Kotlin
 * standard library, so compressing would mean either writing one or reaching
 * for `CompressionStream`, which is asynchronous and absent from older Safari.
 * What is actually in these archives is a few kilobytes of JSON and text plus
 * PNGs, and PNG is already compressed - so the saving would have been small
 * and the machinery large. Every unzip tool reads stored entries.
 */
object ZipWriter {

    /** One file in the archive. */
    data class Entry(val path: String, val bytes: ByteArray) {
        // Data class equality on a ByteArray compares references, which is
        // never what anybody means. Spelt out so a surprising `==` cannot hide.
        override fun equals(other: Any?): Boolean =
            this === other || (other is Entry && path == other.path && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int = 31 * path.hashCode() + bytes.contentHashCode()
    }

    fun archive(entries: List<Entry>): ByteArray {
        val out = ByteBuffer()
        val directory = ByteBuffer()
        var count = 0

        for (entry in entries) {
            val name = entry.path.encodeToByteArray()
            val crc = Crc32.of(entry.bytes)
            val offset = out.size

            // Local file header.
            out.int(0x04034B50)
            out.short(20)          // version needed: 2.0, which is what "stored" wants
            out.short(0x0800)      // flag bit 11: the name is UTF-8
            out.short(0)           // method 0: stored
            out.short(0)           // modification time - see the note on dates below
            out.short(0x0021)      // modification date: 1 January 1980, the epoch of this format
            out.int(crc)
            out.int(entry.bytes.size)
            out.int(entry.bytes.size)
            out.short(name.size)
            out.short(0)
            out.bytes(name)
            out.bytes(entry.bytes)

            // Central directory entry, accumulated as we go.
            directory.int(0x02014B50)
            directory.short(20)    // version made by
            directory.short(20)    // version needed
            directory.short(0x0800)
            directory.short(0)
            directory.short(0)
            directory.short(0x0021)
            directory.int(crc)
            directory.int(entry.bytes.size)
            directory.int(entry.bytes.size)
            directory.short(name.size)
            directory.short(0)     // extra
            directory.short(0)     // comment
            directory.short(0)     // disk number
            directory.short(0)     // internal attributes
            directory.int(0)       // external attributes
            directory.int(offset)
            directory.bytes(name)
            count++
        }

        val directoryOffset = out.size
        val directoryBytes = directory.toByteArray()
        out.bytes(directoryBytes)

        // End of central directory.
        out.int(0x06054B50)
        out.short(0)
        out.short(0)
        out.short(count)
        out.short(count)
        out.int(directoryBytes.size)
        out.int(directoryOffset)
        out.short(0)

        return out.toByteArray()
    }
}

/**
 * Every entry is dated 1 January 1980.
 *
 * Deliberately, rather than "now". An export of an unchanged design should
 * produce an identical archive, which is what makes a checksum worth taking
 * and a diff of two exports worth reading; stamping the clock into it means no
 * two exports of the same design are ever the same bytes. 1980 is the earliest
 * date the format can represent, so it is unambiguous rather than arbitrary.
 */
private class ByteBuffer {
    private var data = ByteArray(4096)
    var size = 0
        private set

    private fun ensure(extra: Int) {
        if (size + extra <= data.size) return
        var capacity = data.size
        while (capacity < size + extra) capacity *= 2
        data = data.copyOf(capacity)
    }

    /** Little-endian, which is the only byte order this format uses. */
    fun short(value: Int) {
        ensure(2)
        data[size++] = (value and 0xFF).toByte()
        data[size++] = ((value ushr 8) and 0xFF).toByte()
    }

    fun int(value: Int) {
        ensure(4)
        data[size++] = (value and 0xFF).toByte()
        data[size++] = ((value ushr 8) and 0xFF).toByte()
        data[size++] = ((value ushr 16) and 0xFF).toByte()
        data[size++] = ((value ushr 24) and 0xFF).toByte()
    }

    fun bytes(value: ByteArray) {
        ensure(value.size)
        value.copyInto(data, size)
        size += value.size
    }

    fun toByteArray(): ByteArray = data.copyOf(size)
}

/** The checksum ZIP entries carry. Table-driven, built once. */
private object Crc32 {

    private val table = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (0xEDB88320.toInt() xor (c ushr 1)) else (c ushr 1) }
        c
    }

    fun of(bytes: ByteArray): Int {
        var crc = -1
        for (b in bytes) crc = table[(crc xor b.toInt()) and 0xFF] xor (crc ushr 8)
        return crc.inv()
    }
}
