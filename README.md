# TubeExt Android

YouTube-dedicated GeckoView browser with Firefox WebExtensions and add-on store.

## Updating without uninstalling (permanent APK signing)

Older APKs from 0.1 / 0.2 / 0.3 were each signed with **different ephemeral GitHub Actions debug keys**. This caused **INSTALL_FAILED_UPDATE_INCOMPATIBLE**. Because the original private signing keys were never preserved, **none of those APKs can be upgraded in place to the newly signed build**. You must uninstall the previous APK **one final time** (back up any plugin settings you can first). Future versions use a single release signing certificate.

New pipeline signs **release**, not **debug**, with a permanent private certificate. The package/application ID remains **app.tubeext**. Each Actions run automatically receives an increasing versionCode, so future released APKs from this workflow will update the installed app while preserving data.

### One-time secure configuration (required)

1. From this ChatGPT conversation, download `TubeExt-GitHub-Secret.txt` and `TubeExt-signing-backup.tar.gz`.
2. Open [repository Actions secrets](https://github.com/Ann-Yee/tubeext-android/settings/secrets/actions) → **New repository secret**.
3. Secret name: **TUBEEXT_SIGNING_BUNDLE**.
4. Secret value: entire single base64 line from `TubeExt-GitHub-Secret.txt`. Save.
5. Open **Actions → Cloud Build APK → Run workflow**. Download artifact `TubeExt-signed-release-apk` and install `app-release.apk`.

⚠️ Protect `TubeExt-signing-backup.tar.gz` as a private credential (it includes the private release key and passwords). Never add it or the .txt to this public GitHub repo; never send the secret into issue comments. Keep an offline backup. The CI checks that the signing certificate fingerprint remains constant: `BB:66:2A:E4:43:35:7D:13:E8:60:34:62:05:B8:85:E6:70:98:41:08:66:76:66:B3:CD:BA:48:96:FF:66:05:7B`.

No Android Studio or local compilation is required. **Until the GitHub secret is configured, Actions deliberately fails before compiling rather than generating yet another non-upgradable debug build.**

## Features
- YouTube default tab, Shorts/subscriptions/profile shortcuts.
- Full GeckoView browser, address bar/search, tabs, extension pages and marketplace.
- Firefox-signed XPI extensions: install via Mozilla catalog, local file or HTTPS.
- Chrome CRX is unsupported.
- Device testing of login/add-on compatibility is still required.

## Release status

Permanent signing has been configured in GitHub Actions by the repository owner. The next successful `Cloud Build APK` artifact is the signed release. Install only that artifact for future in-place updates.
