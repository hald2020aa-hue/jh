package eu.kanade.tachiyomi.ui.source.searchhistory

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.isIncognitoModeForSource
import kotlinx.serialization.Serializable

private const val SEARCH_HISTORY_LIMIT = 20

/**
 * A remembered query + filter combination - either an auto-recorded recent search, or a
 * user-named saved one. [name] is only ever non-null for the latter; the item/view layer reads
 * its presence to tell which kind a given entry is, the same way [query] blank/non-blank already
 * distinguishes a plain search from a filter-only snapshot.
 *
 * @param id each entry's identity (and its recency, since a bump replaces it with a fresh one).
 * @param showOnAllSources only meaningful when [name] is set - whether a saved search is offered
 * on every source, or only [sourceId].
 */
@Serializable
data class SearchHistoryEntry(
    val id: Long,
    val query: String,
    val filters: List<SavedFilter> = emptyList(),
    val sourceId: Long? = null,
    val name: String? = null,
    val showOnAllSources: Boolean = false,
)

fun PreferencesHelper.addToSearchHistory(
    query: String = "",
    filters: List<SavedFilter> = emptyList(),
    sourceId: Long? = null,
) {
    if (!showBrowseSearchHistory().get()) return
    if (isIncognitoModeForSource(sourceId, this)) return
    val trimmedQuery = query.trim()
    if (trimmedQuery.isBlank() && filters.isEmpty()) return
    val pref = browseSearchHistory()
    val entry = SearchHistoryEntry(System.currentTimeMillis(), trimmedQuery, filters, sourceId)
    // bump instead of duplicating only when it's a truly identical repeat - same query, same filters
    val history =
        listOf(entry) + pref.get().filterNot { it.query.equals(trimmedQuery, true) && it.filters == filters }
    pref.set(history.take(SEARCH_HISTORY_LIMIT))
}

fun PreferencesHelper.removeFromSearchHistory(entry: SearchHistoryEntry) {
    val pref = browseSearchHistory()
    pref.set(pref.get().filterNot { it.id == entry.id })
}

fun PreferencesHelper.reinsertIntoSearchHistory(entry: SearchHistoryEntry) {
    val pref = browseSearchHistory()
    pref.set((pref.get() + entry).sortedByDescending { it.id }.take(SEARCH_HISTORY_LIMIT))
}

fun PreferencesHelper.clearSearchHistory() = browseSearchHistory().delete()
