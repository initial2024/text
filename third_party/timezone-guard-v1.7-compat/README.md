# Timezone Guard v1.7 — compatibility repair (UNSIGNED TEST)

Upstream: https://github.com/3Radiance/Timezone-Guard (MIT; v1.7 released 2026-11-08).
Only four upstream files are overlaid in the build; upstream ZIP is pinned by SHA-256 before modification.

## Why
User reports Cloudflare challenge loops while the third-party timezone extension is installed; uninstalling it restores normal manual verification. That is useful A/B evidence, not proof of any exact failing API.

## Scope of patch
- `spoof/main.js`: no speculative Date/Intl/iframe/Worker hooks before the extension's asynchronous per-frame exception verdict. This improves native behavior on excluded sites but early page scripts on other sites may see the original timezone before configuration arrives.
- `bridge.js`: stop writing extension configuration to the website's LocalStorage/SessionStorage, send updates via a document event, keep bfcache attributes only on actively spoofed pages, and leave excepted sites without persistent markers.
- `background/background.js`: optional HTTP header spoofing defaults to OFF on a fresh configuration, while a previously saved explicit ON setting is respected; existing exception/iframe rules remain intact.
- `frontend/popup.html`: default header checkbox OFF. Timezone list, chosen timezone storage and site-exception editor are unchanged.
- No challenge token forging, validation bypass, automatic site whitelisting or forced account resets.

## Build/verification
The GitHub Actions workflow downloads the published v1.7 ZIP and checks SHA-256 `001a210b33d6faa953341478539de6a4d737499a95ea92849807c9dcd1fd9d5b`. A synthetic bridge test checks exception behavior and timezone retention. The build script checks the full ZIP manifest, all JS syntax and static invariants, then outputs UNSIGNED ZIP/XPI for developer testing.

These artifacts are **not signed by Mozilla and may be refused by standard Firefox/GeckoView**. This repository **does not** contain the third-party developer's signing key. Do not represent them as normal installable AMO releases.

## Manual check on Android
1. Prefer reinstalling the upstream signed v1.7 from Firefox Add-ons and using its Site Exceptions to exclude the problematic **website domain**. Full-close and reopen that tab/session, not only refresh.
2. Under the extension's settings, disable `Spoof HTTP Headers` and use the browser's default User-Agent.
3. Compare manual Cloudflare verification on the same network in three conditions: extension OFF, extension ON with site excluded, extension ON without site excluded. Do not clear cookies, Gecko profiles or stored login data.
4. For the patched development build, first validate on a supported Firefox development environment. If tests fail, do not install in OmniGate. A Mozilla-signed replacement add-on must be distributed through an appropriate authorized signing channel.

## Release gates
- CI `TZ_GUARD_COMPAT_STATIC_CHECK=PASS` plus bridge synthetic tests.
- Real GeckoView page/iframe/Worker checks including selected IANA zone, exception native behavior, account-login retention.
- Real user-driven Cloudflare challenge no longer loops on a permitted site; never claim pass before observation.
- Original OmniGate APK unchanged: same package, certificate and data because it was never rebuilt or replaced.

Rollback: use official signed v1.7 and Site Exceptions; do not uninstall OmniGate or wipe website/account data.
