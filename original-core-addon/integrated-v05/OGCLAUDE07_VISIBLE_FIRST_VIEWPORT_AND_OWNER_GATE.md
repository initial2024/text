# OGCLAUDE07 — first visible viewport + strict owner switch

Date: 2026-11-06. Repository: initial2024/text; branch: work/omnigate-integrated-v05; Draft PR #1.

## Reported symptom
After successful user-driven Claude web login, the website's upper-right account/navigation control appears late. There is no verified timing trace proving whether delay is due to Claude's server-side hydration, network, Gecko rendering or an initially collapsed viewport. The implementation previously created its 52dp collapsed overlay before opening the GeckoSession and calling loadUri.

## Minimal source correction
- Only after an explicit user tap, expand the existing overlay *before* its GeckoSession is opened or navigated. A real responsive viewport is visible from the first load instead of 52dp/minimized initial geometry.
- Reopening an already-owned browser session expands it without reload, login duplication or account switch.
- If a previous GeckoSession or view release failed, menu-triggered account changes fail closed. Do not create a replacement or silently fall back to shared session storage.
- No JS/DOM injection, UA/Canvas/WebGL/device tampering, cookies/token copying, forced reload, timer loops, preloading of Claude while on other providers, API auth or modification of original 8787/1604 bridge. No false promise of reducing Claude's website-controlled rendering latency.

## Acceptance gates
1. Verify source contract in OGACCOUNTS05 and Android compile CI.
2. On a real device, explicitly open Claude and confirm that a full-size viewport is present before first navigation; test login with a user-controlled account.
3. After successful login observe whether the actual site upper-right UI remains delayed; measure separately: initial page paint, authentication redirect, user-menu render. App-owned controls should appear immediately, site-owned controls must not be spoofed.
4. Switch to another provider, return, and verify session cookies remain in the correct context and no other profile logs in automatically.
5. Simulate a Gecko session close failure and verify subsequent profile switching is blocked without cross-context load.
6. Test repeated overlay minimizing, expansion, rotation and background/foreground activity. No claim of device E2E or production APK until these pass.

Public source only; do not publish signed APK, signing credentials, session data or profile identifiers.
