# OmniGate v0.10.5 - DeepSeek automatic conversation rollover candidate

## Baseline
- OmniGate v0.10.4 (versionCode 69); preserve working DSH text response path and classes1..4 DEX unchanged.
- Only modify assets/ai_gateway_bridge/bridge.js and AndroidManifest versionCode to 70.

## Changes
- Fallback proactive rollover from positively confirmed bridge sends (12 submissions, or 4 confirmed submissions totaling 32,000 capped text characters), without assuming DOM chat nodes are stable.
- Bounded volatile, in-memory relay history for recent confirmed user/assistant turns; no persistent storage of messages.
- DeepSeek handoff uses DeepSeek's proven send/confirmation adapter, not the ChatGPT attachment-oriented control.
- Broader *explicitly labeled* DeepSeek New Chat control discovery; never click an unlabeled plus icon or unconfirmed ambiguous control.
- Prevent carrying a recursive handoff prompt as if it were the user's original request.
- New diagnostics: proactiveDomTurns, proactiveConfirmedSends, proactiveConfirmedChars, proactiveContinuitySource.

## Guarantees and constraints
- Do not switch during generation or attachment transmission.
- Do not treat server errors/account quota as a per-conversation cap.
- Do not automatically resend requests when new-chat navigation cannot be confirmed.
- Existing conversations predating upgrade may have zero in-memory counters; original provider DOM must still be recognized to trigger immediate proactive migration.
- If DeepSeek performs a full-page navigation, volatile handoff state can be lost. This build is a TEST CANDIDATE, not a verified seamless rollover.
- No real-device E2E test was performed.