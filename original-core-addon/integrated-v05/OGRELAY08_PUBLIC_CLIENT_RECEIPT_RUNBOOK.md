# OGRELAY08 — standalone public-client SSE receipt probe

## Evidence boundary
OGRELAY07 can prove that OkHttp locally accepted or rejected `resStart`, `resChunk` and `resEnd`. It **cannot** prove that the public Relay delivered these frames to the remote client. No matching Relay server receipt-ACK protocol has been verified in the accessible source. Adding client-generated ACK markers without server support would invent evidence.

## Current implementation
A user-operated Node 22+ SSE receiver, default **dry-run without network**. Real single-shot test requires the explicit `--execute` flag and a local `OMNIGATE_PUBLIC_API_KEY` environment variable. Only one request is issued: POST to the pinned `https://gw-33e850180248.claua.de/v1/chat/completions` with `stream:true`, no tools, a fixed benign prompt, no redirect and no automatic retry. The selected model defaults to `deepseek-chat`, overridable by `OMNIGATE_MODEL`.

The receiver checks fragmented UTF-8, SSE comments, event order, JSON shape, nonempty text, exactly one finish and one [DONE], duplicate and post-termination frames, and detects unexpected tool calls. It emits timing/counts/anomalies plus a SHA-256 digest of the assembled content; it never prints reply text, the API key, headers or an error response body. A socket/HTTP 200/one DONE success proves **this single Windows/Node client's receipt**, not a Relay-side durable ACK and not the DSH chat UI or SessionStore.

## User-controlled Windows PowerShell test
Download both scripts from the exact reviewed commit. Set the public key locally as `OMNIGATE_PUBLIC_API_KEY` without pasting it into chat or GitHub. Run `node .\omnigate_remote_sse_receipt.mjs` first (dry-run). Only after choosing to send a model test: `node .\omnigate_remote_sse_receipt.mjs --execute`. The test creates exactly one benign model response, which may consume API quota. Remove the environment variable after testing.

## Release remains blocked
Offline CI tests parser fixtures and default no-network behavior only. No real authenticated public model call, packet loss injection, DSH UI paint, original Android request or APK signing was performed. Avoid installing or replacing the currently working OmniGate app. Do not copy GPL-3.0 fuckds source or adopt web token extraction / PoW.
