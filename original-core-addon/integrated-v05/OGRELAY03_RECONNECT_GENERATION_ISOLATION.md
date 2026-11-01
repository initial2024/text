# OGRELAY03: reconnect generation isolation (candidate only)

## Proven user baseline
The user's existing OmniGate Sidecar / public Relay returned HTTP 200 and a normal DSH assistant reply on 2026-11-01. The only permitted optimization for this candidate is stale WebSocket lifecycle isolation. Preserve every verified live capability and signing certificate.

## Bug addressed
Previously, a delayed onClosed/onFailure callback from an old socket could reset global \`connected\` or create redundant reconnects after a later socket opened. A still-running earlier model response could call \`send(...)\` against the **new** global socket and mix response IDs/chunks across generations.

\`RelayConnectionEpoch\` now enforces one socket generation, one reconnect claim, fail-closed stop, and per-request socket affinity. Old requests continue to drain local native response to natural EOF, but no response chunks, resEnd or errors can leak into a replacement socket. No model call is repeated, canceled or newly triggered by this patch.

## Evidence and limits
- Pure Java17 29 checks exercise begin/attach/open, duplicate terminal callbacks, stale-close after replacement, old response rejection, explicit stop and failed connect before attach.
- Dedicated no-network GitHub Actions job validates source invariants.
- Existing unsigned Android library/DEX compile preflight must independently succeed on exact HEAD.
- No public Relay, real Android reconnection, real assistant SSE or signed APK is exercised by the 29 JVM checks. Keep deployed OmniGate unchanged until real E2E and signing gates PASS.
- Reference: xingling66666/fuckds is GPL-3.0; **no source code is copied**. Only transport goals (incremental SSE fidelity and one terminal result per turn) were considered. No web token extraction, PoW, alternative account pools, DOM tool authorization or bypass is adopted.

No API keys, pairing keys, prompt bodies, or conversation contents should be put in GitHub logs. No automatic retry of uncertain model/tool requests. Preserve 8787, original native 1604, Sidecar owner verification, and public-exact-key auth.

CI note: the initial OGRELAY03 workflow parser rejected an unquoted name containing a colon. The workflow title was quoted in the follow-up without changing the transport implementation.
