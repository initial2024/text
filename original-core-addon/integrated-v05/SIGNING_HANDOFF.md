# Signed v0.5 APK continuation — executed binary integration

GitHub Actions **run 40650355803** passed library compile, D8 output, and fixed certificate identity preflight.

- Frozen original APK SHA256: `920919da7c1dd2ce05b4f1af5055fbbe412ba1773a6e5d1488c3c9d147ca835b`
- D8 integration classes4.dex SHA256: `a87303489a5bec91c9479a19877a87aebbce2b3878d8fd56db3a4d7565de0d1e`
- Integration candidate unsigned APK SHA256: `3c502861bd1e36528c241ac5b38755445e0a4590e805ea53227780afbe70b50a`
- Original classes: 16,160. New: 446. Duplicate class count: 0.
- Original package: com.deepseek2.app, App: com.deepseek2.app.App, versionCode updated 14 -> 15.
- Original ZIP entries preserved as uncompressed content except AndroidManifest.xml; classes4.dex added, stale v1 signer entries removed.
- Real merged unsigned candidate available in ChatGPT conversation, **not stored in this public repository**; do not upload to public release without explicit authorization.
- Neither certificate private key nor passwords were copied from GitHub Secrets.
- Crypto signing pending because the isolated local environment cannot read GitHub Secrets; GitHub Runner lacks securely staged 169 MB original/merged APK.

To finish: use a trusted, temporary private transfer into GitHub Runner with limited-duration scoped access; assert unsigned APK SHA256 above before restoring Secrets; zipalign, sign v2/v3 with exact cert `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`; perform full apksigner verification; output signed **test candidate**. Then perform real Android E2E before declaring production ready.

The local ChatGPT handoff archive contains manifest transformation source, DEX, AAR, unsigned assembly program, evidence, and fixed-key signer script (`sign_on_runner.sh`). Strictly no alternative key and no public APK transfer.


## Update 2026-10-28: locally signed test candidate verified

The user-owned fixed PKCS12 signing material was recovered from the user's private ChatGPT Library file, and verified against certificate SHA-256 `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`. Original v0.5 unsigned merged APK SHA256 matched the frozen audited input. Exact output:

- **Signed test APK**: `OmniGate-Integrated-v0.5-SIGNED-TEST-CANDIDATE.apk`
- SHA256: `e1500c437a543944cda0c6b27542e20dd608a2b2e96e6a5c03a4e58bb60d2bbd`
- Size: 169803690 bytes
- zipalign: PASS
- `apksigner verify --verbose --print-certs`: PASS; v2=true; v3=true; same locked certificate. v1=false; minSdk26 so v1 not required.
- Original package `com.deepseek2.app` preserved; versionCode 14 -> 15; original `com.deepseek2.app.App` preserved.
- Original ZIP member content: 1006 preserved entries with matching SHA256. Only manifest changed, new classes4.dex added, and META-INF signer files recreated by signing.
- Kotlin/class collision audit: original 16160 classes; added 446; duplicates 0.
- No signing private key or password was committed, and temporary local keystore copies were deleted after signature verification. Existing user private Library source file left untouched.
- **STATUS=`PASS_SIGNED_DEVICE_E2E_PENDING`**. Not proven to launch successfully or to serve 8787 on Android device. Do not label as production ready.
- Test caution: stop separately installed Sidecar before launching integrated build to avoid 8787 port collision. Same-package update requires matching signing certificate. Do not wipe original app data.

The APK lives as a conversation attachment; it has **not** been made public on GitHub. The entire signed binary cannot be retrieved from this Git repository alone.
