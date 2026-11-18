# OGTZ03 — Mozilla-signed timezone WebExtension trial (SOURCE CANDIDATE)

## Why change course
User's OmniGate v1.3.16 BrowserLeaks still shows Singapore GMT+0800 after process TZ override. OGTZ02 disabled that non-working experiment.

Mozilla GeckoView supports WebExtensionController.install(), list(), uninstall(), and Mozilla signature validation. The third-party MIT extension [Timezone Guard](https://addons.mozilla.org/en-US/firefox/addon/timezone-guard/) v1.7 claims coverage of JS Date/Intl, iframes, Dedicated/Shared Workers and Service Workers. Its actual behavior **in this embedded GeckoView** is not established. Source: https://github.com/3Radiance/Timezone-Guard

Pinned signed XPI link, observed from official "Download file":
https://addons.mozilla.org/firefox/downloads/file/5224737/timezone_guard-1.7.xpi

## Security contract
- No install on app start. User explicitly enters account quick settings, opens warning, explicitly initiates install, then separately grants actual manifest permissions in Gecko's permission prompt.
- Refuse any other extension ID and any concurrent extension operation; avoid replacing an existing Gecko PromptDelegate.
- No elevated native privileges, remote agent, Marionette, ADB, network proxy, remote debugging, Android TZ or real epoch adjustments.
- Gecko's original runtime reused, the user's already-open GeckoSession left alone. Add-on settings viewed in a temporary Gecko session with a new isolated context ID.
- Third-party extension has broad website access. It can change the main browser and all auxiliary contexts and may affect logins or anti-abuse checks; **do not present as a risk-free or fingerprint-proof feature**.
- One-click uninstall from the same menu, preserving site sessions and user data. User must explicitly reopen pages to reset potentially patched script realms.
- Not bundled: third-party XPI is fetched from Mozilla only if the user authorizes. Source code is not redistributed in this repo.

## Technical checks
- OGTZ03 CI source contract, exact XPI/version, no automatic install, permission delegate, deny optional grants, safe uninstall.
- Android 35 compile and fixed-signing identity preflight must pass.
- **Not proven:** GeckoView v1.3.16's runtime allows MV3 extension APIs and all Worker response filters; whether add-on's popup loads; whether explicit permissions are enforced identically to Firefox desktop; whether original primary browser sees the override.
- **Not proven:** BrowserLeaks JS Date/Intl, CreepJS Worker / iframe / Temporal; DST correctness, login, upload/download; current system time remains unmodified.
- Do not create a production signed APK or claim success before these E2E results. Keep PR #1 Draft.

## Real-device test sequence (user driven)
1. Install an original-cert signed trial APK only after Android CI and package integrity checks.
2. Open main Gecko once (so existing runtime exists). Open one auxiliary account quick settings > OGTZ03. Review two permission dialogs and authorize if acceptable.
3. In the embedded extension panel select America/New_York, leave UA/header changes disabled.
4. Manually open BrowserLeaks in **same** Gecko browser; inspect Date, Intl and offset. Test an existing provider in a second account.
5. Try dedicated/shared/service worker probes where supported, first in a disposable test context with no logins.
6. Confirm original primary behavior, app main/work-profile isolation, cookies, uploads/downloads, and two-way live session handoff.
7. Uninstall from the addon UI and restart. Confirm system timezone restored in Date/Intl and accounts retained. Stop and rollback on any login/permission regression.

## If extension unsupported
Do not fall back to an injected partial script silently. Pursue engine-level implementation, with a genuine Gecko/SpiderMonkey build and byte-preservation/signing review.
