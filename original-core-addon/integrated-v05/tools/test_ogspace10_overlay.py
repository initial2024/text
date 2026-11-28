#!/usr/bin/env python3
"""Source guards, not a physical-device or website-fingerprint test."""
from pathlib import Path
p=Path(__file__).resolve().parents[1]/"library/src/main/java/com/omnigate/integrated"
overlay=(p/"ClaudeFloatingOverlay.java").read_text()
browser=(p/"IsolatedGeckoBrowser.java").read_text()
store=(p/"AccountBrowserStore.java").read_text()
audit=(p/"GeckoAccountIsolationAudit.java").read_text()
assert "BUBBLE_SIZE_DP=44" in overlay
assert "screen*.94f" in overlay and "dp(app,440)" in overlay
assert "tallWindow?.70f:.51f" in overlay
assert "Restyle only the collapsed bubble" in overlay
assert "root.setBackgroundColor(android.graphics.Color.TRANSPARENT)" in overlay
assert "fold.setText(value?\"◈\":\"—\")" in overlay
assert 'AccountBrowserStore.contextId(app,selectedProvider,slot)' in overlay
assert 'AccountBrowserStore.contextId(host,provider,index)' in browser
assert 'return value;' in store and 'assertUniqueOwner(p,provider,index,value)' in store
assert 'guestWindow?AccountBrowserSlots.newContextId()' in browser
assert 'GeckoLiveWindowHandoff.move(sourceView,nextView,existing,id)' in overlay
geometry=overlay.split("private static void applyWindowGeometry()",1)[1].split("private static void updateFoldMemoryState",1)[0]
for forbidden in ("loadUri", "reload(", "releaseSession", "newContextId", "evaluateJavascript"):
    assert forbidden not in geometry, forbidden
assert 'localCode(focusId)' in audit
assert 'MessageDigest.getInstance("SHA-256")' in audit
assert '不' in audit
assert '本地账号空间识别码' in audit
assert 'p.edit(' not in audit and 'AccountBrowserStore.contextId(' not in audit
print("OGSPACE10_LOCAL_CONTEXT_READ_ONLY=PASS")
print("OGUI11_FOLDED_ONLY_LAYOUT_SOURCE=PASS")
print("OGSPACE10_GEOMETRY_NO_RELOAD_SOURCE=PASS")
print("OGSPACE10_WEBSITE_BROWSER_DEVICE_ID=NOT_MEASURED")
print("OGSPACE10_ANDROID_UI_E2E=NOT_TESTED")
