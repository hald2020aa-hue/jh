#!/usr/bin/env python3
"""Regression checks for the V9 OCR paths, caching, update identity and protected geometry."""
from pathlib import Path
from xml.etree import ElementTree as ET
root = Path(__file__).resolve().parents[2]
reader = (root / 'app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt').read_text()
engine = (root / 'app/src/main/java/eu/kanade/tachiyomi/ui/reader/translation/OnDeviceChapterTranslator.kt').read_text()
cache = (root / 'app/src/main/java/eu/kanade/tachiyomi/ui/reader/translation/V9ChapterTranslationCache.kt').read_text()
balloon = (root / 'app/src/main/java/eu/kanade/tachiyomi/ui/reader/translation/V9BubbleContourPolicy.kt').read_text()
spread = (root / 'app/src/main/java/eu/kanade/tachiyomi/ui/reader/translation/V9SpreadGeometry.kt').read_text()
gradle = (root / 'app/build.gradle.kts').read_text()
menu_path = root / 'app/src/main/res/menu/reader.xml'
ET.parse(menu_path)
for key in ['action_translation_auto','action_translation_edit','action_translation_diagnostics']:
    assert key in menu_path.read_text(), key
    assert 'R.id.'+key in reader, key
for key in ['detectLanguage(', 'requiresEnglishPivot(source)', 'toArabic.translate(english)',
            'detectSpeechBalloonRegion(', 'cache.read(chapter, source)', 'cache.write(chapter, source',
            'editTranslatedBlock(', 'withTimeoutOrNull(120_000L)']:
    assert key in engine, key
for key in ['AtomicFile(', 'SHA-256', '24L * 1024 * 1024', 'context.cacheDir']:
    assert key in cache, key
for key in ['return null', 'isBrightBalloonPixel', 'others.any']:
    assert key in balloon, key
for key in ['primary', 'secondary', 'hingePixels']:
    assert key in spread, key
for key in ['applicationId = "eu.kanade.tachiyomi"','applicationIdSuffix = ".enhanced"',
            'applicationIdSuffix = ".debugJ2K"', 'versionCode = AndroidVersions.versionCode + 1',
            'JH_UPDATE_STORE_FILE','jhStableUpdate']:
    assert key in gradle, key
assert 'private val chapterTranslator by lazy' in reader
assert 'startAutoTranslationIfConfigured(page)' in reader
print('PASS: V9 source wiring and explicit signing compatibility safeguards')
