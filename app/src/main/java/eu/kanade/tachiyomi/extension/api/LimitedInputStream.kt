package eu.kanade.tachiyomi.extension.api

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Read a repository index with a hard cap, including after gzip decompression. */
internal fun InputStream.readLimitedBytes(maxBytes: Int): ByteArray {
    require(maxBytes > 0) { "Limit must be positive" }
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) throw IOException("Extension repository index is too large")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
