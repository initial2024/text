package com.omnigate.integrated;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * One in-app floating dock for BOTH original provider chooser and model
 * GeckoView host. No second launcher, automatic API service or global overlay.
 */
public final class CompatBootstrapProvider extends ContentProvider {
    private static boolean isOriginalUi(Activity a) {
        String cls=a.getClass().getName();
        return "com.deepseek2.app.ui.MainActivity".equals(cls)
            || "com.deepseek2.app.ui.ProviderActivity".equals(cls);
    }

    @Override public boolean onCreate() {
        Context ctx=getContext();
        if(ctx==null)return false;
        Context app=ctx.getApplicationContext();
        GatewayApp.initialize(app);
        // App-process only: restore localhost 8787 and a previously authorized
        // Relay without starting the frozen native model/runtime.
        AutoStartRuntime.onProcessCreated();
        if(!(app instanceof Application))return false;
        ((Application)app).registerActivityLifecycleCallbacks(
            new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityResumed(Activity a) {
                    if(isOriginalUi(a) ||
                        a instanceof CompatSettingsActivity ||
                        a instanceof OmniGateHomeActivity){
                        AutoStartRuntime.onActivityVisible(a);
                    }
                    if(!isOriginalUi(a))return;
                    a.runOnUiThread(()->{
                        NativeRelayPanel.attach(a);
                        LegacyUiSkin.apply(a);
                        ApiQuickPanel.show(a);
                        if("com.deepseek2.app.ui.MainActivity".equals(a.getClass().getName())) {
                            OriginalCoreBridge.onOriginalMainResumed(a);
                            // Never collapse the original overlay automatically.
                            // Hiding its window leaves its separate native
                            // ApiServer/WebDriver alive and caused confusing 409.
                            // Offer a single explicit, informed ownership choice.
                            a.getWindow().getDecorView().postDelayed(() -> {
                                if(!a.isFinishing() && !a.isDestroyed())
                                    OwnerGateUi.onMainResumed(a);
                            },800);
                        }
                    });
                }
                @Override public void onActivityPaused(Activity a) {
                    if(isOriginalUi(a))ApiQuickPanel.hide(a);
                }
                @Override public void onActivityDestroyed(Activity a) {
                    ApiQuickPanel.hide(a);
                    if("com.deepseek2.app.ui.MainActivity".equals(a.getClass().getName()))
                        OriginalCoreBridge.onOriginalMainDestroyed(a);
                }
                @Override public void onActivityCreated(Activity a,Bundle b) {}
                @Override public void onActivityStarted(Activity a) {}
                @Override public void onActivityStopped(Activity a) {}
                @Override public void onActivitySaveInstanceState(Activity a,Bundle b) {}
            });
        return true;
    }

    @Override public Cursor query(Uri uri,String[] projection,
            String selection,String[] selectionArgs,String sortOrder){return null;}
    @Override public String getType(Uri uri){return null;}
    @Override public Uri insert(Uri uri,ContentValues values){return null;}
    @Override public int delete(Uri uri,String selection,String[] args){return 0;}
    @Override public int update(Uri uri,ContentValues values,
            String selection,String[] args){return 0;}
}
