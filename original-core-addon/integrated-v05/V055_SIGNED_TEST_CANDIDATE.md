# OmniGate v0.5.5 — integrated signed PRIVATE test candidate

**Status: PASS_STATIC_SIGNED_ANDROID_E2E_PENDING.** Never label as production release.

Original core: user-verified v0.5.4 parallel `com.omnigate.core` baseline. v0.5.4 exact SHA256 `39d495b115c0dd9a3f4614d5ece55bfb921611a83c35d12df6b26c193f1d6046`, user confirmed ChatGPT/Grok webpages display, send and switch successfully.

Signed v0.5.5 test APK `OmniGate_v055.apk` SHA256: `93ca311d4e5e7fb3a23d87be67b298b1dc0f35d6f74bd4a86e50ecc617703b08`, size **179982250** bytes. Not uploaded to public GitHub; provided only to user via ChatGPT.

- Package `com.omnigate.core`, versionCode **20**, versionName **0.5.5**; compatible with v0.5.4 same signing certificate SHA256 `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`.
- Original `classes.dex`, `classes2.dex`, `classes3.dex`, `assets/ai_gateway_bridge/bridge.js` SHA256 identical to device-tested v0.5.4; entire original GeckoView and other 1006 core archive entries content unchanged.
- Newly compiled standalone `classes4.dex` SHA256: `3792efe445cb18f76306be1fc770e58e9d36a34b9ba2a683ad2e6862c870ae8c`. CI compile + D8 DEX validation + certificate test [40675109210](https://github.com/initial2024/text/actions/runs/40675109210) PASS.
- Android DEX class_defs: 7160 + 7628 + 1372 + 451 = **16611**, **0 duplicates**.
- All four DEX string_ids sorted, DEX SHA-1 / Adler32 PASS; ZIP CRC, zipalign, APK signature v2/v3, fixed cert PASS.
- Manifest adds **only** `com.omnigate.integrated.SidecarService` (non-exported) and `com.omnigate.integrated.CompatSettingsActivity` (explicit second Launcher icon `OmniGate API 设置`). There is **NO** bootstrap provider, no automatic 8787 start, no boot receiver. All prior application, launcher, GeckoView services, providers intact.
- API settings page has save upstream/public/relay keys, local 8787 manual start/stop/status, copy base URL, open original provider page for photo/file input. Relay off by default. Original provider website login requirements remain.
- Original injected `findFileInput`, `uploadAttachments` and v2 binary attachment envelope preserved byte-for-byte. File/image upload inside provider web UI remains supported by original logic **but must undergo separate v0.5.5 device testing**; the /v1 proxy forwards HTTP request bodies byte-for-byte, but acceptance of a specific OpenAI multimodal or Files API wire format at 1601 is **UNVERIFIED**.
- Sidecar `HttpURLConnection.setReadTimeout(0)` is retained; no proxy artificial response read deadline and no proxy Stop call. The original 1601 native gateway may have a 3600-second Future timeout, provider-side limits still apply; do not claim infinite generation.

**User testing gate**: install as update to v0.5.4 without wiping any data. Confirm original first launcher still works. Open SECOND launcher `OmniGate API 设置`, tap start 8787, refresh, then use phone browser `http://127.0.0.1:8787/health`. Check models and text response, file upload in provider web UI, only then API attachment formats. If any crash collect redacted FATAL EXCEPTION; leave `main`, original `com.deepseek2.app`, signing secrets and PR unmodified. No merge, no release.
