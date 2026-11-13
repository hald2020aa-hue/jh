package eu.kanade.tachiyomi.ui.reader.translation

/** The OCR network is optional; keep its geometry normalized and bounded to the source image. */
data class NormalizedBubbleRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

fun normalizedBubbleRect(left: Float, top: Float, right: Float, bottom: Float): NormalizedBubbleRect? {
    if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) return null
    val l = left.coerceIn(0f, 1f)
    val t = top.coerceIn(0f, 1f)
    val r = right.coerceIn(0f, 1f)
    val b = bottom.coerceIn(0f, 1f)
    return if (r <= l || b <= t) null else NormalizedBubbleRect(l, t, r, b)
}

/** Ignore queued speculative pages that are no longer within the next seven pages. */
fun outsideReadAheadWindow(pageIndex: Int, visibleIndex: Int, totalPages: Int, ahead: Int): Boolean =
    pageIndex !in pagesAhead(visibleIndex, totalPages, ahead)

/** A retry must visit failed pages only and preserve the translator's successful results. */
fun failedPageRetryOrder(totalPages: Int, visibleIndex: Int, failures: Set<Int>): List<Int> =
    translationPageOrder(totalPages, visibleIndex).filter { it in failures }
