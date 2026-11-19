# OmniGate v0.5.3 — minimal parallel boot baseline

## Outcome
`SIGNED_STATIC_PASS_DEVICE_LAUNCH_UNKNOWN`. This is **not** the integrated product or a production release.

The previous parallel v0.5.1 and v0.5.2 were reported by the user to close immediately upon launch. v0.5.2 disabled auto-start and still failed; absence of an Android FATAL EXCEPTION means the cause has **not** been demonstrated.

## Actual isolated build
- Input: frozen user-provided Original AI Gateway v0.4 APK, SHA256 `920919da7c1dd2ce05b4f1af5055fbbe412ba1773a6e5d1488c3c9d147ca835b`.
- **Directly built from the original input**, not derived from integrated v0.5 or previous failed parallel APKs.
- Output: `OmniGate-Parallel-v0.5.3-CLEAN-BOOT-SIGNED.apk`, SHA256 `1cc6347c37e1dfeb643281479454568ecff3c798fad0f665bfab7cb048ebec68`, 179543920 bytes.
- Package `com.omnigate.core`, versionCode=18, versionName=0.5.3, visible label `OmniGate Boot Test` (original app icon retained intentionally).
- Manifest node count unchanged. Original `com.deepseek2.app.App` and `ProviderActivity` preserved.
- Only `AndroidManifest.xml`, `resources.arsc`, `classes.dex` changed, plus regenerated existing signing entries. There are **no new ZIP entries or Sidecar DEX**, no 8787 module, no startup provider, no high-resolution icon/splash modification.
- 1,004 other original ZIP entries retained byte-identically, including all 26 `lib/**` entries.
- `zipalign -c` PASS; `apksigner verify` v2/v3 PASS; fixed cert SHA256 `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`.
- Private key never committed, original working phone package not uninstalled, no public APK uploaded. The APK and source/audit ZIP were delivered as conversation artifacts.

## Test gate
1. Install in place over old parallel `com.omnigate.core`; keep original `com.deepseek2.app` untouched. No data wipe.
2. Launch only `OmniGate Boot Test`. 8787 must not be expected to exist.
3. If it launches, add compat module only after separate successful device validation.
4. If it crashes, **STOP further blind binary renaming**, request logcat FATAL EXCEPTION/native tombstone or real original buildable source. Do not treat successful static signature verification as runtime PASS.

The presence of a source script and a signed binary is not proof the original provider's GeckoView will run under the new application identity.
