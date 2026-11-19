#!/usr/bin/env python3
"""Minimal offline validation for the TubeExt Chrome home extension."""
import json
import pathlib
import re
import sys

source = pathlib.Path(sys.argv[1])
manifest = json.loads((source / "manifest.json").read_text(encoding="utf-8"))
assert manifest["manifest_version"] == 3, "Manifest V3 required"
assert manifest["chrome_url_overrides"]["newtab"] == "newtab.html"
assert manifest["action"]["default_popup"] == "popup.html"
assert manifest["background"]["service_worker"] == "background.js"
assert set(manifest.get("permissions", [])) <= {"tabs", "storage"}, "Unexpected extension permissions"
for file in ["newtab.html", "popup.html", "options.html", "style.css",
             "home.js", "options.js", "background.js", "icons/icon.png"]:
    f = source / file
    assert f.is_file() and f.stat().st_size > 0, "Missing extension resource: " + file
for file in ["newtab.html", "popup.html", "options.html"]:
    src = (source / file).read_text(encoding="utf-8")
    assert "<script src=" in src and 'eval(' not in src, file + ": unsafe inline code"
    for match in re.findall(r'''(?:src|href)="([^"]+)"''', src):
        if match.startswith(("https:", "http:", "chrome:")):
            continue
        assert (source / match).is_file(), file + ": missing " + match
for file in ["home.js", "options.js", "background.js"]:
    data = (source / file).read_text(encoding="utf-8")
    assert not any(q in data for q in ["eval(", "new Function(", "XMLHttpRequest(", "fetch("]), "Unexpected dynamic code: "+file
print("TubeExt Chromium B extension: manifest and resources OK")
