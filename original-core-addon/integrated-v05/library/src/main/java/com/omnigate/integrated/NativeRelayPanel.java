package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** On the original model screen, put the user's OWN Relay in the formerly
 * third-party tunnel card location. The old controls are folded but remain
 * available in a clearly labelled expandable legacy section.
 * This does not modify the third-party TunnelClient or API session.
 */
public final class NativeRelayPanel {
    private static final WeakHashMap<Activity,WeakReference<Holder>>
        cache=new WeakHashMap<>();
    private static class Holder {
        View legacy;
        Switch toggle;
        TextView status;
        Holder(View a,Switch b,TextView c){legacy=a;toggle=b;status=c;}
    }
    private NativeRelayPanel(){}
    private static int dp(Activity a,int x){
        return Math.round(a.getResources().getDisplayMetrics().density*x);
    }
    private static TextView text(Activity a,String s,int size,int color) {
        TextView v=new TextView(a);v.setText(s);v.setTextColor(color);
        v.setTextSize(size);return v;
    }
    private static TextView findTunnelLabel(View root,int depth){
        if(root==null||depth>20)return null;
        if(root instanceof TextView){
            String s=((TextView)root).getText().toString().trim();
            if("公网穿透".equals(s)||"原版穿透".equals(s))return (TextView)root;
        }
        if(root instanceof ViewGroup){
            ViewGroup group=(ViewGroup)root;
            for(int i=0;i<group.getChildCount();i++){
                TextView hit=findTunnelLabel(group.getChildAt(i),depth+1);
                if(hit!=null)return hit;
            }
        }
        return null;
    }
    public static void attach(Activity a){
        if(a==null || a.isDestroyed() || a.isFinishing() ||
           !"com.deepseek2.app.ui.MainActivity".equals(
               a.getClass().getName()))return;
        WeakReference<Holder> old=cache.get(a);
        Holder cached=old==null?null:old.get();
        if(cached!=null){refresh(cached);return;}
        View root=a.findViewById(android.R.id.content);
        TextView heading=findTunnelLabel(root,0);
        if(heading==null)return;
        View card=heading;
        ViewGroup host=null;
        // The original card is a MaterialCardView; do not assume a fixed
        // resource ID, but refuse installation if the layout is unfamiliar.
        for(int i=0;i<5 && card!=null;i++){
            if(card.getClass().getName().contains("CardView") &&
               card.getParent() instanceof LinearLayout){
                host=(ViewGroup)card.getParent();break;
            }
            if(card.getParent() instanceof View)card=(View)card.getParent();
            else break;
        }
        if(!(host instanceof LinearLayout))return;
        int index=host.indexOfChild(card);
        if(index<0)return;

        LinearLayout brand=new LinearLayout(a);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(a,18),dp(a,16),dp(a,18),dp(a,14));
        GradientDrawable bg=new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{0xFF312367,0xFF5740C7});
        bg.setCornerRadius(dp(a,22));brand.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,0,0,dp(a,12));
        host.addView(brand,index,lp);

        TextView title=text(a,"OmniGate · 我的公网穿透",18,Color.WHITE);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);brand.addView(title);
        TextView addr=text(a,GatewayApp.OWN_PUBLIC_BASE+"/v1",
            12,0xFF9FE7F2);addr.setPadding(0,dp(a,8),0,dp(a,9));
        addr.setTextIsSelectable(true);brand.addView(addr);
        Switch enabled=new Switch(a);
        enabled.setText("连接自己的 Cloudflare Relay");
        enabled.setTextColor(Color.WHITE);
        enabled.setThumbTintList(ColorStateList.valueOf(0xFFFEFEFF));
        enabled.setChecked(GatewayApp.tunnelEnabled());
        brand.addView(enabled);
        TextView status=text(a,"正在核对设备连接…",12,0xFFDCD7FE);
        status.setPadding(0,dp(a,9),0,dp(a,8));brand.addView(status);
        Button settings=new Button(a);settings.setAllCaps(false);
        settings.setText("配置 / 状态 / API Key");
        brand.addView(settings);
        settings.setOnClickListener(v->a.startActivity(
            new Intent(a,CompatSettingsActivity.class)));
        Button legacy=new Button(a);legacy.setAllCaps(false);
        legacy.setText("展开原版第三方隧道（与上述 Relay 独立）");
        brand.addView(legacy);
        card.setVisibility(View.GONE);
        final View oldCard=card;
        legacy.setOnClickListener(v->{
            boolean expanded=oldCard.getVisibility()==View.VISIBLE;
            oldCard.setVisibility(expanded?View.GONE:View.VISIBLE);
            legacy.setText(expanded?"展开原版第三方隧道（与上述 Relay 独立）":
                "收起原版第三方隧道");
        });
        Holder h=new Holder(card,enabled,status);cache.put(a,new WeakReference<>(h));
        // Retain a reference while this new view tree belongs to this Activity.
        brand.setTag(h);
        enabled.setOnCheckedChangeListener((button,checked)->{
            if(checked && GatewayApp.relayKey().length()<24){
                button.setOnCheckedChangeListener(null);
                button.setChecked(false);
                button.setOnCheckedChangeListener((v,again)->setEnabled(a,h,again));
                new AlertDialog.Builder(a)
                    .setTitle("Relay 配对 Key 未配置")
                    .setMessage("请先在 OmniGate 高级设置中保存你之前的 Relay 配对 Key。"+
                        "不会使用原版第三方隧道的密钥。")
                    .setPositiveButton("打开设置",(dialog,which)->
                        a.startActivity(new Intent(a,CompatSettingsActivity.class)))
                    .setNegativeButton("取消",null).show();
                return;
            }
            setEnabled(a,h,checked);
        });
        refresh(h);
    }
    private static void setEnabled(Activity a,Holder h,boolean on){
        GatewayApp.setTunnelEnabled(on);
        if(on) {
            AutoStartRuntime.requestRefresh();
            RelayRuntime.ensureStarted();
        } else RelayRuntime.stop();
        refresh(h);
        h.status.postDelayed(()->{
            if(!a.isDestroyed() && !a.isFinishing())refresh(h);
        },2200);
    }
    private static void refresh(Holder h){
        if(h==null)return;
        boolean configured=GatewayApp.tunnelEnabled();
        if(h.toggle.isChecked()!=configured){
            h.toggle.setOnCheckedChangeListener(null);
            h.toggle.setChecked(configured);
            h.toggle.setOnCheckedChangeListener((v,on)->{
                // External UI state refresh should never silently toggle.
                v.setChecked(GatewayApp.tunnelEnabled());
            });
        }
        h.status.setText(!configured?"OmniGate Relay 未启用":
            RelayRuntime.isConnected()?"Relay WebSocket 已连接（不等于原版 WebDriver 已验证）":
            "已授权，设备连接中："+GatewayApp.tunnelStatus());
    }
}
