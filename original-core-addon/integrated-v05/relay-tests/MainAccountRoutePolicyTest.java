package com.omnigate.integrated;

public final class MainAccountRoutePolicyTest {
    private static int assertions;
    static void ok(boolean value){assertions++;if(!value)throw new AssertionError("main route "+assertions);}
    static MainAccountRoutePolicy.Decision check(boolean armed,String desired,
            String nativeProvider,boolean owner,boolean context){
        return MainAccountRoutePolicy.decide(armed,desired,nativeProvider,owner,context);
    }
    public static void main(String[] args){
        ok(check(false,null,"chatgpt",false,false).allowed);
        ok(!check(true,"chatgpt","deepseek",true,true).allowed);
        ok(check(true,"chatgpt","deepseek",true,true).outcome==
            MainAccountRoutePolicy.Outcome.PROVIDER_MISMATCH);
        ok(!check(true,"chatgpt","chatgpt",false,true).allowed);
        ok(check(true,"chatgpt","chatgpt",false,true).outcome==
            MainAccountRoutePolicy.Outcome.OWNER_UNVERIFIED);
        ok(!check(true,"chatgpt","chatgpt",true,false).allowed);
        ok(check(true,"chatgpt","chatgpt",true,false).outcome==
            MainAccountRoutePolicy.Outcome.CONTEXT_UNVERIFIED);
        ok(check(true,"chatgpt","chatgpt",true,true).allowed);
        ok(check(true,"chatgpt","chatgpt",true,true).outcome==
            MainAccountRoutePolicy.Outcome.VERIFIED);
        for(int i=0;i<500;i++)
            ok(!check(true,"glm","grok",true,true).allowed);
        System.out.println("OGMAIN01_TARGET_NOT_NATIVE_ACCOUNT_FAIL_CLOSED=PASS");
        System.out.println("OGMAIN01_VERIFIED_OWNER_AND_GECKO_CONTEXT_REQUIRED=PASS");
        System.out.println("OGMAIN01_TESTS="+assertions);
    }
}
