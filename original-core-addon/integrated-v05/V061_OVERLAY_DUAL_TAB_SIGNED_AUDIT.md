# OmniGate v0.6.1: compact dual-tab overlay / original model UI coexistence

## User report and intent
- v0.6.0 originally allowed selecting JPG, but API returned HTTP 502 {error:{message:'no reply'}}. Thus no proof the website handled the attachment.
- New small attractive movable floating entry must be visible while the original Grok/ChatGPT model view stays active. The expanded dock must contain API controls and original attachment injection controls as interchangeable tabs.
- This is an overlay within the current app, NOT a global always-on-top window over unrelated apps.

## Changed implementation
- CompatBootstrapProvider attaches ApiQuickPanel to BOTH original MainActivity (actual model/GeckoView) and ProviderActivity (provider picker), and releases it on pause/destroy. Does not auto-start 8787 or add a launcher icon.
- Replaced WindowManager TYPE_APPLICATION_PANEL based quick panel with FrameLayout DecorView child: collapsed 40dp diamond, expanded max ~300dp small dock, two tabs API / file injection, drag persistence, close/minimize, no SYSTEM_ALERT_WINDOW required. Real device still must verify view stacking above GeckoView.
- Added OverlayDocumentPickerActivity: transparent theme selected in onCreate, launches ACTION_OPEN_DOCUMENT and returns bytes to OverlayAttachmentDraft bounded to 8 MiB in memory. Chosen bytes/keys never saved to disk.
- Added OverlayUploadClient that posts the FROZEN original V2 attachment-envelope through local 8787 to original native API; reads without fixed proxy timeout; no Stop click or cancellation on minimize. Explicitly labels HTTP 502 / no reply as undelivered or unconfirmed, never a successful upload.
- Upload guarded by running local/native APIs, live original host and original Prefs model. A live host alone does not guarantee DOM upload or logged-in provider readiness. Completion of actual webpage attachment remains device-E2E pending.
- Original three DEX, GeckoView libraries, classes and assets/ai_gateway_bridge/bridge.js kept byte-identical to independently verified v0.5.4 boot baseline.

## Verified static evidence
- GitHub Actions https://github.com/initial2024/text/actions/runs/40699633674 completed SUCCESS: Java compiler, D8, DEX order/checksum regression, fixed signing-identity preflight.
- Private signed APK: OmniGate_v061.apk, package com.omnigate.core, versionCode=26, versionName=0.6.1.
- APK SHA256: d4422dc20919965f454edf9aae5403b954e79bd6dba95ef7c6c3cd153cde930c. Size: 180006826 bytes.
- ZIP CRC PASS, apk zipalign PASS, v2/v3 cert SHA256 bfdc4de8538066d29ecd17e80ab651d38ae3c440713f9651bd8ce1c81015d27d PASS.
- Manifest 147→151 nodes: one original launcher preserved, new service/settings/provider/picker non-exported, no second app icon.
- Four DEX all SHA1 / Adler32 / sorted string_ids PASS. 16650 defined classes, zero duplicate definitions. 1006 original baseline entries content-identical.
- Device visual behavior, attachment network round trip, and original web upload receipt: NOT VERIFIED.

## Stop conditions / safety
- If compact chip absent in real model view: do not claim overlay PASS. Collect screen and exact page; inspect activity/window lifecycle and GeckoView SurfaceView stacking.
- If HTTP 502/no reply persists: do not mark upload complete; inspect original website runtime/extension receive path, model page and bridge acknowledgements.
- No change to production/main, no merge PR, no original APK upload, no key release, no data wipe. Signed APK was delivered only as a private chat artifact.