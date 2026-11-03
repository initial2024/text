# OGMAIN02 — v1.0.5 private APK exact owner/session chain audit

**Input, read-only from user's connected file Library:** OmniGate v1.0.5 candidate, 170,559,340 bytes, SHA-256 `75023cca15ff0b58c1770e853439029eed145711e7ec05833dfd441d4ea3c21f`. Original APK/signing key are **not** uploaded to the public GitHub repository.

## Grounded in actual original `classes.dex`
Target: `com.deepseek2.app.gateway.ProviderBrowser.createSession()` `code_item` offset `5108208` (0x004DF1F0), `classes.dex`. **Confirm this offset independently before editing a different binary.** The method executes:

1. `new GeckoSessionSettings.Builder()`
2. `usePrivateMode(boolean)`
3. `userAgentMode(int)`
4. `viewportMode(int)`
5. **Directly `build()` — no `contextId(String)` call**
6. `new GeckoSession(settings)`

The `ProviderBrowser.<init>` method at code offset `5108592` puts this session in its own `session` field, passes exactly it into `GeckoBridge(session, provider)`, configures delegates, calls `GeckoSession.open(runtime)`, then `GeckoView.setSession(session)`.

`MainActivity.onCreate` (code offset `5136028`) assigns `browser: ProviderBrowser` and constructs `WebDriver(browser.getBridge(), provider)`. `MainActivity.startServer` (code offset `5139652`) creates the actual `ApiServer` from its own `WebDriver`. `WebDriver.bridge`, `GeckoBridge.session`, `ProviderBrowser.bridge/session/view`, `ApiServer.driver` and all three `provider` fields are present and correctly typed in the signed APK.

## This exact-build improvement

`NativeGeckoContextInspector` now requires these **exact object references**, same original model provider, active `GeckoView.getSession()`, and `GeckoSession.getSettings().getContextId()` equality. A guessed traversal through unrelated sessions is no longer accepted. Source policy is fail-closed if any field changes or is absent. The existing verified socket-owner check is still required before and after inspection.

## Actual native switch still needs a binary insertion

Insert trusted `contextId(String)` in `ProviderBrowser.createSession` **before `build()`** while the original WebDriver is being freshly constructed, using the explicit `MainAccountTargetStore.contextForNewNativeSession(...)` API. The exact `Context` and original selected model are available through original `ProviderBrowser.context/provider`. This must be a reviewed DEX method rebuild (not a patch of arbitrary invoke operands or an in-place change to an already opened GeckoSession), followed by a user-approved idle-only owner transition.

The original classes.dex lacks a `GeckoSessionSettings.Builder.contextId` method reference, even though GeckoView defines it elsewhere. Therefore just replacing the `build` invoke is not sufficient and would produce an invalid DEX/call signature. Correct DEX index tables, registers, code sizes, exception handlers, checksums and class references must be rebuilt with a verified toolchain.

**Current verified:** real APK original bytecode mapping and exact-context source gate. **Not done:** native DEX hook, private signer usage, newly signed installation, account login/streaming/transition E2E. Do not claim a one-tap native switch until those pass.

## Safe, exact-code constructor hook ABI

The public Android entry point added to the compatibility DEX is:

`NativePrimaryContextHook.contextIdForNewSession(Context, Object originalAiProvider)`.

The original `ProviderBrowser.createSession()` can pass **its existing** `this.context` and `this.provider` fields. Return `null` means no primary target was armed: retain the original default session context. A non-null return is a validated, same-provider isolated context. Invoke `Builder.contextId(result)` before `Builder.build()`. A mismatch must abort constructing the new session; do not silently fall back to the old account.

The original DEX needs a method-ID entry for the public hook and `Builder.contextId(String)`, plus sufficient verified local registers and updated code size. These calls are **not** present in original `classes.dex` and cannot be patched by swapping an existing `invoke-virtual` operand. The native code-item rebuild remains blocked until an executable and verifiable DEX assembler/injector and fixed signer run on a private original APK. No claim that the native hook has been installed in a distributable APK.
