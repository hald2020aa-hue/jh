package eu.kanade.tachiyomi.ui.reader.translation

import kotlin.math.max
import kotlin.math.min

/**
 * Conservative on-device speech-balloon candidate search on a bounded RGB thumbnail.
 * A candidate must be an enclosed, near-white component adjacent to the OCR text.
 * This is a pixel-based contour heuristic, not a trained speech-balloon detector.
 * When confidence is insufficient callers keep the original OCR text region.
 */
fun detectSpeechBalloonRegion(
    pixels: IntArray,
    width: Int,
    height: Int,
    glyph: NormalizedBubbleRect,
    others: List<NormalizedBubbleRect> = emptyList(),
): NormalizedBubbleRect? {
    if (width < 16 || height < 16 || width.toLong() * height != pixels.size.toLong()) return null
    val left = (glyph.left * width).toInt().coerceIn(0, width - 1)
    val top = (glyph.top * height).toInt().coerceIn(0, height - 1)
    val right = (glyph.right * width).toInt().coerceIn(left + 1, width)
    val bottom = (glyph.bottom * height).toInt().coerceIn(top + 1, height)
    val gw = right - left
    val gh = bottom - top
    val dx = max(10, gw * 2).coerceAtMost(max(24, width / 4))
    val dy = max(24, gh * 3).coerceAtMost(max(24, height / 3))
    val x0 = max(0, left - dx)
    val y0 = max(0, top - dy)
    val x1 = min(width, right + dx)
    val y1 = min(height, bottom + dy)
    val rw = x1 - x0
    val rh = y1 - y0
    if (rw < 8 || rh < 8) return null
    val areaLimit = min(120_000, rw * rh)
    val seeds = listOf(
        (left - 2) to (top - 2), (right + 1) to (top - 2),
        (left - 2) to (bottom + 1), (right + 1) to (bottom + 1),
        ((left + right) / 2) to (top - 2), ((left + right) / 2) to (bottom + 1),
        (left - 2) to ((top + bottom) / 2), (right + 1) to ((top + bottom) / 2),
    )
    val visited = BooleanArray(rw * rh)
    val stepOffsets = intArrayOf(-1, 1, -rw, rw)
    val queue = IntArray(min(areaLimit + 1, rw * rh))
    for ((sx, sy) in seeds) {
        if (sx !in (x0 + 1) until (x1 - 1) || sy !in (y0 + 1) until (y1 - 1)) continue
        val seedIndex = (sy - y0) * rw + sx - x0
        if (visited[seedIndex] || !isBrightBalloonPixel(pixels[sy * width + sx])) continue
        queue[0] = seedIndex
        visited[seedIndex] = true
        var head = 0
        var tail = 1
        var minX = sx
        var maxX = sx
        var minY = sy
        var maxY = sy
        var escaped = false
        while (head < tail && tail <= areaLimit) {
            val index = queue[head++]
            val cy = index / rw
            val cx = index % rw
            val ax = x0 + cx
            val ay = y0 + cy
            minX = min(minX, ax)
            maxX = max(maxX, ax)
            minY = min(minY, ay)
            maxY = max(maxY, ay)
            if (cx == 0 || cy == 0 || cx == rw - 1 || cy == rh - 1) {
                escaped = true
                break
            }
            for (step in stepOffsets) {
                val next = index + step
                if (visited[next]) continue
                visited[next] = true
                val nx = next % rw
                val ny = next / rw
                if (isBrightBalloonPixel(pixels[(y0 + ny) * width + x0 + nx])) {
                    if (tail >= queue.size) { escaped = true; break }
                    queue[tail++] = next
                }
            }
            if (escaped) break
        }
        if (escaped || tail < 32 || tail > areaLimit || minX >= maxX || minY >= maxY) continue
        // Reject a white margin/sky segment or an unrelated highlight next to the text.
        if (minX > left || minY > top || maxX < right - 1 || maxY < bottom - 1) continue
        if (maxX - minX > max(gw * 6, gw + 64) || maxY - minY > max(gh * 8, gh + 64)) continue
        val candidate = NormalizedBubbleRect(
            minX.toFloat() / width, minY.toFloat() / height,
            (maxX + 1).toFloat() / width, (maxY + 1).toFloat() / height,
        )
        // Never expand over a neighboring OCR glyph rectangle.
        if (others.any { candidate.left < it.right && candidate.right > it.left &&
                    candidate.top < it.bottom && candidate.bottom > it.top }) continue
        return candidate
    }
    return null
}

private fun isBrightBalloonPixel(argb: Int): Boolean {
    val r = argb ushr 16 and 255
    val g = argb ushr 8 and 255
    val b = argb and 255
    return min(r, min(g, b)) >= 212 && max(r, max(g, b)) - min(r, min(g, b)) <= 38
}

/** A cheap, explicit routing rule; English is not translated to English first. */
fun requiresEnglishPivot(source: SourceTextLanguage): Boolean = source != SourceTextLanguage.ENGLISH && source != SourceTextLanguage.AUTO

/** Deterministic script detection from OCR result, with an explicit ambiguity fallback. */
fun recognizeTextScript(text: String): SourceTextLanguage? {
    var hangul = 0
    var kana = 0
    var han = 0
    var latin = 0
    for (ch in text) {
        when (ch.code) {
            in 0xAC00..0xD7AF, in 0x1100..0x11FF -> hangul++
            in 0x3040..0x30FF, in 0x31F0..0x31FF -> kana++
            in 0x3400..0x9FFF -> han++
            in 0x0041..0x005A, in 0x0061..0x007A -> latin++
        }
    }
    return when {
        hangul >= 2 && hangul >= kana -> SourceTextLanguage.KOREAN
        kana >= 2 -> SourceTextLanguage.JAPANESE
        han >= 2 && kana == 0 && hangul == 0 -> SourceTextLanguage.CHINESE
        latin >= 3 && han == 0 && hangul == 0 && kana == 0 -> SourceTextLanguage.ENGLISH
        else -> null
    }
}
