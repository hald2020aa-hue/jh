package eu.kanade.tachiyomi.ui.enhanced

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Runs in the Android unit-test job, not as a UI screenshot or emulator test. */
class NeonUxUnitTest {
    @Test
    fun `chapter progress handles unknown data and complete libraries`() {
        assertNull(knownChapterProgress(0, 0))
        assertEquals(0, knownChapterProgress(0, 4))
        assertEquals(50, knownChapterProgress(100, 100))
        assertEquals(100, knownChapterProgress(500, 0))
        assertEquals(67, knownChapterProgress(182, 86))
        assertEquals(50, knownChapterProgress(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test
    fun `queue sorting changes only presentation and keeps priorities intact`() {
        data class DownloadStub(val name: String, val chapter: Float, val fetchDate: Long, val progress: Int)
        val a = DownloadStub("Zebra", 3f, 200L, 25)
        val b = DownloadStub("alpha", 12f, 100L, 80)
        val c = DownloadStub("Beta", -1f, 300L, 0)
        val rows = listOf(a, b, c)
        fun sorted(mode: DownloadQueueSort) = sortDownloadRows(
            rows, mode, { it.name }, { it.chapter }, { it.fetchDate }, { it.progress },
        )
        assertEquals(rows, sorted(DownloadQueueSort.QUEUE))
        assertEquals(listOf(b, c, a), sorted(DownloadQueueSort.TITLE))
        assertEquals(listOf(b, a, c), sorted(DownloadQueueSort.CHAPTER))
        assertEquals(listOf(c, a, b), sorted(DownloadQueueSort.FETCH_DATE))
        assertEquals(listOf(b, a, c), sorted(DownloadQueueSort.PROGRESS))
        assertEquals(rows, listOf(a, b, c)) // Original queue order is not changed by sorting.
    }

    @Test
    fun `reader controls adapt without breaking phones or vertical layout`() {
        assertEquals(-1, adaptiveReaderBarWidth(480, false, 600, 720, 16))
        assertEquals(688, adaptiveReaderBarWidth(720, false, 600, 720, 16))
        assertEquals(720, adaptiveReaderBarWidth(1500, false, 600, 720, 16))
        assertEquals(-2, adaptiveReaderBarWidth(1000, true, 600, 720, 16))
    }
}
