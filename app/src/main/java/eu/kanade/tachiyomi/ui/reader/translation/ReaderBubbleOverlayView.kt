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
 * A non-interactive, in-memory Arabic OCR overlay drawn *in the original image's coordinates*.
 * The source image, zoom state, gestures and chapter files are left untouched. The overlay is
 * hidden automatically when switching to the original image or the OCR result is unavailable.
 */
class ReaderBubbleOverlayView(
    context: Context,
    private val imageView: () -> View?,
) : View(context) {
    private var blocks: List<TranslatedTextBlock> = emptyList()
    private var enabled = false
    private var backgroundOpacity = 0.83f
    private var fontScale = 1f
    private val density = context.resources.displayMetrics.scaledDensity
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 22, 42, 68)
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density
    }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(13, 24, 37)
        isSubpixelText = true
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
    }

    fun update(
        translated: List<TranslatedTextBlock>?,
        show: Boolean,
        opacity: Float,
        scale: Float,
    ) {
        blocks = translated.orEmpty()
        enabled = show
        backgroundOpacity = opacity.coerceIn(0.45f, 1f)
        fontScale = scale.coerceIn(0.75f, 1.6f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!enabled || blocks.isEmpty()) return
        val image = imageView() ?: return
        fill.alpha = (255f * backgroundOpacity).toInt()
        for (block in blocks) {
            if (block.arabic.isBlank()) continue
            val box = getScreenRect(block, image) ?: continue
            if (box.width() < 15f || box.height() < 12f) continue
            val padding = min(6f * resources.displayMetrics.density, box.width() * 0.07f)
            val innerWidth = (box.width() - padding * 2).toInt().coerceAtLeast(1)
            val maxTextHeight = (box.height() - padding * 2).coerceAtLeast(1f)
            // Keep text proportional to the OCR bubble and downscale when it would overflow.
            var size = min(19f * density * fontScale, max(9f * density, box.height() / 3.3f))
            var layout: StaticLayout
            do {
                text.textSize = size
                layout = StaticLayout.Builder.obtain(block.arabic, 0, block.arabic.length, text, innerWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setTextDirection(TextDirectionHeuristics.RTL)
                    .setIncludePad(false)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setMaxLines(7)
                    .build()
                if (layout.height <= maxTextHeight || size <= 7f * density) break
                size *= 0.86f
            } while (true)
            val checkpoint = canvas.save()
            canvas.clipRect(box)
            canvas.drawRoundRect(box, 9f, 9f, fill)
            canvas.drawRoundRect(box, 9f, 9f, outline)
            val y = box.top + max(padding, (box.height() - layout.height) / 2f)
            canvas.translate(box.left + padding, y)
            layout.draw(canvas)
            canvas.restoreToCount(checkpoint)
        }
    }

    private fun getScreenRect(block: TranslatedTextBlock, image: View): RectF? {
        // OCR coordinates were normalized after a bounded decode and are resolution-independent.
        val normalized = normalizedBubbleRect(block.left, block.top, block.right, block.bottom) ?: return null
        val (l, t, r, b) = normalized
        val result = when (image) {
            is SubsamplingScaleImageView -> {
                if (!image.isReady || image.sWidth <= 0 || image.sHeight <= 0) return null
                val p0 = image.sourceToViewCoord(PointF(l * image.sWidth, t * image.sHeight)) ?: return null
                val p1 = image.sourceToViewCoord(PointF(r * image.sWidth, b * image.sHeight)) ?: return null
                RectF(p0.x, p0.y, p1.x, p1.y)
            }
            is ImageView -> {
                val drawable = image.drawable ?: return null
                val w = drawable.intrinsicWidth.toFloat()
                val h = drawable.intrinsicHeight.toFloat()
                if (w <= 0f || h <= 0f) return null
                RectF(l * w, t * h, r * w, b * h).apply { image.imageMatrix.mapRect(this) }
            }
            else -> return null
        }
        // Frame overlay and image view share a parent but not necessarily identical origins.
        result.offset(image.left.toFloat() - left, image.top.toFloat() - top)
        return result
    }
}
