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
    static synchronized String contextId(Context c,String provider,int index){
        if(index<0||index>=count(c,provider))
            throw new IllegalArgumentException("slot_index");
        String key=provider+"."+index;
        SharedPreferences p=prefs(c);
        String value=p.getString(key,null);
        if(value!=null){
            if(!AccountBrowserSlots.validContextId(value))
                throw new IllegalStateException("invalid_stored_context");
            return value;
        }
        String created=AccountBrowserSlots.newContextId();
        if(!p.edit().putString(key,created).commit())
            throw new IllegalStateException("context_persistence_failed");
        return created;
    }
    static synchronized int create(Context c,String provider){
        int n=count(c,provider);
        if(n>=AccountBrowserSlots.MAX_SLOTS)
            throw new IllegalStateException("slot_capacity_reached");
        if(!prefs(c).edit().putInt(provider+".count",n+1).commit())
            throw new IllegalStateException("slot_persistence_failed");
        contextId(c,provider,n);
        return n;
    }
    static synchronized int selected(Context c,String provider){
        int index=prefs(c).getInt(provider+".selected",0);
        return index>=0&&index<count(c,provider)?index:0;
    }
    static synchronized void select(Context c,String provider,int index){
        contextId(c,provider,index);
        if(!prefs(c).edit().putInt(provider+".selected",index).commit())
            throw new IllegalStateException("selection_persistence_failed");
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
    static synchronized boolean autoBudgetSwitch(Context c,String provider){
        count(c,provider);
        return prefs(c).getBoolean(provider+".user_auto_switch",false);
    }
    static synchronized void setAutoBudgetSwitch(Context c,String provider,boolean enabled){
        if(enabled&&voluntaryBudget(c,provider)<1)
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
    static synchronized int recordUserFinishedTurn(
            Context c,String provider,int slot,long nowMs) {
        int before=usesToday(c,provider,slot,nowMs);
        if(before==Integer.MAX_VALUE)
            throw new IllegalStateException("usage_counter_overflow");
        String prefix=provider+"."+slot+".user_budget_";
        if(!prefs(c).edit()
            .putLong(prefix+"utc_day",utcDay(nowMs))
            .putInt(prefix+"completed",before+1).commit())
            throw new IllegalStateException("usage_persist_failed");
        return before+1;
    }
    static synchronized int[] allUsesToday(Context c,String provider,long nowMs){
        int[] used=new int[count(c,provider)];
        for(int i=0;i<used.length;i++)used[i]=usesToday(c,provider,i,nowMs);
        return used;
    }
}
