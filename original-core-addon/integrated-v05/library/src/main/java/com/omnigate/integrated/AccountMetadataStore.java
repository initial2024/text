package com.omnigate.integrated;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Two independent local concepts:
 * 1. Permanent account tags, names and notes.
 * 2. One temporary quota-status preset = label + recovery duration.
 * Never measures real platform quota or switches/sends requests automatically.
 */
final class AccountMetadataStore {
    private static final String PREFS="omnigate_account_labels_v1";
    private static final String QUOTA_PRESETS="quota_presets";
    private static final String DEFAULT_QUOTA_TAG="额度已无";
    private static final long HOUR_MS=3_600_000L;
    private static final int MAX_PRESETS=16;
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
    private static String sanitize(String raw,int max){
        if(raw==null)return "";
        String s=raw.replace('\n',' ').replace('\r',' ').replace('\t',' ').trim();
        if(s.length()>max)throw new IllegalArgumentException("label_too_long");
        for(int i=0;i<s.length();i++)if(Character.isISOControl(s.charAt(i)))
            throw new IllegalArgumentException("invalid_character");
        return s;
    }
    static String name(Context c,String provider,int slot){
        String s=store(c).getString(key(c,provider,slot)+".name","");
        return s.isEmpty()?"账号 "+(slot+1):s;
    }
    static String note(Context c,String provider,int slot){
        return store(c).getString(key(c,provider,slot)+".note","");
    }
    static void setNameAndNote(Context c,String provider,int slot,String name,String note){
        String k=key(c,provider,slot);
        String n=sanitize(name,28),m=sanitize(note,140);
        if(!store(c).edit().putString(k+".name",n).putString(k+".note",m).commit())
            throw new IllegalStateException("metadata_save_failed");
    }
    /** Account tags persist independently of any quota/cooldown. Up to four tags. */
    static String accountTags(Context c,String provider,int slot){
        return store(c).getString(key(c,provider,slot)+".account_tags","");
    }
    private static String normalizeAccountTags(String raw){
        String text=sanitize(raw,100);
        Set<String> tags=new LinkedHashSet<>();
        if(!text.isEmpty()){
            for(String part:text.split("[,，、;；]+")){
                String tag=sanitize(part,16);
                if(tag.isEmpty())continue;
                tags.add(tag);
                if(tags.size()>4)throw new IllegalArgumentException("too_many_account_tags");
            }
        }
        return android.text.TextUtils.join("、",tags);
    }
    static void setAccountTags(Context c,String provider,int slot,String raw){
        String canonical=normalizeAccountTags(raw);
        if(!store(c).edit().putString(key(c,provider,slot)+".account_tags",canonical)
                .commit())throw new IllegalStateException("account_tags_save_failed");
    }
    /** One transaction: an invalid tag cannot partially rename the account. */
    static void setAccountInfo(Context c,String provider,int slot,
            String name,String note,String tags){
        String k=key(c,provider,slot);
        String n=sanitize(name,28),m=sanitize(note,140);
        String cleanTags=normalizeAccountTags(tags);
        if(!store(c).edit().putString(k+".name",n)
                .putString(k+".note",m).putString(k+".account_tags",cleanTags)
                .commit())throw new IllegalStateException("account_info_save_failed");
    }
    static boolean hasAccountTag(Context c,String provider,int slot,String filter){
        String query=filter==null?"":filter.trim();
        if(query.isEmpty())return true;
        for(String tag:accountTags(c,provider,slot).split("、"))
            if(tag.trim().toLowerCase(Locale.ROOT).equals(query.toLowerCase(Locale.ROOT)))
                return true;
        return false;
    }
    static final class QuotaPreset {
        final String label;
        final int hours; // 0 = until manually cleared, 1..72 = local timer
        QuotaPreset(String label,int hours){this.label=label;this.hours=hours;}
        String title(){
            return label+" · "+(hours==0?"手动解除":hours+"小时后恢复");
        }
        String id(){return label+"\u001F"+hours;}
    }
    private static final QuotaPreset[] DEFAULT_PRESETS={
        new QuotaPreset(DEFAULT_QUOTA_TAG,1),
        new QuotaPreset(DEFAULT_QUOTA_TAG,3),
        new QuotaPreset(DEFAULT_QUOTA_TAG,5),
        new QuotaPreset(DEFAULT_QUOTA_TAG,12),
        new QuotaPreset(DEFAULT_QUOTA_TAG,24),
        new QuotaPreset("等待恢复",0)
    };
    static int builtInQuotaPresetCount(){return DEFAULT_PRESETS.length;}
    private static QuotaPreset parsePreset(String encoded){
        // Backwards-compatible with earlier name-only preset values:
        // an old label becomes a 5-hour pair, rather than being discarded.
        int cut=encoded.lastIndexOf('\u001F');
        String label;
        int hours=5;
        if(cut<0)label=encoded;
        else{
            label=encoded.substring(0,cut);
            try{hours=Integer.parseInt(encoded.substring(cut+1));}
            catch(NumberFormatException invalid){return null;}
        }
        try{
            label=sanitize(label,24);
            if(label.isEmpty()||hours<0||hours>72)return null;
            return new QuotaPreset(label,hours);
        }catch(RuntimeException invalid){return null;}
    }
    static synchronized QuotaPreset[] quotaPresets(Context c){
        ArrayList<QuotaPreset> out=new ArrayList<>(Arrays.asList(DEFAULT_PRESETS));
        Set<String> saved=store(c).getStringSet(QUOTA_PRESETS,Collections.emptySet());
        ArrayList<String> ordered=new ArrayList<>(saved);
        Collections.sort(ordered);
        Set<String> seen=new LinkedHashSet<>();
        for(QuotaPreset preset:DEFAULT_PRESETS)seen.add(preset.id());
        for(String raw:ordered){
            QuotaPreset preset=parsePreset(raw);
            if(preset!=null&&seen.add(preset.id()))out.add(preset);
        }
        return out.toArray(new QuotaPreset[0]);
    }
    static synchronized void saveQuotaPreset(Context c,String name,int hours){
        String label=sanitize(name,24);
        if(label.isEmpty()||hours<0||hours>72)
            throw new IllegalArgumentException("invalid_quota_preset");
        QuotaPreset next=new QuotaPreset(label,hours);
        for(QuotaPreset builtIn:DEFAULT_PRESETS)
            if(builtIn.id().equals(next.id()))return;
        Set<String> saved=new LinkedHashSet<>(
            store(c).getStringSet(QUOTA_PRESETS,Collections.emptySet()));
        String encoded=next.id();
        if(!saved.contains(encoded)&&saved.size()>=MAX_PRESETS)
            throw new IllegalStateException("too_many_quota_presets");
        saved.add(encoded);
        if(!store(c).edit().putStringSet(QUOTA_PRESETS,saved).commit())
            throw new IllegalStateException("preset_save_failed");
    }
    static synchronized void deleteQuotaPreset(Context c,QuotaPreset preset){
        Set<String> saved=new LinkedHashSet<>(
            store(c).getStringSet(QUOTA_PRESETS,Collections.emptySet()));
        boolean changed=saved.remove(preset.id());
        // Also allow removal of older name-only presets.
        if(preset.hours==5)changed=saved.remove(preset.label)||changed;
        if(changed&&!store(c).edit().putStringSet(QUOTA_PRESETS,saved).commit())
            throw new IllegalStateException("preset_delete_failed");
        // Removing a saved choice NEVER edits any account's active mark.
    }
    /**
     * 0=no quota mark, -1=indefinite, positive millis=expiry.
     * Expired status and its label are removed lazily on app/UI read.
     * Account name, note and account tags are NEVER touched.
     */
    static long quotaUntil(Context c,String provider,int slot,long now){
        String base=key(c,provider,slot);
        long until=store(c).getLong(base+".until",0L);
        if(until==-1L||until==0L)return until;
        if(until<=now){
            store(c).edit().remove(base+".until").remove(base+".quota_label").commit();
            return 0L;
        }
        return until;
    }
    static boolean hasQuotaMark(Context c,String provider,int slot,long now){
        return quotaUntil(c,provider,slot,now)!=0L;
    }
    static String quotaLabel(Context c,String provider,int slot,long now){
        if(!hasQuotaMark(c,provider,slot,now))return "";
        String saved=store(c).getString(key(c,provider,slot)+".quota_label","");
        return saved.isEmpty()?DEFAULT_QUOTA_TAG:saved;
    }
    /** -1 clears; 0 manually cleared later; 1..72 hours timed. */
    static void setQuotaMark(Context c,String provider,int slot,String text,int hours,long now){
        if(hours< -1||hours>72)throw new IllegalArgumentException("invalid_hours");
        String base=key(c,provider,slot);
        SharedPreferences.Editor edit=store(c).edit();
        if(hours==-1)edit.remove(base+".until").remove(base+".quota_label");
        else{
            String label=sanitize(text,24);
            if(label.isEmpty())throw new IllegalArgumentException("empty_quota_tag");
            edit.putString(base+".quota_label",label);
            edit.putLong(base+".until",hours==0?-1L:Math.addExact(now,HOUR_MS*hours));
        }
        if(!edit.commit())throw new IllegalStateException("quota_mark_save_failed");
    }
    /** Existing entry point; called explicitly only, never by a timer. */
    static void markQuota(Context c,String provider,int slot,int hours,long now){
        setQuotaMark(c,provider,slot,DEFAULT_QUOTA_TAG,hours==0?-1:hours,now);
    }
    static String status(Context c,String provider,int slot,long now){
        long until=quotaUntil(c,provider,slot,now);
        if(until==0L)return "";
        String label=quotaLabel(c,provider,slot,now);
        if(until==-1L)return label+" · 手动解除";
        long minutes=(until-now+59_999L)/60_000L;
        return label+" · 约"+(minutes>=60?
            (minutes/60)+"小时"+(minutes%60)+"分":minutes+"分钟")+"后恢复";
    }
    /** Status affects *display order* only. Never remaps a Gecko context ID. */
    static int[] sorted(Context c,String provider,long now){
        int n=AccountBrowserStore.count(c,provider);
        Integer[] ids=new Integer[n];
        for(int i=0;i<n;i++)ids[i]=i;
        Arrays.sort(ids,(a,b)->{
            boolean x=hasQuotaMark(c,provider,a,now);
            boolean y=hasQuotaMark(c,provider,b,now);
            return x==y?Integer.compare(a,b):(x?1:-1);
        });
        int[] sorted=new int[n];
        for(int i=0;i<n;i++)sorted[i]=ids[i];
        return sorted;
    }
}