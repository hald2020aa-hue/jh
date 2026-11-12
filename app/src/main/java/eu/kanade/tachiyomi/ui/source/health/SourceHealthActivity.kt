package eu.kanade.tachiyomi.ui.source.health

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.enhanced.EnhancedReadingTheme
import eu.kanade.tachiyomi.ui.enhanced.healthColors
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.SourceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

/** Real status checks for installed online sources; never equate HTTP 200 with working chapters. */
class SourceHealthActivity : AppCompatActivity() {
    private val healthModel: SourceHealthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EnhancedReadingTheme {
                SourceHealthScreen(
                    model = healthModel,
                    onBack = { finish() },
                    onShare = {
                        val text = healthModel.exportReport()
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        startActivity(Intent.createChooser(intent, getString(R.string.enhanced_source_health)))
                    },
                )
            }
        }
    }
}

internal data class HealthSource(
    val id: Long,
    val title: String,
    val lang: String,
    val baseUrl: String,
    val status: ProbeStatus = ProbeStatus.NOT_CHECKED,
    val httpCode: Int? = null,
    val elapsedMs: Long? = null,
    val errorType: String? = null,
    val deepStatus: DeepProbeStatus = DeepProbeStatus.NOT_CHECKED,
    val catalogueCount: Int = 0,
    val chapterCount: Int = 0,
    val pageCount: Int = 0,
    val deepError: String? = null,
)

internal enum class DeepProbeStatus { NOT_CHECKED, CHECKING, OK, EMPTY, FAILED }

internal class SourceHealthViewModel : ViewModel() {
    private val sourceManager: SourceManager = Injekt.get()
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private val _sources = MutableStateFlow<List<HealthSource>>(emptyList())
    val sources = _sources.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning = _scanning.asStateFlow()
    private var scanJob: Job? = null
    private val scanLimit = Semaphore(4)
    // More expensive source API calls only occur when user explicitly requests them.
    private val deepScanLimit = Semaphore(1)

    init {
        refreshSources()
    }

    fun refreshSources() {
        if (_scanning.value) return
        _sources.value = runCatching {
            sourceManager.getOnlineSources().distinctBy { it.id }
                .sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
                .map { source ->
                    HealthSource(source.id, source.name, source.lang, source.baseUrl)
                }
        }.getOrElse { emptyList() }
    }

    fun scanAll() {
        if (_scanning.value) return
        refreshSources()
        _scanning.value = true
        _sources.update { current -> current.map { it.copy(status = ProbeStatus.CHECKING) } }
        val snapshot = _sources.value
        scanJob = viewModelScope.launch {
            try {
                snapshot.map { source ->
                    async(Dispatchers.IO) {
                        scanLimit.withPermit { probe(source) }
                    }
                }.forEach { deferred ->
                    val result = deferred.await()
                    _sources.update { current -> current.map { if (it.id == result.id) result else it } }
                }
            } finally {
                _scanning.value = false
            }
        }
    }

    fun scanOne(id: Long) {
        if (_scanning.value) return
        val source = _sources.value.firstOrNull { it.id == id } ?: return
        _sources.update { it.map { entry -> if (entry.id == id) entry.copy(status = ProbeStatus.CHECKING) else entry } }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { scanLimit.withPermit { probe(source) } }
            _sources.update { it.map { entry -> if (entry.id == id) result else entry } }
        }
    }

    /** Test browse -> manga details -> chapters -> page URLs without downloading image bytes. */
    fun scanDeep(id: Long) {
        if (_scanning.value) return
        val entry = _sources.value.firstOrNull { it.id == id } ?: return
        if (entry.deepStatus == DeepProbeStatus.CHECKING) return
        _sources.update { entries -> entries.map { if (it.id == id) it.copy(deepStatus = DeepProbeStatus.CHECKING, deepError = null) else it } }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { deepScanLimit.withPermit { deepProbe(entry) } }
            _sources.update { entries ->
                entries.map { current ->
                    if (current.id == id) current.copy(
                        deepStatus = outcome.deepStatus,
                        catalogueCount = outcome.catalogueCount,
                        chapterCount = outcome.chapterCount,
                        pageCount = outcome.pageCount,
                        deepError = outcome.deepError,
                    ) else current
                }
            }
        }
    }

    private suspend fun deepProbe(entry: HealthSource): HealthSource {
        if (!SourceHealthRules.isSafeHttpEndpoint(entry.baseUrl)) {
            return entry.copy(deepStatus = DeepProbeStatus.FAILED, deepError = "Unsafe endpoint")
        }
        val source = sourceManager.get(entry.id) as? HttpSource
            ?: return entry.copy(deepStatus = DeepProbeStatus.FAILED, deepError = "Source unavailable")
        var catalogCount = 0
        var chaptersCount = 0
        return try {
            withTimeout(45_000L) {
                val mangas = source.getPopularManga(1).mangas
                catalogCount = mangas.size
                val firstManga = mangas.firstOrNull()
                    ?: return@withTimeout entry.copy(deepStatus = DeepProbeStatus.EMPTY)
                val details = source.getMangaDetails(firstManga)
                val chapters = source.getChapterList(details)
                chaptersCount = chapters.size
                val firstChapter = chapters.firstOrNull()
                    ?: return@withTimeout entry.copy(
                        deepStatus = DeepProbeStatus.EMPTY,
                        catalogueCount = catalogCount,
                    )
                val pages = source.getPageList(firstChapter)
                // This counts returned page records, not successfully downloaded images.
                entry.copy(
                    deepStatus = if (pages.isEmpty()) DeepProbeStatus.EMPTY else DeepProbeStatus.OK,
                    catalogueCount = catalogCount,
                    chapterCount = chaptersCount,
                    pageCount = pages.size,
                )
            }
        } catch (_: TimeoutCancellationException) {
            entry.copy(deepStatus = DeepProbeStatus.FAILED, catalogueCount = catalogCount,
                chapterCount = chaptersCount, deepError = "Timeout 45s")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            entry.copy(deepStatus = DeepProbeStatus.FAILED, catalogueCount = catalogCount,
                chapterCount = chaptersCount, deepError = error.javaClass.simpleName)
        }
    }

    private fun probe(source: HealthSource): HealthSource {
        if (!SourceHealthRules.isSafeHttpEndpoint(source.baseUrl)) {
            return source.copy(status = ProbeStatus.INVALID_ENDPOINT, errorType = "Unsafe URL")
        }
        val request = Request.Builder()
            .url(source.baseUrl)
            .header("User-Agent", NetworkHelper.DEFAULT_USER_AGENT)
            .get()
            .build()
        val started = SystemClock.elapsedRealtime()
        return try {
            client.newCall(request).execute().use { response ->
                source.copy(
                    status = SourceHealthRules.classify(response.code),
                    httpCode = response.code,
                    elapsedMs = SystemClock.elapsedRealtime() - started,
                    errorType = null,
                )
            }
        } catch (error: Exception) {
            source.copy(
                status = ProbeStatus.UNAVAILABLE,
                elapsedMs = SystemClock.elapsedRealtime() - started,
                errorType = error.javaClass.simpleName,
            )
        }
    }

    fun exportReport(): String = buildString {
        appendLine("J2K Enhanced - HTTP source health")
        sources.value.forEach { source ->
            appendLine("${source.title} [${source.lang}] ${source.status} HTTP=${source.httpCode ?: "-"} ${source.elapsedMs ?: "-"}ms ${source.errorType.orEmpty()} " +
                "browse=${source.catalogueCount} chapters=${source.chapterCount} pages=${source.pageCount} deep=${source.deepStatus} ${source.deepError.orEmpty()}")
        }
        appendLine("Base URL checks and user-triggered browse/chapter/page-list checks only; image bytes and full reading not verified.")
    }

    override fun onCleared() {
        scanJob?.cancel()
        client.dispatcher.cancelAll()
        super.onCleared()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceHealthScreen(
    model: SourceHealthViewModel,
    onBack: () -> Unit,
    onShare: () -> Unit,
) {
    val sources by model.sources.collectAsState()
    val scanning by model.scanning.collectAsState()
    val inspected = sources.count { it.status !in listOf(ProbeStatus.NOT_CHECKED, ProbeStatus.CHECKING) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.enhanced_source_health), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                    }
                },
                actions = {
                    IconButton(onClick = onShare, enabled = inspected > 0) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = healthColors.background),
            )
        },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.padding(insets),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 36.dp, top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            stringResource(R.string.enhanced_sources_count, sources.size, inspected),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(stringResource(R.string.enhanced_source_health_help), style = MaterialTheme.typography.bodySmall)
                        Button(onClick = model::scanAll, enabled = !scanning && sources.isNotEmpty()) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.enhanced_scan_all))
                        }
                        if (scanning) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            if (sources.isEmpty()) {
                item { Text(stringResource(R.string.enhanced_no_sources), modifier = Modifier.padding(16.dp)) }
            }
            items(sources, key = { it.id }) { entry ->
                SourceHealthCard(entry = entry, scanning = scanning,
                    onRetry = { model.scanOne(entry.id) }, onDeepTest = { model.scanDeep(entry.id) })
            }
        }
    }
}

@Composable
private fun SourceHealthCard(entry: HealthSource, scanning: Boolean, onRetry: () -> Unit, onDeepTest: () -> Unit) {
    val (label, statusColor) = when (entry.status) {
        ProbeStatus.NOT_CHECKED -> stringResource(R.string.enhanced_not_checked) to MaterialTheme.colorScheme.onSurfaceVariant
        ProbeStatus.CHECKING -> stringResource(R.string.enhanced_checking) to MaterialTheme.colorScheme.primary
        ProbeStatus.AVAILABLE -> stringResource(R.string.enhanced_online) to Color(0xFF86E6BD)
        ProbeStatus.LIMITED -> stringResource(R.string.enhanced_blocked) to Color(0xFFFFCC7E)
        ProbeStatus.UNAVAILABLE, ProbeStatus.INVALID_ENDPOINT -> stringResource(R.string.enhanced_unavailable) to Color(0xFFFF9A97)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.title, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(label, color = statusColor, style = MaterialTheme.typography.labelMedium)
            }
            Text(entry.baseUrl, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val technical = when {
                entry.httpCode != null && entry.elapsedMs != null -> stringResource(R.string.enhanced_request_status, entry.httpCode, entry.elapsedMs)
                entry.errorType != null -> "${stringResource(R.string.enhanced_network_error)}: ${entry.errorType}"
                else -> entry.lang
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(technical, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onRetry, enabled = !scanning && entry.status != ProbeStatus.CHECKING) {
                    Text(stringResource(R.string.enhanced_scan_again))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    val detailText = when (entry.deepStatus) {
                        DeepProbeStatus.NOT_CHECKED -> stringResource(R.string.enhanced_deep_not_checked)
                        DeepProbeStatus.CHECKING -> stringResource(R.string.enhanced_deep_checking)
                        DeepProbeStatus.FAILED -> stringResource(R.string.enhanced_deep_failed, entry.deepError.orEmpty())
                        DeepProbeStatus.EMPTY, DeepProbeStatus.OK -> stringResource(
                            R.string.enhanced_deep_results,
                            entry.catalogueCount, entry.chapterCount, entry.pageCount,
                        )
                    }
                    Text(detailText, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onDeepTest,
                    enabled = !scanning && entry.deepStatus != DeepProbeStatus.CHECKING) {
                    Text(stringResource(R.string.enhanced_deep_test))
                }
            }
        }
    }
}
