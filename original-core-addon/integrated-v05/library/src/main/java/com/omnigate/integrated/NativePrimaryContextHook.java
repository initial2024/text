package com.omnigate.integrated;

import android.content.Context;

/**
 * Public ABI for a reviewed ORIGINAL ProviderBrowser.createSession() binary
 * insertion BEFORE GeckoSessionSettings.Builder.build().
 *
 * When no explicit target is armed the original session's default context is
 * retained. If a target is armed, a model mismatch fails closed; never choose
 * the context from an arbitrary model request or reuse another provider's jar.
 */
public final class NativePrimaryContextHook {
    private NativePrimaryContextHook(){}
    public static String contextIdForNewSession(Context context,Object nativeProvider){
        // A missing/unknown native ABI must NOT crash MainActivity launch.
        // Null never authorizes the target; the independent 8787 admission
        // guard still blocks wrong-account submissions.
        if(context==null||nativeProvider==null)return null;
        try{
            if(!"com.deepseek2.app.gateway.AiProvider"
                .equals(nativeProvider.getClass().getName()))return null;
            Object model=nativeProvider.getClass().getMethod("getModel")
                .invoke(nativeProvider);
            if(!(model instanceof String))return null;
            return MainAccountTargetStore.contextForNewNativeSession(
                context,(String)model);
        }catch(ReflectiveOperationException|RuntimeException unavailable){
            return null;
        }
    }
}
