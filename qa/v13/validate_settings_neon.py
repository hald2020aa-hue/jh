#!/usr/bin/env python3
"""Fail-closed static QA for V13 settings visual-only redesign.

This does not claim that Android compiled, nor that real device settings were tested.
"""
from __future__ import annotations
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'app/src/main/java/eu/kanade/tachiyomi/ui/setting'
SRC = BASE / 'NeonSettingsStyler.kt'
CONTROLLER = BASE / 'SettingsController.kt'
DIALOG = BASE / 'NeonSettingsDialogs.kt'

def expect(value: bool, message: str) -> None:
    if not value:
        raise SystemExit('FAIL V13 SETTINGS: ' + message)
    print('PASS', message)

reference=json.loads((ROOT/'qa/v13/settings_reference_baseline.json').read_text())
for name,record in reference.items():
    candidate = (BASE/name) if '/' not in name else ROOT/'app/src/main/java/eu/kanade/tachiyomi'/name
    content=candidate.read_text()
    expect(re.findall(r'\bkey\s*=\s*([^\n]+)',content)==record['key_lines'], name+': keys unchanged')
    expect(re.findall(r'\bbindTo\(([^\n]+)',content)==record['binding_lines'], name+': persistence bindings unchanged')
    expect(re.findall(r'\bnavigateTo\(([^\n]+)',content)==record['navigation_lines'], name+': navigation targets unchanged')

source=SRC.read_text()
controller=CONTROLLER.read_text()
dialog=DIALOG.read_text()
expect('BuildConfig.FLAVOR == "dev"' in controller, 'only enhanced dev uses neon settings')
expect('NeonSettingsStyler(listView, this is SettingsMainController)' in controller, 'all settings share new styling')
expect('neonSettingsStyler?.detach()' in controller, 'detach view listener on destruction')
expect('decorateSettingsToolbar()' in controller and 'restoreSettingsToolbar()' in controller,
       'app toolbar neon is restored on settings exit')
expect('settingsToolbars[toolbar]' in controller and 'lease.owners -= 1' in controller
       and 'lease.owners <= 0' in controller,
       'shared toolbar background is only restored after last settings screen exits')
expect('setBackgroundColor(animator.animatedValue' not in controller and 'view.foreground = originalForeground' in controller,
       'search highlight preserves rounded card backgrounds')
expect('PreferenceGroupAdapter' in source and 'addOnChildAttachStateChangeListener' in source,
       'visual styling wraps original Preference adapter')
expect('PreferenceCategory' in source and 'RippleDrawable' in source and 'LinearGradient' in source,
       'gradient category borders and ripple cards are implemented')
expect('MaterialSwitch' in source and 'Slider' in source and 'thumbTintList' in source,
       'existing switches and sliders receive visual tints')
for forbidden in ['isChecked =', 'slider.value =', 'setOnPreferenceChangeListener', 'sharedPreferences', 'preferenceDataStore', 'notifyChanged()']:
    expect(forbidden not in source, f'styler does not alter preference behavior: {forbidden}')
expect('materialAlertDialog()' in dialog and 'BuildConfig.FLAVOR == "dev"' in dialog,
       'non-dev dialog behavior unchanged')

# The dev overrides must retain all view binding IDs of the unmodified layouts.
for name in ['settings_search_controller.xml','settings_search_controller_card.xml']:
    base=ROOT/'app/src/main/res/layout'/name
    dev=ROOT/'app/src/dev/res/layout'/name
    old=ET.parse(base).getroot()
    new=ET.parse(dev).getroot()
    android_id='{http://schemas.android.com/apk/res/android}id'
    old_ids={x.get(android_id) for x in old.iter() if x.get(android_id)}
    new_ids={x.get(android_id) for x in new.iter() if x.get(android_id)}
    expect(old_ids==new_ids, name+': all original binding IDs retained')
    expect(old.tag==new.tag, name+': root view type retained')

ripple = ET.parse(ROOT/'app/src/main/res/drawable/v13_settings_card_ripple.xml').getroot()
expect(ripple.tag=='ripple', 'search results preserve bounded tap feedback')
toolbar = ET.parse(ROOT/'app/src/main/res/drawable/v13_settings_toolbar.xml').getroot()
expect(toolbar.tag=='layer-list', 'settings-specific app bar has neon gradient underline')
for name in ['v13_settings_card.xml','v13_settings_dialog.xml']:
    tree=ET.parse(ROOT/'app/src/main/res/drawable'/name).getroot()
    expect(tree.tag=='layer-list' and len(tree)>=2, name+': gradient frame, inset fill')
for name in ['v13_settings_dialog_styles.xml','v13_neon_settings_colors.xml']:
    ET.parse(ROOT/'app/src/main/res/values'/name)

mat=(ROOT/'app/src/main/java/eu/kanade/tachiyomi/widget/preference/MatPreference.kt').read_text()
expect('neonSettingsDialog()' in mat and 'setNegativeButton' in mat, 'selection dialogs retain callbacks')
for name in ['SettingsAdvancedController.kt','SettingsBackupController.kt']:
    expect('neonSettingsDialog()' in (BASE/name).read_text(),name+': options use neon dialog')
expect('neonSettingsDialog()' in (ROOT/'app/src/main/java/eu/kanade/tachiyomi/ui/more/AboutController.kt').read_text(),
       'About section dialog uses neon presentation')
expect('neonSettingsDialog()' in (BASE/'database/ClearDatabaseController.kt').read_text(),
       'database warning dialog styled without altering actions')
expect('ThemeOverlay_J2K_NeonSettingsDialog' in (BASE/'SettingsDownloadController.kt').read_text(),
       'download directory choice dialog styled')

count=0
for xml in (ROOT/'app/src').glob('**/res/**/*.xml'):
    ET.parse(xml)
    count+=1
expect(count >= 720, f'all {count} source XML files parse')
print('V13 SETTINGS STATIC CHECKS PASSED — NOT AN ANDROID BUILD OR DEVICE VALIDATION')
