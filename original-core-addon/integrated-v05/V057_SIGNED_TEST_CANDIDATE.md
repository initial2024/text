# OmniGate v0.5.7 — draggable in-app API chip, compact console, 1601 entry

## Device evidence from prior v0.5.6 user screenshots
- The original provider selection page displays a single in-app API chip.
- Settings Activity launches successfully.
- 8787 /health returns HTTP 200 with ok=true, upstreamReachable=false. Thus only loopback proxy readiness is verified; no real completion or attachment delivery is verified.
- Native Android document picker selects a JPG file. Actual website upload and 1601 completion still not verified.

## Scoped changes
- CompatBootstrapProvider uses bounded raw touch events for drag vs click; persists normalized X/Y in private SharedPreferences. No auto service start, no extra launcher.
- CompatSettingsActivity groups actions into compact sections, adds purple-blue hero, local/upstream health badges, side-by-side start/stop, collapsible advanced keys, higher-contrast model picker.
- The 8787 HTTP test now summarizes local and 1601 status instead of showing raw JSON.
- If 1601 is unreachable, attachment submission explicitly blocks and says file was selected locally but not sent.
- Adds explicit internal navigation to original `com.deepseek2.app.ui.MainActivity` because frozen DEX methods `startServer` and `stopServer` live there. Not proven that navigation alone starts the service.
- Retains original webpage bridge, file picker, GeckoView, provider session and all three original DEX unchanged. No arbitrary Stop action or proxy response read timeout added.
- Background foreground-service keepalive, new icon, splash animation and original website upload fixes remain deferred.

## Actual independent static verification
- GitHub Actions: https://github.com/initial2024/text/actions/runs/40681251776 compile, D8, DEX order regression, fixed certificate preflight PASS.
- Signed private test APK: `OmniGate_v057.apk`, SHA256 `fe87080597e065615a74b3f8fda50d602bfc08da88b872a8537e1550343344f4`, 179994538 bytes.
- Package `com.omnigate.core`, versionCode 22, versionName 0.5.7; fixed certificate `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`.
- ZIP CRC, zipalign, APK v2/v3 signing PASS.
- `classes4.dex` SHA256 `da474acd62f3cc787ab8084adb2f6c4d39259c042e4ce69017012da0276d1bfc`.
- Original 1006 archive entries preserved content-identical; three original DEX and original bridge.js identical to user-verified v0.5.4 baseline.
- Total 16625 class definitions and no duplicates; all four DEX string ordering, SHA-1 and Adler32 PASS.
- Exactly one launcher intent filter and one internal settings Activity.

**STATUS: PASS_STATIC_SIGNED__V057_ANDROID_E2E_PENDING.** No public binary release, no PR merge, no uploaded private key. Test drag, settings visibility, internal 8787, 1601 manager, original ChatGPT/Grok and file upload separately on real phone. Do not claim the native picker proves provider upload.