# OGBROWSER02 — isolated browser web files, permissions, and Android profiles: device release gate

**Branch:** `work/omnigate-integrated-v05` / Draft PR #1.  
**Policy:** source/CI build is **not** real-device acceptance. Do not merge, release, deploy, or sign a new test APK from an older original binary. Never place private signed APKs/keys/account cookies into GitHub Actions.

## Identity, protection and scope

- Each auxiliary website account uses a persisted random Gecko session contextId, distinct from the original 8787 WebDriver/website login.
- All supported auxiliary providers use real Gecko user agent/viewport settings; do not modify JS Canvas, WebGL, attestation, Android ID, network identity or challenge/CAPTCHA flows.
- Native `账号空间 ▾` entry is not the site's own account avatar; it must be ready before the website finishes client-side UI hydration. The Gecko page-load indicator is **not a login verdict**; it may display the base-document load time only.
- Per-slot Gecko tracking protection is enabled by default. User can switch an individual slot to compatibility mode, effective **only on next manual open**. No automatic refresh, cross-slot settings change, or background sign-in.
- Android app clones, managed profiles and secondary OS users must be created/supported by the operating system/OEM. The in-app profile safety center is read-only and may only deep-link to public OS Settings pages. A different app-private probe token or Android user does **not** prove a different physical device or protection from provider fraud controls.
- Android `127.0.0.1:8787` port ownership with multiple users/clones is unverified. Second gateway must never steal/rewrite traffic or switch accounts after a bind error. Verify independently on an actual phone.

## Safe upload matrix (SAF to Gecko)

1. Manual file selection: website `<input type=file>` opens Android `ACTION_OPEN_DOCUMENT` once; user selects one valid small file and explicitly confirms.
2. Multiple files: accept up to 8 with reliable metadata sizes, each at most 64 MiB and aggregate at most 128 MiB; fail closed for oversize, unknown size, unreadable URI, duplicate URI or canceled dialog. No global storage permissions.
3. Original Gecko FilePrompt `confirm(Context, Uri[])` completes for the still-current session; selected content Uri is mediated via Android's temporary read grant. **No separate app-private upload-byte staging currently exists**; an upload is not marked successful merely because a URI was handed to Gecko.
4. Navigation during a pending picker cancels selection, including SSO redirect/link navigation in the same GeckoSession; an unavailable navigation observer blocks that session's file uploads. Cancel on provider switch, browser close or overlay close.
5. Recursive folder upload is explicitly denied. Website `capture` requests must not silently open the camera or microphone. No automatic model/chat POST, repeat upload, injected JS, cookie export or native 8787 proxy call.
6. Verify origin-sensitive picker behavior in fullscreen and overlay on a physical Android 16 phone; some Android OEM/background activity policies may block opening a picker from an app overlay, which must fail closed.

## Safe download matrix

1. Only manually triggered Gecko `onExternalResponse` with original response body and 2xx HTTP status; **never replay** signed URLs or forms/cookie-authenticated POST to “fetch” the file separately.
2. Show explicit permission to save into per-context application-private storage, bounded by file 128 MiB and total context 512 MiB; reject or cancel missing/blocked owner, non-success response, size limit or read errors.
3. Download manager lists only the selected account's private files, provides confirmation before delete, and uses explicit Android `ACTION_CREATE_DOCUMENT` for export to user-selected external storage.
4. On account switch/session close: cancel pending and active streams. On confirmed account storage erasure: retire the context before deletion, close its streams, delete its private downloads and rotate the Gecko context ID; no global host/eTLD+1 deletion and no deletion of other slots.
5. Stress tests: simultaneous downloads, canceled save dialog, stream stalls, abrupt process death, zero-length file, basename/path-traversal filename, disk full, partial writes and deletion races. Validate no stale `.partial` is shown to another account.
6. Private download files are **not guaranteed to remain private once the user deliberately exports them** to a public folder.

## Web permission matrix

1. Android OS app privileges are **shared within one installation**. A Gecko slot cannot revoke Android's OS-level permission independently. For now, Gecko Android permission and camera/microphone requests are explicitly rejected (safe but not feature-complete).
2. Site-level HTTPS/autoplay permission prompt checks exact owner session and `ContentPermission.contextId`; only explicit user click can grant the currently supported types. Location/network discovery/storage access/notifications/XR and hardware permission requests remain restricted pending on-device audit.
3. Per-slot permission reset closes the matching session, reads Gecko `getAllPermissions`, applies `VALUE_PROMPT` only where `contextId` equals that slot. Do not clear cookies or use a global data wipe; check failed/partial reset truthfully.
4. Test switching away during an unresolved permission dialog. No late callback should grant a closed account. Test negative origin cases, website permissions in one slot versus another, and work-profile separate OS grants.

## Language/UX matrix

1. Android `App info > Languages` offers supported registered locales **only in the complete signed APK manifest/resources**. Do not infer from a library manifest alone.
2. Existing GeckoRuntime `setLocales` follows the per-app/system preference on explicit app resume/configuration change **without reloading active sites**; third-party websites may retain their own language in account preferences or cookies. Do not claim guaranteed web-page translation.
3. For Claude login, native account button and load indicator are immediate; no synthetic sign-in success, DOM polling, forced page reload or automated challenge retries. Measure real website avatar delay separately from Gecko page-load timing.

## Immutable release stop conditions

- Old signed v1.3.2 candidate verified as 211,743,121 bytes, SHA-256 `2bbff5d7be4ee28929828c4b238f317a0470f78c0a8364e28920f4359cc6c691`, six DEX files and intact ZIP CRC. It does **not** contain the new OGBROWSER02 code.
- New APK must be packaged **on top of the exact user-owned latest APK**, keeping `com.omnigate.core`, original signer certificate, all six existing DEX payloads and ML Kit runtime/resources, plus a valid future versionCode, signed v2/v3, 16-KiB zip alignment and explicit one-launcher manifest verification. Never replace classes4.dex without preserving private existing classes.
- All 3 levels must pass: source compilation/static tests; private signed binary audit (class closure/signature/manifest); genuine Android 16 login/file/permission/device-profile E2E. A successful CI preflight does not satisfy the last two.
- No fake device identity; no bypass of authentication challenges; no automatic account cycling when a website challenges, returns 401/403/429, or rate limits. Fail closed, keep Draft PR open until measured evidence exists.
