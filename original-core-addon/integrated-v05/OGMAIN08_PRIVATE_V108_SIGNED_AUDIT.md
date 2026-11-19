# OGMAIN08 — v1.0.8 private APK construction audit

**Status:** `SIGNED_STATIC_PASS_DEVICE_E2E_PENDING`. This is NOT a claim of successful 1→2→1 real-account switching.

## Identified problems in the v1.0.7 binary

1. `OWNER_UNVERIFIED` under HTTP 409 is emitted by the deliberate original MainActivity native API socket-owner check, **not** by any account password or 8787 API key check. The previous switch UI could arm a target even though its actual original WebDriver/API socket owner was not verified.
2. The manifest resolves `@mipmap/ic_launcher` to `res/BW.xml` on modern Android; it references `@drawable/ic_launcher_foreground` = `res/BJ.xml`. v1.0.7 still had the OLD diamond in that vector. Updating only some PNG fallbacks therefore did not change adaptive launcher or Android-system splash icons.
3. The previous Home Activity entry effect was only card fade/slide, not a distinctive branded intro.

## This private candidate (not uploaded to GitHub)

- File: `OmniGate-v1.0.8-OGMAIN08-OWNER-ADAPTIVE-INTRO-FIXED-SIGNED-CANDIDATE.apk`
- SHA256: `aeb3a241a88d2f78f8e287468fb285ac82afd6ca19e7d618d7429af32780e2b8`
- Size: **170752065** bytes.
- Package **com.omnigate.core**, versionName **1.0.8**, code **1008**.
- Same original signing cert SHA256 `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`.
- Official APK v2/v3 signature: PASS; 16KiB zipalign: PASS; ZIP CRC: PASS; aapt2 manifest and vector XML: PASS.
- DEX 038 class counts: 7160, 7628, 1372, **697**; 0 duplicate definitions. Original `classes.dex`, `classes2.dex`, `classes3.dex` and private `bridge.js` unchanged vs signed v1.0.7.
- Modified ZIP entries: `AndroidManifest.xml`, `classes4.dex`, `res/BJ.xml`, fallback icon PNGs `res/9w.png`, `res/yn.png`, `res/zR.png`, `res/8c.png`.
- The original `res/BW.xml` / `res/0K.xml` adaptive-icon entries still target `res/BJ.xml`, now an infinity-style foreground. Other 6 xhdpi/xxhdpi/xxxhdpi PNGs were previously changed in v1.0.7.
- New in-app brand intro: OmniGateHomeActivity displays a 1300ms animated logo overlay then fades out. **Not** a new independently animated Android-system splash resource.
- v1.0.8 native switch preflight supports a carefully bounded *idle-only* reconstruction of MainActivity when **no original configured native port listener exists**; unknown live API listener, overlay, active upload/response, or platform holds still hard-stop.
- Before the final startActivity, native socket ownership is checked again to avoid taking over an unknown listener.
- HTTP 409 `omnigate_main_account_guard` now includes a non-sensitive `owner_route` to distinguish `upstream_unreachable`, `unknown_no_original_main`, `main_socket_driver_mismatch`, `port_not_owned_by_main`, etc. The message is **diagnostic**, not an unsafe bypass.
- New binary changes were applied in a **private** Smali workspace; this public repository contains no original APK, signer, cookies, passwords, or private native DEX.

## Unresolved acceptance gate

Android device install/startup, actual adaptive launcher rendering, cold launch branding, login retention, idle-only MainActivity recreation, original API server owner identity, selected account 1→2→1 **real** model response/streaming, and 401/403/429 fail-closed regression tests are **not device-verified**. If 409 persists, collect its `owner_route` plus original-native state report, **not secrets**, and fix the exact verified owner cause. Do not remove OWNER_UNVERIFIED validation to hide the error.

Keep existing Draft PR #1 and Issue #17 open. Preserve older v1.0.7/v1.0.5 APKs as recoverable baselines; no automatic production release.
