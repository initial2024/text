# OGPROFILE05 — Android user/work-profile isolation and Claude browser readiness

Scope: `initial2024/text`, branch `work/omnigate-integrated-v05`, Draft PR #1. This note is a source/QA gate, not a phone E2E acceptance or signed APK release.

## Correct boundaries

1. Android **secondary users** have their own installed app state and app-private data. A separate **managed work profile** also has a separate app data domain. Both require a system-supported profile created by the device owner/system or an appropriate device-policy controller; OmniGate does not silently provision one.
2. Install/run the same signed OmniGate APK **separately in each profile**. Gecko `contextId` per slot separates cookies and site storage within one app installation; it is **not** equivalent to Android user isolation or a hardware virtual machine.
3. A single physical phone may still be recognized as one device by an online site, even across independent Android users. We do **not** spoof Android ID, IP address, UA, WebGL, Client Hints, fingerprint, geolocation, attestations, or CAPTCHA. No guarantee of avoiding platform risk checks.
4. The original native WebDriver/8787 account remains a **different owner** than auxiliary Gecko sessions. Do not equate picker selection with API/main-account switching.
5. Claude remains manual, browser-only; no background login, API adapter via browser, silent retries, account cycling on 401/403/429/challenge, or token/cookie export.

## Latest source optimization

The **native “账号空间 ▾”** control appears immediately in the owned browser header, even while the provider's own top-right account avatar is still rendering. It offers manual account picking, local slot settings, an environment check, and **user-triggered** reload. Native Gecko `ProgressDelegate` reports page start/progress/stop for Claude without script injection and without misreporting web-page completion as authentication success. It does not speed up Claude's website-side React/SSO rendering; it makes app navigation immediately accessible and honest.

Switching a slot closes the previous GeckoSession and opens the new selected context; a close error blocks reuse until a verified restart. Session identity is checked on progress callbacks so a late callback from a closed session cannot overwrite status for the newly opened account.

## Required Android user and work-profile checks

- Check system support on the actual device. Android OEMs may omit multiple users or limit managed profile provisioning. Work profile is provisioned via a user-approved trusted DPC; ordinary OmniGate does not request device owner, root, Shizuku, or DPC privileges.
- In the Android UI, install the *same signed APK* in the personal profile and the work profile (or a second Android user). Grant any notifications/overlay or storage/file-picker permissions separately where applicable. Refusal must not cause a crash or authorization bypass.
- Verify profile separation with local test-only accounts: in profile A log in; profile B must start without A's auth cookies/passwords/site storage. Never use production secrets in CI. Switch back and verify each original state remains untouched.
- Verify the `账号空间 ▾` button is accessible immediately after opening Claude, *before* the site's account avatar appears. `onPageStop` must not display a false “已登录” indicator. Test slow/failed connection, login challenge, switching during load, force-stop/restart, and returning from background.
- Verify opening a system/external browser is **explicitly user-confirmed** and that its target may be resolved outside the present profile. Do not automatically pass private file URIs or website cookies to other profiles. Android work policy can prevent an external intent or document picker; failures should be handled safely.
- Verify the local gateway/sidecar **port 8787**: multiple instances belonging to different Android users/work profiles must not both assume exclusive ownership of the same local port. On conflict the second instance must fail closed, avoid hijacking the first user's gateway, and not silently reroute traffic.
- Observe traffic while another provider/slot is active: no preloading `claude.ai`; no hidden refresh, request replay or forced relogin. Manual CAPTCHA and platform challenges remain manual.
- Verify users/profiles can stop apps in the background and that system resource/notification/overlay restrictions do not invalidate the UI security boundary. Each isolated session must release ownership on close and not resume without a user action.
- Re-run all of these on real Android 35/36, not solely a JVM test or Android compile.

## Release gates

Source compile + static contract checks are necessary; on-device login/cookie isolation, cross-profile intent handling, per-user gateway ownership, and signing/install verification are *separate mandatory gates*. A private APK or fixed signing key must never be committed to the public repository. No automatic deployment/merge until these gates pass.

References (Android/Mozilla official):
- https://source.android.com/docs/devices/admin/multi-user
- https://developer.android.com/work/managed-profiles
- https://developer.android.com/work/dpc/build-dpc
- https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoSession.ProgressDelegate.html
