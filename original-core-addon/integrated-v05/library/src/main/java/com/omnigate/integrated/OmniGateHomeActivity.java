package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;

/**
 * OmniGate's own unified, branded launcher. Does not replace any original
 * provider implementation, original WebDriver, or third-party tunnel code.
 * The third-party AI Gateway page remains reachable as "原版模型界面".
 */
public final class OmniGateHomeActivity extends Activity {
    private static final int BG=0xFF090C1D, CARD=0xFF171B35;
    private static final int VIOLET=0xFF6951F6, CYAN=0xFF56DFE8;
    private static final int MUTED=0xFFADB4D7;
    private LinearLayout page;
    private TextView localStatus, publicStatus, nativeStatus, ownerStatus;
    private boolean checking;
    private int dp(float x){return Math.round(getResources().getDisplayMetrics().density*x);}
    private GradientDrawable background(int c,int rad){
        GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(rad));return d;
    }
    private GradientDrawable gradient(int a,int b,int radius){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});
        g.setCornerRadius(dp(radius));return g;
    }
    private TextView txt(String value,int size,int color,boolean bold){
        TextView v=new TextView(this);v.setText(value);v.setTextColor(color);
        v.setTextSize(size);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    private void add(LinearLayout to,View view,int margin){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.bottomMargin=dp(margin);to.addView(view,p);
    }
    private LinearLayout card(LinearLayout to){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18),dp(18),dp(18),dp(14));c.setBackground(background(CARD,22));
        add(to,c,14);return c;
    }
    private Button button(LinearLayout to,String label,boolean primary,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);
        b.setText(label);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(primary?gradient(VIOLET,0xFF4742CC,16):background(0xFF262B4E,16));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(49));p.bottomMargin=dp(9);
        to.addView(b,p);b.setOnClickListener(v->action.run());return b;
    }
    private static final class Diamond extends View {
        private final Paint p=new Paint(3);
        Diamond(Activity a){super(a);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float w=getWidth(),h=getHeight();
            float cx=w/2f,cy=h/2f,r=Math.min(w,h)*.43f;
            p.setStyle(Paint.Style.FILL);p.setColor(0xFF6951F6);
            c.drawRoundRect(cx-r,cy-r,cx+r,cy+r,r*.25f,r*.25f,p);
            Path outer=new Path();outer.moveTo(cx,cy-r*.69f);
            outer.lineTo(cx+r*.62f,cy);outer.lineTo(cx,cy+r*.69f);
            outer.lineTo(cx-r*.62f,cy);outer.close();
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(3f,r*.08f));
            p.setStrokeJoin(Paint.Join.ROUND);p.setColor(Color.WHITE);c.drawPath(outer,p);
            Path inner=new Path();inner.moveTo(cx,cy-r*.34f);
            inner.lineTo(cx+r*.32f,cy);inner.lineTo(cx,cy+r*.34f);
            inner.lineTo(cx-r*.32f,cy);inner.close();
            p.setColor(0xFF86E9EF);p.setStrokeWidth(Math.max(2f,r*.045f));
            c.drawPath(inner,p);p.setStyle(Paint.Style.FILL);
        }
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);
        setContentView(root);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);
        sc.setVerticalScrollBarEnabled(false);
        root.addView(sc,new FrameLayout.LayoutParams(-1,-1));
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20),dp(35),dp(20),dp(28));
        sc.addView(page);

        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);
        try { logo.setImageDrawable(getPackageManager().getApplicationIcon(getPackageName())); }
        catch(Exception e){ logo.setVisibility(View.INVISIBLE); }
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(logo,new LinearLayout.LayoutParams(dp(64),dp(64)));
        LinearLayout names=new LinearLayout(this);names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(14),0,0,0);
        add(names,txt("OmniGate",29,Color.WHITE,true),1);
        add(names,txt("AI · 一个入口，完整掌控",12,MUTED,false),0);
        header.addView(names);add(page,header,24);

        LinearLayout main=card(page);
        main.setBackground(gradient(0xFF29215C,0xFF171B35,22));
        add(main,txt("网关控制台",20,Color.WHITE,true),5);
        add(main,txt("让模型、私有 API 与公网 Relay 各自保持清晰的归属。",12,MUTED,false),14);
        localStatus=txt("本地代理：检查中…",14,Color.WHITE,true);add(main,localStatus,7);
        publicStatus=txt("我的 Cloudflare Relay：检查中…",13,0xFFBBD5FF,false);add(main,publicStatus,7);
        nativeStatus=txt("原版模型 API：检查中…",13,0xFFBBD5FF,false);add(main,nativeStatus,5);
        ownerStatus=txt("WebDriver 归属：检查中…",12,0xFFFFD18D,false);add(main,ownerStatus,14);
        button(main,"刷新运行状态",true,()->refresh());

        LinearLayout network=card(page);
        add(network,txt("我的公网穿透",19,Color.WHITE,true),8);
        add(network,txt(GatewayApp.OWN_PUBLIC_BASE+"/v1",12,CYAN,true),9);
        add(network,txt("这是 OmniGate 自有地址。原版页面内的 dpdns.org 属于另一套旧隧道，不会替换或修改 Cloudflare Relay。",12,MUTED,false),13);
        button(network,"打开 Relay 与密钥设置",false,
            ()->startActivity(new Intent(this,CompatSettingsActivity.class)));
        button(network,"重连已授权的 Cloudflare Relay",true,()->{
            if(!GatewayApp.tunnelEnabled()){
                new AlertDialog.Builder(this)
                    .setTitle("公网穿透尚未授权")
                    .setMessage("请在高级配置中确认开启现有 Cloudflare Relay。"+
                        "不会自动打开原版第三方隧道。")
                    .setPositiveButton("前往设置",(d,w)->
                        startActivity(new Intent(this,CompatSettingsActivity.class)))
                    .setNegativeButton("取消",null).show();
                return;
            }
            if(!RelayRuntime.isConnected())RelayRuntime.restart();
            refresh();
        });

        LinearLayout original=card(page);
        add(original,txt("原版模型界面",19,Color.WHITE,true),7);
        add(original,txt("保留 AI Gateway 原版全部模型及网页功能。"+
            "原版悬浮窗可拥有独立 WebDriver；不自动关闭或重定向其任务。",12,MUTED,false),13);
        button(original,"进入 ChatGPT 模型界面",true,()->openOriginal(
            "com.deepseek2.app.ui.MainActivity"));
        button(original,"模型 / 提供商选择",false,()->openOriginal(
            "com.deepseek2.app.ui.ProviderActivity"));
        button(original,"处理 409：检查原版悬浮服务并安全交接",false,()->confirmOwnerHandoff());

        add(page,txt("版本说明 · 原版和 OmniGate 兼容层相互独立；"+
            "409 表示原版会话归属仍未验证，绝不自动截停模型对话。",11,MUTED,false),0);

        // Animated branded entry. The real installed icon is also updated
        // independently in private APK resources; there is no raw icon font.
        header.setAlpha(0f);header.setScaleX(.90f);header.setScaleY(.90f);
        header.animate().alpha(1).scaleX(1).scaleY(1).setDuration(650).start();
    }
    @Override protected void onResume(){
        super.onResume();
        AutoStartRuntime.onActivityVisible(this);
        refresh();
    }
    private void openOriginal(String cls){
        try{
            Intent i=new Intent();i.setClassName(getPackageName(),cls);
            i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        }catch(RuntimeException e){
            new AlertDialog.Builder(this).setTitle("模型界面未启动")
                .setMessage(e.getClass().getSimpleName())
                .setPositiveButton("确定",null).show();
        }
    }
    private void refresh(){
        if(checking)return;checking=true;
        localStatus.setText("本地 8787："+(LocalProxyRuntime.isListening()?"已启动":"正在恢复"));
        publicStatus.setText("Cloudflare："+(RelayRuntime.isConnected()?
            "WebSocket 已连接":GatewayApp.tunnelEnabled()?"已授权，连接中":"未授权开启"));
        new Thread(()->{
            JSONObject state=OriginalCoreBridge.diagnoseVisibleHostSocket();
            String route=OriginalCoreBridge.observedRouteState(state);
            boolean running=OriginalCoreBridge.nativeOwnerRunning();
            int port=OriginalCoreBridge.configuredPort();
            runOnUiThread(()->{
                if(isFinishing()||isDestroyed())return;
                nativeStatus.setText("原版 API："+port+" · "+(running?"实例运行":"当前主界面未持有"));
                ownerStatus.setText("WebDriver："+("main_socket_and_driver_match".equals(route)?
                    "已验证，模型界面可用":
                    "待验证（"+route+"）；不会发送到未知实例"));
                checking=false;
            });
        },"omnigate-home-owner-check").start();
    }
    private void confirmOwnerHandoff(){
        new AlertDialog.Builder(this).setTitle("安全交接原版模型运行时")
            .setMessage("只有你确认原版悬浮窗没有正在生成的回答、上传或工具调用，"+
                "才能结束它自己的 WebDriver 并将 API 交给模型主界面。"+
                "这不是无损热迁移，可能结束悬浮会话。不能确认时请取消。")
            .setNegativeButton("取消",null)
            .setPositiveButton("确认悬浮窗空闲，交接",(d,w)->{
                new Thread(()->{
                    String result=OriginalCoreBridge.requestMainOwnerHandoff(this);
                    runOnUiThread(()->new AlertDialog.Builder(this)
                        .setTitle("交接请求状态").setMessage(result)
                        .setPositiveButton("确定",null).show());
                },"omnigate-user-owner-handoff").start();
            }).show();
    }
}
