# OmniGate v0.6.8 — original role-prefix source and webpage→native reply state

## User evidence, 2026-10-29
- Screenshot of v0.6.7: ChatGPT webpage shows the injected user message **`用户：你好`** and an actual assistant reply. Native API floating panel still displays **`文本：已发送，正在等待真正的模型回复…`**. This confirms website roundtrip only; HTTP 200 from 8787 was not visible/proven.
- It is incorrect to assume the visible UI role prefix is added by ChatGPT. Directly inspected user's original `OmniGate_v054.apk` `classes.dex` DEX table and instructions: **`com.deepseek2.app.gateway.ApiServer.extractPrompt()`** contains `用户：` and `助手：` strings, and reads `messages[].role/content`. It intentionally makes role-labeled text even for a single user message.
- Original `WebDriver.readPageState()` returns `PageState`; frozen class exposes getters `getAnswer`, `getGenerating`, `getComposerReady`, `getProviderReady`, `getGeneration`. This is a distinct native cached state, not interchangeable with GeckoBridge DOM diagnosis.

## Changes
1. Added marker ONLY in OmniGate `OverlayTextClient`'s one-message text request: `<<<OMNIGATE_PLAIN_TEXT_V1>>>`. The original native `ApiServer.extractPrompt()` remains untouched; it prepends `用户：`. The private `bridge.js` recognizes `用户：<<<OMNIGATE_PLAIN_TEXT_V1>>>` exactly, strips both before entering ChatGPT composer, and rejects empty/multi-turn-looking marked messages. Other clients, developer/system/tool history, unmarked requests and V2 attachment envelopes retain original role formatting. Do NOT globally strip `用户：`.
2. Added read-only `OriginalCoreBridge.diagnoseNativePageState()` and redacted `WebBridgeDiagnostics` showing both Gecko reply capture and frozen native WebDriver cached reply length, readiness and generating flags. No page text, cookies, credentials or prompts logged.
3. Kept v0.6.7 ChatGPT-specific response extraction and tightened broad article/conversation candidate acceptance to require explicit assistant identity. No generic body.text fallback and no cross-provider change.
4. No original 3 DEX modifications; no extra launcher; retain working web sending, 8787/1601, drag-resizable Text/API/File panel. No provider Stop/cancel or fixed completion deadline.

## Verification
- GitHub Actions [40806585423](https://github.com/initial2024/text/actions/runs/40806585423) completed SUCCESS at branch commit `d1a2edee44962bd6e9e1fd844ba812a7aaa2a17d`: Java compile, D8/DEX structural checks, original fixed signing-certificate identity preflight. Proprietary third-party bridge.js is NOT in public GitHub.
- Private JS role-normalizer regression: **5/5 PASS** (single-user role stripped; unmarked input preserved; history preserved; malformed multirole marker rejected; V2 envelope untouched). Reply recognition regression: **4/4 PASS** (original selector, assistant fallback, reject user turn, other provider untouched).
- Privately assembled/signed `OmniGate_v068.apk`: package `com.omnigate.core`, versionCode 33, versionName 0.6.8, **SHA256 `1216bf1244ca24a1d62aea5e8f5f84354a3b5a42ff787b16aa76d4324ba7067c`**, 170426155 bytes.
- Local checks: ZIP CRC PASS, zipalign PASS, APK signatures **v2/v3 PASS**, fixed cert SHA256 `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`. Four DEX SHA1/Adler32 PASS; 16664 class definitions, **0 duplicate classes**. **1005 original APK entries** content-identical to v0.6.7 including original `classes.dex/classes2.dex/classes3.dex`; only Manifest, compatibility classes4.dex, private bridge.js modified.
- Backup ZIP SHA256 `8b66c6965e4df3202636352570d2504f6f4bfafdd52b8f5158b5724a6bdfd0fd`.

**Gate: STATIC_SIGNED_PASS__PHONE_API_TEXT_COMPLETION_PENDING.** On-device test requires that screenshot shows user bubble without original `用户：` label and without marker, and actual HTTP 200 + nonempty `choices[0].message.content` in floating panel. If webpage replies but API still hangs, compare Gecko assistantReplyChars vs nativeAnswerChars and generating state. No claim of file upload success; no PR merge; original third-party private APK & signer never published to public repo.
