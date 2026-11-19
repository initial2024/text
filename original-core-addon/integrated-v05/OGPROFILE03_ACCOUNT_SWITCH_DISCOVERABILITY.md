# OGPROFILE03 — make isolated account switch discoverable

**Why the user could not see the switcher**: In v1.0.3, only the long-scrolling `CompatSettingsActivity` contained the entry, underneath numerous local 8787 and original API controls, and the branded `OmniGateHomeActivity` had no visible account button.

Add two highly discoverable, direct navigation entries:
1. OmniGate launcher home first `网关控制台` card: `账号切换 / 多账号备份` above all Relay options.
2. Settings immediately beneath the hero banner: `账号切换 / 多账号备份（独立浏览器）`, above `本地 API 服务`. The old lower button is retained as compatibility entry and uses the same picker.

Both open exactly the same `IsolatedGeckoBrowser.showPicker` then select DeepSeek / GLM / ChatGPT / Grok, preserving existing isolated `contextId`, manual self-budget controls and zero automatic model POST. Important: this is an independent auxiliary browser account; it does not replace the frozen native 8787/Minis/DSH model-execution account. It does not initiate any automatic rotation due to 401/403/429/challenge.

Code tests prove button placement and shared picker, while Android build/signature verification and real phone UI navigation remain independent. Never call unsigned DEX an APK.
