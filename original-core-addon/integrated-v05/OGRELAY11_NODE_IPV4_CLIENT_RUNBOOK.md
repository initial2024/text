# OGRELAY11 — fix Node24 diagnostic fetch address-family preference

## User-supplied evidence
Windows direct-route OGRELAY10 demonstrated native Node IPv4 TCP443 and TLS connection, direct GET /health HTTP 200; Windows PowerShell GET /health HTTP 200. IPv6 path failed with deliberately redacted error OTHER. The *same Windows computer* then ran `node --dns-result-order=ipv4first --no-network-family-autoselection -e "fetch(.../health)"` and obtained `http_status:200` with `post_attempts:0`.

This supports using that exact connection preference in the **independent** Node public SSE test client. It **does not prove** that IPv6 was the unique cause of all earlier timeouts or that any live model POST/SSE reached DSH.

## Code
Add `omnigate_ipv4_preference.mjs` and enable `setDefaultResultOrder('ipv4first')` and `setDefaultAutoSelectFamily(false)` only for `--diagnose` and `--execute` inside the diagnostic Node process. Default dry-run still makes no network call or OS mutation. No system DNS/IPv6 changes, no Android code change, no cloud Relay config, no credential exposure.

The opt-in `--execute` remains **one** no-tool model POST and has no automatic retry/redirect following; the empty-key branch still sends zero requests. Avoid further user requests for repetitive network troubleshooting; the screenshot is sufficient to close the *IPv4 path-is-unreachable* hypothesis. CI only tests code / fixtures, not an actual model reply or phone UI.

## Safety and release
Preserve installed working OmniGate + original DSH. Keep API Key locally private; no model calls from CI. APK publication, full SSE incremental painting, Relay delivery, and SessionStore end-to-end tests are still pending and not replaced by Node network success. Prior issue #7 should remain open for end-to-end receiver evidence.
