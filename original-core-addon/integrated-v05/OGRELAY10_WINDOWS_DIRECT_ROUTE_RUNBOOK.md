# OGRELAY10 — Windows direct-path IPv4/IPv6 isolation

## Observation and scope
User's actual Windows Node24 OGRELAY09 `--diagnose` output: `dns=RESOLVED`, `health_http=null`, `health_network.classification=CONNECT_OR_READ_TIMEOUT`, `cause_code=ETIMEDOUT`, `proxy_env_present=false`, no POST. This is a Node client direct-network failure, **not** proof that OmniGate / mobile DSH or public API authentication is defective.

## Changes
One standalone Node24 source probe `omnigate_direct_route_probe.mjs`, with default `DRY_RUN_NO_NETWORK`. Only `--probe` performs one DNS lookup and (for each available family) bounded 7s direct TCP/443, TLS/443 with certificate validation, and fixed-origin HTTPS `GET /health`. IPv4 and IPv6 are classified separately. No redirects, model POST, credentials, response bodies, proxy auth, TLS bypass, browser control or automatic retries. Emits only family counts, status/error categories and rounded timings, **never IP addresses, headers or payload text**. Each family takes a maximum of about 21s; families run concurrently. Missing DNS family skips its network tests.

## Windows comparison
If Node direct path times out, compare **read-only** native Windows PowerShell `Invoke-WebRequest https://gw-33e850180248.claua.de/health` status, with `[System.Net.WebRequest]::GetSystemWebProxy()` bypass boolean (do not print proxy URL). If PowerShell works but Node direct fails, investigate authorized local Windows proxy / split tunneling / per-application firewall rather than resetting phone Relay.

Node 24's built-in `NODE_USE_ENV_PROXY` opt-in reads environment variables `HTTP_PROXY`, `HTTPS_PROXY` and `NO_PROXY`; it does not establish that Windows system proxy is absent. Never copy a private proxy address/API key into GitHub or use an untrusted proxy for credential-bearing POST.

## Release and evidence
This diagnostic has **no Android app source modification**, and no real Windows network test occurs in CI. Even an HTTP 200 only proves direct probe reachability, not a signed APK, SSE receipt or DSH UI. Keep OGRELAY08/09 issue open and signed APK release blocked.
