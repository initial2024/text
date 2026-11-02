# OGRELAY09: one-request error triage without revealing API Key

## Why
User's screenshot of a real Windows PowerShell test showed `DRY_RUN_NO_NETWORK` correctly, then an opt-in single POST failed as `NETWORK_FAILURE_OR_REDIRECT_REJECTED`, `http_status:null`, `detail:TypeError`. This is **not evidence of HTTP 401 or a wrong key**. The original Node script used `redirect:'error'` and collapsed all fetch failures into one TypeError. `requests_sent:1` meant attempted, not delivered.

## Changes
- Add `--diagnose` (never requires a key): one DNS lookup and one fixed public `GET /health`, redirect mode manual, 12s timeout, no response body logged, no POST.
- The existing `--execute` behavior remains user-opt-in, exactly **one** fixed harmless model POST with no retry, but redirects are now **manual** (not followed). A 3xx reports only status and safe redirect category (same origin, other origin, downgrade, etc.) without `Location` URL or secret values.
- For Node `fetch` transport failures traverse `cause` strictly through a whitelist of error codes to report DNS/TLS/timeouts/connection reset. Never emit `e.message`, stack, request URL with query, API key or response body.
- This does **not** certify that the Relay or model ever received an attempted POST. If `GET /health` succeeds but POST fails, isolate route/method/proxy/TLS instead of changing working mobile OmniGate.

## No-release guardrails
No Android native source changed, no public Relay settings or ports modified, no model request issued by CI, no credentials stored, no retry/replay. Existing phone APK still works and is unaffected. The new tool cannot prove DSH SSE paint or Relay durable ack; keep issue open and release blocked.

## Windows read-only operator step
Download the updated `omnigate_remote_sse_receipt.mjs` **and** its new sibling `omnigate_network_error.mjs` from this exact commit. Retain the previously downloaded `sse_receipt_audit.mjs` in same directory. Run `node .\\omnigate_remote_sse_receipt.mjs --diagnose` **without** any API key. Send back sanitized JSON only. Do not repeat a model POST until the read-only result is interpreted.
