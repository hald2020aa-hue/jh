package eu.kanade.tachiyomi.ui.reader.translation

import org.junit.Assert.*
import org.junit.Test

class V9BubbleContourPolicyTest {
    @Test fun `enclosed white balloon is detected without covering page`() {
        val w = 100
        val h = 100
        val px = IntArray(w * h) { -0x1000000 }
        for (y in 22..63) for (x in 19..72) px[y * w + x] = -1
        // Black glyphs inside the bubble.
        for (y in 38..43) for (x in 40..51) px[y * w + x] = -0x1000000
        val region = detectSpeechBalloonRegion(px, w, h, NormalizedBubbleRect(.40f,.38f,.52f,.44f))
        val confirmed = requireNotNull(region)
        assertTrue(confirmed.left <= .20f)
        assertTrue(confirmed.right <= .74f)
        assertTrue(confirmed.bottom <= .65f)
    }
    @Test fun `white page background is never treated as speech balloon`() {
        val px = IntArray(100 * 100) { -1 }
        assertNull(detectSpeechBalloonRegion(px, 100, 100, NormalizedBubbleRect(.40f,.40f,.55f,.47f)))
    }
    @Test fun `never cover another text region`() {
        val px = IntArray(100 * 100) { -0x1000000 }
        for (y in 22..63) for (x in 19..72) px[y * 100 + x] = -1
        for (y in 38..43) for (x in 40..51) px[y * 100 + x] = -0x1000000
        val current = NormalizedBubbleRect(.40f,.38f,.52f,.44f)
        assertNull(detectSpeechBalloonRegion(px,100,100,current,listOf(NormalizedBubbleRect(.60f,.40f,.69f,.45f))))
    }
    @Test fun `language scripts route through English only when needed`() {
        assertEquals(SourceTextLanguage.JAPANESE, recognizeTextScript("こんにちは"))
        assertEquals(SourceTextLanguage.CHINESE, recognizeTextScript("你好世界"))
        assertEquals(SourceTextLanguage.KOREAN, recognizeTextScript("안녕하세요"))
        assertEquals(SourceTextLanguage.ENGLISH, recognizeTextScript("Hello"))
        assertTrue(requiresEnglishPivot(SourceTextLanguage.JAPANESE))
        assertFalse(requiresEnglishPivot(SourceTextLanguage.ENGLISH))
    }
}
