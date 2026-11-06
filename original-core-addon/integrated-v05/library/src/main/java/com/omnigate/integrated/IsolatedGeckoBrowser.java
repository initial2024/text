package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.graphics.Insets;
import android.view.DisplayCutout;
import android.widget.Button;
import android.widget.PopupMenu;
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
    // Same-owner rule borrowed from DSH browser ownership: no two auxiliary
    // Gecko sessions run silently in parallel, even across providers.
    private static IsolatedGeckoBrowser activeBrowser;
    // Sticky fail-closed gate: a failed Gecko close must never authorize reuse
    // of another account context until a fresh, audited application process.
    private static boolean sessionCloseFailed;
    static boolean ownerHealthy(){return !sessionCloseFailed;}
    static boolean ownsLiveSession(Object expected){
        IsolatedGeckoBrowser current=activeBrowser;
        return current!=null && current.session==expected && 
            current.dialog.isShowing() && !sessionCloseFailed;
    }
    private final Activity host;
    private final String provider;
    private final int startingSlot;
    private final boolean guestWindow;
    private String guestContextId;
    private final Dialog dialog;
    private final LinearLayout tabs;
    private final FrameLayout display;
    private final LinearLayout root;
    private final Button fullscreenExit;
    private final EditText addressInput;
    private String currentUrl;
    private String lastPageFailure;
    private boolean contentProcessTerminated;
    private boolean fullscreenMode;
    private float fullscreenEdgeRatio=0.42f;
    private float dragStartRatio,dragStartY;
    private boolean movingEdgeControl;
    private int lastCutoutTop,lastCutoutRight;
    private int originalCutoutMode=-1;
    private final java.util.IdentityHashMap<View,Integer> originalChromeVisibility=
        new java.util.IdentityHashMap<>();
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
    static void closeActiveBrowser(){
        IsolatedGeckoBrowser prior=activeBrowser;
        activeBrowser=null;
        if(prior!=null){
            // Close the GeckoSession synchronously before asynchronous
            // dialog-dismiss callbacks: storage clearing must not race a
            // still-open page that could re-create cookies.
            prior.releaseSession();
            if(prior.dialog.isShowing())prior.dialog.dismiss();
        }
    }
    static boolean closeIfMatches(String provider,int slot){
        IsolatedGeckoBrowser running=activeBrowser;
        if(running==null||!running.provider.equals(provider)||
            running.guestWindow||running.selected!=slot)return false;
        closeActiveBrowser();
        return true;
    }
    static boolean closeIfMatchesForErase(String provider,int slot){
        IsolatedGeckoBrowser running=activeBrowser;
        boolean matches=running!=null&&running.provider.equals(provider)&&
            !running.guestWindow&&running.selected==slot;
        if(matches)closeActiveBrowser();
        if(sessionCloseFailed)throw new IllegalStateException("gecko_session_close_failed");
        return matches;
    }
    static void showPicker(Activity host){
        // Merely opening the account list must not refresh or close an
        // existing login. Ownership changes only on explicit account open.
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
            subtitle.setText("按需打开 · 五个平台均可使用独立网页悬浮窗");
            subtitle.setTextSize(12);subtitle.setTextColor(muted);
            subtitle.setPadding(0,dp(host,6),0,dp(host,12));
            shell.addView(subtitle);
            Button manage=new Button(host);
            manage.setAllCaps(false);
            manage.setText("管理 · 搜索 / 筛选 / 备注 / 额度 / 清理");
            manage.setTextColor(ink);manage.setBackground(surface(0xFF28355A,12,host));
            shell.addView(manage,new LinearLayout.LayoutParams(-1,dp(host,46)));
            manage.setOnClickListener(v->{picker.dismiss();AccountManagerUi.show(host);});
            ScrollView scroll=new ScrollView(host);
            scroll.setFillViewport(false);
            LinearLayout options=new LinearLayout(host);
            options.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(options);
            // Compact accordion: just five provider headings are shown.
            // Expand only one provider and keep each account on ONE row.
            final int providerCount=AccountBrowserSlots.PROVIDERS.length;
            final LinearLayout[] groups=new LinearLayout[providerCount];
            final Button[] headings=new Button[providerCount];
            String last=AccountBrowserStore.lastSelectedProvider(host);
            int initial=0;
            for(int i=0;i<providerCount;i++)
                if(AccountBrowserSlots.PROVIDERS[i].equals(last))initial=i;
            final int initialOpen=initial;
            for(int p=0;p<providerCount;p++){
                final int providerIndex=p;
                final String provider=AccountBrowserSlots.PROVIDERS[p];
                int count=AccountBrowserStore.count(host,provider);
                int selected=AccountBrowserStore.selected(host,provider);
                Button heading=new Button(host);
                heading.setAllCaps(false);
                heading.setTextSize(14);heading.setTextColor(0xFF87E6EF);
                heading.setGravity(android.view.Gravity.CENTER_VERTICAL|
                    android.view.Gravity.START);
                heading.setPadding(dp(host,12),0,dp(host,8),0);
                heading.setBackground(surface(0xFF182441,12,host));
                LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(host,43));
                hp.bottomMargin=dp(host,5);
                options.addView(heading,hp);
                headings[p]=heading;
                LinearLayout group=new LinearLayout(host);
                group.setOrientation(LinearLayout.VERTICAL);
                group.setPadding(dp(host,5),0,dp(host,5),dp(host,6));
                options.addView(group);
                groups[p]=group;
                group.setVisibility(p==initialOpen?View.VISIBLE:View.GONE);
                for(int slot:AccountMetadataStore.sorted(host,provider,
                        System.currentTimeMillis())){
                    final int index=slot;
                    LinearLayout line=new LinearLayout(host);
                    line.setOrientation(LinearLayout.HORIZONTAL);
                    line.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(host,46));
                    lp.bottomMargin=dp(host,4);group.addView(line,lp);
                    Button entry=new Button(host);
                    entry.setAllCaps(false);
                    String quota=AccountMetadataStore.status(host,provider,slot,
                        System.currentTimeMillis());
                    String tags=AccountMetadataStore.accountTags(host,provider,slot);
                    entry.setText(AccountMetadataStore.name(host,provider,slot)+
                        (tags.isEmpty()?"":" · "+tags)+
                        (quota.isEmpty()?"":" · ⏳")+
                        (slot==selected?" ✓":""));
                    entry.setContentDescription(
                        AccountMetadataStore.name(host,provider,slot)+
                        (tags.isEmpty()?"":"；标签 "+tags)+
                        (quota.isEmpty()?"":"；"+quota)+
                        "。点击打开网页，长按编辑账号设置。");
                    entry.setSingleLine(true);
                    entry.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    entry.setGravity(android.view.Gravity.CENTER_VERTICAL|
                        android.view.Gravity.START);
                    entry.setTextColor(ink);entry.setTextSize(13);
                    entry.setPadding(dp(host,10),0,dp(host,4),0);
                    entry.setBackground(surface(slot==selected?
                        0xFF35336C:0xFF1B2443,10,host));
                    line.addView(entry,new LinearLayout.LayoutParams(0,-1,1f));
                    entry.setOnClickListener(v->{picker.dismiss();show(host,provider,index);});
                    entry.setOnLongClickListener(v->{
                        picker.dismiss();AccountQuickSettings.show(host,provider,index);
                        return true;
                    });
                    Button floatOpen=new Button(host);
                    floatOpen.setAllCaps(false);
                    floatOpen.setText("浮窗");
                    floatOpen.setTextSize(12);floatOpen.setTextColor(ink);
                    floatOpen.setPadding(0,0,0,0);
                    floatOpen.setBackground(surface(0xFF253858,10,host));
                    LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(
                        dp(host,57),-1);
                    op.leftMargin=dp(host,5);line.addView(floatOpen,op);
                    floatOpen.setOnClickListener(v->{
                        picker.dismiss();ClaudeFloatingOverlay.show(host,provider,index);
                    });
                    Button quick=new Button(host);
                    quick.setAllCaps(false);quick.setText("设置");
                    quick.setTextColor(ink);quick.setTextSize(11);
                    quick.setPadding(0,0,0,0);
                    quick.setBackground(surface(0xFF2B3155,10,host));
                    LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(
                        dp(host,49),-1);
                    qp.leftMargin=dp(host,4);line.addView(quick,qp);
                    quick.setContentDescription("设置"+AccountMetadataStore.name(host,provider,index)+
                        "的名称、标签、备注、额度记录");
                    quick.setOnClickListener(v->{
                        picker.dismiss();AccountQuickSettings.show(host,provider,index);
                    });
                }
                Button guest=new Button(host);
                guest.setAllCaps(false);
                guest.setText("临时访客 · 不保存登录");
                guest.setTextColor(ink);guest.setTextSize(12);
                guest.setBackground(surface(0xFF26314E,10,host));
                LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,dp(host,39));
                group.addView(guest,gp);
                guest.setOnClickListener(v->{picker.dismiss();showPrivateGuest(host,provider);});
                heading.setOnClickListener(v->{
                    boolean expanding=groups[providerIndex].getVisibility()!=View.VISIBLE;
                    for(int i=0;i<providerCount;i++){
                        groups[i].setVisibility(
                            expanding&&i==providerIndex?View.VISIBLE:View.GONE);
                        headings[i].setText((expanding&&i==providerIndex?"⌄ ":"› ")+
                            AccountBrowserSlots.LABELS[i]+" · "+
                            AccountBrowserStore.count(host,AccountBrowserSlots.PROVIDERS[i]));
                    }
                });
                heading.setText((p==initialOpen?"⌄ ":"› ")+
                    AccountBrowserSlots.LABELS[p]+" · "+count);
            }
            shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
            TextView notice=new TextView(host);
            notice.setText("点击平台展开 · 每账号可选择网页或悬浮窗");
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
                Math.min(Math.round(host.getResources().getDisplayMetrics().heightPixels*.76f),
                    dp(host,555)));
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
        openOwnedBrowser(host,provider,slot);
    }
    private static void showPrivateGuest(Activity host,String provider){
        if(!AccountBrowserSlots.supported(provider))
            throw new IllegalArgumentException("provider");
        openOwnedBrowser(host,provider,-1);
    }
    private static void openOwnedBrowser(Activity host,String provider,int slot){
        ClaudeFloatingOverlay.close();
        closeActiveBrowser();
        if(sessionCloseFailed||!ClaudeFloatingOverlay.ownerHealthy())
            throw new IllegalStateException("previous_gecko_close_failed");
        IsolatedGeckoBrowser next=new IsolatedGeckoBrowser(host,provider,slot);
        activeBrowser=next;
        try{next.show();}
        catch(RuntimeException failure){
            if(activeBrowser==next)activeBrowser=null;
            next.releaseSession();
            throw failure;
        }
    }
    /**
     * Source owner of a LIVE GeckoSession. No loadUri/reload or cookie move.
     * The old dialog must lose its session reference BEFORE dismissal.
     */
    private void handoffToFloatingWindow(){
        if(guestWindow||selected<0||session==null||geckoView==null||
            activeBrowser!=this||!dialog.isShowing()){
            state.setText("当前没有可安全转移的持久账号网页");
            return;
        }
        Object oldView=geckoView;
        // A SAF picker initiated in the old window must not complete
        // after this manual owner transfer. Browser cookies remain intact.
        GeckoBrowserTransfers.cancelSession(host,session);
        if(!ClaudeFloatingOverlay.adoptLiveBrowser(host,provider,selected,
                session,oldView)){
            state.setText("无刷新转移未完成；原网页保留，请检查悬浮窗权限或窗口状态");
            return;
        }
        // On success the SAME GeckoSession is now attached to the overlay.
        // Do not call GeckoSession.close() from Dialog.onDismiss.
        session=null;
        geckoView=null;
        selected=-1;
        currentUrl=null;
        if(oldView instanceof View)display.removeView((View)oldView);
        if(activeBrowser==this)activeBrowser=null;
        dialog.dismiss();
    }

    /**
     * Target owner when a live overlay is explicitly returned to the full
     * account browser. A newly created GeckoView receives the existing
     * GeckoSession. No new GeckoRuntime, session or navigation is created.
     */
    static boolean adoptLiveOverlay(Activity host,String provider,int slot,
            Object existingSession,Object oldOverlayView){
        if(host==null||host.isFinishing()||host.isDestroyed()||
           !AccountBrowserSlots.supported(provider)||slot<0||
           slot>=AccountBrowserStore.count(host,provider)||activeBrowser!=null||
           sessionCloseFailed || !ClaudeFloatingOverlay.ownsLiveSession(existingSession))
            return false;
        IsolatedGeckoBrowser target=null;
        boolean moved=false;
        try{
            String id=AccountBrowserStore.contextId(host,provider,slot);
            GeckoLiveWindowHandoff.verify(existingSession,id);
            target=new IsolatedGeckoBrowser(host,provider,slot);
            target.showFrameOnly();
            Class<?> geckoViewType=Class.forName("org.mozilla.geckoview.GeckoView");
            Object newView=geckoViewType.getConstructor(Context.class).newInstance(host);
            if(!(newView instanceof View))throw new IllegalStateException("new_gecko_view_invalid");
            target.display.addView((View)newView,0,
                new FrameLayout.LayoutParams(-1,-1));
            // Source still holds live ownership until a verified transfer.
            GeckoLiveWindowHandoff.move(oldOverlayView,newView,existingSession,id);
            moved=true;
            target.session=existingSession;
            target.geckoView=newView;
            target.selected=slot;
            activeBrowser=target;
            target.followCurrentLocation(existingSession,
                Class.forName("org.mozilla.geckoview.GeckoSession"));
            target.followClaudePageProgress(existingSession,
                Class.forName("org.mozilla.geckoview.GeckoSession"));
            target.renderTabs();
            target.refreshLocalBudget();
            target.state.setText("已无刷新返回当前账号网页；登录和编辑中的内容仍在原 GeckoSession");
            return true;
        }catch(Throwable failure){
            android.util.Log.w("OmniGateBrowser","overlay_to_page_handoff_rejected",
                failure);
            if(target!=null){
                if(moved){
                    // No destructive fallback: a failed late UI operation
                    // must not close the live Gecko session.
                    target.state.setText("网页转移后界面更新异常；请勿清理账号数据");
                    return true;
                }
                target.session=null;target.geckoView=null;
                if(activeBrowser==target)activeBrowser=null;
                if(target.dialog.isShowing())target.dialog.dismiss();
            }
            return false;
        }
    }

    private IsolatedGeckoBrowser(Activity host,String provider,int slot){
        this.host=host;this.provider=provider;this.startingSlot=slot;
        this.guestWindow=slot==-1;
        // Not a floating alert window: use a genuine edge-to-edge screen
        // surface so the GeckoView can occupy all pixels in immersive mode.
        dialog=new Dialog(host,android.R.style.Theme_Material_NoActionBar){
            @Override public void onWindowFocusChanged(boolean focused){
                super.onWindowFocusChanged(focused);
                if(focused && fullscreenMode){
                    Window window=getWindow();
                    if(window!=null)window.getDecorView().post(()->{
                        if(fullscreenMode)applyFullscreenWindow(true);
                    });
                }
            }
        };
        root=new LinearLayout(host);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(host,12),dp(host,12),dp(host,12),dp(host,8));
        root.setBackgroundColor(0xFF0B1024);
        TextView info=new TextView(host);
        info.setText("OmniGate  /  "+provider.toUpperCase(java.util.Locale.ROOT)+
            (guestWindow?"\n临时访客 · 关闭后清除该空间的站点存储 · 可能仍被网页指纹识别":
            AccountBrowserSlots.browserOnly(provider)
                ? "\n仅浏览器登录 · 不支持 API · 不会后台预加载"
                : "\n隔离账号空间 · 与原版 8787 主执行登录独立"));
        info.setTextSize(16);
        info.setTypeface(null,android.graphics.Typeface.BOLD);
        info.setPadding(dp(host,8),dp(host,5),dp(host,8),dp(host,9));
        info.setTextColor(0xFFF3F4FF);
        // Always-visible native account entry: Claude's own avatar/menu may
        // appear later because the site hydrates asynchronously. This control
        // never asserts login success, touches site DOM, or loads a second page.
        LinearLayout nativeHeader=new LinearLayout(host);
        nativeHeader.setGravity(android.view.Gravity.CENTER_VERTICAL);
        nativeHeader.addView(info,new LinearLayout.LayoutParams(0,-2,1f));
        if(!guestWindow){
            Button accountEntry=button("账号空间 ▾");
            accountEntry.setContentDescription(
                "OmniGate 本地账号空间菜单，不是网站登录头像");
            accountEntry.setOnClickListener(v->showNativeAccountMenu(accountEntry));
            nativeHeader.addView(accountEntry,
                new LinearLayout.LayoutParams(dp(host,102),dp(host,44)));
        }
        root.addView(nativeHeader);
        HorizontalScrollView scroll=new HorizontalScrollView(host);
        tabs=new LinearLayout(host);tabs.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(tabs);
        root.addView(scroll);
        if(guestWindow)scroll.setVisibility(View.GONE);
        // Browsing always stays inside the manually selected GeckoSession.
        LinearLayout addressBar=new LinearLayout(host);
        addressBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        addressBar.setPadding(0,dp(host,5),0,dp(host,5));
        addressInput=new EditText(host);
        addressInput.setSingleLine(true);
        addressInput.setTextSize(12);
        addressInput.setTextColor(0xFFF3F4FF);
        addressInput.setHintTextColor(0xFF9CA6C8);
        addressInput.setHint("网址 / 粘贴验证链接");
        addressInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        addressInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        addressInput.setPadding(dp(host,10),0,dp(host,7),0);
        addressInput.setBackground(surface(0xFF1B2443,10,host));
        addressInput.setOnEditorActionListener((v,action,event)->{
            if(action==android.view.inputmethod.EditorInfo.IME_ACTION_GO ||
                (event!=null && event.getKeyCode()==android.view.KeyEvent.KEYCODE_ENTER &&
                 event.getAction()==android.view.KeyEvent.ACTION_DOWN)){
                navigateTo(addressInput.getText().toString());return true;
            }
            return false;
        });
        addressBar.addView(addressInput,new LinearLayout.LayoutParams(0,dp(host,44),1f));
        Button go=button("前往");
        go.setOnClickListener(v->navigateTo(addressInput.getText().toString()));
        addressBar.addView(go,new LinearLayout.LayoutParams(dp(host,50),dp(host,44)));
        Button forward=button("前进");
        forward.setOnClickListener(v->callSession("goForward"));
        addressBar.addView(forward,new LinearLayout.LayoutParams(dp(host,50),dp(host,44)));
        Button home=button("主页");
        home.setOnClickListener(v->navigateTo(AccountBrowserSlots.home(provider)));
        addressBar.addView(home,new LinearLayout.LayoutParams(dp(host,50),dp(host,44)));
        root.addView(addressBar);
        state=new TextView(host);state.setTextColor(0xFF86E8EE);
        state.setTextSize(12);state.setPadding(dp(host,7),dp(host,6),dp(host,7),dp(host,6));
        root.addView(state);
        TextView instructions=new TextView(host);
        instructions.setText(guestWindow
            ? "临时访客：独立随机空间 + Gecko 私密模式 + 跟踪保护。关闭后清除该空间；不保证网站无法识别设备。"
            : AccountBrowserSlots.browserOnly(provider)
            ? "仅在你点击此账号时连接 claude.ai；切换或关闭即结束当前 Gecko 会话。" +
              "不调用模型 API、不自动发送、不中途轮换账号。"
            : "次数仅来自手动确认；风控时停止切换，不会自动发消息。" +
              "网页 Token 无可信统计来源。");
        instructions.setTextColor(0xFFAFB9DE);
        instructions.setTextSize(11);
        root.addView(instructions);
        localBudgetState=new TextView(host);
        localBudgetState.setTextColor(0xFFCAD3F2);
        localBudgetState.setTextSize(11);
        root.addView(localBudgetState);
        if(AccountBrowserSlots.browserOnly(provider)||guestWindow)
            localBudgetState.setVisibility(View.GONE);
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
        if(AccountBrowserSlots.browserOnly(provider)||guestWindow)
            budgetActions.setVisibility(View.GONE);
        Button mainTarget=button("设为主执行目标 · 核验接线");
        mainTarget.setOnClickListener(v->requestNativeTarget());
        LinearLayout.LayoutParams targetParams=new LinearLayout.LayoutParams(-1,dp(host,45));
        targetParams.topMargin=dp(host,7);
        if(AccountBrowserSlots.nativeExecutorSupported(provider)&&!guestWindow)
            root.addView(mainTarget,targetParams);
        display=new FrameLayout(host);
        root.addView(display,new LinearLayout.LayoutParams(-1,0,1));
        // One movable, low-obstruction edge handle. No buttons over the
        // website's top navigation; Back also exits fullscreen.
        fullscreenExit=button("⋮");
        fullscreenExit.setContentDescription("全屏工具菜单，可上下拖动");
        fullscreenExit.setTextSize(17);
        fullscreenExit.setPadding(0,0,0,0);
        fullscreenExit.setMinWidth(0);fullscreenExit.setMinimumWidth(0);
        fullscreenExit.setMinHeight(0);fullscreenExit.setMinimumHeight(0);
        fullscreenExit.setBackground(surface(0x9914213F,12,host));
        fullscreenExit.setAlpha(0.64f);
        fullscreenExit.setVisibility(View.GONE);
        FrameLayout.LayoutParams edgeParams=new FrameLayout.LayoutParams(
            dp(host,38),dp(host,48),
            android.view.Gravity.TOP|android.view.Gravity.RIGHT);
        display.addView(fullscreenExit,edgeParams);
        fullscreenExit.setOnClickListener(v->showFullscreenActions());
        fullscreenExit.setOnTouchListener((v,event)->{
            switch(event.getActionMasked()){
                case android.view.MotionEvent.ACTION_DOWN:
                    dragStartY=event.getRawY();
                    dragStartRatio=fullscreenEdgeRatio;
                    movingEdgeControl=false;
                    fullscreenExit.setAlpha(1f);
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    float delta=event.getRawY()-dragStartY;
                    if(Math.abs(delta)>dp(host,7))movingEdgeControl=true;
                    if(movingEdgeControl){
                        int height=Math.max(dp(host,100),display.getHeight());
                        fullscreenEdgeRatio=Math.max(0.09f,
                            Math.min(0.86f,dragStartRatio+delta/height));
                        positionFullscreenControls(lastCutoutTop,lastCutoutRight);
                    }
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    fullscreenExit.setAlpha(0.64f);
                    if(!movingEdgeControl)v.performClick();
                    return true;
                case android.view.MotionEvent.ACTION_CANCEL:
                    fullscreenExit.setAlpha(0.64f);
                    return true;
                default:return false;
            }
        });
        display.addOnLayoutChangeListener((v,left,top,right,bottom,
                oldLeft,oldTop,oldRight,oldBottom)->{
            if(fullscreenMode && (bottom-top)!=(oldBottom-oldTop))
                positionFullscreenControls(lastCutoutTop,lastCutoutRight);
        });
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
        Button translate=button("翻译");
        translate.setOnClickListener(v->GeckoPageTranslate.showMenu(host,translate,session,geckoView));
        Button fullscreen=button("全屏模型");
        fullscreen.setOnClickListener(v->setFullscreen(true));
        Button close=button("关闭");close.setOnClickListener(v->dialog.dismiss());
        if(!guestWindow)
            footer.addView(add,new LinearLayout.LayoutParams(0,-2,3f));
        footer.addView(back,new LinearLayout.LayoutParams(0,-2,1f));
        footer.addView(reload,new LinearLayout.LayoutParams(0,-2,1f));
        footer.addView(translate,new LinearLayout.LayoutParams(0,-2,1.1f));
        footer.addView(fullscreen,new LinearLayout.LayoutParams(0,-2,1.8f));
        footer.addView(close,new LinearLayout.LayoutParams(0,-2,1f));
        root.addView(footer);
        if(!guestWindow){
            LinearLayout accountActions=new LinearLayout(host);
            Button floatOpen=button("↗ 当前账号 · 悬浮窗");
            floatOpen.setOnClickListener(v->{
                if(selected>=0)handoffToFloatingWindow();
            });
            accountActions.addView(floatOpen,new LinearLayout.LayoutParams(0,dp(host,42),1));
            Button quick=button("⚙ 账号设置");
            quick.setOnClickListener(v->{
                if(selected>=0)AccountQuickSettings.show(host,provider,selected);
            });
            accountActions.addView(quick,new LinearLayout.LayoutParams(0,dp(host,42),1));
            root.addView(accountActions,new LinearLayout.LayoutParams(-1,dp(host,42)));
        }
        root.setFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            applyBrowserInsets(insets);
            return insets;
        });
        dialog.setContentView(root);
        Window browserWindow=dialog.getWindow();
        if(browserWindow!=null){
            browserWindow.setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(0xFF0B1024));
            browserWindow.getDecorView().setPadding(0,0,0,0);
            browserWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            browserWindow.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        }
        dialog.setOnDismissListener(v->{
            if(fullscreenMode)setFullscreen(false);
            releaseSession();
            if(activeBrowser==this)activeBrowser=null;
        });
        dialog.setOnKeyListener((d,key,event)->{
            if(fullscreenMode && key==android.view.KeyEvent.KEYCODE_BACK){
                if(event.getAction()==android.view.KeyEvent.ACTION_UP)
                    setFullscreen(false);
                return true;
            }
            return false;
        });
    }
    /**
     * Full-screen means a non-floating, edge-to-edge Window, not simply
     * hiding OmniGate buttons inside a dialog-sized viewport.
     */
    private void setFullscreen(boolean enabled){
        if(fullscreenMode==enabled)return;
        fullscreenMode=enabled;
        if(enabled){
            originalChromeVisibility.clear();
            for(int i=0;i<root.getChildCount();i++){
                View child=root.getChildAt(i);
                if(child==display)continue;
                originalChromeVisibility.put(child,child.getVisibility());
                child.setVisibility(View.GONE);
            }
        }else{
            for(java.util.Map.Entry<View,Integer> previous:
                originalChromeVisibility.entrySet())
                previous.getKey().setVisibility(previous.getValue());
            originalChromeVisibility.clear();
        }
        fullscreenExit.setVisibility(enabled?View.VISIBLE:View.GONE);
        applyFullscreenWindow(enabled);
    }
    private void applyFullscreenWindow(boolean enabled){
        Window w=dialog.getWindow();
        if(w==null)return;
        // This is our own Window; no flags or bars on the host Activity change.
        w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
        w.getDecorView().setPadding(0,0,0,0);
        if(android.os.Build.VERSION.SDK_INT>=28){
            WindowManager.LayoutParams attributes=w.getAttributes();
            if(enabled){
                if(originalCutoutMode<0)
                    originalCutoutMode=attributes.layoutInDisplayCutoutMode;
                attributes.layoutInDisplayCutoutMode=
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            }else if(originalCutoutMode>=0){
                attributes.layoutInDisplayCutoutMode=originalCutoutMode;
                originalCutoutMode=-1;
            }
            w.setAttributes(attributes);
        }
        if(android.os.Build.VERSION.SDK_INT>=30){
            // Android 15+ enforces edge-to-edge; explicitly handle insets below.
            w.setDecorFitsSystemWindows(false);
            WindowInsetsController controller=w.getInsetsController();
            if(controller!=null){
                controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                if(enabled)controller.hide(WindowInsets.Type.systemBars());
                else controller.show(WindowInsets.Type.systemBars());
            }
        }else{
            int layout=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            int hidden=View.SYSTEM_UI_FLAG_FULLSCREEN|
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            w.getDecorView().setSystemUiVisibility(layout|(enabled?hidden:0));
            if(enabled)w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            else w.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
        // Recompute normal-mode safe areas after restoring system bars.
        w.getDecorView().requestApplyInsets();
        root.requestLayout();
    }
    private void applyBrowserInsets(WindowInsets insets){
        int barLeft=0,barTop=0,barRight=0,barBottom=0;
        int cutLeft=0,cutTop=0,cutRight=0,cutBottom=0;
        if(android.os.Build.VERSION.SDK_INT>=30){
            Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
            Insets cut=insets.getInsets(WindowInsets.Type.displayCutout());
            barLeft=bars.left;barTop=bars.top;
            barRight=bars.right;barBottom=bars.bottom;
            cutLeft=cut.left;cutTop=cut.top;
            cutRight=cut.right;cutBottom=cut.bottom;
        }else{
            barLeft=insets.getSystemWindowInsetLeft();
            barTop=insets.getSystemWindowInsetTop();
            barRight=insets.getSystemWindowInsetRight();
            barBottom=insets.getSystemWindowInsetBottom();
            if(android.os.Build.VERSION.SDK_INT>=28){
                DisplayCutout cut=insets.getDisplayCutout();
                if(cut!=null){
                    cutLeft=cut.getSafeInsetLeft();cutTop=cut.getSafeInsetTop();
                    cutRight=cut.getSafeInsetRight();cutBottom=cut.getSafeInsetBottom();
                }
            }
        }
        if(fullscreenMode){
            // Website content fills the entire display; only the tiny controls
            // stay out of the display cutout, not the whole Gecko viewport.
            root.setPadding(0,0,0,0);
        }else{
            root.setPadding(
                dp(host,12)+Math.max(barLeft,cutLeft),
                dp(host,12)+Math.max(barTop,cutTop),
                dp(host,12)+Math.max(barRight,cutRight),
                dp(host,8)+Math.max(barBottom,cutBottom));
        }
        positionFullscreenControls(cutTop,cutRight);
    }
    private void positionFullscreenControls(int cutTop,int cutRight){
        lastCutoutTop=cutTop;lastCutoutRight=cutRight;
        int height=Math.max(dp(host,120),display.getHeight());
        int upper=dp(host,8)+cutTop;
        int lower=Math.max(upper,height-dp(host,56));
        int top=Math.max(upper,Math.min(lower,Math.round(height*fullscreenEdgeRatio)));
        int right=dp(host,2)+cutRight;
        FrameLayout.LayoutParams edge=(FrameLayout.LayoutParams)
            fullscreenExit.getLayoutParams();
        if(edge.topMargin!=top || edge.rightMargin!=right){
            edge.topMargin=top;edge.rightMargin=right;
            fullscreenExit.setLayoutParams(edge);
        }
    }

    /**
     * Native account controls do not depend on provider login widgets. The
     * account picker is read-only until the user explicitly chooses a slot.
     * Never infer account authentication from a Gecko navigation event.
     */
    private void showNativeAccountMenu(View anchor){
        if(guestWindow)return;
        PopupMenu menu=new PopupMenu(host,anchor);
        menu.getMenu().add(0,1,0,"切换账号空间（手动）");
        menu.getMenu().add(0,2,1,"当前空间设置");
        menu.getMenu().add(0,3,2,"浏览器环境检查");
        menu.getMenu().add(0,4,3,"刷新当前网页（手动）");
        menu.getMenu().add(0,5,4,"当前空间 · 下载管理");
        menu.getMenu().add(0,6,5,"网页语言偏好 · 查看");
        menu.getMenu().add(0,7,6,"系统分身与隔离说明");
        menu.getMenu().add(0,9,7,"工作资料 DNS 检查（手动）");
        menu.setOnMenuItemClickListener(item->{
            switch(item.getItemId()){
                case 1:showPicker(host);return true;
                case 2:
                    if(selected>=0)AccountQuickSettings.show(host,provider,selected);
                    else state.setText("本地空间尚未打开；未改变任何账号登录");
                    return true;
                case 3:showBrowserEnvironment();return true;
                case 4:callSession("reload");return true;
                case 5:showDownloads();return true;
                case 6:showWebLanguagePreference();return true;
                case 7:AndroidProfileSafetyCenter.show(host);return true;
                default:return false;
            }
        });
        menu.show();
    }

    private void showWebLanguagePreference(){
        String locale=OmniGateBrowserLanguage.requestedLocale(host);
        new AlertDialog.Builder(host).setTitle("当前网页语言偏好")
            .setMessage("Android 应用信息选择的语言："+locale+
                "\nGecko 只将它设置为浏览器后续请求的首选语言。"+
                "不同网站可能保存账号语言设置、忽略 Accept-Language，"+
                "或在登录后延迟渲染页面；本功能不修改 Cookie、UA、网页脚本，也不自动刷新。")
            .setNegativeButton("关闭",null)
            .setPositiveButton("同步语言设置（不刷新网页）",(d,w)->{
                boolean applied=OmniGateBrowserLanguage.syncIfExisting(host);
                state.setText(applied
                    ?"Gecko 语言偏好已同步："+locale+"；页面本身不会自动刷新"
                    :"原版 Gecko 不支持此语言同步；已保留原网页和账号数据");
            }).show();
    }

    private void showDownloads(){
        if(guestWindow||selected<0||session==null){
            state.setText("仅支持已打开的持久账号空间下载列表");return;
        }
        android.content.Intent intent=new android.content.Intent(host,
            GeckoDownloadManagerActivity.class);
        intent.putExtra(GeckoDownloadManagerActivity.EXTRA_CONTEXT,
            AccountBrowserStore.contextId(host,provider,selected));
        host.startActivity(intent);
    }

    private void showFullscreenActions(){
        if(!fullscreenMode)return;
        PopupMenu menu=new PopupMenu(host,fullscreenExit);
        menu.setGravity(android.view.Gravity.END);
        menu.getMenu().add(0,1,0,"输入网址 / 验证链接");
        menu.getMenu().add(0,2,1,"后退");
        menu.getMenu().add(0,3,2,"刷新");
        menu.getMenu().add(0,4,3,"用系统浏览器打开（独立登录）");
        if(!guestWindow)
            menu.getMenu().add(0,6,4,"当前账号 · 悬浮窗");
        menu.getMenu().add(0,5,5,"退出全屏");
        menu.getMenu().add(0,7,6,"翻译网页 / 恢复原文");
        if(!guestWindow)menu.getMenu().add(0,8,7,"当前账号空间 · 下载管理");
        menu.setOnMenuItemClickListener(item->{
            switch(item.getItemId()){
                case 1:showAddressDialog();return true;
                case 2:callSession("goBack");return true;
                case 3:callSession("reload");return true;
                case 4:confirmExternalBrowser();return true;
                case 5:setFullscreen(false);return true;
                case 7:GeckoPageTranslate.showMenu(host,fullscreenExit,session,geckoView);return true;
                case 8:showDownloads();return true;
                case 6:
                    if(!guestWindow&&selected>=0)
                        handoffToFloatingWindow();
                    return true;
                default:return false;
            }
        });
        menu.setOnDismissListener(popup->{
            Window w=dialog.getWindow();
            if(fullscreenMode && w!=null)w.getDecorView().post(()->{
                if(fullscreenMode)applyFullscreenWindow(true);
            });
        });
        menu.show();
    }

    private void confirmExternalBrowser(){
        String link=currentUrl==null?AccountBrowserSlots.home(provider):currentUrl;
        if(!(link.startsWith("https://") || link.startsWith("http://")))return;
        new AlertDialog.Builder(host).setTitle("切换至系统浏览器")
            .setMessage("将手动打开当前网页。外部应用可能由 Android 工作资料或个人资料中的浏览器处理，不能保证仍处于此账号空间；不传递 Cookie，但外部浏览器可能要求重新登录。请自行核对目标应用。")
            .setNegativeButton("取消",null)
            .setPositiveButton("打开",(d,which)->{
                try{
                    android.content.Intent intent=new android.content.Intent(
                        android.content.Intent.ACTION_VIEW,android.net.Uri.parse(link));
                    intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE);
                    host.startActivity(intent);
                }catch(RuntimeException unavailable){
                    new AlertDialog.Builder(host).setMessage("未找到可打开此网址的系统浏览器。")
                        .setPositiveButton("确定",null).show();
                }
            }).show();
    }

    /** Full-screen controls never create a second session or call an API. */
    private void showAddressDialog(){
        EditText entry=new EditText(host);
        entry.setSingleLine(true);
        entry.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        entry.setText(currentUrl==null?AccountBrowserSlots.home(provider):currentUrl);
        entry.selectAll();
        AlertDialog prompt=new AlertDialog.Builder(host).setTitle("在当前浏览器中打开网址")
            .setMessage("仅支持 HTTP/HTTPS。验证码需自行查看和填写。")
            .setView(entry).setNegativeButton("取消",null)
            .setNeutralButton("浏览器检查",(d,w)->showBrowserEnvironment())
            .setPositiveButton("打开",(d,w)->navigateTo(entry.getText().toString())).create();
        prompt.setOnDismissListener(v->{
            Window w=dialog.getWindow();
            if(fullscreenMode && w!=null)
                w.getDecorView().post(()->{
                    if(fullscreenMode)applyFullscreenWindow(true);
                });
        });
        prompt.show();
    }
    /**
     * Local-only compatibility audit: no JavaScript injection and no
     * fabricated device, rendering or automation fingerprint.
     */
    private void showBrowserEnvironment(){
        String defaultUa="不可读取";
        try{
            Class<?> gecko=Class.forName("org.mozilla.geckoview.GeckoSession");
            Object value=gecko.getMethod("getDefaultUserAgent").invoke(null);
            if(value instanceof String)defaultUa=(String)value;
        }catch(Throwable ignored){
            // Do not fabricate a fallback UA on older Gecko versions.
        }
        String overrideState="无法检查（会话未打开或引擎不支持查询）";
        String modeState="无法检查";
        if(session!=null){
            try{
                Object settings=session.getClass().getMethod("getSettings").invoke(session);
                Object override=settings.getClass().getMethod("getUserAgentOverride").invoke(settings);
                overrideState=override==null?"未覆盖（使用 Gecko 原生 UA）":"检测到会话级 UA 覆盖";
                Object uaMode=settings.getClass().getMethod("getUserAgentMode").invoke(settings);
                Object vpMode=settings.getClass().getMethod("getViewportMode").invoke(settings);
                modeState="UA "+("0".equals(String.valueOf(uaMode))?"移动端":"非默认")+
                    " · 视口 "+("0".equals(String.valueOf(vpMode))?"移动端":"非默认");
            }catch(Throwable ignored){
                // Unknown state is not evidence of a spoofed environment.
            }
        }
        String message="Gecko 引擎默认 UA：\n"+defaultUa+
            "\n\n本会话 UA 覆盖："+overrideState+
            "\n会话模式："+modeState+
            "\n本机系统：Android "+android.os.Build.VERSION.RELEASE+
            "（SDK "+android.os.Build.VERSION.SDK_INT+"）"+
            "\n本机语言："+java.util.Locale.getDefault().toLanguageTag()+
            "\n本机时区："+java.util.TimeZone.getDefault().getID()+
            "\n\n以上是本机或 Gecko 报告值，不代表网页必然能访问全部硬件信息。"+
            "Client Hints 可以缺失；本工具不注入伪造的 WebGL、传感器、Canvas 或自动化标志。"+
            "实际网页观察值需要在当前标签中测试。";
        AlertDialog diagnostics=new AlertDialog.Builder(host)
            .setTitle("浏览器兼容性检查（仅本地）")
            .setMessage(message).setPositiveButton("关闭",null).create();
        diagnostics.setOnDismissListener(v->{
            Window w=dialog.getWindow();
            if(fullscreenMode && w!=null)w.getDecorView().post(()->{
                if(fullscreenMode)applyFullscreenWindow(true);
            });
        });
        diagnostics.show();
    }
    /**
     * Gecko native progress only. The website controls when its account
     * avatar and hydrated UI appear; a successful page-stop is NOT a claim
     * that Claude authentication, SSO or client-side hydration completed.
     * No injection, polling DOM, silent reload or login-state inference.
     */
    private void followClaudePageProgress(Object ownedSession,Class<?> sessionClass){
        // ProgressDelegate covers *all* allowlisted platforms, not just Claude.
        // Browser loading success is NOT evidence that SPA hydration, OAuth,
        // anti-bot checks or account sign-in completed.
        try{
            Class<?> progress=Class.forName(
                "org.mozilla.geckoview.GeckoSession$ProgressDelegate");
            final long[] started={-1L};
            Object observer=java.lang.reflect.Proxy.newProxyInstance(
                progress.getClassLoader(),new Class<?>[]{progress},(proxy,method,args)->{
                    String event=method.getName();
                    if("hashCode".equals(event))return System.identityHashCode(proxy);
                    if("equals".equals(event))return args!=null&&args.length>0&&proxy==args[0];
                    if("toString".equals(event))return "OmniGateAllProviderLoadState";
                    if(args==null||args.length<2||args[0]!=ownedSession)return null;
                    if("onPageStart".equals(event)){
                        if(session==ownedSession){
                            GeckoBrowserTransfers.cancelSession(host,ownedSession);
                            lastPageFailure=null;
                            contentProcessTerminated=false;
                        }
                        started[0]=android.os.SystemClock.elapsedRealtime();
                        host.runOnUiThread(()->{
                            if(session==ownedSession&&dialog.isShowing())
                                state.setText(provider.toUpperCase(java.util.Locale.ROOT)+
                                    " 网页正在加载 · 未触发自动刷新或更换账号");
                        });
                    }else if("onProgressChange".equals(event)&&
                            "claude".equals(provider)&&args[1] instanceof Integer){
                        int percent=(Integer)args[1];
                        if(percent>=0&&percent<=100)host.runOnUiThread(()->{
                            if(session==ownedSession&&dialog.isShowing()&&lastPageFailure==null)
                                state.setText("Claude 基础页面 "+percent+
                                    "% · 不代表网页登录或模型功能已经就绪");
                        });
                    }else if("onPageStop".equals(event)&&args[1] instanceof Boolean){
                        boolean success=(Boolean)args[1];
                        long elapsed=started[0]>=0?Math.max(0,
                            android.os.SystemClock.elapsedRealtime()-started[0]):-1;
                        String duration=elapsed>=0?String.format(
                            java.util.Locale.ROOT,"%.1fs",elapsed/1000.0):"未记录";
                        host.runOnUiThread(()->{
                            if(session!=ownedSession||!dialog.isShowing())return;
                            if(lastPageFailure!=null){
                                state.setText(lastPageFailure+
                                    " · 未自动重试，请点击“主页”手动重新打开");
                            }else{
                                state.setText(provider.toUpperCase(java.util.Locale.ROOT)+
                                    (success?" 基础页面加载结束（":" 基础页面加载失败（")+
                                    duration+"）；"+(success
                                        ?"不代表站点登录、客户端脚本加载成功"
                                        :"没有自动刷新或切换账号；可手动打开主页"));
                            }
                        });
                    }
                    return null;
                });
            sessionClass.getMethod("setProgressDelegate",progress)
                .invoke(ownedSession,observer);
            if(sessionClass.getMethod("getProgressDelegate")
                .invoke(ownedSession)!=observer)
                throw new IllegalStateException("all_provider_progress_owner_mismatch");
        }catch(Throwable unsupported){
            GeckoBrowserTransfers.blockSessionFileUploads(ownedSession);
            android.util.Log.w("OmniGateBrowser",
                "all_provider_page_progress_gate_unavailable",unsupported);
        }
    }

    private void navigateTo(String typed){
        if(session==null){state.setText("未打开账号浏览器，不进行后台导航");return;}
        if(contentProcessTerminated){
            // Gecko's ContentDelegate.onCrash/onKill marks this session
            // unusable. Only a direct user action may open a new session
            // under the SAME saved contextId. Never silently rotate IDs.
            int previous=selected;
            if(previous>=0&&!guestWindow){
                open(previous);
                return;
            }
            state.setText("内容进程已关闭；请手动重新打开该账号空间");
            return;
        }
        String raw=typed==null?"":typed.trim();
        String url=raw;
        if(!raw.regionMatches(true,0,"https://",0,8) &&
           !raw.regionMatches(true,0,"http://",0,7))
            url="https://"+raw;
        try{
            if(raw.isEmpty() || raw.length()>4096 || raw.startsWith("//") ||
               raw.indexOf('\\')>=0 || raw.contains("://") &&
                 !(raw.regionMatches(true,0,"https://",0,8) ||
                   raw.regionMatches(true,0,"http://",0,7)))
                throw new IllegalArgumentException("unsupported_scheme");
            java.net.URI parsed=new java.net.URI(url);
            String scheme=parsed.getScheme(),site=parsed.getHost();
            if(site==null || site.isEmpty() || parsed.getRawUserInfo()!=null ||
               parsed.getPort()>65535 ||
               !("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme)))
                throw new IllegalArgumentException("not_web_address");
            url=parsed.toString();
        }catch(Exception invalid){
            new AlertDialog.Builder(host).setTitle("网址无效")
                .setMessage("请输入 HTTP/HTTPS 网站地址；不允许脚本、文件或应用链接。")
                .setPositiveButton("确定",null).show();
            return;
        }
        try{
            GeckoBrowserTransfers.cancelSession(host,session);
            session.getClass().getMethod("loadUri",String.class).invoke(session,url);
            currentUrl=url;
            addressInput.setText(url);
            if(url.startsWith("http://"))
                state.setText("HTTP 未加密，勿在此网站输入密码或验证码");
        }catch(Throwable failure){
            new AlertDialog.Builder(host).setTitle("导航失败")
                .setMessage("无法在当前隔离浏览器中打开网页："+failure.getClass().getSimpleName())
                .setPositiveButton("确定",null).show();
        }
    }
    private void followCurrentLocation(Object ownedSession,Class<?> sessionClass){
        try{
            Class<?> nav=Class.forName("org.mozilla.geckoview.GeckoSession$NavigationDelegate");
            Object listener=java.lang.reflect.Proxy.newProxyInstance(
                nav.getClassLoader(),new Class<?>[]{nav},(proxy,method,args)->{
                    if("hashCode".equals(method.getName()))return System.identityHashCode(proxy);
                    if("equals".equals(method.getName()))return proxy==args[0];
                    if("toString".equals(method.getName()))return "OmniGateManualNavigation";
                    if("onLoadError".equals(method.getName())&&
                            args!=null&&args.length>=3&&args[0]==ownedSession){
                        Object failure=args[2];
                        String reason=GeckoPageFailureUi.detail(failure);
                        host.runOnUiThread(()->{
                            if(session==ownedSession && dialog.isShowing()){
                                lastPageFailure=reason;
                                state.setText(reason+
                                    " · 这是 Gecko 网络/证书错误；不自动重启网页");
                            }
                        });
                        return GeckoPageFailureUi.response(provider,failure);
                    }
                    if("onLocationChange".equals(method.getName()) &&
                        args!=null && args.length>1 && args[1] instanceof String){
                        String current=(String)args[1];
                        host.runOnUiThread(()->{
                            if(session==ownedSession &&
                              (current.startsWith("http://")||current.startsWith("https://"))){
                                if(currentUrl!=null&&!currentUrl.equals(current))
                                    GeckoBrowserTransfers.cancelSession(host,ownedSession);
                                currentUrl=current;
                                if(!addressInput.hasFocus())addressInput.setText(current);
                            }
                        });
                    }
                    return null;
                });
            sessionClass.getMethod("setNavigationDelegate",nav).invoke(ownedSession,listener);
        }catch(Throwable unsupported){
            // Without a proven navigation observer, file selection cannot
            // safely be completed after a same-session page redirect.
            GeckoBrowserTransfers.blockSessionFileUploads(ownedSession);
            android.util.Log.w("OmniGateWebFiles",
                "manual_navigation_upload_gate_unavailable",unsupported);
        }
    }
    private Button button(String name){
        Button b=new Button(host);b.setAllCaps(false);b.setText(name);b.setTextSize(12);
        b.setTextColor(0xFFF1F4FF);
        b.setBackground(surface(0xFF282E55,12,host));
        b.setMinHeight(dp(host,43));
        return b;
    }
    private void showFrameOnly(){
        dialog.show();
        Window w=dialog.getWindow();
        if(w!=null){
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
            if(android.os.Build.VERSION.SDK_INT>=30)
                w.setDecorFitsSystemWindows(false);
            else w.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE|
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            w.getDecorView().requestApplyInsets();
        }
        renderTabs();
    }
    private void show(){
        showFrameOnly();
        if(guestWindow)open(-1);
        else{
            refreshLocalBudget();
            switchTo(startingSlot);
        }
    }
    private void refreshLocalBudget(){
        if(AccountBrowserSlots.browserOnly(provider)){
            localBudgetState.setText("Claude 仅浏览器：登录信息保存在所选的 Gecko 隔离存储中；不读取或调用 API。");
            return;
        }
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
        if(AccountBrowserSlots.browserOnly(provider)){
            state.setText("Claude 浏览器账号必须由你手动点击切换，不会自动轮换");
            return;
        }
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
        if(!AccountBrowserSlots.nativeExecutorSupported(provider)){
            state.setText("此平台仅支持隔离浏览器登录，禁止设置为 8787 API 主执行目标");
            return;
        }
        if(selected<0||session==null){
            state.setText("隔离浏览器尚未打开；未登记执行目标");
            return;
        }
        if(!SidecarProxyServer.voluntaryBrowserSwitchPermitted(provider)){
            state.setText("平台熔断中，不允许通过更改账号绕过限制");
            return;
        }
        final int requestedSlot=selected;
        new AlertDialog.Builder(host)
            .setTitle("切换原版模型至此账号？")
            .setMessage("将先检查当前原版模型与悬浮服务是否空闲，然后按原来的" +
                "受控交接实现重建目标会话。交接失败不会仅登记目标并造成 409；" +
                "正在生成、上传或平台风控中不会切换，也不会自动重发。")
            .setNegativeButton("取消",null)
            .setPositiveButton("确认空闲并切换主模型",(d,w)->{
                // The user-owned signed v1.1.5 binary contains the exact
                // idle-checked NativeModelSwitch$QuickClick handoff. Reuse
                // it rather than accidentally reverting the native owner
                // transition while updating the auxiliary login picker.
                // No private binary or signing material is published here.
                try{
                    Class<?> cls=Class.forName(
                        "com.omnigate.integrated.NativeModelSwitch$QuickClick");
                    java.lang.reflect.Constructor<?> constructor=
                        cls.getDeclaredConstructor(Activity.class,Dialog.class,
                            String.class,int.class);
                    constructor.setAccessible(true);
                    Object action=constructor.newInstance(host,dialog,provider,requestedSlot);
                    if(!(action instanceof android.content.DialogInterface.OnClickListener))
                        throw new IllegalStateException("native_handoff_contract_mismatch");
                    state.setText("正在核验当前任务空闲并交接主执行模型…");
                    ((android.content.DialogInterface.OnClickListener)action).onClick(d,w);
                }catch(Throwable unavailable){
                    state.setText("主执行切换接线不可用：已保留原账号；没有登记新目标或发送请求");
                }
            }).show();
    }
    private void finishedTurn(){
        if(AccountBrowserSlots.browserOnly(provider)){
            state.setText("Claude 仅手动网页登录；没有 API 额度或自动轮换操作");
            return;
        }
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
        if(guestWindow)return;
        int count=AccountBrowserStore.count(host,provider);
        for(int i=0;i<count;i++){
            final int index=i;
            Button b=button(AccountMetadataStore.name(host,provider,i));
            b.setOnClickListener(v->switchTo(index));
            tabs.addView(b);
        }
    }
    private void switchTo(int index){
        if(guestWindow)return;
        if(selected==index)return;
        // Explicit tab click is the entire switch authorization. The OLD
        // GeckoSession is closed before the NEW selected login page loads.
        // Neither the picker nor the app startup preloads a provider.
        open(index);
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
        if(sessionCloseFailed){
            state.setText("旧网页会话关闭失败；已禁止切换账号空间。请重启应用后检查登录状态。");
            return;
        }
        try{
            if(guestWindow!=(index==-1))
                throw new IllegalArgumentException("session_mode_mismatch");
            // Guest IDs are never persisted in AccountBrowserStore. A new
            // guest visit always gets a fresh context even on the same phone.
            String id=guestWindow?AccountBrowserSlots.newContextId():
                AccountBrowserStore.contextId(host,provider,index);
            if(guestWindow)guestContextId=id;
            if(!AccountBrowserSlots.validContextId(id))
                throw new IllegalStateException("gecko_context_id_invalid");
            Object runtime=existingRuntime();
            // App Info language affects Gecko requests, not the Chinese shell.
            // Existing session/context is untouched; no reload or navigation.
            OmniGateBrowserLanguage.applyToExisting(host,runtime);
            Class<?> builderClass=Class.forName(
                "org.mozilla.geckoview.GeckoSessionSettings$Builder");
            Object builder=builderClass.getConstructor().newInstance();
            // Never use the null/default shared-storage session context.
            builderClass.getMethod("contextId",String.class).invoke(builder,id);
            builderClass.getMethod("useTrackingProtection",boolean.class).invoke(builder,
                guestWindow || AccountBrowserStore.trackingProtection(host,provider,index));
            if(guestWindow){
                // Fail closed if this Gecko build cannot provide private mode;
                // never silently fall back to a persisted login context.
                builderClass.getMethod("usePrivateMode",boolean.class).invoke(builder,true);
                builderClass.getMethod("useTrackingProtection",boolean.class)
                    .invoke(builder,true);
            }
            // Match actual Gecko mobile browser behavior without any spoof.
            builderClass.getMethod("userAgentMode",int.class).invoke(builder,0);
            builderClass.getMethod("viewportMode",int.class).invoke(builder,0);
            Object settings=builderClass.getMethod("build").invoke(builder);
            Class<?> settingsClass=Class.forName(
                "org.mozilla.geckoview.GeckoSessionSettings");
            Class<?> sessionClass=Class.forName(
                "org.mozilla.geckoview.GeckoSession");
            Class<?> runtimeClass=Class.forName(
                "org.mozilla.geckoview.GeckoRuntime");
            Object created=sessionClass.getConstructor(settingsClass).newInstance(settings);
            // Verify Gecko actually owns the intended persistent context
            // BEFORE opening any network page. Never open a null/default
            // context or rotate saved account IDs as an error fallback.
            Object effective=sessionClass.getMethod("getSettings").invoke(created);
            Object effectiveId=effective.getClass().getMethod("getContextId")
                .invoke(effective);
            Object privateMode=effective.getClass().getMethod("getUsePrivateMode")
                .invoke(effective);
            if(!id.equals(effectiveId)||
                !Boolean.valueOf(guestWindow).equals(privateMode))
                throw new IllegalStateException("gecko_session_context_owner_mismatch");
            // Register cleanup ownership before opening; any reflection or
            // display failure must close the original GeckoSession.
            session=created;
            GeckoPageTranslate.prepareSession(created);
            sessionClass.getMethod("open",runtimeClass).invoke(created,runtime);
            Class<?> viewClass=Class.forName("org.mozilla.geckoview.GeckoView");
            Object visual=viewClass.getConstructor(Context.class).newInstance(host);
            if(!(visual instanceof View))throw new IllegalStateException("gecko_view_invalid");
            geckoView=visual;
            viewClass.getMethod("setSession",sessionClass).invoke(visual,created);
            display.addView((View)visual,0,new FrameLayout.LayoutParams(-1,-1));
            followCurrentLocation(created,sessionClass);
            followClaudePageProgress(created,sessionClass);
            GeckoBrowserTransfers.attachFilePrompt(host,created,
                ()->GeckoLiveWindowHandoff.owns(created));
            GeckoScopedPermissionGate.install(host,created,id,
                ()->GeckoLiveWindowHandoff.owns(created));
            GeckoBrowserDownloads.attach(host,created,id,
                ()->GeckoLiveWindowHandoff.owns(created),
                event->{
                    if(session!=created||!dialog.isShowing())return;
                    contentProcessTerminated=true;
                    lastPageFailure=event;
                    state.setText(event+
                        " · Gecko 内容进程终止；请手动点“主页”重建此账号网页。"+
                        " Cookie 和账号空间 ID 未清理，未自动重发请求");
                });
            navigateTo(AccountBrowserSlots.home(provider));
            if(!guestWindow)AccountBrowserStore.select(host,provider,index);
            selected=guestWindow?-1:index;
            if(!guestWindow)refreshLocalBudget();
            state.setText(guestWindow
                ? "临时访客已打开：关闭后清除此空间站点数据；未保存账号登录"
                : AccountBrowserSlots.browserOnly(provider)
                ? "Claude 账号 "+(index+1)+" 已按需打开 · 网页正在连接；右上角账号空间入口可立即使用"
                : "账号 "+(index+1)+"：隔离浏览器已打开（不影响原版 API）");
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
        GeckoBrowserTransfers.cancelSession(host,prior);
        GeckoBrowserDownloads.cancelSession(prior);
        String guestId=guestContextId;
        session=null;geckoView=null;selected=-1;currentUrl=null;guestContextId=null;
        lastPageFailure=null;contentProcessTerminated=false;
        if(view!=null){
            try{view.getClass().getMethod("releaseSession").invoke(view);}
            catch(Throwable failure){sessionCloseFailed=true;}
        }
        boolean closed=prior==null;
        if(prior!=null){
            try{
                // Gecko explicitly closes a session on content crash/kill;
                // close() again is unnecessary and may fail on a dead process.
                // isOpen()==false is the documented safe terminal state,
                // and does not imply removal of persistent Gecko context data.
                boolean open=Boolean.TRUE.equals(
                    prior.getClass().getMethod("isOpen").invoke(prior));
                if(open)prior.getClass().getMethod("close").invoke(prior);
                closed=true;
            }catch(Throwable failure){
                sessionCloseFailed=true;
                android.util.Log.w("OmniGateBrowser","guest_session_close_failed");
            }
        }
        if(guestId!=null && closed){
            // Per-context only: NEVER clear the main Gecko profile or any
            // stored provider account. Close session before clearing data.
            try{
                Object runtime=existingRuntime();
                Object storage=runtime.getClass()
                    .getMethod("getStorageController").invoke(runtime);
                storage.getClass().getMethod("clearDataForSessionContext",String.class)
                    .invoke(storage,guestId);
            }catch(Throwable unavailable){
                android.util.Log.w("OmniGateBrowser","guest_context_clear_unavailable");
            }
            try{
                GeckoBrowserDownloads.clearContextFiles(host,guestId);
            }catch(java.io.IOException cannotErase){
                android.util.Log.w("OmniGateBrowser","guest_download_erase_failed");
            }
        }
        if(view instanceof View)display.removeView((View)view);
    }
}
