# TubeExt — Android YouTube client with Firefox WebExtensions

Source is a **prototype**. No claim of APK/device-tested functionality until GitHub Actions build and Android testing succeed.

## Features implemented in source
- GeckoView-powered dedicated YouTube tab and bottom navigation
- Firefox WebExtensions manager: install Mozilla-signed XPI, list, disable, enable, uninstall, and popup controls (best effort)
- One-tap URLs for uBlock Origin and Immersive Translate Firefox editions
- Android YouTube deep links and fullscreen UI
- Local signed XPI file import

**This does not run Chrome .crx directly and is not a fork of Edge Canary.** Same-name Firefox extensions may differ from the Chrome variants. Popup, YouTube login and add-on behavior need device testing. YouTube login can be refused in embedded browsers. Background play, Chromecast and PiP are not implemented.

## Cloud compile, no Android Studio
The push-to-main workflow automatically uses the GitHub Ubuntu runner to build a debug-signed arm64 APK. See repository Actions → Cloud Build APK → latest run → Artifacts → TubeExt-debug-apk; unzip and install app-debug.apk.

If a workflow fails, open its Compile APK step for the exact cause. For long-term distribution use release signing and conduct security review. Install only trusted XPI packages and review their permissions.

## Source references
- https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html
- https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtensionController.html
- https://addons.mozilla.org/android/addon/ublock-origin/
- https://addons.mozilla.org/android/addon/immersive-translate/
