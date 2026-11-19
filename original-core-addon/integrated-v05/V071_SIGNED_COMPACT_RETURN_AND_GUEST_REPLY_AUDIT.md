# OmniGate v0.7.1 — private signed test, compact original Return + guest assistant reply

## User evidence and prioritized failure
- Device's v0.7.0 screenshot: original large blue `返回` window still blocks the bottom corner; ChatGPT displayed an actual answer, yet the local API had previously returned `HTTP 502 {"error":{"message":"no reply"}}`.
- The old bridge diagnosed `assistantCaptureMode=no_scoped_assistant_reply`, `assistantReplyChars=0`, `nativeAnswerChars=0`. This supports the response DOM selector issue, but does not prove the only root cause.
- Re-inspected the original APK: `MainActivity.startServer` and `OverlayService.startGateway` can each create separate `ApiServer` / `WebDriver` instances. Killing `OverlayService` can close its API and driver. Do not use `stopService` as a cosmetic fix.

## Actual implemented changes
1. Public compat Java source adds **read-only actual socket ownership** `OriginalCoreBridge.diagnoseVisibleHostSocket()`. The floating WebBridgeDiagnostics now displays whether the visible MainActivity's actual ServerSocket owns the configured 1601, alongside the separate original OverlayService active state. No request or chat payload is inspected.
2. The existing `OriginalOverlayControl` sends the built-in `ACTION_TOGGLE`, but in the **private APK** the original `classes.dex` has an **in-place four-word Dalvik patch**:
   - `OverlayService.setCollapsed` size `64dp → 44dp`.
   - `OverlayService.onStartCommand` TOGGLE computation becomes idempotent `collapsed=true`, never stop.
   - `showWindow` existing-window path collapses instead of reopening the large rectangle.
   Method length, class definitions and all non-target original instructions remain identical; DEX SHA1 / Adler32 recomputed.
3. Public compat `CompatBootstrapProvider` requests the idempotent collapse after original MainActivity resumes **only when original service is already running**. Does not create/stop an overlay service or interrupt a model reply.
4. Private APK-only `bridge.js` adds ChatGPT guest new-markup assistant selectors: `[data-message-role="assistant"]`, `[data-markdown-text-style="assistant-message"]`, `[data-conversation-role="assistant"]`, `[data-role="assistant"]`. It continues prioritizing the original answer selector and excludes user messages, user bubbles, forms, generic body text; fallback is ChatGPT host only. Other providers unchanged.
5. No new authentication bypass, provider Stop button, automatic request timeout, file-upload enablement, or tunnel exposure.

## Evidence
- Actual Java/D8/DEX/fixed-cert GitHub Actions run [40818761141](https://github.com/initial2024/text/actions/runs/40818761141): **SUCCESS**, source commit `be9e15a0314bee878eabd89afb16f99113d5a94f`.
- Five controlled synthetic guest answer selector regressions **PASS 5/5** including original selector priority, user message refusal, and non-ChatGPT isolation.
- Private signed Android test `OmniGate_v071.apk`, package `com.omnigate.core`, versionCode 36, versionName `0.7.1`, SHA256 `6eba51e797f0ecde27781b67879c52ae71903dc16bdc5305ba8f49b690cbf2c5`, size 175,808,323 bytes.
- Fixed cert SHA256 `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`; APK v2/v3, zipalign, ZIP CRC, four DEX SHA1/Adler32 checks **PASS**.
- 16,667 DEX class definitions, **0 duplicate classes**. Compared to v0.7.0, exactly 1004 ZIP members preserved byte-identically; only Manifest, original `classes.dex`, compiled `classes4.dex`, private `bridge.js` differ. `classes2.dex` and `classes3.dex` are byte-identical.
- Original `classes.dex`: six changed bytes in instruction area (four 16-bit words), plus updated SHA1/Adler32 header. Modified original core is **deliberate and disclosed**, not misrepresented as frozen.
- Private backup ZIP `OmniGate_v071.zip`: SHA256 `5b316adfed1eb5681ba5ae22d06f8b550bf4de12d2de680fcd8ec8a8f03c79d9`, with APK, Chinese readme and JSON audit. APK, original proprietary bridge.js and signing material **not uploaded to public GitHub**.

**GATE: PASS_STATIC_SIGNED__ANDROID_RETURN_AND_HTTP_REPLY_E2E_PENDING.** Device must establish smaller original Return surface, model web still usable, read-only 1601 socket ownership, and HTTP 200 + nonempty `choices[0].message.content` from actual local client. If 502 persists, check socketOwner, assistantCaptureMode and nativeAnswerChars. Image/file upload remains NOT VERIFIED. Do not merge this PR or clear user data.
