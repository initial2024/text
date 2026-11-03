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
        if(context==null||nativeProvider==null)
            throw new IllegalArgumentException("native_context_or_provider_null");
        try{
            if(!"com.deepseek2.app.gateway.AiProvider"
                .equals(nativeProvider.getClass().getName()))
                throw new IllegalArgumentException("wrong_native_provider_type");
            Object model=nativeProvider.getClass().getMethod("getModel")
                .invoke(nativeProvider);
            if(!(model instanceof String))
                throw new IllegalStateException("native_model_unknown");
            return MainAccountTargetStore.contextForNewNativeSession(
                context,(String)model);
        }catch(ReflectiveOperationException impossible){
            throw new IllegalStateException("native_provider_reflection_failed",impossible);
        }
    }
}
