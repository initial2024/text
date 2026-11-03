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
}
