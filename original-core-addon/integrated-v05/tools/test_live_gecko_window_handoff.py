#!/usr/bin/env python3
"""Actual Java handoff helper against in-process GeckoView API fakes.
This does NOT replace Android 16 runtime tests or certify browser DOM/IME state.
"""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess

root = Path(__file__).resolve().parents[1]
impl = root / "library/src/main/java/com/omnigate/integrated"
browser = (impl / "IsolatedGeckoBrowser.java").read_text()
overlay = (impl / "ClaudeFloatingOverlay.java").read_text()
bridge = (impl / "GeckoLiveWindowHandoff.java").read_text()
assert "handoffToFloatingWindow()" in browser
assert "adoptLiveOverlay(" in browser
assert "adoptLiveBrowser(" in overlay
assert "returnLivePage()" in overlay
assert "GeckoLiveWindowHandoff.move(" in browser and "GeckoLiveWindowHandoff.move(" in overlay
assert browser.count("()->GeckoLiveWindowHandoff.owns(created)") == 3
assert overlay.count("()->GeckoLiveWindowHandoff.owns(created)") >= 3
assert "getMethod(\"releaseSession\")" in browser
assert "detach.invoke(sourceView)" in bridge
assert "rollback_owner_not_restored" in bridge
assert "getUsePrivateMode" in bridge
assert "getContextId" in bridge
for fragment in ("private void handoffToFloatingWindow()", "static boolean adoptLiveOverlay("):
    segment = browser.split(fragment, 1)[1].split("\n    private ", 1)[0]
    assert "loadUri(" not in segment and 'callSession("reload")' not in segment
for fragment in ("static boolean adoptLiveBrowser(", "private static void returnLivePage()"):
    segment = overlay.split(fragment, 1)[1].split("\n    private static ", 1)[0]
    assert "loadUri(" not in segment and 'callSession("reload")' not in segment

stubs = {
 "android/os/Looper.java": """
package android.os;
public final class Looper {
    private static final Looper MAIN = new Looper();
    public static boolean ui = true;
    public static Looper getMainLooper(){return MAIN;}
    public static Looper myLooper(){return ui?MAIN:new Looper();}
}
""",
 "com/omnigate/integrated/OwnerStubs.java": """
package com.omnigate.integrated;
final class IsolatedGeckoBrowser {
    static Object owned;
    static boolean ownsLiveSession(Object session){return owned==session;}
}
final class ClaudeFloatingOverlay {
    static Object owned;
    static boolean ownsLiveSession(Object session){return owned==session;}
}
""",
 "org/mozilla/geckoview/GeckoSessionSettings.java": """
package org.mozilla.geckoview;
public final class GeckoSessionSettings {
    private final String id;
    private final boolean secret;
    public GeckoSessionSettings(String id,boolean priv){this.id=id;secret=priv;}
    public String getContextId(){return id;}
    public boolean getUsePrivateMode(){return secret;}
}
""",
 "org/mozilla/geckoview/GeckoSession.java": """
package org.mozilla.geckoview;
public final class GeckoSession {
    private final GeckoSessionSettings settings;
    public int closes=0;
    public int reloads=0;
    public GeckoSession(String id,boolean priv){
        settings=new GeckoSessionSettings(id,priv);
    }
    public GeckoSessionSettings getSettings(){return settings;}
    public void close(){closes++;}
    public void reload(){reloads++;}
}
""",
 "org/mozilla/geckoview/GeckoView.java": """
package org.mozilla.geckoview;
public final class GeckoView {
    private GeckoSession current;
    public boolean failAttach=false;
    public GeckoView(){}
    public GeckoSession getSession(){return current;}
    public GeckoSession releaseSession(){
        GeckoSession prior=current;current=null;return prior;
    }
    public void setSession(GeckoSession newSession){
        if(failAttach)throw new IllegalStateException("synthetic_attach_failure");
        if(current!=null)throw new IllegalStateException("old_session_not_released");
        current=newSession;
    }
}
""",
 "com/omnigate/integrated/LiveHandoffContractTest.java": """
package com.omnigate.integrated;
import android.os.Looper;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.GeckoSession;
public final class LiveHandoffContractTest{
    static int assertions=0;
    static final String ID="og-ctx-v1-01234567-89ab-cdef-0123-456789abcdef";
    static void check(boolean valid,String reason){
        assertions++;if(!valid)throw new AssertionError(reason);
    }
    static void rejected(Runnable r,String message){
        boolean threw=false;
        try{r.run();}catch(Exception expected){threw=true;}
        check(threw,message);
    }
    public static void main(String[] args)throws Exception{
        GeckoSession s=new GeckoSession(ID,false);
        GeckoView page=new GeckoView();
        GeckoView floating=new GeckoView();
        page.setSession(s);
        check(!GeckoLiveWindowHandoff.owns(s),"unowned session must fail");
        IsolatedGeckoBrowser.owned=s;
        check(GeckoLiveWindowHandoff.owns(s),"page owner recognized");
        GeckoLiveWindowHandoff.move(page,floating,s,ID);
        check(page.getSession()==null,"old GeckoView released");
        check(floating.getSession()==s,"same GeckoSession floating");
        check(s.closes==0&&s.reloads==0,"neither close nor reload");
        IsolatedGeckoBrowser.owned=null;
        ClaudeFloatingOverlay.owned=s;
        check(GeckoLiveWindowHandoff.owns(s),"overlay owner recognized");
        GeckoLiveWindowHandoff.move(floating,page,s,ID);
        check(page.getSession()==s&&floating.getSession()==null,"roundtrip exact");
        check(s.closes==0&&s.reloads==0,"roundtrip never navigates or closes");

        rejected(()->{try{
            GeckoLiveWindowHandoff.move(page,floating,s,
                "og-ctx-v1-99999999-89ab-cdef-0123-456789abcdef");
        }catch(Exception e){throw new IllegalStateException(e);}},
        "wrong account must be rejected");
        check(page.getSession()==s,"context rejection preserves old page");

        GeckoView occupied=new GeckoView();
        occupied.setSession(new GeckoSession(ID,false));
        rejected(()->{try{
            GeckoLiveWindowHandoff.move(page,occupied,s,ID);
        }catch(Exception e){throw new IllegalStateException(e);}},
        "nonempty destination refused");
        check(page.getSession()==s,"already-owned target never disrupts source");

        GeckoView fail=new GeckoView();
        fail.failAttach=true;
        rejected(()->{try{
            GeckoLiveWindowHandoff.move(page,fail,s,ID);
        }catch(Exception e){throw new IllegalStateException(e);}},
        "synthetic setSession failure rejected");
        check(page.getSession()==s&&fail.getSession()==null,"rollback exact");

        GeckoSession secret=new GeckoSession(ID,true);
        GeckoView privateSource=new GeckoView();
        privateSource.setSession(secret);
        rejected(()->{try{
            GeckoLiveWindowHandoff.move(privateSource,new GeckoView(),secret,ID);
        }catch(Exception e){throw new IllegalStateException(e);}},
        "private/guest context cannot migrate as a saved account");
        check(privateSource.getSession()==secret,"private context intact");

        Looper.ui=false;
        rejected(()->{try{
            GeckoLiveWindowHandoff.move(page,new GeckoView(),s,ID);
        }catch(Exception e){throw new IllegalStateException(e);}},
        "background GeckoView mutation denied");
        Looper.ui=true;
        check(page.getSession()==s,"off-main-thread rejection no mutation");
        System.out.println("OGLIVE01_JAVA_HANDOFF_ROUNDTRIP=PASS");
        System.out.println("OGLIVE01_NO_RELOAD_OR_CLOSE=PASS");
        System.out.println("OGLIVE01_WRONG_CONTEXT_PRIVATE_AND_OCCUPIED_REJECTED=PASS");
        System.out.println("OGLIVE01_ROLLBACK=PASS");
        System.out.println("OGLIVE01_SOURCE_ONLY_ANDROID_DEVICE_E2E=NOT_TESTED");
        System.out.println("OGLIVE01_ASSERTIONS="+assertions);
    }
}
""",
}

with TemporaryDirectory(prefix="oglive01-") as temp:
    base = Path(temp)
    sources = []
    for name, text in stubs.items():
        path = base / "src" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text)
        sources.append(str(path))
    classes = base / "classes"
    classes.mkdir()
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(classes),
        str(impl / "GeckoLiveWindowHandoff.java"),
        str(impl / "AccountBrowserSlots.java"), *sources],check=True)
    subprocess.run(["java", "-cp", str(classes),
        "com.omnigate.integrated.LiveHandoffContractTest"],check=True)
