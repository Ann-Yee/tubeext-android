#!/usr/bin/env python3
"""TubeExt native Chromium Android app name and launcher icon overlay.

Modifies only known Chromium Android app resources, never any extension binaries.
This runs *after* the upstream Bare patch series and before gn gen.
"""
import pathlib
import re
import sys
from PIL import Image

src = pathlib.Path(sys.argv[1])
icon = pathlib.Path(sys.argv[2])
if not src.is_dir() or not icon.is_file():
    sys.exit("ERROR: missing Chromium tree or supplied TubeExt icon")

res = src / "chrome/android/java/res_chromium_base"
if not res.is_dir():
    sys.exit("ERROR: Chromium app resources moved; pin a compatible upstream")

sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
logo = Image.open(icon).convert("RGBA")
updated = 0
for density, size in sizes.items():
    folder = res / ("mipmap-" + density)
    for basename in ("app_icon.png", "layered_app_icon.png"):
        target = folder / basename
        if target.exists():
            # Preserve the source artwork exactly (letterboxing is already part
            # of the user's uploaded graphic), only scale to Android density.
            logo.resize((size, size), Image.Resampling.LANCZOS).save(target)
            updated += 1
if updated < 5:
    sys.exit("ERROR: unable to replace Chromium launcher icon resources")

labels = 0
channel = res / "values/channel_constants.xml"
if channel.exists():
    data = channel.read_text(encoding="utf-8")
    data2, count = re.subn(
        r'(<string[^>]*name="app_name"[^>]*>)[^<]*(</string>)',
        r'\\1TubeExt Chromium\\2',
        data,
        flags=re.IGNORECASE,
    )
    if count:
        channel.write_text(data2, encoding="utf-8")
        labels += count
    else:
        print("WARNING: app_name not in channel_constants.xml")
for parent in [src / "chrome/android/java/res", src / "chrome/android/java/res_chromium"]:
    if not parent.is_dir():
        continue
    for xml in parent.rglob("*.xml"):
        try:
            data = xml.read_text(encoding="utf-8")
        except UnicodeError:
            continue
        modified = data.replace(">Bare Browser<", ">TubeExt Chromium<")
        if modified != data:
            xml.write_text(modified, encoding="utf-8")
            labels += 1
print(f"TubeExt Chromium: replaced {updated} launcher icon resources; {labels} app label values")
