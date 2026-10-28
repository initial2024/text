# OmniGate Original Core + Sidecar v0.4 fixed-signing pair

Status: `PASS_STATIC_BUILD_SIGNATURE_FREEZE`

## Original core

File: `AI-Gateway-OriginalCore-OmniGateAddon-v0.4-fixed-v2v3.apk`

SHA-256:

`920919da7c1dd2ce05b4f1af5055fbbe412ba1773a6e5d1488c3c9d147ca835b`

Android signature:

- v2: PASS
- v3: PASS
- certificate SHA-256: `bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d`

Frozen baseline:

`base2-no-truncate-600s.apk`

Baseline SHA-256:

`16d78e8a33452571ae18ee13dcd792bd06a4e9b4d185ebda004c14295a88a355`

Allowed non-signature content differences only:

- `assets/ai_gateway_bridge/bridge.js`
- `classes.dex`

v0.3 -> v0.4 non-signature content differences: `0`.

## Sidecar

File: `OmniGate-Sidecar-v0.1.0.apk`

SHA-256:

`78e19ea6c620048a9ca18e565ea508b2a7413506774dbb80cbdcd6c7a66964cd`

Android signature:

- v2: PASS
- v3: PASS
- same fixed certificate as original-core compatibility line

Architecture:

`Minis / DSH -> 127.0.0.1:8787 -> Sidecar -> original AI Gateway 127.0.0.1:1601`

## Conversation continuity policy

- silence is not completion;
- temporary lack of new tokens is not completion;
- a temporarily missing stop button is not completion;
- backgrounding is not a rollover trigger;
- Sidecar drains the upstream response to natural EOF after downstream disconnect;
- automatic rollover requires explicit provider conversation/context-limit evidence;
- rollover never clicks Stop;
- maximum automatic rollovers per submission: 3.

## Runtime boundary

Real-device end-to-end status remains `NOT_YET_VERIFIED_ON_USER_DEVICE`.

If the currently installed original AI Gateway uses a different signing certificate, Android requires a one-time uninstall/reinstall before entering this fixed-signing update line.
