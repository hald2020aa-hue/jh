package eu.kanade.tachiyomi.ui.enhanced

/** Read percentage from known chapters; no cover image or network work on the UI thread. */
fun knownChapterProgress(read: Int, unread: Int): Int? {
    val r = read.coerceAtLeast(0).toLong()
    val u = unread.coerceAtLeast(0).toLong()
    val total = r + u
    if (total == 0L) return null
    return ((r * 100L) / total).toInt().coerceIn(0, 100)
}
