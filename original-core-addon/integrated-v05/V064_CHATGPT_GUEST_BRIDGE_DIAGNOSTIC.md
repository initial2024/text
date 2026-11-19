# OmniGate v0.6.4 — ChatGPT guest composer readiness, original Gecko bridge diagnosis

## Evidence from user's v0.6.3 phone test
- Original ChatGPT page is visible with account-login UI and a usable-looking text composer.
- `/v1/chat/completions` sent a plain-text message but remained waiting, with no valid `choices[0].message.content`.
- This is **not** proof that ChatGPT server requires login, nor proof that a guest call is allowed. Login state needs to be distinguished from webpage input readiness.

## Original third-party APK root-cause candidate
Verified directly against frozen original `classes.dex` and `assets/ai_gateway_bridge/bridge.js` in private signed baseline:
- Original enum `AiProvider.CHATGPT` has `model=chatgpt-web` and `readySelector=[data-testid="accounts-profile-button"]`.
- Web bridge `loggedIn()` in `ready_selector` strategy returns the presence of `config.readySelector`; `readState().providerReady` also relies on that selector. A guest page may have an actual composer but no account-profile button. Thus original `WebDriver.waitUntilReady` can remain blocked before attempting a send.
- Original `WebDriver.diagnose()` delegates to GeckoBridge operation `diagnose`, which reports provider, page DOM and composer/answer element counts. We expose **only the fixed safe flags** in a private floating UI diagnostic, not page text or cookies.

## Private test APK changes
- Branch source: new Java `OriginalCoreBridge.diagnoseCurrentPage()` and `WebBridgeDiagnostics`, plus floating Text pane diagnostic button. CI [40725239901](https://github.com/initial2024/text/actions/runs/40725239901) SUCCESS.
- The private APK contains a **targeted modified original bridge.js asset**, differing from frozen original. Scope:
  * For ChatGPT at exact origin `https://chatgpt.com`, with `ready_selector` strategy only, accept a visible enabled editable composer as a *guest-session readiness candidate*, even if the account-profile UI is absent.
  * Only this compat readiness is mapped onto the frozen native bridge's legacy `loggedIn` field because frozen WebDriver expects that field to determine readiness. This is **not actual account authentication** and must not be described as proof of login.
  * Add redacted `diagnose` fields: `loginStrategy`, `composerVisible`, `guestComposerEligible`, `accountUiVisible`, `effectiveReady`.
  * Other providers' login and ready logic unchanged. Server-enforced login, usage limits, and authorization not bypassed.
- Full modified proprietary third-party bridge.js and signed private APK are **NOT committed to this public GitHub repository**. SHA identities and scoped summary are recorded for private reproducibility.
- Node JavaScript regression: ChatGPT guest with composer=true → eligible; absent composer=false; disabled composer=false; other-provider guest=false; authenticated profile remains eligible. `GUEST_JS_REGRESSION_PASS`.

## Static build verification
- Private signed APK `OmniGate_v064.apk`, package `com.omnigate.core`, versionCode 29, versionName 0.6.4, SHA256 `e51d9e1299a82894ffb00356a333c3de3a6e55d3b4e0a2acd91c2c81bc5e43fd`, size 169754339 bytes.
- ApkSignature v2/v3 PASS, zipalign PASS, ZIP CRC PASS, fixed certificate SHA256 `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`.
- Three original DEX remain byte identical; `classes4.dex` SHA256 `44cbf8942cac4b28cfd92e1a494db7b1c1b499a7c88ce80d42ca48e58d2b8d79`; `bridge.js` new SHA256 `8336608cab915c0c473c6ef7c1a250b4cae653de78fa93dcff84429dfb0bdbb1`, original bridge.js SHA256 `945e3dad1f3f62e66d835a0c036d6122b581638c5c0fd957f1d02b13c9a436ec`.
- 1005 original archive entries unchanged, three expected changed `AndroidManifest.xml`, `classes4.dex`, `assets/ai_gateway_bridge/bridge.js`. Four DEX valid: 16661 defined classes, zero duplicates. One launcher.

**Gate: PASS_SIGNED_STATIC__GUEST_TEXT_DEVICE_E2E_PENDING.**
Install over v0.6.3 (never uninstall / wipe). Open ChatGPT guest if site allows, send plain text through floating Text pane. If waiting or HTTP 502/no reply, run `诊断原网页桥 / 游客输入状态` without cancelling the pending generation; record only redacted flags. Next required diagnosis: whether current GeckoBridge reports effectiveReady, composerVisible, and answer elements; whether native WebDriver logs injection vs bridge-unavailable. Do not claim guest server acceptance or file upload success until real evidence.
