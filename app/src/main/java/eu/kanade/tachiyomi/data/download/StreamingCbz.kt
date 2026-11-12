package eu.kanade.tachiyomi.data.download

import java.io.IOException
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Store a page in a CBZ with O(buffer-size) memory rather than reading it all into RAM. */
internal fun writeStoredZipEntry(
    zip: ZipOutputStream,
    name: String,
    openStream: () -> InputStream,
) {
    val checksum = CRC32()
    val buffer = ByteArray(16 * 1024)
    var length = 0L
    openStream().use { input ->
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            checksum.update(buffer, 0, count)
            length += count
        }
    }
    if (length == 0L) throw IOException("Empty archive entry: $name")

    val entry = ZipEntry(name).apply {
        method = ZipEntry.STORED
        size = length
        compressedSize = length
        crc = checksum.value
    }
    zip.putNextEntry(entry)
    openStream().use { input ->
        input.copyTo(zip, bufferSize = buffer.size)
    }
    zip.closeEntry()
}
