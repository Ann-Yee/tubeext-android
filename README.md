# TubeExt 0.3 — YouTube-focused GeckoView Android Browser

This project treats YouTube as a dedicated home tab **inside a real Firefox GeckoView browser**.

## Fixes implemented
- Real retained GeckoSessions for browser tabs, plugin tabs, options pages and popups.
- Handles WebExtension.TabDelegate.onNewTab, onOpenOptionsPage, SessionTabDelegate for tabs.update and tabs.remove, and navigation.onNewSession for window.open.
- Main view switches between sessions (instead of the old temporary overlay with a blank tab).
- Youtube shortcuts and app controls remain at bottom; browser tabs show a real URL/search field.
- Plugin settings open as full browser tabs with moz-extension:// support when metadata gives an optionsPageUrl.
- Extension action buttons trigger popup rendering in a real tab. No fake popup layer.
- Native Mozilla add-on search/installation remains available; AMO detail links open in-app.
- Signed Firefox XPI import from files and HTTPS URL supported; Chrome CRX is NOT supported.
- Uses the user-provided YouTube logo for the launcher icon (downscaled original).

## How to use
At bottom: **浏览器** → enter URL / plugin site; **标签** switches and closes browser tabs.
**插件** → installed extensions → select → **插件设置**. If no optionsPageUrl exists, TubeExt falls back to the extension action button or shows diagnostics.
**插件** → plugin market for Mozilla-index search and XPI install.

## Limitations / verification
Compilation by GitHub Actions is not proof of real-device functionality. Test: YouTube playback, Google login, Immersive Translate settings, XPI imports, store, web extension popup, links opening new tabs. YouTube/google may refuse Google login in an embedded browser.
No Chrome CRX runtime. Other advanced features (downloads, media PiP, background playback) not guaranteed.
GitHub Actions outputs a debug-signed APK; back up app data before uninstalling if debug signing changes.
