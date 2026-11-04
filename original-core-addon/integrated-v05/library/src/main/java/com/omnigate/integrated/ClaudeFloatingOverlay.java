package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Explicitly opened, one-session Claude web overlay. Not an API model, not
 * an automation bridge, no access to original WebDriver or other accounts.
 * Uses original GeckoRuntime and a persisted Claude-only context ID.
 */
public final class ClaudeFloatingOverlay {
    private static Context app;
    private static WindowManager manager;
    private static WindowManager.LayoutParams window;
    private static LinearLayout root,body;
    private static FrameLayout viewport;
    private static TextView caption;
    private static Button account,fold;
    private static Object session,geckoView;
    private static int selected=-1;
    private static boolean minimized;
    private ClaudeFloatingOverlay(){}
    private static int dp(Context c,int v){
        return Math.round(c.getResources().getDisplayMetrics().density*v);
    }
    private static Button button(Context c,String title){
        Button b=new Button(c);
        b.setAllCaps(false);b.setText(title);b.setTextSize(12);
        b.setMinHeight(0);b.setMinimumHeight(0);
        b.setTextColor(0xFFF7F9FF);b.setPadding(dp(c,3),0,dp(c,3),0);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF303858));
        return b;
    }
    public static boolean showing(){return root!=null && manager!=null;}
    public static void show(Activity host,int slot){
        if(host==null||host.isDestroyed()||host.isFinishing())return;
        if(slot<0||slot>=AccountBrowserStore.count(host,"claude"))return;
        if(!Settings.canDrawOverlays(host)){
            new AlertDialog.Builder(host).setTitle("开启 Claude 悬浮窗")
                .setMessage("需要你在 Android 系统中授予“显示在其他应用上层”权限。"+
                    "授权后回到 OmniGate 再点一次悬浮窗。不会自动启动网页登录或发送请求。")
                .setNegativeButton("取消",null)
                .setPositiveButton("打开系统权限设置",(d,w)->{
                    Intent i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:"+host.getPackageName()));
                    try{host.startActivity(i);}
                    catch(RuntimeException missing){
                        Toast.makeText(host,"请在系统设置中手动启用悬浮窗权限",
                            Toast.LENGTH_LONG).show();
                    }
                }).show();
            return;
        }
        // No concurrent auxiliary session: an existing manual login dialog
        // is explicitly dismissed before the floating GeckoSession opens.
        IsolatedGeckoBrowser.closeActiveBrowser();
        try{
            if(!showing())create(host.getApplicationContext());
            if(minimized)setMinimized(false);
            openAccount(slot);
        }catch(Throwable error){
            close();
            Toast.makeText(host,"Claude 悬浮窗不可用："+error.getClass().getSimpleName()+
                "；原版账号与模型保持不变",Toast.LENGTH_LONG).show();
        }
    }
    private static void create(Context context){
        app=context;
        manager=(WindowManager)app.getSystemService(Context.WINDOW_SERVICE);
        if(manager==null)throw new IllegalStateException("window_manager_missing");
        int screen=app.getResources().getDisplayMetrics().widthPixels;
        int height=app.getResources().getDisplayMetrics().heightPixels;
        window=new WindowManager.LayoutParams(
            Math.min(screen-dp(app,20),dp(app,430)),
            Math.max(dp(app,355),Math.round(height*.63f)),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        window.gravity=Gravity.TOP|Gravity.START;
        window.x=dp(app,10);
        window.y=dp(app,75);
        window.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        root=new LinearLayout(app);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(app,5),dp(app,5),dp(app,5),dp(app,5));
        root.setBackgroundColor(0xFF11192E);
        LinearLayout title=new LinearLayout(app);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(app,48)));
        caption=new TextView(app);
        caption.setText("⠿ Claude");
        caption.setTextColor(0xFFE0E7FF);caption.setTextSize(13);
        caption.setGravity(Gravity.CENTER_VERTICAL);
        caption.setPadding(dp(app,5),0,0,0);
        title.addView(caption,new LinearLayout.LayoutParams(0,-1,1));
        final float[] drag=new float[2];
        final int[] pos=new int[2];
        caption.setOnTouchListener((v,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    drag[0]=event.getRawX();drag[1]=event.getRawY();
                    pos[0]=window.x;pos[1]=window.y;return true;
                case MotionEvent.ACTION_MOVE:
                    int maxX=Math.max(0,app.getResources().getDisplayMetrics().widthPixels-window.width);
                    int maxY=Math.max(0,app.getResources().getDisplayMetrics().heightPixels-dp(app,80));
                    window.x=Math.max(0,Math.min(maxX,pos[0]+Math.round(event.getRawX()-drag[0])));
                    window.y=Math.max(0,Math.min(maxY,pos[1]+Math.round(event.getRawY()-drag[1])));
                    if(manager!=null && root!=null)manager.updateViewLayout(root,window);
                    return true;
                default:return true;
            }
        });
        account=button(app,"账号 ▾");
        title.addView(account,new LinearLayout.LayoutParams(dp(app,76),dp(app,42)));
        account.setOnClickListener(v->showAccounts());
        fold=button(app,"—");
        title.addView(fold,new LinearLayout.LayoutParams(dp(app,40),dp(app,42)));
        fold.setOnClickListener(v->setMinimized(!minimized));
        Button close=button(app,"×");
        title.addView(close,new LinearLayout.LayoutParams(dp(app,40),dp(app,42)));
        close.setOnClickListener(v->close());
        body=new LinearLayout(app);body.setOrientation(LinearLayout.VERTICAL);
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout address=new LinearLayout(app);
        body.addView(address,new LinearLayout.LayoutParams(-1,dp(app,42)));
        EditText url=new EditText(app);
        url.setSingleLine(true);url.setTextSize(11);url.setTextColor(0xFFF1F4FF);
        url.setHintTextColor(0xFFB8BFCE);url.setHint("输入 https:// 网址");
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|
            android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.addView(url,new LinearLayout.LayoutParams(0,-1,1));
        Button go=button(app,"前往");address.addView(go,
            new LinearLayout.LayoutParams(dp(app,52),dp(app,41)));
        go.setOnClickListener(v->navigate(url.getText().toString()));
        viewport=new FrameLayout(app);
        body.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=new LinearLayout(app);
        body.addView(footer,new LinearLayout.LayoutParams(-1,dp(app,42)));
        Button back=button(app,"后退"),reload=button(app,"刷新"),home=button(app,"主页");
        for(Button b:new Button[]{back,reload,home})
            footer.addView(b,new LinearLayout.LayoutParams(0,dp(app,41),1));
        back.setOnClickListener(v->callSession("goBack"));
        reload.setOnClickListener(v->callSession("reload"));
        home.setOnClickListener(v->navigate(AccountBrowserSlots.home("claude")));
        manager.addView(root,window);
    }
    private static void showAccounts(){
        if(account==null)return;
        PopupMenu menu=new PopupMenu(app,account);
        int n=AccountBrowserStore.count(app,"claude");
        for(int i=0;i<n;i++)
            menu.getMenu().add(0,i+1,i,"Claude 账号 "+(i+1)+(i==selected?" ✓":""));
        if(n<AccountBrowserSlots.MAX_SLOTS)
            menu.getMenu().add(0,100,100,"新增独立 Claude 账号");
        menu.setOnMenuItemClickListener(item->{
            try{
                int chosen=item.getItemId()==100
                    ?AccountBrowserStore.create(app,"claude")
                    :item.getItemId()-1;
                if(chosen!=selected)openAccount(chosen);
            }catch(Throwable failure){
                Toast.makeText(app,"账号切换失败；旧会话已关闭",
                    Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        menu.show();
    }
    private static void openAccount(int slot) throws Exception{
        releaseSession();
        selected=-1;
        String id=AccountBrowserStore.contextId(app,"claude",slot);
        if(!AccountBrowserSlots.validContextId(id))
            throw new IllegalStateException("context_invalid");
        Class<?> runtimeClass=Class.forName("org.mozilla.geckoview.GeckoRuntime");
        java.lang.reflect.Method get=runtimeClass.getDeclaredMethod("getInstance");
        get.setAccessible(true);
        Object runtime=get.invoke(null);
        if(runtime==null)throw new IllegalStateException("gecko_runtime_not_ready");
        Class<?> builderClass=Class.forName("org.mozilla.geckoview.GeckoSessionSettings$Builder");
        Object builder=builderClass.getConstructor().newInstance();
        builderClass.getMethod("contextId",String.class).invoke(builder,id);
        builderClass.getMethod("userAgentMode",int.class).invoke(builder,0);
        builderClass.getMethod("viewportMode",int.class).invoke(builder,0);
        Object settings=builderClass.getMethod("build").invoke(builder);
        Class<?> sessionClass=Class.forName("org.mozilla.geckoview.GeckoSession");
        Object created=sessionClass.getConstructor(
            Class.forName("org.mozilla.geckoview.GeckoSessionSettings"))
            .newInstance(settings);
        session=created;
        sessionClass.getMethod("open",runtimeClass).invoke(created,runtime);
        Class<?> viewClass=Class.forName("org.mozilla.geckoview.GeckoView");
        Object visual=viewClass.getConstructor(Context.class).newInstance(app);
        if(!(visual instanceof View))throw new IllegalStateException("gecko_view_invalid");
        geckoView=visual;
        viewClass.getMethod("setSession",sessionClass).invoke(visual,created);
        viewport.addView((View)visual,new FrameLayout.LayoutParams(-1,-1));
        AccountBrowserStore.select(app,"claude",slot);
        selected=slot;
        account.setText("账号 "+(slot+1)+" ▾");
        caption.setText("⠿ Claude 网页");
        navigate(AccountBrowserSlots.home("claude"));
    }
    private static void navigate(String text){
        if(session==null)return;
        try{
            String url=text==null?"":text.trim();
            if(url.isEmpty()||url.length()>4096||url.contains("\\")||
                url.startsWith("//"))throw new IllegalArgumentException("url");
            if(!url.startsWith("https://")&&!url.startsWith("http://"))
                url="https://"+url;
            java.net.URI u=new java.net.URI(url);
            if(u.getHost()==null||u.getRawUserInfo()!=null)
                throw new IllegalArgumentException("url");
            session.getClass().getMethod("loadUri",String.class).invoke(session,url);
        }catch(Exception invalid){
            Toast.makeText(app,"仅支持有效 HTTP/HTTPS 网址",Toast.LENGTH_SHORT).show();
        }
    }
    private static void callSession(String op){
        if(session==null)return;
        try{session.getClass().getMethod(op).invoke(session);}
        catch(Exception ignored){}
    }
    private static void setMinimized(boolean value){
        if(root==null||manager==null)return;
        minimized=value;
        body.setVisibility(value?View.GONE:View.VISIBLE);
        window.height=value?dp(app,56):
            Math.max(dp(app,355),Math.round(app.getResources().getDisplayMetrics().heightPixels*.63f));
        window.width=value?dp(app,238):
            Math.min(app.getResources().getDisplayMetrics().widthPixels-dp(app,20),dp(app,430));
        fold.setText(value?"+":"—");
        manager.updateViewLayout(root,window);
    }
    private static void releaseSession(){
        Object old=session,visual=geckoView;
        session=null;geckoView=null;
        if(visual!=null){
            try{visual.getClass().getMethod("releaseSession").invoke(visual);}
            catch(Throwable ignored){}
            if(visual instanceof View && viewport!=null)
                viewport.removeView((View)visual);
        }
        if(old!=null){
            try{old.getClass().getMethod("close").invoke(old);}
            catch(Throwable ignored){}
        }
    }
    public static void close(){
        releaseSession();
        if(root!=null&&manager!=null){
            try{manager.removeViewImmediate(root);}
            catch(RuntimeException ignored){}
        }
        root=null;body=null;viewport=null;caption=null;account=null;fold=null;
        window=null;manager=null;app=null;selected=-1;minimized=false;
    }
}
