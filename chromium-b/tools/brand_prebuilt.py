#!/usr/bin/env python3
"""Experimental APK repackaging, not Chromium source compilation."""
import argparse
import pathlib
import re

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("decoded",type=pathlib.Path)
    ap.add_argument("logo",type=pathlib.Path)
    ap.add_argument("--package",default="app.tubeext.chromium")
    ap.add_argument("--version-code",type=int,default=900100001)
    args=ap.parse_args()
    if not re.fullmatch(r"[a-z][a-z0-9_.]+",args.package):
        raise RuntimeError("Invalid Android application ID")
    f=args.decoded/"AndroidManifest.xml"
    data=f.read_text(encoding="utf-8")
    matches=re.findall(r'xmlns:([A-Za-z0-9_]+)="http://schemas.android.com/apk/res/android"',data)
    if len(matches)!=1: raise RuntimeError("Missing/ambiguous Android manifest namespace")
    ns=matches[0]+":"
    print("Android namespace:",ns)
    m=re.search(r'<manifest\b[^>]*\bpackage="([^"]+)"',data)
    if not m or m.group(1)!="org.barebrowser":
        raise RuntimeError("Unexpected upstream APK package")
    old=m.group(1)
    data=data[:m.start()]+data[m.start():m.end()].replace('package="'+old+'"','package="'+args.package+'"',1)+data[m.end():]

    def app_attr(value, replacement):
        nonlocal data
        start=data.find("<application")
        end=data.find(">",start)
        if start<0 or end<0:raise RuntimeError("Invalid decoded manifest application element")
        tag=data[start:end]
        attr=re.escape(ns)+re.escape(value)
        if re.search(attr+r'="[^"]*"',tag):
            tag=re.sub(attr+r'="[^"]*"',ns+value+'="'+replacement+'"',tag,count=1)
        else:
            tag+=' '+ns+value+'="'+replacement+'"'
        data=data[:start]+tag+data[end:]

    app_attr("label","TubeExt Chromium")
    app_attr("icon","@drawable/tubeext_launcher")
    app_attr("roundIcon","@drawable/tubeext_launcher")

    authority=re.escape(ns)+r'authorities="([^"]+)"'
    data=re.sub(authority,lambda m:ns+'authorities="'+m.group(1).replace(old,args.package)+'"',data)

    for attr in ("permission","readPermission","writePermission"):
        pattern=r'('+re.escape(ns+attr)+r'=")'+re.escape(old)+r'(?=[."])'
        data=re.sub(pattern,lambda m:m.group(1)+args.package,data)
    data=re.sub(r'('+re.escape(ns)+r'versionCode=")\d+(")',
        lambda m:m.group(1)+str(args.version_code)+m.group(2),data,count=1)
    data=re.sub(r'('+re.escape(ns)+r'versionName=")[^"]*(")',
        lambda m:m.group(1)+"0.1.0-chromium-b"+m.group(2),data,count=1)
    f.write_text(data,encoding="utf-8")

    out=args.decoded/"res/drawable-nodpi/tubeext_launcher.png"
    out.parent.mkdir(parents=True,exist_ok=True)
    out.write_bytes(args.logo.read_bytes())
    asset=args.decoded/"assets/tubeext"
    asset.mkdir(parents=True,exist_ok=True)
    (asset/"UPSTREAM.txt").write_text(
        "TubeExt Chromium is an independent experimental derivative of Bare Browser. "
        "Its original source and BSD-3-Clause license/NOTICE: "
        "https://github.com/BareBrowser/bare-browser. Not endorsed by Bare Browser.\n",
        encoding="utf-8")
    print("UPSTREAM_PKG="+old)
    print("TUBEEXT_PKG="+args.package)
    print("BRANDING_OK=1")
    print("WARNING: Repacked Chromium package requires real-device testing.")

if __name__=="__main__":main()
