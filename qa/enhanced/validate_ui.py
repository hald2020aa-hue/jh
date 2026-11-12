#!/usr/bin/env python3
"""Parse UI resources and verify new entry points without Android SDK.
Does not replace AAPT or Kotlin compilation.
"""
from pathlib import Path
from xml.etree import ElementTree as ET
import re

ROOT = Path(__file__).resolve().parents[2]
ANDROID = '{http://schemas.android.com/apk/res/android}'
checks = 0

def verify(ok, label):
    global checks
    if not ok:
        raise AssertionError(label)
    checks += 1
    print('PASS:', label)

files = list((ROOT / 'app/src/main/res').rglob('*.xml'))
files += list((ROOT / 'app/src/dev/res').rglob('*.xml'))
files += list((ROOT / 'app/src/devDebug/res').rglob('*.xml'))
files.append(ROOT / 'app/src/main/AndroidManifest.xml')
for path in files:
    ET.parse(path)
verify(len(files) > 500, f'Android XML parsed ({len(files)} files)')

for path, token in (
    ('app/src/main/res/menu/library.xml', '@+id/action_library_insights'),
    ('app/src/main/res/menu/library.xml', '@+id/action_only_duplicates'),
    ('app/src/main/res/menu/catalogue_main.xml', '@+id/action_source_health'),
    ('app/src/main/res/layout/reader_nav.xml', '@+id/toggle_spread'),
    ('app/src/main/java/eu/kanade/tachiyomi/ui/download/DownloadBottomSheet.kt', 'enhanced_download_summary'),
    ('app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt', 'toggleSpread.setOnClickListener'),
    ('app/src/main/java/eu/kanade/tachiyomi/ui/source/BrowseController.kt', 'SourceHealthActivity::class.java'),
    ('app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryController.kt', 'LibraryInsightsActivity::class.java'),
):
    verify(token in (ROOT / path).read_text(), path + ' wires '+ token)

manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
for name in ('ui.source.health.SourceHealthActivity', 'ui.library.insights.LibraryInsightsActivity'):
    nodes = [n for n in manifest.iter('activity') if (n.get(ANDROID + 'name') or '').endswith(name)]
    verify(len(nodes) == 1 and nodes[0].get(ANDROID + 'exported') == 'false', name + ' not exported')

for path in ('app/src/devDebug/res/mipmap-anydpi-v26/ic_launcher.xml',
             'app/src/devDebug/res/mipmap-anydpi-v26/ic_launcher_round.xml'):
    data = (ROOT/path).read_text()
    verify('enhanced_launcher_foreground' in data, path + ' uses new icon')

strings=set()
for p in (ROOT/'app/src/main/res/values').glob('*.xml'):
    root=ET.parse(p).getroot()
    for v in root:
        if 'name' in v.attrib: strings.add(v.attrib['name'])
for path in (
    'app/src/main/java/eu/kanade/tachiyomi/ui/source/health/SourceHealthActivity.kt',
    'app/src/main/java/eu/kanade/tachiyomi/ui/library/insights/LibraryInsightsActivity.kt',
):
    refs=set(re.findall(r'R\.string\.([A-Za-z0-9_]+)',(ROOT/path).read_text()))
    verify(not refs-strings, path + ' string references resolved')
verify('verifySingleArm64DevApk' in (ROOT/'app/build.gradle.kts').read_text(), 'single APK verification task')
verify('app-dev-arm64-v8a-debug.apk' in (ROOT/'.github/workflows/quality_gate.yml').read_text(), 'CI uploads only arm64 APK')
verify('getPopularManga(1)' in (ROOT/'app/src/main/java/eu/kanade/tachiyomi/ui/source/health/SourceHealthActivity.kt').read_text(), 'deep source browsing is wired')
verify('getChapterList(details)' in (ROOT/'app/src/main/java/eu/kanade/tachiyomi/ui/source/health/SourceHealthActivity.kt').read_text(), 'deep chapter check is wired')
verify('enhanced_retry_failed' in (ROOT/'app/src/main/java/eu/kanade/tachiyomi/ui/download/DownloadBottomSheet.kt').read_text(), 'download retry command wired')

for path, token in (
    ('app/src/main/java/eu/kanade/tachiyomi/ui/download/center/DownloadCenterActivity.kt', 'manager.queue.addListener(observer)'),
    ('app/src/main/java/eu/kanade/tachiyomi/ui/download/center/DownloadCenterActivity.kt', 'manager.startDownloadNow'),
    ('app/src/main/java/eu/kanade/tachiyomi/ui/setting/SettingsMainController.kt', 'DownloadCenterActivity::class.java'),
    ('app/src/main/java/eu/kanade/tachiyomi/ui/setting/SettingsDownloadController.kt', 'DownloadCenterActivity::class.java'),
):
    verify(token in (ROOT/path).read_text(), path + ' contains ' + token)

manifest = ET.parse(ROOT/'app/src/main/AndroidManifest.xml').getroot()
activity = [e for e in manifest.iter('activity') if (e.get(ANDROID+'name') or '').endswith('ui.download.center.DownloadCenterActivity')]
verify(len(activity) == 1 and activity[0].get(ANDROID+'exported') == 'false', 'Download center explicitly private')

newrefs = set(re.findall(r'R\.string\.([A-Za-z0-9_]+)', (ROOT/'app/src/main/java/eu/kanade/tachiyomi/ui/download/center/DownloadCenterActivity.kt').read_text()))
verify(not newrefs-strings, 'Download center strings are defined')

ci = (ROOT/'.github/workflows/quality_gate.yml').read_text()
verify('path: app/build/outputs/apk/dev/debug/app-dev-arm64-v8a-debug.apk' in ci and 'path: app/build/outputs/apk/dev/debug/*.apk' not in ci, 'one exact APK upload path')
verify('verifySingleArm64DevApk' in ci and '-Pjh.arm64Only=true' in ci, 'Gradle task verifies exact arm64 output')
verify('apksigner' in ci and 'unzip -tqq' in ci, 'CI checks APK archive and signature')

print('ENHANCED UI STATIC CHECKS:',checks,'/',checks,'PASS')
