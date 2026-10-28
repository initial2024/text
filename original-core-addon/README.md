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

The complete source bundle is delivered as the chat artifact:

`OriginalCore-OmniGateAddon-v0.1-patch-source.zip`

SHA-256:

`8d78a4d4cee4d1b8499eaae60a249fe26075d4284a7783f260fded9d4bf0dcc0`

The GitHub connector does not preserve large binary ZIP uploads byte-for-byte, so this branch stores the reviewable contract/test/patch text instead of a corrupted archive. The source archive intentionally contains no APK, signing keystore or private key.

## Current migration boundary

The working original core is the priority. Later custom features such as the `claua.de` public relay, 8787 compatibility and local arbitrary-nonempty-key behavior should be reintroduced as sidecars/add-ons without replacing the original GeckoView/provider automation.

The rewritten 2.8.x line remains on `main` only as prior experimental work; new core work belongs on this branch.


## OmniGate Sidecar v0.1.0

The sidecar is a separate Android package: `com.omnigate.sidecar`.

Architecture:

`Minis / DSH -> 127.0.0.1:8787 -> Sidecar -> frozen original AI Gateway 127.0.0.1:1601`

The sidecar deliberately does not own GeckoView or provider DOM automation.

Continuity rule:

- Sidecar upstream read timeout is disabled.
- If the 8787 downstream client disconnects during a response, Sidecar keeps reading the original 1601 response to natural EOF and discards bytes that no longer have a client.
- This prevents an avoidable downstream disconnect from immediately propagating into the original core's native client-disconnect cancellation path.
- Sidecar never calls WebRelay/provider Stop/cancel methods.

Authentication:

- localhost 8787: any non-empty key is accepted;
- public relay: the exact OmniGate public API key is required;
- optional upstream 1601 key can be configured independently.

Public relay:

- default public base: `https://gw-33e850180248.claua.de`;
- Relay Key is separate from the public API key;
- custom HTTPS public URL remains supported after health verification.

Build:

- package: `com.omnigate.sidecar`
- version: `0.1.0`
- minSdk: 26
- targetSdk: 35
- GitHub Actions build, zipalign, v2/v3 signature and manifest gates passed.


## Permanent signing identity

This compatibility line now uses a fixed signing certificate. Future Sidecar and original-core add-on releases must keep certificate SHA-256:

`BF:DC:4D:E8:53:80:66:D2:9E:CD:17:E8:0A:B6:51:D3:8A:E3:C4:40:71:3F:96:51:BD:8C:E1:C8:10:15:D2:7D`

CI is fail-closed: it does not create an ephemeral fallback key. See `SIGNING_POLICY.md`.

The GitHub repository is public, so the private keystore and passwords are intentionally not committed. They are supplied only through GitHub Actions Secrets.
