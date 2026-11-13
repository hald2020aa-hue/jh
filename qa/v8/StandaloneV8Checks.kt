import eu.kanade.tachiyomi.ui.reader.translation.*

private fun check(label: String, condition: Boolean) {
    require(condition) { "FAILED: $label" }
    println("PASS: $label")
}

fun main() {
    val inBox = NormalizedBubbleRect(.18f,.24f,.52f,.42f)
    val expanded = expandedOcrTextRegion(inBox)
    check("grow left",expanded.left < inBox.left)
    check("grow right",expanded.right > inBox.right)
    check("grow up",expanded.top < inBox.top)
    check("grow down",expanded.bottom > inBox.bottom)
    check("stay in bounds", expanded.left >= 0 && expanded.right <= 1f)
    check("edge left",expandedOcrTextRegion(NormalizedBubbleRect(0f,0f,.4f,.2f)).left==0f)
    check("edge top",expandedOcrTextRegion(NormalizedBubbleRect(0f,0f,.4f,.2f)).top==0f)
    check("edge right",expandedOcrTextRegion(NormalizedBubbleRect(.7f,.8f,1f,1f)).right==1f)
    check("edge bottom",expandedOcrTextRegion(NormalizedBubbleRect(.7f,.8f,1f,1f)).bottom==1f)
    check("NaN bounds rejected",normalizedBubbleRect(Float.NaN,0f,.4f,.3f)==null)
    check("reversed bounds rejected",normalizedBubbleRect(.9f,.2f,.1f,.4f)==null)
    check("font floor",safeArabicOverlayFontScale(0f)==.75f)
    check("font ceiling",safeArabicOverlayFontScale(5f)==1.6f)
    check("font NaN",safeArabicOverlayFontScale(Float.NaN)==1f)
    check("opacity minimum",safeArabicOverlayOpacity(-1f)==.45f)
    check("opacity maximum",safeArabicOverlayOpacity(2f)==1f)
    check("opacity infinity",safeArabicOverlayOpacity(Float.POSITIVE_INFINITY)==.83f)
    check("7 pages",pagesAhead(10,30,7).toList()==(11..17).toList())
    check("end of chapter",pagesAhead(13,14,7).isEmpty())
    check("no duplicate priority indexes",translationPageOrder(14,4).distinct().size==14)
    val nearby = NormalizedBubbleRect(.31f,.22f,.50f,.33f)
    val current = NormalizedBubbleRect(.10f,.20f,.30f,.30f)
    check("adjacent text protection", safeOcrPlateRegion(current,listOf(nearby))==current)
    check("distant text margin", safeOcrPlateRegion(current,listOf(NormalizedBubbleRect(.7f,.7f,.9f,.9f)))!=current)
    check("retry only failed",failedPageRetryOrder(16,4,setOf(2,5))==listOf(5,2))
}
