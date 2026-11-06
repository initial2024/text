package com.omnigate.integrated;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/**
 * Conservative in-place reskin of the original third-party UI.
 * Listeners, server, WebDriver, model controls and web content stay intact.
 * Original tunnel is labelled as LEGACY; it must never pretend to operate
 * OmniGate's independent Cloudflare Relay.
 */
public final class LegacyUiSkin {
    private static final int ACCENT=0xFF6348EC;
    private static final int LIGHT=0xFFF6F3FF;
    private static final int INK=0xFF28224E;
    private static final java.util.WeakHashMap<Activity,Integer> sessions=
        new java.util.WeakHashMap<>();
    private LegacyUiSkin() {}

    public static void apply(Activity owner) {
        if(owner==null || owner.isFinishing() || owner.isDestroyed())return;
        String name=owner.getClass().getName();
        if(!"com.deepseek2.app.ui.MainActivity".equals(name) &&
           !"com.deepseek2.app.ui.ProviderActivity".equals(name))return;
        try {
            owner.getWindow().setStatusBarColor(LIGHT);
            owner.getWindow().setNavigationBarColor(LIGHT);
            owner.getWindow().getDecorView()
                .setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }catch(Throwable ignored){}
        View root=owner.findViewById(android.R.id.content);
        if(root==null)return;
        recolor(root,0);
        // The original Activity updates labels after asynchronous model/tunnel
        // state changes; reapply text-only decoration a few times, boundedly.
        Integer seq=sessions.get(owner);
        int tick=seq==null?1:seq.intValue()+1;
        sessions.put(owner,tick);
        WeakReference<Activity> ref=new WeakReference<>(owner);
        for(int delay:new int[]{350,1500,3200}) {
            root.postDelayed(()->{
                Activity a=ref.get();
                if(a==null || a.isDestroyed() || a.isFinishing() ||
                    !sessions.containsKey(a) ||
                    sessions.get(a).intValue()!=tick)return;
                View again=a.findViewById(android.R.id.content);
                if(again!=null)recolor(again,0);
            },delay);
        }
    }

    private static void recolor(View node,int depth) {
        if(node==null || depth>22)return;
        if(node instanceof TextView) {
            TextView tv=(TextView)node;
            CharSequence raw=tv.getText();
            if(raw!=null) {
                String str=raw.toString();
                if(str.startsWith("AI Gateway")){
                    tv.setText("OmniGate"+str.substring("AI Gateway".length()));
                    tv.setTextColor(INK);
                    tv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
                }else if("端口".equals(str)){
                    tv.setText("原版内部端口（上游）");
                    tv.setTextColor(INK);
                }else if("KEY 密钥".equals(str)){
                    tv.setText("原版内部 Key（不是 Minis 客户端 Key）");
                    tv.setTextColor(INK);
                }else if("公网穿透".equals(str)){
                    // Prevent the old third-party switch from being mistaken
                    // for the personal claua.de Cloudflare Relay switch.
                    tv.setText("原版穿透");
                    tv.setContentDescription(
                        "原版第三方隧道，非 OmniGate Cloudflare Relay");
                }else if(str.contains(".deepseeks.dpdns.org")){
                    tv.setContentDescription("原版旧隧道地址："+str+
                        "。OmniGate 专用 Cloudflare Relay 在独立控制台设置");
                }
                if(node instanceof Button && (
                    "启动".equals(str)||"停止".equals(str)||
                    "切换到悬浮窗".equals(str)||"保存".equals(str))) {
                    try {
                        node.setBackgroundTintList(
                            ColorStateList.valueOf(ACCENT));
                        tv.setTextColor(Color.WHITE);
                    }catch(Throwable ignored){}
                }
            }
        }
        if(node instanceof CompoundButton){
            try{((CompoundButton)node).setButtonTintList(
                ColorStateList.valueOf(ACCENT));}catch(Throwable ignored){}
        }
        if(node instanceof EditText){
            try{((EditText)node).setBackgroundTintList(
                ColorStateList.valueOf(ACCENT));}catch(Throwable ignored){}
        }
        if(node instanceof ViewGroup){
            ViewGroup parent=(ViewGroup)node;
            for(int i=0;i<parent.getChildCount();i++)
                recolor(parent.getChildAt(i),depth+1);
        }
    }
}
