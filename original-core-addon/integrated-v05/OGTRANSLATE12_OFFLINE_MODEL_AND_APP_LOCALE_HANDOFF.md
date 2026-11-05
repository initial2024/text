# OGTRANSLATE12 — real-package handoff and blockers (2026-11-05)

## Truthful status
- New independent **ML Kit offline translation source** is implemented.
- Android platform TranslationService absence can now be handled **after** the optional ML Kit module and its transitive runtime dependencies have actually been packaged, with the language model first downloaded by explicit Wi-Fi-only user action.
- Core compatibility library has a reflection-based, fail-closed optional bridge. If the runtime is missing, an actionable error appears rather than a false success.
- AAR/CI success is **not** a user-installable integrated APK, nor a real translated-page result.

## Last known installed-package baseline (user Library audit)
- User's OmniGate v1.2.9 audit: package **com.omnigate.core**, versionName **1.2.9**, versionCode **1029**; signer cert SHA-256 **bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d**.
- Binary SHA-256 in that audit: **da1f77fe479190f1f2fb0b1655e0977e02cca85b7a45bfb6bf9fc5ae7a66eb34**.
- Source SHA at that private build: **6aa6f6981f2e8bdabda2daac06cee0e6b828813e**.
- That audit explicitly says `system_app_info_language=NOT_IMPLEMENTED`; Android 16 real-device E2E was not done. The missing language option in the screenshot is explained by this baseline.
- The old PR description's original package **com.deepseek2.app** refers to an **earlier binary stage**. DO NOT use it as the final installed package name or produce an APK with the wrong identity.
- The v1.2.9 **actual signed APK bytes are not available as a current working file**. A previous v1.2.6 artifact is **not** an acceptable silent downgrade or binary base. Preserve all v1.2.9 private-only classes and resources.

## Source and CI evidence (2026-11-05)
- Main PR: https://github.com/initial2024/text/pull/1 (Draft).
- Optional ML Kit compile / model consent source: https://github.com/initial2024/text/actions/runs/41967337487 (SUCCESS).
- Core DEX/AAR + fixed-key certificate preflight: https://github.com/initial2024/text/actions/runs/41967343729 (SUCCESS).
- Official developer reference: https://developers.google.com/ml-kit/language/translation/android (on-device inference, first model download ~30 MB/language, API 23+).
- SDK compile module dependency: `com.google.mlkit:translate:17.0.3`.
- Library's main `OfflineMlKitBridge` intentionally uses reflection and does **not** embed Google SDK artifacts into the original frozen DEX. The optional module AAR does not itself contain all transitive SDK classes/resources.

## Ordered package requirements
1. Locate exact **v1.2.9 signed APK** as baseline; verify SHA-256 above and package name com.omnigate.core. Do not improvise a replacement with a v1.2.6 base.
2. Merge changed core compatibility classes into an additional DEX, **and** compile/copy the offline-translation module's runtime classes + all Google ML Kit transitive Java classes. Deduplicate against all original DEX and AndroidX/Kotlin classes.
3. Merge Android library resources and applicable manifests from ML Kit transitive AARs. Check for missing providers, manifest placeholders, resource IDs, native binaries and duplicate library classes. **Do not just append an optional module AAR to APK ZIP.**
4. Correct the actual binary AndroidManifest's package/authorities, exactly one MAIN/LAUNCHER, preserve original Application, upgrade versionCode **>1029** (proposed 1030), add Android 13 `android:localeConfig` resource and Android 14+ dynamic locale registration; inspect installed APK with aapt2. The source library manifest alone is insufficient.
5. Sign exclusively with same fixed private certificate, verify zipalign, v2/v3, SHA-256; never publish keystore files or passwords.
6. On Android 16 (same user account), install as upgrade without wiping app data. Validate App Info > Language lists Chinese (Simplified) and English, and **actual native shell labels** switch languages. Full English string coverage is still incomplete and must be assessed honestly.
7. On OEM lacking system TranslationService: default translation should first offer explicit pair-specific offline model download, then subsequently open actual translated text in independent reader. Both languages and multi-chunk text must be tested. Check that full page does not reload, site session/cookies, scroll and unsent chat prompt survive.
8. Cancelling external share must not leak data. Legacy Gecko full-page translation continues to need consent and may mutate DOM/reload; never make it default.
9. Verify no accidental provider startup, account auto-rotation, challenge bypass, 8787 transport edits, or session contamination.
10. Only after all checks may a signed **TEST** APK be provided. Do not merge/release production while the device gate is blocked.

## Why not shipped
Current source workflow produces an offline module AAR and a dependency ledger, not complete merged DEX/resources into the v1.2.9 APK. This is deliberately **BLOCKED_PRIVATE_APK_RUNTIME_CLOSURE_AND_DEVICE_E2E**, not success.

## OGTRANSLATE13 — unified default no-reload reader
- Removed four duplicate "download model" items from the translation menu. Four existing language directions still open the SAME native reading layer.
- On a fresh language pair, when actual ML Kit dependencies are bundled, the reader displays an in-panel consent control to download the corresponding model over Wi-Fi. No extraction, network download, or translation request is started until that button is clicked.
- On previously authorized language pairs, the same reader translates immediately with ML Kit. If no offline engine is bundled, the existing Android system translator may be used if available; otherwise the reader gives a clear actionable error. Never claim that an unsigned AAR is a working installed engine.
- Fullscreen uses an AlertDialog's existing ScrollView + action Button; floating window uses its **existing** WindowManager translationPanel + action Button. Closing or switching account increments translationEpoch, hides the control, and discards late UI responses.
- The original Gecko session is not navigated, reloaded, restored, reparented or replaced from either default reader path. The optional legacy DOM translator continues under explicit separate consent with a reload warning.
- When a private APK is being created, preserve **all** v1.2.9 original DEX files, including private classes4.dex; append additional DEX instead of overwriting that file.
- The exact device-installed APK may be exported locally with `tools/export_installed_omnigate_v129.ps1`. It only reads the APK code; it does not export account/cookie data and never uploads to GitHub. Its SHA-256 **must** match the audited v1.2.9 base or the process stops.
- Pending mandatory gates: real signed APK with all ML Kit runtime/transitive code and resources; Android 16 per-app language option; actual offline translation of a page after first-time Wi-Fi model download; session, draft, scroll, account and port ownership preservation. No test APK or merge until these pass.
