package eu.kanade.tachiyomi.ui.reader.translation

import kotlin.math.max
import kotlin.math.min

/** Mirrors the pixel placement in ImageUtil.mergeBitmaps for a genuine two-page spread. */
data class V9SpreadPageSlot(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun project(block: TranslatedTextBlock): TranslatedTextBlock = block.copy(
        left = left + block.left * width,
        right = left + block.right * width,
        top = top + block.top * height,
        bottom = top + block.bottom * height,
    )
}

data class V9SpreadGeometry(val primary: V9SpreadPageSlot, val secondary: V9SpreadPageSlot) {
    companion object {
        fun create(w1: Int, h1: Int, w2: Int, h2: Int, isLTR: Boolean, hingePixels: Int): V9SpreadGeometry? {
            if (min(min(w1, h1), min(w2, h2)) <= 0 || hingePixels < 0) return null
            val maxH = max(h1, h2)
            val maxW = max(w1, w2)
            val outW = maxW.toLong() * 2 + hingePixels
            if (outW > Int.MAX_VALUE || outW <= 0) return null
            fun resizedWidth(w: Int, h: Int): Int = if (h != maxH && w != maxW) {
                val ratio = min(maxH.toFloat() / h, maxW.toFloat() / w)
                (w * ratio).toInt().coerceAtLeast(1)
            } else w
            fun resizedHeight(w: Int, h: Int): Int = if (h != maxH && w != maxW) {
                val ratio = min(maxH.toFloat() / h, maxW.toFloat() / w)
                (h * ratio).toInt().coerceAtLeast(1)
            } else h
            val aW = resizedWidth(w1, h1)
            val aH = resizedHeight(w1, h1)
            val bW = resizedWidth(w2, h2)
            val bH = resizedHeight(w2, h2)
            val rightStart = maxW + hingePixels
            fun slot(x: Int, w: Int, h: Int): V9SpreadPageSlot = V9SpreadPageSlot(
                x / outW.toFloat(), ((maxH - h) / 2) / maxH.toFloat(),
                w / outW.toFloat(), h / maxH.toFloat(),
            )
            return V9SpreadGeometry(
                primary = slot(if (isLTR) max(maxW - aW, 0) else rightStart, aW, aH),
                secondary = slot(if (!isLTR) max(maxW - bW, 0) else rightStart, bW, bH),
            )
        }
    }
}
