#!/usr/bin/env python3
"""V14 safety check: top actions reuse existing library commands; main layout unchanged."""
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[2]
lib=ROOT/'app/src/dev/res/layout/library_controller.xml'
x=ET.parse(lib).getroot()
ns='{http://schemas.android.com/apk/res/android}'
ids={el.attrib[ns+'id'].replace('@+id/','').replace('@id/',''):el for el in x.iter() if ns+'id' in el.attrib}
expected=('library_top_actions','library_top_categories','library_top_group','library_top_display','library_top_filter','library_top_expand')
assert all(i in ids for i in expected), 'Missing top action control'
assert all(i in ids for i in ('filter_bottom_sheet','library_grid_recycler','category_hopper_frame'))
code=(ROOT/'app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryController.kt').read_text()
for key,cmd in {'library_top_categories':'categoryButton.performClick()', 'library_top_group':'showGroupOptions()', 'library_top_display':'showDisplayOptions()', 'library_top_filter':'showSheet()', 'library_top_expand':'presenter.toggleAllCategoryVisibility()'}.items():
    assert f'R.id.{key}' in code and cmd in code, (key,cmd)
assert 'top = topOffset + 86.dpToPx' in code, 'Initial cover clearance missing'
assert 'searchCard.getLocationInWindow(cardPosition)' in code, 'Live search alignment missing'
assert 'actions.translationY = actionTop.toFloat()' in code, 'Action row not anchored'
assert 'removeOnPreDrawListener(callback)' in code, 'Listener cleanup missing'
original=(ROOT/'app/src/main/res/menu/bottom_navigation.xml').read_text()
override=(ROOT/'app/src/dev/res/menu/bottom_navigation.xml').read_text()
import re
f=lambda s:re.findall(r'android:id="@\+id/([^"]+)"',s)
assert f(original)==f(override),'Bottom navigation handlers changed'
for menu in ('side_navigation.xml','library.xml','reader.xml','browse_source.xml',
             'manga_details.xml','settings_main.xml','recents.xml','catalogue_main.xml'):
    orig=(ROOT/'app/src/main/res/menu'/menu).read_text()
    dev=(ROOT/'app/src/dev/res/menu'/menu).read_text()
    assert f(orig)==f(dev),menu
assert (ROOT/'app/src/main/res/values/v14_library_action_ids.xml').is_file()
assert (ROOT/'app/src/dev/res/values/v14_library_action_flags.xml').is_file()
for d in ('v14_library_actions_frame','v14_neon_popup','v14_icon_library','v14_icon_recent','v14_icon_browse'):
    assert (ROOT/'app/src/dev/res/drawable'/f'{d}.xml').is_file(),d
for source in ('main','dev'):
    for fp in (ROOT/f'app/src/{source}/res').rglob('*.xml'):ET.parse(fp)
print('V14 PASS: five top actions retain original behavior, menu IDs unchanged, resources parse')
