package com.omnigate.integrated;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Local display-only labels and user-declared quota cooldowns.
 * No passwords, login tokens, model requests or network calls.
 */
final class AccountMetadataStore {
    private static final String PREFS="omnigate_account_labels_v1";
    private static final long HOUR_MS=3_600_000L;
    private static final String PRESETS_KEY="quota_presets";
    private static final int MAX_PRESET_COUNT=16;
    private static final String DEFAULT_QUOTA_TAG="额度已无";
    private static final String[] BUILTIN_TAGS={
        DEFAULT_QUOTA_TAG,"等待恢复","暂不可用"
    };
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

    /** Built-in choices plus user-managed reusable labels, stored only locally. */
    static synchronized String[] quotaTagOptions(Context c){
        Set<String> tags=new LinkedHashSet<>();
        Collections.addAll(tags,BUILTIN_TAGS);
        Set<String> custom=store(c).getStringSet(PRESETS_KEY,Collections.emptySet());
        ArrayList<String> ordered=new ArrayList<>(custom);
        Collections.sort(ordered);
        tags.addAll(ordered);
        return tags.toArray(new String[0]);
    }
    static synchronized void saveQuotaTagPreset(Context c,String text){
        String tag=sanitize(text,24);
        if(tag.isEmpty())throw new IllegalArgumentException("empty_tag");
        for(String built:BUILTIN_TAGS)if(built.equals(tag))return;
        Set<String> tags=new LinkedHashSet<>(
            store(c).getStringSet(PRESETS_KEY,Collections.emptySet()));
        if(!tags.contains(tag)&&tags.size()>=MAX_PRESET_COUNT)
            throw new IllegalStateException("too_many_quota_tags");
        tags.add(tag);
        if(!store(c).edit().putStringSet(PRESETS_KEY,tags).commit())
            throw new IllegalStateException("preset_save_failed");
    }
    static synchronized void deleteQuotaTagPreset(Context c,String text){
        String tag=sanitize(text,24);
        Set<String> tags=new LinkedHashSet<>(
            store(c).getStringSet(PRESETS_KEY,Collections.emptySet()));
        if(tags.remove(tag)&&
            !store(c).edit().putStringSet(PRESETS_KEY,tags).commit())
            throw new IllegalStateException("preset_delete_failed");
        // Removing a reusable choice doesn't rewrite existing account status.
    }
    static String quotaLabel(Context c,String provider,int slot,long now){
        long until=quotaUntil(c,provider,slot,now);
        if(until==0)return "";
        String value=store(c).getString(key(c,provider,slot)+".quota_label","");
        return value.isEmpty()?DEFAULT_QUOTA_TAG:value;
    }
    /**
     * 0 means no active label, -1 means manually cleared indefinite timer,
     * positive timestamps expire on first UI read without a background alarm.
     */
    static long quotaUntil(Context c,String provider,int slot,long now){
        String base=key(c,provider,slot);
        long until=store(c).getLong(base+".until",0L);
        if(until==-1L)return -1L;
        if(until==0L)return 0L;
        if(until<=now){
            store(c).edit().remove(base+".until")
                .remove(base+".quota_label").commit();
            return 0L;
        }
        return until;
    }
    static boolean hasQuotaMark(Context c,String provider,int slot,long now){
        return quotaUntil(c,provider,slot,now)!=0L;
    }
    /**
     * hours: -1 = clear; 0 = indefinite label; 1..72 = timed label.
     * A label is a manual note, NEVER a platform quota read or auto-switch.
     */
    static void setQuotaMark(Context c,String provider,int slot,String text,int hours,long now){
        if(hours< -1||hours>72)throw new IllegalArgumentException("duration_out_of_range");
        String base=key(c,provider,slot);
        SharedPreferences.Editor e=store(c).edit();
        if(hours==-1){
            e.remove(base+".until").remove(base+".quota_label");
        }else{
            String label=sanitize(text,24);
            if(label.isEmpty())throw new IllegalArgumentException("empty_quota_tag");
            e.putString(base+".quota_label",label);
            e.putLong(base+".until",hours==0?-1L:Math.addExact(now,HOUR_MS*hours));
        }
        if(!e.commit())throw new IllegalStateException("quota_marker_save_failed");
    }
    /** Compatibility entry point for the original hourly presets. */
    static void markQuota(Context c,String provider,int slot,int hours,long now){
        setQuotaMark(c,provider,slot,DEFAULT_QUOTA_TAG,hours==0?-1:hours,now);
    }
    static String status(Context c,String provider,int slot,long now){
        long until=quotaUntil(c,provider,slot,now);
        if(until==0)return "";
        String label=quotaLabel(c,provider,slot,now);
        if(until==-1)return label+" · 手动解除";
        long minutes=(until-now+59_999L)/60_000L;
        return label+" · 约"+(minutes>=60?
            (minutes/60)+"小时"+(minutes%60)+"分":minutes+"分钟")+"后恢复";
    }
    /** Fixed identity; sorting affects only display order, never Gecko IDs. */
    static int[] sorted(Context c,String provider,long now){
        int n=AccountBrowserStore.count(c,provider);
        Integer[] slots=new Integer[n];
        for(int i=0;i<n;i++)slots[i]=i;
        Arrays.sort(slots,(a,b)->{
            boolean x=hasQuotaMark(c,provider,a,now);
            boolean y=hasQuotaMark(c,provider,b,now);
            return x==y?Integer.compare(a,b):(x?1:-1);
        });
        int[] out=new int[n];
        for(int i=0;i<n;i++)out[i]=slots[i];
        return out;
    }
}
