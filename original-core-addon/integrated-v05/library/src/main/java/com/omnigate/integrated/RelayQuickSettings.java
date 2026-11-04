package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Small explicit Relay configuration surface, independent of the older
 * multi-purpose API activity. No compiled domain/key; no clipboard or logging.
 */
public final class RelayQuickSettings {
    private RelayQuickSettings(){}
    private static int dp(Context c,int v){
        return Math.round(c.getResources().getDisplayMetrics().density*v);
    }
    private static TextView label(Activity a,String s){
        TextView v=new TextView(a);
        v.setText(s);v.setTextSize(13);v.setTextColor(0xFF303A58);
        v.setPadding(0,dp(a,10),0,dp(a,4));
        return v;
    }
    private static EditText edit(Activity a,String hint,boolean secret){
        EditText v=new EditText(a);
        v.setSingleLine(true);v.setTextSize(14);v.setHint(hint);
        v.setInputType(InputType.TYPE_CLASS_TEXT|
            (secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:
                InputType.TYPE_TEXT_VARIATION_URI));
        return v;
    }
    public static void show(Activity a){
        if(a==null||a.isFinishing()||a.isDestroyed())return;
        try{
            GatewayApp.initialize(a.getApplicationContext());
            LinearLayout form=new LinearLayout(a);
            form.setOrientation(LinearLayout.VERTICAL);
            form.setPadding(dp(a,18),dp(a,8),dp(a,18),0);
            form.addView(label(a,"HTTPS Relay 地址（由你自行输入，不包含在 APK 中）"));
            EditText url=edit(a,"https://your-relay.example",false);
            url.setText(GatewayApp.relayBaseUrl());
            form.addView(url);
            form.addView(label(a,"Relay 配对密钥（留空表示继续使用本机已保存的密钥）"));
            EditText key=edit(a,
                GatewayApp.relayKey().isEmpty()?"请输入配对 Key":"已保存；留空保持不变",true);
            form.addView(key);
            CheckBox enabled=new CheckBox(a);
            enabled.setText("明确开启公网 Relay 连接");
            enabled.setChecked(GatewayApp.tunnelEnabled());
            enabled.setTextColor(0xFF263756);
            form.addView(enabled);
            TextView note=label(a,
                "不预置个人域名或密码。仅保存到本机应用数据；关闭公网开关不影响 8787。"+
                "设置地址后才会连接，账号 Cookie 和原版模型不受影响。");
            form.addView(note);
            AlertDialog dialog=new AlertDialog.Builder(a)
                .setTitle("OmniGate · Relay 与密钥")
                .setView(form)
                .setNegativeButton("取消",null)
                .setNeutralButton("删除本机 Relay 配置",(d,w)->
                    new AlertDialog.Builder(a)
                        .setTitle("清除本机 Relay 配置？")
                        .setMessage("断开 Relay 并清除本机保存的穿透地址与配对 Key，"+
                            "不会删除独立账号、Cookie 或模型历史。")
                        .setNegativeButton("取消",null)
                        .setPositiveButton("确认清除",(x,y)->{
                            GatewayApp.setTunnelEnabled(false);
                            RelayRuntime.stop();
                            GatewayApp.setRelayKey("");
                            GatewayApp.setRelayBaseUrl("");
                            android.widget.Toast.makeText(a,
                                "本机 Relay 配置已清除",android.widget.Toast.LENGTH_SHORT).show();
                        }).show())
                .setPositiveButton("保存",null)
                .create();
            dialog.setOnShowListener(ignore->{
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                    try{
                        String origin=GatewayApp.normalizeRelayBase(url.getText().toString());
                        String typed=key.getText().toString().trim();
                        if(!typed.isEmpty() && (typed.length()<24||typed.length()>256))
                            throw new IllegalArgumentException("relay_key_length");
                        if(enabled.isChecked() && (origin.isEmpty() ||
                            (typed.isEmpty()&&GatewayApp.relayKey().length()<24)))
                            throw new IllegalArgumentException("relay_configuration_missing");
                        String previous=GatewayApp.relayBaseUrl();
                        GatewayApp.setRelayBaseUrl(origin);
                        if(!typed.isEmpty())GatewayApp.setRelayKey(typed);
                        GatewayApp.setTunnelEnabled(enabled.isChecked());
                        if(enabled.isChecked() && (!previous.equals(GatewayApp.relayBaseUrl())
                                || !RelayRuntime.isConnected()))
                            RelayRuntime.restart();
                        if(!enabled.isChecked())RelayRuntime.stop();
                        android.widget.Toast.makeText(a,
                            "Relay 配置已保存",android.widget.Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    }catch(RuntimeException error){
                        url.setError("请输入有效的 HTTPS 根地址；如启用连接，还须配置配对密钥");
                    }
                });
            });
            dialog.show();
        }catch(RuntimeException error){
            new AlertDialog.Builder(a).setTitle("Relay 设置无法打开")
                .setMessage("本地配置初始化失败；没有修改账号或连接状态。")
                .setPositiveButton("关闭",null).show();
        }
    }
}
