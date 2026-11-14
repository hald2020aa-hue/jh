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

/** Only a small number of nearby pages are checked for automatic OCR language.
 * Blank covers, credits or full-page drawings must not disable the chapter.
 * No additional preference or user-facing action is introduced.
 */
fun languageDetectionSampleOrder(totalPages: Int, currentIndex: Int, limit: Int = 4): List<Int> {
    if (totalPages <= 0 || limit <= 0) return emptyList()
    val current = currentIndex.coerceIn(0, totalPages - 1)
    val available = (current until totalPages).toList() + (0 until current).toList()
    return available.take(limit)
}

/** Wordless or failed OCR must never be treated as cached Arabic translation. */
fun hasReadableTranslation(page: TranslatedReaderPage): Boolean =
    page.error == null && page.blocks.any { it.arabic.isNotBlank() }

/** Whole-image coordinates for one bounded OCR window on a tall Webtoon page. */
data class OcrTileSlice(
    val start: Int, val end: Int, val keepStart: Int, val keepEnd: Int,
    val fullWidth: Int, val fullHeight: Int,
) {
    val height: Int get() = end - start
}

/** Scan the full tall page in overlapping strips, each bounded by ~2.8 MP.
 * Rounding is deterministic so adjacent OCR tiles never own the same block.
 */
fun ocrTileSlices(width: Int, height: Int, sampleSize: Int): List<OcrTileSlice> {
    if (width <= 0 || height <= 0 || sampleSize <= 0) return emptyList()
    val approximateHeight = (2400L * sampleSize).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val tileHeight = approximateHeight.coerceAtMost(height).coerceAtLeast(1)
    if (height <= tileHeight) return listOf(OcrTileSlice(0, height, 0, height, width, height))
    val overlap = (tileHeight / 12).coerceIn(60, 320).coerceAtMost(tileHeight / 3)
    val ranges = mutableListOf<Pair<Int, Int>>()
    var start = 0
    while (start < height) {
        val end = (start.toLong() + tileHeight).coerceAtMost(height.toLong()).toInt()
        ranges += start to end
        if (end == height) break
        start = end - overlap
    }
    return ranges.mapIndexed { index, (a, b) ->
        val previousOverlap = if (index == 0) 0 else ranges[index - 1].second - a
        val nextOverlap = if (index == ranges.lastIndex) 0 else b - ranges[index + 1].first
        OcrTileSlice(a, b, a + previousOverlap / 2, b - (nextOverlap + 1) / 2,
            width, height)
    }
}

data class NormalizedOcrRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Convert OCR bounds from a sampled tile into normalized full-page geometry. */
fun ocrTileToPageRect(
    l: Float, t: Float, r: Float, b: Float, tile: OcrTileSlice,
): NormalizedOcrRect {
    // Normalized X is unchanged because each strip spans the whole width.
    return NormalizedOcrRect(
        l.coerceIn(0f, 1f),
        ((tile.start + t.coerceIn(0f, 1f) * tile.height) / tile.fullHeight).coerceIn(0f, 1f),
        r.coerceIn(0f, 1f),
        ((tile.start + b.coerceIn(0f, 1f) * tile.height) / tile.fullHeight).coerceIn(0f, 1f),
    )
}
