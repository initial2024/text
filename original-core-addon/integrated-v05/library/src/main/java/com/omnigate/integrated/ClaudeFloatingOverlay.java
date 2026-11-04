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
    private static Button account,fold,closeControl;
    private static boolean inputFocusHeld;
    private static final int BUBBLE_SIZE_DP=52;
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
            // Keep the small non-focusable bubble on top of other apps.
            // Expanding the page must be an explicit user tap.
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
            Math.min(Math.round(screen*.78f),dp(app,340)),
            Math.max(dp(app,285),Math.round(height*.50f)),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH|
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
        // Tapping any other app always releases the IME focus previously
        // acquired for the Claude address field or Gecko text input.
        root.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_OUTSIDE){
                setOverlayFocusable(false);
                return true;
            }
            return false;
        });
        root.getViewTreeObserver().addOnWindowFocusChangeListener(hasFocus->{
            if(!hasFocus && inputFocusHeld)setOverlayFocusable(false);
        });
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
        title.addView(fold,new LinearLayout.LayoutParams(dp(app,42),dp(app,42)));
        fold.setContentDescription("收起或展开 Claude 悬浮窗");
        // A collapsed bubble is draggable without forcing keyboard focus.
        final float[] handleDown=new float[2];
        final int[] handlePos=new int[2];
        final boolean[] dragged=new boolean[1];
        fold.setOnTouchListener((v,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    handleDown[0]=event.getRawX();handleDown[1]=event.getRawY();
                    handlePos[0]=window.x;handlePos[1]=window.y;
                    dragged[0]=false;return true;
                case MotionEvent.ACTION_MOVE:
                    float dx=event.getRawX()-handleDown[0];
                    float dy=event.getRawY()-handleDown[1];
                    if(Math.abs(dx)>dp(app,7)||Math.abs(dy)>dp(app,7))
                        dragged[0]=true;
                    if(dragged[0]){
                        int maxX=Math.max(0,app.getResources().getDisplayMetrics().widthPixels-window.width);
                        int maxY=Math.max(0,app.getResources().getDisplayMetrics().heightPixels-window.height);
                        window.x=Math.max(0,Math.min(maxX,handlePos[0]+Math.round(dx)));
                        window.y=Math.max(0,Math.min(maxY,handlePos[1]+Math.round(dy)));
                        manager.updateViewLayout(root,window);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if(!dragged[0])v.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:return true;
                default:return false;
            }
        });
        fold.setOnClickListener(v->setMinimized(!minimized));
        closeControl=button(app,"×");
        title.addView(closeControl,new LinearLayout.LayoutParams(dp(app,38),dp(app,42)));
        closeControl.setOnClickListener(v->close());
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
        url.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN)
                setOverlayFocusable(true);
            return false;
        });
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
        setMinimized(true); // Open as a small, non-IME-stealing bubble.
    }
    private static void setOverlayFocusable(boolean enabled){
        if(window==null||manager==null||root==null)return;
        if(minimized)enabled=false;
        if(inputFocusHeld==enabled)return;
        inputFocusHeld=enabled;
        if(enabled){
            window.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            window.flags &= ~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        }else{
            window.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            android.view.inputmethod.InputMethodManager ime=
                (android.view.inputmethod.InputMethodManager)
                app.getSystemService(Context.INPUT_METHOD_SERVICE);
            if(ime!=null)ime.hideSoftInputFromWindow(root.getWindowToken(),0);
            root.clearFocus();
        }
        try{manager.updateViewLayout(root,window);}
        catch(RuntimeException ignored){}
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
        menu.setOnDismissListener(popup->setOverlayFocusable(false));
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
        ((View)visual).setOnTouchListener((v,event)->{
            // GeckoView needs focus to open its own soft keyboard. It should
            // not retain that focus once the user taps outside or folds away.
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN)
                setOverlayFocusable(true);
            return false;
        });
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
        if(value){
            // Releasing the overlay focus allows the underlying app to use
            // its own keyboard. The GeckoSession and login remain alive.
            inputFocusHeld=true;
            setOverlayFocusable(false);
        }else{
            // Expanded browser starts passive; text fields acquire focus
            // only following an intentional touch inside the overlay.
            inputFocusHeld=true;
            setOverlayFocusable(false);
        }
        body.setVisibility(value?View.GONE:View.VISIBLE);
        caption.setVisibility(value?View.GONE:View.VISIBLE);
        account.setVisibility(value?View.GONE:View.VISIBLE);
        closeControl.setVisibility(value?View.GONE:View.VISIBLE);
        window.height=value?dp(app,BUBBLE_SIZE_DP):
            Math.max(dp(app,285),Math.round(app.getResources().getDisplayMetrics().heightPixels*.50f));
        window.width=value?dp(app,BUBBLE_SIZE_DP):
            Math.min(Math.round(app.getResources().getDisplayMetrics().widthPixels*.78f),dp(app,340));
        int maxX=Math.max(0,app.getResources().getDisplayMetrics().widthPixels-window.width);
        int maxY=Math.max(0,app.getResources().getDisplayMetrics().heightPixels-window.height);
        window.x=Math.max(0,Math.min(maxX,window.x));
        window.y=Math.max(0,Math.min(maxY,window.y));
        fold.setText(value?"C":"—");
        fold.setContentDescription(value?"展开 Claude 网页悬浮窗":"收起 Claude 网页悬浮窗");
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
        closeControl=null;window=null;manager=null;app=null;
        selected=-1;minimized=false;inputFocusHeld=false;
    }
}
