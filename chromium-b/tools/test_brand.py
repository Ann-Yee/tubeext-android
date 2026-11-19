#!/usr/bin/env python3
"""Offline launcher icon/name source-level regression test for native Chromium build."""
import pathlib
import subprocess
import sys
import tempfile
from PIL import Image

root = pathlib.Path(__file__).resolve().parents[1]
icon = root / "extension/icons/icon.png"
assert icon.is_file()
with tempfile.TemporaryDirectory(prefix="tubeext-brand-test-") as tmp:
    chromium = pathlib.Path(tmp)
    appres = chromium / "chrome/android/java/res_chromium_base"
    sizes = {"mdpi":48,"hdpi":72,"xhdpi":96,"xxhdpi":144,"xxxhdpi":192}
    for density, size in sizes.items():
        d=appres / ("mipmap-"+density)
        d.mkdir(parents=True,exist_ok=True)
        Image.new("RGBA",(size,size),(0,0,0,255)).save(d/"app_icon.png")
        Image.new("RGBA",(size,size),(0,0,0,255)).save(d/"layered_app_icon.png")
    values=appres / "values"
    values.mkdir(parents=True)
    label=values / "channel_constants.xml"
    label.write_text('<resources><string name="app_name">Chromium</string></resources>',encoding="utf-8")
    subprocess.run([sys.executable,str(root / "tools/brand.py"),str(chromium),str(icon)],check=True)
    assert "TubeExt Chromium" in label.read_text(), "Native app label not updated"
    for density, size in sizes.items():
        with Image.open(appres / ("mipmap-"+density) / "app_icon.png") as image:
            assert image.size == (size,size), (density,image.size)
            assert image.getpixel((size//2,size//2))[:3] != (0,0,0), "Source logo absent"
print("TubeExt launcher label and icon resource overlay test OK")
