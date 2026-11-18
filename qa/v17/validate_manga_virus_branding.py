from pathlib import Path
import re
import struct
import xml.etree.ElementTree as ET
import zlib

R = Path(__file__).resolve().parents[2]
ANDROID = "{http://schemas.android.com/apk/res/android}"

def read(rel):
    path = R / rel
    if not path.is_file():
        raise SystemExit(f"Missing required file: {rel}")
    return path.read_text(encoding="utf-8")

def png_meta(path):
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n") or len(data) < 29:
        return None
    width, height, bit_depth, color_type = struct.unpack(">IIBB", data[16:26])
    return width, height, bit_depth, color_type, data

def paeth(a, b, c):
    p = a + b - c
    pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c

def alpha_corners(path):
    meta = png_meta(path)
    if not meta:
        return None
    width, height, bit_depth, color_type, data = meta
    if bit_depth != 8 or color_type != 6:
        return None
    pos = 8
    compressed = bytearray()
    while pos + 12 <= len(data):
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        kind = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        if kind == b"IDAT":
            compressed.extend(chunk)
        pos += 12 + length
        if kind == b"IEND":
            break
    raw = zlib.decompress(bytes(compressed))
    stride = width * 4
    previous = bytearray(stride)
    corners = None
    offset = 0
    for y in range(height):
        filter_type = raw[offset]
        row = bytearray(raw[offset + 1:offset + 1 + stride])
        offset += stride + 1
        for i in range(stride):
            left = row[i - 4] if i >= 4 else 0
            up = previous[i]
            upper_left = previous[i - 4] if i >= 4 else 0
            if filter_type == 1:
                row[i] = (row[i] + left) & 255
            elif filter_type == 2:
                row[i] = (row[i] + up) & 255
            elif filter_type == 3:
                row[i] = (row[i] + ((left + up) // 2)) & 255
            elif filter_type == 4:
                row[i] = (row[i] + paeth(left, up, upper_left)) & 255
            elif filter_type != 0:
                return None
        if y == 0:
            top = (row[3], row[(width - 1) * 4 + 3])
        if y == height - 1:
            bottom = (row[3], row[(width - 1) * 4 + 3])
        previous = row
    return top + bottom

checks = {}

main = ET.parse(R / "app/src/main/res/values/strings.xml").getroot()
main_ar = ET.parse(R / "app/src/main/res/values-ar/strings.xml").getroot()
dev = ET.parse(R / "app/src/dev/res/values/strings.xml").getroot()
dev_ar = ET.parse(R / "app/src/dev/res/values-ar/strings.xml").getroot()

def string_value(root, name):
    node = root.find(f"./string[@name='{name}']")
    return node.text if node is not None else None

checks["English main app label"] = string_value(main, "app_name") == "MANGA VIRUS"
checks["English main short label"] = string_value(main, "app_short_name") == "MANGA VIRUS"
checks["Arabic main app label"] = string_value(main_ar, "app_name") == "فيروس مانجا"
checks["Arabic main short label"] = string_value(main_ar, "app_short_name") == "فيروس مانجا"
checks["English development app label"] = string_value(dev, "app_name") == "MANGA VIRUS"
checks["English development short label"] = string_value(dev, "app_short_name") == "MANGA VIRUS"
checks["Arabic development app label"] = string_value(dev_ar, "app_name") == "فيروس مانجا"
checks["Arabic development short label"] = string_value(dev_ar, "app_short_name") == "فيروس مانجا"

main_en_text = read("app/src/main/res/values/strings.xml")
app_name_tag = re.search(r'<string\s+name="app_name"[^>]*>', main_en_text)
checks["Main app name allows Arabic translation"] = bool(app_name_tag) and "translatable=\"false\"" not in app_name_tag.group(0)

gradle = read("app/build.gradle.kts")
checks["Application ID preserved"] = 'applicationId = "eu.kanade.tachiyomi"' in gradle
checks["Debug application suffix preserved"] = 'applicationIdSuffix = ".debugJ2K"' in gradle
checks["Development application suffix preserved"] = 'applicationIdSuffix = ".enhanced"' in gradle

manifest = ET.parse(R / "app/src/main/AndroidManifest.xml").getroot()
application = manifest.find("application")
checks["Manifest launcher icon reference preserved"] = application is not None and application.get(ANDROID + "icon") == "@mipmap/ic_launcher"
checks["Manifest round launcher reference preserved"] = application is not None and application.get(ANDROID + "roundIcon") == "@mipmap/ic_launcher_round"

branding = ET.parse(R / "app/src/main/res/values/manga_virus_branding.xml").getroot()
checks["Brand background color exists"] = branding.find("./color[@name='manga_virus_icon_background']") is not None

for source_set in ("main", "debug", "devDebug"):
    for filename in ("ic_launcher.xml", "ic_launcher_round.xml"):
        path = f"app/src/{source_set}/res/mipmap-anydpi-v26/{filename}"
        root = ET.parse(R / path).getroot()
        layers = [node.get(ANDROID + "drawable") for node in list(root)]
        checks[f"{source_set}/{filename} uses branded background"] = "@color/manga_virus_icon_background" in layers
        checks[f"{source_set}/{filename} uses adaptive foreground"] = "@mipmap/ic_launcher_foreground" in layers
        checks[f"{source_set}/{filename} uses monochrome layer"] = "@mipmap/ic_launcher_monochrome" in layers

themes = read("app/src/main/res/values/themes.xml")
checks["Android 12 splash background branded"] = bool(re.search(
    r'<item\s+name="android:windowSplashScreenBackground"[^>]*>@color/manga_virus_icon_background</item>',
    themes,
))
checks["Android 12 splash icon uses launcher identity"] = bool(re.search(
    r'<item\s+name="android:windowSplashScreenAnimatedIcon"[^>]*>@mipmap/ic_launcher</item>',
    themes,
))
splash = ET.parse(R / "app/src/main/res/drawable/splash_background.xml").getroot()
splash_text = read("app/src/main/res/drawable/splash_background.xml")
checks["Legacy splash uses branded dark background"] = "@color/manga_virus_icon_background" in splash_text
checks["Legacy splash uses full approved logo"] = "@drawable/manga_virus_logo" in splash_text

logo = R / "app/src/main/res/drawable-nodpi/manga_virus_logo.webp"
logo_data = logo.read_bytes() if logo.is_file() else b""
checks["Full logo is a valid WebP container"] = len(logo_data) > 10_000 and logo_data[:4] == b"RIFF" and logo_data[8:12] == b"WEBP"

legacy_sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
adaptive_sizes = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
for density, size in legacy_sizes.items():
    base = R / f"app/src/main/res/mipmap-{density}"
    for name in ("ic_launcher.png", "ic_launcher_round.png"):
        meta = png_meta(base / name) if (base / name).is_file() else None
        checks[f"{density} {name} dimensions"] = bool(meta) and meta[:2] == (size, size)

for density, size in adaptive_sizes.items():
    base = R / f"app/src/main/res/mipmap-{density}"
    for name in ("ic_launcher_foreground.png", "ic_launcher_monochrome.png"):
        path = base / name
        meta = png_meta(path) if path.is_file() else None
        checks[f"{density} {name} adaptive dimensions"] = bool(meta) and meta[:2] == (size, size)
        checks[f"{density} {name} has RGBA alpha"] = bool(meta) and meta[2:4] == (8, 6)
        corners = alpha_corners(path) if path.is_file() else None
        checks[f"{density} {name} transparent corners"] = corners == (0, 0, 0, 0)

for rel in (
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-ar/strings.xml",
    "app/src/dev/res/values/strings.xml",
    "app/src/dev/res/values-ar/strings.xml",
    "app/src/main/res/values/themes.xml",
    "app/src/main/res/values/manga_virus_branding.xml",
    "app/src/main/res/drawable/splash_background.xml",
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
    "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
    "app/src/debug/res/mipmap-anydpi-v26/ic_launcher.xml",
    "app/src/debug/res/mipmap-anydpi-v26/ic_launcher_round.xml",
    "app/src/devDebug/res/mipmap-anydpi-v26/ic_launcher.xml",
    "app/src/devDebug/res/mipmap-anydpi-v26/ic_launcher_round.xml",
):
    try:
        ET.parse(R / rel)
        checks[f"XML parses: {rel}"] = True
    except Exception:
        checks[f"XML parses: {rel}"] = False

workflow = read(".github/workflows/quality_gate.yml")
checks["V17 branding QA is part of GitHub Actions"] = "qa/v17/validate_manga_virus_branding.py" in workflow
checks["Existing V16 quality gate remains"] = "qa/v16/validate_crash_hardening.py" in workflow

for label, ok in checks.items():
    print(("PASS " if ok else "FAIL ") + label)

failed = [label for label, ok in checks.items() if not ok]
if failed:
    raise SystemExit(f"MANGA VIRUS V17 branding QA failed: {len(failed)} check(s)")
print(f"PASS {len(checks)} MANGA VIRUS V17 branding checks")
