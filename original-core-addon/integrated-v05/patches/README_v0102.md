# OmniGate v0.10.2 — Context-Scoped Auto-Rollover

Baseline: v0.10.1 APK, same signer certificate and same four DEX files.

This update modifies only the provider bridge and Android versionCode 66 -> 67.

Behavior:
- Confirmed per-conversation context/length limit: one automatic new-chat attempt after generation ends, with recent role-scoped context and one pending text prompt; no hidden duplicate retry.
- Proactive rollover: on the next text-only request, if >=42 trustworthy conversation turns or >=48,000 rendered characters over >=10 turns. These are approximate local heuristics, NOT real provider token counts.
- Do NOT rollover on a server outage, account usage/message limits, a rate-limit notification or an active generation.
- Provider-scoped DeepSeek message extraction; if no trustworthy history or unique visible New Chat control, refuse instead of guessing.
- Manual fresh_chat reports true only after browser observed an actual fresh session.

Limitations:
- A full navigation that destroys the browser bridge context may still require manual handoff. No session contents are persisted to localStorage or logs.
- Does not increase model/account quotas, bypass rate limits, or eliminate DeepSeek transient server outages.
- No real-device runtime or account-limit simulation was performed.

Run: `node --check bridge.js && node test_rollover.js`.