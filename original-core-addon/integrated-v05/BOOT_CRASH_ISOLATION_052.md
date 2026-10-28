# OmniGate v0.5.2 — first-launch crash isolation (NOT production ready)

User report: v0.5.1 parallel APK `com.omnigate.core` installs but tapping launcher exits immediately. The earlier static signature PASSES did **not** verify Android runtime.

- v0.5.1 SHA-256: `969a74a644c5cdd82791240977ebdf07165b6b03ef6c02d68b0c4919a019b5a2`
- v0.5.2 signed local test APK SHA-256: `cf17c8c37e27721144ee383c3883490dea62eab472d6397492cccc28bca0b2fe`
- Package: `com.omnigate.core`, versionCode: 17, versionName: 0.5.2.
- The **only** non-signature changed member from signed v0.5.1: AndroidManifest.xml.
- Remove `com.omnigate.integrated.CompatBootstrapProvider` from Manifest to prevent automatic Sidecar foreground-service startup. Retain original `com.deepseek2.app.App`, original launcher `com.deepseek2.app.ui.ProviderActivity`, existing integrated Sidecar service declaration, original DEX, all libraries and resources verbatim. Note: this diagnostic build will **not** auto start 8787.
- 1007 original ZIP entries identical CRC; 26 native lib entries unchanged.
- Fixed certificate `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`, signature v2/v3 PASS, zipalign PASS.
- No Android real-device execution done; behavior **UNKNOWN**.
- If v0.5.2 launches, focus subsequent fix on auto-start FGS failure and privilege / service lifecycle. If v0.5.2 still crashes, investigate repackaging/package-name migration, original App startup, original GeckoView initialization, resource ID/authority mismatch; require redacted AndroidRuntime FATAL EXCEPTION logcat. Do not assert cause without logs.
- Do not publicly publish user-owned 170MB APK nor sign keystore. Do not merge PR into original-core-addon or main. Do not delete the installed original AI Gateway.
