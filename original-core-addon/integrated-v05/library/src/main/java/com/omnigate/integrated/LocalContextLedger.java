package com.omnigate.integrated;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Opt-in manual, privacy-preserving conversation context-size notes.
 * No website DOM inspection, API requests, clipboard reading or chat text
 * persistence. Android private preferences are scoped to its own OS user.
 */
final class LocalContextLedger {
    private static final String FILE="omnigate_user_owned_context_notes_v1";
    private LocalContextLedger(){}
    private static SharedPreferences preferences(Context c){
        return c.getApplicationContext().getSharedPreferences(FILE,Context.MODE_PRIVATE);
    }
    private static String key(Context c,String provider,int slot){
        if(!AccountBrowserSlots.supported(provider)||slot<0||
            slot>=AccountBrowserStore.count(c,provider))
            throw new IllegalArgumentException("invalid_login_slot");
        return "context."+AccountBrowserStore.contextId(c,provider,slot);
    }

    /** Statistics are OFF until the user explicitly opts in for this ID.
     * Existing numeric notes (from older builds) are not silently enabled.
     * Disabling never destroys Gecko cookies, sessions or account registry.
     */
    static synchronized boolean enabled(Context c,String provider,int slot){
        return preferences(c).getBoolean(key(c,provider,slot)+".enabled",false);
    }
    static synchronized void setEnabled(Context c,String provider,int slot,boolean value){
        String k=key(c,provider,slot);
        SharedPreferences.Editor e=preferences(c).edit().putBoolean(k+".enabled",value);
        if(!value)e.putBoolean(k+".include_provider",false);
        if(!e.commit())throw new IllegalStateException("context_opt_in_save_failed");
    }
    static synchronized boolean providerAggregationEnabled(Context c,String provider){
        if(!AccountBrowserSlots.supported(provider))
            throw new IllegalArgumentException("provider");
        return preferences(c).getBoolean("provider."+provider+".aggregate",false);
    }
    static synchronized void setProviderAggregationEnabled(Context c,String provider,
            boolean value){
        if(!AccountBrowserSlots.supported(provider))
            throw new IllegalArgumentException("provider");
        SharedPreferences.Editor edit=preferences(c).edit()
            .putBoolean("provider."+provider+".aggregate",value);
        if(!value){
            // Revocation must be sticky. Re-enabling the provider does not
            // silently reactivate accounts that consented months ago.
            SharedPreferences saved=c.getApplicationContext().getSharedPreferences(
                "omnigate_isolated_accounts_v1",Context.MODE_PRIVATE);
            int n=AccountBrowserStore.count(c,provider);
            for(int i=0;i<n;i++){
                String id=saved.getString(provider+"."+i,null);
                if(id!=null&&AccountBrowserSlots.validContextId(id))
                    edit.putBoolean("context."+id+".include_provider",false);
            }
        }
        if(!edit.commit())throw new IllegalStateException("provider_aggregation_save_failed");
    }
    static synchronized boolean participates(Context c,String provider,int slot){
        String k=key(c,provider,slot);
        return enabled(c,provider,slot)&&
            preferences(c).getBoolean(k+".include_provider",false);
    }
    static synchronized void setParticipates(Context c,String provider,int slot,
            boolean value){
        String k=key(c,provider,slot);
        if(value&&!enabled(c,provider,slot))
            throw new IllegalStateException("enable_account_statistics_first");
        if(value&&!providerAggregationEnabled(c,provider))
            throw new IllegalStateException("enable_provider_aggregation_first");
        if(!preferences(c).edit().putBoolean(k+".include_provider",value).commit())
            throw new IllegalStateException("account_participation_save_failed");
    }
    /** Provider aggregate is an explicitly consented VIEW over numeric records
     * ONLY. It never reads Gecko DOM, account cookies, notes or chat text.
     * An uninitialized slot must not get a new Gecko context ID here.
     */
    static synchronized java.util.List<Contribution> contributions(Context c,
            String provider){
        if(!providerAggregationEnabled(c,provider))
            return java.util.Collections.emptyList();
        java.util.ArrayList<Contribution> list=new java.util.ArrayList<>();
        android.content.SharedPreferences registry=c.getApplicationContext()
            .getSharedPreferences("omnigate_isolated_accounts_v1",Context.MODE_PRIVATE);
        java.util.HashSet<String> seen=new java.util.HashSet<>();
        int n=AccountBrowserStore.count(c,provider);
        for(int i=0;i<n;i++){
            String id=registry.getString(provider+"."+i,null);
            if(id==null)continue;
            if(!AccountBrowserSlots.validContextId(id)||!seen.add(id))
                throw new IllegalStateException("unsafe_provider_context_registry");
            String k="context."+id;
            SharedPreferences pref=preferences(c);
            if(pref.getBoolean(k+".enabled",false)&&
               pref.getBoolean(k+".include_provider",false)){
                long tokens=pref.getLong(k+".estimated",0);
                int window=pref.getInt(k+".window",0);
                if(tokens<0||window<0)
                    throw new IllegalStateException("invalid_local_count");
                list.add(new Contribution(i,tokens,window));
            }
        }
        return list;
    }
    static final class Contribution {
        final int slot;
        final long estimated;
        final int userWindow;
        Contribution(int slot,long estimated,int userWindow){
            this.slot=slot;this.estimated=estimated;this.userWindow=userWindow;
        }
    }
    static synchronized long combinedEstimate(Context c,String provider){
        long total=0;
        for(Contribution entry:contributions(c,provider))
            total=Long.MAX_VALUE-entry.estimated<total?
                Long.MAX_VALUE:total+entry.estimated;
        return total;
    }

    static synchronized long used(Context c,String provider,int slot){
        return preferences(c).getLong(key(c,provider,slot)+".estimated",0);
    }
    static synchronized int window(Context c,String provider,int slot){
        return preferences(c).getInt(key(c,provider,slot)+".window",0);
    }
    static synchronized void setWindow(Context c,String provider,int slot,int tokens){
        if(tokens!=0&&(tokens<4096||tokens>2000000))
            throw new IllegalArgumentException("manual_context_window_out_of_range");
        if(!preferences(c).edit().putInt(key(c,provider,slot)+".window",tokens).commit())
            throw new IllegalStateException("local_context_window_save_failed");
    }
    static synchronized long record(Context c,String provider,int slot,
            CharSequence suppliedText,boolean replaceSnapshot){
        if(!enabled(c,provider,slot))
            throw new IllegalStateException("manual_statistics_opt_in_required");
        long estimate=LocalContextEstimator.estimate(suppliedText);
        if(estimate<=0)throw new IllegalArgumentException("empty_manual_text");
        String k=key(c,provider,slot)+".estimated";
        long prior=preferences(c).getLong(k,0);
        long next=replaceSnapshot?estimate:
            (prior>Long.MAX_VALUE-estimate?Long.MAX_VALUE:prior+estimate);
        if(!preferences(c).edit().putLong(k,next).commit())
            throw new IllegalStateException("local_context_record_save_failed");
        return next;
    }
    static synchronized void resetCount(Context c,String provider,int slot){
        if(!preferences(c).edit()
            .remove(key(c,provider,slot)+".estimated").commit())
            throw new IllegalStateException("local_context_reset_failed");
    }
    static String handoffPrompt(){
        // Only generate a user-owned instruction. No reading messages from
        // GeckoView or automatic sending to a website.
        return "请为当前对话制作一份用于新聊天无缝接续的【上下文交接摘要】。\n"+
            "请只根据本对话中确实出现的信息总结，禁止猜测或杜撰。请覆盖：\n"+
            "1. 用户的最终目标、当前任务、优先级及已经明确的偏好；\n"+
            "2. 已经核实的事实、完成的工作和可以复核的证据；\n"+
            "3. 重要决策、设计限制、安全边界、尚未解决的问题；\n"+
            "4. 接下来最具体的3至7项操作和验收标准；\n"+
            "5. 关键项目名、文件名、代码位置、版本号和错误码（仅确有提及时）；\n"+
            "6. 重要上下文冲突、信息不确定性及必须重新核验的内容。\n"+
            "不要写入密码、API Key、Cookie、会话 Token、短信验证码或其他敏感数据；"+
            "如这些内容曾出现，只描述其类别而不重复明文。\n"+
            "请先输出不超过1200字的可复制交接版，再列出下一步执行清单。"+
            "最后标注：哪些信息无法根据当前可见上下文确认。"+
            "不要以已经完成为名虚构没有验证的功能。";
    }
}
