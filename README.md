# DSH Web Gateway (clean Android rebuild)

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
- Attachment envelope compatible with `dsh-web-gateway-v2`
- Auto rollover: detects conversation/context limit, creates one fresh chat, replays the full current DSH prompt and attachments

## Security / failure policy

- Binds only to `127.0.0.1`, never `0.0.0.0`.
- Attachment upload is fail-closed: if a real file input / upload DOM evidence is not observed, the prompt is not silently sent without the file.
- Automatic rollover is capped at once per submission to avoid loops.

## Build

Every push to `main` runs `.github/workflows/android.yml`. The workflow builds a release APK, aligns it, signs it with an ephemeral build key, verifies the APK signature, and uploads both the APK and verification report as workflow artifacts.

This repository is temporary and may be deleted after device acceptance.
