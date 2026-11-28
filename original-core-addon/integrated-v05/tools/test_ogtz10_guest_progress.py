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

assert 'Button extensionEntry=button("扩展权限")' in page
assert page.count('GeckoTimezoneAddonUi.showGuestPermissions(host)') == 2
assert 'if(guestWindow)menu.getMenu().add(0,9,8' in page
guest_entry=addon.split("static void showGuestPermissions",1)[1].split("private static void listAndManage",1)[0]
assert 'listAndManage(a)' in guest_entry
assert 'AccountBrowserStore' not in guest_entry
assert 'setAllowedInPrivateBrowsing' not in guest_entry
assert 'panel.setVisibility(View.VISIBLE)' in bar
assert 'panel.setVisibility(View.GONE)' in bar
assert 'label.setText("网页加载 "+percent+"%")' in bar
assert 'dp(context,7)' in bar
assert 'panel.setClickable(false)' in bar and 'panel.setFocusable(false)' in bar
print('OGUI11_GUEST_ENTRY_AND_VISIBLE_PROGRESS_SOURCE=PASS')
print('OGUI11_REAL_DEVICE_UI=NOT_TESTED')

# Browser-only startup is fail-closed, including upgrades with old true flags.
gateway=(src/"GatewayApp.java").read_text()
local=(src/"LocalProxyRuntime.java").read_text()
relay=(src/"RelayRuntime.java").read_text()
service=(src/"SidecarService.java").read_text()
home=(src/"OmniGateHomeActivity.java").read_text()
assert 'getBoolean("auto_start_local_enabled",false)' in gateway
assert 'putBoolean("browser_default_v1",true)' in gateway
migration=gateway.split("private static synchronized void migrateBrowserDefault()",1)[1].split("public static boolean autoStartEnabled",1)[0]
for key in ("auto_start_local_enabled","relay_enabled","background_keepalive_enabled"):
    assert 'putBoolean("'+key+'",false)' in migration
assert 'if(!GatewayApp.autoStartEnabled())' in local
assert 'if(!GatewayApp.autoStartEnabled() || !GatewayApp.tunnelEnabled())' in relay
assert 'return START_NOT_STICKY;' in service and 'return START_STICKY;' not in service
assert 'advancedBody.setVisibility(View.GONE)' in home
assert home.index('IsolatedGeckoBrowser.showPicker(this)') < home.index('LinearLayout advanced=card(page)')
print('OGBROWSER12_DEFAULT_OFF_AND_COMPACT_HOME_SOURCE=PASS')
print('OGBROWSER12_REAL_DEVICE_PORT_AND_UI=NOT_TESTED')

settings=(src/"CompatSettingsActivity.java").read_text()
bridge=(src/"OriginalCoreBridge.java").read_text()
assert 'gatewaySection.setVisibility(View.GONE)' in settings
assert 'panel(gatewaySection, "本地 API 服务"' in settings
assert 'GeckoTimezoneAddonUi.show(this)' in home
assert 'if(!GatewayApp.autoStartEnabled())' in bridge.split('private static void startNativeOnResumedOwner',1)[1]
assert 'if(!GatewayApp.autoStartEnabled())' in bridge.split('private static void completeMainOwnerHandoff',1)[1]
print('OGBROWSER12_COMPACT_SETTINGS_AND_DEFERRED_START_GATE_SOURCE=PASS')

splash=(src/"OmniGateSplashActivity.java").read_text()
assert 'txt("OmniPaws",27,INK,true)' in home
assert 'loadIcon(getContext().getPackageManager())' in home
assert 'loadIcon(getContext().getPackageManager())' in splash
assert 'pair(browser,"账号管理"' in home
assert 'pair(browser,"浏览器扩展"' in home
assert 'modeStatus.setText' in home
print('OGPAWS13_COMPACT_BRAND_SOURCE=PASS')
print('OGPAWS13_LAUNCHER_BINARY_RESOURCE_AND_DEVICE=NOT_TESTED_BY_SOURCE_CI')
