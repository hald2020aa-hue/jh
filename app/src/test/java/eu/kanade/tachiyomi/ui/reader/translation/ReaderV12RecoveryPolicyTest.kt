package eu.kanade.tachiyomi.ui.reader.translation

import org.junit.Assert.*
import org.junit.Test

class ReaderV12RecoveryPolicyTest {
    @Test fun `sample visible and next pages so a blank cover cannot block language detection`() {
        assertEquals(listOf(0, 1, 2, 3), languageDetectionSampleOrder(40, 0))
        assertEquals(listOf(8, 9, 0, 1), languageDetectionSampleOrder(10, 8))
        assertEquals(listOf(0), languageDetectionSampleOrder(1, 0))
        assertTrue(languageDetectionSampleOrder(0, 0).isEmpty())
    }

    @Test fun `do not cache blank OCR or failure as completed Arabic`() {
        val blank = TranslatedReaderPage(2, emptyList())
        val failed = TranslatedReaderPage(2, emptyList(), "OCR unavailable")
        val arabic = TranslatedReaderPage(2, listOf(TranslatedTextBlock(
            "Hello", "مرحبا", .1f, .2f, .3f, .4f,
        )))
        assertFalse(hasReadableTranslation(blank))
        assertFalse(hasReadableTranslation(failed))
        assertTrue(hasReadableTranslation(arabic))
        assertFalse(hasReadableTranslation(arabic.copy(error = "failed")))
        assertFalse(hasReadableTranslation(arabic.copy(blocks = listOf(arabic.blocks[0].copy(arabic = " ")))))
    }

    @Test fun `tall webtoon tiles cover the entire source without duplicate ownership`() {
        val tiles = ocrTileSlices(900, 20500, 1)
        assertTrue(tiles.size >= 8)
        assertEquals(0, tiles.first().start)
        assertEquals(0, tiles.first().keepStart)
        assertEquals(20500, tiles.last().end)
        assertEquals(20500, tiles.last().keepEnd)
        for (pair in tiles.zipWithNext()) {
            assertTrue(pair.first.end >= pair.second.start)
            assertEquals(pair.first.keepEnd, pair.second.keepStart)
        }
    }

    @Test fun `normalized positions of a lower tile map to the full webtoon page`() {
        val tile = ocrTileSlices(1000, 10000, 1)[1]
        val mapped = ocrTileToPageRect(.2f, .1f, .8f, .3f, tile)
        assertEquals(.2f, mapped.left, .0001f)
        assertEquals(.8f, mapped.right, .0001f)
        assertEquals((tile.start + tile.height * .1f) / 10000f, mapped.top, .0001f)
        assertTrue(mapped.bottom > mapped.top)
    }
}
