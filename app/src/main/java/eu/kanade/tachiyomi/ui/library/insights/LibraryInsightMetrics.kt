package eu.kanade.tachiyomi.ui.library.insights

import eu.kanade.tachiyomi.ui.library.search.DuplicateCandidate
import eu.kanade.tachiyomi.ui.library.search.crossSourceDuplicateIds

/** Keep aggregations independent of Android UI so thousands of titles can be regression tested. */
internal data class LibraryInsightEntry(
    val id: Long,
    val sourceId: Long,
    val title: String,
    val unread: Int,
    val read: Int,
    val completed: Boolean,
)

internal data class LibraryInsightMetrics(
    val titles: Int,
    val unreadChapters: Int,
    val readChapters: Int,
    val sources: Int,
    val likelyDuplicateTitles: Int,
    val completedTitles: Int,
)

internal fun calculateLibraryInsights(entries: Collection<LibraryInsightEntry>): LibraryInsightMetrics {
    val unique = entries.distinctBy { it.id }
    val duplicateIds = crossSourceDuplicateIds(unique.map { DuplicateCandidate(it.id, it.title, it.sourceId) })
    return LibraryInsightMetrics(
        titles = unique.size,
        unreadChapters = unique.sumOf { it.unread.coerceAtLeast(0) },
        readChapters = unique.sumOf { it.read.coerceAtLeast(0) },
        sources = unique.map { it.sourceId }.distinct().size,
        likelyDuplicateTitles = duplicateIds.size,
        completedTitles = unique.count { it.completed },
    )
}
