# DSH Web Gateway 2.7.0

Clean Android rebuild of the original AI Gateway, preserving the original provider/model contract while adding the new reliability features.

## Original-provider compatibility

The original APK is the compatibility source of truth.

| Provider | Web page | OpenAI-compatible model id |
| --- | --- | --- |
| DeepSeek | https://chat.deepseek.com/ | `deepseek-chat` |
| ChatGPT | https://chatgpt.com/ | `chatgpt-web` |
| Grok | https://grok.com/ | `grok-web-fast` |
| GLM / 智谱清言 | https://chatglm.cn/ | `glm-web-flash` |

The relay prioritizes the provider-specific selectors recovered from the original APK bridge and keeps generic fallbacks only as a resilience layer.

## Gateway contract

- Package: `com.dsh.webgateway` (side-by-side test package)
- Local API: `http://127.0.0.1:8787/v1`
- Health: `GET /health`
- Models: `GET /v1/models`
- Chat: `POST /v1/chat/completions`
- Request-body cap: 32 MiB
- Hard generation timeout: 600 seconds
- End-of-generation grace: 5 seconds
- Full DSH role/tool context reconstruction
- Real browser attachment upload through `File + DataTransfer`
- Attachment failure is fail-closed
- Automatic conversation rollover is capped at one per submission

## Original public tunnel

The rebuild preserves the original tunnel contract:

- generated host: `https://gw-<prefix>.deepseeks.dpdns.org`
- local port: `8787`
- original tunnel endpoint/protocol and TLS certificate pin are retained
- `/__tunnel_probe` verifies that a public URL reaches this exact Gateway
- `/v1/*` requires the generated Gateway API key
- old tunnel identity can be restored only with the matching old `prefix + tunnel_token`
- a custom HTTPS public URL can be entered and is accepted only after probe verification

## Compact floating window

The optional floating control is a 48dp provider bubble:

- D / G / X / 智 reflects the current provider
- drag to move
- tap to reopen the main Gateway screen
- long-press to hide
- hiding the bubble does **not** stop the Gateway, port 8787, or the tunnel
- the foreground notification/main screen can show it again

Android overlay permission is requested only when the user enables this feature.

## Build provenance

Every push runs `.github/workflows/android.yml`.

Release acceptance requires:

1. source-contract checks,
2. `assembleRelease`,
3. `zipalign -c -v 4`,
4. official Android `apksigner verify --verbose --print-certs`,
5. package/version/minSdk/targetSdk inspection,
6. successful artifact upload and temporary prerelease.

The GitHub build uses an ephemeral test signing key. It is intended for side-by-side device acceptance, not as the permanent update signing identity.

See `QA_AGENT.md` for the independent runtime acceptance gates.
