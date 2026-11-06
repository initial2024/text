# OGTRANSLATE10 / App languages + translation diagnosis (2026-11-05)

Status: **SOURCE CHANGES COMMITTED; SIGNED INSTALLED APK AND DEVICE E2E NOT VERIFIED**.

Repository: initial2024/text. Only branch: work/omnigate-integrated-v05. PR #1 (Draft).
The original app binary, signing certificate and real account profiles MUST remain intact. No DSH com.dsh.android modification.

## Screenshot repro and diagnosis
- Android safe reader says "系统未提供设备端翻译服务；未发送内容至在线翻译".
- Old Gecko full-page translator appears to wait without a translated page.
- Android App info lacks the per-app Language option.
- Android 12+ SDK level alone does not guarantee an actual configured system TranslationService.
- Earlier Gecko code passed null translation options, did not opt in to a model download,
  and treated accepted requests as though visible page translation had finished.
- The private full APK previously lacked language metadata; the library AAR alone
  does not update the original APK's binary manifest.

## Source fixes
1. Detect a missing Android system translation service before extracting any text.
   Bounded 35-second once-only callback for safe reader and worker-thread capability probe.
2. Manual external app fallback via ACTION_SEND chooser; the user must select the
   destination, and no account tokens/cookies are included. This is not unattended
   translation; external apps have their own privacy policies and may be absent.
3. Legacy Gecko full-page translator requires explicit per-use consent, sets
   downloadModel(true), tracks TranslationState.hasVisibleChange/error, and reports
   a non-destructive 45-second timeout warning. Request accepted is not completion.
4. Android 14+ LocaleManager override declares zh-CN and en-US on app startup,
   allowing App Info > Language after launching the upgraded app once.
   Android 13 static XML + manifest declaration only works when the resource and
   manifest are actually merged into a newly signed private APK.
   The new shell has partial English labels; original model screens and dynamic
   status strings may remain Chinese until a comprehensive localization pass.

## Non-negotiable safety
- Do not reload or mutate original web page from safe reader.
- Do not inject JS, spoof UA, silently call online translation, rotate account,
  start unrelated provider in background, replay a failed model request or bypass challenges.
- Preserve original main package, original model session and 8787->1604 runtime ownership.
- Keep fixed signing identity; never put keystore secrets in GitHub source or artifacts.

## Required verification before release
1. CI library compile + D8, no duplicate classes or missing Android APIs.
2. Inspect signed private APK versionCode, exact certificate, merged resources and
   manifest, and exactly one MAIN/LAUNCHER activity.
3. Android 16: launch once; App Info > Language shows 中文 (简体) / English;
   choose each; verify translated owned UI without resetting accounts.
4. Android without TranslationService: fail immediately, not after a long wait.
   Cancel the reader; original ChatGPT/Claude URL, scroll, draft, cookies remain.
5. External chooser: no automatic sending; cancel has no browser side effects;
   only user-selected external app receives page text.
6. On-device service available: either actual translated text or bounded error;
   no false success.
7. Legacy Gecko translator: explicit consent, language model download if needed,
   observe real visible change or error, timeout doesn't force a reload.
8. Android 13: only mark PASS after private final APK resource/manifest merge and
   real device's App Info language menu verification.
9. Full user-facing English translation is a separate gate from mere option visibility.

**Current status: BLOCKED_PRIVATE_SIGNED_APK_AND_REAL_DEVICE_E2E.**
