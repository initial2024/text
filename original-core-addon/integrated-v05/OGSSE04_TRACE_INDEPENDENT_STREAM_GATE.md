# OGSSE04 — direct native DeepSeek SSE must not depend on optional diagnostics

## Observed baseline

On 2026-11-01 the user confirmed the **existing** OmniGate public Relay and model replies work. The current `proxyDirectNativeSse` has a hidden transport dependency: its meaningful-delta forwarding and empty-reply recovery decisions consume counters from an optional `CURRENT_TRACE` ThreadLocal. If telemetry is absent, `NativeSseContentProbe.observe` returns immediately and the direct SSE branch may buffer until EOF instead of streaming, or skip verified empty-reply recovery. This is a **source-level conditional defect**, not a claim it occurred on the user's device.

## Narrow change

- Add per-request `SseMeaningfulGate`, retaining only booleans for observed nonempty `delta.content` and verified tool-call/finish indicators.
- The same existing `org.json` parser marks these booleans independently from `ProxyTrace`. Tracing counters remain optional, redacted and do not control output.
- First genuine content/tool signal opens the existing raw-byte stream immediately (no reformatting), including when trace is null.
- A genuinely empty native SSE response still enters the **original** verified-page recovery path even if trace is absent. Keep `[DONE]` from being mistaken for an assistant reply.
- No new SSE conversion, duplicated tool calls, model retry, external POST, credential read, port/relay/409 owner change, browser Stop, device restart or APK publish.

## Validation

The standalone Java17 gate test covers early/late content, reasoning/control-only streams, tool-only replies and independent per-turn state; static assertions bind actual `SidecarProxyServer` to this gate. Compile the unchanged Android source tree through the existing Gradle/DEX and fixed-signer preflight on the **same commit**. **No real Android streaming benchmark has been performed**, and this patch must not be shipped until a user-approved real-device test confirms progressive text rendering and normal empty-reply behavior.

Reference `xingling66666/fuckds` is GPL-3.0; no source copied and none of its web-token/PoW/account or text-tool authorization techniques are adopted.
