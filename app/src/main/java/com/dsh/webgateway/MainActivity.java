package com.dsh.webgateway;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusRefresh = new Runnable() {
        @Override public void run() {
            updateTunnelUi();
            uiHandler.postDelayed(this, 1000L);
        }
    };

    private WebView webView;
    private WebRelay relay;
    private TextView status;
    private TextView endpointInfo;
    private Button tunnelButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        configureWebView();
        startGatewayService(null);
        updateTunnelUi();

        if (savedInstanceState == null) {
            webView.loadUrl("https://chat.deepseek.com/");
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(16, 10, 16, 6);

        status = new TextView(this);
        status.setText("Gateway :" + GatewayApp.PORT + " · browser starting");
        status.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams statusLp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(status, statusLp);

        Button reload = new Button(this);
        reload.setText("DeepSeek");
        reload.setOnClickListener(v -> webView.loadUrl("https://chat.deepseek.com/"));
        bar.addView(reload);

        Button refresh = new Button(this);
        refresh.setText("刷新");
        refresh.setOnClickListener(v -> webView.reload());
        bar.addView(refresh);

        root.addView(bar);

        LinearLayout tunnelBar = new LinearLayout(this);
        tunnelBar.setOrientation(LinearLayout.HORIZONTAL);
        tunnelBar.setGravity(Gravity.CENTER_VERTICAL);
        tunnelBar.setPadding(16, 0, 16, 4);

        tunnelButton = new Button(this);
        tunnelButton.setOnClickListener(v -> {
            boolean enabled = !GatewayApp.tunnelEnabled();
            GatewayApp.setTunnelEnabled(enabled);
            restartTunnel();
            updateTunnelUi();
            Toast.makeText(
                    this,
                    enabled ? "dpdns 公网隧道已开启" : "dpdns 公网隧道已关闭",
                    Toast.LENGTH_SHORT).show();
        });
        tunnelBar.addView(
                tunnelButton,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button identity = new Button(this);
        identity.setText("隧道身份");
        identity.setOnClickListener(v -> showTunnelIdentityDialog());
        tunnelBar.addView(identity);

        Button copy = new Button(this);
        copy.setText("复制连接");
        copy.setOnClickListener(v -> copyConnectionInfo());
        tunnelBar.addView(copy);

        root.addView(tunnelBar);

        endpointInfo = new TextView(this);
        endpointInfo.setTextColor(Color.DKGRAY);
        endpointInfo.setTextSize(12f);
        endpointInfo.setPadding(20, 0, 20, 8);
        endpointInfo.setTextIsSelectable(true);
        root.addView(endpointInfo);

        webView = new WebView(this);
        root.addView(
                webView,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0,
                        1f));

        setContentView(root);
    }

    private void showTunnelIdentityDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = 28;
        box.setPadding(p, 8, p, 0);

        TextView tip = new TextView(this);
        tip.setText(
                "要继续使用旧域名 " + GatewayApp.LEGACY_PUBLIC_URL +
                "，prefix 必须是 " + GatewayApp.LEGACY_PREFIX +
                "，并且需要旧安装里的 tunnel_token。\n" +
                "新 App 无法跨包名读取旧 App 私有 SharedPreferences，所以 token 必须手动导入。");
        box.addView(tip);

        EditText prefix = new EditText(this);
        prefix.setSingleLine(true);
        prefix.setHint("prefix，例如 " + GatewayApp.LEGACY_PREFIX);
        prefix.setText(GatewayApp.tunnelPrefix());
        box.addView(prefix);

        EditText token = new EditText(this);
        token.setSingleLine(true);
        token.setHint("旧 tunnel_token（原版通常 64 位）");
        token.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        token.setText(GatewayApp.tunnelToken());
        box.addView(token);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("dpdns 隧道身份")
                .setView(box)
                .setPositiveButton("保存", null)
                .setNeutralButton("旧域名前缀", null)
                .setNegativeButton("取消", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                prefix.setText(GatewayApp.LEGACY_PREFIX);
                prefix.setSelection(prefix.length());
            });

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    GatewayApp.setTunnelIdentity(
                            prefix.getText().toString(),
                            token.getText().toString());
                    if (GatewayApp.tunnelEnabled()) restartTunnel();
                    updateTunnelUi();
                    Toast.makeText(this, "隧道身份已保存", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } catch (IllegalArgumentException e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        });

        dialog.show();
    }

    private void updateTunnelUi() {
        if (tunnelButton == null || endpointInfo == null) return;

        boolean enabled = GatewayApp.tunnelEnabled();
        tunnelButton.setText(enabled ? "公网隧道：开" : "公网隧道：关");

        StringBuilder s = new StringBuilder();
        s.append("原版本机: http://127.0.0.1:")
                .append(GatewayApp.PORT)
                .append("/v1");
        s.append("\n兼容本机: http://127.0.0.1:")
                .append(GatewayApp.COMPAT_PORT)
                .append("/v1");
        s.append("\n公网: ").append(GatewayApp.generatedPublicUrl()).append("/v1");
        s.append("\n隧道状态: ").append(GatewayApp.tunnelStatus());
        s.append("\n模型: deepseek-chat");
        s.append("\n接口 API Key: ").append(GatewayApp.apiKey());
        if (!GatewayApp.LEGACY_PREFIX.equals(GatewayApp.tunnelPrefix())) {
            s.append("\n旧域名目标: ").append(GatewayApp.LEGACY_PUBLIC_URL);
        }
        endpointInfo.setText(s.toString());
    }

    private void copyConnectionInfo() {
        String text =
                "Original local Base URL: http://127.0.0.1:" + GatewayApp.PORT + "/v1\n" +
                "Compatibility Base URL: http://127.0.0.1:" + GatewayApp.COMPAT_PORT + "/v1\n" +
                "Public Base URL: " + GatewayApp.generatedPublicUrl() + "/v1\n" +
                "Model: deepseek-chat\n" +
                "API Key: " + GatewayApp.apiKey() + "\n" +
                "Tunnel status: " + GatewayApp.tunnelStatus();

        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(
                ClipData.newPlainText("DSH Web Gateway connection", text));
        Toast.makeText(this, "连接信息已复制", Toast.LENGTH_SHORT).show();
    }

    private void restartTunnel() {
        startGatewayService(GatewayService.ACTION_RESTART_TUNNEL);
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                status.setText(
                        "Gateway :" + GatewayApp.PORT + " · " +
                        (url.contains("deepseek") ? "DeepSeek ready" : url));
                updateTunnelUi();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        relay = new WebRelay(webView);
        GatewayApp.setRelay(relay);
    }

    private void startGatewayService(String action) {
        Intent intent = new Intent(this, GatewayService.class);
        if (action != null) intent.setAction(action);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        uiHandler.removeCallbacks(statusRefresh);
        uiHandler.post(statusRefresh);
    }

    @Override
    protected void onPause() {
        uiHandler.removeCallbacks(statusRefresh);
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacks(statusRefresh);
        if (isFinishing()) {
            GatewayApp.setRelay(null);
            if (webView != null) {
                webView.stopLoading();
                webView.destroy();
            }
        }
        super.onDestroy();
    }
}
