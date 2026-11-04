package com.omnigate.integrated;

import android.content.Context;

/**
 * Scoped data erasure only. Gecko host/base-domain deletion is explicitly
 * forbidden because it spans multiple contextIds for the same website.
 */
final class AccountStorageCleaner {
    private AccountStorageCleaner(){}
    static boolean closeCurrentPage(String provider,int slot){
        boolean closed=IsolatedGeckoBrowser.closeIfMatches(provider,slot);
        return ClaudeFloatingOverlay.closeIfMatches(provider,slot)||closed;
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
        // Retire the old context ID after Gecko accepts the delete command.
        // Even if deletion internally takes time, the old ID cannot be
        // opened again by this account on the next login.
        AccountBrowserStore.rotateContext(c,provider,slot,oldId);
    }
}
