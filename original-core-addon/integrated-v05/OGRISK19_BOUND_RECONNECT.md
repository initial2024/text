# OGRISK19 — transport reconnect limit, not platform-circumvention

The original Cloudflare Relay Android WebSocket client previously retried handshake failures indefinitely (1500 ms ramp, cap 15 seconds), even when the HTTP handshake reported 401/403/429 or challenge. This is not a provider API model replay, but still creates unnecessary repeated requests on policy/auth errors.

Now any handshake 401/403/429 or `cf-mitigated: challenge` causes an immediate fail-closed stop. A valid `Retry-After` is remembered and only an explicit later user start can attempt again after the deadline; unknown rate-limit duration requires review. WebSocket close code 1008 is treated as a policy failure and never auto-reconnected. Transient socket failures allow a maximum of 2 automatic transport reconnects per successful open interval; after that, a manual start is required. Existing stale-socket epoch isolation and no model re-POST remain unchanged. Audit records fixed reason/category/status only.

Two local socket reconnections are a *resource-bounding implementation choice*, not a claim of any platform's officially safe frequency. Do not change identity, IP, device, cookies, or browser profile to evade a restriction. No public deployment or Android APK install is performed by this source change.
