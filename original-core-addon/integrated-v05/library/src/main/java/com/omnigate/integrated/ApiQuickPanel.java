package com.omnigate.integrated;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * Two-tab floating dock INSIDE the existing app window, not a second screen.
 *
 * - Collapsed 40dp chip, draggable. Expanded 300dp mini panel.
 * - API and injection tabs, selected mode stays visible behind panel.
 * - Attaches directly to DecorView so it does not need a WindowManager token,
 *   global overlay permission or foreground service.
 * - On resume rebuilds from memory-only attachment state; on pause removes UI.
 * - All upload networking executes in OverlayUploadClient; minimizing UI never
 *   stops the in-progress HTTP request.
 */
public final class ApiQuickPanel {
    private static final WeakHashMap<Activity, Dock> DOCKS=new WeakHashMap<>();
    private static final String PREF="omnigate_overlay_dock_v061";
    private static final int CHIP=40, MARGIN=9;
    private static final int DEFAULT_W=252, DEFAULT_H=335;
    private static final int MIN_W=206, MAX_W=360, MIN_H=225, MAX_H=520;
    private static int selectedTab=0;
    private static boolean expanded=false;
    private static String lastState="尚未挂载浮窗";
    private ApiQuickPanel(){}

    private static int dp(Context c,int n){
        return Math.round(c.getResources().getDisplayMetrics().density*n);
    }
    private static GradientDrawable bg(int color,int radius,int border) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);
        d.setCornerRadius(radius);
        if(border!=0)d.setStroke(1,border);
        return d;
    }
    private static int clamp(int n,int lo,int hi){
        return Math.max(lo,Math.min(n,Math.max(lo,hi)));
    }

    public static String status(){return lastState;}
    public static void show(Activity a){
        if(a==null||a.isFinishing()||a.isDestroyed()||DOCKS.containsKey(a))return;
        a.getWindow().getDecorView().post(()->{
            if(a.isFinishing()||a.isDestroyed()||DOCKS.containsKey(a))return;
            View root=a.getWindow().getDecorView();
            if(!(root instanceof FrameLayout)){
                lastState="未挂载：窗口不是 FrameLayout";
                return;
            }
            Dock dock=new Dock(a,(FrameLayout)root);
            DOCKS.put(a,dock);
            dock.attach();
        });
    }
    public static void hide(Activity a){
        Dock d=DOCKS.remove(a);
        if(d!=null)d.detach();
    }

    private static final class Dock {
        final Activity activity;
        final FrameLayout frame;
        final Handler main=new Handler(Looper.getMainLooper());
        final SharedPreferences pref;
        LinearLayout shell;
        LinearLayout body;
        ScrollView innerScroll;
        FrameLayout.LayoutParams params;
        int desiredWidthDp;
        int desiredHeightDp;
        TextView status;
        TextView textStatus;
        TextView textAnswer;
        TextView pinnedTextResult;
        TextView guestDiagText;
        Button textSend;
        EditText prompt;
        EditText textPrompt;
        int lastRevision=-1;
        int lastTextRevision=-1;
        int lastGuestDiagRevision=-1;
        boolean attached;
        int startX,startY;
        float touchX,touchY;
        boolean dragged;
        final Runnable refresh=new Runnable() {
            @Override public void run(){
                if(!attached)return;
                if(status!=null&&selectedTab==2) {
                    int revision=OverlayAttachmentDraft.revision();
                    if(revision!=lastRevision){
                        lastRevision=revision;
                        status.setText(OverlayAttachmentDraft.status());
                    }
                }
                if(textStatus!=null&&selectedTab==0) {
                    int revision=OverlayTextClient.revision();
                    if(revision!=lastTextRevision){
                        lastTextRevision=revision;
                        textStatus.setText(OverlayTextClient.status());
                        if(textAnswer!=null)
                            textAnswer.setText(OverlayTextClient.response());
                    }
                    if(textSend!=null)
                        textSend.setEnabled(!OverlayTextClient.isSending());
                }
                if(guestDiagText!=null&&selectedTab==0) {
                    int revision=WebBridgeDiagnostics.revision();
                    if(revision!=lastGuestDiagRevision) {
                        lastGuestDiagRevision=revision;
                        guestDiagText.setText(WebBridgeDiagnostics.state());
                    }
                }
                if(pinnedTextResult!=null){
                    pinnedTextResult.setText(textE2ESummary());
                    pinnedTextResult.setTextColor(OverlayTextClient.lastSuccess()?
                        0xFF126F4D:0xFF96612C);
                }
                main.postDelayed(this,1100);
            }
        };

        Dock(Activity a,FrameLayout f){
            activity=a;frame=f;
            pref=a.getSharedPreferences(PREF,Context.MODE_PRIVATE);
            desiredWidthDp=Math.round(pref.getFloat("widthDp",DEFAULT_W));
            desiredHeightDp=Math.round(pref.getFloat("heightDp",DEFAULT_H));
        }
        void attach(){
            if(attached)return;
            shell=new LinearLayout(activity);
            shell.setOrientation(LinearLayout.VERTICAL);
            shell.setClickable(true);
            shell.setElevation(dp(activity,15));
            params=new FrameLayout.LayoutParams(dp(activity,CHIP),dp(activity,CHIP),
                Gravity.TOP|Gravity.LEFT);
            frame.addView(shell,params);
            attached=true;
            render();
            frame.post(()->{
                if(!attached)return;
                position(Math.round(pref.getFloat("x",0.82f)*
                    Math.max(1,frame.getWidth()-shell.getWidth())),
                    Math.round(pref.getFloat("y",0.60f)*
                    Math.max(1,frame.getHeight()-shell.getHeight())));
                lastState="已挂载悬浮控制（"+activity.getClass().getSimpleName()+"）";
            });
            main.post(refresh);
        }
        void detach(){
            attached=false;
            main.removeCallbacks(refresh);
            status=null;prompt=null;textStatus=null;textAnswer=null;
            textPrompt=null;textSend=null;guestDiagText=null;pinnedTextResult=null;
            if(shell!=null&&shell.getParent()==frame)frame.removeView(shell);
            shell=null;body=null;innerScroll=null;
        }
        int maxPanelWidth() {
            return Math.max(dp(activity,MIN_W), Math.min(dp(activity,MAX_W),
                Math.max(dp(activity,MIN_W), frame.getWidth()-dp(activity,MARGIN*2))));
        }
        int maxPanelHeight() {
            int screen=frame.getHeight()>0?frame.getHeight():dp(activity,720);
            return Math.max(dp(activity,MIN_H),Math.min(dp(activity,MAX_H),
                Math.round(screen*0.68f)));
        }
        void storeSize() {
            pref.edit().putFloat("widthDp",desiredWidthDp)
                .putFloat("heightDp",desiredHeightDp).apply();
        }
        void resizeBy(int dwDp,int dhDp) {
            if(!attached || !expanded) return;
            desiredWidthDp=clamp(desiredWidthDp+dwDp,MIN_W,
                Math.max(MIN_W,Math.round(maxPanelWidth()/
                    activity.getResources().getDisplayMetrics().density)));
            desiredHeightDp=clamp(desiredHeightDp+dhDp,MIN_H,
                Math.max(MIN_H,Math.round(maxPanelHeight()/
                    activity.getResources().getDisplayMetrics().density)));
            storeSize();
            render();
        }
        View.OnTouchListener sizeControl() {
            return new View.OnTouchListener() {
                float x,y;
                int initialW,initialH;
                @Override public boolean onTouch(View v,MotionEvent event) {
                    switch(event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            x=event.getRawX();y=event.getRawY();
                            initialW=params.width;initialH=params.height;
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            int w=clamp(initialW+Math.round(event.getRawX()-x),
                                dp(activity,MIN_W),maxPanelWidth());
                            int h=clamp(initialH+Math.round(event.getRawY()-y),
                                dp(activity,MIN_H),maxPanelHeight());
                            params.width=w;params.height=h;
                            shell.setLayoutParams(params);
                            position(params.leftMargin,params.topMargin);
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            float density=activity.getResources()
                                .getDisplayMetrics().density;
                            desiredWidthDp=Math.round(params.width/density);
                            desiredHeightDp=Math.round(params.height/density);
                            storeSize();
                            return true;
                        default:return false;
                    }
                }
            };
        }
        void position(int x,int y){
            if(!attached||shell==null)return;
            int pad=dp(activity,MARGIN);
            int maxX=frame.getWidth()-shell.getWidth()-pad;
            int maxY=frame.getHeight()-shell.getHeight()-pad;
            params.leftMargin=clamp(x,pad,maxX);
            params.topMargin=clamp(y,pad,maxY);
            shell.setLayoutParams(params);
        }
        void save(){
            float x=(float)params.leftMargin/Math.max(1,frame.getWidth()-shell.getWidth());
            float y=(float)params.topMargin/Math.max(1,frame.getHeight()-shell.getHeight());
            pref.edit().putFloat("x",Math.max(0,Math.min(1,x)))
                .putFloat("y",Math.max(0,Math.min(1,y))).apply();
        }
        View.OnTouchListener dragControl(boolean toggleOnTap) {
            return (view,event)->{
                switch(event.getActionMasked()){
                    case MotionEvent.ACTION_DOWN:
                        touchX=event.getRawX();touchY=event.getRawY();
                        startX=params.leftMargin;startY=params.topMargin;
                        dragged=false;return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx=Math.round(event.getRawX()-touchX);
                        int dy=Math.round(event.getRawY()-touchY);
                        if(Math.abs(dx)>dp(activity,7)||Math.abs(dy)>dp(activity,7))
                            dragged=true;
                        if(dragged)position(startX+dx,startY+dy);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if(dragged)save();
                        else if(toggleOnTap){expanded=!expanded;render();}
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        if(dragged)save();return true;
                    default:return false;
                }
            };
        }

        TextView text(String value,int size,int color,boolean heavy) {
            TextView t=new TextView(activity);
            t.setText(value);t.setTextSize(size);t.setTextColor(color);
            if(heavy)t.setTypeface(null,Typeface.BOLD);
            return t;
        }
        void add(LinearLayout box,View v,int margin){
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.bottomMargin=dp(activity,margin);
            box.addView(v,p);
        }
        Button button(String txt,boolean primary) {
            Button b=new Button(activity);
            b.setAllCaps(false);b.setTextSize(11);b.setText(txt);
            b.setPadding(dp(activity,3),dp(activity,3),dp(activity,3),dp(activity,3));
            b.setTextColor(primary?Color.WHITE:0xFF3A41A4);
            b.setBackground(bg(primary?0xFF4E42DB:0xFFF0F2FF,dp(activity,10),0));
            return b;
        }
        LinearLayout horizontal(LinearLayout box,int bottom) {
            LinearLayout r=new LinearLayout(activity);r.setOrientation(LinearLayout.HORIZONTAL);
            add(box,r,bottom);return r;
        }
        void addHalf(LinearLayout row,View v) {
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(activity,35),1);
            p.rightMargin=dp(activity,4);row.addView(v,p);
        }
        void render(){
            if(!attached)return;
            int lastX=params.leftMargin,lastY=params.topMargin;
            if(textPrompt!=null && selectedTab==0)
                OverlayTextClient.setDraft(textPrompt.getText().toString());
            shell.removeAllViews();body=null;innerScroll=null;
            status=null;prompt=null;
            textStatus=null;textAnswer=null;textPrompt=null;textSend=null;
            guestDiagText=null;pinnedTextResult=null;
            shell.setPadding(0,0,0,0);
            if(!expanded){
                params.width=dp(activity,CHIP);
                params.height=dp(activity,CHIP);
                shell.setBackground(bg(0xFF4D43DF,dp(activity,20),0xFFB5CFFF));
                TextView collapsed=text("◈",22,Color.WHITE,true);
                collapsed.setGravity(Gravity.CENTER);
                collapsed.setContentDescription("OmniGate 控制；点击展开，拖动移动");
                shell.addView(collapsed,new LinearLayout.LayoutParams(-1,-1));
                collapsed.setOnTouchListener(dragControl(true));
            }else{
                params.width=clamp(dp(activity,desiredWidthDp),
                    dp(activity,MIN_W),maxPanelWidth());
                params.height=clamp(dp(activity,desiredHeightDp),
                    dp(activity,MIN_H),maxPanelHeight());
                shell.setBackground(bg(0xFFFBFCFF,dp(activity,18),0xFFDEE3F7));
                shell.setPadding(dp(activity,11),dp(activity,10),dp(activity,11),dp(activity,11));
                LinearLayout titlebar=horizontal(shell,7);
                TextView title=text("◈  OmniGate",14,0xFF2B2B79,true);
                title.setGravity(Gravity.CENTER_VERTICAL);
                titlebar.addView(title,new LinearLayout.LayoutParams(0,dp(activity,32),1));
                title.setOnTouchListener(dragControl(false));
                Button shrink=button("−",false);
                shrink.setContentDescription("缩小悬浮面板");
                titlebar.addView(shrink,new LinearLayout.LayoutParams(dp(activity,30),dp(activity,32)));
                shrink.setOnClickListener(v->resizeBy(-30,-58));
                Button grow=button("+",false);
                grow.setContentDescription("放大悬浮面板");
                titlebar.addView(grow,new LinearLayout.LayoutParams(dp(activity,30),dp(activity,32)));
                grow.setOnClickListener(v->resizeBy(30,58));
                Button close=button("▁",false);
                close.setContentDescription("收起悬浮面板，不取消正在进行的模型请求");
                titlebar.addView(close,new LinearLayout.LayoutParams(dp(activity,34),dp(activity,32)));
                close.setOnClickListener(v->{expanded=false;render();});

                LinearLayout tabs=horizontal(shell,9);
                Button chat=button("文本",selectedTab==0);
                Button api=button("API",selectedTab==1);
                Button attach=button("文件",selectedTab==2);
                addThird(tabs,chat);addThird(tabs,api);addThird(tabs,attach);
                chat.setOnClickListener(v->{selectedTab=0;render();});
                api.setOnClickListener(v->{
                    if(textPrompt!=null)
                        OverlayTextClient.setDraft(textPrompt.getText().toString());
                    selectedTab=1;render();
                });
                attach.setOnClickListener(v->{
                    if(textPrompt!=null)
                        OverlayTextClient.setDraft(textPrompt.getText().toString());
                    selectedTab=2;render();
                });
                innerScroll=new ScrollView(activity);
                innerScroll.setFillViewport(false);
                innerScroll.setVerticalScrollBarEnabled(true);
                body=new LinearLayout(activity);
                body.setOrientation(LinearLayout.VERTICAL);
                body.setPadding(dp(activity,2),0,dp(activity,2),dp(activity,3));
                innerScroll.addView(body);
                shell.addView(innerScroll,new LinearLayout.LayoutParams(-1,0,1));
                if(selectedTab==0)renderText();
                else if(selectedTab==1)renderApi();
                else renderUpload();
                pinnedTextResult=text(textE2ESummary(),10,
                    OverlayTextClient.lastSuccess()?0xFF126F4D:0xFF96612C,true);
                pinnedTextResult.setMaxLines(2);
                add(shell,pinnedTextResult,4);
                LinearLayout footer=horizontal(shell,0);
                TextView hint=text("拖动标题移动 · 拖动右下角缩放",10,0xFF7D86A0,false);
                hint.setGravity(Gravity.CENTER_VERTICAL);
                footer.addView(hint,new LinearLayout.LayoutParams(0,dp(activity,30),1));
                TextView grip=text("⤡",24,0xFF5144DC,true);
                grip.setGravity(Gravity.CENTER);
                grip.setContentDescription("按住并拖动以缩放悬浮窗口");
                grip.setBackground(bg(0xFFEEF0FF,dp(activity,10),0));
                footer.addView(grip,new LinearLayout.LayoutParams(dp(activity,42),dp(activity,30)));
                grip.setOnTouchListener(sizeControl());
            }
            shell.setLayoutParams(params);
            frame.post(()->{
                if(attached) position(lastX,lastY);
            });
        }
        String textE2ESummary(){
            if(OverlayTextClient.isSending()) return "文本：已发送，正在等待真正的模型回复…";
            if(OverlayTextClient.lastSuccess())
                return "仅纯文本：HTTP 200 · 图片/描述/发送尚需单独验收";
            if(OverlayTextClient.lastHttp()!=0)
                return "文本：HTTP "+OverlayTextClient.lastHttp()+" · 尚未获得有效回复";
            return "文本：尚未完成 API 端到端验证";
        }
        void addThird(LinearLayout row,View v) {
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(activity,35),1);
            p.rightMargin=dp(activity,4);
            row.addView(v,p);
        }
        void renderText(){
            TextView model=text("当前网页模型："+OriginalCoreBridge.activeProviderModel(),
                11,0xFF3C4688,true);
            add(body,model,5);
            textStatus=text(OverlayTextClient.status(),11,0xFF64708B,false);
            textStatus.setMaxLines(3);
            add(body,textStatus,7);

            textPrompt=new EditText(activity);
            textPrompt.setTextSize(12);
            textPrompt.setTextColor(0xFF232953);
            textPrompt.setHint("输入一句普通文字，先验收文本接口…");
            textPrompt.setSingleLine(false);
            textPrompt.setMaxLines(3);
            textPrompt.setText(OverlayTextClient.draft());
            textPrompt.setPadding(dp(activity,8),dp(activity,7),
                dp(activity,8),dp(activity,7));
            textPrompt.setBackground(bg(0xFFF0F3FB,dp(activity,9),0xFFDDE3F3));
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(activity,63));
            ip.bottomMargin=dp(activity,8);
            body.addView(textPrompt,ip);

            Button send=button("发送普通文本（不含附件）",true);
            textSend=send;
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(activity,39));
            sp.bottomMargin=dp(activity,8);body.addView(send,sp);
            send.setEnabled(!OverlayTextClient.isSending());
            send.setOnClickListener(v->{
                String input=textPrompt.getText().toString();
                String result=OverlayTextClient.send(input);
                textStatus.setText(result);
                send.setEnabled(false);
                if(!OverlayTextClient.isSending())send.setEnabled(true);
                lastTextRevision=-1;
            });
            TextView answerLabel=text("真实模型回复 / 失败原因",11,0xFF3D4681,true);
            add(body,answerLabel,4);
            textAnswer=text(OverlayTextClient.response(),11,0xFF232953,false);
            textAnswer.setMaxLines(5);
            textAnswer.setPadding(dp(activity,7),dp(activity,6),
                dp(activity,7),dp(activity,6));
            textAnswer.setBackground(bg(0xFFF0F3FB,dp(activity,9),0));
            add(body,textAnswer,6);
            Button copy=button("复制完整回复",false);
            add(body,copy,6);
            copy.setOnClickListener(v->{
                String reply=OverlayTextClient.response();
                if(reply.isEmpty())return;
                ClipboardManager cb=(ClipboardManager)activity
                    .getSystemService(Context.CLIPBOARD_SERVICE);
                if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("OmniGate 文本回复",reply));
            });
            Button diagnose=button("诊断原网页桥 / 游客输入状态",false);
            add(body,diagnose,6);
            guestDiagText=text(WebBridgeDiagnostics.state(),10,0xFF666E8C,false);
            guestDiagText.setMaxLines(14);
            add(body,guestDiagText,5);
            diagnose.setOnClickListener(v->{
                guestDiagText.setText(WebBridgeDiagnostics.run());
            });
            TextView note=text("此处仅显示通用说明；本次真实 HTTP 结果固定显示在面板底部。未确认服务器回复前不验收附件。",
                10,0xFF9D664B,false);
            add(body,note,6);
        }
        void renderApi(){
            int nativePort=OriginalCoreBridge.configuredPort();
            boolean local=LocalProxyRuntime.isListening();
            boolean upstream=OriginalCoreBridge.isListening();
            boolean nativeRunning=OriginalCoreBridge.nativeOwnerRunning();
            TextView a=text((local?"● 8787 在线":"○ 8787 未启动")+"    "+
                (nativeRunning && upstream
                    ? "● 原版 "+nativePort+" 运行"
                    : "○ 原版 "+nativePort+" 未就绪"),
                11,nativeRunning && upstream?0xFF147754:0xFF9B612F,true);
            add(body,a,7);
            TextView model=text("当前网页模型："+OriginalCoreBridge.activeProviderModel(),
                11,0xFF4E5782,false);
            add(body,model,7);
            LinearLayout row=horizontal(body,6);
            Button start=button("启动 8787",true),test=button("刷新状态",false);
            addHalf(row,start);addHalf(row,test);
            start.setOnClickListener(v->{
                LocalProxyRuntime.start();
                render();
            });
            test.setOnClickListener(v->render());
            Button recover=button("恢复原版 API（不关闭网页）",false);
            add(body,recover,6);
            recover.setOnClickListener(v->{
                String result=OriginalCoreBridge.requestExplicitNativeStart(activity);
                lastState=result;
                TextView notice=text(result,10,0xFF956132,false);
                add(body,notice,4);
            });
            Button originalReturn=button("收起 / 展开原版「返回」浮窗",false);
            add(body,originalReturn,6);
            originalReturn.setOnClickListener(view->{
                String result=OriginalOverlayControl.toggleOriginalReturn(activity);
                lastState=result;
                TextView notice=text(result,10,0xFF956132,false);
                add(body,notice,4);
            });
            LinearLayout tools=horizontal(body,7);
            Button settings=button("完整设置",false);
            Button copy=button("复制 API 地址",false);
            addHalf(tools,settings);addHalf(tools,copy);
            settings.setOnClickListener(v->{
                try {activity.startActivity(new Intent(activity,CompatSettingsActivity.class));}
                catch(RuntimeException ex){lastState="完整设置打开失败："+ex.getClass().getSimpleName();}
            });
            copy.setOnClickListener(v->{
                ClipboardManager cb=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("OmniGate","http://127.0.0.1:8787/v1"));
            });
            TextView note=text("本地服务在线不代表模型已回复。附件请切换到「文件注入」。",
                10,0xFF78829C,false);
            add(body,note,7);
        }
        void renderUpload(){
            TextView model=text("网页模型："+OriginalCoreBridge.activeProviderModel(),
                11,0xFF3C4688,true);
            add(body,model,5);
            status=text(OverlayAttachmentDraft.status(),11,0xFF576781,false);
            status.setMaxLines(4);
            add(body,status,6);
            LinearLayout row=horizontal(body,6);
            Button choose=button("选图片 / 文件",false),clear=button("清除",false);
            addHalf(row,choose);addHalf(row,clear);
            choose.setOnClickListener(v->{
                if(prompt!=null)OverlayAttachmentDraft.setPrompt(prompt.getText().toString());
                try {activity.startActivity(new Intent(activity,OverlayDocumentPickerActivity.class));}
                catch(RuntimeException ex){OverlayAttachmentDraft.report(
                    "文件选择器打开失败："+ex.getClass().getSimpleName());}
            });
            clear.setOnClickListener(v->{OverlayAttachmentDraft.clear();render();});
            prompt=new EditText(activity);
            prompt.setSingleLine(false);prompt.setMaxLines(2);
            prompt.setTextSize(12);
            prompt.setTextColor(0xFF232953);
            prompt.setHint("要向 AI 问什么？");
            prompt.setText(OverlayAttachmentDraft.prompt());
            prompt.setPadding(dp(activity,7),dp(activity,3),dp(activity,7),dp(activity,3));
            prompt.setBackground(bg(0xFFF0F3FB,dp(activity,9),0xFFDDE3F3));
            LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(activity,43));
            pp.bottomMargin=dp(activity,7);body.addView(prompt,pp);
            Button send=button("向当前网页注入附件（测试）",true);
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(activity,38));
            sp.bottomMargin=dp(activity,8);body.addView(send,sp);
            send.setEnabled(OverlayTextClient.lastSuccess() &&
                OverlayTextClient.lastModel().equals(OriginalCoreBridge.activeProviderModel()));
            send.setOnClickListener(v->{
                if(!OverlayTextClient.lastSuccess() ||
                    !OverlayTextClient.lastModel().equals(OriginalCoreBridge.activeProviderModel())){
                    OverlayAttachmentDraft.report("请先在当前模型的「文本」页完成真实回复测试，再验收附件。");
                    render();return;
                }
                OverlayAttachmentDraft.setPrompt(prompt.getText().toString());
                String result=OverlayUploadClient.start();
                OverlayAttachmentDraft.report(result);
                render();
            });
            TextView note=text(OverlayTextClient.lastSuccess() &&
                OverlayTextClient.lastModel().equals(OriginalCoreBridge.activeProviderModel())
                ? "文本接口已验收；附件仍需网页缩略图和真实回复确认。"
                : "先去「文本」页确认真实文字回复；当前附件发送按钮禁用。",
                10,0xFF8E6946,false);
            add(body,note,6);
        }
    }
}
