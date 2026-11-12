package eu.kanade.tachiyomi.ui.library.insights

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.ui.enhanced.EnhancedReadingTheme
import eu.kanade.tachiyomi.ui.source.health.SourceHealthActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class LibraryInsightsActivity : AppCompatActivity() {
    private val insightModel: LibraryInsightsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EnhancedReadingTheme {
                LibraryInsightsScreen(
                    model = insightModel,
                    onBack = { finish() },
                    openSourceHealth = {
                        startActivity(Intent(this, SourceHealthActivity::class.java))
                    },
                )
            }
        }
    }
}

internal class LibraryInsightsViewModel : ViewModel() {
    private val database: DatabaseHelper = Injekt.get()
    private val _metrics = MutableStateFlow<LibraryInsightMetrics?>(null)
    val metrics = _metrics.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_loading.value) return
        _loading.value = true
        _loadError.value = null
        viewModelScope.launch {
            try {
                val newMetrics = withContext(Dispatchers.IO) {
                    val records = database.getLibraryMangas().executeAsBlocking()
                        .asSequence()
                        .filter { !it.isBlank() && !it.isHidden() }
                        .mapNotNull { manga ->
                            val id = manga.id ?: return@mapNotNull null
                            LibraryInsightEntry(
                                id = id,
                                sourceId = manga.source,
                                title = manga.title,
                                unread = manga.unread,
                                read = manga.read,
                                completed = manga.status == SManga.COMPLETED,
                            )
                        }.toList()
                    calculateLibraryInsights(records)
                }
                _metrics.value = newMetrics
            } catch (failure: Exception) {
                _loadError.value = failure.javaClass.simpleName
            } finally {
                _loading.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryInsightsScreen(
    model: LibraryInsightsViewModel,
    onBack: () -> Unit,
    openSourceHealth: () -> Unit,
) {
    val metrics by model.metrics.collectAsState()
    val loading by model.loading.collectAsState()
    val loadError by model.loadError.collectAsState()
    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.enhanced_library_insights), fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.close))
                }
            },
            actions = {
                IconButton(onClick = model::refresh, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.enhanced_refresh))
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )
    }) { insets ->
        val result = metrics
        val stats = if (result == null) emptyList() else listOf(
            R.string.enhanced_metric_titles to result.titles,
            R.string.enhanced_metric_unread to result.unreadChapters,
            R.string.enhanced_metric_read to result.readChapters,
            R.string.enhanced_metric_sources to result.sources,
            R.string.enhanced_metric_duplicates to result.likelyDuplicateTitles,
            R.string.enhanced_metric_completed to result.completedTitles,
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(145.dp),
            modifier = Modifier.padding(insets),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.enhanced_metrics_description), style = MaterialTheme.typography.bodyMedium)
                    if (loadError != null) {
                        Text(stringResource(R.string.enhanced_metrics_error, loadError.orEmpty()),
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = model::refresh, enabled = !loading) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.enhanced_refresh))
                        }
                        OutlinedButton(onClick = openSourceHealth) {
                            Icon(Icons.Default.WifiTethering, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.enhanced_source_health))
                        }
                    }
                }
            }
            items(stats) { (title, value) ->
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(value.toString(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(title), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
