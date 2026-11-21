#!/usr/bin/env python3
"""Compare Chromium Android upstream APK to the altered APK *by content*.

We intentionally do not call an APK "stable" merely because it was signed.
Print exact counts and hashes of changed resources/dex/native libraries.
"""
import collections
import hashlib
import pathlib
import sys
import zipfile

original=pathlib.Path(sys.argv[1])
modified=pathlib.Path(sys.argv[2])
out=pathlib.Path(sys.argv[3])
def category(p):
    if p.startswith("lib/"): return "native libraries"
    if p.startswith("res/"): return "Android compiled resources"
    if p.startswith("assets/"): return "Chromium assets"
    if p.endswith(".dex"): return "Java dex"
    if p=="resources.arsc": return "binary resource table"
    if p=="AndroidManifest.xml": return "compiled manifest"
    return "misc"

lines=["# APK repack integrity comparison", "", f"Original: {original.stat().st_size:,} bytes",
       f"Modified: {modified.stat().st_size:,} bytes", ""]
with zipfile.ZipFile(original) as a, zipfile.ZipFile(modified) as b:
    an={x.filename:x for x in a.infolist()}
    bn={x.filename:x for x in b.infolist()}
    common=sorted(set(an)&set(b))
    missing=sorted(set(an)-set(b))
    added=sorted(set(bn)-set(a))
    bad=[]
    same=[]
    diff=collections.Counter()
    for name in common:
        x=an[name]; y=bn[name]
        if x.CRC==y.CRC and x.file_size==y.file_size:
            same.append(name)
        else:
            bad.append(name)
            diff[category(name)]+=1
    lines += [f"Original entries: {len(an)}",f"Modified entries: {len(bn)}",
              f"Same payload: {len(same)}", f"Different payload: {len(bad)}",
              f"Missing: {len(missing)}", f"Added: {len(added)}", "",
              "## Different by category"]
    lines += [f"- {key}: {num}" for key,num in diff.most_common()]
    lines += ["", "## Resource and native library fingerprints"]
    for name in ["resources.arsc","AndroidManifest.xml","lib/arm64-v8a/libchrome.so",
                 "assets/resources.pak+40064988+","classes.dex","classes2.dex"]:
        if name in an and name in bn:
            x,y=an[name],bn[name]
            lines.append(f"- `{name}`: original CRC {x.CRC:08x} size {x.file_size:,} compression {x.compress_type}; modified CRC {y.CRC:08x} size {y.file_size:,} compression {y.compress_type} (MATCH={x.CRC==y.CRC and x.file_size==y.file_size})")
    lines += ["", "## Added entries", *["- "+p for p in added[:100]],
              "", "## Missing entries", *["- "+p for p in missing[:100]],
              "", "## Modified entries, sample", *["- "+p for p in bad[:150]]]
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines)+"\n",encoding="utf-8")
    print("\n".join(lines[:80]))
    # Don't mask differences in compiled Chromium resources: they can encode
    # native resource IDs, and APKTool has not been validated to preserve them.
    if diff["Android compiled resources"]>10:
        print("BLOCKING: many compiled Android resources altered by repackaging")
        sys.exit(12)
    if diff["native libraries"]>0:
        print("BLOCKING: native library bytes changed")
        sys.exit(13)
    if diff["Chromium assets"]>0:
        print("BLOCKING: Chromium assets payloads changed")
        sys.exit(14)
