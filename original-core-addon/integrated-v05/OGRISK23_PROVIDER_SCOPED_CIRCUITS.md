# OGRISK23 — DeepSeek, ChatGPT, Grok and GLM provider-scope risk management

The frozen original AiProvider enum supports four web providers (DeepSeek, ChatGPT, Grok, GLM). OGRISK17–22 mistakenly collapsed all browser providers into one `native` hold. This patch adds separate provider-scope circuit states while preserving the existing `native` global gate and any pre-upgrade cooldown: a provider switch cannot evade a historical global restriction.

Provider selection is validated twice: exact supported requested model family AND original frozen app's current selected provider model; if they cannot both be verified/matched, return a fixed 409 before opening the original provider HTTP request. No guessing provider from username, hostname strings, prompt, DOM, or user-supplied account names. Settings shows all four providers plus Claude/Relay with explicit reviewed resets. Different models on the same provider share its provider-wide risk gate; provider accounts cannot auto-failover after platform restriction.

All native browser traffic still shares one global in-flight user-turn for original WebDriver correlation. This is local safety, not a platform RPM/TPM. **Full browser account isolation requires actual separate GeckoSession contextId binding**, which this patch does not claim. No platform credentials or cookies are stored in circuit state.
