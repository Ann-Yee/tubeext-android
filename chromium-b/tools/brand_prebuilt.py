#!/usr/bin/env python3
"""Experimental APK repackage only. NOT compiling Chromium source.

The upstream BSD-3-Clause license/NOTICE remain in the source repository.
This script only changes application identity and launcher branding.
"""
import argparse
import pathlib
import re
import sys

def replace_app_attribute(manifest: str, attribute: str, value: str) -> str:
    start=manifest.find("<application")
    if start<0:
        raise RuntimeError("No <application> tag in decoded APK")
    end=manifest.find(">",start)
    if end<0:
        raise RuntimeError("Malformed application tag")
    before=manifest[start:end]
    key="android:"+attribute
    if re.search(r'\b'+re.escape(key)+r'="[^"]*"', before):
        after=re.sub(r'\b'+re.escape(key)+r'="[^"]*"',key+'="'+value+'"',before,count=1)
    else:
        after=before+" "+key+'="'+value+'"'
    return manifest[:start]+after+manifest[end:]

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument("decoded",type=pathlib.Path)
    parser.add_argument("logo",type=pathlib.Path)
    parser.add_argument("--package",default="app.tubeext.chromium")
    parser.add_argument("--version-code",type=int,default=900100001)
    args=parser.parse_args()
    assert re.fullmatch(r"[a-z][a-z0-9_.]+",args.package)
    f=args.decoded/"AndroidManifest.xml"
    data=f.read_text(encoding="utf-8")
    m=re.search(r'<manifest\b[^>]*\bpackage="([^"]+)"',data)
    if not m:
        raise RuntimeError("Unable to find root manifest package")
    old=m.group(1)
    if old != "org.barebrowser":
        raise RuntimeError("Wrong upstream manifest package: "+old)
    data=data[:m.start()]+data[m.start():m.end()].replace('package="'+old+'"','package="'+args.package+'"',1)+data[m.end():]
    data=replace_app_attribute(data,"label","TubeExt Chromium")
    data=replace_app_attribute(data,"icon","@drawable/tubeext_launcher")
    data=replace_app_attribute(data,"roundIcon","@drawable/tubeext_launcher")
    data=re.sub(r'\bandroid:authorities="([^"]+)"',
        lambda m:'android:authorities="'+m.group(1).replace(old,args.package)+'"',data)
    # Only replace permission *names* that were already namespaced to Bare.
    for attr in ("android:permission","android:readPermission","android:writePermission"):
        data=re.sub(r'('+attr+r'=")'+re.escape(old)+r'(?=[."])',r'\1'+args.package,data)
    data=re.sub(r'(\bandroid:versionCode=")\d+(")',lambda m:m.group(1)+str(args.version_code)+m.group(2),data,count=1)
    data=re.sub(r'(\bandroid:versionName=")[^"]*(")',r'\g<1>0.1.0-chromium-b\2',data,count=1)
    f.write_text(data,encoding="utf-8")
    out=args.decoded/"res/drawable-nodpi/tubeext_launcher.png"
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_bytes(args.logo.read_bytes())
    # Retain upstream license & copyright notice in dedicated asset.
    assets=args.decoded/"assets/tubeext"
    assets.mkdir(parents=True,exist_ok=True)
    (assets/"UPSTREAM.txt").write_text(
        "TubeExt Chromium is an independent derivative of the open-source "
        "Bare Browser (https://github.com/BareBrowser/bare-browser). "
        "Original Bare Browser BSD-3-Clause license and NOTICE are available in "
        "that repository. This binary repackage is not endorsed by Bare Browser.\n",encoding="utf-8")
    print("UPSTREAM_PKG="+old)
    print("TUBEEXT_PKG="+args.package)
    print("BRANDING_OK=1")
    print("NOTE: chromium native components may depend on the upstream app ID; "
          "a decoded/repacked APK is not yet considered device tested.")

if __name__=="__main__":
    main()
