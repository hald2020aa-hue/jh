package eu.kanade.tachiyomi.ui.enhanced

import java.util.Locale

enum class DownloadQueueSort { QUEUE, FETCH_DATE, CHAPTER, TITLE, PROGRESS }

/** Stable sort: never changes persisted queue priority. Queue mode retains actual download order. */
fun <T> sortDownloadRows(
    rows: List<T>,
    mode: DownloadQueueSort,
    title: (T) -> String,
    chapterNumber: (T) -> Float,
    fetchDate: (T) -> Long,
    progress: (T) -> Int,
): List<T> =
    when (mode) {
        DownloadQueueSort.QUEUE -> rows
        DownloadQueueSort.FETCH_DATE -> rows.sortedByDescending(fetchDate)
        DownloadQueueSort.CHAPTER -> rows.sortedByDescending { chapterNumber(it).takeIf { number -> number.isFinite() } ?: -1f }
        DownloadQueueSort.TITLE -> rows.sortedBy { title(it).lowercase(Locale.ROOT) }
        DownloadQueueSort.PROGRESS -> rows.sortedByDescending(progress)
    }
