#!/usr/bin/env python3
"""Prevent the top library row from drawing behind the search field."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
s=(ROOT/'app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryController.kt').read_text()
checks={
    'live card bottom': 'cardPosition[1] - rootPosition[1] + searchCard.height',
    'anchored actions': 'actions.translationY = actionTop.toFloat()',
    'first row clearance': 'recycler.updatePaddingRelative(top = contentTop)',
    'screen/scroll changes': 'ViewTreeObserver.OnPreDrawListener',
    'listener cleanup': 'removeOnPreDrawListener(callback)',
    'empty view clearance': 'bigToolbarHeight + activityBinding.appBar.paddingTop +',
    'old incorrect margin removed': 'topMargin = 0',
}
for label, token in checks.items():
    assert token in s, label
assert 'topMargin = topOffset + 6.dpToPx' not in s
print('V15 PASS: live search-card alignment, list/empty clearance, listener cleanup')
