# OGWEBSEC01 — fail-closed site permission and download source disclosure
Date 2026-11-06. Branch work/omnigate-integrated-v05, Draft PR #1.

GeckoSession.PermissionDelegate onAndroidPermissionsRequest grants capabilities for the entire installed Android application, not for a single Gecko contextId. Do not automatically pass through previously granted app camera, microphone or location permission. The sensitive app/physical media callback is denied for this candidate, even if Android already grants it. Content permission choices are limited to autoplay and must match the Gecko ContentPermission contextId and HTTPS origin. The installer verifies that Gecko getPermissionDelegate() is the exact installed delegate; on unavailable API or owner mismatch, throw and abort the auxiliary session. No fallback to unknown Gecko permission behavior is permitted.

There is no claim that a site can never use sensors outside the modeled API, and native original Gecko owner is unchanged. Future opt-in camera/mic support must require a per-site foreground UI, verified OS capability, revocation and hardware-stop E2E before release.

Download onExternalResponse now discards non-2xx bodies. The confirmation screen displays only a site hostname, not a full signed URL or query token. The data remains in the per-context private directory, user-confirmed delete/export as before.

Acceptance: CI source/compile and Android real-device tests: wrong context denies; permission hook missing -> session fails; OS callback/media callback reject; autoplay explicit yes/no; switch account while permission dialog open rejects; no other account gains permission; HTTP 4xx download does not persist file; signed URL not shown on screen. Do not merge, publish APK or claim production secure until verified. Original model request transport and 8787 remain untouched.
