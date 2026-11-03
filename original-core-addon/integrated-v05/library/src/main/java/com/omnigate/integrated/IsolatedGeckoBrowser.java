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
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Manual backup web accounts. One independent GeckoSession at a time with
 * mandatory persistent contextId. NEVER changes the original WebDriver,
 * injects a prompt, copies cookies or creates a second GeckoRuntime.
 */
final class IsolatedGeckoBrowser {
    private final Activity host;
    private final String provider;
    private final int startingSlot;
    private final Dialog dialog;
    private final LinearLayout tabs;
    private final FrameLayout display;
    private final TextView state;
    private final TextView localBudgetState;
    private final Button autoToggle;
    private Object session;
    private Object geckoView;
    private int selected=-1;

    private static int dp(Context c,int pixels){
        return Math.round(c.getResources().getDisplayMetrics().density*pixels);
    }
    private static android.graphics.drawable.GradientDrawable surface(int color,int radius,Context c){
        android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c,radius));
        return g;
    }
    static void showPicker(Activity host){
        try{
            final int ink=0xFFF5F6FF,muted=0xFFB4BEDF,accent=0xFF6C5BFB;
            Dialog picker=new Dialog(host);
            LinearLayout shell=new LinearLayout(host);
            shell.setOrientation(LinearLayout.VERTICAL);
            shell.setPadding(dp(host,18),dp(host,24),dp(host,18),dp(host,16));
            shell.setBackgroundColor(0xFF0B1024);
            TextView title=new TextView(host);
            title.setText("账号空间");
            title.setTextColor(ink);title.setTextSize(27);
            title.setTypeface(null,android.graphics.Typeface.BOLD);
            shell.addView(title);
            TextView subtitle=new TextView(host);
            subtitle.setText("点选平台和账号，即可打开独立登录空间。不会改变原版 8787 主执行账号。");
            subtitle.setTextSize(12);subtitle.setTextColor(muted);
            subtitle.setPadding(0,dp(host,6),0,dp(host,12));
            shell.addView(subtitle);
            ScrollView scroll=new ScrollView(host);
            scroll.setFillViewport(false);
            LinearLayout options=new LinearLayout(host);
            options.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(options);
            for(int p=0;p<AccountBrowserSlots.PROVIDERS.length;p++){
                final String provider=AccountBrowserSlots.PROVIDERS[p];
                int count=AccountBrowserStore.count(host,provider);
                int selected=AccountBrowserStore.selected(host,provider);
                TextView heading=new TextView(host);
                heading.setText(AccountBrowserSlots.LABELS[p]+"    ·    "+count+" 个隔离账号");
                heading.setTextColor(0xFF87E6EF);heading.setTextSize(14);
                heading.setTypeface(null,android.graphics.Typeface.BOLD);
                heading.setPadding(dp(host,4),dp(host,13),0,dp(host,8));
                options.addView(heading);
                for(int slot=0;slot<count;slot++){
                    final int index=slot;
                    Button entry=new Button(host);
                    entry.setAllCaps(false);
                    entry.setText("账号 "+(slot+1)+(slot==selected?"   ·   上次打开":"")+"     ›");
                    entry.setGravity(android.view.Gravity.CENTER_VERTICAL|android.view.Gravity.START);
                    entry.setTextColor(ink);entry.setTextSize(14);
                    entry.setPadding(dp(host,18),0,dp(host,12),0);
                    entry.setBackground(surface(slot==selected?0xFF35336C:0xFF1B2443,14,host));
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(host,48));
                    lp.bottomMargin=dp(host,7);options.addView(entry,lp);
                    entry.setOnClickListener(v->{picker.dismiss();show(host,provider,index);});
                }
            }
            shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            TextView notice=new TextView(host);
            notice.setText("独立 Cookie / localStorage · 风控熔断优先 · 不会自动重发");
            notice.setTextSize(11);notice.setTextColor(muted);
            notice.setPadding(0,dp(host,9),0,dp(host,8));shell.addView(notice);
            Button close=new Button(host);
            close.setText("返回控制台");close.setAllCaps(false);
            close.setTextColor(ink);close.setBackground(surface(accent,14,host));
            shell.addView(close,new LinearLayout.LayoutParams(-1,dp(host,48)));
            close.setOnClickListener(v->picker.dismiss());
            picker.setContentView(shell);
            picker.show();
            Window w=picker.getWindow();
            if(w!=null)w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        }catch(RuntimeException storageError){
            new AlertDialog.Builder(host)
                .setTitle("账号选择暂不可用")
                .setMessage("读取本地隔离账号槽位失败；没有改变登录状态、"+
                    "模型请求或风控。请保留现有应用数据。")
                .setPositiveButton("确定",null).show();
        }
    }
    static void show(Activity host,String provider){
        show(host,provider,AccountBrowserStore.selected(host,provider));
    }
    static void show(Activity host,String provider,int slot){
        if(!AccountBrowserSlots.supported(provider))
            throw new IllegalArgumentException("provider");
        if(slot<0||slot>=AccountBrowserStore.count(host,provider))
            throw new IllegalArgumentException("slot");
        new IsolatedGeckoBrowser(host,provider,slot).show();
    }
    private IsolatedGeckoBrowser(Activity host,String provider,int slot){
        this.host=host;this.provider=provider;this.startingSlot=slot;
        dialog=new Dialog(host);
        LinearLayout root=new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(host,12),dp(host,12),dp(host,12),dp(host,8));
        root.setBackgroundColor(0xFF0B1024);
        TextView info=new TextView(host);
        info.setText("OmniGate  /  "+provider.toUpperCase(java.util.Locale.ROOT)+
            "\n隔离账号空间 · 与原版 8787 主执行登录独立");
        info.setTextSize(16);
        info.setTypeface(null,android.graphics.Typeface.BOLD);
        info.setPadding(dp(host,8),dp(host,5),dp(host,8),dp(host,9));
        info.setTextColor(0xFFF3F4FF);
        root.addView(info);
        HorizontalScrollView scroll=new HorizontalScrollView(host);
        tabs=new LinearLayout(host);tabs.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(tabs);
        root.addView(scroll);
        state=new TextView(host);state.setTextColor(0xFF86E8EE);
        state.setTextSize(12);state.setPadding(dp(host,7),dp(host,6),dp(host,7),dp(host,6));
        root.addView(state);
        TextView instructions=new TextView(host);
        instructions.setText("次数仅来自手动确认；风控时停止切换，不会自动发消息。"+
            "网页 Token 无可信统计来源。");
        instructions.setTextColor(0xFFAFB9DE);
        instructions.setTextSize(11);
        root.addView(instructions);
        localBudgetState=new TextView(host);
        localBudgetState.setTextColor(0xFFCAD3F2);
        localBudgetState.setTextSize(11);
        root.addView(localBudgetState);
        LinearLayout budgetActions=new LinearLayout(host);
        budgetActions.setOrientation(LinearLayout.HORIZONTAL);
        Button configure=button("设置额度");
        configure.setOnClickListener(v->configureBudget());
        autoToggle=button("自动切换：关闭");
        autoToggle.setOnClickListener(v->toggleAutomaticBudget());
        Button completed=button("完成本轮");
        completed.setOnClickListener(v->finishedTurn());
        budgetActions.addView(configure,new LinearLayout.LayoutParams(0,-2,1f));
        budgetActions.addView(autoToggle,new LinearLayout.LayoutParams(0,-2,1f));
        budgetActions.addView(completed,new LinearLayout.LayoutParams(0,-2,1f));
        root.addView(budgetActions);
        Button mainTarget=button("设为主执行目标 · 核验接线");
        mainTarget.setOnClickListener(v->requestNativeTarget());
        LinearLayout.LayoutParams targetParams=new LinearLayout.LayoutParams(-1,dp(host,45));
        targetParams.topMargin=dp(host,7);
        root.addView(mainTarget,targetParams);
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
        Button b=new Button(host);b.setAllCaps(false);b.setText(name);b.setTextSize(12);
        b.setTextColor(0xFFF1F4FF);
        b.setBackground(surface(0xFF282E55,12,host));
        b.setMinHeight(dp(host,43));
        return b;
    }
    private void show(){
        dialog.show();
        Window w=dialog.getWindow();
        if(w!=null)w.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);
        renderTabs();
        refreshLocalBudget();
        switchTo(startingSlot);
    }
    private void refreshLocalBudget(){
        int limit=AccountBrowserStore.voluntaryBudget(host,provider);
        int hourlyLimit=AccountBrowserStore.hourlyBudget(host,provider);
        boolean enabled=AccountBrowserStore.autoBudgetSwitch(host,provider);
        int current=selected>=0?selected:AccountBrowserStore.selected(host,provider);
        long now=System.currentTimeMillis();
        int[] today=AccountBrowserStore.allUsesToday(host,provider,now);
        int[] hourly=AccountBrowserStore.allUsesLastHour(host,provider,now);
        StringBuilder counts=new StringBuilder();
        for(int i=0;i<today.length;i++){
            if(i>0)counts.append("  ");
            counts.append("账号").append(i+1).append(":")
                .append(hourly[i]).append("/小时 · ").append(today[i]).append("/UTC日");
        }
        int reference=AccountBrowserStore.userDailyReference(host,provider);
        String recommendation=reference>=2
            ?("所填日参考额度的80%建议 "+
              AccountBudgetAdvisor.recommendFromUserEnteredDailyReference(reference)+
              "次/槽（人工填写，未自动核验）")
            :("保守起点建议 "+AccountBudgetAdvisor.conservativeUnverifiedStart()+
              "次/槽/日（纯本地经验，非官方限制）");
        int hourRef=AccountBrowserStore.userHourlyReference(host,provider);
        String hourAdvice=hourRef>=2
            ?("所填每小时参考额度的80%建议 "+
                AccountBudgetAdvisor.recommendFromUserEnteredHourlyReference(hourRef)+"次/小时")
            :("未核实网页限制：本地建议 "+
                AccountBudgetAdvisor.conservativeUnverifiedHourlyStart()+"次/滚动60分钟");
        localBudgetState.setText(
            "个人次数预算 · 滚动60分钟 "+(hourlyLimit>0?hourlyLimit+"次":"未设定")+
            " | UTC日 "+(limit>0?limit+"次":"未设定")+
            "\n"+hourAdvice+"；"+recommendation+"\n"+counts+
            "\n仅计算你手动确认的轮次；网页 Token 用量不可可靠计量。");
        autoToggle.setText(enabled?"自动：开":"自动：关");
    }
    private void configureBudget(){
        LinearLayout content=new LinearLayout(host);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(16*host.getResources().getDisplayMetrics().density);
        content.setPadding(pad,pad/2,pad,pad/2);
        TextView detail=new TextView(host);
        detail.setText("推荐值只作本地提醒，不能推算网页平台风控。"+
            "分别设置每小时与每日次数。只有账号页面明确公布对应周期的"+
            "网页额度才填参考值，不可把 API 的 RPM/TPM 套用于网页。"+
            "小时按滚动60分钟计算；网页版 Token 不可统计。");
        content.addView(detail);
        EditText daily=new EditText(host);
        daily.setInputType(InputType.TYPE_CLASS_NUMBER);
        daily.setSingleLine(true);
        daily.setHint("官方账号页单日额度（可留空）");
        int reference=AccountBrowserStore.userDailyReference(host,provider);
        if(reference>0)daily.setText(Integer.toString(reference));
        content.addView(daily);
        EditText hourlyRef=new EditText(host);
        hourlyRef.setInputType(InputType.TYPE_CLASS_NUMBER);
        hourlyRef.setSingleLine(true);
        hourlyRef.setHint("官方账号页每小时次数额度（可留空）");
        int savedHourRef=AccountBrowserStore.userHourlyReference(host,provider);
        if(savedHourRef>0)hourlyRef.setText(Integer.toString(savedHourRef));
        content.addView(hourlyRef);
        TextView examples=new TextView(host);
        examples.setText("参考额度 60 次/日 → 推荐 48 次/日（80%）；"+
            "未提供日额度 → 保守经验建议 8 次/日，不代表安全阈值。");
        content.addView(examples);
        EditText input=new EditText(host);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setHint("自定义本地预算 1–100 次/槽/日");
        int prior=AccountBrowserStore.voluntaryBudget(host,provider);
        if(prior>0)input.setText(Integer.toString(prior));
        content.addView(input);
        EditText hourlyInput=new EditText(host);
        hourlyInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        hourlyInput.setSingleLine(true);
        hourlyInput.setHint("自定义滚动60分钟 1–1000 次/槽");
        int priorHour=AccountBrowserStore.hourlyBudget(host,provider);
        if(priorHour>0)hourlyInput.setText(Integer.toString(priorHour));
        content.addView(hourlyInput);
        new AlertDialog.Builder(host)
            .setTitle("次数预算 · 滚动60分钟 / 每日")
            .setView(content)
            .setNegativeButton("取消",null)
            .setNeutralButton("应用推荐",(d,which)->{
                try{
                    String raw=daily.getText().toString().trim();
                    int suggested=AccountBudgetAdvisor.conservativeUnverifiedStart();
                    if(!raw.isEmpty()){
                        int explicit=Integer.parseInt(raw);
                        suggested=AccountBudgetAdvisor
                            .recommendFromUserEnteredDailyReference(explicit);
                        AccountBrowserStore.setUserDailyReference(host,provider,explicit);
                    }else if(reference>=2){
                        // Blank field means use conservative unknown starting
                        // point rather than silently trusting an old limit.
                        suggested=AccountBudgetAdvisor.conservativeUnverifiedStart();
                    }
                    int hourSuggested=AccountBudgetAdvisor.conservativeUnverifiedHourlyStart();
                    String hourRaw=hourlyRef.getText().toString().trim();
                    if(!hourRaw.isEmpty()){
                        int explicitHour=Integer.parseInt(hourRaw);
                        hourSuggested=AccountBudgetAdvisor
                            .recommendFromUserEnteredHourlyReference(explicitHour);
                        AccountBrowserStore.setUserHourlyReference(host,provider,explicitHour);
                    }
                    AccountBrowserStore.setVoluntaryBudget(host,provider,suggested);
                    AccountBrowserStore.setHourlyBudget(host,provider,hourSuggested);
                    refreshLocalBudget();
                    state.setText("已应用本地建议："+hourSuggested+
                        " 次/滚动60分钟，"+suggested+" 次/UTC日；不保证平台安全");
                }catch(RuntimeException invalid){
                    state.setText("未修改预算：请填写 2–100000 的真实单日参考额度");
                }
            })
            .setPositiveButton("保存自定义",(d,which)->{
                try{
                    String dayRaw=input.getText().toString().trim();
                    String hourRaw=hourlyInput.getText().toString().trim();
                    if(dayRaw.isEmpty()&&hourRaw.isEmpty())
                        throw new IllegalArgumentException("empty_budget");
                    int custom=dayRaw.isEmpty()?0:Integer.parseInt(dayRaw);
                    int customHour=hourRaw.isEmpty()?0:Integer.parseInt(hourRaw);
                    if(!dayRaw.isEmpty()&&(custom<1||custom>100))
                        throw new IllegalArgumentException("daily_budget");
                    if(!hourRaw.isEmpty()&&(customHour<1||customHour>1000))
                        throw new IllegalArgumentException("hour_budget");
                    String raw=daily.getText().toString().trim();
                    String refHour=hourlyRef.getText().toString().trim();
                    int dayRef=raw.isEmpty()?0:Integer.parseInt(raw);
                    int hRef=refHour.isEmpty()?0:Integer.parseInt(refHour);
                    if(!raw.isEmpty()&&(dayRef<2||dayRef>100000))
                        throw new IllegalArgumentException("day_reference");
                    if(!refHour.isEmpty()&&(hRef<2||hRef>100000))
                        throw new IllegalArgumentException("hour_reference");
                    if(!dayRaw.isEmpty())AccountBrowserStore.setVoluntaryBudget(host,provider,custom);
                    if(!hourRaw.isEmpty())AccountBrowserStore.setHourlyBudget(host,provider,customHour);
                    if(!raw.isEmpty())AccountBrowserStore.setUserDailyReference(host,provider,dayRef);
                    if(!refHour.isEmpty())AccountBrowserStore.setUserHourlyReference(host,provider,hRef);
                    refreshLocalBudget();
                }catch(RuntimeException invalid){
                    state.setText("预算无效：日 1–100、滚动小时 1–1000；参考额度 2–100000");
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
            if(AccountBrowserStore.voluntaryBudget(host,provider)<1 &&
                AccountBrowserStore.hourlyBudget(host,provider)<1){
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
    private void requestNativeTarget(){
        if(selected<0||session==null){
            state.setText("隔离浏览器尚未打开；未登记执行目标");
            return;
        }
        if(!SidecarProxyServer.voluntaryBrowserSwitchPermitted(provider)){
            state.setText("平台熔断中，不允许更改执行目标来绕过限制");
            return;
        }
        final int requestedSlot=selected;
        new AlertDialog.Builder(host)
            .setTitle("把此账号设为主执行目标？")
            .setMessage("将登记 "+provider+" · 账号 "+(requestedSlot+1)+
                " 作为未来 8787 原生 WebDriver 的目标隔离上下文。"+
                "当前辅助 GeckoSession 不能直接替换原版驱动；在原版实际"+
                "WebDriver 未绑定并核验之前，模型发送会返回 409，不会错发到旧账号。"+
                "本操作不复制 Cookie、不重启或切断正在生成的任务。")
            .setNegativeButton("取消",null)
            .setPositiveButton("登记目标并保护发送",(d,w)->{
                try{
                    MainAccountTargetStore.request(host,provider,requestedSlot);
                    state.setText("主执行目标已登记；正在核验实际原版 WebDriver…");
                    new Thread(()->{
                        String checked=MainAccountRequestGuard.state(host);
                        host.runOnUiThread(()->{
                            if(dialog.isShowing()&&!host.isDestroyed())state.setText(checked);
                        });
                    },"omnigate-main-target-inspect").start();
                }catch(RuntimeException error){
                    state.setText("执行目标登记失败："+error.getClass().getSimpleName());
                }
            }).show();
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
            int[] hour=AccountBrowserStore.allUsesLastHour(host,provider,now);
            VoluntaryAccountBudget.Decision decision=VoluntaryAccountBudget.evaluateWithHour(
                true,true,AccountBrowserStore.autoBudgetSwitch(host,provider),
                AccountBrowserStore.voluntaryBudget(host,provider),
                AccountBrowserStore.hourlyBudget(host,provider),finishedSlot,counts,hour);
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
