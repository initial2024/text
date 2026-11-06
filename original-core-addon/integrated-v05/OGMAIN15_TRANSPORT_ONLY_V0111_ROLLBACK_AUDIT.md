# OGMAIN15 — transport-only rollback, not application rollback

Date: 2026-11-04. Original repo `initial2024/text`, existing branch `work/omnigate-integrated-v05` and Draft PR #1. No merging or production release.

## Verified source, not an arbitrary historical APK

The user explicitly requested reverting ONLY the model send/SSE transport to the last documented working implementation, while keeping all current UI, accounts, risk gates, attachment interfaces and branding. From the user's private File Library, all **11** parts of `OmniGate-v0111-LongThinking-Empty-SSE-Recovery-TEST.apk` were reconstructed and checked against the original offline joiner's expected complete SHA-256:

- Historical complete APK: `170511769` bytes
- Historical SHA-256: `b01e34270cdc9e95ecc3251cc6dfaf4f200ef1ef8de1821172e3867223ec178a` — verified.
- Historical private JS bridge SHA-256: `e581636809e91d3aafdddd93e6abe16ee8818ee86bc8ce915502e246e4550f0d` (no private JS uploaded).
- Prior 2026-10-30 successful **instance** of native SSE reported 32 content events and an initial content event at about 10.7 seconds. This evidence does NOT guarantee every request, or prove v1.1.5 will work on-device.

## v1.1.5 private, signed test APK: only transport functionality reverted

The current baseline is private signed v1.1.4 (`com.omnigate.core`). The new APK does not change the package name or original signing identity.

1. **Browser-side DeepSeek send/receive only:** transplant the historical `sendPrompt` DeepSeek one-shot branch into a separately named function called only for the verified DeepSeek provider. It retains the historical composer-text retention check, one confirmed send-button click OR one Enter event when no scoped button exists, and evidence of a new user turn/active generation/new answer, with **no automatic resend**. The existing new ChatGPT/Grok/GLM and attachment submission code remains.
2. **Answer observation:** restore historical assistant-message-only `.ds-message .ds-assistant-message-main-content` parsing before the present strict new DeepSeek reader, and historical DeepSeek generation/answer completion stability leases. Do not use generic Markdown nodes or user message text as an assistant reply. Other provider state handling stays current.
3. **Native stream transport only:** graft **one** historical original method `SidecarProxyServer.proxyDirectNativeSse(InputStream,BufferedOutputStream,Map,NativeEmptyAnchor)` from the hash-verified historical `classes4.dex` into the current compat `classes4.dex`. Every other compatible Smali class and method starts from current version. Main request guard, provider risk holds, 401/403/429, concurrency limits, public relay, native verified owner/Overlay handoff and other code paths remain current. New DEX output kept **DEX 038**.
4. **Version only:** v1.1.4 / 1014 → v1.1.5 / 1015.

**Exactly three non-signature APK entries changed:** `AndroidManifest.xml`, `assets/ai_gateway_bridge/bridge.js`, `classes4.dex`. Classes 1–3 unchanged; 16,860 class definitions and same class set, **zero duplicates**. All launcher/adaptive fox resources and all other APK entries untouched. The current code's other request routing and credential handling are not rolled back.

## Current private artifact checks

- `OmniGate-v1.1.5-OGMAIN15-TRANSPORT-ONLY-ROLLBACK-SIGNED-TEST.apk`
- SHA-256 `5c1916b0402c1d429234d75bd9062cca883160782ce68a9477d6aaa1c5c4a1c4`
- Bytes `171378809`
- Original signer certificate SHA-256 `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`
- Official `apksigner verify`: v2 PASS, v3 PASS; official `zipalign -c -P16 4`: PASS; AAPT2 package/version: PASS.
- Synthetic DeepSeek sender/response tests: 7 PASS; JS syntax `node --check` PASS.
- Exact non-signing entry comparison: ONLY 3 listed entries changed. Same four DEX class sets, no duplicates.
- Private original APK, password, keystore, private `bridge.js`, and signed APK are NOT uploaded to this public repository.

**Acceptance gate open:** Android/device DeepSeek request success, genuine SSE text returned to DSH, account 1→2→1 and cookies persist, concurrent user-request safety, provider holds 401/403/429 all remain **NOT E2E VERIFIED**. Preserve Draft PR #1 and Issue #17.

Device verification should use one fresh short text request with no manually duplicated sending. Verify `/health.lastProxyRequest.nativeSseContentEvents > 0` OR `nativeRecoveredContentFrames > 0` **AND** that the actual DSH reply is received. HTTP 200 with no content is not a PASS. If still empty, fix actual verified original WebDriver answer state, not the app's entire frontend. No automatic duplicate post, retries, unverified answers or account rotation after platform holds.
