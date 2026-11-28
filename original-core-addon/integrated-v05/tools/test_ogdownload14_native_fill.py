#!/usr/bin/env python3
from pathlib import Path
p=Path(__file__).resolve().parents[1]/"library/src/main/java/com/omnigate/integrated"
d=(p/"GeckoBrowserDownloads.java").read_text()
n=(p/"GeckoNewPasswordFill.java").read_text()
b=(p/"IsolatedGeckoBrowser.java").read_text()
t=(p/"GeckoBrowserTransfers.java").read_text()
assert "GeckoDownloadResponsePolicy.reject(status,source,body!=null)" in d
assert d.index("if(args[0]!=session||!owner.getAsBoolean()")<d.index("GeckoDownloadResponsePolicy.reject")
assert "GeckoDownloadApprovalActivity.class" in d
assert "getNoBackupFilesDir()" in d
for required in ("getAutofillSession","getFocused","getDomain","getHint","getAttribute","new-password",
    "isVisible","now.node!=original.node","now.id!=original.id","now.structure!=original.structure",
    "origin.equals(LocalPasswordCenter.origin(domain))","owner.getAsBoolean()",
    'getMethod("autofill",SparseArray.class)'):
    assert required in n, required
for forbidden in ('getValue','evaluateJavascript','ClipboardManager','onLoginFetch','getCookies','getPassword'):
    assert forbidden not in n, forbidden
assert "passwordPageEpoch==epoch" in b and "id.equals(AccountBrowserStore.contextId(host,provider,slot))" in b
assert '"onLoginSave".equals(callback)' in t and '"onLoginSelect".equals(callback)' in t
print("OGDOWNLOAD14_OWNER_AND_APPROVAL_SOURCE=PASS")
print("OGVAULT14_GENERATED_ONLY_NATIVE_FORM_GATE_SOURCE=PASS")
print("OGVAULT14_EXISTING_PASSWORD_SAVE_AND_FILL=NOT_IMPLEMENTED")
print("OGVAULT14_ANDROID_REAL_FORM_AND_BLOB=NOT_TESTED")
