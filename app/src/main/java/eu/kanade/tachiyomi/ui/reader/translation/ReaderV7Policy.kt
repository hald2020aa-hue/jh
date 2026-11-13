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

/**
 * OCR bounding boxes usually contain only the glyphs, not the speech bubble around them.
 * Reserve a modest margin for Arabic lettering while keeping the plate in the image.
 * This is a text-region approximation, NOT speech-bubble contour detection.
 */
fun expandedOcrTextRegion(rect: NormalizedBubbleRect): NormalizedBubbleRect {
    val horizontal = ((rect.right - rect.left) * 0.18f).coerceIn(0.006f, 0.035f)
    val vertical = ((rect.bottom - rect.top) * 0.45f).coerceIn(0.005f, 0.028f)
    return NormalizedBubbleRect(
        (rect.left - horizontal).coerceAtLeast(0f),
        (rect.top - vertical).coerceAtLeast(0f),
        (rect.right + horizontal).coerceAtMost(1f),
        (rect.bottom + vertical).coerceAtMost(1f),
    )
}

/** Screen-independent font/opacity settings; invalid preference values never break drawing. */
fun safeArabicOverlayFontScale(value: Float): Float = if (value.isFinite()) value.coerceIn(0.75f, 1.6f) else 1f
fun safeArabicOverlayOpacity(value: Float): Float = if (value.isFinite()) value.coerceIn(0.45f, 1f) else 0.83f

/** Never enlarge an OCR plate across the printed text of a different bubble. */
fun safeOcrPlateRegion(
    current: NormalizedBubbleRect,
    otherTextRects: List<NormalizedBubbleRect>,
): NormalizedBubbleRect {
    val expanded = expandedOcrTextRegion(current)
    val crossesOtherText = otherTextRects.any { other ->
        expanded.left < other.right && expanded.right > other.left &&
            expanded.top < other.bottom && expanded.bottom > other.top
    }
    return if (crossesOtherText) current else expanded
}
