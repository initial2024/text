# OGTRANSLATE19 — native mirrored, no-reload offline reading layer

Date: 2026-11-05

## Scope and design judgment

The user requested a visual "copy of the screen" where foreign-language text
is translated while the page appearance and account state survive.

**Implemented in public source, not signed or installed as an APK:**
- Reuses the **existing owned GeckoView**; no new session, background provider
  or hidden tab. GeckoView.capturePixels() takes a one-shot bitmap of the
  **visible viewport**, triggered by a user translation action.
- A private native ScrollView draws the captured image as an optional visual
  backdrop with a translucent, selectable, **scrollable translation sheet**.
  There is a button to toggle between mirrored-background and clean reading.
- Both fullscreen account-browser and floating-window paths use the same
  PageMirrorSnapshot helper. Floating-window async callbacks remain fenced by
  the existing session/provider/slot and translationEpoch identity checks.
- First-use per-language model-download consent stays inside the same reading
  surface. Screenshot capture is separate from model installation.
- Capture has a 2.5 million-pixel bound; failure falls back to the plain
  no-reload reading layer. Bitmap remains in memory only and is discarded
  on reader close, navigation to a different account or cancellation.
- No MediaProjection, accessibility overlay over **other** applications,
  screenshot upload, OCR, browser DOM manipulation, forced reload or JS injection.

### Critical limitation

This is a **read-only visual snapshot + translated text sheet**, *not*
pixel-perfect replacement of every foreign-language text region in the captured
image. Gecko getPageContent() returns text or markdown, not exact screen-space
bounding boxes. An OCR/text-box alignment engine would be a separate
privacy-sensitive feature with additional binary dependencies and on-device
acceptance requirements. The screenshot is frozen and not clickable. The user
must return to the original page to interact with the website.

## Release constraints

- Public source: initial2024/text; only
  work/omnigate-integrated-v05, Draft PR #1.
- Last verified user-signed **private** APK: com.omnigate.core
  v1.3.1-TEST / versionCode=1031, same fixed certificate SHA256
  bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d.
- A future user-installable upgraded APK must have versionCode >=1032,
  preserve existing private-only code and all app resources, retain signer,
  and independently verify no duplicate DEX classes, ML Kit resources,
  manifest, zipalign and native libs.
- **No private v1.3.2 APK or ZIP was built by this source-only commit**.
  AAR/D8 CI PASS is not a signed complete app, not a phone test and not a
  production release.
- The exact private baseline and signing materials must never be pushed to
  public GitHub or added to public CI artifacts.

## Mandatory Android 16 acceptance

1. Upgrade over installed 1031 without uninstalling/clearing data; same
   fixed cert and all prior accounts/settings remain.
2. Open selected authenticated web account, enter an *unsent* draft; scroll
   away from the top. Translate and show mirrored snapshot. Verify snapshot
   corresponds to visible page and does not show another account's content.
3. Toggle "show/hide page snapshot"; translated text remains legible, scrolls
   independently, and has selectable text. Close. Original same GeckoSession,
   URL, input draft, cookies and web-page scroll remain unchanged.
4. Do first Wi-Fi-only ML Kit model download after explicit in-reader consent.
   Confirm actual translation result, then offline repeat. NoClassDefFoundError,
   wrong language pair or silent remote translator fallback.
5. Switch provider and account before a delayed screenshot or translation
   resolves. Old result/snapshot must not display in new account/slot. Close
   floating window while pending; ensure all native view backgrounds release.
6. No automatic Claude/DeepSeek/other account login, request or rotation.
   Do not change DSH.
