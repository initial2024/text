#!/usr/bin/env python3
"""OGTZ03 source contract: user-consented signed Gecko add-on only.
NOT proof of Gecko extension installation or website Date/Intl/Worker parity.
"""
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=root/"library/src/main/java/com/omnigate/integrated"
addon=(p/"GeckoTimezoneAddonUi.java").read_text()
quick=(p/"AccountQuickSettings.java").read_text()
failclosed=(p/"GeckoSharedWebTimeZone.java").read_text()

assert '"timezoneguard@3radiance.dev"' in addon
assert '"https://addons.mozilla.org/firefox/downloads/file/5224737/timezone_guard-1.7.xpi"' in addon
assert '"https://addons.mozilla.org/en-US/firefox/addon/timezone-guard/"' in addon
assert "new AlertDialog.Builder(a)" in addon
assert "setPositiveButton(\"授予并安装\"" in addon
assert "setNegativeButton(\"拒绝\"" in addon
assert "promptResponse(false)" in addon
assert "newInstance(allow,Boolean.FALSE,Boolean.FALSE)" in addon
assert "if(prior!=null)throw new IllegalStateException" in addon
assert 'if(current==delegate)' in addon
assert '"getPromptDelegate"' in addon and '"setPromptDelegate"' in addon
assert '"getWebExtensionController"' in addon
assert '"getInstance"' in addon
assert '"install",String.class' in addon and '"uninstall",wc' in addon
assert '"list"' in addon
assert '"moz-extension://"' in addon
assert 'AccountBrowserSlots.newContextId()' in addon
assert 'if(which==14)GeckoTimezoneAddonUi.show(a);' in quick
assert 'if(which==13)GeckoSharedWebsiteTimeZoneUi.show(a);' in quick
assert '"网页时区 · 真机未生效（已停用实验）"' in quick
assert 'Os.setenv' not in failclosed and 'Os.unsetenv' not in failclosed
for forbidden in ('Runtime.getRuntime().exec(', 'System.loadLibrary(', 'GeckoRuntime.create(',
                  'remoteDebuggingEnabled(', 'TimeZone.setDefault(', 'clearData(',
                  'deleteDatabase(', 'getCookie(', 'getCookies('):
    assert forbidden not in addon, forbidden
assert 'getMethod("loadUri",String.class).invoke(session,' in addon
assert 'base+"frontend/popup.html"' in addon
# OGTZ04 replaces the too-small alert panel with a full-screen settings dialog.
assert 'new android.app.Dialog(a,' in addon
assert 'Theme_DeviceDefault_NoActionBar_Fullscreen' in addon
assert 'GeckoAddonChoicePrompts.install(a,session,choiceDialog)' in addon
assert 'GeckoAddonChoicePrompts.release(choiceDialog)' in addon
assert 'base+"frontend/popup.html"' in addon
assert 'private static void showActions' in addon
menu=addon.split('private static void showActions',1)[1].split('private static void message',1)[0]
assert '.setItems(' in menu and '.setMessage(' not in menu
print("OGTZ03_SIGNED_ADDON_OPTIN_SOURCE_CONTRACT=PASS")
print("OGTZ03_REAL_GECKO_EXTENSION_INSTALL=NOT_TESTED")
print("OGTZ03_BROWSER_DATE_INTL_WORKER_PARITY=NOT_TESTED")
