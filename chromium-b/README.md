# TubeExt Chromium B — real CRX architecture (development)

Status: **Experimental TubeExt-branded Chromium B APK built and signed from the official Bare Browser 2.0.7 binary.** This is a derivative *repackage*, **not** a full compile of the Chromium source and **not** a verified working release. The full source-build script remains unexecuted without >=32GB RAM and >=100GB build storage. See the test notes below.

This is a separate branch (`chromium-b`) so the installed stable GeckoView version remains unchanged.

## Why Bare Browser as upstream
Bare Browser is an open-source Chromium Desktop Android patch set that supports Chrome Web Store, CRX, unpacked extensions, action popup/options, mobile bottom toolbar and background video. It is based on pinned Chromium 157. See:
- https://github.com/BareBrowser/bare-browser
- https://github.com/BareBrowser/bare-browser/blob/main/docs/build-from-source.md
- https://github.com/BareBrowser/bare-browser/blob/main/BUILDING.md

**Upstream uses the Bare Browser brand and signing key. We cannot re-sign the upstream APK and call it our app.** A true TubeExt Chromium APK has to be built from the Chromium source + Bare's published patches + our own app ID and branding, then signed using our own private key.

## Implemented in this branch
- `chromium-b/extension/`: YouTube home screen, Shorts, subscriptions, profile, search, playlists, one-click Chrome Web Store and `chrome://extensions`.
- New Tab override via Manifest V3; action popup and working options page.
- Optional YouTube opening on browser launch, stored in chrome.storage.local; default is OFF.
- Reproducible extension linter and ZIP/CRX3 packer (a local test signing key is generated if no key is supplied; production should use persistent key).
- GitHub Actions builds artifact `chromium-b-home-extension` on push, no Android Studio.
- `chromium-b/scripts/build-on-linux.sh`: source build recipe with unique app ID, pinned upstream Chromium, Bare patches and project branding.

## To test Chrome extension support immediately, without a custom APK
1. Install upstream official Bare Browser arm64 APK from https://github.com/BareBrowser/bare-browser/releases/latest (Android 10+).
2. Its Extensions screen supports CRX and unpacked extensions, including the original QuickVideo v0.7 CRX.
3. From Actions artifacts download `TubeExt-YouTube-Home-unpacked.zip`, extract, and select `Load unpacked` on the extracted folder. Or try the developer-signed `.crx` using `Install CRX`. **A developer signed CRX may be refused by some Chromium policies**.
4. The installed new-tab home extension supplies the YouTube landing UI.

These steps test the same extension mechanism that the eventual native TubeExt Chromium app will inherit; **Bare itself is not yet a TubeExt-branded app**.

## Native APK build requirements
The full Chromium checkout/compile needs >=32 GiB RAM and >=130 GiB free disk on a Linux x86-64 cloud runner. Typical free GitHub runners do not have this capacity; the workflow's native APK job is intentionally gated by a custom runner label and repository variable so it does not endlessly queue or misrepresent validation as an APK build.

To enable native cloud compilation: register a sufficiently large self-hosted (cloud) GitHub Actions runner with labels `linux`, `x64`, `chromium-build`, then set repository Actions variable `TUBEEXT_CHROMIUM_BUILDER_READY=true` and run the workflow manually. This does not require Android Studio, but the cloud machine may cost money.

The native job currently produces an unsigned APK for build feasibility; signing/release identity and the final fully customized UI require follow-up. It uses a separate test package `app.tubeext.chromium` so it cannot conflict with your installed `app.tubeext` (GeckoView). Do not install unknown signed APKs without confirming origin.

## Known uncertainties
- `chrome_url_overrides.newtab` and extension actions should work in the Chromium desktop extension environment, but **require testing in the actual Android build**.
- The launcher icon replacement is best-effort until confirmed against the pinned source resources.
- YouTube/Google login, Shorts, media controls, background play and CRX plugins must be tested on a physical Android device.
- Original Chrome CRX has its own API compatibility constraints even in an extension-capable Chromium fork.
- Upstream license: BSD-3-Clause; preserve its attribution when building. Do not misrepresent your build as official Bare Browser.

## Experimental binary APK, November 19 2026

- Signed build: https://github.com/Ann-Yee/tubeext-android/actions/runs/44497314116
- Android package: `app.tubeext.chromium`, separate from GeckoView `app.tubeext` and upstream Bare `org.barebrowser`.
- Installation artifacts were created using the existing protected TubeExt release signing secret; signature and package label verified in CI.
- This APK has the user YouTube icon and a small Java launcher routing home-screen taps to `https://m.youtube.com/` through Chromium's ACTION_VIEW handler. It inherits Chrome extension and CRX capability from Bare's Chromium engine.
- The preceding build was emulator smoke-tested. **It crashed under x86_64 emulator ARM native-bridge translation.** The **unmodified official Bare 2.0.7 APK crashed with the same SIGSEGV** in that emulator (control run https://github.com/Ann-Yee/tubeext-android/actions/runs/44496727294). Thus the emulator results cannot establish whether the repack works on a real ARM64 device. An Android ARM64 device test is needed.
- Repacking has known compatibility risks (Chromium process/package naming, native resource access, extensions). It must not be distributed as a stable product until actual device launch + YouTube video + Google login + CRX + extension options are tested.
- Proprietary branding and signing of the upstream Bare package are not claimed; this derivative maintains attribution to upstream.
