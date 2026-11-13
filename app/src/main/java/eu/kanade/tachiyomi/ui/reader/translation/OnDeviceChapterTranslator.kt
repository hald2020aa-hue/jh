package eu.kanade.tachiyomi.ui.reader.translation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

/** Unlike the reader's bitmap cache, this stores only text and normalized OCR rectangles. */
enum class SourceTextLanguage(val tag: String, val arabicName: String) {
    AUTO("auto", "تلقائي — اكتشاف لغة النص"),
    ENGLISH(TranslateLanguage.ENGLISH, "الإنجليزية"),
    JAPANESE(TranslateLanguage.JAPANESE, "اليابانية"),
    KOREAN(TranslateLanguage.KOREAN, "الكورية"),
    CHINESE(TranslateLanguage.CHINESE, "الصينية"),
    ;

    fun recognizer(): TextRecognizer = when (this) {
        AUTO -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        ENGLISH -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
}

data class TranslatedTextBlock(
    val original: String,
    val arabic: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val bubbleDetected: Boolean = false,
)

data class TranslatedReaderPage(
    val pageIndex: Int,
    val blocks: List<TranslatedTextBlock>,
    val error: String? = null,
) {
    val text: String get() = blocks.joinToString("\n") { it.arabic }
}

data class ChapterTranslationProgress(
    val chapterId: Long? = null,
    val totalPages: Int = 0,
    val processedPages: Int = 0,
    val failedPages: Int = 0,
    val running: Boolean = false,
    val message: String = "",
    val translated: Map<Int, TranslatedReaderPage> = emptyMap(),
)

/**
 * V6 stage 1 translator: all pages are processed sequentially in a cancellable reader session.
 * Existing chapter files and page bitmaps are NEVER written back or replaced. Text only in RAM.
 * Translation is opt-in because the first use may download a language model and consume data.
 */
class OnDeviceChapterTranslator(context: Context) {
    private val cache = V9ChapterTranslationCache(context.applicationContext)
    private var activeLanguage: SourceTextLanguage = SourceTextLanguage.ENGLISH
    private val _progress = MutableStateFlow(ChapterTranslationProgress())
    val progress: StateFlow<ChapterTranslationProgress> = _progress.asStateFlow()

    suspend fun translateChapter(
        chapter: ReaderChapter,
        language: SourceTextLanguage,
        currentPageIndex: Int = 0,
        retryFailedOnly: Boolean = false,
    ) {
        val pages = chapter.pages ?: error("صفحات الفصل غير جاهزة")
        check(pages.isNotEmpty()) { "لا توجد صفحات للترجمة" }
        val previous = _progress.value.takeIf { retryFailedOnly && it.chapterId == chapter.chapter.id }
        val retryIndices = previous?.translated?.filterValues { it.error != null }?.keys.orEmpty()
        if (retryFailedOnly && retryIndices.isEmpty()) return
        _progress.value = (previous ?: ChapterTranslationProgress()).copy(
            chapterId = chapter.chapter.id, totalPages = pages.size, running = true,
            message = "جارٍ تحديد اللغة وتجهيز الترجمة…",
        )
        var recognizer: TextRecognizer? = null
        var intermediate: Translator? = null
        var toArabic: Translator? = null
        try {
            val source = if (language == SourceTextLanguage.AUTO) {
                detectLanguage(pages[currentPageIndex.coerceIn(0, pages.lastIndex)])
            } else language
            activeLanguage = source
            val disk = cache.read(chapter, source)
            val retained = previous?.translated?.filterValues { it.error == null }.orEmpty() + disk
            val ordered = if (retryFailedOnly) {
                failedPageRetryOrder(pages.size, currentPageIndex, retryIndices)
            } else {
                translationPageOrder(pages.size, currentPageIndex).filterNot { it in retained }
            }
            _progress.value = _progress.value.copy(
                translated = retained,
                processedPages = retained.size,
                failedPages = 0,
                message = "اللغة المكتشفة: ${source.arabicName}؛ استُعيدت ${retained.size} صفحة محفوظة",
            )
            if (ordered.isEmpty()) {
                _progress.value = _progress.value.copy(running = false, message = "الترجمة المحفوظة جاهزة")
                return
            }
            recognizer = source.recognizer()
            // Explicit two-hop translation requested by the user: ja/ko/zh -> en -> ar.
            if (requiresEnglishPivot(source)) {
                intermediate = makeTranslator(source.tag, TranslateLanguage.ENGLISH)
            }
            val translator = makeTranslator(TranslateLanguage.ENGLISH, TranslateLanguage.ARABIC)
            toArabic = translator
            val conditions = DownloadConditions.Builder().build()
            _progress.value = _progress.value.copy(message = "تنزيل نماذج اللغة عند الحاجة؛ قد يتطلب الاتصال بالإنترنت…")
            withTimeoutOrNull(180_000L) {
                intermediate?.downloadModelIfNeeded(conditions)?.awaitTask()
                translator.downloadModelIfNeeded(conditions).awaitTask()
                true
            } ?: error("انتهت مهلة تنزيل نموذج اللغة؛ تحقق من الإنترنت وأعد المحاولة")
            val reader = requireNotNull(recognizer)
            val arabicTranslator = translator
            val englishTranslator = intermediate
            for (pageIndex in ordered) {
                coroutineContext.ensureActive()
                val page = pages[pageIndex]
                _progress.value = _progress.value.copy(
                    message = "OCR ثم الترجمة العربية: الصفحة ${pageIndex + 1} من ${pages.size}",
                )
                val translated = try {
                    withTimeoutOrNull(120_000L) { translatePage(page, reader, englishTranslator, arabicTranslator) }
                        ?: TranslatedReaderPage(page.index, emptyList(), "انتهت مهلة OCR أو الترجمة للصفحة")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    TranslatedReaderPage(page.index, emptyList(), classifyTranslationError(e))
                }
                val current = _progress.value
                val results = current.translated + (page.index to translated)
                _progress.value = current.copy(
                    translated = results,
                    processedPages = results.size,
                    failedPages = results.values.count { it.error != null },
                    message = translated.error ?: "تمت ترجمة ${results.size} من ${pages.size} صفحة",
                )
                if (translated.error == null) {
                    // Cache errors must not stop reading or translation.
                    try { cache.write(chapter, source, results) } catch (_: Exception) { }
                }
            }
            val failures = _progress.value.failedPages
            _progress.value = _progress.value.copy(
                running = false,
                message = if (failures == 0) "اكتملت ترجمة الفصل" else "انتهت المعالجة؛ فشل $failures صفحات ويمكن إعادة المحاولة",
            )
        } catch (e: CancellationException) {
            _progress.value = _progress.value.copy(running = false, message = "توقفت الترجمة")
            throw e
        } catch (e: Throwable) {
            _progress.value = _progress.value.copy(running = false, message = classifyTranslationError(e))
        } finally {
            recognizer?.close()
            intermediate?.close()
            toArabic?.close()
        }
    }

    /** An explicit correction is saved into the bounded page-text cache. */
    suspend fun editTranslatedBlock(chapter: ReaderChapter, pageIndex: Int, blockIndex: Int, arabic: String) {
        val value = arabic.trim()
        require(value.isNotEmpty() && value.length <= 8_000) { "نص الترجمة غير صالح" }
        val current = _progress.value
        check(current.chapterId == chapter.chapter.id) { "انتقل القارئ إلى فصل آخر" }
        val page = current.translated[pageIndex] ?: error("الصفحة لم تُترجم بعد")
        check(blockIndex in page.blocks.indices) { "موضع الفقاعة غير صحيح" }
        val blocks = page.blocks.toMutableList()
        blocks[blockIndex] = blocks[blockIndex].copy(arabic = value)
        val updated = current.translated + (pageIndex to page.copy(blocks = blocks))
        _progress.value = current.copy(translated = updated)
        cache.write(chapter, activeLanguage, updated)
    }

    private fun makeTranslator(from: String, to: String): Translator = Translation.getClient(
        TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build(),
    )

    /** Try each bundled OCR script once on a 2048px-bounded visible page; never scan the entire chapter. */
    private suspend fun detectLanguage(page: ReaderPage): SourceTextLanguage {
        awaitPage(page)
        val bitmap = withContext(Dispatchers.IO) { decodeForOcr(page) }
        try {
            var best: SourceTextLanguage? = null
            var bestScore = 0
            val input = InputImage.fromBitmap(bitmap, 0)
            for (candidate in SourceTextLanguage.entries.filter { it != SourceTextLanguage.AUTO }) {
                coroutineContext.ensureActive()
                val recognizer = candidate.recognizer()
                try {
                    val output = withTimeout(20_000L) { recognizer.process(input).awaitTask() }
                    val detected = recognizeTextScript(output.text)
                    val score = if (detected == candidate) {
                        output.text.length.coerceAtMost(1_000) + 100
                    } else 0
                    if (score > bestScore) { best = candidate; bestScore = score }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // If an OCR language fails, continue trying the other bundled models.
                } finally {
                    recognizer.close()
                }
            }
            return best ?: error("تعذر تحديد لغة الفقاعات تلقائيًا؛ اختر اللغة يدويًا من القائمة")
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun translatePage(
        page: ReaderPage,
        recognizer: TextRecognizer,
        toEnglish: Translator?,
        toArabic: Translator,
    ): TranslatedReaderPage {
        awaitPage(page)
        val bitmap = withContext(Dispatchers.IO) { decodeForOcr(page) }
        try {
            val recognized = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitTask()
            val valid = recognized.textBlocks.mapNotNull { b ->
                val bounds = b.boundingBox ?: return@mapNotNull null
                val verticalCjk = (activeLanguage == SourceTextLanguage.JAPANESE ||
                    activeLanguage == SourceTextLanguage.CHINESE) &&
                    bounds.height() >= bounds.width() * 1.45f && b.lines.size > 1
                // Column order matters for Japanese vertical writing: right column first.
                val raw = if (verticalCjk) {
                    b.lines.sortedWith(compareByDescending<com.google.mlkit.vision.text.Text.Line> {
                        it.boundingBox?.centerX() ?: Int.MIN_VALUE
                    }.thenBy { it.boundingBox?.top ?: 0 }).joinToString("") { it.text.trim() }
                } else b.text.trim()
                if (raw.isEmpty()) return@mapNotNull null
                Triple(b, bounds, raw)
            }
            // A 640px thumbnail bounds the segmentation work, independent of huge Webtoon originals.
            val (sw, sh, pixels) = withContext(Dispatchers.Default) {
                val longest = max(bitmap.width, bitmap.height)
                val scale = min(1f, 640f / longest)
                val w = max(1, (bitmap.width * scale).toInt())
                val h = max(1, (bitmap.height * scale).toInt())
                val sample = if (w == bitmap.width && h == bitmap.height) bitmap
                    else Bitmap.createScaledBitmap(bitmap, w, h, true)
                val pixels = IntArray(w * h)
                try { sample.getPixels(pixels, 0, w, 0, 0, w, h) }
                finally { if (sample !== bitmap) sample.recycle() }
                Triple(w, h, pixels)
            }
            val glyphBoxes = valid.map { (_, rect, _) ->
                NormalizedBubbleRect(rect.left.toFloat() / bitmap.width, rect.top.toFloat() / bitmap.height,
                    rect.right.toFloat() / bitmap.width, rect.bottom.toFloat() / bitmap.height)
            }
            val blocks = mutableListOf<TranslatedTextBlock>()
            for ((index, data) in valid.withIndex()) {
                coroutineContext.ensureActive()
                val raw = data.third
                val english = if (toEnglish != null && recognizeTextScript(raw) != SourceTextLanguage.ENGLISH) {
                    toEnglish.translate(raw).awaitTask()
                } else raw
                val arabic = toArabic.translate(english).awaitTask()
                val box = glyphBoxes[index]
                val detected = withContext(Dispatchers.Default) {
                    detectSpeechBalloonRegion(pixels, sw, sh, box,
                        glyphBoxes.filterIndexed { i, _ -> i != index })
                }
                val bounds = detected ?: box
                blocks += TranslatedTextBlock(raw, arabic,
                    bounds.left, bounds.top, bounds.right, bounds.bottom,
                    bubbleDetected = detected != null)
            }
            return TranslatedReaderPage(page.index, blocks)
        } finally {
            bitmap.recycle()
        }
    }

    private fun classifyTranslationError(e: Throwable): String {
        val message = e.message.orEmpty()
        return when {
            e is kotlinx.coroutines.TimeoutCancellationException -> "انتهت المهلة: تحقق من الإنترنت أو أعد محاولة الصفحة"
            message.contains("model", ignoreCase = true) || message.contains("download", ignoreCase = true) ->
                "نموذج اللغة غير متاح أو تعذر تنزيله؛ تحقق من الإنترنت"
            message.contains("page", ignoreCase = true) || message.contains("صورة") ->
                "تعذر قراءة صورة الصفحة؛ حاول إعادة تنزيلها"
            message.isNotBlank() -> "خطأ محرك الترجمة: ${message.take(180)}"
            else -> "تعذرت الترجمة؛ جرّب إعادة المحاولة أو اختيار اللغة يدويًا"
        }
    }

    private suspend fun awaitPage(page: ReaderPage) = coroutineScope {
        if (page.status == Page.State.READY) return@coroutineScope
        val loader = page.chapter.pageLoader ?: error("تعذر الوصول إلى محرك صفحات الفصل")
        // PageLoader.loadPage may never return. Cancel only our subscription on status change;
        // the already ready page and shared reader cache are NOT deleted.
        val request = launch(Dispatchers.IO) { loader.loadPage(page) }
        try {
            val state = withTimeout(90_000L) {
                page.statusFlow.first { it == Page.State.READY || it == Page.State.ERROR }
            }
            check(state == Page.State.READY) { "تعذر تنزيل الصفحة ${page.number}" }
        } finally {
            request.cancelAndJoin()
        }
    }

    /** Decode at most ~2048 pixels on the longest edge, never a full-resolution webtoon in RAM. */
    private fun decodeForOcr(page: ReaderPage): Bitmap {
        val open = page.stream ?: error("لا تتوفر صورة الصفحة ${page.number}")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "صورة غير مدعومة" }
        val sample = generateSequence(1) { it * 2 }
            .first { max(bounds.outWidth, bounds.outHeight) / it <= 2048 }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return open().use { stream ->
            BitmapFactory.decodeStream(stream, null, options) ?: error("تعذر فك صورة الصفحة")
        }
    }
}

private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        when {
            task.isSuccessful -> continuation.resume(task.result)
            task.isCanceled -> continuation.cancel(CancellationException("أُلغيت المهمة"))
            else -> continuation.resumeWithException(task.exception ?: IllegalStateException("فشل محرك ML Kit"))
        }
    }
}
