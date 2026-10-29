# OmniGate v0.7.1 investigation — original duplicate runtime ownership

Status: **DIAGNOSTIC SOURCE ONLY, NOT A SIGNED APK, NO DEVICE PASS**.

## User evidence
- v0.7.0: ChatGPT webpage visibly accepts plain `你好呀` and replies, but OmniGate 8787 still does not return that answer.
- Visible floating debug shows `网页回复字数=0`, `原生缓存字数=0`, `回复来源=no_scoped_assistant_reply`, and recent `send_button_clicked_unconfirmed`.
- Large blue original `返回` floating overlay still occupies the bottom-right of the model view. The previous `TOGGLE_OVERLAY` command has not been proven to remove or resize this window.

## Ground truth from private original third-party APK
Inspected exact `OmniGate_v054.apk` DEX class/method/field tables and invoke references:
- `MainActivity.startServer()` constructs and starts its own `ApiServer`, owning its own `WebDriver`, `server` and `port`.
- `OverlayService.startGateway()` **also** constructs its own `ChatController`, `WebDriver`, `ApiServer`, owns its own browser and runs a tunnel client. **Two native runtimes can exist.**
- `OverlayService.onDestroy()` calls `stopRuntime()`. The latter invokes `ApiServer.stop()`, `WebDriver.close()`, `ChatController.close()`. **Do not stop this service merely to hide its large UI**.
- `OverlayService.createWindow()` calls `installDrag()`; `setCollapsed()` updates window dimensions, but apparently the currently displayed collapsed control is still too big. Editing only the in-app OmniGate dock will not resize this original service-owned window.
- `ApiServer` stores a real `java.net.ServerSocket serverSocket` alongside `running` and `port`. Checking `running=true` or TCP connectivity alone does not identify which runtime owns that socket.

## Completed scoped code change
- On `work/omnigate-integrated-v05`, public-source `OriginalCoreBridge.diagnoseVisibleHostSocket()` now **read-only** inspects the live original MainActivity's `ApiServer.serverSocket`, `isBound`, `isClosed`, `getLocalPort`, configured port, and original OverlayService's static running flag. It never starts/stops a server or reads private chat content.
- HEAD: `75ca8445f1a52c56227829ac8b919e035b3174d2`.
- GitHub Actions: https://github.com/initial2024/text/actions/runs/40816506449
- Never merge or publish binary until source CI and device E2E.
- An attempt to add a hard `stopService` button was deliberately abandoned because that can stop the original native API and corrupt state.

## Reply extraction
The original `assets/ai_gateway_bridge/bridge.js` `chatgptAnswerText()` accepts explicit assistant-role selectors. The user report `no_scoped_assistant_reply` means none matched even though the rendered website displays an answer.
Contemporary ChatGPT new app-shell variants also omit many legacy `assistant` / `conversation-turn` selectors (see external 2026-10 issue https://github.com/miuuyy/codex-chatgpt-web/issues/813).
Do not synthesize a model reply from arbitrary document.body text. Preserve request/response provenance; scope to an actual new assistant turn with independent evidence or report unsupported DOM.

## Required next gate before new APK
1. Read original DEX methods `MainActivity.startServer`, `OverlayService.startGateway`, `OverlayService.setCollapsed`, `OverlayService.stopRuntime`, `ApiServer.handleNonStream`, `WebDriver.chat/readPageState`.
2. Verify which actual ServerSocket holds 1601 in the user's process, and if `OverlayService` is active. A false ownership result with TCP true suggests a second runtime, not proof of model failure.
3. Design one shared WebDriver/API owner without creating duplicate classes or prematurely destroying a live host; keep original website/GeckoView active.
4. Rework the original global Return view dimensions (target 40–48 dp, draggable and hideable) **without** invoking OverlayService.stopRuntime, and make the single unified OmniGate panel the replacement entry point.
5. Test new ChatGPT app-shell assistant reply provenance; do not accept a user turn or generic chrome text; wait for actual assistant turn and completion before returning HTTP 200.
6. Real Android test: original text website works, local `8787/v1/chat/completions` returns HTTP 200 with nonempty correct answer, the global return surface no longer blocks the model composer, and there is no session corruption.
7. Image/file transfer only after text passes.

**No v0.7.1 signed APK created from this diagnostic change.** Current signed candidate stays `OmniGate_v070.apk` with genuine E2E blocker.
