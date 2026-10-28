# TASK_NAME: OMNIGATE-V05-INTEGRATED-APK-BINARY-BUILD-AND-E2E-GATE

## Inputs
- Original frozen signed core APK (not in public Git): SHA256 `920919da7c1dd2ce05b4f1af5055fbbe412ba1773a6e5d1488c3c9d147ca835b`
- Sidecar signed APK: SHA256 `78e19ea6c620048a9ca18e565ea508b2a7413506774dbb80cbdcd6c7a66964cd`
- Existing reviewable source at `original-core-addon/integrated-v05`.
- Expected cert fingerprint: `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`

## Strict authorization
Work ONLY on an isolated derived directory and this review branch. Never mutate original input APK, `original-core-addon`, `main`, production Cloudflare or published releases. Actions permitted on this public repository using standard ubuntu-latest only. Inspect Actions runner/security/settings and storage limits. Do not use paid larger runners. Do not create a replacement keystore. Do not print keys or passwords.

## Build work
1. Re-check CRC, all SHA256, APK v2/v3 signed block and, when Android SDK is available, full `apksigner verify --verbose --print-certs`.
2. Compile `integrated-v05/library` under Android SDK; fix compile problems inside the new isolated integration module only. Preserve SidecarProxyServer forwarding/streaming, CloudflareRelayClient and original upstream behavior.
3. Resolve all duplicate DEX classes by true dependency closure/relocation/exclusion, never by silently accepting duplicate `class_def`. Cross-check the same Kotlin/OkHttp ABI version. Assert 0 duplicate class definitions in output and no missing references.
4. Inject private bootstrap provider, service and settings Activity into original binary manifest. Keep `com.deepseek2.app`, `com.deepseek2.app.App`, original launcher, GeckoView, file provider, overlays, original resource table/assets/libraries. Increment versionCode from 14 to >=15, with same minSdk 26; do not change targetSdk 34 without separate behavior checks.
5. App starts original UI normally, schedules integrated 8787 service only when foreground allowed, no restart storm. If original `App` cannot run safely twice, keep service in default process until verified. Expose status to user; show real startup errors instead of claiming success.
6. Localhost any-nonempty-key only. Public relay exact-key. Passwords stored privately, relay disabled by default; no private Sidecar UID prefs scraping. Original provider login still required.
7. Guard long generation: no hard silence cutoff, no stop-button disappearance heuristic, no arbitrary Stop/cancel, no automatic rollover except explicit provider limit. Upstream response drains after downstream disconnect, subject to OS killing process.
8. Use original fixed signing key obtained from user's trusted private environment or GitHub Actions Secrets (not stored in repository). Zipalign, sign v2/v3 and verify exact cert. If key missing, STOP: do not ship an APK branded "final".
9. Run static source & resource preservation diff: every unchanged original zip entry, especially `assets/ai_gateway_bridge/bridge.js` and `lib/**`, must match byte for byte. Enumerate all deliberately changed entries.

## Real-device E2E (mandatory before release)
- cold launch; original four login sessions and visible textbox/input/send; files and images; GeckoView navigation; floating window; original 1601; 8787 health and /v1/models; streaming + long reasoning; app background/foreground; downstream disconnect/EOF; true context limit once and up to three rollovers without Stop; accidental no-progress must not truncate; local any-nonempty key vs missing key; strict public key/relay; notification start/stop; uninstall/update compatibility with same signer and package; Android 14,15,16 when possible.
- App sandbox migrations require explicit export/import or re-entry; do not claim preservation of separate Sidecar settings.

## Evidence and stop conditions
Evidence: signed APK sha256, original-to-final zip entry diff, class_def collision count=0, manifest+permissions+version report, v2/v3 apksigner output, test logs/screenshots, original core runtime behavior results.
Stop on signature mismatch, missing SDK/signing key, irreversible original core changes, unresolved ClassNotFound/VerifyError, runtime Gecko crash, FGS startup denial, file upload regression, undesired Stop, or incomplete E2E. Status must remain BLOCKED, never PASS.

## Output
One `OmniGate-Integrated-v0.5.apk` only if ALL build/sign gates pass. Otherwise provide `source-candidate.zip` and a precise blocking report. Do not merge PR or deploy without explicit authorization.

## Mandatory post-crash DEX gate (2026-10-28)
Android bugreport of v0.5.3 showed `Out-of-order string_ids`, followed by `ClassNotFoundException` for the original `Application`. Directly replacing DEX string values while preserving the existing `string_ids` ordering corrupted the main DEX despite a valid APK signature.

**Fail closed before sign or artifact publication:**

- Run `python3 original-core-addon/integrated-v05/tools/test_dex_integrity.py` on each compiler pipeline change.
- Run `python3 original-core-addon/integrated-v05/tools/check_dex_integrity.py PATH_TO_CANDIDATE.apk` on **every** assembled APK, testing all classes*.dex members (string ordering, SHA-1, Adler32, ZIP CRC). If using D8-only output, stage it as `classes.dex` in a temporary ZIP and run the same gate.
- Never perform raw byte-level package-name replacement inside a DEX string table and then merely update checksum. Use full DEX reassembly from source (D8/R8 or a structured DEX assembler), with independently validated class defs, references and indices.
- Preserve original launcher and Application class definitions and verify manifest references. ClassNotFound alone may be downstream of invalid DEX loading; inspect *suppressed* exceptions in logcat.
- **Runtime gate**: actual user-device cold launch remains mandatory even if DEX/order/signature all pass. Currently v0.5.4 is `PASS_STATIC_DEX_FORMAT_DEVICE_LAUNCH_UNCONFIRMED`. No untested v0.5.5, new splash or Sidecar service release.
- Separate `com.dsh.android` crash in the same device bugreport is a different package and must not be attributed to `com.omnigate.core`.
