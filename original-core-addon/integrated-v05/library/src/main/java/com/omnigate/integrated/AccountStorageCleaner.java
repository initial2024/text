package com.omnigate.integrated;

import android.content.Context;

/**
 * Scoped data erasure only. Gecko host/base-domain deletion is explicitly
 * forbidden because it spans multiple contextIds for the same website.
 */
final class AccountStorageCleaner {
    private AccountStorageCleaner(){}
    static boolean closeCurrentPage(String provider,int slot){
        boolean closed=IsolatedGeckoBrowser.closeIfMatchesForErase(provider,slot);
        boolean overlay=ClaudeFloatingOverlay.closeIfMatchesForErase(provider,slot);
        if(!IsolatedGeckoBrowser.ownerHealthy()||!ClaudeFloatingOverlay.ownerHealthy())
            throw new IllegalStateException("gecko_session_owner_unverified");
        return overlay||closed;
    }
    static void clearWholeContext(Context c,String provider,int slot) throws Exception{
        final String oldId=AccountBrowserStore.contextId(c,provider,slot);
        if(!AccountBrowserSlots.validContextId(oldId))
            throw new IllegalStateException("invalid_account_context");
        // Fail closed when the original GeckoRuntime is unavailable.
        Class<?> runtimeClass=Class.forName("org.mozilla.geckoview.GeckoRuntime");
        java.lang.reflect.Method get=runtimeClass.getDeclaredMethod("getInstance");
        get.setAccessible(true);
        Object runtime=get.invoke(null);
        if(runtime==null)throw new IllegalStateException("gecko_runtime_not_initialized");
        // The open page must be closed BEFORE clearing. Other account IDs
        // remain open and keep their own cookies, indexedDB and storage.
        closeCurrentPage(provider,slot);
        Object storage=runtimeClass.getMethod("getStorageController").invoke(runtime);
        storage.getClass().getMethod("clearDataForSessionContext",String.class)
            .invoke(storage,oldId);
        // GeckoView's clearDataForSessionContext has a void API, not an
        // independently verifiable disk-erasure receipt. This is a submitted
        // per-context deletion, NOT certified secure erasure. Rotate the ID
        // so the deleted context cannot be re-opened through this slot.
        AccountBrowserStore.rotateContext(c,provider,slot,oldId);
    }
}
