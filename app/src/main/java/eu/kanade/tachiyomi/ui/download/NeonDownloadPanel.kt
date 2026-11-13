package eu.kanade.tachiyomi.ui.download

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.tachiyomi.data.download.model.Download
import java.util.Locale
import eu.kanade.tachiyomi.ui.enhanced.neonChapterPaletteIndex
import eu.kanade.tachiyomi.ui.enhanced.estimatedTransferSeconds

/** Read-only snapshot of queue state. Transfer bytes are observed separately via StateFlow. */
data class NeonDownloadRow(
    val download: Download,
    val name: String,
    val chapterName: String,
    val percentage: Int,
    val downloadedPages: Int,
    val totalPages: Int?,
    val paused: Boolean,
    val status: Download.State,
    val completedInCache: Int,
)

private val background = Color(0xFF061021)
private val panel = Color(0xFF0D1D35)
private val foreground = Color(0xFFEFF7FF)
private val muted = Color(0xFFB3C4D9)
private val cyan = Color(0xFF14DAF1)
private val pink = Color(0xFFED1B91)
private val palettes = listOf(
    listOf(Color(0xFF04D5E6), Color(0xFF2275FC), Color(0xFF983BEC), Color(0xFFF11DBF)),
    listOf(Color(0xFFFF4265), Color(0xFFE11DA6), Color(0xFF9655F2)),
    listOf(Color(0xFFFF843A), Color(0xFFFFC741), Color(0xFFFF4B9B)),
    listOf(Color(0xFFFFDA51), Color(0xFFF3B229), Color(0xFFFF7633)),
    listOf(Color(0xFF13DFF3), Color(0xFF04A9D8), Color(0xFF3466FF)),
    listOf(Color(0xFFFFF463), Color(0xFFEACA2F), Color(0xFFED9E24)),
    listOf(Color(0xFF34ED98), Color(0xFF19CACA), Color(0xFF268DE6)),
)

private fun colorsFor(row: NeonDownloadRow): List<Color> =
    palettes[neonChapterPaletteIndex(row.download.chapter.chapter_number, palettes.size)]

private fun bytesString(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

@Composable
private fun NeonProgressTrack(value: Float, colors: List<Color>, modifier: Modifier = Modifier) {
    val fraction = value.coerceIn(0f, 1f)
    Box(
        modifier = modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF263650)),
    ) {
        Box(
            Modifier.fillMaxWidth(fraction).height(14.dp)
                .background(Brush.horizontalGradient(colors), RoundedCornerShape(20.dp)),
        )
    }
}

/**
 * Dev-only Compose download center. Uses real queue items and byte telemetry; it never invents
 * sizes, speed or ETA. Existing download database, cache and WorkManager remain untouched.
 */
@Composable
fun NeonDownloadPanel(
    rows: List<NeonDownloadRow>,
    isRunning: Boolean,
    onToggleAll: () -> Unit,
    onDeleteAll: () -> Unit,
    onPause: (Download) -> Unit,
    onResume: (Download) -> Unit,
    onDelete: (Download) -> Unit,
    onPrioritize: (Download) -> Unit,
    onConcurrentSources: (Int) -> Unit,
    onClose: (() -> Unit)? = null,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    var showDeleteAll by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(background).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("قائمة التنزيل", color = foreground, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.Download, contentDescription = null, tint = cyan)
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = foreground)
                }
            }
        }
        Text("${rows.size} فصلًا في قائمة التنزيل", color = muted, fontSize = 12.sp)
        if (rows.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("لا توجد فصول قيد التنزيل", color = muted)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val grouped = rows.groupBy { it.download.manga.id }
                grouped.forEach { (_, chapters) ->
                    val current = chapters.firstOrNull { it.status == Download.State.DOWNLOADING }
                        ?: chapters.firstOrNull { !it.paused } ?: chapters.first()
                    item(key = "banner_${chapters.first().download.manga.id}") {
                        MangaNeonDownloadCard(
                            current = current,
                            completed = chapters.first().completedInCache,
                            queued = chapters.size,
                            onPause = { onPause(current.download) },
                            onResume = { onResume(current.download) },
                        )
                    }
                    item(key = "label_${chapters.first().download.manga.id}") {
                        Text("الفصول — ${chapters.first().name}", color = foreground, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 3.dp))
                    }
                    items(chapters, key = { it.download.chapter.id ?: it.hashCode() }) { row ->
                        NeonChapterDownloadRow(row, onPause, onResume, onDelete, onPrioritize)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onToggleAll,
                colors = ButtonDefaults.buttonColors(containerColor = pink),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (isRunning) "إيقاف مؤقت" else "استئناف")
            }
            IconButton(onClick = { showDeleteAll = true }) {
                Icon(Icons.Default.Delete, contentDescription = "إلغاء التنزيلات", tint = pink)
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = "إعدادات التنزيل", tint = cyan)
            }
        }
    }
    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text("إلغاء قائمة التنزيل؟") },
            text = { Text("سيُلغى طابور التنزيل. لا تُحذف الفصول التي اكتمل تنزيلها.") },
            confirmButton = { TextButton(onClick = { showDeleteAll = false; onDeleteAll() }) { Text("إلغاء القائمة") } },
            dismissButton = { TextButton(onClick = { showDeleteAll = false }) { Text("رجوع") } },
        )
    }
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("إعدادات التنزيل") },
            text = {
                Column {
                    Text("عدد المصادر التي تنزل بالتزامن:")
                    listOf(1, 2, 3).forEach { number ->
                        TextButton(onClick = { onConcurrentSources(number); showSettings = false }) {
                            Text("$number ${if (number == 1) "مصدر" else "مصادر"}")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("إغلاق") } },
        )
    }
    }
}

@Composable
private fun MangaNeonDownloadCard(
    current: NeonDownloadRow,
    completed: Int,
    queued: Int,
    onPause: () -> Unit,
    onResume: () -> Unit,
) {
    val stats by current.download.transferStats.collectAsState()
    val colors = colorsFor(current)
    Card(
        border = BorderStroke(1.5.dp, Brush.horizontalGradient(colors)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = panel),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(current.name, color = foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (current.paused) "متوقف" else "جاري التنزيل", color = colors.last(), fontSize = 12.sp)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$completed / ${completed + queued} فصول", color = cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text("${current.percentage}%", color = foreground, fontWeight = FontWeight.Bold, fontSize = 23.sp)
            }
            NeonProgressTrack(current.percentage / 100f, colors)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${current.downloadedPages} / ${current.totalPages?.toString() ?: "؟"} صفحات", color = muted, fontSize = 11.sp)
                Text("${bytesString(stats.receivedBytes)} / ${stats.knownChapterBytes?.let(::bytesString) ?: "غير معروف"}", color = muted, fontSize = 11.sp)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${bytesString(stats.speedBytesPerSecond)}/s · المتبقي: ${estimatedTransferSeconds(stats.knownChapterBytes, stats.receivedBytes, stats.speedBytesPerSecond)
                        ?.let { "$it ث" } ?: "غير معروف"}", color = muted, fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = if (current.paused || current.status != Download.State.DOWNLOADING) onResume else onPause) {
                    Icon(if (current.paused || current.status != Download.State.DOWNLOADING) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (current.paused || current.status != Download.State.DOWNLOADING) "استئناف الفصل" else "إيقاف الفصل", tint = cyan)
                }
            }
        }
    }
}

@Composable
private fun NeonChapterDownloadRow(
    row: NeonDownloadRow,
    onPause: (Download) -> Unit,
    onResume: (Download) -> Unit,
    onDelete: (Download) -> Unit,
    onPrioritize: (Download) -> Unit,
) {
    val stats by row.download.transferStats.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val colors = if (row.status == Download.State.DOWNLOADING && !row.paused) colorsFor(row)
        else listOf(Color(0xFF4C627E), Color(0xFF758AA5))
    Card(
        shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, if (row.status == Download.State.DOWNLOADING && !row.paused) colors.last() else Color(0xFF2C476A)),
        colors = CardDefaults.cardColors(containerColor = panel),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(51.dp)) {
                CircularProgressIndicator(
                    progress = { row.percentage / 100f },
                    modifier = Modifier.size(46.dp),
                    color = colors.last(),
                    trackColor = Color(0xFF34435B),
                    strokeWidth = 5.dp,
                )
                Text("${row.percentage}%", fontSize = 11.sp, color = foreground, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(row.chapterName, fontSize = 14.sp, color = foreground, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${row.downloadedPages}/${row.totalPages?.toString() ?: "؟"} صفحة · ${stats.knownChapterBytes?.let(::bytesString) ?: "الحجم غير معروف"}",
                    fontSize = 11.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { if (row.paused || row.status != Download.State.DOWNLOADING) onResume(row.download) else onPause(row.download) }) {
                Icon(if (row.paused || row.status != Download.State.DOWNLOADING) Icons.Default.Download else Icons.Default.Pause,
                    contentDescription = "إيقاف أو استئناف الفصل", tint = cyan)
            }
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "خيارات الفصل", tint = foreground)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("نقل إلى الأعلى") }, onClick = { expanded = false; onPrioritize(row.download) })
                    DropdownMenuItem(text = { Text("إلغاء تنزيل الفصل") }, onClick = { expanded = false; confirmDelete = true })
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("إلغاء تنزيل الفصل؟") },
            text = { Text(row.chapterName) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(row.download) }) { Text("إلغاء التنزيل") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("رجوع") } },
        )
    }
}
