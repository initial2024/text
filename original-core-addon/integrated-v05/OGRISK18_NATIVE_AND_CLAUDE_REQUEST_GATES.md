# OGRISK18 — wire risk circuit before actual model submission

Separate native browser-chat and local-only official Claude API paths. Local sidecar health requests and invalid local API-key requests do not trip provider limit counters.

After local API authentication, each `POST /v1/chat/completions` and supported `/v1/responses`/`/v1/completions` entry is admitted by an independent, synchronized one-in-flight provider circuit before any native request or browser editor send occurs. Blocked requests return sanitized JSON with a fixed reason and a server-derived retry-after minimum; they never reach the original core. A `finally` releases the reservation on success, transport failure, client disconnect or owner mismatch.

For the original native proxy, response codes 401, 403, 429 and `cf-mitigated: challenge` are observed only when supplied by the actual local original native API. This does **not** guarantee detection of account-level web challenges hidden inside a provider's HTML page: no undocumented page scraping or anti-bot bypass. For official Claude local-only API, honor `Retry-After` and `anthropic-ratelimit-requests-{remaining,reset}`; inspect only the fixed quota error code to distinguish billing holds. No public Relay access to Claude credentials.

The sanitized Logcat tag `OmniGateRisk` stores only fixed channel, state, HTTP status and optional deadline, never prompt, response, API key, model name, raw URL or raw exception. Request concurrency 1 per independently owned channel is a **local safety ceiling**, not a claim that the provider publishes an RPM=1 quota; no automatic model retry is added. Existing completed stream and user session are not cancelled by a newly opened circuit.

Official: https://developers.openai.com/api/docs/guides/rate-limits ; https://platform.claude.com/docs/en/api/rate-limits ; https://developers.cloudflare.com/cloudflare-challenges/challenge-types/challenge-pages/detect-response/
