package eu.kanade.tachiyomi.ui.reader.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderV7PolicyTest {
    @Test fun `normalizes an OCR rectangle without changing original image`() {
        assertEquals(NormalizedBubbleRect(0f, 0.25f, 1f, 0.9f), normalizedBubbleRect(-0.2f, .25f, 1.2f, .9f))
    }

    @Test fun `drops invalid OCR rectangles`() {
        assertNull(normalizedBubbleRect(.6f, .1f, .2f, .3f))
        assertNull(normalizedBubbleRect(Float.NaN, .1f, .5f, .3f))
        assertNull(normalizedBubbleRect(.1f, .1f, .2f, Float.POSITIVE_INFINITY))
    }

    @Test fun `seven page window tracks current reading page`() {
        assertFalse(outsideReadAheadWindow(11, 10, 40, 7))
        assertFalse(outsideReadAheadWindow(17, 10, 40, 7))
        assertTrue(outsideReadAheadWindow(18, 10, 40, 7))
        assertTrue(outsideReadAheadWindow(9, 10, 40, 7))
    }

    @Test fun `retry processes only failed pages and prioritizes current`() {
        assertEquals(listOf(9, 15, 2), failedPageRetryOrder(20, 8, setOf(2, 15, 9)))
        assertTrue(failedPageRetryOrder(10, 3, emptySet()).isEmpty())
    }
}
