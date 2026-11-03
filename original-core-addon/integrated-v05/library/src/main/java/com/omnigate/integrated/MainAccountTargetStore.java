package com.omnigate.integrated;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Separate durable MAIN-executor target intent, not auxiliary UI selection.
 * No cookies, passwords, provider tokens, model requests or account IDs stored.
 * A target is only an intent until exact native WebDriver context attestation.
 */
final class MainAccountTargetStore {
    static final class Target {
        final String provider;
        final int slot;
        final String contextId;
        Target(String provider,int slot,String contextId){
            this.provider=provider;this.slot=slot;this.contextId=contextId;
        }
    }
    private static final String NAME="omnigate_main_executor_target_v1";
    private MainAccountTargetStore(){}
    private static SharedPreferences pref(Context c){
        if(c==null)throw new IllegalStateException("app_context_missing");
        return c.getApplicationContext().getSharedPreferences(NAME,Context.MODE_PRIVATE);
    }
    static synchronized Target active(Context c){
        SharedPreferences p=pref(c);
        if(!p.getBoolean("armed",false))return null;
        String provider=p.getString("provider","");
        int slot=p.getInt("slot",-1);
        String context=p.getString("context","");
        if(!AccountBrowserSlots.supported(provider)||
            slot<0||slot>=AccountBrowserStore.count(c,provider)||
            !AccountBrowserSlots.validContextId(context)||
            !context.equals(AccountBrowserStore.contextId(c,provider,slot)))
            throw new IllegalStateException("main_target_state_invalid");
        return new Target(provider,slot,context);
    }
    static synchronized Target request(Context c,String provider,int slot){
        if(!AccountBrowserSlots.supported(provider))
            throw new IllegalArgumentException("provider");
        String context=AccountBrowserStore.contextId(c,provider,slot);
        if(!pref(c).edit().putString("provider",provider)
            .putInt("slot",slot).putString("context",context)
            .putBoolean("armed",true).commit())
            throw new IllegalStateException("main_target_write_failed");
        return new Target(provider,slot,context);
    }
    static synchronized void cancel(Context c){
        if(!pref(c).edit().putBoolean("armed",false).commit())
            throw new IllegalStateException("main_target_cancel_failed");
    }

    /**
     * Native-build injection handshake: to be called ONLY while constructing
     * a fresh original WebDriver GeckoSession BEFORE it is opened. No code in
     * this addon currently calls it as a native GeckoView builder hook.
     */
    static synchronized String contextForNewNativeSession(Context c,String nativeModel){
        Target target=active(c);
        if(target==null)return null;
        // A different provider must NOT inherit another provider's
        // isolated cookie jar. Leave its existing session unchanged and
        // keep the 8787 target guard armed, so no unintended send occurs.
        if(!target.provider.equals(ProviderRiskScopes.fromModel(nativeModel)))
            return null;
        return target.contextId;
    }
}
