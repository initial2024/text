package com.omnigate.integrated;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;

/**
 * Local display-only labels and user-declared quota cooldowns.
 * No passwords, login tokens, model requests or network calls.
 */
final class AccountMetadataStore {
    private static final String PREFS="omnigate_account_labels_v1";
    private static final long HOUR_MS=3_600_000L;
    private AccountMetadataStore(){}
    private static SharedPreferences store(Context c){
        return c.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    private static String key(Context c,String provider,int slot){
        if(!AccountBrowserSlots.supported(provider)||
            slot<0||slot>=AccountBrowserStore.count(c,provider))
            throw new IllegalArgumentException("account_slot");
        return provider+"."+slot;
    }
    static String name(Context c,String provider,int slot){
        String s=store(c).getString(key(c,provider,slot)+".name","");
        return s.isEmpty()?"账号 "+(slot+1):s;
    }
    static String note(Context c,String provider,int slot){
        return store(c).getString(key(c,provider,slot)+".note","");
    }
    private static String sanitize(String raw,int max){
        if(raw==null)return "";
        String s=raw.replace('\n',' ').replace('\r',' ').replace('\t',' ').trim();
        if(s.length()>max)throw new IllegalArgumentException("label_too_long");
        for(int i=0;i<s.length();i++)if(Character.isISOControl(s.charAt(i)))
            throw new IllegalArgumentException("invalid_character");
        return s;
    }
    static void setNameAndNote(Context c,String provider,int slot,String name,String note){
        String k=key(c,provider,slot);
        String n=sanitize(name,28),m=sanitize(note,140);
        if(!store(c).edit().putString(k+".name",n).putString(k+".note",m).commit())
            throw new IllegalStateException("metadata_save_failed");
    }
    static long quotaUntil(Context c,String provider,int slot,long now){
        String key=key(c,provider,slot)+".until";
        long until=store(c).getLong(key,0L);
        if(until<=now){
            if(until!=0)store(c).edit().remove(key).commit();
            return 0;
        }
        return until;
    }
    static void markQuota(Context c,String provider,int slot,int hours,long now){
        String key=key(c,provider,slot)+".until";
        if(hours<0||hours>72)throw new IllegalArgumentException("duration_out_of_range");
        SharedPreferences.Editor e=store(c).edit();
        if(hours==0)e.remove(key);
        else e.putLong(key,Math.addExact(now,HOUR_MS*hours));
        if(!e.commit())throw new IllegalStateException("quota_marker_save_failed");
    }
    static String status(Context c,String provider,int slot,long now){
        long until=quotaUntil(c,provider,slot,now);
        if(until<=now)return "";
        long minutes=(until-now+59_999L)/60_000L;
        return "额度已无 · 约"+(minutes>=60?(minutes/60)+"小时"+(minutes%60)+"分":minutes+"分钟")+"后恢复";
    }
    /** Fixed identity; sorting affects only display order, never Gecko IDs. */
    static int[] sorted(Context c,String provider,long now){
        int n=AccountBrowserStore.count(c,provider);
        Integer[] slots=new Integer[n];
        for(int i=0;i<n;i++)slots[i]=i;
        Arrays.sort(slots,(a,b)->{
            boolean x=quotaUntil(c,provider,a,now)>0;
            boolean y=quotaUntil(c,provider,b,now)>0;
            return x==y?Integer.compare(a,b):(x?1:-1);
        });
        int[] out=new int[n];
        for(int i=0;i<n;i++)out[i]=slots[i];
        return out;
    }
}
