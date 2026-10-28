# OmniGate 2.8.0

OmniGate is a clean Android rebuild of the original AI Gateway with a new product identity and a more complete UI, while preserving the original provider/model compatibility contract.

## Product identity

- App name: **OmniGate**
- Three main sections: **概览 / 会话 / 网页**
- Adaptive purple-blue OmniGate launcher icon
- Android splash + branded in-app launch animation
- Light/dark resources
- Compact 44dp provider-colored floating bubble

## Original provider compatibility

| Provider | Web page | Model id |
| --- | --- | --- |
| DeepSeek | https://chat.deepseek.com/ | `deepseek-chat` |
| ChatGPT | https://chatgpt.com/ | `chatgpt-web` |
| Grok | https://grok.com/ | `grok-web-fast` |
| GLM / 智谱清言 | https://chatglm.cn/ | `glm-web-flash` |

The original APK is the compatibility source of truth for provider/model ids and the first-choice DOM selectors.

## Connection panel

OmniGate shows these separately:

- Local Base URL
- Public Base URL
- Model
- API Key

Each value can be copied independently.

API Key behavior:

- hidden by default,
- reveal/hide,
- user-defined value,
- minimum 12 / maximum 256 characters,
- random secure regeneration,
- stored only in the app's private preferences,
- immediately enforced for `/v1/*`.

## Gateway reliability

- Local API: `http://127.0.0.1:8787/v1`
- Request-body cap: 32 MiB
- Hard generation timeout: 600 seconds
- 5-second generation-end grace
- real browser file upload using `File + DataTransfer`
- attachment fail-closed behavior
- full DSH role/tool context reconstruction
- automatic conversation rollover capped at one per submission

## Legacy public tunnel

OmniGate preserves the original tunnel contract where possible:

- generated host: `https://gw-<prefix>.deepseeks.dpdns.org`
- tunnel endpoint: `57.183.5.186:9000`
- TLS certificate pin
- registration frame: `REGISTER <token> <prefix>`
- local target: port 8787

A tunnel is **not** treated as healthy merely because registration returns OK.

OmniGate now reports stages separately:

1. remote TCP connect,
2. TLS handshake,
3. certificate-pin verification,
4. registration,
5. registration response,
6. end-to-end public probe.

After registration it verifies:

`https://<prefix>.deepseeks.dpdns.org/__tunnel_probe`

Only a matching probe response proves the public path is working.

The old URL cannot be recovered from a prefix alone. To restore an existing old hostname, the matching old `prefix + tunnel_token` is required.

## Custom public URL

A custom HTTPS public URL can be entered. It is not accepted as active until `/__tunnel_probe` proves it routes to this exact OmniGate instance.

## Floating window

The optional 44dp floating bubble:

- D = DeepSeek
- G = ChatGPT
- X = Grok
- 智 = GLM
- draggable
- tap opens OmniGate
- long-press hides it
- hiding it does not stop the API, WebView session or tunnel

## Build

Every push to `main` runs the Android release gate:

1. source-contract checks,
2. `assembleRelease`,
3. `zipalign -c -v 4`,
4. Android `apksigner verify`,
5. package/version/minSdk/targetSdk checks,
6. artifact upload,
7. temporary prerelease.

See `QA_AGENT.md` for the independent acceptance contract.
