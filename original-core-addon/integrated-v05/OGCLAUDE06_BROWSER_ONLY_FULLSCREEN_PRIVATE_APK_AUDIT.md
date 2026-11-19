# OGCLAUDE06 — manual-only Claude login + immersive full-screen model browser

Date: 2026-11-04. Target: existing `initial2024/text` / `work/omnigate-integrated-v05` / Draft PR #1. No new repository or automatic deployment.

## User-owned feature scope

- Claude added as a **BROWSER-ONLY** login origin `https://claude.ai/` in `AccountBrowserSlots.PROVIDERS`, with the same per-slot random Gecko `contextId` isolation and existing manual account selector as other supported websites. It is NOT a Claude-Web `/v1/chat/completions` adapter and cannot be armed via `MainAccountTargetStore` or the native model switch. The **previously existing** independent Claude Direct API client is left unchanged.
- On app start / account picker opening: no Claude `GeckoSession` is constructed and no Claude site is loaded. Only a deliberate tap on the provider/account invokes `IsolatedGeckoBrowser.open` and `GeckoSession.loadUri`; selecting another slot releases the previous view and **closes** its GeckoSession first, so another login is not silently activated in the background. Claude account controls do not offer budget-triggered auto-swapping, native API bindings or model auto-submission.
- One-tap account tab switching is manual-only; each context retains a distinct persisted Gecko session ID. Claimed login persistence across Android process restarts must be device-verified; the app does not read/export passwords, cookies or website tokens.
- All auxiliary model browser pages (including Claude) have an explicit **全屏模型** button. Fullscreen hides only this dialog's chrome and Android system bars, retaining its same GeckoSession and displaying an overlay **退出全屏** action and Android Back escape. No additional model/API request on fullscreen toggle.
- `CompatSettingsActivity` old Claude button no longer opens an external `Intent.ACTION_VIEW`; it opens the previously selected Claude account inside the isolated Gecko browser, by user click.
- Previously private same-signer v1.1.5 native model owner handoff `NativeModelSwitch$QuickClick` remains loaded for the FOUR existing native providers; Claude is barred, and no main target is armed if handoff binary is missing.

## Private signed v1.1.6 candidate

- APK: `OmniGate-v1.1.6-OGCLAUDE-FULLSCREEN-LAZY-FIXED-SIGNED-TEST.apk`
- SHA-256: `50b7422ccf1f714ff1c81323c037a46154335cae0d2327a65a318ab16437d475`
- Length: `171376610` bytes; package `com.omnigate.core`; versionName `1.1.6`, versionCode `1016`.
- Same original signing cert SHA-256 `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`. Android APK v2 and v3 signature PASS; official `zipalign -c -P 16 4` PASS; AAPT2 manifest PASS; complete CRC PASS.
- Private binary built from the user's verified v1.1.5 OGMAIN15 transport-rollback baseline; ONLY **AndroidManifest.xml** and **classes4.dex** changed among non-signature files. Original `assets/ai_gateway_bridge/bridge.js`, `classes.dex`, `classes2.dex`, `classes3.dex`, native 1604/SSE sending route, original account/overlay owner controls, 401/403/429 holds, relay, attachments and all fox launch icons/splash asset bytes are **unchanged**.
- Private compat DEX 038 preserved 700 classes, original overall 16860 class definitions, zero class additions/removals.
- Exact 22 class-file replacements for isolated Gecko/target control; targeted private Smali update to the old Claude Settings link to prevent opening external system browser. Existing `NativeModelSwitch$QuickClick` class remains byte-unmodified.
- Public code compiles and contract tests are green: [Android source preflight #41797951345](https://github.com/initial2024/text/actions/runs/41797951345), [OGCLAUDE browser-only lazy/fullscreen contract #41797944896](https://github.com/initial2024/text/actions/runs/41797944896); updated five-provider picker tests and original native owner safety gate also passed on preceding commits.

**Never commit the private signed APK, original private bridge, signing key or browser data to the public repository.**

## Open device gate

The following are NOT verified with the user's physical phone: Claude login compatibility with embedded Android GeckoView (including any SSO/anti-embedded-browser restrictions), persistence of actual authenticated cookies across app restarts, functional fullscreen exit at phone aspect ratio, no network use after switching away, and actual background/foreground lifecycle behavior. User should test with their own selected Claude account by opening the account picker, verify login, switch to another account/provider, and independently observe network requests. Browser isolation does not imply unrestricted third-party sign-in support. Never auto rotate on challenge/401/403/429.

No claim that the historical v1.1.5 DeepSeek SSE problem is solved by this UI-only feature; the reverted transfer code is preserved unchanged, not retested. Keep existing Draft PR open.
