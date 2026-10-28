# Original AI Gateway core + OmniGate add-on

This branch freezes the working original Android AI Gateway and adds features around it instead of reimplementing browser automation.

## Frozen baseline

Input APK: `base2-no-truncate-600s.apk`

SHA-256:

`16d78e8a33452571ae18ee13dcd792bd06a4e9b4d185ebda004c14295a88a355`

Original package: `com.deepseek2.app`

Verified local API: `http://127.0.0.1:1601/v1/chat/completions`

Models already present in the original core:

- `deepseek-chat`
- `chatgpt-web`
- `grok-web-fast`
- `glm-web-flash`

## Frozen-core rule

Only two non-signature APK entries may change:

1. `assets/ai_gateway_bridge/bridge.js`
2. `classes.dex` — only the three verified chat timeout immediates.

Everything else must remain byte-identical to the frozen baseline.

The working original bridge implementations of input/upload/send are protected. Add-ons may wrap them but must not rewrite them:

- `findFileInput`
- `uploadAttachments`
- `submitContentEditable`
- `submitTextarea`
- `sendPrompt`

## No-random-interruption policy

- Native chat hard timeout: 3600 seconds.
- Initial post-send protection: 30 seconds.
- Generation/content activity lease: 10 seconds, renewable while answer/thinking text changes.
- Missing stop button, silence, backgrounding, network jitter, or temporarily no new token are not conversation-limit evidence.
- Auto rollover requires a new visible strong context/conversation-limit message.
- Rollover never clicks Stop.
- Maximum automatic rollover: 3 per submission.
- Current-turn attachments may be re-uploaded after rollover; historical attachment bytes are not duplicated.
- The native `client_disconnected` branch is not byte-patched without verified disassembly.

For DSH, use `initial2024/wet` v0.4.0 or newer. It protects the original Gateway from ordinary UI/client disconnects by draining the SSE response in the background. Only explicit user cancellation tears down a live request.

## Companion architecture

Use `chatgpt/omnigate-companion-v1` for the user's custom control plane:

`client -> OmniGate Companion :8787 -> original AI Gateway :1601 -> original browser automation`

The companion owns local/public API-key policy, `claua.de` relay, custom public URL and status UI. It does not own browser login/input/upload or a second chat overlay.
