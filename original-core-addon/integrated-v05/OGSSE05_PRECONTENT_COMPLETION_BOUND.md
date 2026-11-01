# OGSSE05 — native SSE end-marker integrity under precontent overflow

## Existing live baseline
As of 2026-11-01 the user reports actual OmniGate/DSH can generate normal assistant replies. OGSSE04 fixed an optional telemetry dependency and passed exact-HEAD Java/Android compile. Preserve installed app and Cloudflare Relay.

## Concrete source defects
1. `NativeSseContentProbe` previously marked `finish_reason: tool_calls` as proof of an actual tool delta. But a terminal marker alone contains neither function ID nor arguments. This could release an otherwise empty native SSE stream as a seemingly successful tool reply.
2. The original 64KiB pre-answer buffer silently set `rawForwarding=true` on overflow, even without any verified content/tool delta. It could then pass through native `[DONE]` despite unverified empty assistant output.

## Narrow corrective changes
- Only actual nonempty `delta.tool_calls` or `delta.function_call` count as tool output. Terminal-only `finish_reason` never becomes real tool data.
- `SsePrecontentBound` tracks bytes before first meaningful delta: 64KiB buffer unchanged. If a verified content/tool delta arrives in a read that crosses the bound, forward the original bytes as usual. If only control/thinking data crosses the bound, fail closed with **one structured SSE error**, no `[DONE]`, no auto retry, and continue reading original upstream to natural EOF.
- Existing NativeEmptyAnswer verified-page recovery remains unchanged for a normal short empty native stream. The fix touches no auth, headers, external model API, user files, source-provider automation, native owner / 409 routing, public tunnel, or signing. No user-visible APK is produced.

## Release boundary
This fail-closed branch may alter behavior for unusually long (>64KiB) reasoning before actual text. A controlled real Android stream experiment is mandatory before release. Never claim streaming UI or long thinking verified by pure-Java unit tests. Any regression in model completion, byte integrity, real tool_calls, or no-retry behavior blocks deployment.

## License
`xingling66666/fuckds` GPL-3.0 was consulted only for protocol ideas. No copyrighted implementation, webpage token collection, PoW, account pools or text-based tool authorization has been copied/adopted.

## Regression workflow compatibility
The previous OGSSE04 workflow expected a standalone `if(probe.hasMeaningfulDelta())` branch. OGSSE05 introduces `SsePrecontentBound.observe(n,probe.hasMeaningfulDelta())` with a `START_FORWARD` decision. The old Java semantic assertions remained green; the source-form assertion failed on the first OGSSE05 commit. This follow-up checks the new source path while preserving original telemetry-independent and empty-response recovery checks. Keep initial failure in CI history; never count it as PASS.
