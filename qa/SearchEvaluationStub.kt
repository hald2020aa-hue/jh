package eu.kanade.tachiyomi.ui.library

class FakeManga(
    val title: String = "",
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val source: Long = 1,
    val id: Long? = 1,
    val date_added: Long = 0L,
    val unread: Int = 0,
    val read: Int = 0,
    val totalChapters: Int = 0,
    val score: Float = 0f,
) {
    fun getGenres(): List<String>? = null
}
class LibraryItem(val manga: FakeManga, val isCrossSourceDuplicate: Boolean = false) {
    val sourceName get() = "Test Source"
    fun matchesSourceName(v: String) = sourceName.contains(v, ignoreCase=true)
    fun matchesGenreOrType(v: String) = false
    fun matchesSeriesType(v: String) = false
    fun searchLanguage(): String = "en"
}
