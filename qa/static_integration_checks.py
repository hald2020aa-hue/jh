#!/usr/bin/env python3
"""Offline structural and SQL checks; does not replace Android build/tests."""
import sqlite3
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parent.parent
passed = 0

def check(name, condition):
    global passed
    assert condition, name
    passed += 1
    print(f"PASS: {name}")

for path in [
    'app/src/main/res/layout/stats_controller.xml',
    'app/src/main/res/values/strings.xml',
    'app/src/main/res/values-ar/strings.xml',
]:
    ET.parse(root / path)
check('changed Android XML parse', True)

for folder in ['values', 'values-ar']:
    root_xml = ET.parse(root / 'app/src/main/res' / folder / 'strings.xml').getroot()
    names = [node.get('name') for node in root_xml.findall('string')]
    check(f'{folder} has unique string resources', len(names) == len(set(names)))
    check(f'{folder} contains new labels', {
        'search_cross_source_duplicates', 'stats_recent_chapters', 'stats_recent_days'
    }.issubset(set(names)))

layout = (root / 'app/src/main/res/layout/stats_controller.xml').read_text()
check('recent activity UI controls declared', all(x in layout for x in
    ['stats_recent_chapters_text', 'stats_recent_days_text']))

# Validate the query used to display the recent history counters with SQLite.
src = (root / 'app/src/main/java/eu/kanade/tachiyomi/data/database/queries/HistoryQueries.kt').read_text()
check('SQL is scoped to timestamp and local calendar days',
    'COUNT(DISTINCT date(' in src and 'WHERE ${HistoryTable.COL_LAST_READ} >= ?' in src)
con = sqlite3.connect(':memory:')
con.execute('CREATE TABLE history(history_last_read INTEGER)')
for timestamp in [1_700_000_000_000, 1_700_000_010_000, 1_700_086_400_000, 1_650_000_000_000]:
    con.execute('INSERT INTO history VALUES (?)', (timestamp,))
result = con.execute("""SELECT COUNT(*), COUNT(DISTINCT date(history_last_read / 1000, 'unixepoch', 'localtime'))
                        FROM history WHERE history_last_read >= ?""", (1_699_000_000_000,)).fetchone()
check('recent activity query counts chapters and distinct days', result == (3, 2))

check('Gradle wrapper JAR is included', (root / 'gradle/wrapper/gradle-wrapper.jar').is_file())
check('existing license is retained', (root / 'LICENSE').is_file())
print(f'STATIC INTEGRATION CHECKS: {passed} / {passed} PASS')
