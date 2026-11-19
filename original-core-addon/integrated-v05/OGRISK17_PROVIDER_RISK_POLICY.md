# OGRISK17 — provider-protocol-based guard (source only)

This policy is not a platform bypass and never assigns a fabricated "safe RPM" or model-request retry count. Actual provider account limits vary with plan, usage, and changing official policy.

- HTTP 401/403 and `cf-mitigated: challenge`: manual hold until an explicit reviewed reset.
- HTTP 429: stop new submissions; use the unmodified server `Retry-After` seconds or HTTP date when present; if absent/invalid, manual hold rather than guessing the wait duration.
- Quota/billing error codes: manual hold even if a retry hint is present.
- Temporary HTTP 503/529: honor server `Retry-After` only when present, no automatic model re-POST.
- Official remaining-requests 0 + reset header: cool until the provided reset (OpenAI duration, Anthropic RFC3339), no homegrown RPM computation.
- Local concurrent in-flight ceiling 1 per independently managed upstream account path is a *resource/turn-correlation safeguard*, not a service quota. Automatic model retry ceiling 0. All user inputs, URLs, keys, response bodies and exception messages are excluded from audit.
- The gate is process-scoped and has an explicit manual-reset API; it must be wired to the real request sources before claiming protection. It does not inspect or infer account controls from DOM CSS alone.

References: RFC9110 Retry-After https://www.rfc-editor.org/rfc/rfc9110.html#section-10.2.3 ; OpenAI rate-limit policy https://developers.openai.com/api/docs/guides/rate-limits ; Anthropic https://platform.claude.com/docs/en/api/rate-limits ; Cloudflare challenge header https://developers.cloudflare.com/cloudflare-challenges/challenge-types/challenge-pages/detect-response/ . Resilience4j state machine is conceptual only; no GPL or third-party source copied.
