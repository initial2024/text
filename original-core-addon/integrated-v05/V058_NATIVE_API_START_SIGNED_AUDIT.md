# OmniGate v0.5.8 — user-requested native upstream recovery

## Evidence leading to repair
- User v0.5.7 Android screenshot: localhost 8787 healthy but native 1601 unavailable; real model/file sending blocked.
- Verified against original frozen classes.dex: com.deepseek2.app.ui.MainActivity has private final `startServer()` / `stopServer()` / `toggleApi()` methods. `startServer()` invokes ApiServer constructor/start; `MainActivity.onDestroy()` invokes `stopServer()`.
- Original Prefs.INSTANCE.port(Context) returns configured port, and Prefs.INSTANCE.key(Context) returns original API key; a hardcoded 1601/blank upstream key is insufficient.

## Changes made in isolated work branch
- Added `OriginalCoreBridge`: only on explicit settings click, launch original `MainActivity` with one-time intent flag; ActivityLifecycleCallbacks on MainActivity resume invoke original private `startServer()` via reflection after its UI initialization, then report result. No start at boot. Keep native MainActivity alive when checking settings; destroying it stops the server.
- Sidecar upstream target port read from original Prefs.port(Context) with validated port range and fallback 1601. The original native key is read internally only when no user-configured upstream key exists; never written to logs or the repository.
- Health now reports real configured upstream target and explicit native-start status. Missing native upstream produces JSON HTTP 503 `native_api_unavailable` instead of a silent disconnect.
- Original three DEX, original GeckoView libraries and original browser bridge.js remain byte-identical to working v0.5.4.
- Existing draggable API chip and compact settings UI retained; chip also attaches to original MainActivity to navigate to settings **without destroying MainActivity**.
- No automatic foreground-service launch, no provider Stop/cancel injection, no forced response read timeout; website account restrictions, original native timeouts and Android process lifecycle still apply.

## Actual build verification
- GitHub Actions [40685187830](https://github.com/initial2024/text/actions/runs/40685187830): latest source commit 21d098a27d862a968922b8b17cc2ebfea363d408. CI compile/D8/DEX gate/fixed certificate identity checks successful in this run (runner job steps observed).
- Signed private APK `OmniGate_v058.apk`: sha256 `ea046be6d94524cca060cf823060ca8199a6aabde016f57741e6a84d0cfe95f8`, 179994538 bytes.
- Manifest: package `com.omnigate.core`, versionCode 23, versionName 0.5.8. Single launcher; internal settings and bootstrap provider. 147 original XML nodes + 3 additive nodes.
- Exactly 1006 non-manifest/non-signature original core entries copied identically.
- All four DEX SHA1 + Adler32 + string ordering PASS. 16628 class defs with no duplicate definitions.
- zipalign PASS; fixed original compatibility certificate sha256 `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`, APK v2/v3 signature PASS.
- Original signing material remained private in local user-owned vault; no key or user APK publicly committed/released. Draft PR only.

**Status: SIGNED_STATIC_PASS__NATIVE_API_ANDROID_RUNTIME_PENDING.**

Test: install over v0.5.7 without wiping; original web must still open. Settings -> '启动原核心 API（自动读取端口）' -> keep MainActivity active -> tap its API chip -> Settings -> verify actual native port upstreamReachable true -> run real text completion -> image/file round trip only afterward. If startServer reflection errors, record its class and collect targeted AndroidRuntime logs rather than claiming success. Do not close original MainActivity before testing, as onDestroy calls stopServer.