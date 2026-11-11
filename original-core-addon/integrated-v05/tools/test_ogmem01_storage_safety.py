#!/usr/bin/env python3
"""OGMEM01: Java storage/hidden-session tests and source-only cleanup gates.

Never uses a real account, browser profile, user APK or private signing key.
"""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

root=Path(__file__).resolve().parents[1]
java=root/"library/src/main/java/com/omnigate/integrated"
def read(name):return (java/name).read_text(encoding="utf-8")
foot=read("GeckoLocalFootprint.java")
downloads=read("GeckoBrowserDownloads.java")
controller=read("GeckoFoldMemoryController.java")
floating=read("ClaudeFloatingOverlay.java")
settings=read("AccountBrowserStore.java")
quick=read("AccountQuickSettings.java")
view=read("GeckoStorageMemoryUi.java")
assert "return prefs(c).getBoolean(" in settings
assert '"low_memory_on_fold."+contextId(c,provider,slot),false' in settings
assert "GeckoFoldMemoryController.setVisible(current,id,false)" in floating
assert "GeckoFoldMemoryController.setVisible(current,id,true)" in floating
assert "private static void updateFoldMemoryState(boolean isFolded)" in floating
assert "updateFoldMemoryState(value);" in floating
assert "if(foldedLowMemorySession==session)updateFoldMemoryState(false);" in floating
assert "foldedLowMemorySession=null" in floating
assert "GeckoStorageMemoryUi.show(" in floating
assert "GeckoStorageMemoryUi.show(" in quick
assert 'getString(provider+"."+slot,null)' in foot
assert "AccountBrowserStore.contextId(c,provider,slot)" not in foot
assert "getTotalPss()" in foot and "getNativeHeapAllocatedSize()" in foot
assert "getNoBackupFilesDir()" in foot
assert "clearDataForSessionContext(" not in foot
assert "delete()" not in foot
assert "MAX_WALK_ENTRIES" in foot
assert "Files.isSymbolicLink" in foot
assert "24L*60*60*1000" in downloads
assert "download_transfer_in_progress" in downloads
assert "synchronized(LOCK)" in downloads
assert "contextId.equals(active.contextId)" in downloads
assert ".matches(" in downloads and ".partial" in downloads
assert "Files.isSymbolicLink" in downloads
assert "download_scan_limit" in downloads
assert ".delete()" in downloads  # restricted to explicit download method
assert "setPositiveButton(\"确认清理\"" in view
assert "if(!BUSY.compareAndSet(false,true))" in view
assert "new Thread(" in view
assert "clearDataForSessionContext(" not in view
assert "clearApplicationUserData(" not in view
assert "AccountBrowserStore.rotateContext" not in downloads
assert "setActive" in controller and "GeckoLiveWindowHandoff.verify" in controller
assert "GeckoLiveWindowHandoff.owns" in controller

stubs={
"android/content/Context.java":r'''
package android.content;
import java.io.File;
public class Context{
  public static final int MODE_PRIVATE=0;
  public Context getApplicationContext(){return this;}
  public SharedPreferences getSharedPreferences(String name,int mode){
    return new SharedPreferences();
  }
  public File getCacheDir(){return new File("/ignored");}
  public File getCodeCacheDir(){return new File("/ignored");}
  public File getFilesDir(){return new File("/ignored");}
  public File getNoBackupFilesDir(){return new File("/ignored");}
}
''',
"android/content/SharedPreferences.java":r'''
package android.content;
public class SharedPreferences{
    public String getString(String key,String fallback){return fallback;}
}
''',
"android/os/Debug.java":r'''
package android.os;
public class Debug{
  public static class MemoryInfo{public int getTotalPss(){return 2048;}}
  public static void getMemoryInfo(MemoryInfo info){}
  public static long getNativeHeapAllocatedSize(){return 1024;}
}
''',
"android/os/Looper.java":r'''
package android.os;
public final class Looper{
  private static final Looper MAIN=new Looper();
  public static boolean isMain=true;
  public static Looper getMainLooper(){return MAIN;}
  public static Looper myLooper(){return isMain?MAIN:new Looper();}
}
''',
"android/util/Log.java":r'''
package android.util;
public final class Log{
  public static int w(String tag,String text){return 0;}
}
''',
"com/omnigate/integrated/OwnershipStubs.java":r'''
package com.omnigate.integrated;
import android.content.Context;
final class AccountBrowserSlots{
  static boolean validContextId(String id){return id!=null&&id.startsWith("og-ctx-v1-");}
  static boolean supported(String provider){return "claude".equals(provider);}
}
final class AccountBrowserStore {
  static int count(Context c,String p){return 1;}
}
final class GeckoBrowserDownloads {
  static java.io.File directory(Context c,String id){return new java.io.File("/unused");}
  static boolean hasLiveTransferForContext(String id){return false;}
}
final class GeckoLiveWindowHandoff {
  static Object active;
  static boolean owns(Object s){return s==active;}
  static void verify(Object s,String id)throws Exception{
    if(s!=active)throw new Exception("wrong_owner");
    if(!id.equals(((FakeSession)s).context))throw new Exception("context");
  }
}
public final class FakeSession {
  final String context;
  int changed=0;
  boolean isActive=true;
  public FakeSession(String id){context=id;}
  public void setActive(boolean visible){changed++;isActive=visible;}
}
''',
"com/omnigate/integrated/MemContract.java":r'''
package com.omnigate.integrated;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import android.os.Looper;
public final class MemContract{
  static int asserts=0;
  static void check(boolean v,String reason){asserts++;if(!v)throw new AssertionError(reason);}
  public static void main(String[] args)throws Exception{
    Path base=Files.createTempDirectory("ogmem-regression");
    try{
      Path owned=Files.createDirectories(base.resolve("owned"));
      Path outside=Files.createDirectories(base.resolve("outside"));
      byte[] bytes=new byte[2048];
      Files.write(owned.resolve("first.bin"),bytes);
      Files.write(outside.resolve("secret.bin"),new byte[50000]);
      Path nested=Files.createDirectories(owned.resolve("nested"));
      Files.write(nested.resolve("second.bin"),new byte[1024]);
      Files.createSymbolicLink(owned.resolve("external-link"),outside);
      GeckoLocalFootprint.Usage u=GeckoLocalFootprint.measure(owned.toFile());
      check(u.bytes==3072,"only owned files counted, no symlink target");
      check(u.files==2,"only two owned files");
      check(u.unreadable,"symlink must report unsafe/incomplete");
      check(Files.exists(outside.resolve("secret.bin")),"scanner does not delete");
      check("0.00".equals(GeckoLocalFootprint.mb(0)),"mb zero");
      check("1.00".equals(GeckoLocalFootprint.mb(1024L*1024)),"mb in MiB");
      String id="og-ctx-v1-01234567-89ab-cdef-0123-456789abcdef";
      FakeSession s=new FakeSession(id);
      check(!GeckoFoldMemoryController.setVisible(s,id,false),"no owner denied");
      GeckoLiveWindowHandoff.active=s;
      check(GeckoFoldMemoryController.setVisible(s,id,false),"owner folded");
      check(!s.isActive&&s.changed==1,"actual setActive(false) call");
      check(GeckoFoldMemoryController.setVisible(s,id,true),"owner restored");
      check(s.isActive&&s.changed==2,"same exact session restored");
      check(!GeckoFoldMemoryController.setVisible(s,"og-ctx-v1-wrong",false),
            "context mismatch rejected");
      Looper.isMain=false;
      check(!GeckoFoldMemoryController.setVisible(s,id,false),
            "off-ui-thread mutation denied");
      check(s.isActive&&s.changed==2,"no invalid owner/thread mutation");
      System.out.println("OGMEM01_JAVA_ASSERTIONS="+asserts);
      System.out.println("OGMEM01_BOUNDED_SYMLINK_SAFE_READONLY_USAGE=PASS");
      System.out.println("OGMEM01_SAME_SESSION_FOLD_RESTORE=PASS_SIMULATED");
      System.out.println("OGMEM01_ACCOUNT_LOGIN_STORAGE_NOT_CLEARED=PASS_SOURCE_ONLY");
      System.out.println("OGMEM01_ANDROID16_TRUE_RAM_SAVED=NOT_TESTED");
    }finally{
      try(java.util.stream.Stream<Path> all=Files.walk(base)){
        all.sorted(java.util.Comparator.reverseOrder()).forEach(p->{
          try{Files.deleteIfExists(p);}catch(Exception ignored){}
        });
      }
    }
  }
}
'''
}
with TemporaryDirectory(prefix="ogmem01-") as temp:
    rootp=Path(temp)
    sources=[]
    for name,text in stubs.items():
        path=rootp/"src"/name
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(text,encoding="utf-8")
        sources.append(str(path))
    # Public test helper must have matching filename.
    fake=rootp/"src/com/omnigate/integrated/OwnershipStubs.java"
    data=fake.read_text()
    helper=data[data.index("public final class FakeSession"):]
    fake.write_text(data[:data.index("public final class FakeSession")])
    source=rootp/"src/com/omnigate/integrated/FakeSession.java"
    source.write_text("package com.omnigate.integrated;\n"+helper)
    sources.append(str(source))
    classes=rootp/"classes";classes.mkdir()
    subprocess.run(["javac","-encoding","UTF-8","-d",str(classes),
      str(java/"GeckoLocalFootprint.java"),
      str(java/"GeckoFoldMemoryController.java"),*sources],check=True)
    subprocess.run(["java","-cp",str(classes),
      "com.omnigate.integrated.MemContract"],check=True)
