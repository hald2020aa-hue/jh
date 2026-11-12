package eu.kanade.tachiyomi.ui.enhanced

/**
 * -1 == MATCH_PARENT, -2 == WRAP_CONTENT. Keeps navigation reachable on phones,
 * while centering a readable-width bar on expanded foldable/tablet displays.
 * A physically separating hinge is handled independently in ReaderActivity.
 */
fun adaptiveReaderBarWidth(
    viewportPx: Int,
    vertical: Boolean,
    tabletThresholdPx: Int,
    maxBarPx: Int,
    horizontalPaddingPx: Int,
): Int {
    if (vertical) return -2
    if (viewportPx <= 0 || viewportPx < tabletThresholdPx) return -1
    return (viewportPx - horizontalPaddingPx * 2).coerceAtLeast(0).coerceAtMost(maxBarPx)
}
