package com.omnigate.integrated;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bootstrap after an actual foreground Activity resume, not during background startup. */
public final class CompatBootstrapProvider extends ContentProvider {
    private final AtomicBoolean requested=new AtomicBoolean(false);
    @Override public boolean onCreate(){
        Context context=getContext();
        if(context==null)return false;
        final Context appContext=context.getApplicationContext();
        GatewayApp.initialize(appContext);
        if(!(appContext instanceof Application))return false;
        ((Application)appContext).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
            @Override public void onActivityResumed(Activity a){
                if(!requested.compareAndSet(false,true))return;
                try{
                    Intent i=new Intent(a,SidecarService.class);
                    i.setAction(SidecarService.ACTION_ENSURE_RUNNING);
                    if(Build.VERSION.SDK_INT>=26)a.startForegroundService(i); else a.startService(i);
                }catch(Exception ex){
                    requested.set(false);
                    GatewayApp.setLocalApiStatus("启动受限: "+ex.getClass().getSimpleName());
                }
            }
            @Override public void onActivityCreated(Activity a,Bundle b){}
            @Override public void onActivityStarted(Activity a){}
            @Override public void onActivityPaused(Activity a){}
            @Override public void onActivityStopped(Activity a){}
            @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
            @Override public void onActivityDestroyed(Activity a){}
        });
        return true;
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sortOrder){return null;}
    @Override public String getType(Uri uri){return null;}
    @Override public Uri insert(Uri uri,ContentValues values){return null;}
    @Override public int delete(Uri uri,String where,String[] args){return 0;}
    @Override public int update(Uri uri,ContentValues values,String where,String[] args){return 0;}
}
