# OGTZ02: website timezone on Android 16 — measured failure and fail-closed response

**Project:** OmniGate / `initial2024/text` / `work/omnigate-integrated-v05`, Draft PR #1.

## User-observed failure (2026-11-19)
- v1.3.16 shared Gecko timezone configured `America/New_York` and restarted.
- BrowserLeaks JavaScript still shows `GMT+0800 (Singapore Standard Time)`.
- Setting Android process `TZ` at app bootstrap did **not** establish a Gecko web-visible timezone override.
- Failure is evidence for this exact installed build/device, not a proof about every Gecko version.

## Immediate regression containment
- Stop calling `Os.setenv("TZ", ...)` or changing any process/global clock.
- Preserve old `requested_timezone` for safe migration/reset; never activate it.
- Expose current status in account quick settings without an ineffective "enable" button.
- All existing per-context native clocks, independent storage, work-profile separation, sign-in and transfer logic remain untouched.
- No website timezone success claim or new APK release until actual on-device verification.

## Engineering gate for a genuine replacement
1. Establish a supported engine-level SpiderMonkey/Gecko timezone override interface. A `GeckoSession` timezone option is not currently exposed by the stable public API.
2. If only a single Gecko runtime is used, override should intentionally apply to **all** primary/auxiliary Gecko pages, not silently act per account.
3. `Date.toString()`, `Date#getTimezoneOffset()`, local getters, `Date#toLocaleString()`, `Intl.DateTimeFormat().resolvedOptions().timeZone`, and worker / iframe / cross-origin cases must agree; preserve real epoch time in `Date.now()`.
4. Verify `America/New_York`, `Asia/Tokyo`, DST gap and transition dates, engine startup order and system fallback after cold start.
5. Test authentication, file picker/upload/download, tab handoff and profile isolation on the user's device before any "website timezone changed" claim.
6. Do not use partial content-script spoofing or Java's `TimeZone.setDefault` as the asserted full engine solution.

## Release gates
- OGTZ02 Java tests: preference persistence and fail-closed no environment writes.
- Android compile and signing identity: must be validated separately.
- BrowserLeaks web `Date/Intl` parity: **FAILED for v1.3.16**; not repaired in OGTZ02.
- Signed APK: **not produced** by this source-only change.
