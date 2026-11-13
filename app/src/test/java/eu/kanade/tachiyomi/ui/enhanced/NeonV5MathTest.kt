package eu.kanade.tachiyomi.ui.enhanced

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** V5 regression checks for real display modes and honest download metadata. */
class NeonV5MathTest {
    @Test
    fun `two three and five columns remain exact on phone and adapt when unfolded`() {
        assertEquals(2, neonColumnCount(2, 360f))
        assertEquals(3, neonColumnCount(3, 360f))
        assertEquals(5, neonColumnCount(5, 360f))
        assertEquals(6, neonColumnCount(3, 720f))
        assertEquals(10, neonColumnCount(5, 720f))
    }

    @Test
    fun `zero and unknown chapter pages do not manufacture progress`() {
        assertNull(knownPagePercentage(1, null))
        assertNull(knownPagePercentage(0, 0))
        assertEquals(0, knownPagePercentage(0, 16))
        assertEquals(50, knownPagePercentage(8, 16))
        assertEquals(100, knownPagePercentage(16, 16))
        assertEquals(100, knownPagePercentage(20, 16))
    }

    @Test
    fun `ETA is omitted unless true byte total and observed speed exist`() {
        assertNull(estimatedTransferSeconds(null, 1024L, 2048L))
        assertNull(estimatedTransferSeconds(2048L, 1024L, 0))
        assertEquals(2L, estimatedTransferSeconds(3072L, 1024L, 1024L))
        assertEquals(0L, estimatedTransferSeconds(3072L, 4096L, 1024L))
    }

    @Test
    fun `chapter palettes rotate on chapter change`() {
        assertEquals(0, neonChapterPaletteIndex(0f))
        assertEquals(1, neonChapterPaletteIndex(1f))
        assertEquals(0, neonChapterPaletteIndex(7f))
        assertEquals(1, neonChapterPaletteIndex(8f))
    }
}
