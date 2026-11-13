#!/usr/bin/env python3
"""Offline checks only. NEVER substitute for Gradle compilation or device verification."""
from pathlib import Path
from xml.etree import ElementTree as ET
root = Path(__file__).resolve().parents[2]
checks = []
def check(name, ok):
    checks.append((name, bool(ok)))
    if not ok:
        raise AssertionError(name)
    print('PASS:', name)
def read(p): return (root / p).read_text(encoding='utf-8')
for path in [
    'app/src/main/res/menu/reader.xml',
    'app/src/main/res/values/strings.xml',
    'app/src/dev/res/values-ar/strings.xml',
]:
    ET.fromstring(read(path))
    check('parse '+path, True)
strings = read('app/src/dev/res/values-ar/strings.xml')
for needle in ['v6_translate_chapter', 'v6_translation_page', 'v6_translation_stop']:
    check('Arabic '+needle, f'name="{needle}"' in strings)
menu = read('app/src/main/res/menu/reader.xml')
for needle in ['action_translate_chapter', 'action_translation_page', 'action_translation_stop']:
    check('reader menu '+needle, needle in menu)
act = read('app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt')
check('UI action wired to engine', 'chapterTranslator.translateChapter(' in act)
check('translation keeps visible page priority', 'translatedPage?.index ?: 0' in act)
check('scope cancels with Activity', 'translationJob = scope.launch' in act)
trans = read('app/src/main/java/eu/kanade/tachiyomi/ui/reader/translation/OnDeviceChapterTranslator.kt')
for x in ['TextRecognition.getClient', 'Translation.getClient', 'InputImage.fromBitmap', 'downloadModelIfNeeded', 'recognizer.close()', 'translator.close()', 'ensureActive()', 'cancelAndJoin()', 'translationPageOrder']:
    check('engine '+x, x in trans)
for x in ['createFile(', 'writeBytes(', 'deleteChapter', 'deleteManga']:
    check('never mutates manga files: '+x, x not in trans)
check('prefetch default 7', 'Keys.preloadSize, 7' in read('app/src/main/java/eu/kanade/tachiyomi/data/preference/PreferencesHelper.kt'))
check('prefetch seven selectable', '.plus(7).sorted()' in read('app/src/main/java/eu/kanade/tachiyomi/ui/setting/SettingsReaderController.kt'))
check('one-time migration', 'v6_preload_7_initialized' in read('app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/HttpPageLoader.kt'))
gradle = read('app/build.gradle.kts')
for lib in ['text-recognition:', 'text-recognition-japanese:', 'text-recognition-korean:', 'text-recognition-chinese:', 'translate:17.0.3']:
    check('Android dependency '+lib, lib in gradle)
check('only one APK quality gate preserved', 'verifySingleArm64DevApk' in read('.github/workflows/quality_gate.yml'))
print(f'V6 STATIC PRECHECK: {len(checks)}/{len(checks)} PASS (NOT Android compile)')
