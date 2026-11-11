package com.omnigate.integrated;

import android.os.Looper;
import android.util.Log;

/**
 * Gecko-approved low-memory hint for a truly hidden/folded GeckoSession.
 * Never call on a still-visible page, across Android user/profile boundaries,
 * a stale owner, or during an in-progress live handoff.
 */
final class GeckoFoldMemoryController {
    private static final String TAG="OmniGateMemoryFold";
    private GeckoFoldMemoryController(){}
    static boolean setVisible(Object session,String expectedId,boolean visible){
        if(Looper.myLooper()!=Looper.getMainLooper())return false;
        if(session==null||!AccountBrowserSlots.validContextId(expectedId)||
            !GeckoLiveWindowHandoff.owns(session))return false;
        try{
            GeckoLiveWindowHandoff.verify(session,expectedId);
            session.getClass().getMethod("setActive",boolean.class)
                .invoke(session,visible);
            // Gecko's async internal release is *not* proof memory was freed.
            return true;
        }catch(Throwable failure){
            Log.w(TAG,"gecko_inactive_hint_failed_"+
                failure.getClass().getSimpleName());
            return false;
        }
    }
}
