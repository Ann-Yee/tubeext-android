#!/usr/bin/env bash
# Full Chromium build for a large Ubuntu x86-64 cloud machine, not a free small runner.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="$ROOT/dist"
mkdir -p "$OUT"
: "${CHROMIUM_WORKDIR:=$ROOT/_chromium-build}"
mkdir -p "$CHROMIUM_WORKDIR"
MEM_KIB="$(awk '/MemTotal/ {print $2}' /proc/meminfo)"
FREE_KIB="$(df -Pk "$CHROMIUM_WORKDIR" | awk 'NR==2 {print $4}')"
if (( MEM_KIB < 32000000 || FREE_KIB < 130000000 )); then
  echo "Chromium requires >=32 GB RAM and >=130 GB free disk." >&2
  echo "RAM KiB: $MEM_KIB Disk KiB: $FREE_KIB" >&2
  exit 2
fi

UPSTREAM="$CHROMIUM_WORKDIR/bare"
if [[ ! -d "$UPSTREAM/.git" ]]; then
  git clone --depth 1 https://github.com/BareBrowser/bare-browser.git "$UPSTREAM"
fi
# Pin Chromium to the build guide's known-tested revision to match Bare patches.
CHROMIUM_COMMIT="$(sed -n 's/^- Chromium commit: \\([0-9a-f]\\{40\\}\\).*/\\1/p' "$UPSTREAM/BUILDING.md" | head -n 1)"
if [[ ! "$CHROMIUM_COMMIT" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Could not read the pinned Chromium revision from Bare BUILDING.md" >&2
  exit 3
fi
echo "Pinning Chromium $CHROMIUM_COMMIT"
TOOLCHAIN="$CHROMIUM_WORKDIR/depot_tools"
if [[ ! -d "$TOOLCHAIN/.git" ]]; then
  git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git "$TOOLCHAIN"
fi
export PATH="$TOOLCHAIN:$PATH"
mkdir -p "$CHROMIUM_WORKDIR/chromium"
cd "$CHROMIUM_WORKDIR/chromium"
if [[ ! -d src/.git ]]; then
  fetch --nohooks android
fi
cd src
git checkout "$CHROMIUM_COMMIT"
gclient sync -D --force --reset
./build/install-build-deps.sh --android
sudo apt-get update && sudo apt-get install -y python3-pil

# Applying the upstream patch series once; fail on changed/unmatched Chromium.
if [[ ! -e "$CHROMIUM_WORKDIR/.bare-patches-applied" ]]; then
  git apply --check "$UPSTREAM"/patches/*.patch
  git apply --index "$UPSTREAM"/patches/*.patch
  touch "$CHROMIUM_WORKDIR/.bare-patches-applied"
fi
mkdir -p chrome/browser/resources/bare
cp "$UPSTREAM/third_party/ublock_origin/uBlockOrigin-1.75.0.crx" \
  chrome/browser/resources/bare/ublock_origin.crx

# Name/logo resources in Chromium vary by revision. The branding helper is
# conservative and refuses to overwrite unrecognised source resources.
python3 "$ROOT/chromium-b/tools/brand.py" \
    "$CHROMIUM_WORKDIR/chromium/src" \
    "$ROOT/chromium-b/extension/icons/icon.png"

# Pinned independent app ID; cannot collide with GeckoView app.tubeext.
# Next release MUST increase android_override_version_code with the SAME sign key.
mkdir -p out/TubeExt
cat >out/TubeExt/args.gn <<'GNARGS'
target_os = "android"
target_cpu = "arm64"
is_desktop_android = true
is_official_build = true
is_debug = false
is_java_debug = false
is_component_build = false
dcheck_always_on = false
android_static_analysis = "off"
symbol_level = 0
blink_symbol_level = 0
v8_symbol_level = 0
use_remoteexec = false
use_reclient = false
proprietary_codecs = true
ffmpeg_branding = "Chrome"
use_mlkit_for_aicore = false
enable_glic_internal_resources = false
enable_reporting = false
enable_service_discovery = false
enable_mdns = false
enable_arcore = false
enable_cardboard = false
enable_openxr = false
disable_fieldtrial_testing_config = true
chrome_public_manifest_package = "app.tubeext.chromium"
android_override_version_code = "900100001"
android_override_version_name = "0.1.0-chromium-b"
GNARGS

gn gen out/TubeExt
autoninja -C out/TubeExt chrome_public_apk
cp out/TubeExt/apks/ChromePublic.apk "$OUT/TubeExt-Chromium-B-unsigned.apk"
echo "Built $OUT/TubeExt-Chromium-B-unsigned.apk"
echo "IMPORTANT: this native build is not a signed, device-tested production release."
