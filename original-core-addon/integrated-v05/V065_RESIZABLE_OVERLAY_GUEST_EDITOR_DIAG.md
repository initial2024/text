# OmniGate v0.6.5 — Resizable floating dock & redacted composer-path diagnosis

## Evidence from v0.6.4 user device
- User screenshot: actual in-app floating dock visible on ChatGPT web page, but it is too large and must be freely resizable.
- Read-only original GeckoBridge diagnosis: `provider=chatgpt`, `DOM=complete`, `composer=0`, `answer=0`, `guestComposerEligible=false`, `effectiveReady=false`. **Plain-text model E2E remains FAIL / unconfirmed**, and this cannot be attributed only to lack of login. The original DOM selection or document-targeting issue remains undiagnosed.

## Implementation
- `ApiQuickPanel` uses a small 40dp collapsed chip and an expanded dock with bounded width/height (206–360dp / 225–520dp, additionally clamped to viewport). Top `−/+` controls resize in steps, and bottom-right `⤡` handle changes both dimensions continuously. Drag the title to reposition independently. Persists normalized position and dimensions in app-private SharedPreferences.
- Expanded dock now has fixed title and tabs with an independent scrolling content region. Minimize and resize **do not issue provider Stop or cancel any in-flight `OverlayTextClient` request**.
- `WebBridgeDiagnostics` prints additional **redacted** fields from built-in `diagnose`: generic visible/editable DOM elements, iframe count, top-frame flag and provider-host match. These distinguish outdated CSS selectors from wrong document injection. It does not collect page text, full URLs, cookies or API credentials.
- The additional original `bridge.js` fields are patched into the **private signed APK only**; full third-party original script is not pushed into public GitHub. The narrow ChatGPT guest composer-readiness behavior from v0.6.4 is preserved, NOT claimed to prove guest server authorization or reply.
- Frozen original `classes.dex`, `classes2.dex`, `classes3.dex`, GeckoView libraries and original server architecture remain unchanged. Only `classes4.dex`, `AndroidManifest.xml` version metadata, and private `assets/ai_gateway_bridge/bridge.js` diagnostic fields change.

## Verified build and signed artifact
- CI [40730743990](https://github.com/initial2024/text/actions/runs/40730743990) completed SUCCESS on source commit `2c978ce81b60ef36f091d0bad1c83dbe1777d5e4`: Android Java build, D8 compilation, DEX ordering/hashes, fixed certificate identity.
- Private signed Android test APK: `OmniGate_v065.apk`, SHA256 `4da5ecc3d12e050ed5495ff503af858cfb9fe5f3cb3146f99b476cd99083a3a0`, size 169754339 bytes, package `com.omnigate.core`, versionCode 30, versionName 0.6.5.
- APK zipalign -p 4 PASS; v2 & v3 signature PASS with the original fixed cert SHA256 `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`; ZIP CRC PASS.
- 16664 DEX class definitions across 4 DEX, **0 duplicates**. 1005 unchanged original archive entries relative to v0.6.4. The only expected changed members are `AndroidManifest.xml`, `classes4.dex`, and `assets/ai_gateway_bridge/bridge.js`.
- Backup private ZIP SHA256 `3a89c71825782d03a6544902034ce97151a292bbef88bf4d764ea9139bd2660d` (APK plus user instructions and JSON audit).
- No signing key, proprietary original APK, or proprietary bridge script committed to the public repository; no merge, release or deployment.

**STATUS: PASS_STATIC_SIGNED__ANDROID_RESIZE_AND_TEXT_E2E_PENDING.** Device must verify drag resize, height/width clipping, size persistence after switching model pages, model text unaffected, real ordinary text return (HTTP 200 and nonempty content), new redacted bridge diagnosis. File/image end-to-end is still NOT VERIFIED. Failure should be reported as such; do not mark any model or upload feature PASS based on HTTP health alone.
