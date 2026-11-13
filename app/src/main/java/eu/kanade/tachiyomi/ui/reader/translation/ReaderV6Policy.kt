package eu.kanade.tachiyomi.ui.reader.translation

/** Return exactly the next [amount] indexes when available; excludes the visible page. */
fun pagesAhead(currentIndex: Int, totalPages: Int, amount: Int): IntRange {
    if (currentIndex < 0 || totalPages <= 0 || amount <= 0 || currentIndex >= totalPages - 1) return IntRange.EMPTY
    val start = currentIndex + 1
    val last = (currentIndex.toLong() + amount.toLong()).coerceAtMost((totalPages - 1).toLong()).toInt()
    return start..last
}

/** Translate the visible page and its seven neighbors first, then cover the ENTIRE chapter. */
fun translationPageOrder(totalPages: Int, currentIndex: Int): List<Int> {
    if (totalPages <= 0) return emptyList()
    val visible = currentIndex.coerceIn(0, totalPages - 1)
    val ahead = pagesAhead(visible, totalPages, 7)
    val priority = listOf(visible) + ahead.toList()
    val remaining = (0 until totalPages).filterNot { it in priority }
    return priority + remaining
}
