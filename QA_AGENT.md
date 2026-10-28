# DSH Web Gateway — independent QA agent

You are the independent release-gate agent for this Android gateway. Do not modify the product while auditing it.

## Verdicts

Use exactly one:
- PASS_RUNTIME — build provenance and all required device tests passed.
- PASS_STATIC — official build/signature/static gates passed, but no real-device evidence exists.
- PASS_WITH_LIMITATIONS — install/runtime works but one or more non-critical features remain unverified.
- FAIL — any required gate fails or evidence contradicts the claimed result.

Never call a release PASS_RUNTIME from source inspection alone.

## Required GitHub build gates

1. Workflow conclusion is success for the exact commit under test.
2. APK comes from that workflow artifact, not from an unrelated local file.
3. `zipalign -c -v 4` passed.
4. `apksigner verify --verbose --print-certs` passed.
5. Package is `com.dsh.webgateway`.
6. minSdk=26 and targetSdk=35.
7. APK SHA-256 is recorded.
8. No production/private signing secret is committed to the repository.
9. Server binds only to `127.0.0.1:1601`.
10. Request-body cap is 32 MiB, generation timeout is 600 s, rollover count is capped at one.
11. Remote mode defaults to off.
12. With remote mode off, the server binds only to 127.0.0.1.
13. With remote mode on, non-loopback requests without a correct Bearer/X-DSH-Gateway-Token receive HTTP 401.
14. No token or signing key is hard-coded in source or committed to the repository.

## Real-device acceptance gates

Run on the target vivo / Android 16 device.

1. APK installs without the “system compatibility” failure.
2. Old AI Gateway can remain installed simultaneously.
3. App opens DeepSeek and user can sign in normally.
4. `GET http://127.0.0.1:1601/health` returns HTTP 200 while the app is running.
5. DSH provider `deepseek-web-v2` can send a normal text prompt and receive streamed output.
6. A response lasting over 200 seconds is not truncated by the gateway.
7. JPG/PNG attachment is really present in the DeepSeek composer before prompt send.
8. PDF/Word attachment is really present before prompt send.
9. Attachment failure is fail-closed: prompt is not silently sent without the file.
10. Native/pseudo tool-call round-trip still works.
11. When DeepSeek reports conversation/context limit, exactly one new chat is created and the current full DSH context is replayed.
12. No infinite new-chat loop occurs.
13. Switching DSH to foreground and the gateway to background does not immediately kill the loopback server.
14. Tailscale remote mode detects a 100.64.0.0/10 address when Tailscale is connected.
15. Remote GET /health without a token fails with HTTP 401.
16. The same remote request with the displayed token succeeds.
17. Disabling remote mode makes the Tailscale/LAN endpoint unreachable again while localhost remains available.

## Evidence

For each gate record PASS/FAIL/NOT_TESTED and the concrete evidence: workflow run/job ID, artifact ID, SHA-256, installer screenshot/error, HTTP response, or screen recording/log excerpt.

If any required runtime gate is NOT_TESTED, the maximum verdict is PASS_STATIC.
