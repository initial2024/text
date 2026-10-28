# DSH Web Gateway 2.6.0

A clean Android rebuild of the DSH → DeepSeek Web gateway.

The old 169 MB APK was directly repacked during earlier attempts and failed Android 16 / vivo installation checks. This project instead builds from source with the Android Gradle Plugin, then uses official Android `zipalign` and `apksigner`.

## Local endpoints

The gateway intentionally listens only on loopback:

- Original-compatible: `http://127.0.0.1:8787/v1`
- DSH compatibility alias: `http://127.0.0.1:1601/v1`
- Model: `deepseek-chat`

Both point to the same logged-in DeepSeek WebView relay.

The Gateway API key shown in the app is used as:

`Authorization: Bearer <Gateway API key>`

## Original deepseeks.dpdns.org reverse tunnel

This build restores the protocol found in the original APK. It is **not** Tailscale, Funnel, Cloudflare Tunnel, or a generic LAN listener.

Protocol constants recovered from the original APK:

- TLS server: `57.183.5.186:9000`
- Certificate SHA-256 pin:
  `AA:14:DC:75:AE:54:3E:19:3C:25:6B:46:F1:1E:A6:B9:70:D3:A7:D6:79:C4:3E:74:74:79:67:E1:41:DE:D4:71`
- Registration:
  `REGISTER <tunnel_token> <prefix>\n`
- Incoming framing: `REQ <id> <method> <path>`
- Response framing: `RESP / DATA / END`
- Probe route: `/__tunnel_probe`
- Probe response:
  `{"probe":"392125113473d61e8aaba950e010d41453ceb63597a7b047"}`
- Generated public URL:
  `https://<prefix>.deepseeks.dpdns.org`

The tunnel forwards requests back to the original-compatible local port `127.0.0.1:8787`.

## Existing public URL

The user's previous public URL is:

`https://gw-33e850180248.deepseeks.dpdns.org`

Its prefix is therefore:

`gw-33e850180248`

However, the original APK generated and stored a separate random 64-character `tunnel_token` in its private SharedPreferences. A newly installed app under package `com.dsh.webgateway` cannot read private data belonging to the old package.

Therefore the app includes a **Tunnel identity** editor:

1. Set prefix to `gw-33e850180248`.
2. Import the matching old `tunnel_token` if available.
3. Enable the public tunnel.
4. Require tunnel state = connected.
5. Verify the public `/__tunnel_probe` endpoint.

The prefix alone is not proof that the old URL has been reclaimed. Without the matching token or a successful external probe, legacy takeover remains unproven.

If a new identity is used instead, the app generates the same format as the original:

- prefix = `gw-` + first 12 lower-case UUID hex characters;
- token = two UUIDs with hyphens removed.

## DeepSeek relay additions

Compared with the old gateway, this rebuild also contains:

- hard generation timeout: 600 seconds;
- 5-second generation-end grace to reduce false truncation during DOM transitions;
- 32 MiB HTTP request-body cap;
- real WebView file/image attachment injection;
- attachment upload fail-closed behavior;
- one automatic new-conversation rollover per DSH submission when DeepSeek reports a conversation/context limit;
- full current DSH prompt replay on rollover.

## Build gate

Every build must pass:

- source contract assertions for the recovered tunnel protocol;
- Gradle release compilation;
- official `zipalign`;
- official `apksigner` verification;
- APK Signature Scheme v2;
- APK Signature Scheme v3;
- package, minSdk and targetSdk checks;
- SHA-256 recording.

The independent acceptance specification is in `QA_AGENT.md`.

## Signing limitation

GitHub Actions currently creates a fresh temporary signing key for each CI build. This is safe for a one-off side-by-side test APK because the key is not committed, but **two separately built CI APKs cannot be installed as in-place updates over each other**.

Do not treat this temporary CI signing setup as a long-term release-signing strategy. If the app is retained after acceptance, create a private persistent release key and store it outside this public repository / in GitHub Actions secrets.

## Repository lifecycle

This repository is temporary. It can be deleted after the APK and any required source/evidence have been retained.
