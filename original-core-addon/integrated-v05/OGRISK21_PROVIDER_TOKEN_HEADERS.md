# OGRISK21 — token capacity is independent of request count

Official APIs may enforce both RPM and TPM, and a successful response may already report token remaining=0 while requests remain. Read only official `x-ratelimit-remaining-tokens/reset-tokens` (native-compatible OpenAI HTTP) or `anthropic-ratelimit-tokens-remaining/reset` (Claude), and stop new submissions until the provider's supplied reset time. Unknown reset formats do not fabricate a time. This is separate from the request-count and Retry-After gates.

The manual review UI cannot shorten a still-valid provider cooldown. For provider web UIs that do not expose official limit headers through the original core, no numeric API account limit is inferred; auto model retry remains zero and concurrent user-submitted turns are bounded locally.
