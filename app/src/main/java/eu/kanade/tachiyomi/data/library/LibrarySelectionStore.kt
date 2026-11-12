package eu.kanade.tachiyomi.data.library

import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * WorkManager Data has a 10 KiB limit. Large dynamic categories must not depend on
 * static JVM state: Android can kill the app between enqueueing and executing work.
 * Selection files live in the app's private files directory, not shared storage.
 */
internal class LibrarySelectionStore(private val directory: File) {
    fun save(ids: List<Long>): String {
        require(ids.isNotEmpty()) { "Cannot queue an empty library selection" }
        require(ids.size <= MAX_IDS) { "Too many selected manga" }
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot create selection directory")
        val name = "selection-${UUID.randomUUID()}.ids"
        val pending = File(directory, "$name.tmp")
        val stored = File(directory, name)
        try {
            pending.bufferedWriter().use { writer ->
                ids.distinct().forEach { id ->
                    writer.write(id.toString())
                    writer.newLine()
                }
            }
            if (pending.length() > MAX_FILE_BYTES) throw IOException("Selection too large")
            if (!pending.renameTo(stored)) throw IOException("Unable to store selection")
            return name
        } finally {
            pending.delete()
        }
    }

    /**
     * Read a selection without removing it: WorkManager may retry after the process
     * is killed while updating, so the selection must stay until the worker finishes.
     */
    fun load(name: String): List<Long> {
        if (!NAME_PATTERN.matches(name)) throw IOException("Invalid selection filename")
        val file = File(directory, name)
        if (!file.isFile || file.length() <= 0L || file.length() > MAX_FILE_BYTES) {
            throw IOException("Missing or invalid library selection")
        }
        try {
            val result = file.useLines { lines ->
                lines.map { it.toLong() }.take(MAX_IDS + 1).toList()
            }
            if (result.isEmpty() || result.size > MAX_IDS) throw IOException("Invalid selection count")
            return result.distinct()
        } catch (e: NumberFormatException) {
            throw IOException("Corrupt library selection", e)
        }
    }

    fun delete(name: String): Boolean = NAME_PATTERN.matches(name) && File(directory, name).delete()

    /** Utility for one-off callers which do not need WorkManager retry behavior. */
    fun loadAndDelete(name: String): List<Long> {
        try {
            return load(name)
        } finally {
            delete(name)
        }
    }

    /** Remove selections orphaned for a month; preserve offline pending work. */
    fun deleteStale(now: Long = System.currentTimeMillis()) {
        directory.listFiles()?.forEach { file ->
            if (file.isFile &&
                (NAME_PATTERN.matches(file.name) || file.name.endsWith(".ids.tmp")) &&
                now - file.lastModified() > TWO_DAYS_MILLIS
            ) {
                file.delete()
            }
        }
    }

    companion object {
        private const val MAX_IDS = 50_000
        private const val MAX_FILE_BYTES = 1_500_000L
        private const val TWO_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1000
        private val NAME_PATTERN = Regex("""selection-[0-9a-fA-F-]{36}\.ids""")
    }
}
