# Integrated v0.5 — experimental source candidate, not a signed APK

Source branch: original-core-addon; original immutable binary SHA-256 920919da7c1dd2ce05b4f1af5055fbbe412ba1773a6e5d1488c3c9d147ca835b.

This source adapts the v0.4 Sidecar HTTP bridge without replacing original com.deepseek2.app.App, provider GeckoView, 1601 API, uploads or overlay.

New namespace: com.omnigate.integrated. Start on first actual Activity resumed through a private ContentProvider callback. Foreground service stays in the main process until a real-device test establishes that the original Gecko App can safely initialize in :omnigate. Preserves the original provider DOM logic untouched.

Android SDK/Gradle can compile the library independently. **A library AAR is not a merged installable APK.** A safe merge still requires an APK manifest injection into the original binary, dex dependency closure (avoid 933 duplicate class definitions), resource verification, zipalign, original fixed private signing key, v2/v3 verification, and real-device E2E.

Other constraints: original base package=com.deepseek2.app, versionCode=14, minSdk=26, targetSdk=34. The final integrated original package must keep com.deepseek2.app and increment versionCode >14, signing cert SHA-256 bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d. Sidecar 0.1.0 package is com.omnigate.sidecar and cannot be in-place updated to com.deepseek2.app.

The separate Sidecar preferences **cannot be silently migrated** into the new original app sandbox; public relay configuration and keys must be imported through a user-authorized export/import, or reentered. Local 127.0.0.1:8787 accepts any nonempty key, public relay enforces the exact stored key. Neither behavior bypasses original provider authentication; user login is still required.

## Hard stop
Do not publish or rename an unsigned APK as final. Do not run GitHub Actions while quota is exhausted. Do not export secrets. Do not replace the original package's Application or resources with Sidecar's. Do not assume no-timeout upstream implies real provider answer is infinite.
