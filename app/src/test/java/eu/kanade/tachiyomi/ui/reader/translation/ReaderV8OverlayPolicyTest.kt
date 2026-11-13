package eu.kanade.tachiyomi.ui.reader.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure math tests; the actual OCR overlay still requires real-device visual QA. */
class ReaderV8OverlayPolicyTest {
    @Test fun `text plate reserves space around OCR glyphs`() {
        val input = NormalizedBubbleRect(.25f, .30f, .60f, .50f)
        val result = expandedOcrTextRegion(input)
        assertTrue(result.left < input.left)
        assertTrue(result.top < input.top)
        assertTrue(result.right > input.right)
        assertTrue(result.bottom > input.bottom)
    }

    @Test fun `text plate never escapes page corners`() {
        val corner = expandedOcrTextRegion(NormalizedBubbleRect(0f, 0f, 1f, 1f))
        assertEquals(0f, corner.left, 0f)
        assertEquals(0f, corner.top, 0f)
        assertEquals(1f, corner.right, 0f)
        assertEquals(1f, corner.bottom, 0f)
    }

    @Test fun `large OCR boxes receive bounded expansion`() {
        val input = NormalizedBubbleRect(.05f, .06f, .95f, .94f)
        val rect = expandedOcrTextRegion(input)
        assertTrue(rect.left >= .015f)
        assertTrue(rect.right <= .985f)
        assertTrue(rect.top >= .032f)
        assertTrue(rect.bottom <= .968f)
    }

    @Test fun `overlay font scale avoids invalid preferences`() {
        assertEquals(1f, safeArabicOverlayFontScale(Float.NaN), 0f)
        assertEquals(1f, safeArabicOverlayFontScale(Float.POSITIVE_INFINITY), 0f)
        assertEquals(.75f, safeArabicOverlayFontScale(-9f), 0f)
        assertEquals(1.6f, safeArabicOverlayFontScale(99f), 0f)
        assertEquals(1.25f, safeArabicOverlayFontScale(1.25f), 0f)
    }

    @Test fun `overlay opacity avoids invalid preferences`() {
        assertEquals(.83f, safeArabicOverlayOpacity(Float.NaN), 0f)
        assertEquals(1f, safeArabicOverlayOpacity(99f), 0f)
        assertEquals(.45f, safeArabicOverlayOpacity(-2f), 0f)
        assertEquals(.65f, safeArabicOverlayOpacity(.65f), 0f)
    }

    @Test fun `seven page prefetch still stops at chapter boundary`() {
        assertEquals(7, pagesAhead(10, 30, 7).count())
        assertEquals(listOf(11, 12, 13, 14, 15, 16, 17), pagesAhead(10, 30, 7).toList())
        assertEquals(listOf(12, 13), pagesAhead(11, 14, 7).toList())
        assertTrue(pagesAhead(13, 14, 7).isEmpty())
        assertFalse(outsideReadAheadWindow(17, 10, 30, 7))
    }
    @Test fun `a nearby bubble prevents the OCR plate crossing other text`() {
        val current = NormalizedBubbleRect(.10f, .20f, .30f, .30f)
        val neighbor = NormalizedBubbleRect(.31f, .22f, .50f, .33f)
        assertEquals(current, safeOcrPlateRegion(current, listOf(neighbor)))
    }

    @Test fun `distant bubble allows a modest plate margin`() {
        val current = NormalizedBubbleRect(.10f, .20f, .30f, .30f)
        val neighbor = NormalizedBubbleRect(.70f, .70f, .90f, .90f)
        assertEquals(expandedOcrTextRegion(current), safeOcrPlateRegion(current, listOf(neighbor)))
    }

}
