package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.text.InputType;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Manual backup web accounts. One independent GeckoSession at a time with
 * mandatory persistent contextId. NEVER changes the original WebDriver,
 * injects a prompt, copies cookies or creates a second GeckoRuntime.
 */
final class IsolatedGeckoBrowser {
    private final Activity host;
    private final String provider;
    private final Dialog dialog;
    private final LinearLayout tabs;
    private final FrameLayout display;
    private final TextView state;
    private final TextView localBudgetState;
    private final Button autoToggle;
    private Object session;
    private Object geckoView;
    private int selected=-1;

    static void showPicker(Activity host){
        // The model homepage and Settings share the same user-controlled
        // picker. There is no model POST, account fallback or token copying.
        new AlertDialog.Builder(host)
            .setTitle("账号切换 · 隔离浏览器")
            .setMessage("每个账号槽位独立保存网页登录状态；"+
                "此入口不更改原版 WebDriver/8787 使用的账号。"+
                "平台限流或鉴权异常时不得换号继续请求。")
            .setItems(AccountBrowserSlots.LABELS,(dialog,which)->
                show(host,AccountBrowserSlots.PROVIDERS[which]))
            .setNegativeButton("取消",null).show();
    }
    static void show(Activity host,String provider){
        if(!AccountBrowserSlots.supported(provider))
            throw new IllegalArgumentException("provider");
        new IsolatedGeckoBrowser(host,provider).show();
    }
    private IsolatedGeckoBrowser(Activity host,String provider){
        this.host=host;this.provider=provider;
        dialog=new Dialog(host);
        LinearLayout root=new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(10,10,10,10);
        root.setBackgroundColor(Color.WHITE);
        TextView info=new TextView(host);
        info.setText("独立网页登录："+provider+
            "\n每个账号隔离 Cookie / localStorage；首次需要自行登录。"+
            "不接管原版 8787 模型运行，风控时不会自动轮换账号。");
        info.setTextColor(Color.BLACK);
        root.addView(info);
        HorizontalScrollView scroll=new HorizontalScrollView(host);
        tabs=new LinearLayout(host);tabs.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(tabs);
        root.addView(scroll);
        state=new TextView(host);state.setTextColor(Color.DKGRAY);
        root.addView(state);
        TextView instructions=new TextView(host);
        instructions.setText("自定义使用预算 ≠ 平台安全次数。仅在你点击「本轮已完成」"+
            "确认浏览器空闲后计数并按预算切换；不监听账号请求、不自动发消息。"+
            "平台风控或限流仍强制停止切换。");
        instructions.setTextColor(Color.DKGRAY);
        root.addView(instructions);
        localBudgetState=new TextView(host);
        localBudgetState.setTextColor(Color.DKGRAY);
        root.addView(localBudgetState);
        LinearLayout budgetActions=new LinearLayout(host);
        budgetActions.setOrientation(LinearLayout.HORIZONTAL);
        Button configure=button("设置本地次数预算");
        configure.setOnClickListener(v->configureBudget());
        autoToggle=button("自动切换：关闭");
        autoToggle.setOnClickListener(v->toggleAutomaticBudget());
        Button completed=button("本轮已完成 / 计数");
        completed.setOnClickListener(v->finishedTurn());
        budgetActions.addView(configure,new LinearLayout.LayoutParams(0,-2,1f));
        budgetActions.addView(autoToggle,new LinearLayout.LayoutParams(0,-2,1f));
        budgetActions.addView(completed,new LinearLayout.LayoutParams(0,-2,1f));
        root.addView(budgetActions);
        display=new FrameLayout(host);
        root.addView(display,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=new LinearLayout(host);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        Button add=button("新账号槽位");
        add.setOnClickListener(v->{
            try{
                int index=AccountBrowserStore.create(host,provider);
                renderTabs();switchTo(index);
            }catch(RuntimeException ex){
                state.setText("新增失败："+ex.getClass().getSimpleName());
            }
        });
        Button back=button("后退");back.setOnClickListener(v->callSession("goBack"));
        Button reload=button("刷新");reload.setOnClickListener(v->callSession("reload"));
        Button close=button("关闭");close.setOnClickListener(v->dialog.dismiss());
        footer.addView(add,new LinearLayout.LayoutParams(0,-2,3f));
        footer.addView(back,new LinearLayout.LayoutParams(0,-2,1f));
        footer.addView(reload,new LinearLayout.LayoutParams(0,-2,1f));
        footer.addView(close,new LinearLayout.LayoutParams(0,-2,1f));
        root.addView(footer);
        dialog.setContentView(root);
        dialog.setOnDismissListener(v->releaseSession());
    }
    private Button button(String name){
        Button b=new Button(host);b.setAllCaps(false);b.setText(name);b.setTextSize(11);
        return b;
    }
    private void show(){
        dialog.show();
        Window w=dialog.getWindow();
        if(w!=null)w.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        renderTabs();
        refreshLocalBudget();
        switchTo(AccountBrowserStore.selected(host,provider));
    }
    private void refreshLocalBudget(){
        int limit=AccountBrowserStore.voluntaryBudget(host,provider);
        boolean enabled=AccountBrowserStore.autoBudgetSwitch(host,provider);
        int current=selected>=0?selected:AccountBrowserStore.selected(host,provider);
        int[] today=AccountBrowserStore.allUsesToday(
            host,provider,System.currentTimeMillis());
        StringBuilder counts=new StringBuilder();
        for(int i=0;i<today.length;i++){
            if(i>0)counts.append("  ");
            counts.append("账号").append(i+1).append(":").append(today[i]);
        }
        localBudgetState.setText(
            "仅本地记录·UTC日｜预算："+(limit>0?limit+"次/槽":"未设定")+
            "｜"+counts+"（不是实际平台请求统计）");
        autoToggle.setText(enabled?"自动切换：已启用":"自动切换：关闭");
    }
    private void configureBudget(){
        EditText input=new EditText(host);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        int prior=AccountBrowserStore.voluntaryBudget(host,provider);
        if(prior>0)input.setText(Integer.toString(prior));
        new AlertDialog.Builder(host)
            .setTitle("自定义每账号每日使用预算（UTC）")
            .setMessage("输入 1–100，纯本地自定预算，不代表平台允许的安全调用次数。"+
                "只统计你主动点击「本轮已完成」的次数，不读取对话。")
            .setView(input)
            .setNegativeButton("取消",null)
            .setPositiveButton("保存",(d,which)->{
                try{
                    int configured=Integer.parseInt(input.getText().toString().trim());
                    AccountBrowserStore.setVoluntaryBudget(host,provider,configured);
                    refreshLocalBudget();
                }catch(RuntimeException invalid){
                    state.setText("预算无效：请输入 1–100 的整数");
                }
            }).show();
    }
    private void toggleAutomaticBudget(){
        try{
            boolean enabled=AccountBrowserStore.autoBudgetSwitch(host,provider);
            if(enabled){
                AccountBrowserStore.setAutoBudgetSwitch(host,provider,false);
                refreshLocalBudget();
                return;
            }
            if(AccountBrowserStore.voluntaryBudget(host,provider)<1){
                state.setText("先自行设置每账号本地预算，再开启自动切换");
                return;
            }
            new AlertDialog.Builder(host)
                .setTitle("开启自定预算自动切换？")
                .setMessage("仅你确认本轮已完成时触发，且要求网页模型全局和"+
                    "当前提供商均未被风控。自动打开下一个有本地预算的"+
                    "独立账号槽位；不会自动发送或重新发送消息，"+
                    "不会接管原版 8787 模型账号。")
                .setNegativeButton("取消",null)
                .setPositiveButton("我确认开启",(d,which)->{
                    try{
                        AccountBrowserStore.setAutoBudgetSwitch(host,provider,true);
                        refreshLocalBudget();
                    }catch(RuntimeException failed){
                        state.setText("自动切换未启用："+failed.getClass().getSimpleName());
                    }
                }).show();
        }catch(RuntimeException invalid){
            state.setText("自动切换设置失败："+invalid.getClass().getSimpleName());
        }
    }
    private void finishedTurn(){
        if(selected<0||session==null){
            state.setText("没有可计数的独立浏览器会话");
            return;
        }
        // A user's explicit click is the finished/idle acknowledgment.
        // Never infer from network errors or platform challenge responses.
        if(!SidecarProxyServer.voluntaryBrowserSwitchPermitted(provider)){
            state.setText("官方平台风险门禁未放行，停止自定切换；"+
                "不会通过其他账号绕过限制，也不会重新发送请求");
            return;
        }
        try{
            long now=System.currentTimeMillis();
            int finishedSlot=selected;
            AccountBrowserStore.recordUserFinishedTurn(host,provider,finishedSlot,now);
            int[] counts=AccountBrowserStore.allUsesToday(host,provider,now);
            VoluntaryAccountBudget.Decision decision=VoluntaryAccountBudget.evaluate(
                true,true,AccountBrowserStore.autoBudgetSwitch(host,provider),
                AccountBrowserStore.voluntaryBudget(host,provider),finishedSlot,counts);
            refreshLocalBudget();
            if(decision.action==VoluntaryAccountBudget.Action.SWITCH_BROWSER){
                // Recheck after recording; a newly opened platform hold wins.
                if(!SidecarProxyServer.voluntaryBrowserSwitchPermitted(provider)){
                    state.setText("新增风控状态：停止自动切换，不发起其他账号请求");
                    return;
                }
                open(decision.nextSlot);
                if(selected==decision.nextSlot)
                    state.setText("按你设定的本地预算，已打开账号 "+
                        (selected+1)+"；未发送任何模型消息");
            }else if(decision.action==VoluntaryAccountBudget.Action.ALL_SLOTS_AT_BUDGET){
                state.setText("所有账号槽位已达到你的本地预算；不再自动切换");
            }else{
                state.setText("已记录你确认完成的一轮；未触发自动切换");
            }
        }catch(RuntimeException failure){
            state.setText("本地预算处理失败："+failure.getClass().getSimpleName());
        }
    }
    private void renderTabs(){
        tabs.removeAllViews();
        int count=AccountBrowserStore.count(host,provider);
        for(int i=0;i<count;i++){
            final int index=i;
            Button b=button("账号 "+(i+1));
            b.setOnClickListener(v->switchTo(index));
            tabs.addView(b);
        }
    }
    private void switchTo(int index){
        if(selected==index)return;
        if(selected<0){open(index);return;}
        new AlertDialog.Builder(host).setTitle("切换隔离网页账号")
            .setMessage("会关闭当前备用浏览器页面；正在生成的网页回复可能中断。"+
                "不会修改原版登录状态，也不会自动重发模型请求。")
            .setNegativeButton("取消",null)
            .setPositiveButton("切换",(d,w)->open(index)).show();
    }
    private static Object existingRuntime() throws Exception{
        Class<?> runtime=Class.forName("org.mozilla.geckoview.GeckoRuntime");
        java.lang.reflect.Method get=runtime.getDeclaredMethod("getInstance");
        get.setAccessible(true);
        Object existing=get.invoke(null);
        if(existing==null)throw new IllegalStateException("original_gecko_not_started");
        return existing;
    }
    private void open(int index){
        releaseSession();
        try{
            String id=AccountBrowserStore.contextId(host,provider,index);
            if(!AccountBrowserSlots.validContextId(id))
                throw new IllegalStateException("gecko_context_id_invalid");
            Object runtime=existingRuntime();
            Class<?> builderClass=Class.forName(
                "org.mozilla.geckoview.GeckoSessionSettings$Builder");
            Object builder=builderClass.getConstructor().newInstance();
            // Never use the null/default shared-storage session context.
            builderClass.getMethod("contextId",String.class).invoke(builder,id);
            Object settings=builderClass.getMethod("build").invoke(builder);
            Class<?> settingsClass=Class.forName(
                "org.mozilla.geckoview.GeckoSessionSettings");
            Class<?> sessionClass=Class.forName(
                "org.mozilla.geckoview.GeckoSession");
            Class<?> runtimeClass=Class.forName(
                "org.mozilla.geckoview.GeckoRuntime");
            Object created=sessionClass.getConstructor(settingsClass).newInstance(settings);
            // Register cleanup ownership before opening; any reflection or
            // display failure must close the original GeckoSession.
            session=created;
            sessionClass.getMethod("open",runtimeClass).invoke(created,runtime);
            Class<?> viewClass=Class.forName("org.mozilla.geckoview.GeckoView");
            Object visual=viewClass.getConstructor(Context.class).newInstance(host);
            if(!(visual instanceof View))throw new IllegalStateException("gecko_view_invalid");
            geckoView=visual;
            viewClass.getMethod("setSession",sessionClass).invoke(visual,created);
            display.addView((View)visual,new FrameLayout.LayoutParams(-1,-1));
            sessionClass.getMethod("loadUri",String.class)
                .invoke(created,AccountBrowserSlots.home(provider));
            AccountBrowserStore.select(host,provider,index);
            selected=index;
            refreshLocalBudget();
            state.setText("账号 "+(index+1)+"：隔离浏览器已打开（不影响原版 API）");
        }catch(Throwable failed){
            releaseSession();
            state.setText("无法启动隔离浏览器："+failed.getClass().getSimpleName()+
                "；先打开原版模型网页。不会回退到共享 Cookie 的浏览器。");
        }
    }
    private void callSession(String method){
        if(session==null)return;
        try{session.getClass().getMethod(method).invoke(session);}
        catch(Throwable failure){state.setText("网页操作失败："+failure.getClass().getSimpleName());}
    }
    private void releaseSession(){
        Object prior=session,view=geckoView;
        session=null;geckoView=null;selected=-1;
        if(view!=null){
            try{view.getClass().getMethod("releaseSession").invoke(view);}
            catch(Throwable ignored){}
        }
        if(prior!=null){
            try{prior.getClass().getMethod("close").invoke(prior);}
            catch(Throwable ignored){}
        }
        display.removeAllViews();
    }
}
