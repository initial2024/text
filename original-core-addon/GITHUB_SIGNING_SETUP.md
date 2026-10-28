# One-time GitHub Actions signing setup

The repository is public. Never paste the private key or passwords into source files, issues, pull requests, workflow YAML, release notes or logs.

Open:

**Repository → Settings → Secrets and variables → Actions → New repository secret**

Create these three repository secrets exactly:

1. `OMNIGATE_KEYSTORE_B64`
2. `OMNIGATE_KEYSTORE_PASSWORD`
3. `OMNIGATE_KEY_ALIAS`

The private values are supplied separately outside the repository.

After all three secrets exist, manually run:

**Actions → Build Original Core Sidecar APK → Run workflow**

The workflow must show the locked certificate fingerprint:

`BF:DC:4D:E8:53:80:66:D2:9E:CD:17:E8:0A:B6:51:D3:8A:E3:C4:40:71:3F:96:51:BD:8C:E1:C8:10:15:D2:7D`

If the fingerprint differs, stop. Do not publish the APK.

The workflow has no ephemeral-key fallback. Missing secrets cause an intentional build failure.
