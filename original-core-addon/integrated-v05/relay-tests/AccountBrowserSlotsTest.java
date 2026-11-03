package com.omnigate.integrated;
import java.util.HashSet;
public final class AccountBrowserSlotsTest {
    static int assertions;
    static void ok(boolean b){assertions++;if(!b)throw new AssertionError("account slot "+assertions);}
    public static void main(String[] args){
        ok(AccountBrowserSlots.MAX_SLOTS==5);
        ok(AccountBrowserSlots.PROVIDERS.length==4);
        HashSet<String> ids=new HashSet<>();
        for(String p:AccountBrowserSlots.PROVIDERS){
            ok(AccountBrowserSlots.supported(p));
            ok(AccountBrowserSlots.home(p).startsWith("https://"));
            for(int i=0;i<50;i++){
                String id=AccountBrowserSlots.newContextId();
                ok(AccountBrowserSlots.validContextId(id));
                ok(ids.add(id));
            }
        }
        ok(!AccountBrowserSlots.validContextId(null));
        ok(!AccountBrowserSlots.validContextId("default"));
        ok(!AccountBrowserSlots.validContextId(""));
        ok(!AccountBrowserSlots.supported("unknown"));
        System.out.println("OGPROFILE01_200_ISOLATED_CONTEXT_IDS=PASS");
        System.out.println("OGPROFILE01_FOUR_FIXED_PROVIDER_ORIGINS=PASS");
        System.out.println("OGPROFILE01_NO_DEFAULT_SHARED_COOKIE_CONTEXT=PASS");
        System.out.println("OGPROFILE01_ASSERTIONS="+assertions);
    }
}
