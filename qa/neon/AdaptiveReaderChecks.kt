import eu.kanade.tachiyomi.ui.enhanced.adaptiveReaderBarWidth
fun main() {
    check(adaptiveReaderBarWidth(0, false, 600, 720, 16) == -1)
    check(adaptiveReaderBarWidth(450, false, 600, 720, 16) == -1)
    check(adaptiveReaderBarWidth(720, false, 600, 720, 16) == 688)
    check(adaptiveReaderBarWidth(1000, false, 600, 720, 16) == 720)
    check(adaptiveReaderBarWidth(720, true, 600, 720, 16) == -2)
    check(adaptiveReaderBarWidth(650, false, 600, 720, 16) == 618)
    println("PASS: six reader sizing cases")
}