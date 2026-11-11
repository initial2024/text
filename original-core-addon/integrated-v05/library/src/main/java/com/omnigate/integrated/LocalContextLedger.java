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
