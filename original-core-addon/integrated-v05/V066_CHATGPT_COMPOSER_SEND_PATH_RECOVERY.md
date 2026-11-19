# OmniGate v0.6.6 — actual ChatGPT editor selection and send-path recovery

## User evidence
- v0.6.5 user screenshots: loopback 8787 listening, original `MainActivity.server.isRunning=true`, 1601 TCP connected. Original bridge `provider=chatgpt; DOM=complete; topFrame=true; hostMatched=true; genericEditableCount=1; genericVisibleCount=1`; **original provider composer CSS selector matches 0**; answer=0; guest readiness=false. This specifically points to a stale composer selector, not a server-port failure.
- Model response never returned; no claim that an unauthenticated provider server has authorized the request.

## Fix scope
- Existing original three DEX and GeckoView libraries untouched; preserve v0.6.5 working resizable Text/API/File panel. No PR merge.
- Targeted private APK-only `assets/ai_gateway_bridge/bridge.js` patch: for **ChatGPT, top-frame, https://chatgpt.com only**, when old composer selector fails, find a **unique visible writable contenteditable or textarea**. Reject zero or multiple candidates. This fallback is never applied to other providers or origins.
- Unify the editor resolver across `guestComposerEligible`, `readState().composerReady`, `replayAfterRollover`, `uploadAttachments` composer context, and **actual `sendPrompt`**. Prior versions only changed the *readiness* gate but not the *real send* selector.
- Update actual send path to attempt editor input, then wait up to 4 seconds for a single usable Send button. Failure results in `no_composer`, `ambiguous_editables`, `unsupported_editor`, or `send_button_not_ready`, rather than false “ok” or indefinite silent scheduling. A click is labeled `send_button_clicked_unconfirmed`; not proof of website acceptance. Never click provider Stop. Read timeout for model answer unchanged.
- Extend read-only original bridge diagnostics with `composerSelectionMode`, `effectiveComposerReady` and `lastSendOutcome`, surfaced through the Java `WebBridgeDiagnostics` panel. No cookie/token/page text collected. Full proprietary original bridge script is not committed to public GitHub.

## Actual verification
- Five Node.js synthetic tests PASS: ChatGPT unique visible editor → legacy selector miss but real send-button click; missing editor → block; ambiguous editors → block; other provider → no fallback; original selector still works → prefer it.
- GitHub Actions [40732977267](https://github.com/initial2024/text/actions/runs/40732977267) **completed success** for exact branch Java diagnostic source commit `1bba64e5e0d3fac98c7d3f706a770be80cf6dab8`: compile, D8/DEX structure and fixed signing identity.
- Privately assembled signed test APK `OmniGate_v066.apk`, package `com.omnigate.core`, versionCode=31, versionName=0.6.6; SHA256 `73c675ac9df595f3882ed1e77835063f07dca8553bc9a20634413b457c3a6879`; 170422059 bytes. Same signing certificate fingerprint `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`; APK v2/v3 PASS; zipalign PASS.
- Four DEX checksums/sorted strings PASS; 16664 classes, 0 duplicates; 1005 retained original ZIP entries CRC/size match v0.6.5 and 3 original DEX are byte-identical. Modified: versioned AndroidManifest.xml, new classes4.dex, private injected bridge.js. Binary is NOT uploaded to public repository or GitHub Release.

**GATE: PASS_SIGNED_STATIC__ANDROID_REAL_TEXT_E2E_PENDING.** Test original ChatGPT page -> float Text -> diagnose unique-visible-chatgpt-editor -> send once -> diagnose lastSendOutcome. Only actual HTTP 200 + nonempty `choices[0].message.content` passes real text E2E. If `send_button_clicked_unconfirmed` but still no reply, next examine provider send-button handler and answer selectors, not network ports. File upload remains disabled/unverified until text E2E passes. No automatic stop, production deployment, data wipe, or original app replacement.
