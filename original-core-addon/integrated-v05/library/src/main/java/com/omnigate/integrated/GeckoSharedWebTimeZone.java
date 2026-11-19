package com.omnigate.integrated;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * OGTZ02: fail-closed after the owner's Android 16 Gecko BrowserLeaks test.
 *
 * In v1.3.16 process-level TZ assignment was confirmed INEFFECTIVE for website
 * JavaScript Date/Intl. Never reapply that process-wide experiment at boot.
 * Gecko's web-visible timezone is NOT known from any Android process env.
 *
 * Keep the persisted IANA preference only so old installs can safely migrate
 * or reset; do not touch the original shared GeckoRuntime, Android OS clock,
 * Java default timezone, web workers, cookies, profile data or auth clocks.
 */
final class GeckoSharedWebTimeZone {
    private static final String PREFS="omnigate_gecko_shared_website_tz_v1";
    private static final String KEY="requested_timezone";
    private static volatile String currentBoot="NOT_APPLIED";
    private static volatile String applied="NO_OVERRIDE";
    private GeckoSharedWebTimeZone(){}

    private static SharedPreferences preferences(Context c){
        return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    static String requested(Context c){
        String raw=preferences(c).getString(KEY,"SYSTEM");
        return AccountClockFormat.validate(raw);
    }
    static synchronized void setRequested(Context c,String iana){
        String chosen=AccountClockFormat.validate(iana);
        if(!preferences(c).edit().putString(KEY,chosen).commit())
            throw new IllegalStateException("global_website_tz_save_failed");
        // Persist for explicit reset/migration only; NEVER enable JS timezone
        // changes using a native display-clock preference.
    }
    static String bootStatus(){return currentBoot;}
    static String appliedAtBootstrap(){return applied;}

    /** Does not set TZ or create/modify a Gecko runtime. */
    static synchronized void applyAtAppBootstrap(Context c){
        applied="NO_OVERRIDE";
        if(c==null){currentBoot="NO_CONTEXT_FAIL_CLOSED";return;}
        try{
            String saved=requested(c);
            currentBoot="SYSTEM".equals(saved)
                ?"NATIVE_GECKO_DEFAULT_NO_OVERRIDE"
                :"STALE_EXPERIMENT_IGNORED_REAL_DEVICE_DATE_INTL_FAILED";
        }catch(RuntimeException corrupt){
            currentBoot="CORRUPT_PREFERENCE_IGNORED_NO_OVERRIDE";
        }
    }
}
