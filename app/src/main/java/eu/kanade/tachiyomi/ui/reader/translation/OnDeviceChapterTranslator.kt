package eu.kanade.tachiyomi.ui.reader.translation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.translate.DownloadConditions
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
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

/** Unlike the reader's bitmap cache, this stores only text and normalized OCR rectangles. */
enum class SourceTextLanguage(val tag: String, val arabicName: String) {
    ENGLISH(TranslateLanguage.ENGLISH, "الإنجليزية"),
    JAPANESE(TranslateLanguage.JAPANESE, "اليابانية"),
    KOREAN(TranslateLanguage.KOREAN, "الكورية"),
    CHINESE(TranslateLanguage.CHINESE, "الصينية"),
    ;

    fun recognizer(): TextRecognizer = when (this) {
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
class OnDeviceChapterTranslator {
    private val _progress = MutableStateFlow(ChapterTranslationProgress())
    val progress: StateFlow<ChapterTranslationProgress> = _progress.asStateFlow()

    suspend fun translateChapter(chapter: ReaderChapter, language: SourceTextLanguage, currentPageIndex: Int = 0) {
        val pages = chapter.pages ?: error("صفحات الفصل غير جاهزة")
        check(pages.isNotEmpty()) { "لا توجد صفحات للترجمة" }
        _progress.value = ChapterTranslationProgress(
            chapterId = chapter.chapter.id,
            totalPages = pages.size,
            running = true,
            message = "تجهيز نموذج الترجمة العربية…",
        )
        val recognizer = language.recognizer()
        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(language.tag)
                .setTargetLanguage(TranslateLanguage.ARABIC)
                .build(),
        )
        try {
            // The user explicitly requested translation. Model downloads may require a connection.
            // We do not call translate() until ML Kit confirms the model is ready.
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).awaitTask()
            _progress.value = _progress.value.copy(message = "جارٍ ترجمة صفحات الفصل…")
            for (pageIndex in translationPageOrder(pages.size, currentPageIndex)) {
                val page = pages[pageIndex]
                coroutineContext.ensureActive()
                val result = try {
                    translatePage(page, recognizer, translator)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    TranslatedReaderPage(page.index, emptyList(), e.message ?: "تعذرت ترجمة الصفحة")
                }
                val current = _progress.value
                _progress.value = current.copy(
                    processedPages = current.processedPages + 1,
                    failedPages = current.failedPages + if (result.error != null) 1 else 0,
                    translated = current.translated + (page.index to result),
                )
            }
            val failures = _progress.value.failedPages
            _progress.value = _progress.value.copy(
                running = false,
                message = if (failures == 0) "اكتملت ترجمة الفصل" else "اكتملت المعالجة مع $failures صفحات غير مترجمة",
            )
        } catch (e: CancellationException) {
            _progress.value = _progress.value.copy(running = false, message = "توقفت الترجمة")
            throw e
        } catch (e: Throwable) {
            _progress.value = _progress.value.copy(running = false, message = e.message ?: "تعذرت الترجمة")
            throw e
        } finally {
            recognizer.close()
            translator.close()
        }
    }

    private suspend fun translatePage(
        page: ReaderPage,
        recognizer: TextRecognizer,
        translator: Translator,
    ): TranslatedReaderPage {
        awaitPage(page)
        val bitmap = withContext(Dispatchers.IO) { decodeForOcr(page) }
        try {
            val recognized = recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitTask()
            val blocks = mutableListOf<TranslatedTextBlock>()
            // Translate each OCR block to keep its geometry; do not cap text or omit later pages.
            for (block in recognized.textBlocks) {
                coroutineContext.ensureActive()
                val raw = block.text.trim()
                if (raw.isEmpty()) continue
                val bounds = block.boundingBox ?: continue
                val arabic = translator.translate(raw).awaitTask()
                blocks += TranslatedTextBlock(
                    original = raw,
                    arabic = arabic,
                    left = bounds.left.toFloat() / bitmap.width,
                    top = bounds.top.toFloat() / bitmap.height,
                    right = bounds.right.toFloat() / bitmap.width,
                    bottom = bounds.bottom.toFloat() / bitmap.height,
                )
            }
            return TranslatedReaderPage(page.index, blocks)
        } finally {
            bitmap.recycle()
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
