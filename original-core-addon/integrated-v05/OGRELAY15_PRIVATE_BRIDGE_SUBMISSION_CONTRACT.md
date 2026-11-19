# OGRELAY15 — classify missing private bridge submission contract

Observed Android failure after the v1.0.0 full package: text appears in the provider composer but is not automatically submitted; the client later receives `omnigate_native_empty_reply/cannot_correlate_current_turn`.

Root cause audit found the packaged private `bridge.js` ordinary-text path returned `ok` immediately after a single unconfirmed button click and did not expose the `lastSubmissionSequence` / `deepseekSubmissionStatus` fields expected by current Sidecar recovery. Thus newest Java DEX and older private page bridge protocol were mismatched.

This public-source change does not attempt another Send. If no pre-submit anchor exists, it reads only redacted bridge diagnostics and distinguishes `bridge_submission_contract_missing` from `native_send_not_confirmed`; a true unknown mismatch remains `cannot_correlate_current_turn`. No retry, port rotation, provider Stop, or fabricated answer. The private asset is repaired separately in the user-owned signed APK and is not uploaded wholesale to this public repository.
