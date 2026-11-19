# OGMAIN14 — Verified native-empty-SSE DeepSeek scoped DOM bridge recovery

**Date:** 2026-11-04. **State:** private same-cert APK signed and static checks PASS; Android E2E NOT TESTED.

## Ground truth from user handset

`/health` shows `upstreamHttp=200`, `downstreamHttp=200`, `nativeSseBytesRead=314`, `nativeSseDataEvents=3`, `nativeSseContentEvents=0`, `nativeSseDoneMs=9914`, `nativeEmptyRecovery=waiting_for_verified_web_answer`, `nativeRecoveredContentFrames=0`, `nativeConcurrencyInFlight=1`. Exact native owner is verified OverlayService on port 1604. This is **not** a local 409 or 503 gate; the original native SSE completed with only control frames and recovery could not yet attest a matching DeepSeek browser assistant answer. No success should be claimed from HTTP 200 or an editor draft.

## Regression against earlier known successful flow

A 2026-10-30 v0.10.11 run previously reported the first nonempty native SSE content at 10683ms and **32 content events**. The current request has zero content events at roughly the same time. Earlier success was raw native SSE forwarding, not proof that the current fallback DOM recovery works.

## Private bridge implementation

`assets/ai_gateway_bridge/bridge.js` in the user-owned original APK did not have DeepSeek-specific fallback logic when native `config.selectors.answer`, `config.selectors.composer`, or `config.selectors.sendButton` became stale; fallback paths were ChatGPT-specific. It could therefore leave a DeepSeek DOM reply out of both JS `readState().answer` and native `PageState.answer`.

The private-only patch:
1. Checks strictly that `config.provider === "deepseek"`, `location.origin === "https://chat.deepseek.com"`, and the top-level frame.
2. First attempts visible assistant-scoped DeepSeek answer nodes, using `.ds-assistant-message-main-content .ds-markdown` and explicit `[data-*-role="assistant"] .ds-markdown` containers. **Never** treats a generic `.ds-markdown` (which can also contain user text) as an answer; excludes user-role, form, textbox and editable descendants.
3. Falls back to the original frozen answer selector if scoped DeepSeek reply does not exist. ChatGPT logic is unchanged.
4. Tries a unique live `textarea#chat-input` (or contenteditable equivalent) for the DeepSeek editor if the frozen composer selector no longer matches. Retains no-duplicate-send and refuses ambiguous editable controls.
5. Finds a single enabled visible DeepSeek send button only within the recognized provider origin; two candidates are refused. No automatic retries.
6. Accounts for explicit user-role confirmation on a newly observed DeepSeek turn in addition to the existing verified editor-clear + generating/answer evidence.

**No public proprietary original `bridge.js` or private APK/keystore uploaded.** The public repo can include a patch plan and pure regression harness, but not original user private binary assets.

## v1.1.4 private signed candidate

- File: `OmniGate-v1.1.4-OGMAIN14-DEEPSEEK-REPLY-SIGNED-TEST.apk`
- SHA256: `f0a85e7894768656fa540c04ffdc464094a7eb7ce727a5fb1f8c86b2caf963cd`
- Bytes: `171366521`, package `com.omnigate.core`, versionCode 1014.
- Certificate SHA256 remains `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`.
- APK v2/v3 verified PASS, 16 KiB zipalign PASS, ZIP CRC PASS, Android manifest PASS, 7 JavaScript synthetic scoped-selectors/single-send tests PASS.
- Compared with v1.1.3, only `AndroidManifest.xml` and `assets/ai_gateway_bridge/bridge.js` changed (ignoring regenerated META-INF signatures), verified by CRC+uncompressed size. Classes.dex 1-4, account/Overlay guard, sidecar SSE, fox launcher assets unchanged.

## Hard stop

No real-device install or full send/answer/SSE E2E verified. If the native SSE remains empty and reply recovery does not progress, next **read-only** diagnostic should inspect verified owner page metadata: `lastSubmissionSequence`, `deepseekSubmissionStatus`, `lastSendOutcome` fixed categories, `assistantCaptureMode`, `assistantReplyChars`, `assistantReplyChangedSinceSend`, and native cached `getAnswer().length`. Do not log prompts, tokens, cookies or page content. Never auto-click twice, resubmit another HTTP request, or fabricate model text. A manually clicked page message may not belong to the API turn and cannot be silently conflated.

Keep Draft PR #1 and Issue #17 open; no production-release claims.
