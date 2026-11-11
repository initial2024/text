#!/usr/bin/env python3
"""OGCONTEXT01: Java consent/ownership negative tests + clipboard safety source gate.

Runs without Android devices, user APKs, real credentials or provider access.
A PASS is NOT proof of real Gecko selection menus on Android 16.
"""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

root=Path(__file__).resolve().parents[1]
src=root/"library/src/main/java/com/omnigate/integrated"

def read(n): return (src/n).read_text(encoding="utf-8")
ledger=read("LocalContextLedger.java")
ui=read("LocalContextUi.java")
merged=read("LocalProviderSummaryUi.java")
crypto=read("LocalReviewedSummaryStore.java")
copy=read("GeckoSelectionControls.java")
overlay=read("ClaudeFloatingOverlay.java")
browser=read("IsolatedGeckoBrowser.java")
profiles=read("AndroidProfileSafetyCenter.java")
nav=read("GeckoAccountNavigationGuard.java")
for expected in (
    '"context."+AccountBrowserStore.contextId',
    '".enabled",false)', '".aggregate",false)',
    'enable_account_statistics_first',
    'enable_provider_aggregation_first',
    'putBoolean("context."+id+".include_provider",false)',
    'return java.util.Collections.emptyList()',
    'if(id==null)continue',
):
    assert expected in ledger, "Opt-in missing: "+expected
assert 'LocalReviewedSummaryStore.save(' in merged
assert 'LocalReviewedSummaryStore.read(' in merged
assert 'LocalReviewedSummaryStore.erase(' in merged
assert 'preview.setTextIsSelectable(true)' not in merged
assert 'input.onTextContextMenuItem(android.R.id.paste)' in ui
assert 'field.onTextContextMenuItem(android.R.id.paste)' in merged
assert 'addressInput.onTextContextMenuItem(android.R.id.paste)' in browser
assert 'url.onTextContextMenuItem(android.R.id.paste)' in overlay
assert 'setSelectionActionDelegate' in copy and 'BasicSelectionActionDelegate' in copy
assert 'isActionAvailable' in copy and 'getMethod("execute",String.class)' in copy
assert 'getField("text")' not in copy
assert 'getPrimaryClip' not in copy
assert 'GeckoSelectionControls.overlay(' in overlay
assert 'GeckoSelectionControls.fullPage(' in browser
assert 'openSingleAuthorizedCounterpart' in profiles
assert '!isProfile' in profiles and 'bridge.isManagedProfile' in profiles
assert 'java.security.KeyStore' in crypto and 'AndroidKeyStore' in crypto
assert 'AES/GCM/NoPadding' in crypto
assert 'updateAAD(associatedData(provider,id))' in crypto
assert 'setRandomizedEncryptionRequired(true)' in crypto
assert 'MAX_CHARS=8000' in crypto
assert 'setPrimaryClip' in merged and 'ClipboardManager' in merged
for script in (crypto,merged,ui,ledger,copy,profiles):
    assert "getCookie(" not in script and "clearDataForSessionContext(" not in script
    assert "getWebResponseBody" not in script and "evaluateJavascript" not in script
assert 'getText().toString()' in merged # user-initiated review
assert 'setOnDismissListener' in merged and 'field.setText("")' in merged
assert 'providerAggregationEnabled(a,provider)' in merged

stubs = {
"android/content/SharedPreferences.java":r'''
package android.content;
public interface SharedPreferences {
  int getInt(String k,int fallback);
  long getLong(String k,long fallback);
  boolean getBoolean(String k,boolean fallback);
  String getString(String k,String fallback);
  boolean contains(String k);
  Editor edit();
  interface Editor {
    Editor putString(String k,String value);
    Editor putBoolean(String k,boolean value);
    Editor putInt(String k,int value);
    Editor putLong(String k,long value);
    Editor remove(String k);
    boolean commit();
  }
}
''',
"android/content/Context.java":r'''
package android.content;
import java.util.HashMap;
import java.util.Map;
public class Context {
  public static final int MODE_PRIVATE=0;
  private final Map<String,SharedPreferences> prefs=new HashMap<>();
  public Context getApplicationContext(){return this;}
  public SharedPreferences getSharedPreferences(String name,int flags){
    SharedPreferences found=prefs.get(name);
    if(found!=null)return found;
    found=new FakePrefs();
    prefs.put(name,found);
    return found;
  }
  private static final class FakePrefs implements SharedPreferences{
    final Map<String,Object> vals=new HashMap<>();
    public int getInt(String k,int fallback){
      Object v=vals.get(k);return v instanceof Integer?(Integer)v:fallback;}
    public long getLong(String k,long fallback){
      Object v=vals.get(k);return v instanceof Long?(Long)v:fallback;}
    public boolean getBoolean(String k,boolean fallback){
      Object v=vals.get(k);return v instanceof Boolean?(Boolean)v:fallback;}
    public String getString(String k,String fallback){
      Object v=vals.get(k);return v instanceof String?(String)v:fallback;}
    public boolean contains(String k){return vals.containsKey(k);}
    public SharedPreferences.Editor edit(){
      return new SharedPreferences.Editor(){
        final Map<String,Object> puts=new HashMap<>();
        final java.util.HashSet<String> removes=new java.util.HashSet<>();
        public SharedPreferences.Editor putString(String k,String v){puts.put(k,v);return this;}
        public SharedPreferences.Editor putBoolean(String k,boolean v){puts.put(k,v);return this;}
        public SharedPreferences.Editor putInt(String k,int v){puts.put(k,v);return this;}
        public SharedPreferences.Editor putLong(String k,long v){puts.put(k,v);return this;}
        public SharedPreferences.Editor remove(String k){removes.add(k);return this;}
        public boolean commit(){for(String k:removes)vals.remove(k);
          vals.putAll(puts);return true;}
      };
    }
  }
}
''',
"com/omnigate/integrated/OptInTest.java":r'''
package com.omnigate.integrated;
import android.content.Context;
import java.util.List;
public final class OptInTest{
  static int tests=0;
  static void check(boolean value,String why){
    tests++;if(!value)throw new AssertionError(why);}
  static void rejected(Runnable r,String why){
    boolean fail=false;try{r.run();}catch(RuntimeException expected){fail=true;}
    check(fail,why);}
  public static void main(String[] args)throws Exception{
    Context personal=new Context();
    Context work=new Context();
    String provider="claude";
    String id0=AccountBrowserStore.contextId(personal,provider,0);
    check(id0.startsWith("og-ctx-v1-"),"context ID format");
    check(!LocalContextLedger.enabled(personal,provider,0),"default OFF");
    check(!LocalContextLedger.providerAggregationEnabled(personal,provider),
      "provider aggregate default OFF");
    rejected(()->LocalContextLedger.record(personal,provider,0,"hello",true),
      "stat recording must reject before opt-in");
    rejected(()->LocalContextLedger.setParticipates(personal,provider,0,true),
      "participant must reject before opt-in");
    LocalContextLedger.setEnabled(personal,provider,0,true);
    check(LocalContextLedger.enabled(personal,provider,0),"explicit opt-in");
    rejected(()->LocalContextLedger.setParticipates(personal,provider,0,true),
      "provider must opt in separately");
    LocalContextLedger.setWindow(personal,provider,0,8192);
    long count=LocalContextLedger.record(personal,provider,0,"hello world",true);
    check(count>0,"explicit text counted");
    check(LocalContextLedger.window(personal,provider,0)==8192,"manual model window");
    check(LocalContextLedger.contributions(personal,provider).isEmpty(),
      "OFF provider no aggregation");
    LocalContextLedger.setProviderAggregationEnabled(personal,provider,true);
    check(LocalContextLedger.contributions(personal,provider).isEmpty(),
      "ON provider cannot automatically include first slot");
    LocalContextLedger.setParticipates(personal,provider,0,true);
    List<LocalContextLedger.Contribution> entries=
      LocalContextLedger.contributions(personal,provider);
    check(entries.size()==1&&entries.get(0).slot==0,"only explicit slot A");
    check(entries.get(0).estimated==count,"aggregate numeric record");
    String id1=AccountBrowserStore.contextId(personal,provider,1);
    check(!id0.equals(id1),"Gecko slots physically unique");
    check(LocalContextLedger.contributions(personal,provider).size()==1,
      "unapproved slot B excluded");
    LocalContextLedger.setEnabled(personal,provider,1,true);
    LocalContextLedger.record(personal,provider,1,"private B summary",true);
    check(LocalContextLedger.contributions(personal,provider).size()==1,
      "enabled alone is not participation");
    LocalContextLedger.setParticipates(personal,provider,1,true);
    check(LocalContextLedger.contributions(personal,provider).size()==2,
      "explicit B joining");
    check(LocalContextLedger.combinedEstimate(personal,provider)>count,
      "numeric provider summary only");
    check(!LocalContextLedger.enabled(work,provider,0),
      "Android different user must have separate local preferences");
    check(!LocalContextLedger.providerAggregationEnabled(work,provider),
      "work profile never auto-inherits personal consents");
    LocalContextLedger.setProviderAggregationEnabled(personal,provider,false);
    check(LocalContextLedger.contributions(personal,provider).isEmpty(),
      "revoke provider disables report");
    LocalContextLedger.setProviderAggregationEnabled(personal,provider,true);
    check(LocalContextLedger.contributions(personal,provider).isEmpty(),
      "reenable cannot restore stale per-account consent");
    LocalContextLedger.setParticipates(personal,provider,0,true);
    LocalContextLedger.setEnabled(personal,provider,0,false);
    check(!LocalContextLedger.participates(personal,provider,0),
      "turning stats off revokes participation");
    check(LocalContextLedger.used(personal,provider,0)==count,
      "disabling preserves count until user explicitly clears");
    rejected(()->LocalContextLedger.record(personal,provider,0,"no",false),
      "stats remain inactive");
    rejected(()->LocalContextLedger.setWindow(personal,provider,0,3000),
      "unrealistic custom window rejected");
    check(LocalContextEstimator.estimate("hello world")>0,
      "offline estimate exists");
    check(LocalContextEstimator.warningPercent(3000,4096)>=85,
      "local warning boundary");
    check(LocalContextLedger.handoffPrompt().contains("不得") ||
      LocalContextLedger.handoffPrompt().contains("不要"),"redacted handoff");
    check(GeckoAccountNavigationGuard.denyUri("https://example.org",1,true)==false,
      "normal HTTPS allowed in strict mode");
    check(GeckoAccountNavigationGuard.denyUri("intent://open",1,true),
      "external-app protocol denied");
    check(GeckoAccountNavigationGuard.denyUri("https://example.org",2,true),
      "new window target denied");
    check(!GeckoAccountNavigationGuard.denyUri("intent://open",2,false),
      "compatibility mode remains opt-out");
    System.out.println("OGCONTEXT01_ASSERTIONS="+tests);
    System.out.println("OGCONTEXT01_PROVIDER_DOUBLE_OPT_IN_AND_REVOCATION=PASS");
    System.out.println("OGCONTEXT01_ANDROID_USER_PREFERENCES_SEPARATE=PASS_SIMULATED");
    System.out.println("OGCONTEXT01_ENCRYPTION_AND_CLIPBOARD=PASS_SOURCE_ONLY");
    System.out.println("OGCONTEXT01_GEKOVIEW_ANDROID16_COPY_PASTE=NOT_TESTED");
  }
}
'''
}
with TemporaryDirectory(prefix="ogcontext01-") as temp:
    rootp=Path(temp)
    java=[]
    for name,code in stubs.items():
        f=rootp/name;f.parent.mkdir(parents=True,exist_ok=True)
        f.write_text(code,encoding="utf-8");java.append(str(f))
    target=rootp/"classes";target.mkdir()
    java += [str(src/n) for n in (
        "AccountBrowserSlots.java","AccountBrowserStore.java",
        "LocalContextEstimator.java","LocalContextLedger.java",
        "GeckoAccountNavigationGuard.java")]
    subprocess.run(["javac","-encoding","UTF-8","-d",str(target),*java],check=True)
    subprocess.run(["java","-cp",str(target),
        "com.omnigate.integrated.OptInTest"],check=True)
