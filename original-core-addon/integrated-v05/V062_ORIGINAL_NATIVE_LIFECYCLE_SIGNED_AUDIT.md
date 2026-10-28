# OmniGate v0.6.2 — Native lifecycle recovery against original APK

## User evidence and original binary

User reports the original webpage text chat working while v0.6.1 local `127.0.0.1:8787` listens and original `1601` does not connect, with the UI sometimes showing inconsistent TCP status.

Grounded directly in user-owned private original APK `OriginalCore-v04.apk`, not assumptions based on Settings UI:
- Exact frozen original main DEX SHA-256 `c54743ff1b76c6449dad5a1e3199eee5c2dc8dde56f6a87e84cfe95d7669bd35`.
- `MainActivity.onCreate()` invokes `startServer()` after browser, provider, WebDriver initialization.
- `MainActivity.onDestroy()` invokes `stopServer()`, driver.close, popupHost.closeAll, browser.close, controller.close.
- `startServer()` itself invokes `stopServer()` before constructing new `ApiServer`, so unconditional repeat starts disrupt active sessions.
- The original `MainActivity` owns private instance fields `port` (int) and `server`. `savePort()` calls `Prefs.setPort` and restarts the server when appropriate. Mutating Prefs without syncing the *already-open* Activity's private port can leave the existing instance at the old port.

## Actual v0.6.2 changes
- Native bridge now reuses a live original `MainActivity` (`FLAG_ACTIVITY_REORDER_TO_FRONT|SINGLE_TOP`) rather than creating another owner when possible. If clicked while inside original model host, operates on that same Activity without navigation.
- Explicit user-click action checks original `MainActivity.server.isRunning()` using reflection, aligns instance port with verified `Prefs.port`, prevents 8787 proxy self-loop, calls original `startServer()` **only when no working native server** and without auto-retries. Reports errors by exception class; no secrets.
- `GET /health` now exposes separate `originalOwnerAlive`, `originalServerRunning`, `originalLifecycle` alongside transport reachability. HTTP 200 or TCP connect alone cannot prove the model API works.
- Console local/native badges and inline health status distinguish real native server owner from arbitrary open sockets; small API panel includes manual "恢复原版 API（不关闭网页）".
- Do not alter original GeckoView, original browser injection `assets/ai_gateway_bridge/bridge.js`, first three DEX, original installed `com.deepseek2.app` or account data. Original file/image upload not claimed fixed.

## Build and verification
- Branch `work/omnigate-integrated-v05`; Draft PR #1, no merge.
- GitHub Actions [40707715151](https://github.com/initial2024/text/actions/runs/40707715151) **completed success** at source commit `088fec8db45113076690eac81ffcd0fb2a280a07`: Java/AAR, D8, string-order/checksum regression, fixed certificate identity preflight.
- Local private signed test APK `OmniGate_v062.apk`: SHA256 `7e307be27d6b154cfc93cdbf04ab3c98e32fc412654ea59d8798ca7adb621d5d`, 180006826 bytes. Package `com.omnigate.core`, versionCode 27, versionName 0.6.2, fixed signing cert `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`.
- Independent local ZIP CRC, zipalign, APK v2/v3 signatures, all four DEX SHA-1 / Adler32 / sorted string_ids PASS; 16653 class defs, **0 duplicates**; exactly one launcher; 1006 original archive entries and original 3 DEX + bridge.js preserved byte-identically.
- Private signed binary delivered as conversation attachment only. No private signing keys, original APK, or account data were pushed to public repository.

**GATE: PASS_STATIC_SIGNED__ANDROID_NATIVE_LIFECYCLE_AND_MODEL_E2E_PENDING.** The actual vivo phone has not run v0.6.2. Until it does, cannot call 1601, API model response, original floating control, or file injection PASS.

## Required real-device checks
1. Upgrade from v0.6.1 without uninstalling or wiping data. Original Grok/ChatGPT text chat must continue working.
2. While inside actual original model page, expand 40dp in-app diamond → API tab → "恢复原版 API（不关闭网页）". Re-read status from the same Activity, avoid closing model page.
3. Check Settings `/health` including `originalOwnerAlive`, `originalServerRunning`, `upstreamReachable`. If any false, record exact result; do not mark service as running.
4. After native owner and port are verified, perform an actual short text API completion via loopback 8787. Only then test the real webpage attachment round trip. `HTTP 502/no reply` remains failure.
5. If original `MainActivity` is destroyed the native server WILL stop per original `onDestroy`; background persistence needs separate source-level refactor, not repeated UI tricks.
