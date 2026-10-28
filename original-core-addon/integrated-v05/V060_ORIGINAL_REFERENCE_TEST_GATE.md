# OmniGate v0.6.0 — original third-party APK behavior referenced

## User-reported v0.5.9 gaps
- Local selection of image in API console works, but real attachment transfer has not been proven. User asked whether a model/web session must be open first.
- API return chip and old floating overlay disappeared within actual Grok/ChatGPT browser UI.
- User requested that the original third-party AI Gateway binary/automation be the reference instead of inventing a new file protocol.

## Original APK grounded behavior
Examined PRIVATE user-owned original APK assets/ai_gateway_bridge/bridge.js without publishing it. Verified the frozen byte-identical bridge is present in the previous integrated signed baseline.
- parseRelayPayload validates base64 V2 JSON envelope with v=2, prompt, attachments, and fields id/name/mime/current/data.
- sendRelayText awaits uploadAttachments(current attachments, true) BEFORE sending prompt.
- uploadAttachments looks for provider DOM input[type=file] (optionally opens site attachment buttons), builds DataTransfer/File objects, fires input/change, and requires visual/lifecycle upload evidence within 20 seconds. Failure is not a successful upload.
- Existing app retains original extension, provider GeckoView, website login and attachment restrictions, original DEX and bridge; do not claim real file delivery without real provider response.
- Generation itself is not prematurely stopped by 8787 read timeout; original core and provider may still impose separate limits.

## Changes in private v0.6.0 candidate
- Added active provider model inspection through original Prefs.provider(Context) / AiProvider.getModel and weak-lifecycle MainActivity host existence gate. Attachment test rejects absent host, unknown provider or mismatched target model with visible instructions; host alive alone is not proof DOM/session login is ready.
- Added explicit “open provider webpage first” action in the console using the original ProviderActivity. Keeps original website injection unchanged.
- Added manual request to original MainActivity.requestOverlay() to reuse the original app's user-authorized floating overlay behavior.
- Added ApiQuickPanel, a token-scoped TYPE_APPLICATION_PANEL in-app window over original GeckoView MainActivity, with API shortcut, drag/persist position and pause/destroy cleanup. This has no SYSTEM_ALERT_WINDOW or foreground service requirement; popup/real-device visibility still needs testing. ProviderActivity retains original in-app chip.
- Did not alter original three DEX or browser bridge.

## Verified build
- GitHub Actions run 40692435023: compile, D8, DEX ordering regression, signing certificate preflight PASS.
- Built local private Android test APK: OmniGate_v060.apk, package com.omnigate.core, versionCode 25, versionName 0.6.0, SHA256 bc5c4ae2a879aed76cae0c149a802229e8724d5c8000340d0f6574ad0ce744d0; size 179998634 bytes.
- APK ZIP integrity, zipalign, fixed certificate SHA256 bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d, v2/v3 signing PASS.
- Original retained ZIP entries 1006, classes.dex/classes2.dex/classes3.dex and bridge.js byte-identical to bootable v0.5.4; new classes4.dex SHA256 40f126b70f49d400ace783ba4092dc9baea95646a2fcad797f33736aae6b3826; 16635 class definitions, zero duplicates; single launcher.

**Status: PASS_STATIC_SIGNED_DEVICE_E2E_PENDING**. No public APK, no private key committed, no production deployment or PR merge. Required device verification: original provider normal chat, quick API return in actual GeckoView, manual original overlay permission path, genuine upload acceptance with opened correct model, API 8787->original native completion; preserve user data.