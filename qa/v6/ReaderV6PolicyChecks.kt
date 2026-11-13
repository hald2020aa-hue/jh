import eu.kanade.tachiyomi.ui.reader.translation.pagesAhead
import eu.kanade.tachiyomi.ui.reader.translation.translationPageOrder

fun main() {
    check(pagesAhead(9, 30, 7).toList() == (10..16).toList())
    check(pagesAhead(0, 8, 7).toList().size == 7)
    check(pagesAhead(15, 16, 7).isEmpty())
    check(pagesAhead(14, 16, 7).toList() == listOf(15))
    check(pagesAhead(0, 20, 0).isEmpty())
    check(pagesAhead(-1, 20, 7).isEmpty())
    check(pagesAhead(9, 12, 7).toList() == listOf(10, 11))
    check(pagesAhead(0, 8, Int.MAX_VALUE).toList() == (1..7).toList())
    check(translationPageOrder(20, 9).take(8) == (9..16).toList())
    check(translationPageOrder(20, 9).sorted() == (0 until 20).toList())
    check(translationPageOrder(7, 6).sorted() == (0..6).toList())
    check(translationPageOrder(0, 0).isEmpty())
    println("V6 PRELOAD + TRANSLATION ORDER: 12/12 PASS")
}
