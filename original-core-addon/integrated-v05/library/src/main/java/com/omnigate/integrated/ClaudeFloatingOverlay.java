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
 * Explicitly opened one-session Gecko web overlay for allowlisted providers.
 * Never connects to native 8787 execution or copies authentication state.
 * Retains exactly one active GeckoSession, opened only by a user tap.
 */
public final class ClaudeFloatingOverlay {
    private static Context app;
    private static WindowManager manager;
    private static WindowManager.LayoutParams window;
    private static LinearLayout root,body,addressBar;
    private static boolean addressExpanded,tallWindow;
    private static Button addressControl;
    private static android.widget.ScrollView quickPanel;
    private static EditText quickName,quickTags,quickNote,quickQuotaLabel,quickQuotaHours;
    private static android.widget.CheckBox quickIndefinite,quickSavePreset;
    private static TextView quickStatus;
    private static boolean quickOpen;
    private static FrameLayout viewport;
    private static TextView caption;
    private static Button account,fold,closeControl,fullscreenControl,fullscreenFooter,quickControl;
    // Full-window is a geometry change of the SAME GeckoView and GeckoSession.
    // Never reload the URL or close the session for a size/mode transition.
    private static boolean fullscreenWindow;
    private static int compactX,compactY;
    private static boolean inputFocusHeld;
    private static final int BUBBLE_SIZE_DP=52;
    // Native window geometry only; never rewrite site CSS, DPR or JS viewport.
    private static int compactWidth(){
        int screen=app.getResources().getDisplayMetrics().widthPixels;
        return Math.min(Math.round(screen*.94f),dp(app,440));
    }
    private static int compactHeight(){
        int screen=app.getResources().getDisplayMetrics().heightPixels;
        int target=Math.round(screen*(tallWindow?.70f:.51f));
        return Math.max(dp(app,300),Math.min(target,dp(app,tallWindow?720:540)));
    }
    private static Object session,geckoView;
    private static int selected=-1;
    private static String selectedProvider="";
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
    public static void show(Activity host,int slot){show(host,"claude",slot);}
    public static void show(Activity host,String provider,int slot){
        if(host==null||host.isDestroyed()||host.isFinishing()||
            !AccountBrowserSlots.supported(provider))return;
        if(slot<0||slot>=AccountBrowserStore.count(host,provider))return;
        if(!Settings.canDrawOverlays(host)){
            new AlertDialog.Builder(host).setTitle("开启网页悬浮窗")
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
            // All providers share one overlay owner; never preloads another.
            if(!provider.equals(selectedProvider)||selected!=slot||session==null){
                hideQuickSettings();
                selectedProvider=provider;
                openAccount(slot);
            }
        }catch(Throwable error){
            close();
            Toast.makeText(host,"网页悬浮窗不可用："+error.getClass().getSimpleName()+
                "；原版账号与模型保持不变",Toast.LENGTH_LONG).show();
        }
    }
    private static void create(Context context){
        app=context;
        manager=(WindowManager)app.getSystemService(Context.WINDOW_SERVICE);
        if(manager==null)throw new IllegalStateException("window_manager_missing");
        window=new WindowManager.LayoutParams(
            compactWidth(),
            compactHeight(),
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
        root.setPadding(dp(app,2),dp(app,2),dp(app,2),dp(app,2));
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
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(app,40)));
        caption=new TextView(app);
        caption.setText("⠿");
        caption.setTextColor(0xFFE0E7FF);caption.setTextSize(15);
        caption.setGravity(Gravity.CENTER);
        caption.setSingleLine(true);
        caption.setContentDescription("拖动悬浮窗位置");
        title.addView(caption,new LinearLayout.LayoutParams(dp(app,28),-1));
        final float[] drag=new float[2];
        final int[] pos=new int[2];
        caption.setOnTouchListener((v,event)->{
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    drag[0]=event.getRawX();drag[1]=event.getRawY();
                    pos[0]=window.x;pos[1]=window.y;return true;
                case MotionEvent.ACTION_MOVE:
                    if(fullscreenWindow)return true;
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
        account.setSingleLine(true);
        account.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.addView(account,new LinearLayout.LayoutParams(0,dp(app,38),1));
        account.setOnClickListener(v->showAccounts());
        quickControl=button(app,"设置");
        quickControl.setContentDescription("仅管理当前账号的标签备注和额度记录");
        title.addView(quickControl,new LinearLayout.LayoutParams(dp(app,42),dp(app,38)));
        quickControl.setOnClickListener(v->{
            if(quickOpen)hideQuickSettings();else showQuickSettings();
        });
        fullscreenControl=button(app,"全屏");
        fullscreenControl.setContentDescription("返回全屏，保留当前网页");
        title.addView(fullscreenControl,new LinearLayout.LayoutParams(dp(app,51),dp(app,38)));
        fullscreenControl.setOnClickListener(v->setFullscreenWindow(!fullscreenWindow));
        fold=button(app,"—");
        title.addView(fold,new LinearLayout.LayoutParams(dp(app,38),dp(app,38)));
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
        title.addView(closeControl,new LinearLayout.LayoutParams(dp(app,34),dp(app,38)));
        closeControl.setOnClickListener(v->close());
        body=new LinearLayout(app);body.setOrientation(LinearLayout.VERTICAL);
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        buildQuickPanel();
        addressBar=new LinearLayout(app);
        addressBar.setVisibility(View.GONE);
        body.addView(addressBar,new LinearLayout.LayoutParams(-1,dp(app,39)));
        EditText url=new EditText(app);
        url.setSingleLine(true);url.setTextSize(11);url.setTextColor(0xFFF1F4FF);
        url.setHintTextColor(0xFFB8BFCE);url.setHint("输入 https:// 网址");
        url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|
            android.text.InputType.TYPE_TEXT_VARIATION_URI);
        addressBar.addView(url,new LinearLayout.LayoutParams(0,-1,1));
        url.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN)
                setOverlayFocusable(true);
            return false;
        });
        Button go=button(app,"前往");addressBar.addView(go,
            new LinearLayout.LayoutParams(dp(app,52),dp(app,38)));
        go.setOnClickListener(v->{
            navigate(url.getText().toString());
            addressExpanded=false;
            addressBar.setVisibility(View.GONE);
            setOverlayFocusable(false);
        });
        viewport=new FrameLayout(app);
        body.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=new LinearLayout(app);
        body.addView(footer,new LinearLayout.LayoutParams(-1,dp(app,39)));
        Button back=button(app,"后退"),reload=button(app,"刷新"),home=button(app,"主页");
        addressControl=button(app,"网址");
        addressControl.setContentDescription("显示或隐藏网址输入栏，不刷新网页");
        fullscreenFooter=button(app,"全屏");
        fullscreenFooter.setContentDescription("全屏／还原当前网页，不刷新");
        for(Button b:new Button[]{back,reload,home,addressControl,fullscreenFooter})
            footer.addView(b,new LinearLayout.LayoutParams(0,dp(app,38),1));
        back.setOnClickListener(v->callSession("goBack"));
        reload.setOnClickListener(v->callSession("reload"));
        home.setOnClickListener(v->navigate(AccountBrowserSlots.home(selectedProvider)));
        addressControl.setOnClickListener(v->{
            addressExpanded=!addressExpanded;
            addressBar.setVisibility(addressExpanded?View.VISIBLE:View.GONE);
            if(!addressExpanded)setOverlayFocusable(false);
        });
        fullscreenFooter.setOnClickListener(v->setFullscreenWindow(!fullscreenWindow));
        manager.addView(root,window);
        setMinimized(true); // Open as a small, non-IME-stealing bubble.
    }

    /**
     * Overlay-inline account editing: no Activity token is needed while
     * OmniGate is in the background. Hiding this panel DOES NOT detach the
     * GeckoView, close the session or load a URL.
     */
    private static EditText quickInput(LinearLayout parent,String hint){
        EditText e=new EditText(app);
        e.setSingleLine(true);e.setTextSize(13);
        e.setTextColor(0xFFF4F6FF);e.setHintTextColor(0xFF9DACC7);
        e.setHint(hint);
        parent.addView(e,new LinearLayout.LayoutParams(-1,dp(app,43)));
        e.setOnTouchListener((v,event)->{
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN)
                setOverlayFocusable(true);
            return false;
        });
        return e;
    }
    private static void buildQuickPanel(){
        quickPanel=new android.widget.ScrollView(app);
        quickPanel.setFillViewport(false);
        quickPanel.setVisibility(View.GONE);
        root.addView(quickPanel,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=new LinearLayout(app);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(app,8),dp(app,4),dp(app,8),dp(app,8));
        quickPanel.addView(content);
        quickStatus=new TextView(app);
        quickStatus.setTextColor(0xFF82E6E7);quickStatus.setTextSize(12);
        content.addView(quickStatus);
        quickName=quickInput(content,"账号名称（最多28字）");
        quickTags=quickInput(content,"账号标签：主力、备用（最多4个）");
        quickNote=quickInput(content,"账号备注（最多140字）");
        Button saveInfo=button(app,"保存名称 / 账号标签 / 备注");
        content.addView(saveInfo,new LinearLayout.LayoutParams(-1,dp(app,41)));
        saveInfo.setOnClickListener(v->{
            if(selected<0)return;
            try{
                AccountMetadataStore.setAccountInfo(app,selectedProvider,selected,
                    quickName.getText().toString(),quickNote.getText().toString(),
                    quickTags.getText().toString());
                account.setText(AccountMetadataStore.name(app,selectedProvider,selected)+" ▾");
                Toast.makeText(app,"当前账号信息已保存",Toast.LENGTH_SHORT).show();
                setOverlayFocusable(false);
                refreshQuickPanel();
            }catch(RuntimeException error){
                quickTags.setError("账号标签最多4个、每个16字；请检查名称和备注长度");
            }
        });
        TextView quotaTitle=new TextView(app);
        quotaTitle.setText("额度状态和恢复时间（一次选择）");
        quotaTitle.setTextSize(12);quotaTitle.setTextColor(0xFFFFD29C);
        content.addView(quotaTitle);
        Button pickPreset=button(app,"选择常用额度预设 ▾");
        content.addView(pickPreset,new LinearLayout.LayoutParams(-1,dp(app,41)));
        pickPreset.setOnClickListener(v->showQuotaPresets(pickPreset));
        quickQuotaLabel=quickInput(content,"自定义额度状态（最多24字）");
        quickQuotaHours=quickInput(content,"自定义恢复小时（1–72）");
        quickQuotaHours.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        quickIndefinite=new android.widget.CheckBox(app);
        quickIndefinite.setText("标记持续到手动解除");
        quickIndefinite.setTextColor(0xFFDEE5FF);
        content.addView(quickIndefinite);
        quickSavePreset=new android.widget.CheckBox(app);
        quickSavePreset.setText("保存为常用组合预设");
        quickSavePreset.setTextColor(0xFFDEE5FF);
        content.addView(quickSavePreset);
        LinearLayout bar=new LinearLayout(app);
        content.addView(bar,new LinearLayout.LayoutParams(-1,dp(app,41)));
        Button saveQuota=button(app,"保存自定义额度");
        Button clearQuota=button(app,"解除标记");
        bar.addView(saveQuota,new LinearLayout.LayoutParams(0,-1,2));
        bar.addView(clearQuota,new LinearLayout.LayoutParams(0,-1,1));
        saveQuota.setOnClickListener(v->saveCustomQuota());
        clearQuota.setOnClickListener(v->{
            try{
                AccountMetadataStore.setQuotaMark(app,selectedProvider,selected,"",-1,
                    System.currentTimeMillis());
                refreshQuickPanel();
                Toast.makeText(app,"已解除当前账号额度标记",Toast.LENGTH_SHORT).show();
                setOverlayFocusable(false);
            }catch(RuntimeException error){
                Toast.makeText(app,"解除失败",Toast.LENGTH_SHORT).show();
            }
        });
        Button returnPage=button(app,"返回当前网页（不刷新）");
        content.addView(returnPage,new LinearLayout.LayoutParams(-1,dp(app,42)));
        returnPage.setOnClickListener(v->hideQuickSettings());
    }
    private static void showQuickSettings(){
        if(root==null||selected<0||selectedProvider.isEmpty())return;
        if(minimized)setMinimized(false);
        quickOpen=true;
        body.setVisibility(View.GONE);
        quickPanel.setVisibility(View.VISIBLE);
        quickControl.setText("返回");
        refreshQuickPanel();
        // Start non-focusable: focus only when user taps an EditText.
        setOverlayFocusable(false);
    }
    private static void hideQuickSettings(){
        quickOpen=false;
        if(quickPanel!=null)quickPanel.setVisibility(View.GONE);
        if(body!=null)body.setVisibility(minimized?View.GONE:View.VISIBLE);
        if(quickControl!=null)quickControl.setText("设置");
        setOverlayFocusable(false);
    }
    private static void refreshQuickPanel(){
        if(selected<0||quickStatus==null||app==null)return;
        String provider=selectedProvider;
        int slot=selected;
        quickName.setText(AccountMetadataStore.name(app,provider,slot));
        quickTags.setText(AccountMetadataStore.accountTags(app,provider,slot));
        quickNote.setText(AccountMetadataStore.note(app,provider,slot));
        String current=AccountMetadataStore.status(app,provider,slot,
            System.currentTimeMillis());
        quickStatus.setText(provider.toUpperCase(java.util.Locale.ROOT)+" / "+
            AccountMetadataStore.name(app,provider,slot)+
            "\n额度记录："+(current.isEmpty()?"未标记":current)+
            "\n仅影响当前账号，不更换登录空间");
        String label=AccountMetadataStore.quotaLabel(app,provider,slot,
            System.currentTimeMillis());
        quickQuotaLabel.setText(label.isEmpty()?"额度已无":label);
        quickQuotaHours.setText("5");
    }
    private static void showQuotaPresets(Button anchor){
        if(selected<0||app==null)return;
        AccountMetadataStore.QuotaPreset[] presets=
            AccountMetadataStore.quotaPresets(app);
        PopupMenu menu=new PopupMenu(app,anchor);
        for(int i=0;i<presets.length;i++)
            menu.getMenu().add(0,i+1,i,presets[i].title());
        menu.getMenu().add(0,100,100,"解除当前账号额度标记");
        menu.setOnMenuItemClickListener(item->{
            try{
                if(item.getItemId()==100){
                    AccountMetadataStore.setQuotaMark(app,selectedProvider,
                        selected,"",-1,System.currentTimeMillis());
                }else{
                    int index=item.getItemId()-1;
                    if(index<0||index>=presets.length)return true;
                    AccountMetadataStore.QuotaPreset preset=presets[index];
                    AccountMetadataStore.setQuotaMark(app,selectedProvider,
                        selected,preset.label,preset.hours,System.currentTimeMillis());
                }
                refreshQuickPanel();
                Toast.makeText(app,"额度记录已更新",Toast.LENGTH_SHORT).show();
            }catch(RuntimeException error){
                Toast.makeText(app,"额度记录保存失败",Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        menu.show();
    }
    private static void saveCustomQuota(){
        if(selected<0||app==null)return;
        try{
            int hours=quickIndefinite.isChecked()?0:
                Integer.parseInt(quickQuotaHours.getText().toString().trim());
            if(hours<0||hours>72||(!quickIndefinite.isChecked()&&hours==0))
                throw new IllegalArgumentException("hours");
            String label=quickQuotaLabel.getText().toString().trim();
            AccountMetadataStore.setQuotaMark(app,selectedProvider,selected,
                label,hours,System.currentTimeMillis());
            if(quickSavePreset.isChecked())try{
                AccountMetadataStore.saveQuotaPreset(app,label,hours);
            }catch(RuntimeException ignored){
                Toast.makeText(app,"额度已保存，常用预设未保存",Toast.LENGTH_SHORT).show();
            }
            refreshQuickPanel();
            Toast.makeText(app,"额度状态已记录",Toast.LENGTH_SHORT).show();
            setOverlayFocusable(false);
        }catch(RuntimeException error){
            quickQuotaHours.setError("请输入1–72小时，或选手动解除；状态最多24字");
        }
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
        int n=AccountBrowserStore.count(app,selectedProvider);
        for(int slot:AccountMetadataStore.sorted(app,selectedProvider,
                System.currentTimeMillis())){
            String name=AccountMetadataStore.name(app,selectedProvider,slot);
            String mark=AccountMetadataStore.status(app,selectedProvider,slot,
                System.currentTimeMillis());
            menu.getMenu().add(0,slot+1,slot,name+(slot==selected?" ✓":"")+
                (mark.isEmpty()?"":" · "+mark));
        }
        if(n<AccountBrowserSlots.MAX_SLOTS)
            menu.getMenu().add(0,100,100,"新增当前平台隔离账号");
        menu.getMenu().add(0,101,101,
            fullscreenWindow?"还原窗口（不刷新网页）":"全屏显示（不刷新网页）");
        menu.getMenu().add(0,102,102,"设置当前账号 · 标签 / 备注 / 额度");
        menu.getMenu().add(0,103,103,
            tallWindow?"窗口大小 · 恢复标准高度":"窗口大小 · 加高阅读区");
        menu.setOnMenuItemClickListener(item->{
            if(item.getItemId()==103){
                tallWindow=!tallWindow;
                applyWindowGeometry(); // no navigation or GeckoSession change
                return true;
            }
            if(item.getItemId()==102){
                showQuickSettings();
                return true;
            }
            if(item.getItemId()==101){
                setFullscreenWindow(!fullscreenWindow);
                return true;
            }
            try{
                int chosen=item.getItemId()==100
                    ?AccountBrowserStore.create(app,selectedProvider)
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
        String id=AccountBrowserStore.contextId(app,selectedProvider,slot);
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
        AccountBrowserStore.select(app,selectedProvider,slot);
        selected=slot;
        account.setText(AccountMetadataStore.name(app,selectedProvider,slot)+" ▾");
        caption.setText(selectedProvider.toUpperCase(java.util.Locale.ROOT)+" · 网页悬浮窗");
        navigate(AccountBrowserSlots.home(selectedProvider));
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
    /**
     * Expand/restores the *same* WindowManager view tree. Never detaches
     * GeckoView, creates another GeckoSession or calls loadUri().
     * System bars are owned by Android and may remain visible for overlays.
     */
    private static void setFullscreenWindow(boolean enabled){
        if(root==null||manager==null||window==null)return;
        if(minimized)setMinimized(false);
        if(fullscreenWindow==enabled)return;
        if(enabled){
            compactX=window.x;
            compactY=window.y;
        }
        fullscreenWindow=enabled;
        // Keep the overlay passive until explicit text input; otherwise
        // unrelated applications can lose their keyboard focus.
        setOverlayFocusable(false);
        applyWindowGeometry();
    }

    private static void applyWindowGeometry(){
        if(root==null||manager==null||window==null)return;
        if(minimized){
            window.width=dp(app,BUBBLE_SIZE_DP);
            window.height=dp(app,BUBBLE_SIZE_DP);
        }else if(fullscreenWindow){
            window.width=WindowManager.LayoutParams.MATCH_PARENT;
            window.height=WindowManager.LayoutParams.MATCH_PARENT;
            window.x=0;
            window.y=0;
        }else{
            window.width=compactWidth();
            window.height=compactHeight();
            window.x=compactX;
            window.y=compactY;
        }
        if(window.width>0){
            int maxX=Math.max(0,
                app.getResources().getDisplayMetrics().widthPixels-window.width);
            window.x=Math.max(0,Math.min(maxX,window.x));
        }
        if(window.height>0){
            int maxY=Math.max(0,
                app.getResources().getDisplayMetrics().heightPixels-window.height);
            window.y=Math.max(0,Math.min(maxY,window.y));
        }
        if(fullscreenControl!=null){
            fullscreenControl.setText(fullscreenWindow?"还原":"全屏");
            if(fullscreenFooter!=null)
                fullscreenFooter.setText(fullscreenWindow?"还原":"全屏");
            fullscreenControl.setContentDescription(fullscreenWindow
                ?"还原悬浮窗，保留网页"
                :"返回全屏，保留当前网页");
        }
        manager.updateViewLayout(root,window);
    }

    private static void setMinimized(boolean value){
        if(root==null||manager==null)return;
        minimized=value;
        // Minimized always returns IME ownership to the underlying app.
        // Expanded starts passive and only acquires focus on direct input.
        setOverlayFocusable(false);
        if(value)quickOpen=false;
        body.setVisibility(value||quickOpen?View.GONE:View.VISIBLE);
        if(quickPanel!=null)quickPanel.setVisibility(
            !value&&quickOpen?View.VISIBLE:View.GONE);
        caption.setVisibility(value?View.GONE:View.VISIBLE);
        account.setVisibility(value?View.GONE:View.VISIBLE);
        quickControl.setVisibility(value?View.GONE:View.VISIBLE);
        fullscreenControl.setVisibility(value?View.GONE:View.VISIBLE);
        closeControl.setVisibility(value?View.GONE:View.VISIBLE);
        // Remember the last compact placement only when leaving compact.
        if(value&&!fullscreenWindow && window.width>0){
            compactX=window.x;
            compactY=window.y;
        }
        fold.setText(value?"C":"—");
        fold.setContentDescription(value?"展开 Claude 网页悬浮窗":"收起 Claude 网页悬浮窗");
        applyWindowGeometry();
    }
    static boolean closeIfMatches(String provider,int slot){
        if(!showing()||!provider.equals(selectedProvider)||selected!=slot)
            return false;
        close();
        return true;
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
        root=null;body=null;addressBar=null;addressControl=null;
        addressExpanded=false;tallWindow=false;
        viewport=null;caption=null;account=null;fold=null;
        closeControl=null;fullscreenControl=null;fullscreenFooter=null;
        quickControl=null;quickPanel=null;quickName=null;quickTags=null;
        quickNote=null;quickQuotaLabel=null;quickQuotaHours=null;
        quickIndefinite=null;quickSavePreset=null;quickStatus=null;
        quickOpen=false;
        window=null;manager=null;app=null;
        selected=-1;selectedProvider="";minimized=false;
        fullscreenWindow=false;inputFocusHeld=false;
        compactX=0;compactY=0;
    }
}
