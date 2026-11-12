package eu.kanade.tachiyomi.qa.enhanced

import eu.kanade.tachiyomi.ui.source.health.ProbeStatus
import eu.kanade.tachiyomi.ui.source.health.SourceHealthRules
import eu.kanade.tachiyomi.ui.library.insights.LibraryInsightEntry
import eu.kanade.tachiyomi.ui.library.insights.calculateLibraryInsights

private var checks = 0
private fun checkCase(test: Boolean, label: String) {
    check(test) { "FAILED: $label" }
    println("PASS: $label")
    checks++
}

fun main() {
    for (code in listOf(200, 201, 204, 301, 302, 304)) {
        checkCase(SourceHealthRules.classify(code) == ProbeStatus.AVAILABLE, "HTTP $code available")
    }
    for (code in listOf(401, 403, 407, 429)) {
        checkCase(SourceHealthRules.classify(code) == ProbeStatus.LIMITED, "HTTP $code limited")
    }
    for (code in listOf(400, 404, 500, 503)) {
        checkCase(SourceHealthRules.classify(code) == ProbeStatus.UNAVAILABLE, "HTTP $code unavailable")
    }
    for (url in listOf("https://example.com", "http://example.org/search", "https://manga.example.net")) {
        checkCase(SourceHealthRules.isSafeHttpEndpoint(url), "Accept valid URL $url")
    }
    for (url in listOf("file:///etc/passwd", "https://127.0.0.1", "http://10.1.2.3", "http://192.168.1.1", "http://172.16.4.5", "http://169.254.169.254", "https://localhost", "http://[::1]", "http://[fe80::1]", "http://server.local", "http://user:pass@example.com", "javascript:alert(1)")) {
        checkCase(!SourceHealthRules.isSafeHttpEndpoint(url), "Reject unsafe URL $url")
    }
    val sample = listOf(
        LibraryInsightEntry(1, 100, "الفتى الساحر", 4, 2, false),
        LibraryInsightEntry(2, 200, "الْفَتَى السَّاحِر", 1, 3, true),
        LibraryInsightEntry(2, 200, "الْفَتَى السَّاحِر", 1, 3, true), // repeat category row
        LibraryInsightEntry(3, 200, "Another work", -1, -3, false), // corrupt negative counters
    )
    val result = calculateLibraryInsights(sample)
    checkCase(result.titles == 3, "Multi-category rows deduplicated")
    checkCase(result.unreadChapters == 5, "Unread chapters aggregated")
    checkCase(result.readChapters == 5, "Read chapters aggregated")
    checkCase(result.sources == 2, "Source count distinct")
    checkCase(result.likelyDuplicateTitles == 2, "Arabic-normalized cross-source duplicates")
    checkCase(result.completedTitles == 1, "Completed title count")
    val massive = (0 until 4000).map { index ->
        LibraryInsightEntry(index.toLong(), (index % 20).toLong(), "Manga #$index", 1, 2, false)
    }
    val metric = calculateLibraryInsights(massive)
    checkCase(metric.titles == 4000 && metric.sources == 20 && metric.unreadChapters == 4000, "4000 titles aggregated")
    println("ENHANCED UI LOGIC TESTS: $checks / $checks PASS")
}
