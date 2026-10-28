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
    private static final int CHIP=40, PANEL=300, MARGIN=10;
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
        FrameLayout.LayoutParams params;
        TextView status;
        EditText prompt;
        int lastRevision=-1;
        boolean attached;
        int startX,startY;
        float touchX,touchY;
        boolean dragged;
        final Runnable refresh=new Runnable() {
            @Override public void run(){
                if(!attached)return;
                if(status!=null&&selectedTab==1) {
                    int revision=OverlayAttachmentDraft.revision();
                    if(revision!=lastRevision){
                        lastRevision=revision;
                        status.setText(OverlayAttachmentDraft.status());
                    }
                }
                main.postDelayed(this,1100);
            }
        };

        Dock(Activity a,FrameLayout f){
            activity=a;frame=f;
            pref=a.getSharedPreferences(PREF,Context.MODE_PRIVATE);
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
            status=null;prompt=null;
            if(shell!=null&&shell.getParent()==frame)frame.removeView(shell);
            shell=null;
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
            shell.removeAllViews();status=null;prompt=null;
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
                int available=frame.getWidth()-dp(activity,MARGIN*2);
                params.width=Math.min(dp(activity,PANEL),Math.max(dp(activity,235),available));
                params.height=-2;
                shell.setBackground(bg(0xFFFBFCFF,dp(activity,18),0xFFDEE3F7));
                shell.setPadding(dp(activity,11),dp(activity,10),dp(activity,11),dp(activity,11));
                LinearLayout titlebar=horizontal(shell,7);
                TextView title=text("◈  OmniGate",14,0xFF2B2B79,true);
                title.setGravity(Gravity.CENTER_VERTICAL);
                titlebar.addView(title,new LinearLayout.LayoutParams(0,dp(activity,32),1));
                title.setOnTouchListener(dragControl(false));
                Button close=button("−",false);
                titlebar.addView(close,new LinearLayout.LayoutParams(dp(activity,36),dp(activity,32)));
                close.setOnClickListener(v->{expanded=false;render();});

                LinearLayout tabs=horizontal(shell,9);
                Button api=button("API",selectedTab==0);
                Button attach=button("文件注入",selectedTab==1);
                addHalf(tabs,api);addHalf(tabs,attach);
                api.setOnClickListener(v->{selectedTab=0;render();});
                attach.setOnClickListener(v->{selectedTab=1;render();});
                if(selectedTab==0)renderApi();else renderUpload();
                TextView hint=text("模型网页保持在下方 · 拖动顶部移动",10,0xFF7D86A0,false);
                hint.setGravity(Gravity.CENTER);
                add(shell,hint,0);
            }
            shell.setLayoutParams(params);
            frame.post(()->{
                if(attached) position(lastX,lastY);
            });
        }
        void renderApi(){
            int nativePort=OriginalCoreBridge.configuredPort();
            boolean local=LocalProxyRuntime.isListening();
            boolean upstream=OriginalCoreBridge.isListening();
            TextView a=text((local?"● 8787 在线":"○ 8787 未启动")+"    "+
                (upstream?"● "+nativePort+" 在线":"○ "+nativePort+" 未就绪"),
                11,upstream?0xFF147754:0xFF9B612F,true);
            add(shell,a,7);
            TextView model=text("当前网页模型："+OriginalCoreBridge.activeProviderModel(),
                11,0xFF4E5782,false);
            add(shell,model,7);
            LinearLayout row=horizontal(shell,6);
            Button start=button("启动 8787",true),test=button("刷新状态",false);
            addHalf(row,start);addHalf(row,test);
            start.setOnClickListener(v->{
                LocalProxyRuntime.start();
                render();
            });
            test.setOnClickListener(v->render());
            LinearLayout tools=horizontal(shell,7);
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
            add(shell,note,7);
        }
        void renderUpload(){
            TextView model=text("网页模型："+OriginalCoreBridge.activeProviderModel(),
                11,0xFF3C4688,true);
            add(shell,model,5);
            status=text(OverlayAttachmentDraft.status(),11,0xFF576781,false);
            status.setMaxLines(4);
            add(shell,status,6);
            LinearLayout row=horizontal(shell,6);
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
            pp.bottomMargin=dp(activity,7);shell.addView(prompt,pp);
            Button send=button("向当前网页注入附件（测试）",true);
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(activity,38));
            sp.bottomMargin=dp(activity,8);shell.addView(send,sp);
            send.setOnClickListener(v->{
                OverlayAttachmentDraft.setPrompt(prompt.getText().toString());
                String result=OverlayUploadClient.start();
                OverlayAttachmentDraft.report(result);
                render();
            });
            TextView note=text("网页必须已加载并登录；HTTP 502/no reply 表示未收到回执。",
                10,0xFF8E6946,false);
            add(shell,note,6);
        }
    }
}
