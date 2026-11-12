package eu.kanade.tachiyomi.util.system

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Read a decoded stream without accepting unbounded/decompression-bomb input. */
fun InputStream.readAtMost(maxBytes: Int): ByteArray {
    require(maxBytes > 0)
    val result = ByteArrayOutputStream(minOf(maxBytes, 8192))
    val buffer = ByteArray(8192)
    var length = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) return result.toByteArray()
        if (read == 0) {
            // Defensive handling for unusual streams reporting zero progress.
            val singleByte = read()
            if (singleByte < 0) return result.toByteArray()
            if (length == maxBytes) throw IOException("Data exceeds the permitted size")
            result.write(singleByte)
            length++
            continue
        }
        if (read > maxBytes - length) throw IOException("Data exceeds the permitted size")
        result.write(buffer, 0, read)
        length += read
    }
}
