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
java -Xmx6g -jar _repack/apktool.jar b _repack/decoded -o _repack/tubeext-unsigned.apk
echo "::endgroup::"
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
echo "Chromium-B binary experiment APK created. NOT confirmed to function on Android yet."
