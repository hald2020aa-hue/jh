package qa.v7

import eu.kanade.tachiyomi.ui.reader.translation.*

fun main() {
    check(pagesAhead(10, 50, 7).toList() == (11..17).toList())
    check(pagesAhead(17, 20, 7).toList() == listOf(18, 19))
    check(outsideReadAheadWindow(20, 10, 40, 7))
    check(!outsideReadAheadWindow(17, 10, 40, 7))
    check(outsideReadAheadWindow(0, 10, 40, 7))
    check(failedPageRetryOrder(20, 8, setOf(2, 15, 9)) == listOf(9, 15, 2))
    check(failedPageRetryOrder(20, 8, emptySet()).isEmpty())
    check(normalizedBubbleRect(-1f, .25f, 2f, .9f) == NormalizedBubbleRect(0f, .25f, 1f, .9f))
    check(normalizedBubbleRect(.6f, .1f, .2f, .4f) == null)
    check(normalizedBubbleRect(Float.NaN, .2f, .4f, .5f) == null)
    check(normalizedBubbleRect(.1f, .2f, Float.POSITIVE_INFINITY, .5f) == null)
    check(translationPageOrder(100, 25).take(8) == (25..32).toList())
    check(translationPageOrder(23, 18).sorted() == (0 until 23).toList())
    println("V7 PURE KOTLIN POLICY: 13 / 13 PASS")
}
