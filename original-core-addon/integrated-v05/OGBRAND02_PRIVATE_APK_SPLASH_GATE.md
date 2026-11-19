# OGBRAND02 — Original-full-APK splash integration gate

## What source code now provides

- `OmniGateSplashActivity` (pure Canvas resources) can live in the pre-existing `classes4.dex` compatibility layer without loading any public repository's icon bitmap or unverified private resource ID.
- The splash is a real Activity. It animates on Android, respects system disabled animators and moves to `OmniGateHomeActivity`. It does **not** start another native WebDriver or re-send a model request.
- The addon manifest declares this activity with `android:exported=true`. No MAIN/LAUNCHER filter is added to the library manifest, preventing a second launcher entry in source-level merges.

## Mandatory private full-APK binary integration (NOT YET DONE)

The publicly available Gradle `app` builds `com.omnigate.sidecar`, not the user's original signed `com.omnigate.core`. **Do not distribute it as a v1.0.6 full APK.**

1. Verify private original signed v1.0.5 APK and its signing certificate SHA-256: `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`. Keep the original file private.
2. Compile addon; inject updated compatibility dex into the trusted original binary, preserving the existing source app, original WebDriver and private `assets/ai_gateway_bridge/bridge.js`.
3. Atomically move the sole original `android.intent.action.MAIN` / `android.intent.category.LAUNCHER` intent filter from the original Launcher `ProviderActivity` to `com.omnigate.integrated.OmniGateSplashActivity`; ensure the splash activity is exported and all nested providers preserve original permissions.
4. To change the **installed launcher icon**, replace or remap the original binary Manifest's `android:icon` and adaptive/round icon resource entries with verified newly integrated icon resources. A `Canvas` drawn splash or `app/src/main/res` inside `com.omnigate.sidecar` is **not** an installed icon update.
5. Set `com.omnigate.core` package unchanged, versionCode >= 1006, versionName v1.0.6 only after successful full build; use the **same protected original signer**. Do not export or check in key material.
6. Independently verify `apksigner verify --verbose --print-certs`, certificate match, `zipalign`, install/upgrade with data preserved, exactly **one** launcher icon and exactly one splash-to-home transition. Verify original model/session, login state, account isolation, API transport, Relay and risk holds.
7. If original APK, signer, or source-mapped binary manifest is inaccessible, stop. No fake ZIP or other-package APK. No automatic destructive uninstalls or data migration.

## Honest remaining gap: main execution account switch

Opening isolated `GeckoSession` context IDs does not change the original frozen WebDriver cookies or the 8787 main execution account. Main-account selection must not claim success until a trusted exact-instance session handoff transfers the original model execution context, the original driver identity is reverified, and the first user-approved model response is tied to the selected account. Never copy cookies between untrusted origins or rotate to evade 401/403/429/challenge.
