package com.omnigate.integrated;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.widget.*;
import android.text.InputType;

/** Minimal no-R-resource settings. The original app remains the launcher. */
public final class CompatSettingsActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);
        GatewayApp.initialize(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(32,28,32,24);
        ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
        TextView title=new TextView(this);title.setText("OmniGate 集成兼容服务");title.setTextSize(22);root.addView(title);
        addText(root,"本地地址: http://127.0.0.1:8787/v1");
        addText(root,"本地 API Key: 任意非空值；公网接口必须验证独立密钥");
        addText(root,"默认模型: deepseek-chat / chatgpt-web / grok-web-fast / glm-web-flash");
        addText(root,"状态: "+GatewayApp.localApiStatus());
        EditText up=secretField(root,"原核心 1601 API Key（留空则不转发 Authorization）",GatewayApp.upstreamApiKey());
        EditText pub=secretField(root,"公网 API Key（独立于本地任意 Key）",GatewayApp.apiKey());
        EditText relay=secretField(root,"Relay 配对 Key（>=24 字符）",GatewayApp.relayKey());
        Switch enabled=new Switch(this);enabled.setText("启用公网 Relay（默认关闭）");enabled.setChecked(GatewayApp.tunnelEnabled());root.addView(enabled);
        Button save=new Button(this);save.setText("保存配置并重启兼容服务");root.addView(save);
        save.setOnClickListener(v->{
            try{
                GatewayApp.setUpstreamApiKey(up.getText().toString());
                GatewayApp.setApiKey(pub.getText().toString());
                GatewayApp.setRelayKey(relay.getText().toString());
                GatewayApp.setTunnelEnabled(enabled.isChecked());
                Intent i=new Intent(this,SidecarService.class);i.setAction(SidecarService.ACTION_RESTART_RELAY);
                if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
                Toast.makeText(this,"设置已保存",Toast.LENGTH_SHORT).show();
            }catch(Exception ex){Toast.makeText(this,ex.getMessage(),Toast.LENGTH_LONG).show();}
        });
    }
    private void addText(LinearLayout root,String msg){TextView t=new TextView(this);t.setText(msg);t.setTextSize(14);t.setPadding(0,12,0,12);root.addView(t);}
    private EditText secretField(LinearLayout root,String label,String value){
        addText(root,label); EditText v=new EditText(this);v.setSingleLine(true);v.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);v.setText(value);root.addView(v);return v;
    }
}
