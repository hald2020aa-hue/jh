package eu.kanade.tachiyomi.ui.reader.viewer

import java.io.InputStream

/** ISO-BMFF AVIF can advertise its major or one of its compatible brands. */
internal fun hasAvifBrand(header: ByteArray, length: Int = header.size): Boolean {
    if (length < 12 || header[4] != 'f'.code.toByte() ||
        header[5] != 't'.code.toByte() || header[6] != 'y'.code.toByte() ||
        header[7] != 'p'.code.toByte()
    ) return false

    for (offset in 8 until length - 3 step 4) {
        if (offset == 12) continue // minor version, not a brand
        val brand = String(header, offset, 4, Charsets.US_ASCII)
        if (brand == "avif" || brand == "avis") return true
    }
    return false
}

/** Always restore the stream position; this is safe for the reader's buffered streams. */
internal fun isAvifContainer(stream: InputStream): Boolean {
    if (!stream.markSupported()) return false
    val header = ByteArray(32)
    stream.mark(header.size)
    var count = 0
    return try {
        while (count < header.size) {
            val n = stream.read(header, count, header.size - count)
            if (n <= 0) break
            count += n
        }
        hasAvifBrand(header, count)
    } finally {
        stream.reset()
    }
}
