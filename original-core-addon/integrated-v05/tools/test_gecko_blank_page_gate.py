#!/usr/bin/env python3
"""O G B L A N K 0 1 — pure JVM Gecko error-page contract, no real cookies,
network, accounts, Android runtime, or APK signing credentials."""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

root=Path(__file__).resolve().parents[1]
impl=root/"library/src/main/java/com/omnigate/integrated"
browser=(impl/"IsolatedGeckoBrowser.java").read_text()
overlay=(impl/"ClaudeFloatingOverlay.java").read_text()
downloads=(impl/"GeckoBrowserDownloads.java").read_text()
error=(impl/"GeckoPageFailureUi.java").read_text()
for name,content in [("fullpage",browser),("overlay",overlay)]:
    assert '"onLoadError".equals' in content, name
    assert "GeckoPageFailureUi.response(" in content, name
    assert "GeckoPageFailureUi.detail(" in content, name
    assert "GeckoBrowserDownloads.attach(" in content, name
assert "onCrash" in downloads and "onKill" in downloads
assert "cancelSession(session)" in downloads
assert "contentProcessTerminated=true" in browser and "contentProcessTerminated=true" in overlay
assert "getMethod(\"isOpen\")" in browser and "getMethod(\"isOpen\")" in overlay
assert "GECKO_NETWORK_ERROR(category=" in error
assert "data:text/html;charset=utf-8," in error
assert "uri.toString()" not in error
assert "loadUri" not in error and "reload" not in error.replace('\"主页\"或\"前往\"按钮','')
assert "clearDataForSessionContext" not in error
assert "clearApplicationUserData" not in error
assert "setUserAgentOverride" not in error
assert "certificate_exception" not in error.lower()

stubs={
 "android/net/Uri.java": r'''
package android.net;
public final class Uri{
  public static String encode(String value){
    try{return java.net.URLEncoder.encode(value,"UTF-8").replace("+","%20");}
    catch(Exception impossible){throw new IllegalStateException(impossible);}
  }
}
''',
 "android/util/Log.java": r'''
package android.util;
public final class Log {
  public static int w(String tag,String message){return 0;}
}
''',
 "org/mozilla/geckoview/GeckoResult.java": r'''
package org.mozilla.geckoview;
public class GeckoResult<T>{
  public final T value;
  public GeckoResult(T value){this.value=value;}
  public static <V> GeckoResult<V> fromValue(V value){return new GeckoResult<>(value);}
}
''',
 "com/omnigate/integrated/BlankPageTest.java": r'''
package com.omnigate.integrated;
public final class BlankPageTest {
  public static final class SampleError{
    public final int category=7;
    public final int code=112;
  }
  private static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
  public static void main(String[] args){
    for(String provider:AccountBrowserSlots.PROVIDERS){
      Object response=GeckoPageFailureUi.response(provider,new SampleError());
      check(response instanceof org.mozilla.geckoview.GeckoResult,"not a GeckoResult");
      String uri=(String)((org.mozilla.geckoview.GeckoResult<?>)response).value;
      check(uri.startsWith("data:text/html;charset=utf-8,"),"must be local data page");
      check(!uri.contains("https://")&&!uri.contains("http://"),"no network");
      check(!uri.contains("oauth")&&!uri.contains("cookie"),"no sensitive URI");
      check(uri.length()<9000,"bounded HTML");
    }
    final class DnsError{
      public final int category=5;
      public final int code=37;
    }
    check(GeckoPageFailureUi.explain(new DnsError()).contains("DNS_UNKNOWN_HOST"),
      "category5 code37 must resolve to DNS unknown host");
    check(GeckoPageFailureUi.explain(new DnsError()).contains("工作资料"),
      "work-profile guidance exists");
    for(String provider:AccountBrowserSlots.PROVIDERS){
      Object response=GeckoPageFailureUi.response(provider,new DnsError());
      String uri=(String)((org.mozilla.geckoview.GeckoResult<?>)response).value;
      check(uri.contains("DNS_UNKNOWN_HOST")||uri.contains("DNS"),"dns signal visible");
    }
    String detail=GeckoPageFailureUi.detail(new SampleError());
    check("GECKO_NETWORK_ERROR(category=7,code=112)".equals(detail),"error code");
    check("GECKO_NETWORK_ERROR(category=-1,code=-1)".equals(
      GeckoPageFailureUi.detail(null)),"missing error fail safe");
    System.out.println("OGBLANK01_FIVE_PROVIDER_GECKO_LOCAL_ERROR_PAGE=PASS");
    System.out.println("OGBLANK01_REDACTED_ERROR_CATEGORY_CODE=PASS");
    System.out.println("OGBLANK01_NO_AUTOMATIC_RETRY_OR_COOKIE_CLEAR=PASS_SOURCE_ONLY");
    System.out.println("OGBLANK01_ANDROID16_REAL_WEBSITE_E2E=NOT_TESTED");
  }
}
'''}
with TemporaryDirectory(prefix="ogblank01-") as temp:
    d=Path(temp)
    src=[]
    for name,text in stubs.items():
        p=d/"src"/name
        p.parent.mkdir(parents=True,exist_ok=True)
        p.write_text(text)
        src.append(str(p))
    out=d/"classes"
    out.mkdir()
    subprocess.run(["javac","-encoding","UTF-8","-d",str(out),
        str(impl/"AccountBrowserSlots.java"),
        str(impl/"GeckoPageFailureUi.java"),*src],check=True)
    subprocess.run(["java","-cp",str(out),
        "com.omnigate.integrated.BlankPageTest"],check=True)
