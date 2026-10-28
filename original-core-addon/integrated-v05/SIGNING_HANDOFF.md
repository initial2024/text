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
