package com.omnigate.integrated;

import android.content.Context;

/**
 * The original immutable WebDriver does NOT inherit an auxiliary Gecko slot.
 * A chosen primary target blocks all other webpage-adapter model submissions
 * until exact context+provider+socket-owner proof. Claude's own API bypasses
 * this *native* browser context check, not any Claude risk/throttle circuit.
 */
final class MainAccountRequestGuard {
    private MainAccountRequestGuard(){}
    static MainAccountRoutePolicy.Decision evaluate(Context ctx,String channel){
        if("claude".equals(channel))
            return MainAccountRoutePolicy.decide(false,null,channel,false,false);
        MainAccountTargetStore.Target t=MainAccountTargetStore.active(ctx);
        if(t==null)
            return MainAccountRoutePolicy.decide(false,null,channel,false,false);
        if(!t.provider.equals(channel))
            return MainAccountRoutePolicy.decide(true,t.provider,channel,false,false);
        boolean verified=OriginalCoreBridge.verifiedMainDriverUsesContext(
            t.provider,t.contextId);
        return MainAccountRoutePolicy.decide(true,t.provider,channel,
            OriginalCoreBridge.isExactMainApiOwner(),verified);
    }
    static String state(Context c){
        try{
            MainAccountTargetStore.Target target=MainAccountTargetStore.active(c);
            if(target==null)return "原版执行账号 · 未启用独立绑定（按原版登录）";
            String p=target.provider+" / 账号 "+(target.slot+1);
            MainAccountRoutePolicy.Decision d=evaluate(c,target.provider);
            if(d.allowed&&d.outcome==MainAccountRoutePolicy.Outcome.VERIFIED)
                return "主执行上下文匹配："+p+
                    "（仅证明 Gecko 存储隔离，仍需真实登录/回复验收）";
            return "主执行目标待接线："+p+" · "+d.outcome.name()+
                "；8787 禁止向旧会话发送";
        }catch(RuntimeException corrupted){
            return "主执行绑定状态异常：禁止模型发送；请检查设备资料";
        }
    }
}
