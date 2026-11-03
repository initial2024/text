# OGMAIN01 — Main execution profile is NOT an auxiliary browser

## Verified original binary boundary
The original `com.omnigate.core` native `MainActivity` and `OverlayService` can each own a distinct `WebDriver` and `ApiServer`. Opening `IsolatedGeckoBrowser` alone never changes the original 8787 execution account. Gecko context IDs are immutable once the session is opened. No cookie copying or unsafe `WebDriver` field replacement is allowed.

## In this source commit
- Explicit user action selects a **pending native execution target**, separately from auxiliary browser selection. Persists only provider, slot, and generated random Gecko `contextId`.
- Before outbound web-model submission through 8787, the bridge rejects requests with HTTP 409 unless the *current verified main* original `ApiServer` holds the exact same `WebDriver`, an unambiguous reachable `GeckoSession` has the desired `contextId`, and the provider matches. This is fail closed, not a claim that main switching already works.
- An owner mismatch, unrecognizable reflection structure, null/default Gecko context, different slot or provider, or ambiguous native/overlay owner **cannot** count as verified.
- The user can explicitly cancel a target to restore legacy native routing. This does not reset platform holds or delete Gecko storage.
- Claude official direct API retains its own risk gates and is not forced into a Gecko context.
- Pure policy test and Android compilation must pass before considering a binary build.

## Remaining actual switch (blocking)
The original native WebDriver still constructs its own GeckoSession with a default context. The NEW source-side method `MainAccountTargetStore.contextForNewNativeSession(Context,String)` is a contract for an approved exact-version native integration patch. A future, audited modification **inside original GeckoSessionSettings.Builder at native WebDriver construction time**, before `open()`, must select this validated context ID and re-establish original Gecko bridge handlers. No mutable-context API or unsandboxed cookie transfer exists. Do not pretend a source contract is a completed native binary integration.

**Before enabling true one-tap main switching**, obtain the user's private signed original APK and inspect exact `WebDriver`/browser creation method; only when idle, require a user-approved teardown/recreate or an atomic owner-handoff with full session identity proof. Ensure the old socket/driver is retired without disrupting in-flight tasks. Require first user-authorized model message to complete under the new selected context; validate 401/403/429 challenge remains a hard stop. Preserve installed package, fixed signer, existing data, all private bridge assets, original user sessions. This PR must stay Draft until then.

No real APK is available in this connector session; no new signed APK was created or verified.
