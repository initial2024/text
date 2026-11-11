# OGRELAY07 — no false Relay success after WebSocket queue rejection

The user confirmed working public OmniGate model replies. Source review found a separate Relay issue: OkHttp WebSocket.send() returns a boolean queue admission status. Former code ignored false, so a rejected response chunk might be followed by a success resEnd.

This patch adds a per-request RelayReplySendGate, never sending further chunks or a success terminal after any queue rejection. Original HTTP response continues to be consumed to its natural EOF, with no replay, model re-POST, stop, or port rotation. Gate holds no prompt, key, request ID, or payload bytes. Any true return is local queue acceptance, NOT proof that a remote DSH client consumed the response.

Tests verify valid streams, first/chunk/end/error failures, terminal state, 1000 accepted chunks, and concrete Android source wiring. Existing same-HEAD Android compile and signer preflight remain required. No phone disconnect, saturation, public Relay, or DSH chat UI E2E has been performed. Keep current installed APK unchanged and formal release blocked.

No GPL-3.0 fuckds source, webpage token extraction, PoW or prompt-based tool authorization has been reused.
