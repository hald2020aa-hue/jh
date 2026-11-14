#!/usr/bin/env python3
"""V12 static invariants only; Android build & real translation must be verified separately."""
from pathlib import Path
from xml.etree import ElementTree as ET
root = Path(__file__).resolve().parents[2]
kt = root / 'app/src/main/java/eu/kanade/tachiyomi'
engine = (kt/'ui/reader/translation/OnDeviceChapterTranslator.kt').read_text()
policy = (kt/'ui/reader/translation/ReaderV6Policy.kt').read_text()
cache = (kt/'ui/reader/translation/V9ChapterTranslationCache.kt').read_text()
reader = (kt/'ui/reader/ReaderActivity.kt').read_text()
controller = (kt/'ui/manga/MangaDetailsController.kt').read_text()
holder = (kt/'ui/manga/MangaHeaderHolder.kt').read_text()
menu = (root/'app/src/main/res/menu/reader.xml').read_text()
required = (
    'detectLanguageFromVisiblePages(pages, currentPageIndex)',
    'scanOcrFrames(page, maxFrames = 3)',
    'scanOcrFrames(page)',
    'BitmapRegionDecoder.newInstance(stream, false)',
    'ocrTileSlices(wide, tall, sampleSize)',
    'ocrTileToPageRect(',
    'hasReadableTranslation(translated)',
    'withTimeoutOrNull(120_000L)',
    'intermediate?.downloadModelIfNeeded(conditions)',
    'translator.downloadModelIfNeeded(conditions)',
    'toArabic.translate(english)',
)
for s in required:
    assert s in engine, 'Translator recovery missing: '+s
for s in ('fun languageDetectionSampleOrder(', 'fun ocrTileSlices(',
          'fun ocrTileToPageRect(', 'fun hasReadableTranslation('):
    assert s in policy, 'Pure safety policy missing: '+s
assert 'if (values.isNotEmpty()) put(index' in cache
assert 'filter { hasReadableTranslation(it) }' in cache
assert 'autoTranslationAttempts >= 2' in reader
assert 'lastAutoTranslationLanguage' not in reader
assert 'v12_neon_details_header' in holder and 'v12_neon_details_header' in controller
assert 'resources.getBoolean(R.bool.v4_neon_mode)' in controller
assert 'getBoolean(R.bool.v4_neon_mode)' in holder
for s in ('startReadingButton.background =','R.drawable.v12_neon_read_button'):
    assert s in holder
for s in ('v12_neon_read_button.xml', 'v12_neon_details_header.xml'):
    ET.parse(root/'app/src/main/res/drawable'/s)
ET.parse(root/'app/src/main/res/values/v12_neon_runtime_colors.xml')
assert '<color name="v12_neon_bg">#080F20</color>' in (root/'app/src/main/res/values/v12_neon_runtime_colors.xml').read_text()
for s in ('action_translate_chapter','action_translation_auto','action_translation_edit',
          'action_translation_diagnostics','action_translation_page','action_translation_toggle',
          'action_translation_appearance','action_translation_retry','action_translation_stop'):
    assert s in menu and 'R.id.'+s in reader
assert (root/'app/src/test/java/eu/kanade/tachiyomi/ui/reader/translation/ReaderV12RecoveryPolicyTest.kt').is_file()
print('PASS V12: bounded webtoon OCR tiles, cache recovery, fallback and enhanced-only visual overrides')
print('NOT a substitute for Android compilation, real OCR/ML Kit or on-device visual QA')
