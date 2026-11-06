# OGRELAY12: fail closed on premature native SSE EOF

## Evidence boundary
The native OmniGate `CloudflareRelayClient` used to send `resEnd` whenever a local HTTP InputStream reached EOF, without checking that the OpenAI-compatible SSE stream had emitted one **complete** `data: [DONE]` event. This could make a truncated upstream stream appear to be a successful remote reply. Prior OGRELAY07 checks only local OkHttp queue acceptance, not whether the upstream SSE itself completed.

## Narrow patch
A bounded byte-by-byte `RelaySseCompletionGate` checks exactly one full SSE `data: [DONE]` event terminated by the blank line (CRLF or LF), handles UTF-8 split chunks without decoding the assistant's text, rejects incomplete markers, duplicate DONE and data after DONE, retaining only at most 8192 bytes for the current line. The gated path is ONLY `POST /v1/chat/completions` (also query string) with HTTP 200, `Content-Type: text/event-stream`, identity encoding. All other endpoints and JSON responses continue unchanged. At EOF without completion, one fixed `resError` may be queued if the current WebSocket generation is still valid; no `resEnd`, no automatic retry/cancel, no new POST. `resEnd` still signifies only local queue admission, NOT durable delivery.

## Validation and caution
Pure JVM fixture exercises split CRLF/UTF-8, valid complete turn, [DONE] missing, duplicates, invalid event, data after DONE and bounded untrusted line. Source static guard checks exact Android Relay binding. Exact-head Android library compile and fixed signer identity CI must pass. **No real Android SSE, public traffic, partial socket injection, DSH SessionStore or UI paint tested.** If a legitimate provider uses an SSE dialect without [DONE], this narrow gate must not be expanded without protocol evidence. Keep currently working phone APK unchanged and signed release blocked.

No code copied from GPL-3.0 `xingling66666/fuckds`; no token/PoW/account flows or prompt-based tools added.
