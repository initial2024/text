# OmniGate v0.6.7 — ChatGPT reply extraction and true HTTP status

## User evidence
- v0.6.6 user screenshot shows a real ChatGPT webpage user message injected through OmniGate and a genuine displayed reply: `文本接口测试成功。`.
- The currently visible portion of floating text dock does **not** show the active HTTP result field. In particular, orange `HTTP 502/no reply` in older screenshots was **static explanatory text**, not proof the latest request returned 502. Do not misstate the result.
- Web input injection is visibly working; local 8787 and original native 1601 were independently confirmed running by v0.6.5 evidence. **True 8787 completion still requires actual HTTP 200 with nonempty model text.**

## Scoped v0.6.7 changes
- Private signed APK-only narrow patch to `assets/ai_gateway_bridge/bridge.js`: `readState()` and `sendRelayText()` baseline now use a common `chatgptAnswerText()` resolver.
- Preserve original configured answer selector as first priority. For **ChatGPT at exact https://chatgpt.com top-frame only**, if it has no answer, use conversation-scoped assistant reply selectors (`[data-testid="assistant-message"] .markdown`, `main [data-message-author-role="assistant"]`, `main .agent-turn .markdown`, `main [data-testid^="conversation-turn-"] .markdown/.prose`, `main article .markdown`) with exclusion of user-message and form containers. No broad body.innerText fallback. Other providers unchanged.
- Diagnostics report **only redacted** `assistantCaptureMode`, `assistantFallbackMatches`, `assistantReplyChars`, and `assistantReplyChangedSinceSend`, not message contents.
- Public-source Java `WebBridgeDiagnostics` displays these safe counters. Floating `ApiQuickPanel` footer pins the live **actual** status of the local text request: waiting, HTTP 200 with content, HTTP error, or untested, independent of scroll. Prior generic 502 example text is explicitly labeled as generic help.
- No changes to original 3 DEX, original AI provider accounts, native 1601 lifecycle, GeckoView, upload protocol or original 8787 proxy behavior.
- No automatic Stop or fixed read timeout. File upload requires real same-model HTTP 200 and remains unverified.

## Verification
- Node simulated web bridge regression **4/4 PASS**: preserve legacy answer, ChatGPT assistant fallback, refuse user-turn false positive, never apply fallback to Grok.
- Actual GitHub Actions [40804729827](https://github.com/initial2024/text/actions/runs/40804729827) completed SUCCESS at source commit `77e55d45fb00867f091e0d2573a8c7bbc3d4ac03`: Java compiler, D8, DEX regression, fixed signing identity. The private bridge.js patch is **not** in GitHub source, only this explanatory report.
- Privately signed `OmniGate_v067.apk`: package `com.omnigate.core`, versionCode 32, v0.6.7, **SHA256 `f47e06fa024279fc0b1d058ed8c6e6b55cf15b9ab58a197a4aa2bd84cb63e861`**, 170426083 bytes.
- Same fixed signing certificate `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`; APK v2/v3 PASS, zipalign PASS, ZIP CRC PASS.
- CI-sourced compatibility `classes4.dex` SHA256 `333930e5ffb3f85e2395a267f7d16a99209329065e0f2aa5207ce782290f6745`. All four DEX checksum integrity PASS. 16664 class_defs and 0 duplicates. 1005 original APK members preserved content-identical, with expected changes only Manifest, classes4.dex, privately patched bridge.js.
- Private backup ZIP SHA256 `490f1b3c47760b262a515eaf80ea9f38b925274862ee2cf7e2c14ca335ff1ea6`. No signer secrets or private third-party source published.

**Gate: PASS_SIGNED_STATIC__ANDROID_HTTP_TEXT_COMPLETION_PENDING**. User should install over v0.6.6, send one ordinary message from floating Text tab and check bottom persistent HTTP status. If web answers but local API fails, capture only redacted `assistantCaptureMode`, `assistantReplyChars`, and HTTP code. Do not claim file/image upload passed until independent website proof. Draft PR remains unmerged and no production deployment.
