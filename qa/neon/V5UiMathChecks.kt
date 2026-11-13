import eu.kanade.tachiyomi.ui.enhanced.estimatedTransferSeconds
import eu.kanade.tachiyomi.ui.enhanced.knownPagePercentage
import eu.kanade.tachiyomi.ui.enhanced.neonChapterPaletteIndex
import eu.kanade.tachiyomi.ui.enhanced.neonColumnCount

fun main() {
    var passed = 0
    fun test(value: Boolean) { check(value); passed++ }
    test(neonColumnCount(2, 360f) == 2)
    test(neonColumnCount(3, 360f) == 3)
    test(neonColumnCount(5, 360f) == 5)
    test(neonColumnCount(3, 720f) == 6)
    test(neonColumnCount(5, 720f) == 10)
    test(knownPagePercentage(0, 16) == 0)
    test(knownPagePercentage(8, 16) == 50)
    test(knownPagePercentage(16, 16) == 100)
    test(knownPagePercentage(0, null) == null)
    test(knownPagePercentage(0, 0) == null)
    test(knownPagePercentage(100, 16) == 100)
    test(estimatedTransferSeconds(null, 0, 1_000) == null)
    test(estimatedTransferSeconds(100, 50, 0) == null)
    test(estimatedTransferSeconds(100, 50, 10) == 5L)
    test(estimatedTransferSeconds(100, 150, 10) == 0L)
    test(neonChapterPaletteIndex(0f) == 0)
    test(neonChapterPaletteIndex(7f) == 0)
    test(neonChapterPaletteIndex(8f) == 1)
    println("V5 UI MATH: $passed/$passed PASS")
}
