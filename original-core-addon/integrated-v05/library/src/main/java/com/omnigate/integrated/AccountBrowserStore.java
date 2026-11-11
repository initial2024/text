package com.omnigate.integrated;
import android.content.Context;
import android.content.SharedPreferences;

/** Private slot indexes and random Gecko context IDs only; no credentials. */
final class AccountBrowserStore {
    private static final String NAME="omnigate_isolated_accounts_v1";
    private AccountBrowserStore(){}
    private static SharedPreferences prefs(Context c){
        return c.getApplicationContext().getSharedPreferences(NAME,Context.MODE_PRIVATE);
    }
    static synchronized int count(Context c,String provider){
        if(!AccountBrowserSlots.supported(provider))throw new IllegalArgumentException("provider");
        int count=prefs(c).getInt(provider+".count",2);
        if(count<1||count>AccountBrowserSlots.MAX_SLOTS)
            throw new IllegalStateException("invalid_slot_count");
        return count;
    }
    /** Per-slot anti-tracking preference; does not change Gecko device identity. */
    /** OFF by default: a hidden floating GeckoSession may go inactive to
     * reduce memory, but can suspend media, JS activity and model streaming.
     * This only affects the explicitly opted-in saved account.
     */
    static synchronized boolean lowMemoryOnFold(Context c,String provider,int slot){
        return prefs(c).getBoolean(
            "low_memory_on_fold."+contextId(c,provider,slot),false);
    }
    static synchronized void setLowMemoryOnFold(Context c,String provider,
            int slot,boolean value){
        String id=contextId(c,provider,slot);
        if(!prefs(c).edit().putBoolean(
                "low_memory_on_fold."+id,value).commit())
            throw new IllegalStateException("fold_memory_setting_save_failed");
    }

    /** Compatibility-first opt-in: strict external-app/new-window navigation.
     * Identity is tied to the immutable Gecko context, not a shared provider.
     */
    static synchronized boolean strictNavigation(Context c,String provider,int slot){
        return prefs(c).getBoolean("strict_nav."+
            contextId(c,provider,slot),false);
    }
    static synchronized void setStrictNavigation(Context c,String provider,
            int slot,boolean enabled){
        String id=contextId(c,provider,slot);
        if(!prefs(c).edit().putBoolean("strict_nav."+id,enabled).commit())
            throw new IllegalStateException("strict_navigation_persist_failed");
    }

    static synchronized boolean trackingProtection(Context c,String provider,int slot){
        contextId(c,provider,slot);
        return prefs(c).getBoolean(provider+"."+slot+".tracking_protection",true);
    }
    static synchronized void setTrackingProtection(Context c,String provider,
            int slot,boolean enabled){
        contextId(c,provider,slot);
        if(!prefs(c).edit().putBoolean(provider+"."+slot+".tracking_protection",
                enabled).commit())
            throw new IllegalStateException("tracking_preference_not_saved");
    }
    /**
     * Never replace, reassign or silently repair a persisted Gecko context.
     * Before using a slot, ensure no OTHER saved slot has the same ID:
     * duplicate ownership could expose a second login to the first account.
     * A broken registry is read-only blocked, not auto-rotated.
     */
    private static void assertUniqueOwner(SharedPreferences p,String owner,
            int slot,String contextId){
        for(String provider:AccountBrowserSlots.PROVIDERS){
            int n=p.getInt(provider+".count",2);
            if(n<1||n>AccountBrowserSlots.MAX_SLOTS)
                throw new IllegalStateException("invalid_saved_slot_count");
            for(int i=0;i<n;i++){
                if(provider.equals(owner)&&i==slot)continue;
                String other=p.getString(provider+"."+i,null);
                if(contextId.equals(other))
                    throw new IllegalStateException("duplicate_gecko_context_owner");
            }
        }
    }
    static synchronized String contextId(Context c,String provider,int index){
        if(index<0||index>=count(c,provider))
            throw new IllegalArgumentException("slot_index");
        String key=provider+"."+index;
        SharedPreferences p=prefs(c);
        String value=p.getString(key,null);
        if(value!=null){
            if(!AccountBrowserSlots.validContextId(value))
                throw new IllegalStateException("invalid_stored_context");
            assertUniqueOwner(p,provider,index,value);
            return value;
        }
        // A previously selected account must never silently receive a NEW
        // context on restoration/corruption, leaving its login inaccessible.
        if(p.contains(provider+".selected") &&
            p.getInt(provider+".selected",-1)==index)
            throw new IllegalStateException("selected_login_context_missing_preserve_data");
        String created=AccountBrowserSlots.newContextId();
        assertUniqueOwner(p,provider,index,created);
        if(!p.edit().putString(key,created).commit())
            throw new IllegalStateException("context_persistence_failed");
        return created;
    }
    /**
     * Retire only the selected slot after the matching Gecko context has
     * been cleared. Keep its stable slot index and user-provided label.
     */
    static synchronized void rotateContext(Context c,String provider,
            int slot,String expectedOldId){
        String key=provider+"."+slot;
        String actual=contextId(c,provider,slot);
        if(!actual.equals(expectedOldId))
            throw new IllegalStateException("concurrent_context_change");
        String replacement=AccountBrowserSlots.newContextId();
        String usage=key+".user_budget_";
        if(!prefs(c).edit().putString(key,replacement)
                .remove(usage+"utc_day").remove(usage+"completed")
                .remove(key+".manual_hour_events").commit())
            throw new IllegalStateException("context_rotation_failed");
    }

    static synchronized int create(Context c,String provider){
        int n=count(c,provider);
        if(n>=AccountBrowserSlots.MAX_SLOTS)
            throw new IllegalStateException("slot_capacity_reached");
        SharedPreferences p=prefs(c);
        String key=provider+"."+n;
        // Preserve a valid orphaned slot ID from an interrupted legacy
        // two-stage create; do not discard potentially recoverable data.
        String saved=p.getString(key,null);
        if(saved!=null&&!AccountBrowserSlots.validContextId(saved))
            throw new IllegalStateException("orphaned_context_invalid_preserve_data");
        String id=saved!=null?saved:AccountBrowserSlots.newContextId();
        assertUniqueOwner(p,provider,n,id);
        // Single atomic preference transaction: capacity and context owner
        // cannot be persisted independently. Existing slots are untouched.
        if(!p.edit().putInt(provider+".count",n+1)
                .putString(key,id).commit())
            throw new IllegalStateException("slot_context_atomic_persistence_failed");
        return n;
    }
    static synchronized int selected(Context c,String provider){
        int index=prefs(c).getInt(provider+".selected",0);
        return index>=0&&index<count(c,provider)?index:0;
    }
    static synchronized void select(Context c,String provider,int index){
        contextId(c,provider,index);
        if(!prefs(c).edit().putInt(provider+".selected",index)
            .putString("last_provider",provider).commit())
            throw new IllegalStateException("selection_persistence_failed");
    }
    /** Reflects the last OPENED auxiliary Gecko context, never original 8787. */
    static synchronized String lastSelectedProvider(Context c){
        String provider=prefs(c).getString("last_provider","");
        return AccountBrowserSlots.supported(provider)?provider:"";
    }

    /** No guessed platform-safe count: feature OFF and budget unset by default. */
    static synchronized int voluntaryBudget(Context c,String provider){
        count(c,provider);
        int n=prefs(c).getInt(provider+".user_daily_budget",0);
        if(n<0||n>VoluntaryAccountBudget.MAX_USER_BUDGET)
            throw new IllegalStateException("budget_invalid");
        return n;
    }
    static synchronized void setVoluntaryBudget(Context c,String provider,int budget){
        count(c,provider);
        if(budget<1||budget>VoluntaryAccountBudget.MAX_USER_BUDGET)
            throw new IllegalArgumentException("budget");
        if(!prefs(c).edit().putInt(provider+".user_daily_budget",budget).commit())
            throw new IllegalStateException("budget_persist_failed");
    }
    /** Explicitly user-entered daily reference; never inferred from API RPM. */
    static synchronized int userDailyReference(Context c,String provider){
        count(c,provider);
        int value=prefs(c).getInt(provider+".reference_daily_turns",0);
        if(value<0||value>100000)
            throw new IllegalStateException("reference_daily_invalid");
        return value;
    }
    static synchronized void setUserDailyReference(Context c,String provider,int value){
        count(c,provider);
        if(value<2||value>100000)
            throw new IllegalArgumentException("reference_daily");
        if(!prefs(c).edit().putInt(provider+".reference_daily_turns",value).commit())
            throw new IllegalStateException("reference_daily_persist_failed");
    }
    /** User-owned local guard, not a claim about any web platform's rate limit. */
    static synchronized int hourlyBudget(Context c,String provider){
        count(c,provider);
        int n=prefs(c).getInt(provider+".user_hourly_budget",0);
        if(n<0||n>1000)throw new IllegalStateException("hourly_budget_invalid");
        return n;
    }
    static synchronized void setHourlyBudget(Context c,String provider,int n){
        count(c,provider);
        if(n<1||n>1000)throw new IllegalArgumentException("hourly_budget");
        if(!prefs(c).edit().putInt(provider+".user_hourly_budget",n).commit())
            throw new IllegalStateException("hourly_budget_persist_failed");
    }
    static synchronized int userHourlyReference(Context c,String provider){
        count(c,provider);
        int n=prefs(c).getInt(provider+".reference_hourly_turns",0);
        if(n<0||n>100000)throw new IllegalStateException("reference_hourly_invalid");
        return n;
    }
    static synchronized void setUserHourlyReference(Context c,String provider,int n){
        count(c,provider);
        if(n<2||n>100000)throw new IllegalArgumentException("reference_hourly");
        if(!prefs(c).edit().putInt(provider+".reference_hourly_turns",n).commit())
            throw new IllegalStateException("reference_hourly_persist_failed");
    }
    static synchronized boolean autoBudgetSwitch(Context c,String provider){
        count(c,provider);
        return prefs(c).getBoolean(provider+".user_auto_switch",false);
    }
    static synchronized void setAutoBudgetSwitch(Context c,String provider,boolean enabled){
        if(enabled&&voluntaryBudget(c,provider)<1&&hourlyBudget(c,provider)<1)
            throw new IllegalStateException("budget_not_set");
        if(!prefs(c).edit().putBoolean(provider+".user_auto_switch",enabled).commit())
            throw new IllegalStateException("auto_setting_persist_failed");
    }
    private static long utcDay(long nowMs) {
        return Math.floorDiv(nowMs,86_400_000L);
    }
    static synchronized int usesToday(Context c,String provider,int slot,long nowMs) {
        contextId(c,provider,slot);
        SharedPreferences p=prefs(c);
        String prefix=provider+"."+slot+".user_budget_";
        if(p.getLong(prefix+"utc_day",Long.MIN_VALUE)!=utcDay(nowMs))
            return 0;
        int value=p.getInt(prefix+"completed",0);
        if(value<0)throw new IllegalStateException("local_count_corrupt");
        return value;
    }
    /**
     * Called ONLY after the user explicitly marks a turn completed/idle.
     * It does not inspect provider cookies or infer platform request counts.
     */
    /** Sliding sixty-minute window of MANUALLY confirmed completed turns. */
    static synchronized int usesLastHour(Context c,String provider,int slot,long nowMs){
        contextId(c,provider,slot);
        return SlidingHourAcknowledgements.count(
            prefs(c).getString(provider+"."+slot+".manual_hour_events",""),nowMs);
    }
    static synchronized int[] allUsesLastHour(Context c,String provider,long nowMs){
        int[] used=new int[count(c,provider)];
        for(int i=0;i<used.length;i++)used[i]=usesLastHour(c,provider,i,nowMs);
        return used;
    }
    static synchronized int recordUserFinishedTurn(
            Context c,String provider,int slot,long nowMs) {
        int before=usesToday(c,provider,slot,nowMs);
        if(before==Integer.MAX_VALUE)
            throw new IllegalStateException("usage_counter_overflow");
        String prefix=provider+"."+slot+".user_budget_";
        String hourlyKey=provider+"."+slot+".manual_hour_events";
        String events=SlidingHourAcknowledgements.append(
            prefs(c).getString(hourlyKey,""),nowMs);
        if(!prefs(c).edit()
            .putLong(prefix+"utc_day",utcDay(nowMs))
            .putInt(prefix+"completed",before+1)
            .putString(hourlyKey,events).commit())
            throw new IllegalStateException("usage_persist_failed");
        return before+1;
    }
    static synchronized int[] allUsesToday(Context c,String provider,long nowMs){
        int[] used=new int[count(c,provider)];
        for(int i=0;i<used.length;i++)used[i]=usesToday(c,provider,i,nowMs);
        return used;
    }
}
