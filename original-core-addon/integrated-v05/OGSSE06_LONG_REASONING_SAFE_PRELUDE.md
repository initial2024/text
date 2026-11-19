# OGSSE06: long reasoning compatibility without releasing fake native [DONE]

### Root cause and narrow fix
OGSSE05's 64KiB *total precontent* buffer protects from fake tool terminal and false [DONE], but would fail a normal long-thinking response producing many small nonterminal `reasoning_content` SSE events. OGSSE06 keeps the same strict maximum for **unverified retained bytes** and, only when approaching overflow, sends fully framed, UTF-8-valid, JSON-verified **nonterminal role/reasoning/comment** events unmodified to the existing downstream connection. It never passes an unverified [DONE], finish_reason, unknown/error envelope, tool result or content delta before `NativeSseContentProbe` has recognized real content/tool data. A single unsplittable >64KiB event remains fail-closed with one SSE error; original provider is drained to EOF, never retried/cancelled by this patch.

### Verified and unverified
Pure JVM fixture verifies >64KiB of individually safe reasoning frames is released while terminal or truncated data is withheld. Static gates bind the classifier to the existing actual Android SidecarProxyServer method. Compile/signing preflight at exact HEAD remains necessary. Offline tests are NOT real phone SSE, DSH UI, relay transport, or signed APK evidence.

### Authorizations and invariants
No code copied from GPL-3.0 xingling66666/fuckds. No credential/token/PoW/account work. Original port 8787/native 1604, 409 owner check, fixed signer, user login state and existing APK stay untouched. Do not publish unless a controlled real phone long-thinking and genuine tool event test passes.