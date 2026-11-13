package eu.kanade.tachiyomi.ui.download.center

import android.os.Bundle
import android.widget.ImageView
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.image.coil.loadManga
import eu.kanade.tachiyomi.data.download.DownloadJob
import eu.kanade.tachiyomi.ui.download.NeonDownloadPanel
import eu.kanade.tachiyomi.ui.download.NeonDownloadRow
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.download.model.DownloadQueue
import eu.kanade.tachiyomi.ui.enhanced.EnhancedReadingTheme
import eu.kanade.tachiyomi.ui.enhanced.DownloadQueueSort
import eu.kanade.tachiyomi.ui.enhanced.sortDownloadRows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/** A real queue view backed by DownloadManager; no duplicate download state is maintained. */
class DownloadCenterActivity : AppCompatActivity() {
    private val model: DownloadCenterModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EnhancedReadingTheme {
                if (resources.getBoolean(R.bool.v4_neon_mode)) {
                    val rows by model.neonRows.collectAsState()
                    val paused by model.paused.collectAsState()
                    NeonDownloadPanel(
                        rows = rows,
                        isRunning = !paused,
                        onToggleAll = model::toggleQueue,
                        onDeleteAll = model::clearQueue,
                        onPause = { item -> item.chapter.id?.let(model::pauseChapter) },
                        onResume = { item -> item.chapter.id?.let(model::resumeChapter) },
                        onDelete = { item -> item.chapter.id?.let(model::cancel) },
                        onPrioritize = { item -> item.chapter.id?.let(model::prioritize) },
                        onConcurrentSources = model::setConcurrentSources,
                        onClose = { finish() },
                    )
                } else {
                    DownloadCenterScreen(model = model, onBack = { finish() })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.refresh()
    }
}

internal data class DownloadCenterRow(
    val chapterId: Long,
    val cover: Manga,
    val title: String,
    val chapter: String,
    val source: String,
    val status: Download.State,
    val progress: Int,
    val readyPages: Int,
    val totalPages: Int,
    val chapterNumber: Float,
    val dateFetchedAt: Long,
)

internal class DownloadCenterModel : ViewModel() {
    private val manager: DownloadManager = Injekt.get()
    private val _rows = MutableStateFlow<List<DownloadCenterRow>>(emptyList())
    val rows = _rows.asStateFlow()
    private val _neonRows = MutableStateFlow<List<NeonDownloadRow>>(emptyList())
    val neonRows = _neonRows.asStateFlow()
    private val _paused = MutableStateFlow(true)
    // Frame-friendly coalescing: a page download may notify us dozens of times per second.
    private val refreshSignals = Channel<Unit>(Channel.CONFLATED)
    val paused = _paused.asStateFlow()

    private val observer = object : DownloadQueue.DownloadListener {
        override fun updateDownload(download: Download) { refresh() }
        override fun updateDownloads() { refresh() }
    }

    init {
        manager.queue.addListener(observer)
        viewModelScope.launch(Dispatchers.Main.immediate) {
            refreshSignals.receiveAsFlow().collect {
                _paused.value = manager.isPaused()
                _neonRows.value = manager.queue.map { download ->
                    NeonDownloadRow(
                        download = download,
                        name = download.manga.title,
                        chapterName = download.chapter.name,
                        percentage = download.progress.coerceIn(0, 100),
                        downloadedPages = download.downloadedImages,
                        totalPages = download.pages?.size,
                        paused = download.chapter.id?.let(manager::isChapterPaused) ?: false,
                        status = download.status,
                        completedInCache = manager.getDownloadCount(download.manga),
                    )
                }
                _rows.value = manager.queue.mapNotNull { download ->
                    val id = download.chapter.id ?: return@mapNotNull null
                    DownloadCenterRow(
                        chapterId = id,
                        cover = download.manga,
                        title = download.manga.title,
                        chapter = download.chapter.name,
                        source = download.source.name,
                        status = download.status,
                        progress = download.progress.coerceIn(0, 100),
                        readyPages = download.downloadedImages,
                        totalPages = download.pages?.size ?: 0,
                        chapterNumber = download.chapter.chapter_number,
                        dateFetchedAt = download.chapter.date_fetch,
                    )
                }
            }
        }
        viewModelScope.launch { manager.queue.state.collect { refresh() } }
        refresh()
    }

    /** Non-blocking from downloader callbacks; coalesce bursts into one UI snapshot. */
    fun refresh() {
        refreshSignals.trySend(Unit)
    }

    fun toggleQueue() {
        if (manager.isPaused()) DownloadJob.start(manager.context)
        else manager.pauseDownloads()
        refresh()
    }

    fun pauseChapter(id: Long) {
        manager.pauseChapter(id)
        refresh()
    }

    fun resumeChapter(id: Long) {
        manager.resumeChapter(id)
        refresh()
    }

    fun clearQueue() {
        manager.clearQueue()
        refresh()
    }

    fun setConcurrentSources(count: Int) {
        // Keep this setting inside the established preferences subsystem.
        val prefs = Injekt.get<eu.kanade.tachiyomi.data.preference.PreferencesHelper>()
        prefs.numberOfConcurrentSourceDownloads().set(count.coerceIn(1, 3))
        refresh()
    }


    /** Prioritize via the existing queue API, preserving its persistence and source limits. */
    fun prioritize(id: Long) {
        manager.queue.firstOrNull { it.chapter.id == id }?.let {
            manager.startDownloadNow(it.chapter)
        }
        refresh()
    }

    fun cancel(id: Long) {
        manager.queue.firstOrNull { it.chapter.id == id }?.let {
            manager.deletePendingDownloads(it)
        }
        refresh()
    }

    override fun onCleared() {
        refreshSignals.close()
        manager.queue.removeListener(observer)
        super.onCleared()
    }
}

/** Neutral, readable navy surfaces with restrained magenta/cyan actions. */
private val neonBackground = Color(0xFF080F20)
private val neonSurface = Color(0xFF121C30)
private val neonBorder = Color(0xFF36516F)
private val neonMagenta = Color(0xFFF21C82)
private val neonCyan = Color(0xFF35CEDB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadCenterScreen(model: DownloadCenterModel, onBack: () -> Unit) {
    val queue by model.rows.collectAsState()
    val paused by model.paused.collectAsState()
    var filter by rememberSaveable { mutableStateOf("all") }
    var sortMode by rememberSaveable { mutableStateOf(DownloadQueueSort.QUEUE.name) }
    var sortExpanded by rememberSaveable { mutableStateOf(false) }
    val filtered = when (filter) {
        "failed" -> queue.filter { it.status == Download.State.ERROR }
        "pending" -> queue.filter { it.status == Download.State.QUEUE || it.status == Download.State.DOWNLOADING }
        else -> queue
    }
    val sortedRows = sortDownloadRows(
        rows = filtered,
        mode = DownloadQueueSort.valueOf(sortMode),
        title = { it.title },
        chapterNumber = { it.chapterNumber },
        fetchDate = { it.dateFetchedAt },
        progress = { it.progress },
    )
    val finishedPages = queue.sumOf { it.readyPages }
    val failed = queue.count { it.status == Download.State.ERROR }
    val downloading = queue.count { it.status == Download.State.DOWNLOADING }

    Scaffold(
        containerColor = neonBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(R.string.enhanced_download_center), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = neonBackground,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(insets),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = neonSurface),
                    border = BorderStroke(1.dp, neonBorder),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            stringResource(R.string.enhanced_download_overview, queue.size, finishedPages, failed),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            QueueNumber(
                                label = stringResource(R.string.enhanced_dl_downloading),
                                count = downloading,
                                modifier = Modifier.weight(1f),
                            )
                            QueueNumber(
                                label = stringResource(R.string.enhanced_dl_queued),
                                count = queue.count { it.status == Download.State.QUEUE },
                                modifier = Modifier.weight(1f),
                            )
                            QueueNumber(
                                label = stringResource(R.string.enhanced_dl_error),
                                count = failed,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Button(
                            onClick = model::toggleQueue,
                            enabled = queue.isNotEmpty(),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Icon(
                                if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(if (paused) R.string.resume else R.string.pause))
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "all" to R.string.enhanced_filter_all,
                        "pending" to R.string.enhanced_filter_pending,
                        "failed" to R.string.enhanced_filter_failed,
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = filter == key,
                            onClick = { filter = key },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }
            item {
                Box {
                    OutlinedButton(onClick = { sortExpanded = true }) {
                        Text(stringResource(R.string.enhanced_sort_downloads))
                    }
                    DropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false },
                    ) {
                        listOf(
                            DownloadQueueSort.QUEUE to R.string.enhanced_sort_queue,
                            DownloadQueueSort.FETCH_DATE to R.string.enhanced_sort_fetch_date,
                            DownloadQueueSort.CHAPTER to R.string.enhanced_sort_chapter_number,
                            DownloadQueueSort.TITLE to R.string.enhanced_sort_manga_name,
                            DownloadQueueSort.PROGRESS to R.string.enhanced_sort_progress,
                        ).forEach { (mode, titleRes) ->
                            DropdownMenuItem(
                                text = { Text(stringResource(titleRes)) },
                                onClick = {
                                    sortMode = mode.name
                                    sortExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            if (sortedRows.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = neonSurface),
                        border = BorderStroke(1.dp, neonBorder),
                    ) {
                        Text(
                            stringResource(R.string.enhanced_downloads_empty),
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(sortedRows, key = { it.chapterId }) { entry ->
                DownloadCenterItem(
                    entry = entry,
                    onPrioritize = { model.prioritize(entry.chapterId) },
                    onRemove = { model.cancel(entry.chapterId) },
                )
            }
        }
    }
}

@Composable
private fun QueueNumber(label: String, count: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color(0xFF1B2B44), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(count.toString(), fontWeight = FontWeight.Bold, color = neonCyan)
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DownloadCenterItem(
    entry: DownloadCenterRow,
    onPrioritize: () -> Unit,
    onRemove: () -> Unit,
) {
    var confirmRemove by rememberSaveable(entry.chapterId) { mutableStateOf(false) }
    val statusLabel = when (entry.status) {
        Download.State.QUEUE -> R.string.enhanced_dl_queued
        Download.State.DOWNLOADING -> R.string.enhanced_dl_downloading
        Download.State.DOWNLOADED -> R.string.enhanced_dl_downloaded
        Download.State.ERROR -> R.string.enhanced_dl_error
        else -> R.string.enhanced_dl_waiting
    }
    val statusColor = when (entry.status) {
        Download.State.ERROR -> MaterialTheme.colorScheme.error
        Download.State.DOWNLOADED -> neonCyan
        Download.State.DOWNLOADING -> neonMagenta
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = neonSurface),
        border = BorderStroke(1.dp, neonBorder),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Use the application's extension-aware manga cover loader, not a raw HTTP URL.
                AndroidView(
                    factory = { context ->
                        ImageView(context).apply {
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            setBackgroundColor(android.graphics.Color.rgb(24, 37, 60))
                        }
                    },
                    modifier = Modifier.size(width = 74.dp, height = 100.dp).clip(RoundedCornerShape(12.dp)),
                    update = { view ->
                        if (view.tag != entry.cover.id || view.contentDescription != entry.title) {
                            view.tag = entry.cover.id
                            view.contentDescription = entry.title
                            view.loadManga(entry.cover)
                        }
                    },
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        entry.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(entry.chapter, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(entry.source + " · " + stringResource(statusLabel),
                        style = MaterialTheme.typography.labelMedium, color = statusColor)
                }
            }
            LinearProgressIndicator(
                progress = { entry.progress.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().height(7.dp),
                color = neonMagenta,
                trackColor = Color(0xFF30405A),
            )
            Text(
                stringResource(R.string.enhanced_download_pages, entry.readyPages, entry.totalPages, entry.progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPrioritize,
                    enabled = entry.status != Download.State.DOWNLOADED,
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(stringResource(R.string.enhanced_priority_first))
                }
                OutlinedButton(onClick = { confirmRemove = true }) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(stringResource(R.string.enhanced_remove_queue))
                }
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.enhanced_remove_queue)) },
            text = { Text(entry.title + " · " + entry.chapter) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemove() }) {
                    Text(stringResource(R.string.enhanced_remove_queue))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
