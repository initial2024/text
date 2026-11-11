package com.omnigate.integrated;

import android.os.Looper;

/**
 * Live GeckoSession handoff, never a login/session migration.
 *
 * GeckoView.releaseSession() explicitly does NOT close GeckoSession; the
 * session is reattached to a new GeckoView without a loadUri/reload call.
 * This class does not persist, copy or expose session data.
 */
final class GeckoLiveWindowHandoff {
    private GeckoLiveWindowHandoff(){}

    static boolean owns(Object currentSession){
        return currentSession!=null &&
            (IsolatedGeckoBrowser.ownsLiveSession(currentSession) ||
             ClaudeFloatingOverlay.ownsLiveSession(currentSession));
    }

    static void verify(Object geckoSession,String expectedContext)throws Exception{
        if(geckoSession==null||
            !AccountBrowserSlots.validContextId(expectedContext))
            throw new IllegalStateException("live_gecko_identity_unavailable");
        Object settings=geckoSession.getClass().getMethod("getSettings")
            .invoke(geckoSession);
        if(settings==null||
            !expectedContext.equals(settings.getClass()
                .getMethod("getContextId").invoke(settings))||
            Boolean.TRUE.equals(settings.getClass()
                .getMethod("getUsePrivateMode").invoke(settings)))
            throw new IllegalStateException("live_gecko_context_mismatch");
    }

    static void move(Object sourceView,Object targetView,Object geckoSession,
            String expectedContext)throws Exception{
        if(Looper.myLooper()!=Looper.getMainLooper())
            throw new IllegalStateException("gecko_handoff_ui_thread_required");
        verify(geckoSession,expectedContext);
        if(sourceView==null||targetView==null||sourceView==targetView)
            throw new IllegalStateException("gecko_handoff_invalid_views");
        Class<?> gecko=Class.forName("org.mozilla.geckoview.GeckoSession");
        Class<?> view=Class.forName("org.mozilla.geckoview.GeckoView");
        if(!view.isInstance(sourceView)||!view.isInstance(targetView)||
            !gecko.isInstance(geckoSession))
            throw new IllegalStateException("gecko_handoff_type_mismatch");
        java.lang.reflect.Method read=view.getMethod("getSession");
        java.lang.reflect.Method detach=view.getMethod("releaseSession");
        java.lang.reflect.Method attach=view.getMethod("setSession",gecko);
        if(read.invoke(sourceView)!=geckoSession || read.invoke(targetView)!=null)
            throw new IllegalStateException("gecko_handoff_current_view_mismatch");

        Object released=detach.invoke(sourceView);
        if(released!=geckoSession){
            // Refuse to continue if the source released an unexpected owner.
            if(released!=null && read.invoke(sourceView)==null)
                attach.invoke(sourceView,released);
            throw new IllegalStateException("gecko_handoff_wrong_released_session");
        }
        try{
            attach.invoke(targetView,geckoSession);
            if(read.invoke(targetView)!=geckoSession)
                throw new IllegalStateException("gecko_handoff_attach_not_confirmed");
            verify(geckoSession,expectedContext);
        }catch(Throwable attachFailure){
            // Restore the old window without reload. If restoration itself
            // fails, preserve both views and raise a HARD STOP for the caller.
            try{
                if(read.invoke(targetView)==geckoSession)
                    detach.invoke(targetView);
                if(read.invoke(sourceView)!=null)
                    throw new IllegalStateException("rollback_source_not_empty");
                attach.invoke(sourceView,geckoSession);
                if(read.invoke(sourceView)!=geckoSession)
                    throw new IllegalStateException("rollback_owner_not_restored");
            }catch(Throwable rollbackFailed){
                throw new IllegalStateException(
                    "gecko_handoff_rollback_failed_preserve_session",rollbackFailed);
            }
            throw new IllegalStateException("gecko_handoff_rolled_back_no_reload",
                attachFailure);
        }
    }
}
