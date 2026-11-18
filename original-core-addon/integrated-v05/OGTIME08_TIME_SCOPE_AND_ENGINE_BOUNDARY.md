# OGTIME08 — per-account displayed timezone and virtual local wall clock

**Release scope:** native OmniGate **display-only**. Not a Gecko time override.

## Reproduced observed behavior (2026-11-19, user-provided test)

BrowserLeaks JavaScript page within a Gecko account:
- `Date/Time System Time: Thu Nov 19 2026 00:14:37 GMT+0800 (Singapore Standard Time)`
- `Intl DateTimeFormat: Singapore Standard Time`
- Account preference selected: a US timezone.

Conclusion: the user's selected timezone affects **native Android OmniGate labels only**;
the website still sees the real Gecko/OS timezone. These results are not evidence of
the VPN exit IP or of the overall website risk/region assessment.

## What OGTIME08 genuinely implements

- Validate canonical IANA time zones, follow `SYSTEM` dynamically, and format
  account-scoped time with DST rules.
- Store `clock_timezone.<contextId>` and `clock_virtual_shift_ms.<contextId>`
  in app-private SharedPreferences with durable `.commit()`.
- Native virtual time = `System.currentTimeMillis() + validatedOffset`.
  Allow explicit date/hour/minute selection in the configured IANA zone and
  reset to zero. Display continues to advance after selection.
- Refuse nonexistent spring-forward DST times, incorrect time-zone IDs,
  and offsets outside +/- 10 * 366 days.
- Keep account A/B and Android personal/work profiles separate. Context erase
  retires their display preferences.
- Do not alter cookies, site storage, browser UA, WebGL, Location, IP,
  GeckoRuntime, `Date.now()`, JS `Intl.DateTimeFormat`, Worker clocks,
  geolocation, TLS, expiration timestamps, or the device system clock.
- Never auto-navigate from a clock setting, auto-reload a webpage, or
  tamper with website-upload or login paths.

## True website-visible per-context timezone: NOT IMPLEMENTED

Mozilla GeckoView `GeckoSessionSettings.Builder` exposes `contextId`,
`userAgentOverride` and other options but **no per-session timezone**:
https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoSessionSettings.Builder.html

A WebExtension content-script timezone override (e.g., Timezone-Guard)
may modify `Date.prototype` / `Intl` reads after injection, but scripts,
iframes, dedicated/shared/service workers, Temporal, JS-engine clocks and
early page code may disagree; it cannot change the device system time:
https://github.com/3Radiance/Timezone-Guard

Camoufox's custom Firefox engine demonstrates per-realm timezone patches
including Worker propagation. It requires SpiderMonkey/C++ engine changes,
a new Gecko build and real-device tests; its own documented global fallback
must not be copied into OmniGate's shared runtime:
https://github.com/VisualxIntelligence/camoufox/blob/main/docs/per-context-patches.md

**Do not market a content-script shim as full engine isolation.** Do not
enable experimental global timezone overrides in the original primary account
or silently alter user authentication and security timestamps.

## Website validation protocol

1. Install signed test APK over the old APK, do not clear user data.
2. In auxiliary account A set IANA `America/New_York`, B `Asia/Tokyo`.
3. Verify independent native labels and configurable virtual date/time,
   then compare both to the actual system clock.
4. Using the **same GeckoSession** manually visit
   https://browserleaks.com/javascript with user confirmation.
5. Compare displayed native clock with the website `Date.toString`,
   `Date.toLocaleString`, `Intl.DateTimeFormat().resolvedOptions().timeZone`
   and `Date.getTimezoneOffset()`. These values are website-visible,
   and should **NOT** be expected to match the native preference yet.
6. Test switching A/B and full-page/overlay handoff without data loss,
   then verify upload/download and permissions remain unchanged.
7. Never claim website timezone override PASS or independent per-space
   "system time" until actual engine-level equivalence is proven on-device.

## CI evidence categories

- Pure Java IANA/DST/virtual-offset tests: static/unit only
- Account Context simulation: Java isolation only
- Android library compile: ABI/source only
- Signed APK: package and signature integrity only
- Device E2E, website-visible timezone parity: **NOT TESTED**
