package eu.kanade.tachiyomi.data.download.model

import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

class Download(
    val source: HttpSource,
    val manga: Manga,
    val chapter: Chapter,
) {
    var pages: List<Page>? = null

    /** Network bytes actually received for this transfer session, never an estimated chapter size. */
    @Transient private val _transferStats = MutableStateFlow(DownloadTransferStats())
    @Transient val transferStats: StateFlow<DownloadTransferStats> = _transferStats.asStateFlow()
    private var sampleBytes = 0L
    private var totalReceivedBytes = 0L
    private var sampleTime = System.nanoTime() / 1_000_000

    @Synchronized
    fun addReceivedBytes(count: Long, force: Boolean = false) {
        if (count < 0) return
        val now = System.nanoTime() / 1_000_000
        totalReceivedBytes += count
        val newBytes = totalReceivedBytes
        val elapsed = (now - sampleTime).coerceAtLeast(1L)
        if (force || elapsed >= 300L) {
            val speed = if (elapsed >= 300L) (newBytes - sampleBytes) * 1000L / elapsed else _transferStats.value.speedBytesPerSecond
            _transferStats.value = _transferStats.value.copy(receivedBytes = newBytes, speedBytesPerSecond = speed)
            sampleBytes = newBytes
            sampleTime = now
        }
    }

    @Synchronized
    fun stopTransferRate() {
        _transferStats.value = _transferStats.value.copy(receivedBytes = totalReceivedBytes, speedBytesPerSecond = 0)
        sampleBytes = totalReceivedBytes
        sampleTime = System.nanoTime() / 1_000_000
    }

    @Synchronized
    fun setKnownChapterBytes(total: Long) {
        if (total >= 0) _transferStats.value = _transferStats.value.copy(knownChapterBytes = total)
    }


    val totalProgress: Int
        get() = pages?.sumOf(Page::progress) ?: 0

    val downloadedImages: Int
        get() = pages?.count { it.status == Page.State.READY } ?: 0

    @Volatile @Transient
    var status: State = State.default
        set(status) {
            field = status
            _statusFlow.value = status
            statusCallback?.invoke(this)
        }

    @Transient private val _statusFlow = MutableStateFlow(State.default)

    /**
     * Emits on every [status] change. The downloader watches this to notice an active download
     * erroring, which frees up its source for the next chapter without the queue itself changing.
     */
    @Transient val statusFlow: StateFlow<State> = _statusFlow.asStateFlow()

    @Transient private var statusCallback: ((Download) -> Unit)? = null

    val pageProgress: Int
        get() {
            val pages = pages ?: return 0
            return pages.map(Page::progress).sum()
        }

    val progress: Int
        get() {
            val pages = pages ?: return 0
            return pages.map(Page::progress).average().roundToInt()
        }

    fun setStatusCallback(f: ((Download) -> Unit)?) {
        statusCallback = f
    }

    enum class State {
        CHECKED,
        NOT_DOWNLOADED,
        QUEUE,
        DOWNLOADING,
        DOWNLOADED,
        ERROR,

        /** Downloaded status not yet known - the download cache hasn't finished its initial scan. */
        PENDING,
        ;

        companion object {
            val default = NOT_DOWNLOADED
        }
    }
}

/** A nullable total means the source does not disclose the full chapter size yet. */
data class DownloadTransferStats(
    val receivedBytes: Long = 0L,
    val knownChapterBytes: Long? = null,
    val speedBytesPerSecond: Long = 0L,
)
