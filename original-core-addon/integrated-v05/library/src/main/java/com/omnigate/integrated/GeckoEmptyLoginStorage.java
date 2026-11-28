package com.omnigate.integrated;

import java.lang.reflect.Array;
import java.lang.reflect.Proxy;
import java.util.WeakHashMap;

/**
 * Shared Gecko storage is deliberately empty, not a password provider.
 * Only install when no previous delegate exists. Never replace an unknown
 * delegate or return any account-space credential from a runtime callback.
 */
final class GeckoEmptyLoginStorage {
    private static final WeakHashMap<Object,Object> owned=new WeakHashMap<>();
    private GeckoEmptyLoginStorage(){}
    static boolean install(Object runtime){
        if(runtime==null)return false;
        try{
            Object previous=runtime.getClass().getMethod("getAutocompleteStorageDelegate").invoke(runtime);
            if(previous!=null)return previous==owned.get(runtime);
            Class<?> delegate=Class.forName("org.mozilla.geckoview.Autocomplete$StorageDelegate");
            Object empty=Proxy.newProxyInstance(delegate.getClassLoader(),new Class<?>[]{delegate},
                (proxy,method,args)->{
                    String name=method.getName();
                    if("hashCode".equals(name))return System.identityHashCode(proxy);
                    if("equals".equals(name))return args!=null&&args.length==1&&proxy==args[0];
                    if("toString".equals(name))return "OmniPawsEmptySharedLoginStore";
                    if("onLoginFetch".equals(name)||"onAddressFetch".equals(name)||"onCreditCardFetch".equals(name)){
                        String entry="onLoginFetch".equals(name)?"LoginEntry":
                            "onAddressFetch".equals(name)?"Address":"CreditCard";
                        Object values=Array.newInstance(Class.forName("org.mozilla.geckoview.Autocomplete$"+entry),0);
                        return Class.forName("org.mozilla.geckoview.GeckoResult")
                            .getMethod("fromValue",Object.class).invoke(null,values);
                    }
                    // No save, update, use telemetry or external storage operation.
                    return null;
                });
            runtime.getClass().getMethod("setAutocompleteStorageDelegate",delegate).invoke(runtime,empty);
            if(runtime.getClass().getMethod("getAutocompleteStorageDelegate").invoke(runtime)!=empty)
                return false;
            owned.put(runtime,empty);return true;
        }catch(Exception unsupported){return false;}
    }
    static boolean verified(Object session){
        try{
            Object runtime=session.getClass().getMethod("getRuntime").invoke(session);
            Object delegate=owned.get(runtime);
            return delegate!=null&&runtime.getClass().getMethod("getAutocompleteStorageDelegate")
                .invoke(runtime)==delegate;
        }catch(Exception unavailable){return false;}
    }
}
