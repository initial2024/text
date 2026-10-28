package com.omnigate.integrated;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Explicitly user-invoked settings launcher for the parallel package.
 * Never intercepts the original GeckoView Activity or WebExtension injection.
 * No automatic foreground service launch on the original app startup.
 */
public final class CompatSettingsActivity extends Activity {
    private static final String LOCAL_URL = "http://127.0.0.1:8787/v1";
    private TextView status;
    private EditText upstream;
    private EditText publicApiKey;
    private EditText relayKey;
    private Switch relayEnabled;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        GatewayApp.initialize(getApplicationContext());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 28, 32, 36);
        ScrollView scroller = new ScrollView(this);
        scroller.addView(root);
        setContentView(scroller);

        TextView title = new TextView(this);
        title.setText("OmniGate API 设置 · v0.5.5");
        title.setTextSize(22);
        root.addView(title);
        addText(root, "使用原 AI Gateway 的 GeckoView 和网页注入，不代替网站账号登录。");
        addText(root, "Base URL: " + LOCAL_URL);
        addText(root, "本地 API Key：任意非空值，例如 test。公网 API Key：必须与下方独立密钥完全一致。");
        addText(root, "模型: deepseek-chat / chatgpt-web / grok-web-fast / glm-web-flash");
        status = addText(root, "");

        Button copy = button(root, "复制本地 API 地址");
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("OmniGate API", LOCAL_URL));
                toast("已复制 Base URL");
            }
        });

        upstream = secretField(root, "原始 1601 API Key（可留空）", GatewayApp.upstreamApiKey());
        publicApiKey = secretField(root, "公网 API Key（只用于公网严格校验）", GatewayApp.apiKey());
        relayKey = secretField(root, "Relay 配对 Key（未使用可留空）", GatewayApp.relayKey());
        relayEnabled = new Switch(this);
        relayEnabled.setText("启用公网 Relay（默认关闭）");
        relayEnabled.setChecked(GatewayApp.tunnelEnabled());
        root.addView(relayEnabled);

        Button save = button(root, "保存设置");
        save.setOnClickListener(v -> saveChanges());

        Button start = button(root, "启动 / 恢复本地 8787 服务");
        start.setOnClickListener(v -> {
            try {
                Intent service = new Intent(this, SidecarService.class);
                service.setAction(SidecarService.ACTION_ENSURE_RUNNING);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
                else startService(service);
                toast("已请求启动。请点击“刷新服务状态”检查。");
            } catch (RuntimeException ex) {
                GatewayApp.setLocalApiStatus("启动失败: " + ex.getClass().getSimpleName());
                toast("启动失败: " + ex.getClass().getSimpleName());
            }
            updateStatus();
        });
        Button stop = button(root, "停止本地 8787 服务");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, SidecarService.class));
            GatewayApp.setLocalApiStatus("用户手动停止");
            updateStatus();
        });
        Button health = button(root, "刷新服务状态");
        health.setOnClickListener(v -> updateStatus());

        Button original = button(root, "打开原 AI Gateway（图片 / 文件上传）");
        original.setOnClickListener(v -> {
            try {
                Intent page = new Intent();
                page.setClassName(getPackageName(), "com.deepseek2.app.ui.ProviderActivity");
                startActivity(page);
            } catch (RuntimeException ex) {
                toast("无法打开原网页: " + ex.getClass().getSimpleName());
            }
        });
        addText(root, "图片 / 文件：在原网页的附件按钮上传。继续使用冻结的 findFileInput、uploadAttachments 和 DataTransfer 注入；本地 8787 只透传请求，不能绕过网站登录或服务端附件限制。");
        addText(root, "长回复：代理上游读取超时禁用，客户端断连后继续读取上游直至结束；原 1601 和系统仍可能存在自身限制，不能保证无限生成。");

        updateStatus();
    }

    private boolean saveChanges() {
        try {
            GatewayApp.setUpstreamApiKey(upstream.getText().toString());
            GatewayApp.setApiKey(publicApiKey.getText().toString());
            GatewayApp.setRelayKey(relayKey.getText().toString());
            GatewayApp.setTunnelEnabled(relayEnabled.isChecked());
            if (SidecarService.isServiceAlive()) {
                Intent refresh = new Intent(this, SidecarService.class);
                refresh.setAction(SidecarService.ACTION_RESTART_RELAY);
                startService(refresh);
            }
            toast("配置已保存");
            updateStatus();
            return true;
        } catch (RuntimeException ex) {
            toast("无法保存: " + ex.getMessage());
            return false;
        }
    }

    private void updateStatus() {
        if (status == null) return;
        boolean alive = SidecarService.isServiceAlive();
        boolean upstreamIsReady = SidecarProxyServer.isUpstreamReachable();
        status.setText("8787 服务: " + (alive ? "已启动" : "未启动")
            + "\n原核心 1601: " + (upstreamIsReady ? "可连接" : "尚不可连接")
            + "\n状态: " + GatewayApp.localApiStatus()
            + "\n公网 Relay: " + GatewayApp.tunnelStatus());
    }

    private Button button(LinearLayout root, String name) {
        Button b = new Button(this);
        b.setText(name);
        root.addView(b);
        return b;
    }

    private TextView addText(LinearLayout root, String value) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(14);
        t.setPadding(0, 10, 0, 10);
        root.addView(t);
        return t;
    }

    private EditText secretField(LinearLayout root, String label, String value) {
        addText(root, label);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setText(value);
        root.addView(input);
        return input;
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}
