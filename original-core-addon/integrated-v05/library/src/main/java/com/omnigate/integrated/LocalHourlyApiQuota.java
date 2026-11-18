package com.omnigate.integrated;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Optional, user-owned per-provider local request admission budget. It counts
 * attempts which reached an actual outbound submission path, not platform
 * totals, and has NO relation to website usage or official API token counts.
 * Default OFF. Official/401/403/429 provider risk gates remain authoritative.
 */
final class LocalHourlyApiQuota {
    static final int MAX_LIMIT=500;
    private static final String PREF="omnigate_local_api_hour_01";
    static final class Decision {
        final boolean allowed;
        final String code;
        final long retrySeconds;
        final int count,limit;
        Decision(boolean allowed,String code,long retrySeconds,int count,int limit){
            this.allowed=allowed;this.code=code;this.retrySeconds=retrySeconds;
            this.count=count;this.limit=limit;
        }
    }
    private LocalHourlyApiQuota(){}
    private static SharedPreferences pref(Context c){
        if(c==null)throw new IllegalStateException("app_context_missing");
        return c.getApplicationContext().getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }
    private static void valid(String provider){
        if(!"claude".equals(provider)&&!AccountBrowserSlots.supported(provider))
            throw new IllegalArgumentException("unrecognized_provider");
    }
    static synchronized int limit(Context c,String provider){
        valid(provider);
        int n=pref(c).getInt(provider+".limit",0);
        if(n<0||n>MAX_LIMIT)throw new IllegalStateException("local_hour_limit_invalid");
        return n;
    }
    static synchronized void setLimit(Context c,String provider,int limit){
        valid(provider);
        if(limit<0||limit>MAX_LIMIT)
            throw new IllegalArgumentException("local_hour_limit");
        if(!pref(c).edit().putInt(provider+".limit",limit).commit())
            throw new IllegalStateException("local_hour_limit_persist");
    }
    static synchronized int used(Context c,String provider,long now){
        valid(provider);
        return SlidingHourAcknowledgements.count(
            pref(c).getString(provider+".events",""),now);
    }
    /** Only locally admitted 8787 API submissions, counted automatically.
     * This is not a website counter, official rate limit, or token balance. */
    static synchronized int observed(Context c,String provider,long now){
        valid(provider);
        return SlidingHourAcknowledgements.count(
            pref(c).getString(provider+".observed_events",""),now);
    }
    private static void noteObserved(SharedPreferences preferences,String provider,long now){
        String key=provider+".observed_events";
        String history=preferences.getString(key,"");
        preferences.edit().putString(key,
            SlidingHourAcknowledgements.append(history,now)).apply();
    }
    static synchronized Decision admit(Context c,String provider,long now){
        valid(provider);
        SharedPreferences prefs=pref(c);
        int configured=limit(c,provider);
        if(configured==0){
            // No network probe and no added quota gate. Best-effort numeric
            // ledger only after the original trusted submission path reaches us.
            try{noteObserved(prefs,provider,now);}
            catch(RuntimeException unavailable){/* preserve disabled-budget behavior */}
            return new Decision(true,"disabled",0,0,0);
        }
        String key=provider+".events",events=prefs.getString(key,"");
        int count=SlidingHourAcknowledgements.count(events,now);
        if(count>=configured)
            return new Decision(false,"local_user_hour_request_limit",
                SlidingHourAcknowledgements.retryAfterSeconds(events,now),
                count,configured);
        String next=SlidingHourAcknowledgements.append(events,now);
        String observedKey=provider+".observed_events";
        String observedNext=SlidingHourAcknowledgements.append(
            prefs.getString(observedKey,""),now);
        if(!prefs.edit().putString(key,next)
            .putString(observedKey,observedNext).commit())
            throw new IllegalStateException("local_hour_count_persist_failed");
        return new Decision(true,"admitted",0,count+1,configured);
    }
}
