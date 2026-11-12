package eu.kanade.tachiyomi.ui.enhanced

import eu.kanade.tachiyomi.ui.library.insights.LibraryInsightEntry
import eu.kanade.tachiyomi.ui.library.insights.calculateLibraryInsights
import eu.kanade.tachiyomi.ui.source.health.ProbeStatus
import eu.kanade.tachiyomi.ui.source.health.SourceHealthRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhancedUiTest {
    @Test
    fun classifySourceResponses() {
        assertEquals(ProbeStatus.AVAILABLE, SourceHealthRules.classify(200))
        assertEquals(ProbeStatus.AVAILABLE, SourceHealthRules.classify(302))
        assertEquals(ProbeStatus.LIMITED, SourceHealthRules.classify(403))
        assertEquals(ProbeStatus.LIMITED, SourceHealthRules.classify(429))
        assertEquals(ProbeStatus.UNAVAILABLE, SourceHealthRules.classify(503))
    }

    @Test
    fun doNotProbeLocalInfrastructure() {
        assertFalse(SourceHealthRules.isSafeHttpEndpoint("http://127.0.0.1"))
        assertFalse(SourceHealthRules.isSafeHttpEndpoint("http://192.168.1.1"))
        assertFalse(SourceHealthRules.isSafeHttpEndpoint("http://169.254.169.254"))
        assertFalse(SourceHealthRules.isSafeHttpEndpoint("http://[::1]"))
        assertTrue(SourceHealthRules.isSafeHttpEndpoint("https://example.org"))
    }

    @Test
    fun libraryInsightsIgnoreCategoryDuplicates() {
        val first = LibraryInsightEntry(1, 100, "Title!", 4, 1, false)
        val second = LibraryInsightEntry(2, 200, "Title", 2, 3, true)
        val metrics = calculateLibraryInsights(listOf(first, first, second))
        assertEquals(2, metrics.titles)
        assertEquals(2, metrics.sources)
        assertEquals(2, metrics.likelyDuplicateTitles)
        assertEquals(6, metrics.unreadChapters)
        assertEquals(4, metrics.readChapters)
        assertEquals(1, metrics.completedTitles)
    }
}
