#!/usr/bin/env python3
"""Static contract only; real Android Keystore and device-lock checks required."""
from pathlib import Path
p=Path(__file__).resolve().parents[1]/"library/src/main/java/com/omnigate/integrated"
v=(p/"LocalPasswordCenter.java").read_text()
a=(p/"CompatSettingsActivity.java").read_text()
c=(p/"AccountStorageCleaner.java").read_text()
m=(p/"AccountManagerUi.java").read_text()
t=(p/"GeckoBrowserTransfers.java").read_text()
for x in ('getNoBackupFilesDir()', 'new AtomicFile(', 'AndroidKeyStore', 'setKeySize(256)', 'AES/GCM/NoPadding', 'setRandomizedEncryptionRequired(true)', 'setUserAuthenticationRequired(true)', 'AUTH_DEVICE_CREDENTIAL', 'cipher.updateAAD(aad(id))', 'alias(id)', 'FLAG_SECURE', 'IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS', 'setFilterTouchesWhenObscured(true)', 'if(!same(a,p,slot,id))return;', 'https_origin_required', 'recordId.equals'):
    assert x in v,x
for x in ('evaluateJavascript(', 'getPassword(', 'setAutocompleteStorageDelegate(', 'ClipboardManager', 'HttpURLConnection', 'Log.d(', 'Log.i(', 'Log.w(', 'Log.e('):
    assert x not in v,x
assert 'createConfirmDeviceCredentialIntent' in a
assert 'if(passwordMode&&resultCode==RESULT_OK&&action!=null' in a
assert 'pendingVaultAuthentication=null;' in a
assert 'LocalPasswordCenter.erase(c,oldId)' in c
assert c.index('LocalPasswordCenter.erase(c,oldId)')<c.index('AccountBrowserStore.rotateContext(c,provider,slot,oldId)')
assert '此空间的独立密码库' in m
assert '"onLoginSave".equals(callback)' in t
assert '"onLoginSelect".equals(callback)' in t
print("OGUI11_VAULT_SCOPE_AND_AUTH_STATIC=PASS")
print("OGUI11_SHARED_LOGIN_STORAGE_CONFIRMATION_BLOCKED=PASS")
print("OGUI11_KEYSTORE_DEVICE_E2E=NOT_TESTED")
print("OGUI11_NATIVE_SAVE_REQUIRES_EXPLICIT_CONSENT=YES")
