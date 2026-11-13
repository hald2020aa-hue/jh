package eu.kanade.tachiyomi.ui.reader.translation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.widget.ImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlin.math.max
import kotlin.math.min

/**
 * Arabic text is drawn in a transparent, non-interactive overlay over the actual page image.
 * OCR boxes are projected through the image view's current zoom/pan transformation, and
 * the actual manga bitmap/cache is never written or modified.
 */
class ReaderBubbleOverlayView(
    context: Context,
    private val imageView: () -> View?,
) : View(context) {
    private var blocks: List<TranslatedTextBlock> = emptyList()
    private var enabled = false
    private var backgroundOpacity = 0.83f
    private var fontScale = 1f
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val screenDensity = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(135, 25, 45, 65)
        style = Paint.Style.STROKE
        strokeWidth = screenDensity
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(15, 24, 35)
        isSubpixelText = true
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
    }

    fun update(translated: List<TranslatedTextBlock>?, show: Boolean, opacity: Float, scale: Float) {
        blocks = translated.orEmpty()
        enabled = show && blocks.isNotEmpty()
        backgroundOpacity = safeArabicOverlayOpacity(opacity)
        fontScale = safeArabicOverlayFontScale(scale)
        visibility = if (enabled) VISIBLE else GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!enabled || blocks.isEmpty()) return
        val image = imageView() ?: return
        fill.alpha = (255f * backgroundOpacity).toInt().coerceIn(0, 255)
        for (block in blocks) {
            if (block.arabic.isBlank()) continue
            val original = getScreenRect(block, image) ?: continue
            // OCR detects glyph bounds, not bubble contours. Pad conservatively rather than
            // covering an entire speech balloon (which may overlap drawings or another box).
            val box = RectF(original)
            if (box.width() < 10f || box.height() < 9f) continue
            val padding = min(7f * screenDensity, box.width() * 0.07f)
            val innerWidth = (box.width() - 2f * padding).toInt().coerceAtLeast(1)
            val innerHeight = (box.height() - 2f * padding).toInt().coerceAtLeast(1)
            val layout = fitArabic(block.arabic, innerWidth, innerHeight, box.height())
            val radius = min(12f * screenDensity, box.height() / 3f)
            val checkpoint = canvas.save()
            canvas.clipRect(box)
            canvas.drawRoundRect(box, radius, radius, fill)
            canvas.drawRoundRect(box, radius, radius, outline)
            // The text stays at the OCR position even when the user zooms and pans the page.
            val y = box.top + max(padding, (box.height() - layout.height) / 2f)
            canvas.translate(box.left + padding, y)
            layout.draw(canvas)
            canvas.restoreToCount(checkpoint)
        }
    }

    /** Find the largest readable size that fits. Ellipsis is a last resort; full text stays
     * available from the existing "ترجمة الصفحة الحالية" action, never from changed artwork. */
    private fun fitArabic(arabic: String, width: Int, height: Int, boxHeight: Float): StaticLayout {
        val minimumSize = 7.5f * scaledDensity
        var size = min(29f * scaledDensity * fontScale, max(minimumSize, boxHeight / 2.8f))
        while (size >= minimumSize) {
            textPaint.textSize = size
            val layout = StaticLayout.Builder.obtain(arabic, 0, arabic.length, textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(false)
                .setMaxLines(40)
                .build()
            if (layout.height <= height) return layout
            size *= 0.88f
        }
        textPaint.textSize = minimumSize
        val allowedLines = (height / textPaint.fontSpacing).toInt().coerceAtLeast(1)
        return StaticLayout.Builder.obtain(arabic, 0, arabic.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .setMaxLines(allowedLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }

    private fun getScreenRect(block: TranslatedTextBlock, image: View): RectF? {
        val normalized = normalizedBubbleRect(block.left, block.top, block.right, block.bottom)
            ?: return null
        // Keep a nearby speech balloon's OCR glyphs completely unobscured.
        val neighbors = blocks.asSequence().filter { it !== block }.mapNotNull {
            normalizedBubbleRect(it.left, it.top, it.right, it.bottom)
        }.toList()
        val region = safeOcrPlateRegion(normalized, neighbors)
        val result = when (image) {
            is SubsamplingScaleImageView -> {
                if (!image.isReady || image.sWidth <= 0 || image.sHeight <= 0) return null
                val a = image.sourceToViewCoord(PointF(region.left * image.sWidth, region.top * image.sHeight))
                    ?: return null
                val b = image.sourceToViewCoord(PointF(region.right * image.sWidth, region.bottom * image.sHeight))
                    ?: return null
                RectF(a.x, a.y, b.x, b.y)
            }
            is ImageView -> {
                val drawable = image.drawable ?: return null
                val w = drawable.intrinsicWidth.toFloat()
                val h = drawable.intrinsicHeight.toFloat()
                if (w <= 0f || h <= 0f) return null
                RectF(region.left * w, region.top * h, region.right * w, region.bottom * h).apply {
                    image.imageMatrix.mapRect(this)
                }
            }
            else -> return null
        }
        // Both views share the ReaderPageImageView frame, but their bounds can differ in webtoon.
        result.offset(image.left.toFloat() - left, image.top.toFloat() - top)
        if (!result.left.isFinite() || !result.right.isFinite() || result.width() <= 0f || result.height() <= 0f) return null
        if (!RectF.intersects(result, RectF(0f, 0f, width.toFloat(), height.toFloat()))) return null
        return result
    }
}
