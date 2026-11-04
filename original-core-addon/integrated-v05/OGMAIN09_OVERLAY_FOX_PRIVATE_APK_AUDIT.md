# OGMAIN09 · Verified overlay-owner route and fox adaptive icon

**Private signed candidate:** `OmniGate-v1.0.9-OGMAIN09-FOX-OVERLAY-OWNER-FIXED-SIGNED-CANDIDATE.apk` was generated from the user's own v1.0.8 candidate in a private workspace. Do **not** add the APK, original DEX, signing material, browser cookies, or generated private account files to this public repository.

## Reported real-device failure

The user supplied a local `/health` report: `verifiedNativeOwner=overlay`, `observedRuntimeRoute=overlay_socket_and_driver_match`, `Prefs.port=1604`, `originalServerRunning=false`, `originalOverlayActive=true`, upstream reachable. The 8787 sidecar returned **HTTP 409** *locally*, no upstream HTTP request, no SSE. This is a socket-owner mismatch in the native account guard, **not** an account password failure.

## Actual binary fixes in the private v1.0.9 candidate

1. **Exact overlay owner verification:** The guard now accepts a verified `OverlayService` socket owner only if its captured service, original `ApiServer.driver`, `WebDriver.bridge`, `ProviderBrowser.bridge/session/view`, `GeckoBridge.session`, `GeckoView.getSession()`, provider enum identity/model and `GeckoSession.getSettings().getContextId()` all match the currently armed target. It rechecks the original socket owner and driver identity after inspection. MainActivity's previous exact check remains. Merely observing `overlay_socket_and_driver_match` is **insufficient**.
2. **One-click overlay handoff:** After explicit user consent, the account selection path checks no upload/send, a uniquely verified original overlay and the **overlay driver** itself reporting idle; invokes original `RETURN_FROM_OVERLAY` via the existing trusted handoff method, waits up to approximately 15 seconds, rechecks the main model idle/owner, then creates the desired native main model context. Unverified/busy/rate-held/time-out conditions keep the existing session; no process kill, token copying or automatic retry.
3. **Real fox icon:** Updated `resources.arsc` path for `drawable/ic_launcher_foreground` from old `res/BJ.xml` vector to new `res/BJ.png` raster and provided matching PNG inside the package; Android's adaptive icon now uses the fox rather than the old vector. All ten legacy density launcher PNGs updated. In-app launcher adds mascot fade, move, rotate, scale, branding and fade-out, not a claim of a separately animated Android-system splash.

Private APK has package `com.omnigate.core`, version 1.0.9 (1009), size **171362425 bytes**, SHA-256 `18ca4c3e5c92dcc01f61321e7e971e32f3b17c63f0d00832d2d7e58909a3ecd4`, original signer cert SHA-256 `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`.

Static checks: official v2/v3 signatures PASS, zipalign 16KiB PASS, aapt2 manifest/adaptive foreground PASS, ZIP integrity PASS, 4 DEX class counts 7160/7628/1372/699 with zero duplicate definitions PASS. Original `classes.dex`/2/3 and `assets/ai_gateway_bridge/bridge.js` unchanged relative to v1.0.8. Existing public Java source checks are separate from the private assembled APK. Public GitHub actions do not build/sign this user's private candidate.

**Open gate:** Android install/cold-launch, actual fox icon on user's launcher, account 1→2→1 sign-in persistence, real model reply/SSE through overlay ownership, safety of user-approved overlay handoff, and 401/403/429 regressions **not device-tested**. Keep Draft PR #1 and Issue #17 open; do not claim production release or that the 409 definitely disappears for all device conditions.
