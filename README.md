# TubeExt Android 0.2.0-alpha

Android YouTube-focused GeckoView browser supporting **Mozilla-signed Firefox XPI WebExtensions**.

## Improvements
- Both navigation and utility bars at bottom. Window-inset aware status and navigation spacing.
- Native searchable Mozilla AMO plugin market (Mozilla API v5, Android extension results): search, browse and install a signed XPI into this app. The Mozilla website alone **does not** install into GeckoView; use the native market Install button.
- Improved plugin settings: waits for AddonManagerDelegate.onReady (extension metadata) and displays settings in its own GeckoSession overlay without replacing the YouTube page; plugin action popup fallback, WebExtension.TabDelegate handles options and tabs.
- File Manager import via Storage Access Framework, copies content:// to a temporary file:// XPI before invoking GeckoView installer; also accepts HTTPS XPI links.
- Larger plugin popup panel; supports enable/disable/uninstall.

## Limitations
- Chrome .crx and unpacked unsigned extensions are NOT supported by production GeckoView.
- A successful APK compile does not prove that all third-party plugins work, nor that Google sign-in works in embedded browsers. Device tests required.
- The CI produces ephemeral debug-signed APKs; Android may reject updating previously installed builds due to differing signatures. In that case uninstall the old build **after exporting any extension settings**, then install the new APK.
- This build does not support Picture-in-Picture or native background playback.

## Build
GitHub Actions workflow on push to main, downloads at Actions -> Cloud Build APK -> Artifacts -> TubeExt-debug-apk.

## Sources
- https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtensionController.html
- https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebExtension.TabDelegate.html
- https://mozilla.github.io/addons-server/topics/api/addons.html
