#!/usr/bin/env python3
"""Pre-CI checks for V5. Not an Android build or emulator test."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = '{http://schemas.android.com/apk/res/android}'
checks = 0

def test(ok, name):
    global checks
    if not ok:
        raise AssertionError(name)
    checks += 1
    print('PASS', name)

def src(path):
    return (ROOT / path).read_text(encoding='utf-8')

source_path = ROOT / 'app/src/dev/res/layout/source_item.xml'
source_xml = ET.parse(source_path).getroot()
card = next(n for n in source_xml.iter() if n.attrib.get(ANDROID+'id') == '@+id/card')
test(card.attrib.get(ANDROID+'layout_height') == '68dp', 'source rows reduced to 68dp')
for name in ['source_image','title','source_latest','source_pin']:
    test(any(n.attrib.get(ANDROID+'id','').endswith('/'+name) for n in source_xml.iter()), 'source preserves '+name)

browse = src('app/src/main/java/eu/kanade/tachiyomi/ui/source/browse/BrowseSourceController.kt')
lib = src('app/src/main/java/eu/kanade/tachiyomi/ui/library/display/LibraryDisplayView.kt')
mode = src('app/src/main/java/eu/kanade/tachiyomi/ui/enhanced/NeonGridMode.kt')
for n in [2,3,5]:
    test(f'const val { {2:"LARGE",3:"MEDIUM",5:"SMALL"}[n] } = {n}' in mode, f'exact {n}-column preset persisted')
test('showNeonDisplayModes()' in browse and 'MaterialAlertDialogBuilder' in browse, 'source browse has actual four-mode choice')
test('v5DisplayModes.setOnClickListener' in lib, 'library has functional four-mode selector')
test('neonColumnCount' in src('app/src/main/java/eu/kanade/tachiyomi/widget/AutofitRecyclerView.kt'), 'Fold-size column count uses measured width')

panel = src('app/src/main/java/eu/kanade/tachiyomi/ui/download/NeonDownloadPanel.kt')
for pattern, label in [
    ('Brush.horizontalGradient(colors)', 'real gradient shader'),
    ('.height(14.dp)', '14dp straight progress track'),
    ('CircularProgressIndicator', 'per-chapter circular percentage'),
    ('onPause: (Download) -> Unit', 'per-chapter pause callback'),
    ('onResume: (Download) -> Unit', 'per-chapter resume callback'),
    ('onDelete: (Download) -> Unit', 'per-chapter delete callback'),
    ('onPrioritize: (Download) -> Unit', 'queue prioritize callback'),
    ('CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)', 'explicit RTL layout'),
    ('knownChapterBytes?.let(::bytesString) ?: "غير معروف"', 'honest unknown chapter bytes'),
    ('"قائمة التنزيل"', 'Arabic download title'),
]:
    test(pattern in panel, label)
test('ImageView' not in panel and 'loadManga' not in panel, 'no manga cover in new download UI')
test('24.6 MB' not in panel and '3.2 MB/s' not in panel, 'no placeholder size or speed in production UI')

center = src('app/src/main/java/eu/kanade/tachiyomi/ui/download/center/DownloadCenterActivity.kt')
sheet = src('app/src/main/java/eu/kanade/tachiyomi/ui/download/DownloadBottomSheet.kt')
test('if (resources.getBoolean(R.bool.v4_neon_mode)) {' in center and 'NeonDownloadPanel(' in center, 'dev download center uses Compose, standard unchanged')
test('setupNeonCompose()' in sheet and 'refreshNeonRows()' in sheet, 'bottom download sheet wired to live queue')
manager = src('app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadManager.kt')
downloader = src('app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt')
model = src('app/src/main/java/eu/kanade/tachiyomi/data/download/model/Download.kt')
test('fun pauseChapter(id: Long)' in downloader and 'pausedChapterIds' in downloader, 'per-chapter pause operates in downloader')
test('fun resumeChapter(id: Long)' in downloader and 'schedulerVersion' in downloader, 'resume causes scheduler recomputation')
test('v5_paused_download_chapters' in downloader, 'pause state survives process restart')
test('fun resumeChapter(id: Long)' in manager, 'UI calls supported manager API')
test('it.body.byteStream()' in downloader and 'download.addReceivedBytes' in downloader, 'network bytes are counted at transport boundary')
test('val transferStats: StateFlow<DownloadTransferStats>' in model, 'byte-speed telemetry observable by Compose')
test('DownloadStore.kt' not in '\n'.join(str(x) for x in (ROOT/'qa').rglob('*.patch')), 'no migration assumed for persistent queue')

a=ET.parse(ROOT/'app/src/dev/res/values-ar/strings.xml').getroot()
translations={node.attrib.get('name'):node.text for node in a}
test(translations.get('download_queue')=='قائمة التنزيل', 'Arabic download queue label corrected')
for k in ['downloading','downloads','enhanced_retry_failed']:
    test('تحميل' not in translations[k] and 'تنزيل' in translations[k], f'{k} terminology uses تنزيل')
test('app-dev-arm64-v8a-debug.apk' in src('.github/workflows/quality_gate.yml'), 'single arm64 APK CI target remains unchanged')

for f in (ROOT/'app/src/dev/res').rglob('*.xml'):
    ET.parse(f)
test(True, 'all dev XML files parse')
print(f'V5 INTEGRATION STATIC: {checks}/{checks} PASS (not an Android build)')
