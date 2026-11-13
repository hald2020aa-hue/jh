#!/usr/bin/env python3
"""Static contract checks for the V8 in-page Arabic bubble overlay; no claims of runtime visual QA."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]

def read(path):
    return (ROOT / path).read_text(encoding='utf-8')

def test(label, okay):
    if not okay: raise AssertionError(label)
    print('PASS', label)

base = 'app/src/main/java/eu/kanade/tachiyomi/ui/reader/'
reader = read(base+'ReaderActivity.kt')
overlay = read(base+'translation/ReaderBubbleOverlayView.kt')
image = read(base+'viewer/ReaderPageImageView.kt')
engine = read(base+'translation/OnDeviceChapterTranslator.kt')
policy = read(base+'translation/ReaderV7Policy.kt')
pager = read(base+'viewer/pager/PagerPageHolder.kt')
webtoon = read(base+'viewer/webtoon/WebtoonPageHolder.kt')

for label,marker in [
    ('OCR boxes normalized', 'normalizedBubbleRect('),
    ('safe OCR margins', 'safeOcrPlateRegion('),
    ('subsampling zoom geometry', 'sourceToViewCoord('),
    ('animated image geometry', 'image.imageMatrix.mapRect(this)'),
    ('Arabic direction', 'TextDirectionHeuristics.RTL'),
    ('word wrap with StaticLayout', 'StaticLayout.Builder.obtain'),
    ('non-interactive overlay', 'isClickable = false'),
    ('font autoshrink', 'size *= 0.88f'),
    ('oversized text ellipsis fallback', 'TextUtils.TruncateAt.END'),
    ('image clip', 'canvas.clipRect(box)'),
    ('Arabic text in the bubble', 'layout.draw(canvas)'),
    ('original source never redrawn by OCR', 'canvas.drawRoundRect(box')]:
    test(label, marker in overlay)
for label,marker in [
    ('status banner moved from bottom', 'Gravity.TOP or Gravity.END'),
    ('success banner hidden', 'translated.blocks.isNotEmpty()'),
    ('toggle original/Arabic', 'R.id.action_translation_toggle'),
    ('font size seekbar', 'fontBar = android.widget.SeekBar'),
    ('opacity seekbar', 'opacityBar = android.widget.SeekBar'),
    ('failed page retry', 'retryFailedTranslations()'),
    ('view retarget when reader changes', 'refreshTranslationOverlays()'),
    ('manual choice of four sources', 'SourceTextLanguage.entries.toTypedArray()')]:
    test(label, marker in reader)
for label,marker in [
    ('pan follows PhotoView', 'setOnViewDragListener'),
    ('holder keeps page identity', 'setTranslationIdentity('),
    ('live zoom overlay invalidation', 'translationOverlay?.invalidate()'),
    ('page image not replaced by OCR', 'showTranslation(')]:
    test(label, marker in image)
for label,marker in [
    ('ML Kit recognizer English', 'TextRecognizerOptions.DEFAULT_OPTIONS'),
    ('ML Kit recognizer Japanese', 'JapaneseTextRecognizerOptions.Builder()'),
    ('ML Kit recognizer Korean', 'KoreanTextRecognizerOptions.Builder()'),
    ('ML Kit recognizer Chinese', 'ChineseTextRecognizerOptions.Builder()'),
    ('target Arabic', 'TranslateLanguage.ARABIC'),
    ('model timeout', 'withTimeoutOrNull(180_000L)'),
    ('per page timeout', 'withTimeoutOrNull(120_000L)'),
    ('error status', 'تعذرت تهيئة الترجمة'),
    ('per page OCR blocks', 'recognized.textBlocks'),
    ('rectangle normalization', 'bounds.left.toFloat() / bitmap.width'),
    ('cancellable job', 'coroutineContext.ensureActive()')]:
    test(label,marker in engine)
for label,marker in [('page bounds','expandedOcrTextRegion('),('neighbor protection','safeOcrPlateRegion('),('safe opacity','safeArabicOverlayOpacity'),('safe font','safeArabicOverlayFontScale')]:
    test(label,marker in policy)
test('webtoon overlay wired', 'decorateTranslationImage(frame, page)' in webtoon)
test('paged overlay wired', 'decorateTranslationImage(this, page' in pager)
test('image files not rewritten by translation', all(x not in engine for x in ['openOutputStream(', 'deleteChapter(', 'replaceChapter(', 'writeBytes(']))
test('single arm64 APK target still enforced', 'verifySingleArm64DevApk' in read('.github/workflows/quality_gate.yml'))
print('V8 STATIC CHECKS PASS (NOT device testing)')
