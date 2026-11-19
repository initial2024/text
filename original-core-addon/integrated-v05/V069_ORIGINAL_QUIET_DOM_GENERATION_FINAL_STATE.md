# OmniGate v0.6.9 — Original bridge quiet-DOM generation completion

## User reproduction and evidence
- v0.6.8 user screenshot: injected message exactly `你好` (the previous `用户：` prefix is now absent), ChatGPT webpage visibly replies, but floating panel stays `文本：已发送，正在等待真正的模型回复…`. This does **not** verify local HTTP 200.
- User explicitly asked to refer to the earliest working third-party APK and fix the returned text rather than endlessly changing the input selectors or 8787/1601 networking.

## Original third-party APK source findings
Directly inspected **private user-supplied** original APK `assets/ai_gateway_bridge/bridge.js` and compared with v0.6.8 patched script:
- Original uses `MutationObserver` to trigger `scheduleState()` and `emitState()`, which posts `{type:'state',state:{answer,thinking,generating,...}}` to original Gecko native bridge.
- The original generation grace is armed on send and renewed on answer change; initial grace 30,000 ms, activity grace 10,000 ms.
- The original `emitState()` has **no autonomous timer to post `generating=false` once the grace deadline expires** if the DOM stays idle. It can therefore leave the last native WebDriver PageState at `generating=true` even when the model webpage has visibly stopped and answered.
- This is a grounded lifecycle deadlock candidate and was reproduced in a fake-clock/no-mutation JS harness. It is distinct from older ChatGPT composer-selector fixes and the native 1601 port issue.

## Private APK-only fix
- Added dedicated timer `generationLeaseWakeTimer` and sequence `generationLeaseWakeSeq` to the original private `bridge.js` asset, without changing other providers, selection logic or original native DEX.
- `emitState(force)` now checks whether `generating` is only held by the existing inactivity grace and schedules a single wake immediately after expiration, even if no DOM mutations occur.
- On new webpage activity/renewal, stale wakeups are cancelled. If the website still exposes its actual generating element, no expiry-based completion is forced. On original bridge reconfiguration or disconnect, pending wakeups are cleared.
- **No provider Stop/cancel, forced completion response, or extra network read timeout**. Existing 30 s / 10 s guards are maintained. If the provider has no real generating marker and goes silent for the grace period while actually working, this limitation remains; cannot guarantee all model behavior.
- Added redacted diagnostic to public-source Java `WebBridgeDiagnostics`: `generationLeaseWakePending`, `generationLeaseRemainingMs`, `leaseWakeCount`, and `lastLeaseWakeOutcome`. These are observation flags, not chat content.
- Complete original third-party `bridge.js` and signed binary were **not** published in public GitHub.

## Actual test and signed artifact
- 3 deterministic Node.js fake-clock tests on the **actual patched bridge script**: quiet DOM ends by emitting `generating=false`; renewed lease cancels old scheduled completion; bridge disconnect cleans timer. **PASS 3/3**.
- Java compatibility source on commit `4fc81e37c5e85f1c9a9358ba3c2cead11ad2bed6`. GitHub Actions [40808510620](https://github.com/initial2024/text/actions/runs/40808510620) compile/D8/DEX verifier/fixed certificate identity **PASS**.
- Signed PRIVATE test APK `OmniGate_v069.apk`: `com.omnigate.core`, versionCode 34, versionName 0.6.9, SHA256 **`1b2bdfce3dfd728effe17ca5c2987653d4aff00ffd2eb81f9988df99447ae902`**, 170430251 bytes.
- Same fixed signing certificate SHA256 `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`. Independent APK v2/v3, zipalign, ZIP CRC PASS; 4 DEX SHA1/Adler32 PASS, 16664 class defs, 0 duplicates.
- Relative to signed v0.6.8, **1005 preserved entries are byte-identical**; only version Manifest, new `classes4.dex`, privately patched `assets/ai_gateway_bridge/bridge.js` are changed. First three original DEX byte-identical.
- Backup ZIP SHA256 `72737edcab65f125e91d1a65f046194b5b7197b839fd4ec5f7966ee4542a0cfe`.

**Gate: SIGNED_STATIC_PASS__PHONE_REAL_API_TEXT_E2E_PENDING**. Do not claim fully fixed until Android 8787 returns actual HTTP 200 + nonempty model message. If it still hangs: on the *same* model conversation capture Gecko `assistantReplyChars`, native `getAnswer` character count, `getGenerating`, and new lease status. A reply on the website alone is not HTTP success. File and image upload remain blocked/unverified. No PR merge, release, user-data wipe or production action.
