package eu.kanade.tachiyomi.ui.reader.translation

import android.content.Context
import android.util.AtomicFile
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * A bounded, app-private, atomic on-device cache of OCR rectangles and Arabic text.
 * No manga images, translator models, or chapter files are modified.
 * This is a disposable cache; Android or the user may clear it.
 */
class V9ChapterTranslationCache(context: Context) {
    private val root = File(context.cacheDir, "jh_v9_arabic_page_text")

    private fun key(chapter: ReaderChapter, source: SourceTextLanguage): String {
        val pages = chapter.pages.orEmpty()
        val fingerprint = buildString {
            append("v9-contour-1|")
            append(chapter.chapter.id)
            append('|')
            append(source.tag)
            append('|')
            append(pages.size)
            pages.forEach { append('|'); append(it.url) }
        }
        return MessageDigest.getInstance("SHA-256").digest(fingerprint.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    suspend fun read(chapter: ReaderChapter, source: SourceTextLanguage): Map<Int, TranslatedReaderPage> = withContext(Dispatchers.IO) {
        val file = File(root, "${key(chapter, source)}.json")
        if (!file.isFile || file.length() !in 2..2_000_000) return@withContext emptyMap()
        runCatching {
            val content = AtomicFile(file).openRead().bufferedReader().use { it.readText() }
            val pages = JSONObject(content).getJSONArray("pages")
            buildMap {
                for (i in 0 until pages.length()) {
                    val item = pages.getJSONObject(i)
                    val index = item.getInt("index")
                    if (index !in chapter.pages.orEmpty().indices) continue
                    val blocks = item.getJSONArray("blocks")
                    val values = buildList {
                        for (j in 0 until blocks.length()) {
                            val b = blocks.getJSONObject(j)
                            val coords = normalizedBubbleRect(
                                b.getDouble("l").toFloat(), b.getDouble("t").toFloat(),
                                b.getDouble("r").toFloat(), b.getDouble("b").toFloat(),
                            ) ?: continue
                            add(TranslatedTextBlock(
                                original = b.getString("src").take(8_000),
                                arabic = b.getString("ar").take(8_000),
                                left = coords.left, top = coords.top,
                                right = coords.right, bottom = coords.bottom,
                                bubbleDetected = b.optBoolean("bubble", false),
                            ))
                        }
                    }
                    put(index, TranslatedReaderPage(index, values))
                }
            }
        }.getOrDefault(emptyMap())
    }

    suspend fun write(chapter: ReaderChapter, source: SourceTextLanguage, saved: Map<Int, TranslatedReaderPage>) = withContext(Dispatchers.IO) {
        if (saved.isEmpty()) return@withContext
        root.mkdirs()
        val file = AtomicFile(File(root, "${key(chapter, source)}.json"))
        val pages = JSONArray()
        saved.values.asSequence().filter { it.error == null }.sortedBy { it.pageIndex }.take(500).forEach { page ->
            val blocks = JSONArray()
            page.blocks.take(150).forEach { b ->
                blocks.put(JSONObject().apply {
                    put("src", b.original.take(8_000)); put("ar", b.arabic.take(8_000))
                    put("l", b.left.toDouble()); put("t", b.top.toDouble())
                    put("r", b.right.toDouble()); put("b", b.bottom.toDouble())
                    put("bubble", b.bubbleDetected)
                })
            }
            pages.put(JSONObject().put("index", page.pageIndex).put("blocks", blocks))
        }
        val bytes = JSONObject().put("pages", pages).toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > 2_000_000) return@withContext
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
        // Bounding by count and total size keeps thousands of manga chapters from filling storage.
        val ordered = root.listFiles()?.filter { it.isFile && it.extension == "json" }
            ?.sortedByDescending { it.lastModified() } ?: return@withContext
        var size = 0L
        ordered.forEachIndexed { i, entry ->
            size += entry.length()
            if (i >= 32 || size > 24L * 1024 * 1024) entry.delete()
        }
    }
}
