#!/usr/bin/env python3
"""Source invariants; do not claim actual browser callback or Android Keystore E2E."""
from pathlib import Path
p=Path(__file__).resolve().parents[1]/"library/src/main/java/com/omnigate/integrated"
v=(p/"LocalPasswordCenter.java").read_text()
b=(p/"IsolatedGeckoBrowser.java").read_text()
a=(p/"CompatSettingsActivity.java").read_text()
t=(p/"GeckoBrowserTransfers.java").read_text()
o=(p/"ClaudeFloatingOverlay.java").read_text()
h=(p/"GeckoVaultFormHint.java").read_text()
n=(p/"GeckoNewPasswordFill.java").read_text()
save=v.split("static void offerNativeSave",1)[1].split("private static PendingFill",1)[0]
assert "verified.equals(site)" in save and "site.equals(origin(action))" in save
assert 'getField("httpRealm").get(entry)!=null' in save
assert 'Array.getLength(options)!=1' in save
assert "pendingSave=null;" in save and "offer.token.equals(token)" in save
assert save.index("auth(a,offer.provider,offer.slot,offer.id") < save.index('getField("password").get(offer.entry)')
assert 'offer.live()' in save and 'same(a,offer.provider,offer.slot,offer.id)' in save
assert "offer.clear();a.finish();" in save
assert '"vault_save_token",offer.token' in save
assert '"vault_fill_token",request.token' in v
assert 'request.target.origin.equals(row.getString("origin"))' in v
assert 'request.live()' in v and 'r.id.equals(existing(c,r.provider,r.slot))' in v
assert 'r.foreground.getAsBoolean()' in v and 'GeckoNewPasswordFill.sameTarget(r.target,now)' in v
assert v.index('r.foreground.getAsBoolean()')<v.index('GeckoNewPasswordFill.fill(now,value[0])')
assert "value[0]=null" in v and "attempt<9" in v
assert 'passwordPageEpoch==epoch' in b and 'guestWindow?null:prompt->' in b
assert 'localSaveOffer.accept(args[1])' in t and 'immediateDismiss(args[1])' in t
assert '"onLoginSelect".equals(callback)' in t
assert 'getMethod("setAutofillEnabled",boolean.class).invoke(view,false)' in h
assert b.count('GeckoVaultFormHint.disableSystemAutofill(')==2
assert o.count('GeckoVaultFormHint.disableSystemAutofill(')==2
for x in (v,t,n,h):
    assert 'setAutocompleteStorageDelegate(' not in x
for x in (n,h):
    assert 'getValue(' not in x and 'evaluateJavascript(' not in x
assert 'consumeNativeSave(this,saveToken)' in a and 'consumeNativeFill(this,fillToken)' in a
print("OGVAULT15_LOCAL_CONSENT_AND_SESSION_ORIGIN_SOURCE=PASS")
print("OGVAULT15_NO_SHARED_STORAGE_CONFIRMATION_SOURCE=PASS")
print("OGVAULT15_NATIVE_CALLBACK_AND_KEYSTORE_E2E=NOT_TESTED")
