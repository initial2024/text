package com.omnigate.integrated;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Opt-in, context-bound navigation gate. This does not alter the real
 * network stack, DNS, fingerprints, JavaScript or website credentials.
 * If enabled, external-app schemes and new browser windows are rejected.
 * OAuth providers sometimes require popups: the policy defaults OFF
 * until the user explicitly enables it for the particular login.
 */
final class GeckoAccountNavigationGuard {
    private GeckoAccountNavigationGuard(){}

    static boolean denyUri(String raw,int target,boolean strict){
        if(!strict)return false;
        if(target==1)return true; // Gecko TARGET_WINDOW_NEW verified in tests.
        if(raw==null||raw.length()>8192)return true;
        try{
            URI uri=new URI(raw);
            String scheme=uri.getScheme();
            if(scheme==null)return false; // relative navigation is Gecko-owned
            switch(scheme.toLowerCase(Locale.ROOT)){
                case "https":case "http":
                case "about":case "data":case "blob":
                case "resource":case "moz-extension":
                    return false;
                default:
                    // file:, content:, intent:, javascript:, tel:, market:,
                    // Android app-deeplinks, and unknown external schemes.
                    return true;
            }
        }catch(Exception invalid){return true;}
    }

    static Object intercept(Object request,BooleanSupplier owner,boolean strict){
        // A callback captured by a now-foreign/stale GeckoSession never
        // receives a positive navigation decision.
        boolean current=false;
        try{current=owner!=null&&owner.getAsBoolean();}
        catch(RuntimeException ignored){}
        if(!current)return denyResult();
        if(!strict)return null;
        try{
            Class<?> cls=request.getClass();
            Field f=cls.getField("uri");
            Object raw=f.get(request);
            int target=cls.getField("target").getInt(request);
            if(!denyUri((String)raw,target,true))return null;
            return denyResult();
        }catch(Throwable invalid){
            return denyResult();
        }
    }

    private static Object denyResult(){
        try{
            return Class.forName("org.mozilla.geckoview.GeckoResult")
                .getMethod("deny").invoke(null);
        }catch(Throwable unavailable){
            // Returning null means Gecko ALLOWS the navigation, so do not
            // silently claim that a failed safety hook blocked a request.
            throw new IllegalStateException("gecko_navigation_deny_unavailable",unavailable);
        }
    }
}
