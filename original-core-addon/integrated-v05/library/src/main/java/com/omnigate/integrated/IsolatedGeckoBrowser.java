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
    private Object session;
    private Object geckoView;
    private int selected=-1;

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
        switchTo(AccountBrowserStore.selected(host,provider));
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
