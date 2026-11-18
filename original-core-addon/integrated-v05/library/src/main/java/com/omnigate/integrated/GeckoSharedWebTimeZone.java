package com.omnigate.integrated;

import android.content.Context;
import android.content.SharedPreferences;
import android.system.ErrnoException;
import android.system.Os;

/**
 * OGTZ01 experimental EARLY process-scoped TZ environment preference.
 *
 * NOT a Gecko per-session timeZone API. Gecko's JS/Intl may ignore the
 * environment, and all Gecko sessions using the original process/runtime
 * (including its original main browser) can be affected.
 *
 * Apply ONLY at app-process bootstrap, before its original GeckoRuntime
 * is normally created. Never dynamically change TZ for live Gecko threads.
 * Never modify System.currentTimeMillis, android.util.DateFormat, global
 * java.util.TimeZone defaults, OS time, cookies or login timestamps.
 */
final class GeckoSharedWebTimeZone {
    private static final String PREFS="omnigate_gecko_shared_website_tz_v1";
    private static final String KEY="requested_timezone";
    private static volatile String currentBoot="NOT_APPLIED";
    private static volatile String applied="SYSTEM";
    private GeckoSharedWebTimeZone(){}

    private static SharedPreferences preferences(Context c){
        return c.getApplicationContext().getSharedPreferences(
            PREFS,Context.MODE_PRIVATE);
    }
    static String requested(Context c){
        String raw=preferences(c).getString(KEY,"SYSTEM");
        // A corrupt preference cannot cause an arbitrary TZ injection.
        return AccountClockFormat.validate(raw);
    }
    static synchronized void setRequested(Context c,String iana){
        String chosen=AccountClockFormat.validate(iana);
        if(!preferences(c).edit().putString(KEY,chosen).commit())
            throw new IllegalStateException("global_website_tz_save_failed");
        // The current runtime is intentionally NOT changed. No automatic
        // process kill: it could corrupt open drafts, cookies or uploads.
    }
    static String bootStatus(){return currentBoot;}
    static String appliedAtBootstrap(){return applied;}

    /**
     * Run before Gateway startup, activity launch and Gecko initialization.
     * A call from an already-running Gecko process is never supported.
     */
    static synchronized void applyAtAppBootstrap(Context c){
        if(c==null){currentBoot="NO_CONTEXT";return;}
        final String chosen;
        try{chosen=requested(c);}
        catch(RuntimeException corrupt){
            currentBoot="INVALID_SAVED_TIMEZONE_NO_CHANGE";
            applied="SYSTEM";
            return;
        }
        if("SYSTEM".equals(chosen)){
            applied="SYSTEM";
            currentBoot="SYSTEM_DEFAULT_NO_ENV_MODIFICATION";
            return;
        }
        String previous=null;
        try{
            previous=Os.getenv("TZ");
            Os.setenv("TZ",chosen,true);
            String readback=Os.getenv("TZ");
            if(!chosen.equals(readback))
                throw new IllegalStateException("tz_environment_readback_failed");
            applied=chosen;
            currentBoot="PROCESS_ENV_SET_GECKO_JS_UNVERIFIED";
        }catch(ErrnoException|SecurityException|LinkageError|
                IllegalStateException problem){
            // Restore the previous process environment if setenv succeeded
            // but readback failed. Never leave a silent halfway success.
            try{
                if(previous==null)Os.unsetenv("TZ");
                else Os.setenv("TZ",previous,true);
            }catch(Throwable ignored){}
            applied="SYSTEM";
            currentBoot="PROCESS_ENV_REJECTED_GECKO_UNCHANGED_UNVERIFIED";
        }
    }
}
