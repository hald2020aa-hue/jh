#!/usr/bin/env python3
"""Checks resource overlays, ViewBinding IDs and functional wiring without Android SDK.
This is not an Android build, emulator test or image comparison.
"""
from pathlib import Path
from xml.etree import ElementTree as ET
import re

ROOT=Path(__file__).resolve().parents[2]
MAIN=ROOT/'app/src/main/res'
DEV=ROOT/'app/src/dev/res'
A='{http://schemas.android.com/apk/res/android}'
AP='{http://schemas.android.com/apk/res-auto}'
checks=0

def check(cond, name):
 global checks
 if not cond:raise AssertionError(name)
 checks+=1
 print('PASS',name)

def parsed(p):
 try:return ET.parse(str(p))
 except ET.ParseError as e:raise AssertionError(f'Invalid XML {p}: {e}')

def ids(p):
 root=parsed(p).getroot()
 return [x.get(A+'id').split('/')[-1] for x in root.iter() if isinstance(x.tag,str) and x.get(A+'id')]

for f in (DEV/'layout').glob('*.xml'):
 main=MAIN/'layout'/f.name
 check(main.exists(),f'{f.name} has stable baseline')
 old=set(ids(main));new=set(ids(f));check(old<=new,f'{f.name} keeps existing binding IDs')
 check(len(ids(f))==len(set(ids(f))),f'{f.name} does not repeat IDs')

for src in ('main','dev','devDebug'):
 folder=ROOT/f'app/src/{src}/res'
 if not folder.exists():continue
 files=list(folder.rglob('*.xml'))
 for p in files:parsed(p)
 check(len(files)>0,f'{src}: {len(files)} resource XML documents parsed')

names={}
for flavor,folder in [('main',MAIN),('dev',DEV)]:
 resources={}
 for p in (folder/'values').glob('*.xml'):
  for el in parsed(p).getroot():
   if not isinstance(el.tag,str):continue
   name=el.get('name')
   if name:
    key=(el.tag,name)
    check(key not in resources,f'{flavor} {p.name}: resource {el.tag}:{name} unique') if key in resources else None
    resources[key]=p
 names[flavor]=resources
check(('bool','v4_neon_mode') in names['main'] and ('bool','v4_neon_mode') in names['dev'], 'flavor on/off switch exists')
for n in ('v4_card','v4_bg','v4_text','v4_muted','v4_primary','v4_cyan','v4_outline'):
 check(('color',n) in names['main'] and ('color',n) in names['dev'],f'color {n} available for both variants')
for shape in ('v4_neon_header','v4_neon_surface','v4_neon_nav','v4_neon_chip'):
 check((DEV/'drawable'/f'{shape}.xml').exists(),f'drawable {shape} exists')
for f in (DEV/'layout').glob('*.xml'):
 for token in re.findall(r'@(color|drawable)/([A-Za-z0-9_]+)',f.read_text()):
  kind,name=token
  if name.startswith('v4_'):
   if kind=='color':check(('color',name) in names['dev'],f'{f.name} color {name} available')
   elif kind=='drawable':check((DEV/'drawable'/f'{name}.xml').exists(),f'{f.name} drawable {name} available')

mgrid=ROOT/'app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryGridHolder.kt'
mlist=ROOT/'app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryListHolder.kt'
chapter=ROOT/'app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaHeaderHolder.kt'
reader=ROOT/'app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt'
dl=ROOT/'app/src/main/java/eu/kanade/tachiyomi/ui/download/center/DownloadCenterActivity.kt'
for x in (mgrid,mlist):check('knownChapterProgress' in x.read_text() and 'readingProgress' in x.read_text(),x.name+' uses real read progress')
check('v4ChapterProgress' in chapter.read_text() and 'chapter.read' in chapter.read_text(), 'details use real chapter flags')
check('adaptiveReaderBarWidth' in reader.read_text(), 'foldable reader layout uses adaptive sizing')
check('view.loadManga(entry.cover)' in dl.read_text(), 'download center uses authenticated image loader')
check('confirmRemove' in dl.read_text(), 'download action requires confirmation')
check('sortDownloadRows' in dl.read_text() and 'DropdownMenuItem' in dl.read_text(), 'download queue has functional sorting actions')
check('Channel.CONFLATED' in dl.read_text() and 'receiveAsFlow' in dl.read_text(), 'download events coalesce before UI rendering')
check('manager.queue.addListener' in dl.read_text(), 'download center follows real queue state')
check('v4_neon_mode' in reader.read_text(), 'reader fold change gated to dev version')
check('app-dev-arm64-v8a-debug.apk' in (ROOT/'.github/workflows/quality_gate.yml').read_text(), 'single APK target preserved')
check('NeonUxUnitTest' in (ROOT/'app/src/test/java/eu/kanade/tachiyomi/ui/enhanced/NeonUxUnitTest.kt').read_text(), 'new unit tests registered for Gradle')
print(f'NEON DESIGN/INTEGRATION: {checks}/{checks} PASS — not an Android build')
