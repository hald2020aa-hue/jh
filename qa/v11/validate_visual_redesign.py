#!/usr/bin/env python3
"""V11 neon visual-only safety gate (no Gradle/SDK required).

Pins original view IDs, classes and function-related XML attributes from the
successful Stage2to6 branch. Purely decorative overlays must not alter the
source, reader, download, translation, or database contracts.
"""
from __future__ import annotations
import json, sys
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'app/src/main'
DEV = ROOT / 'app/src/dev'
A='{http://schemas.android.com/apk/res/android}'
P='{http://schemas.android.com/apk/res-auto}'

visual_attrs={
 A+'background',A+'backgroundTint',A+'foreground',A+'padding',A+'paddingStart',A+'paddingEnd',
 A+'paddingTop',A+'paddingBottom',A+'layout_marginStart',A+'layout_marginEnd',A+'layout_marginTop',
 A+'layout_marginBottom',A+'layout_width',A+'layout_height',A+'layout_weight',A+'gravity',
 A+'textSize',A+'textColor',A+'textAlignment',A+'elevation',A+'minHeight',A+'clipChildren',
 A+'clipToPadding',A+'layout_gravity',A+'maxWidth',P+'cardBackgroundColor',P+'cardCornerRadius',
 P+'cardElevation',P+'strokeColor',P+'strokeWidth',P+'cornerRadius',P+'backgroundTint',
 P+'iconTint',P+'thumbTint',P+'trackTint',P+'itemIconTint',P+'itemTextColor',
 P+'itemActiveIndicatorColor',P+'thumbColor',P+'trackColorActive',P+'trackColorInactive',
 P+'popupTheme',P+'layout_constraintHorizontal_bias',P+'layout_constrainedWidth',
 P+'layout_constraintEnd_toEndOf',
}

def check(ok:bool,msg:str):
 if not ok:raise AssertionError(msg)

def info(xml):
 result={}
 for el in ET.parse(xml).getroot().iter():
  raw=el.attrib.get(A+'id')
  if not raw:continue
  id=raw.replace('@+id/','').replace('@id/','')
  check(id not in result, f'duplicate XML id {id}: {xml}')
  attrs={a:v for a,v in el.attrib.items()
         if a not in visual_attrs and not a.startswith('{http://schemas.android.com/tools}')
         and not a.startswith('{http://schemas.android.com/apk/res-auto}layout_constraint')
         and a not in ('style',A+'textAppearance')}
  result[id]={'tag':el.tag,'functional_attrs':attrs}
 return result

def normalize_pinned(attrs):
 return {a:v for a,v in attrs.items()
  if not a.startswith('{http://schemas.android.com/apk/res-auto}layout_constraint')
  and a not in ('style',A+'textAppearance')}

manifest=json.loads((ROOT/'qa/v11/stage2_controls_baseline.json').read_text())
check(manifest['base_source_commit']=='7ffa25b17ad6ccd3062a179fd355e8a1d60f0803','Unexpected baseline V9 commit')
verified=0
for rel,old in manifest['screen_bindings'].items():
 source=ROOT/rel
 # For both common layouts, a dev variant override is deliberate but the same
 # view-binding IDs and functional values must remain present.
 if rel in ('app/src/main/res/layout/common_view_empty.xml',
            'app/src/main/res/layout/material_text_button.xml'):
  source=DEV/'res/layout'/source.name
 check(source.is_file(),f'Missing layout {source}')
 now=info(source)
 # V14 adds independent top-action controls to the dev library layout only.
 # Existing bound widgets must remain identical; only these six new IDs are allowed.
 extra = ({'library_top_actions','library_top_categories','library_top_group',
           'library_top_display','library_top_filter','library_top_expand'}
          if rel == 'app/src/dev/res/layout/library_controller.xml' else set())
 check(set(old).issubset(now) and set(now)-set(old)==extra,
       f'Changed functional UI binding ID set in {source}')
 for id,entry in old.items():
  current=now[id]
  check(entry['tag']==current['tag'],f'Changed view class at {source}:{id}')
  check(normalize_pinned(entry['functional_attrs'])==current['functional_attrs'],
        f'Changed functional XML attributes in {source}:{id}')
  verified+=1

# The 9 existing commands remain in the original reader menu, same IDs and dispatch.
menu=(MAIN/'res/menu/reader.xml').read_text()
popup=(MAIN/'java/eu/kanade/tachiyomi/ui/reader/NeonReaderTranslationMenu.kt').read_text()
reader=(MAIN/'java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt').read_text()
ids=('action_translate_chapter','action_translation_auto','action_translation_edit',
 'action_translation_diagnostics','action_translation_page','action_translation_toggle',
 'action_translation_appearance','action_translation_retry','action_translation_stop')
for id in ids:
 check('android:id="@+id/'+id+'"' in menu, 'Existing action removed from reader menu: '+id)
 check('R.id.'+id+' to ' in popup, 'Existing action not in neon popup: '+id)
 check('R.id.'+id+' ->' in reader, 'Existing action lost its original handler: '+id)
check('dispatch(menuItem)' in popup,'Neon popup no longer dispatches to original handler')
check('onOptionsItemSelected(item)' in reader,'Neon popup does not reuse original handlers')
check('getBoolean(R.bool.v4_neon_mode)' in reader, 'Dev guard missing')

# All application resources parse, including main and dev overridden resources.
parsed=0
for source in (MAIN/'res',DEV/'res'):
 for p in source.rglob('*.xml'):
  ET.parse(p);parsed+=1
for name in ('v11_neon_translation_popup','v11_neon_translation_row'):
 check((MAIN/'res/drawable'/f'{name}.xml').is_file(),f'Unresolved shared popup resource {name}')
for name in ('v11_neon_search_frame','v11_neon_bottom_nav','v11_neon_title_tag',
             'v11_neon_reader_frame','v11_neon_source_chip'):
 check((DEV/'res/drawable'/f'{name}.xml').is_file(),f'Missing dev neon frame {name}')
source=(MAIN/'java/eu/kanade/tachiyomi/ui/source/browse/BrowseSourceController.kt').read_text()
check('presenter.source.name' in source, 'Dynamic source title lost')
main=(MAIN/'java/eu/kanade/tachiyomi/ui/main/MainActivity.kt').read_text()
check(any('R.drawable.' + drawable in main for drawable in ('neon_unified_sheet_background', 'v12_neon_details_header')), 'Search animation wipes out header gradient')
sheet=(MAIN/'java/eu/kanade/tachiyomi/ui/reader/chapter/ReaderChapterSheet.kt').read_text()
check('backgroundTintList = null' in sheet,'Bottom reader tint still wipes out neon frame')
print(f'PASS V11 VISUAL SAFETY: {verified} stable bound widgets, {len(ids)} original translation commands, {parsed} valid XML resources')
print('NOTE: Not proof of APK compilation or physical-screen rendering.')
