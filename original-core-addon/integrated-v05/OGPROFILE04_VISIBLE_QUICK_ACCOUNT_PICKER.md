# OGPROFILE04 — account picker invisible on real Android: direct quick-switch repair

**Real screenshot** 2026-11-03: Android `独立浏览器账号` AlertDialog showed descriptive text and only `取消`, but none of the four provider choices. Root cause was our use of both `AlertDialog.Builder.setMessage` and `setItems` on the same dialog. Android's message view takes the content panel, leaving the choice list absent.

Repair: remove `setMessage` from the chooser (not from other confirmation dialogs), and flatten the rows to exact **provider + account slot**, for example `DeepSeek · 账号 1（上次选择）` / `GLM · 账号 2`. Each tap immediately opens its associated isolated Gecko context. Home first card and Settings-top entry both use the same corrected picker. A separate clearly visible browser detail page provides the isolation and platform-risk explanation; cancellation changes no account state.

Choices use only the persisted slot count and last-selected index, and never claim to know whether a provider is logged in. Each fixed provider has 2 slots by default and at most 5. No cookies, passwords or account identifiers are copied. No automatic model POST, no original WebDriver or 8787 account migration, and no platform-limit circumvention.

Test: 8 default to 20 maximum explicit rows, provider/slot mapping, selected marker, corruption fail-closed; static checks specifically reject `setMessage` in chooser and assert direct `show(host,provider,slot)` routing. Exact-head Android DEX / fixed signing preflight and live device UI validation are additional separate gates.
