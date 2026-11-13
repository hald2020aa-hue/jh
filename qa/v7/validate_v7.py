#!/usr/bin/env python3
"""V7 stage 2-6 source checks. This is NOT Android compilation or device QA."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
checks = []

def src(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')

def check(desc: str, result: bool):
    checks.append((desc, result))
    print(('PASS' if result else 'FAIL') + ': ' + desc)

reader = src('app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt')
overlay = src('app/src/main/java/eu/kanade/tachiyomi/ui/reader/translation/ReaderBubbleOverlayView.kt')
image = src('app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/ReaderPageImageView.kt')
ocr = src('app/src/main/java/eu/kanade/tachiyomi/ui/reader/translation/OnDeviceChapterTranslator.kt')
loader = src('app/src/main/java/eu/kanade/tachiyomi/ui/reader/loader/HttpPageLoader.kt')
manager = src('app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadManager.kt')
queue = src('app/src/main/java/eu/kanade/tachiyomi/data/download/model/DownloadQueue.kt')
store = src('app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadStore.kt')
lib = src('app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryController.kt')
grid = src('app/src/main/java/eu/kanade/tachiyomi/widget/AutofitRecyclerView.kt')

for marker in ['ReaderBubbleOverlayView', 'sourceToViewCoord', 'image.imageMatrix.mapRect', 'TextDirectionHeuristics.RTL', 'StaticLayout.Builder', 'backgroundOpacity', 'fontScale', 'normalizedBubbleRect']:
    check('OCR bubble overlay: '+marker, marker in overlay)
for marker in ['action_translation_toggle', 'action_translation_retry', 'action_translation_appearance', 'translationUiPrefs', 'refreshTranslationOverlays', 'translationMergedSpread', 'focusOnPage']:
    check('Reader UI wiring: '+marker, marker in reader)
for marker in ['setTranslationIdentity', 'showTranslation', 'translationOverlay?.invalidate()', 'translationOverlay?.bringToFront()']:
    check('Image wrapper: '+marker, marker in image)
for marker in ['retryFailedOnly: Boolean = false', 'failedPageRetryOrder', 'val retained', 'translator.close()', 'recognizer.close()']:
    check('OCR lifecycle/retry: '+marker, marker in ocr)
for marker in ['queuedByPage', 'focusOnPage', 'offerPage(', 'outsideReadAheadWindow', 'suspendCancellableCoroutine']:
    check('Seven-page prefetch: '+marker, marker in loader)
check('single queue reorders without store deletion', 'downloader.queue.reorder(downloads)' in manager and 'downloader.queue.clear()\n        downloader.queue.addAll(downloads)' not in manager)
check('queue persists atomically', 'store.replaceAll(queue.toList())' in queue)
check('store atomic reordering', 'fun replaceAll(downloads: List<Download>)' in store)
check('restore does not clear persisted queue', 'clear()\n        return downloads' not in store)
check('library search throttled', 'delay(140)' in lib)
check('Fold preserves visible cover', 'scrollToPositionWithOffset(first, top - paddingTop)' in grid)
check('preload exactly 7 default', 'flowPrefs.getInt(Keys.preloadSize, 7)' in src('app/src/main/java/eu/kanade/tachiyomi/data/preference/PreferencesHelper.kt'))
check('five-column mode preserved', 'const val SMALL = 5' in src('app/src/main/java/eu/kanade/tachiyomi/ui/enhanced/NeonGridMode.kt'))
check('neon download Compose preserved', 'fun NeonDownloadPanel(' in src('app/src/main/java/eu/kanade/tachiyomi/ui/download/NeonDownloadPanel.kt'))
check('reader original images not overwritten', all(k not in ocr for k in ['writeBytes(', 'deleteChapter(', 'replaceChapter(', 'openOutputStream(']))

android = '{http://schemas.android.com/apk/res/android}'
menu = ET.parse(ROOT / 'app/src/main/res/menu/reader.xml')
ids = [i.attrib.get(android+'id', '') for i in menu.getroot()]
for key in ['action_translation_toggle', 'action_translation_appearance', 'action_translation_retry']:
    check('XML menu: '+key, any(v.endswith('/'+key) for v in ids))
for location in ['app/src/main/res/values/strings.xml','app/src/dev/res/values-ar/strings.xml']:
    elements = ET.parse(ROOT/location).getroot()
    keys = [e.attrib.get('name') for e in elements if e.tag=='string']
    check('translation strings present in '+location, all(key in keys for key in ['v7_translation_toggle','v7_translation_appearance','v7_translation_retry']))
    check('no duplicate string names in '+location, len(keys)==len(set(keys)))

workflow = src('.github/workflows/quality_gate.yml')
check('CI runs V7 source validator', 'qa/v7/validate_v7.py' in workflow)
check('CI builds a single arm64 APK', 'verifySingleArm64DevApk' in workflow)
check('Gradle uses arm64-only property', '-Pjh.arm64Only=true' in workflow)

assert all(ok for _, ok in checks), 'V7 prechecks failed!'
print(f'V7 STATIC PRECHECK: {len(checks)}/{len(checks)} PASS — Android build and physical testing still required')
