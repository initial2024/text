# OGPROFILE08 — OmniGate Android clone/work-profile and browser-security handoff

Date: 2026-11-06. Scope: `initial2024/text` / `work/omnigate-integrated-v05` / Draft PR #1.

## Status: SOURCE_ONLY; no signed new private APK or Android 16 E2E

- The latest user Library artifact independently found and hash-checked for this audit was **OmniGate-v1.3.2-GECKO-LANGUAGE-MIRROR-SIGNED-TEST.apk**, SHA-256 `2bbff5d7be4ee28929828c4b238f317a0470f78c0a8364e28920f4359cc6c691`. It predates the source changes below. It must **not** be labeled the newly fixed version.
- The APK contains six DEX files and the original compatibility data. Public GitHub builds validate Android source / class DEX / secret signing-certificate identity only; they are **not** a fully assembled, same-signer, user-installable updated APK.
- Do not overwrite the private APK's original DEX or package metadata, publish an APK or keystore to a public repository, or claim fixed package install without explicit binary checks and on-device E2E.

## What is actually implemented in the Draft PR

1. **Manual Gecko account isolation:** each provider/slot has a persisted distinct Gecko context ID for website cookies/storage. Original model 8787/WebDriver remains a separate owner; changes do not silently rotate platform accounts. A failed close retains a sticky fail-closed gate.
2. **Claude browser-only:** manual login and explicit account entry available before the website avatar appears. Gecko-native page-start/progress/stop and monotonic *base page load* duration never assert authenticated hydration.
3. **File upload:** Gecko FilePrompt -> system ACTION_OPEN_DOCUMENT -> bounded metadata check and explicit user confirmation -> current Gecko response. No injection or account-wide file access.
4. **File download:** consume the original Gecko WebResponse body, no replayed URL/cookies/POST. Explicit save confirmation, per-context app-private storage and download/delete/export UI; the latter is accessible without reopening the website.
5. **Permission boundary:** per-Gecko-context website permission requests; Android system capability escalation and hardware camera/microphone/location requests are currently **denied** in this experimental bridge, pending phone tests. An app's OS permission is not a sub-account scoped resource. Permissions can be revoked by selected Gecko context without deleting the other context's cookies.
6. **Native tracking protection:** default enabled for each persistent manual slot and guest. Per-slot manual compatibility toggle, which takes effect on the next explicit browser open. No UA/WebGL/canvas spoofing.
7. **Language:** Android per-app locale is synced to the existing GeckoRuntime's preferred locales. No automatic reload and no guarantee a site will choose that language over its own stored setting. Full application translation into English is not certified complete.
8. **Safe deletion:** guest's Gecko context and private downloads are retired on close. Explicit bulk account-space erasure also deletes that *selected* context's private download directory, while normal close preserves downloads and other slots. A tombstone prevents a cancelled late stream from repopulating a retired context.
9. **Android profile inspector:** displays only local user serial, associated profile count and a random local *app storage* marker, with public OS settings handoff. This does not provision users, work profiles or clones, does not grant DPC/device-owner privileges, and is not evidence that a website sees a different physical phone.

## Android truth and untested cases

- AOSP supports clone profiles, managed work profiles, and secondary users as distinct mechanisms; OEM cloning behavior varies. Website/device linkage is not prevented by cookie isolation. No zero-risk or no-detection promise.
- Real tests required: first-run personal vs cloned/work/secondary user storage, same-account login with isolated cookies, independent Android permissions/Files SAF and no cross-profile URI, 8787 listener conflict across users, and restart/switch safety.
- Real tests required: Claude login SSO compatibility/anti-embedded browser restrictions; page native entry immediate; browser site avatar eventual; real upload with one and multiple files, cancel/over-limit and account switch during file picker.
- Real tests required: authenticated downloads including POST-produced files; reject/error response; 128 MiB limit and download cancellation/old context deletion; export/delete and system profile separation.
- Real tests required: Gecko runtime permission delegate install, scoped autoplay permission and rejection of OS/hardware requests; revoke one context while preserving another; no crash or bypass on denied permission.
- Real tests required: native tracking protection on/off persistent per-slot after explicit reopen, site compatibility, language change on subsequent navigation and existing chat draft/scroll preservation.
- On old/unknown Gecko or OEM hooks, fail closed and show an error; do not silently fall back to shared session or broad system data access.

## Sources

- https://source.android.com/docs/devices/admin/multi-user
- https://developer.android.com/work/managed-profiles
- https://developer.android.com/reference/android/os/UserManager
- https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoSessionSettings.Builder.html
- https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoSession.PermissionDelegate.html
- https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebResponse.html
