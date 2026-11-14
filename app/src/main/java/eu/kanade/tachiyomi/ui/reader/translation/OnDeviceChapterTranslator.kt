package eu.kanade.tachiyomi.ui.reader.translation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
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
            // A chapter can begin with a cover, credits or a wordless panel. Do not
            // mark auto-translation as broken just because the first page has no text.
            val source = if (language == SourceTextLanguage.AUTO) {
                detectLanguageFromVisiblePages(pages, currentPageIndex)
            } else language
            activeLanguage = source
            val disk = cache.read(chapter, source).filterValues { hasReadableTranslation(it) }
            // V9 could cache an empty OCR page as a successful translation. Do not
            // reuse those entries: the user must be able to retry the actual text.
            val retained = previous?.translated?.filterValues { hasReadableTranslation(it) }.orEmpty() + disk
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
                if (hasReadableTranslation(translated)) {
                    // Cache only real translated text; never cache a wordless/failed
                    // OCR result as if the Arabic overlay were already complete.
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

    /** Probe a bounded set of nearby pages instead of treating a text-free cover
     * as a permanent failure of the entire manhwa. Never scan the full chapter. */
    private suspend fun detectLanguageFromVisiblePages(
        pages: List<ReaderPage>, currentPageIndex: Int,
    ): SourceTextLanguage {
        var lastError: Throwable? = null
        for (index in languageDetectionSampleOrder(pages.size, currentPageIndex)) {
            coroutineContext.ensureActive()
            try {
                return detectLanguage(pages[index])
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
            }
        }
        val detail = lastError?.message?.take(100)?.let { " ($it)" }.orEmpty()
        error("لم يُعثر على لغة واضحة في الصفحات القريبة$detail؛ اختر لغة النص يدويًا")
    }

    /** Detect text using a bounded number of OCR tiles, so extra-tall Webtoon
     * pages are never reduced to unreadable thumbnail-width strips. */
    private suspend fun detectLanguage(page: ReaderPage): SourceTextLanguage {
        var best: SourceTextLanguage? = null
        var bestScore = 0
        scanOcrFrames(page, maxFrames = 3) { bitmap, _ ->
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
                    // A missing OCR language model must not abort the other scripts.
                } finally {
                    recognizer.close()
                }
            }
            // Enough readable text to choose a model; skip unnecessary OCR tiles.
            bestScore < 120
        }
        return best ?: error("تعذر تحديد لغة النص في هذه الصفحة")
    }

    private suspend fun translatePage(
        page: ReaderPage,
        recognizer: TextRecognizer,
        toEnglish: Translator?,
        toArabic: Translator,
    ): TranslatedReaderPage {
        val blocks = mutableListOf<TranslatedTextBlock>()
        scanOcrFrames(page) { bitmap, slice ->
            val recognized = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitTask()
            val valid = recognized.textBlocks.mapNotNull { b ->
                val bounds = b.boundingBox ?: return@mapNotNull null
                val verticalCjk = (activeLanguage == SourceTextLanguage.JAPANESE ||
                    activeLanguage == SourceTextLanguage.CHINESE) &&
                    bounds.height() >= bounds.width() * 1.45f && b.lines.size > 1
                val raw = if (verticalCjk) {
                    b.lines.sortedWith(compareByDescending<com.google.mlkit.vision.text.Text.Line> {
                        it.boundingBox?.centerX() ?: Int.MIN_VALUE
                    }.thenBy { it.boundingBox?.top ?: 0 }).joinToString("") { it.text.trim() }
                } else b.text.trim()
                if (raw.isEmpty()) return@mapNotNull null
                Triple(b, bounds, raw)
            }
            if (valid.isEmpty()) return@scanOcrFrames true
            // Segmentation is bounded to a 640px thumbnail of THIS tile, not
            // a severely downsampled full webtoon strip.
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
            for ((index, data) in valid.withIndex()) {
                coroutineContext.ensureActive()
                val rect = data.second
                // Adjacent tiles overlap; each OCR block is owned by exactly
                // one tile. Otherwise the Arabic overlay would duplicate it.
                val blockCenterSourceY = slice.start + rect.centerY().toFloat() / bitmap.height * slice.height
                if (blockCenterSourceY < slice.keepStart || blockCenterSourceY >= slice.keepEnd) continue
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
                val tileRect = detected ?: box
                val fullRect = ocrTileToPageRect(
                    tileRect.left, tileRect.top, tileRect.right, tileRect.bottom,
                    slice,
                )
                blocks += TranslatedTextBlock(
                    raw, arabic, fullRect.left, fullRect.top, fullRect.right, fullRect.bottom,
                    bubbleDetected = detected != null,
                )
            }
            true
        }
        return if (blocks.isEmpty()) {
            TranslatedReaderPage(page.index, emptyList(),
                "لم يتعرف OCR على نص واضح في الصفحة؛ جرّب اللغة يدويًا أو أعد المحاولة")
        } else {
            TranslatedReaderPage(page.index, blocks)
        }
    }

    /** Stream at most one decoded tile at a time. All source files stay read-only.
     * The callback returns false to finish after adequate language detection. */
    @Suppress("DEPRECATION")
    private suspend fun scanOcrFrames(
        page: ReaderPage,
        maxFrames: Int = Int.MAX_VALUE,
        process: suspend (Bitmap, OcrTileSlice) -> Boolean,
    ) {
        awaitPage(page)
        val open = page.stream ?: error("لا تتوفر صورة الصفحة ${page.number}")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        withContext(Dispatchers.IO) { open().use { BitmapFactory.decodeStream(it, null, bounds) } }
        check(bounds.outWidth > 0 && bounds.outHeight > 0) { "صورة غير مدعومة" }
        val wide = bounds.outWidth
        val tall = bounds.outHeight
        val isLongStrip = tall > max(3600, wide * 3)
        if (isLongStrip) {
            val decoderAndStream = withContext(Dispatchers.IO) {
                val stream = open()
                try {
                    val decoder = requireNotNull(BitmapRegionDecoder.newInstance(stream, false)) {
                        "صيغة الصورة لا تدعم القراءة الجزئية"
                    }
                    Pair(decoder, stream)
                } catch (e: Throwable) {
                    stream.close()
                    // Unsupported image formats use the safe bitmap fallback below.
                    null
                }
            }
            if (decoderAndStream != null) {
                val (decoder, stream) = decoderAndStream
                val sampleSize = generateSequence(1) { it * 2 }.first { wide / it <= 1400 }
                val slices = ocrTileSlices(wide, tall, sampleSize)
                try {
                    for (slice in slices.take(maxFrames)) {
                        coroutineContext.ensureActive()
                        val bitmap = withContext(Dispatchers.IO) {
                            decoder.decodeRegion(Rect(0, slice.start, wide, slice.end),
                                BitmapFactory.Options().apply { inSampleSize = sampleSize })
                        } ?: error("تعذر قراءة جزء من صفحة المانهوا")
                        try { if (!process(bitmap, slice)) break }
                        finally { bitmap.recycle() }
                    }
                } finally {
                    // Cleanup must run even after the user cancels translation;
                    // withContext(IO) would immediately rethrow cancellation.
                    decoder.recycle()
                    stream.close()
                }
                return
            }
        }
        // Normal pages and image formats without region decoding use V9's
        // conservative, memory-bounded path.
        val bitmap = withContext(Dispatchers.IO) { decodeForOcr(page) }
        try { process(bitmap, OcrTileSlice(0, tall, 0, tall, wide, tall)) }
        finally { bitmap.recycle() }
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
