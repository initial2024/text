# DSH Web Gateway 2.6.0

A clean Android rebuild of the DSH → DeepSeek Web gateway.

The app keeps the DeepSeek website login as the model identity and exposes an OpenAI-compatible local/public endpoint for DSH. It does not use the DeepSeek API.

## Why this repository exists

Directly repacking the old 169 MB APK produced Android 16/vivo installation failures. This repository rebuilds the gateway from source with the Android Gradle plugin and validates the APK using official Android build-tools.

Package: `com.dsh.webgateway`

It is intentionally different from the old package, so this test build can coexist with the old AI Gateway.

## Local API

The original gateway port is restored:

- Local Base URL: `http://127.0.0.1:8787/v1`
- Model: `deepseek-chat`
- Health: `GET /health`
- Models: `GET /v1/models`
- Chat: `POST /v1/chat/completions`
- Tunnel probe: `GET /__tunnel_probe`

The server remains bound to loopback. It is not exposed directly to Wi-Fi/LAN.

## Original public tunnel

The rebuild restores the original reverse-tunnel design found in the old APK:

- public suffix: `.deepseeks.dpdns.org`
- generated prefix: `gw-` + 12 lowercase random hex-like UUID characters
- tunnel registration identity: a persistent random prefix plus a persistent tunnel token
- tunnel transport: TLS
- tunnel server: `57.183.5.186:9000`
- pinned server-certificate SHA-256 fingerprint is retained from the old APK
- registration line: `REGISTER <token> <prefix>`
- public address: `https://<prefix>.deepseeks.dpdns.org`

When the public-tunnel switch is off, the TLS tunnel client is stopped while the local API remains available.

### Restoring an old public URL

A URL such as:

`https://gw-33e850180248.deepseeks.dpdns.org`

contains the prefix but not the private tunnel token. Restoring exactly the same old public URL requires both the old prefix and its matching old `tunnel_token`.

The app therefore has an **Import old tunnel identity** action that accepts the old prefix and token, then reconnects using that identity.

Without the old token the rebuild generates a new `gw-xxxxxxxxxxxx.deepseeks.dpdns.org` address.

## Custom public domain

The app can save a custom HTTPS URL, but it does not trust the text alone.

A custom URL becomes the active displayed/copied public Base URL only after:

`GET https://your-domain.example/__tunnel_probe`

returns this gateway's expected probe value.

This means the user's DNS/reverse-proxy layer must actually forward the custom domain to the working gateway tunnel before the app marks it verified.

## API authentication

The gateway generates a persistent API key.

Requests to `/v1/*` require the key through:

`Authorization: Bearer <API_KEY>`

or an accepted API-key header.

The health and tunnel-probe endpoints remain unauthenticated so connectivity/domain verification can work.

## Relay behavior

- DeepSeek website login/cookies provide the identity.
- Hard generation timeout: 600 seconds.
- Generation-end grace: 5 seconds.
- Request-body limit: 32 MiB.
- JPG/PNG/PDF/Word and other DSH attachments are relayed through a real browser `File` / `DataTransfer` flow.
- Attachment delivery is fail-closed: if upload evidence is not observed, the text prompt is not silently sent without its file.
- Full DSH message roles/context are reconstructed for plain OpenAI-compatible requests.
- When DeepSeek reports a conversation/context limit, the gateway may create one fresh DeepSeek chat and replay the current full DSH prompt/attachments.
- Automatic rollover is capped at one per submission.

## GitHub build gate

`.github/workflows/android.yml` must pass all of these before the APK is published:

- source-contract checks,
- Gradle `assembleRelease`,
- `zipalign -c -v 4`,
- official `apksigner` v2 verification,
- package/version/minSdk/targetSdk inspection,
- SHA-256 generation.

Successful 2.6.0 test builds are published as a temporary prerelease.

## Signing warning

The current public-repository build uses a one-time test signing key created inside the workflow.

It is suitable for installation/runtime acceptance only. It is not the permanent update-signing channel.

After device acceptance, move a stable signing key into a private secret/signing environment before producing an APK intended for future in-place updates.

This repository is temporary. Do not delete it until the user explicitly requests deletion after installation/testing.
