import eu.kanade.tachiyomi.ui.enhanced.DownloadQueueSort
import eu.kanade.tachiyomi.ui.enhanced.sortDownloadRows

data class Row(val title: String, val number: Float, val date: Long, val percent: Int)
fun main() {
    val a = Row("Zebra", 3f, 200, 25)
    val b = Row("alpha", 12f, 100, 80)
    val c = Row("Beta", -1f, 300, 0)
    val rows = listOf(a,b,c)
    fun ordered(mode: DownloadQueueSort) = sortDownloadRows(rows,mode,{it.title},{it.number},{it.date},{it.percent})
    check(ordered(DownloadQueueSort.QUEUE) == listOf(a,b,c))
    check(ordered(DownloadQueueSort.TITLE) == listOf(b,c,a))
    check(ordered(DownloadQueueSort.CHAPTER) == listOf(b,a,c))
    check(ordered(DownloadQueueSort.FETCH_DATE) == listOf(c,a,b))
    check(ordered(DownloadQueueSort.PROGRESS) == listOf(b,a,c))
    check(sortDownloadRows(emptyList<Row>(),DownloadQueueSort.QUEUE,{it.title},{it.number},{it.date},{it.percent}).isEmpty())
    check(sortDownloadRows(listOf(Row("zero",Float.NaN,0,0),b),DownloadQueueSort.CHAPTER,{it.title},{it.number},{it.date},{it.percent}).first() == b)
    println("PASS: seven sorted-download queue scenarios")
}
