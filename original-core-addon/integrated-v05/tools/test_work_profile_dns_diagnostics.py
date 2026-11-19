#!/usr/bin/env python3
"""OGDNS01: source-level guardrails. Does not claim real work-profile DNS access."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]/"library/src/main/java/com/omnigate/integrated"
diag=(root/"GeckoWorkProfileDnsDiagnostics.java").read_text()
err=(root/"GeckoPageFailureUi.java").read_text()
full=(root/"IsolatedGeckoBrowser.java").read_text()
overlay=(root/"ClaudeFloatingOverlay.java").read_text()

for label,host in (
    ("DeepSeek","chat.deepseek.com"),("GLM","chatglm.cn"),
    ("ChatGPT","chatgpt.com"),("Grok","grok.com"),("Claude","claude.ai")
):
    assert '"'+label+'","'+host+'"' in diag, (label,host)
assert 'category==5&&code==37' in diag and 'category==5&&code==37' in err
assert "DNS_UNKNOWN_HOST" in err and "DNS_UNKNOWN_HOST" in diag
assert "InetAddress.getAllByName(" in diag
assert "host.getSystemService(UserManager.class)" in diag
assert "manager.isManagedProfile()" in diag
assert "if(!BUSY.compareAndSet(false,true))" in diag
assert "worker.setDaemon(true)" in diag and 'worker.start();' in diag
assert 'Settings.ACTION_VPN_SETTINGS' in diag
assert 'GeckoWorkProfileDnsDiagnostics.show(host)' in full
assert 'GeckoWorkProfileDnsDiagnostics.show(host)' in overlay
assert "GECKO_NETWORK_ERROR(category=" in err
assert "getClass().getField(\"category\")" in err
assert "getClass().getField(\"code\")" in err
for code in (diag,err):
    for banned in (
        "setTrustedRecursiveResolverMode(", "setTrustedRecursiveResolverUri(",
        "bindProcessToNetwork(", "setAlwaysOnVpnPackage(", "VpnService.prepare(",
        "getCookies(", "getCookie(", "clearDataForSessionContext(",
        "AccountBrowserStore.rotate(", "disconnectVpn(", "setHttpProxy(",
        "HttpURLConnection(", "URLConnection(", "ProcessBuilder(", "Runtime.getRuntime().exec(",
        "getAllByName(\""
    ):
        assert banned not in code, "Forbidden resolver/VPN override or account action: "+banned
assert 'data:text/html;charset=utf-8,' in err
print("OGDNS01_GECKO_5_37_DNS_UNKNOWN_HOST=PASS")
print("OGDNS01_SYSTEM_RESOLVER_CURRENT_PROFILE_USER_INITIATED=PASS_SOURCE_ONLY")
print("OGDNS01_FIVE_FIXED_DOMAINS_AND_NO_SECRET_LOGGING=PASS_SOURCE_ONLY")
print("OGDNS01_NO_VPN_DNS_OVERRIDE_OR_COOKIE_RESET=PASS_SOURCE_ONLY")
print("OGDNS01_ANDROID16_WORK_PROFILE_REAL_DNS_E2E=NOT_TESTED")
