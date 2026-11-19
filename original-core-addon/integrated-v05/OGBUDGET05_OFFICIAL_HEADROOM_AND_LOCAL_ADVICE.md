# OGBUDGET05 — API 80% official headroom / website self-budget advisory

Scope: OmniGate independent personal application. It must NOT assume a published API rate quota applies to consumer **web** chat accounts.

1. Where the official provider API actually returns coherent **same-response** `remaining`, `limit` and `reset` for requests or tokens, proactively cool that same upstream account at consumed >=80% (remaining <= limit/5) until the provider's own reset; no guesses at RPM/TPM and no automatic model resend. Client-side 20% reserve is a precaution, NOT an official safe threshold. Existing 429 Retry-After, 401/403, challenge, billing hold always have precedence and remain persisted.
2. For standalone auxiliary Gecko web logins where the provider does NOT give an account-applicable numeric daily allowance, show optional conservative local self-budget starting point **8 completed turns per UTC day per slot**, explicitly labeled **unverified example, not official and not guaranteed safe**. It does NOT automatically activate, and does not modify any previously configured budget (e.g. user's 60).
3. A user may manually enter a verifiable **daily request count** from their account's applicable official/console page. Recommend floor(80% of the manually typed daily number), capped by the existing local 100/day setting; label the source '用户手动填写、未自动核验'. Do not input RPM or TPM in the daily form. Provide in the popup two explicit actions: `应用推荐` or `保存自定义`. No cross-model automatic quota inference.
4. Prior auxiliary self-budget rotation retains explicit `本轮已完成` action and opt-in; never responds to 401/403/429, website challenge or quota by switching to another account. Auxiliary Gecko slot switching still does not transfer original frozen native WebDriver/8787 model execution account.

References:
- OpenAI response rate-limit headers: https://developers.openai.com/api/docs/guides/rate-limits
- Anthropic rate limit headers: https://platform.claude.com/docs/en/api/rate-limits
- DeepSeek API per-account concurrency is distinct from consumer website rules: https://api-docs.deepseek.com/quick_start/rate_limit/
- LiteLLM separation of budgets, RPM, TPM and per-key concurrency: https://github.com/BerriAI/litellm-docs/blob/main/docs/proxy/users.md

Verification: pure-JVM fixtures for 60→48 request threshold, 1000 tokens→200 remaining, missing headers, tiny quota, hard hold priority, daily 8 suggestion; exact-head Android CI/DEX, fixed signing identity and phone UI acceptance separate. No public release until all gates pass.