package eu.kanade.tachiyomi.ui.library.search

import java.text.Normalizer
import java.util.Locale

/**
 * A conservative title fingerprint used for filtering likely duplicated entries.
 * It strips decorative punctuation and combining marks, but does not guess aliases,
 * translate titles or automatically merge any library records.
 */
fun normalizeLibraryTitle(title: String): String {
    val decomposed = Normalizer.normalize(title, Normalizer.Form.NFKD).lowercase(Locale.ROOT)
    val result = StringBuilder(decomposed.length)
    var pendingSpace = false
    for (char in decomposed) {
        if (char == '\u0640' || Character.getType(char) == Character.NON_SPACING_MARK.toInt() ||
            Character.getType(char) == Character.COMBINING_SPACING_MARK.toInt()
        ) {
            continue
        }
        if (char.isLetterOrDigit()) {
            if (pendingSpace && result.isNotEmpty()) result.append(' ')
            result.append(char)
            pendingSpace = false
        } else {
            pendingSpace = true
        }
    }
    return result.toString()
}

fun libraryTitleContains(title: String, query: String): Boolean {
    if (title.contains(query, ignoreCase = true)) return true
    val normalizedQuery = normalizeLibraryTitle(query)
    return normalizedQuery.isNotEmpty() && normalizeLibraryTitle(title).contains(normalizedQuery)
}

data class DuplicateCandidate(val id: Long, val title: String, val sourceId: Long)

/** Return IDs with the same normalized title from more than one source. */
fun crossSourceDuplicateIds(candidates: Collection<DuplicateCandidate>): Set<Long> {
    val uniqueRecords = candidates.distinctBy { it.id }
    val sourcesByTitle = HashMap<String, MutableSet<Long>>()
    for (candidate in uniqueRecords) {
        val key = normalizeLibraryTitle(candidate.title)
        if (key.isNotEmpty()) sourcesByTitle.getOrPut(key) { HashSet() }.add(candidate.sourceId)
    }
    val duplicates = sourcesByTitle.filterValues { it.size > 1 }.keys
    return uniqueRecords.asSequence()
        .filter { normalizeLibraryTitle(it.title) in duplicates }
        .map { it.id }
        .toSet()
}
