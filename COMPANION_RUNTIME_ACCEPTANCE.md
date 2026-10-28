# OmniGate Companion + Original AI Gateway runtime acceptance

## Architecture under test

client -> OmniGate Companion :8787 -> frozen original AI Gateway :1601 -> original GeckoView/browser automation

The Companion must not own a second provider WebView or a second floating chat window.

## PASS levels

- FAIL: build/runtime regression or original-core feature broken.
- PASS_STATIC: build, signature, architecture gates and frozen-core hashes pass.
- PASS_WITH_LIMITATIONS: real-device basic proxy works, but context-limit or disconnect protection has not been reproduced.
- PASS: all tests below have real-device evidence.

## Required real-device tests

1. Original core alone
   - Launch com.deepseek2.app.
   - Verify original text input/send works.
   - Verify original image upload works.
   - Verify original file upload works.
   - Verify original floating mini window works.
   - Verify existing login/Cookie identity remains available.

2. Companion detection
   - Start original core first.
   - Start OmniGate Companion.
   - Companion status must show: 8787 running and original core 1601 connected.
   - If original core is stopped, Companion must report 1601 not connected; it must not silently switch to its own browser implementation.

3. Local API
   - Base URL: http://127.0.0.1:8787/v1
   - Any non-empty local API key must be accepted.
   - Empty API-key field must fail.
   - Test each model id:
     - deepseek-chat
     - chatgpt-web
     - grok-web-fast
     - glm-web-flash
   - Confirm the message visibly appears in the original app's webpage and the returned answer reaches the client.

4. Attachments through the original core
   - Send one JPG/PNG from DSH/compatible client and confirm the original web page shows the upload.
   - Send one PDF or DOCX and confirm the original web page receives it.
   - The Companion must not implement or substitute its own file-input DOM logic.

5. Public relay
   - https://gw-33e850180248.claua.de/v1 must require the exact OmniGate public API key.
   - Wrong public key -> 401.
   - Correct public key -> request reaches original :1601 and returns normally.

6. Do-not-randomly-stop gate
   - Start a deliberately long answer.
   - Background/replace the DSH/Minis UI or transiently detach the downstream client.
   - Observe the original web page.
   - The page must continue generating; Companion must continue draining :1601.
   - A temporary missing stop button, silence, or no-new-token period must not create a new chat and must not click Stop.

7. Explicit user cancellation
   - An actual user Stop/Cancel action may stop the active request.
   - Generic UI/client lifecycle abort must not be classified as explicit user cancellation.

8. Context-limit rollover
   - Reproduce a real provider-side context/conversation-limit message.
   - Only then may rollover occur.
   - The fresh chat must continue the same unfinished task without asking the user to repeat it.
   - Current-turn attachments may be re-uploaded.
   - Historical attachment bytes must not all be duplicated.
   - Maximum automatic rollover: 3.
   - Failure to find/create a fresh chat must return an explicit AUTO_ROLLOVER_FAILED-style error; no loop.

## Frozen-core prohibition

Do not claim PASS if any original APK entry outside these approved functional changes differs from the locked baseline:

- assets/ai_gateway_bridge/bridge.js
- classes.dex (verified timeout immediates only)

Signature metadata may differ in a test-signed APK.
