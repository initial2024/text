# OmniGate v0.6.3 — ordinary-text first, file upload gated

## User evidence from v0.6.2
The user-provided Android screenshot shows the original ChatGPT webpage visible with Login / Sign up actions and a Google sign-in prompt, while the in-app floating API/File panel is visible. File injection was not confirmed; past test showed HTTP 502 `no reply`. The user explicitly prioritized **ordinary text input and real reply first**.

## Implemented
- Added `OverlayTextClient` that sends a **plain** OpenAI-compatible chat-completion request through `http://127.0.0.1:8787/v1/chat/completions` to the frozen original native API; **no V2 attachment envelope and no files**.
- Uses the actual provider from original `Prefs.provider(Context)`, requires original live MainActivity/ApiServer ownership and non-conflicted real native port; starts the loopback 8787 listener only after explicit user send if necessary.
- Maintains draft/status/last text reply in volatile process memory; does not log private text or persist a transcript. Prevents concurrent duplicate sends. Does not auto-click website Stop, does not use a finite read timeout, and keeps draining response even if display truncates it.
- Parses HTTP code and `choices[0].message.content`. **HTTP 200 without text is not PASS**. HTTP 502/no reply surfaces an explicit diagnostic.
- Changed the existing 40dp model-adjacent floating dock to **three tabs:** 文本 (default), API, 文件. Text tab includes prompt, send, last HTTP result, actual model response and copy reply. API tab retains native service recovery. File send remains disabled until *the same model* has produced a real text reply.
- Frozen original 3 DEX, GeckoView native libraries, web injection `assets/ai_gateway_bridge/bridge.js`, original app identity, single launcher all preserved.
- Original website login requirements still apply; this is not a guest login bypass. Single text E2E is a gate, not proof of durable multi-turn context.

## Actual build evidence
- GitHub Actions [40717896000](https://github.com/initial2024/text/actions/runs/40717896000) **SUCCESS** for current Java source compile, D8, DEX sorted string IDs/Adler32/SHA-1 regression, fixed signing identity preflight. Earlier intermediate compilation failure from variable shadowing was fixed on later commit.
- CI artifact `OmniGate_v063_compiled_dex_CI.zip` DEX verified against its included SHA-256.
- Locally signed PRIVATE test APK `OmniGate_v063.apk`: SHA256 `f13a5cfce51ed71bf970e60fa938444a18d74f06d3e360cbbbcaba942cfb3c93`, 169750243 bytes. Package `com.omnigate.core`, versionCode=28, versionName=0.6.3, fixed cert SHA256 `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`.
- Actual apksigner verification: **v2=true, v3=true**, v1=false. zipalign `-p 4` PASS. APK ZIP CRC PASS, compiled DEX SHA matches CI, all four DEX ordering/SHA-1/Adler32 PASS, 16658 class definitions, **0 duplicates**.
- Exactly 1006 prior APK content entries preserved byte-identically; only AndroidManifest.xml (version name/code) and classes4.dex content changed. Single launcher unchanged, no new exported components.
- Backup ZIP and separate machine-readable static audit created privately, without exposing signing material. The temporary signing key was cleaned after use.
- **GATE: PASS_STATIC_SIGNED__ANDROID_TEXT_E2E_PENDING.** Real 8787 plain chat response and website login-dependent functionality have NOT been verified on user phone. Actual file/image upload NOT VERIFIED, not a release.

## Device instructions
1. Install v0.6.3 over existing v0.6.2 (do not uninstall original AI Gateway or wipe any data).
2. Enter original ChatGPT/Grok model webpage, confirm ordinary web text chat works. Sign in normally if the website requires it.
3. Expand OmniGate ◈ mini floating dock; default tab 文本. Send `你好，请只回复：文本接口测试成功。`.
4. If original API is inactive, switch API tab and click 恢复原版 API（不关闭网页）; return to 文本 and resend.
5. Capture a redacted screenshot of exact status: **only HTTP 200 + nonempty choices[0].message.content is PASS**. If 502/no reply, stop and investigate original bridge/WebDriver/session readiness; no further blind binary modifications.
6. File tab stays gated until same-model text PASS, after which attachment acceptance still needs independent website evidence.

No merge, public APK upload, key disclosure, original-app uninstall, data wipe, or background automation.
