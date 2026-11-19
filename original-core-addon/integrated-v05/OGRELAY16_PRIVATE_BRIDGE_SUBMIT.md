# OGRELAY16 — private page bridge ordinary-text submit confirmation

Android v1.0.0 regression evidence: ordinary text was inserted into the provider composer but not actually submitted; the later native recovery emitted `omnigate_native_empty_reply/cannot_correlate_current_turn`.

The packaged private bridge was older than current Sidecar Java: ordinary `sendPrompt` returned `ok` immediately after an unconfirmed button click, and its redacted diagnose response lacked `lastSubmissionSequence` and `deepseekSubmissionStatus`. Current Sidecar recovery explicitly consumes those fields.

The private user-owned APK patch therefore:
- verifies prompt retention before submit;
- performs exactly one safe submit action, preferring form.requestSubmit when a real form exists and otherwise one button click;
- does not synthesize Enter and never auto-retries an uncertain send;
- requires a new explicit user turn, or composer-cleared plus generating/answer-change evidence, before `submitted_confirmed`;
- exposes only monotonic submission sequence and status, never prompt text;
- resets the correlation state on provider reconfigure/disconnect.

Base private bridge SHA256: `0a4e4b6ea3b72820b3c29b2d49e71594704435f4ed11e7e5c71649138f7fde67`.
Patched private bridge SHA256: `1449dd2e57deb535ad4358c25465904a1d0b96383938ab5c29449ffc007f6525`.
The full proprietary bridge asset remains outside the public repository; only the narrow audit patch is committed.
