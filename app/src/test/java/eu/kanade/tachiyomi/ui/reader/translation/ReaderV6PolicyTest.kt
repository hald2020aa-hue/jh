package eu.kanade.tachiyomi.ui.reader.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderV6PolicyTest {
    @Test fun `ahead contains exactly seven next pages`() {
        assertEquals((11..17).toList(), pagesAhead(10, 40, 7).toList())
    }

    @Test fun `preload never exceeds chapter bounds`() {
        assertEquals(listOf(19), pagesAhead(18, 20, 7).toList())
        assertTrue(pagesAhead(19, 20, 7).isEmpty())
    }

    @Test fun `translation prioritizes visible page then next seven`() {
        assertEquals((5..12).toList(), translationPageOrder(20, 5).take(8))
    }

    @Test fun `translation eventually visits each page once`() {
        val order = translationPageOrder(100, 60)
        assertEquals(100, order.size)
        assertEquals((0 until 100).toList(), order.sorted())
    }
}
