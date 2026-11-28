#!/usr/bin/env python3
"""Source gates: private-browsing opt-in and Gecko page progress.
No claim of real extension permission, private-window JS, or Android UI E2E."""
from pathlib import Path
src=Path(__file__).resolve().parents[1]/"library/src/main/java/com/omnigate/integrated"
addon=(src/"GeckoTimezoneAddonUi.java").read_text()
page=(src/"IsolatedGeckoBrowser.java").read_text()
overlay=(src/"ClaudeFloatingOverlay.java").read_text()
bar=(src/"GeckoLoadIndicator.java").read_text()
assert 'newInstance(allow,Boolean.FALSE,Boolean.FALSE)' in addon
assert '"allowedInPrivateBrowsing"' in addon
assert '"setAllowedInPrivateBrowsing",wc,boolean.class' in addon
assert '.invoke(ct,addon,allow)' in addon
assert 'Boolean.valueOf(allow).equals(observed)' in addon
assert 'confirmPrivatePermission(a,addon,!allowed)' in addon
toggle=addon.split("private static void confirmPrivatePermission",1)[1].split("private static void confirmUninstall",1)[0]
assert '.setNegativeButton("取消",null)' in toggle
assert '.setPositiveButton(allow?"允许":"撤销"' in toggle
assert 'ADDON_ID.equals' in addon.split("private static Boolean privatePermission",1)[1].split("private static void confirmPrivatePermission",1)[0]
assert 'usePrivateMode",boolean.class).invoke(builder,true)' in page
assert 'guestWindow?AccountBrowserSlots.newContextId()' in page
assert 'clearDataForSessionContext",String.class' in page
for code in (page,overlay):
    assert '"onPageStart"' in code and '"onProgressChange"' in code and '"onPageStop"' in code
    assert 'pageProgress.start(' in code and 'pageProgress.progress(' in code and 'pageProgress.stop(' in code
    assert 'pageProgress.request(session)' in code
    assert 'pageProgress.clear()' in code
assert '"claude".equals(provider)&&args[1] instanceof Integer' not in page
assert 'revision==requestRevision' in bar
assert 'owner!=session||!active' in bar
assert 'bar.setProgress(percent)' in bar
assert 'bar.setVisibility(View.GONE)' in bar
for s in ('evaluateJavascript(', 'TimeZone.setDefault(', 'getCookie(', 'getCookies('):
    assert s not in bar and s not in toggle
print('OGTZ10_PRIVATE_PERMISSION_EXPLICIT_CONSENT_SOURCE=PASS')
print('OGTZ10_GUEST_PRIVATE_CONTEXT_ISOLATION_SOURCE=PASS')
print('OGLOAD10_NATIVE_PROGRESS_AND_SESSION_BOUNDARY_SOURCE=PASS')
print('OGTZ10_VIVO_EXTENSION_PRIVATE_PERMISSION_E2E=NOT_TESTED')
