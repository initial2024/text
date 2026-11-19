#!/usr/bin/env python3
"""OGTZ01 pure-Java host harness with a fake Android Os environment.

This is deliberately not a real GeckoDate/Intl test. A green result proves
pre-bootstrap preference isolation and host state transitions only.
"""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

base=Path(__file__).resolve().parents[1]
pkg=base/"library/src/main/java/com/omnigate/integrated"
boot=(pkg/"CompatBootstrapProvider.java").read_text()
quick=(pkg/"AccountQuickSettings.java").read_text()
ui=(pkg/"GeckoSharedWebsiteTimeZoneUi.java").read_text()
clock=(pkg/"AccountTimeZoneUi.java").read_text()
begin=boot.index("GatewayApp.initialize(app)")
assert begin < boot.index("GeckoSharedWebTimeZone.applyAtAppBootstrap(app)") < boot.index("AutoStartRuntime.onProcessCreated()")
assert 'GeckoSharedWebsiteTimeZoneUi.show(a)' in quick
assert '"网页时区 · 真机未生效（已停用实验）"' in quick
assert 'android.app.DatePickerDialog' in clock
assert 'System.currentTimeMillis()' in clock
assert '不修改 Date.now()' in ui
assert 'BrowserLeaks' in ui
assert '强行停止' in ui
assert 'GeckoRuntime.getInstance()' not in (pkg/"GeckoSharedWebTimeZone.java").read_text()
assert 'GeckoRuntime.create(' not in (pkg/"GeckoSharedWebTimeZone.java").read_text()
assert 'TimeZone.setDefault' not in (pkg/"GeckoSharedWebTimeZone.java").read_text()
assert 'Os.setenv' not in (pkg/"GeckoSharedWebTimeZone.java").read_text()
assert 'Os.unsetenv' not in (pkg/"GeckoSharedWebTimeZone.java").read_text()

files={
 "android/content/SharedPreferences.java":r'''
package android.content;
public interface SharedPreferences {
 String getString(String key,String defaultValue);
 Editor edit();
 interface Editor {Editor putString(String key,String v);boolean commit();}
}
''',
 "android/content/Context.java":r'''
package android.content;
import java.util.HashMap;
import java.util.Map;
public final class Context {
 public static final int MODE_PRIVATE=0;
 private final Map<String,SharedPreferences> store=new HashMap<>();
 public Context getApplicationContext(){return this;}
 public SharedPreferences getSharedPreferences(String name,int mode){
  SharedPreferences existing=store.get(name);
  if(existing!=null)return existing;
  Fake created=new Fake();store.put(name,created);return created;
 }
 static final class Fake implements SharedPreferences{
  final Map<String,String> prefs=new HashMap<>();
  public String getString(String k,String def){return prefs.getOrDefault(k,def);}
  public Editor edit(){return new Editor(){
   final Map<String,String> staged=new HashMap<>();
   public Editor putString(String k,String v){staged.put(k,v);return this;}
   public boolean commit(){prefs.putAll(staged);return true;}
  };}
 }
}
''',
 "android/system/ErrnoException.java":r'''
package android.system;
public final class ErrnoException extends Exception{
 public ErrnoException(String message){super(message);}
}
''',
 "android/system/Os.java":r'''
package android.system;
public final class Os{
 private static String tz=null;
 public static int writes=0;
 public static boolean fail=false;
 public static String getenv(String name){return tz;}
 public static void setenv(String name,String value,boolean overwrite)
   throws ErrnoException {
  writes++;
  if(fail)throw new ErrnoException("rejected");
  tz=value;
 }
 public static void unsetenv(String name)throws ErrnoException {
  writes++;
  if(fail)throw new ErrnoException("rejected");
  tz=null;
 }
}
''',
 "com/omnigate/integrated/SharedTZTest.java":r'''
package com.omnigate.integrated;
import android.content.Context;
import android.system.Os;
public final class SharedTZTest {
 static int passed=0;
 static void check(boolean condition,String message){
  if(!condition)throw new AssertionError(message);
  passed++;
 }
 static void reject(Runnable action){
  boolean thrown=false;
  try{action.run();}catch(IllegalArgumentException expected){thrown=true;}
  check(thrown,"invalid IANA must reject");
 }
 public static void main(String[] args){
  Context personal=new Context(), work=new Context();
  check("SYSTEM".equals(GeckoSharedWebTimeZone.requested(personal)),
    "default must be off");
  GeckoSharedWebTimeZone.applyAtAppBootstrap(personal);
  check("NATIVE_GECKO_DEFAULT_NO_OVERRIDE".equals(
    GeckoSharedWebTimeZone.bootStatus()),"system default");
  check(Os.writes==0,"default must not set environment");
  GeckoSharedWebTimeZone.setRequested(personal,"America/New_York");
  check(Os.writes==0,"save never mutates live environment");
  check("SYSTEM".equals(GeckoSharedWebTimeZone.requested(work)),
    "Android work profile must not inherit personal timezone");
  check("America/New_York".equals(GeckoSharedWebTimeZone.requested(personal)),
    "explicit IANA preference persisted");
  reject(()->GeckoSharedWebTimeZone.setRequested(personal,"my.fake.zone"));
  check("America/New_York".equals(GeckoSharedWebTimeZone.requested(personal)),
    "invalid zone must not overwrite saved data");
  GeckoSharedWebTimeZone.applyAtAppBootstrap(personal);
  check("STALE_EXPERIMENT_IGNORED_REAL_DEVICE_DATE_INTL_FAILED".equals(
    GeckoSharedWebTimeZone.bootStatus()),"stale preference is disabled");
  check(Os.writes==0,"custom preference must NEVER set TZ environment");
  check("NO_OVERRIDE".equals(GeckoSharedWebTimeZone.appliedAtBootstrap()),
    "never claim a Gecko website time zone override");
  GeckoSharedWebTimeZone.setRequested(personal,"Asia/Tokyo");
  check(Os.writes==0,"live settings do not mutate process environment");
  GeckoSharedWebTimeZone.applyAtAppBootstrap(personal);
  check(Os.writes==0,"subsequent boot remains fail-closed");
  GeckoSharedWebTimeZone.setRequested(work,"Europe/London");
  check("Asia/Tokyo".equals(GeckoSharedWebTimeZone.requested(personal)),
    "work profile preference remains separate");
  GeckoSharedWebTimeZone.setRequested(personal,"SYSTEM");
  GeckoSharedWebTimeZone.applyAtAppBootstrap(personal);
  check("NATIVE_GECKO_DEFAULT_NO_OVERRIDE".equals(
    GeckoSharedWebTimeZone.bootStatus()),"reset shows engine default");
  check(Os.writes==0,"reset never mutates original Gecko environment");
  System.out.println("OGTZ02_FAILED_ENV_DISABLED_AND_ANDROID_PROFILE_"+passed+"_OF_"+passed+"=PASS");
  System.out.println("OGTZ02_GECKO_JS_INTL_DATE_E2E=USER_REPORTED_FAIL");
 }
}
'''
}
with TemporaryDirectory(prefix="ogtz01-") as dir:
    tmp=Path(dir)
    java=[]
    for n,s in files.items():
        path=tmp/n
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(s)
        java.append(str(path))
    java+=[str(pkg/n) for n in ("AccountClockFormat.java","GeckoSharedWebTimeZone.java")]
    out=tmp/"out";out.mkdir()
    subprocess.run(["javac","-encoding","UTF-8","-d",str(out),*java],check=True)
    subprocess.run(["java","-cp",str(out),"com.omnigate.integrated.SharedTZTest"],check=True)
print("OGTZ02_BOOT_ORDER_AND_FAIL_CLOSED_NO_ENV_MUTATION=PASS_SOURCE")
