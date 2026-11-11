# OmniGate v0.7.9 — native API image integration and existing HTTPS relay

## Scope and grounded state

- Original Android 1601 `MainActivity`/GeckoView/WebDriver remains frozen. No third API server.
- Sidecar listens **127.0.0.1:8787** only.
- Existing Cloudflare route confirmed by API: `gw-33e850180248.claua.de/*` → `omnigate-relay`, with a proxied DNS record in the `claua.de` zone.
- External Base URL: `https://gw-33e850180248.claua.de/v1`. Existing WebSocket relay client continues to use `wss://gw-33e850180248.claua.de/__device`.
- Cloudflare domain/route existence is **not** a verified online device connection. No production Worker/DNS edits in this branch.

## Compatible image request

`POST /v1/chat/completions` with `stream:false`, last `role:user`, `content:[{type:"text",text:"..."},{type:"image_url",image_url:{url:"data:image/jpeg;base64,..."}}]`.

The adapter accepts exactly ONE data URL image, JPEG/PNG/WEBP, up to 8 MiB decoded. Remote `https://` image URLs are deliberately NOT fetched (no SSRF); unsupported image types/streaming return 422 and do not submit a prompt.

The existing legacy `<<<DSH_WEB_RELAY_V2_ATTACHMENT_ENVELOPE>>>` single-attachment client continues to work through the same path, with bounded sizes.

Ordinary text requests, `/v1/models`, and other original routes remain pass-through.

## Recovery contract

1. Before POST to 1601, require an exact visible MainActivity socket + WebDriver identity and record its current answer baseline.
2. Forward the envelope once, without retry. A transport error or 502 may occur after the website submitted the image.
3. If native HTTP 200 returns nonempty content, deliver it with `omnigate.delivery=native_http_200`.
4. Otherwise, recover only via the same attachment UUID, observed new user turn, changed scoped assistant response, native/page answer-length match, generation completed, and **eight seconds of stable answer**.
5. On verified recovery, sidecar may return **HTTP 200 as a newly verified gateway reply** with `omnigate.delivery=correlated_native_recovery`, `omnigate.native_http_status=502` (or null for unknown transport), `browser_reply_verified=true`, and the exact attachment request ID. This is NOT a claim that native 1601 returned 200.
6. If uncertain, return 502 with `possible_provider_submission=true` and **DO NOT auto-retry**.

Concurrent image requests are rejected with 409 while one attachment is unresolved, to avoid misattributing sessions. This is an API-level limitation; external callers must serialize.

## Manual setup and test

From Android's OmniGate API console:
1. Start original API 1601 and sidecar 8787; confirm original visible WebDriver identity.
2. Under *Advanced configuration*, keep the existing separately configured public API key and Relay pairing key. Public API uses the exact key; `test` is only for local compatibility.
3. Tap the **manual enable** public relay control and explicitly confirm. Do not enable without the original pairing key; no new worker or hostname is provisioned.
4. Local: `GET http://127.0.0.1:8787/health`; public: `GET https://gw-33e850180248.claua.de/__relay_health`. Check deviceConnected and original worker identity. Public /health alone never proves a model reply.
5. From an authorized client test one short `/v1/chat/completions` text request, then one **non-sensitive** single-image data URL request. Ensure HTTP response contains `choices[0].message.content` and `omnigate.delivery`, and compare with page output. Do not re-POST after ambiguous 502.
6. For a failed test, record sanitized HTTP status, `omnigate` recovery metadata, `OverlayUploadClient.recoveryStage()`, no key, no login data, no page content.

## Release gates

- GHA Java/D8 compile, DEX class integrity, fixed certificate identity.
- Signed APK v2/v3 identity, original three DEX and frozen private JS matching v0.7.8.
- Real Android local text and image E2E, original page finish, image recovery returned via 8787, exact public key behavior and authorized HTTPS device E2E.
- No automatic publication to public Cloudflare, no PR merge, no signed APK in public GitHub.

`BUILD_PASS` or Worker DNS existence must **not** be reported as `DEVICE_PUBLIC_E2E_PASS`.
