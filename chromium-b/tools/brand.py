#!/usr/bin/env python3
"""Conservative TubeExt Chromium branding of upstream source resources.

Will not change package IDs/Java namespaces; those are set by Chromium GN.
Only replaces literal display labels in the app's resource files. Detailed
launcher icon replacement will be implemented after the first native build.
"""
import pathlib,sys
src=pathlib.Path(sys.argv[1])
icon=pathlib.Path(sys.argv[2])
assert src.is_dir() and icon.is_file()
# Bare patch resources already carry the upstream name. Restrict to exact XML
# user-visible labels, not library symbols, filenames or copyright notices.
found=0
for xml in (src / "chrome/android/java/res").rglob("*.xml"):
    if not xml.is_file():continue
    try: data=xml.read_text(encoding="utf-8")
    except UnicodeDecodeError:continue
    edited=data.replace(">Bare Browser<",">TubeExt Chromium<")
    if edited!=data:
        xml.write_text(edited,encoding="utf-8")
        found+=1
print("Updated app labels in",found,"XML files; original logo supplied for future native resource overlay.")
