# Original AI Gateway Core + OmniGate Add-on

This branch is the new compatibility line. It deliberately preserves the original AI Gateway browser automation instead of reimplementing it.

## Frozen functional baseline

`base2-no-truncate-600s.apk`

SHA-256:

`16d78e8a33452571ae18ee13dcd792bd06a4e9b4d185ebda004c14295a88a355`

The original APK already contains GeckoView, provider/login/session handling, textarea/contenteditable input, file upload, floating-window behavior and the native local API.

Original model IDs verified in the DEX:

- `deepseek-chat`
- `chatgpt-web`
- `grok-web-fast`
- `glm-web-flash`

## Verified original local API

The frozen original core uses:

`http://127.0.0.1:1601/v1/chat/completions`

The DEX contains `1601` and no `8787` literal. Future 8787 compatibility must therefore be a sidecar/proxy (`8787 -> 1601`) rather than a rewrite of the original native API server.

## Hard freeze rule

Only these APK content entries may change:

1. `assets/ai_gateway_bridge/bridge.js`
2. `classes.dex` — only the three already-verified native API Future timeout constants.

The original working functions below are text-locked against the v2.1 working bridge reference:

- `findFileInput`
- `uploadAttachments`
- `submitContentEditable`
- `submitTextarea`
- `sendPrompt`

If any one changes, the source gate must fail.

## Do not randomly interrupt an active conversation

The add-on does not infer completion or conversation rollover from silence, elapsed time, a missing stop button, app backgrounding or temporary DOM stalls.

- Hard native Future timeout: 3600 s.
- Confirmed generation activity gets a 10 s lease; new answer/thinking text renews it.
- Automatic rollover is capped at 3 per submission.
- Rollover requires a new, visible, strong provider conversation/context-limit notice after the generation activity lease settles.
- Rollover never clicks Stop.
- Only current-turn attachments are re-uploaded after rollover.
- The original native `client_disconnected` cancellation path is not blindly byte-patched without verified disassembly; this remains an explicit residual risk.

## DSH add-on

Provider: `original-web-gateway`

Models:

- `deepseek-chat`
- `chatgpt-web`
- `grok-web-fast`
- `glm-web-flash`

The adapter preserves the strict textual pseudo-tool-call compatibility, real image/file envelope and bounded rollover handoff.

## Source bundle

`OriginalCore-OmniGateAddon-v0.1-patch-source.zip`

SHA-256:

`8d78a4d4cee4d1b8499eaae60a249fe26075d4284a7783f260fded9d4bf0dcc0`

The source archive intentionally contains no APK, signing keystore or private key.

## Current migration boundary

The working original core is the priority. Later custom features such as the `claua.de` public relay, 8787 compatibility and local arbitrary-nonempty-key behavior should be reintroduced as sidecars/add-ons without replacing the original GeckoView/provider automation.

The rewritten 2.8.x line remains on `main` only as prior experimental work; new core work belongs on this branch.
