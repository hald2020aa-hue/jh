#!/usr/bin/env python3
# V18 visual consistency gate; not an APK build or device test.
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
DEV = ROOT / "app/src/dev/res"
MAIN = ROOT / "app/src/main/res"
A = "{http://schemas.android.com/apk/res/android}"

def check(value, label):
    if not value:
        raise AssertionError(label)
    print("PASS:", label)

def parse(path):
    return ET.parse(path).getroot()

def ids(path):
    root = parse(path)
    return [e.attrib[A + "id"].split("/")[-1] for e in root.iter() if A + "id" in e.attrib]

palette = parse(DEV / "values/neon_v18_colors.xml")
colors = {e.attrib.get("name") for e in palette if "name" in e.attrib}
required_colors = {
    "v18_bg", "v18_surface", "v18_surface_raised", "v18_text", "v18_muted",
    "v18_cyan", "v18_pink", "v18_blue", "v18_border", "v18_track",
    "v18_cyan_edge", "v18_blue_edge", "v18_pink_edge",
}
check(required_colors <= colors, "V18 palette is complete")

xml_count = 0
for path in DEV.rglob("*.xml"):
    parse(path)
    xml_count += 1
check(xml_count > 0, f"all {xml_count} dev XML resources parse")

drawables = {p.stem for p in (DEV / "drawable").glob("*.xml")}
required_drawables = {
    "v18_surface_card", "v18_detail_header", "v18_dialog_frame",
    "v18_reader_frame", "v18_chip", "v18_cover_placeholder", "v18_surface_selected",
}
check(required_drawables <= drawables, "V18 shared drawable set exists")

for path in DEV.rglob("*.xml"):
    text = path.read_text(encoding="utf-8")
    for kind, name in re.findall(r"@(color|drawable)/(v18_[A-Za-z0-9_]+)", text):
        if kind == "color":
            check(name in colors, f"{path.relative_to(ROOT)} resolves @{kind}/{name}")
        else:
            check(name in drawables, f"{path.relative_to(ROOT)} resolves @{kind}/{name}")

targets = {
    "browse_source_controller.xml": {"source_layout", "floating_browse_bar", "popular_group", "latest_group", "filter_group"},
    "browse_controller.xml": {"browse_layout", "browse_frame_layout", "source_recycler"},
    "source_item.xml": {"source_card", "source_image", "title", "source_latest", "source_pin"},
    "library_controller.xml": {"library_layout", "library_top_actions", "library_grid_recycler", "filter_bottom_sheet"},
    "manga_grid_item.xml": {"card", "cover_thumbnail", "title", "subtitle"},
    "manga_list_item.xml": {"list_card", "cover_thumbnail", "title", "subtitle"},
    "manga_header_item.xml": {"manga_cover", "title", "manga_source", "chapters_title"},
    "chapters_item.xml": {"chapter_card", "chapter_title", "chapter_scanlator", "download_button"},
    "download_item.xml": {"front_view", "title", "chapter_title", "download_progress"},
    "reader_activity.xml": {"viewer_container", "page_number", "app_bar", "chapters_sheet"},
    "reader_nav.xml": {"reader_nav", "left_chapter", "page_seekbar", "right_chapter"},
}
for filename, expected in targets.items():
    path = DEV / "layout" / filename
    check(path.is_file(), f"{filename} overlay exists")
    check(expected <= set(ids(path)), f"{filename} retains required view IDs")

# Every dev layout overlay must retain all IDs from its main counterpart.
# New IDs are allowed; removing an existing ID can break ViewBinding consumers.
overlay_count = 0
for overlay in sorted((DEV / "layout").glob("*.xml")):
    base = MAIN / "layout" / overlay.name
    overlay_ids_list = ids(overlay)
    duplicate_ids = sorted({item for item in overlay_ids_list if overlay_ids_list.count(item) > 1})
    check(not duplicate_ids, f"{overlay.name} has no duplicate view IDs" +
          (f" ({', '.join(duplicate_ids)})" if duplicate_ids else ""))
    if not base.is_file():
        continue
    base_ids = set(ids(base))
    dev_ids = set(overlay_ids_list)
    missing_ids = sorted(base_ids - dev_ids)
    check(not missing_ids,
          f"{overlay.name} preserves all original view IDs" +
          (f" (missing: {', '.join(missing_ids)})" if missing_ids else ""))
    overlay_count += 1
check(overlay_count > 0, "dev layout overlays were compared with main layouts")

source_item = (DEV / "layout/source_item.xml").read_text(encoding="utf-8")
title_tag = re.search(r'<TextView\b[^>]*android:id="@\+id/title"[^>]*>', source_item, re.S)
check(bool(title_tag) and "android:text=" not in title_tag.group(0),
      "source card title remains dynamically bound, not hard-coded")
source_controller = (ROOT / "app/src/main/java/eu/kanade/tachiyomi/ui/source/browse/BrowseSourceController.kt").read_text(encoding="utf-8")
check("presenter.source.name" in source_controller, "catalogue heading uses the real source name")

for name in ("v4_neon_surface", "v4_neon_header", "v11_neon_dialog_frame",
             "v11_neon_reader_frame", "v11_neon_source_chip", "v14_library_actions_frame"):
    path = DEV / "drawable" / f"{name}.xml"
    check(path.is_file(), f"legacy drawable {name} remains available")
    check("@drawable/v18_" in path.read_text(encoding="utf-8"),
          f"{name} delegates to the V18 shared design")

workflow = (ROOT / ".github/workflows/quality_gate.yml").read_text(encoding="utf-8")
for command, label in [
    ("bash qa/run_local_tests.sh", "CI runs the 64-case Kotlin regression suite and search evaluator"),
    ("bash qa/enhanced/run_enhanced_tests.sh", "CI runs the 36-case enhanced UI logic suite"),
    ("python3 qa/neon/validate_v5.py", "CI runs the V5 UI validation suite"),
    (":app:lintDevDebug", "CI runs Android lint for the development variant"),
    ('- "feature/**"', "CI triggers on feature branch pushes"),
]:
    check(command in workflow, label)

print("V18 UNIFIED NEON STATIC CHECKS PASSED — NOT AN APK BUILD OR DEVICE VALIDATION")
