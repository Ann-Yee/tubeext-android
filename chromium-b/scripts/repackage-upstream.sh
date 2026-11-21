#!/usr/bin/env bash
set -euo pipefail
VERSION="2.0.7"
ASSET="Bare-${VERSION}-arm64-v8a.apk"
BASE="https://github.com/BareBrowser/bare-browser/releases/download/v${VERSION}"
mkdir -p dist _repack
echo "::group::Upstream source and release verification"
curl --fail --location --retry 3 --silent --show-error \
    -o "_repack/$ASSET" "$BASE/$ASSET"
curl --fail --location --retry 3 --silent --show-error \
    -o "_repack/SHA256SUMS.txt" "$BASE/SHA256SUMS.txt"
(cd _repack && sha256sum --check --status --ignore-missing SHA256SUMS.txt)
echo "Verified Bare upstream v$VERSION release hash"
echo "::endgroup::"

echo "::group::Detect upstream app"
"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging "_repack/$ASSET" | grep -E "^(package:|application-label|launchable-activity)" | head -n 12
echo "::endgroup::"
curl --fail --location --retry 3 --silent --show-error \
    -o "_repack/apktool.jar" \
    "https://github.com/iBotPeaches/Apktool/releases/download/v2.11.1/apktool_2.11.1.jar"
java -jar _repack/apktool.jar --version
echo "::group::Decode upstream"
java -Xmx5g -jar _repack/apktool.jar d -f -s "_repack/$ASSET" -o _repack/decoded
echo "::endgroup::"
head -c 1400 _repack/decoded/AndroidManifest.xml
echo
python3 chromium-b/tools/brand_prebuilt.py \
    _repack/decoded chromium-b/extension/icons/icon.png \
    --version-code "$((900100000 + ${GITHUB_RUN_NUMBER:-1}))"
echo "::group::Rebuild Android resources"
# Bare Chromium 157 uses new API-37 Android service/zygote attributes.
# Apktool's default bundled API-35 framework cannot resolve them.
ANDROID37="$ANDROID_HOME/platforms/android-37.0/android.jar"
test -s "$ANDROID37" || { echo "::error::Android API37 framework is not installed"; exit 5; }
mkdir -p _repack/framework
if unzip -l "$ANDROID37" | grep -q 'resources.arsc'; then
  cp "$ANDROID37" _repack/framework/1.apk
else
  echo "::error::API37 android.jar has no framework resources.arsc; cannot rebuild safely."
  exit 5
fi
java -Xmx6g -jar _repack/apktool.jar b -p _repack/framework _repack/decoded -o _repack/tubeext-unsigned.apk
echo "::endgroup::"
# Build a tiny Android Activity, not a WebView. It routes launcher taps to
# YouTube inside the actual Chromium browsing activity/profile.
mkdir -p _repack/launcher-classes _repack/launcher-dex
javac -source 8 -target 8 \
  -cp "$ANDROID_HOME/platforms/android-37.0/android.jar" \
  -d _repack/launcher-classes \
  chromium-b/native/YouTubeLauncherActivity.java
"$ANDROID_HOME/build-tools/36.0.0/d8" \
  --min-api 29 \
  --lib "$ANDROID_HOME/platforms/android-37.0/android.jar" \
  --output _repack/launcher-dex \
  _repack/launcher-classes/app/tubeext/chromium/YouTubeLauncherActivity.class
test -s _repack/launcher-dex/classes.dex
cp _repack/launcher-dex/classes.dex _repack/classes3.dex
(cd _repack && zip -q -u tubeext-unsigned.apk classes3.dex)
echo "YouTube entry Activity compiled into classes3.dex"
"$ANDROID_HOME/build-tools/36.0.0/zipalign" -P 16 -f -v 4 \
    _repack/tubeext-unsigned.apk _repack/tubeext-aligned.apk >/dev/null
echo "::group::Sign package using permanent TubeExt private key"
python3 - <<'PY'
import pathlib,os
d=pathlib.Path(os.environ["TUBEEXT_SIGNING_DIR"])
data=dict(line.split("=",1) for line in (d/"signing.properties").read_text().splitlines()
          if "=" in line and not line.startswith("#"))
for key, file in [("storePassword","store-pass"),("keyPassword","key-pass")]:
    (d/file).write_text(data[key],encoding="utf-8")
    (d/file).chmod(0o600)
pathlib.Path("_repack/key-alias.txt").write_text(data["keyAlias"])
PY
ALIAS="$(cat _repack/key-alias.txt)"
"$ANDROID_HOME/build-tools/36.0.0/apksigner" sign \
  --ks "$TUBEEXT_SIGNING_DIR/release.p12" \
  --ks-type PKCS12 --ks-key-alias "$ALIAS" \
  --ks-pass "file:$TUBEEXT_SIGNING_DIR/store-pass" \
  --key-pass "file:$TUBEEXT_SIGNING_DIR/key-pass" \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out dist/TubeExt-Chromium-B-experimental-arm64.apk \
  _repack/tubeext-aligned.apk
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs \
    dist/TubeExt-Chromium-B-experimental-arm64.apk >_repack/apk-signer-report.txt
grep "certificate SHA-256 digest" _repack/apk-signer-report.txt
EXPECTED="BB662AE443357D13E860346205B885E670984108667666B3CDBA4896FF66057B"
ACTUAL="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' _repack/apk-signer-report.txt | head -n1 | tr '[:lower:]' '[:upper:]')"
if [[ "$ACTUAL" != "$EXPECTED" ]]; then
    echo "::error::Unexpected TubeExt signing identity"; exit 3
fi
echo "::endgroup::"
"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging \
    dist/TubeExt-Chromium-B-experimental-arm64.apk | grep -E "^(package:|application-label|launchable-activity)" | head -n 12
du -h dist/*.apk
echo "::warning::LEGACY/QUARANTINED APK. Signatures and zipalign do not establish Chromium runtime compatibility."
echo "::warning::No APK artifact will be published. Full Chromium source compile and ARM64 testing are required."
