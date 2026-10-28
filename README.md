# DSH Web Gateway 2.5.0

A clean, reproducible Android gateway for DSH that relays OpenAI-compatible local requests to the logged-in DeepSeek web page.

## Why this repository exists

Directly repacking the old 169 MB APK produced installation failures on Android 16/vivo. This project rebuilds the gateway from source using the official Android Gradle plugin, then runs `zipalign` and `apksigner` from Android build-tools in GitHub Actions.

## Runtime contract

- Package: `com.dsh.webgateway` (side-by-side with the old AI Gateway)
- Local endpoint: `http://127.0.0.1:1601`
- `GET /health`
- `GET /v1/models`
- `POST /v1/chat/completions`
- Default model: `deepseek-chat`
- Browser identity: DeepSeek website login/cookies, no DeepSeek API key
- Hard generation timeout: 600 seconds
- Generation-end grace: 5 seconds
- Request body limit: 32 MiB
- Attachment envelope compatible with the DSH web relay provider
- Auto rollover: detects conversation/context limit, creates at most one fresh chat, then replays the current full DSH prompt and attachments

## Remote / Tailscale mode

Remote access is **off by default**.

When enabled in the app:
- the server binds `0.0.0.0:1601`;
- the UI displays a detected Tailscale `100.64.0.0/10` IPv4 address when available;
- LAN IPv4 is also shown for diagnostics;
- every non-loopback HTTP request must provide the generated token using either:
  - `Authorization: Bearer <token>`, or
  - `X-DSH-Gateway-Token: <token>`.
- loopback requests from DSH on the same phone do not require the token.

For a remote DSH/OpenAI-compatible client, use:
- Base URL: `http://<tailscale-ip>:1601/v1`
- Model: `deepseek-chat`
- API key: the token displayed in the app.

This is direct tailnet access, not a public Tailscale Funnel. Public Funnel/Serve setup requires the Tailscale CLI on a supported machine.

## Security / failure policy

- Default mode binds only to `127.0.0.1`.
- Remote mode is explicit and token-protected.
- Attachment upload is fail-closed: if real file-input / upload DOM evidence is not observed, the prompt is not silently sent without the file.
- Automatic rollover is capped at once per submission to avoid loops.

## Build

Every push to `main` runs `.github/workflows/android.yml`. The workflow builds a release APK, aligns it, signs it using the official Android `apksigner`, verifies signature schemes and package metadata, and uploads the APK plus verification report as a workflow artifact.

This repository is temporary and may be deleted after device acceptance.
