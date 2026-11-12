import eu.kanade.tachiyomi.ui.enhanced.knownChapterProgress
fun main() {
    check(knownChapterProgress(0, 0) == null)
    check(knownChapterProgress(0, 100) == 0)
    check(knownChapterProgress(50, 50) == 50)
    check(knownChapterProgress(182, 86) == 67)
    check(knownChapterProgress(100, 0) == 100)
    check(knownChapterProgress(-1, 10) == 0)
    check(knownChapterProgress(Int.MAX_VALUE, Int.MAX_VALUE) == 50)
    println("PASS: seven real chapter progress cases")
}