package com.dsh.webgateway;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private WebView webView;
    private WebRelay relay;
    private TextView status;
    private TextView endpointInfo;
    private Button remoteButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        configureWebView();
        startGatewayService(null);
        updateRemoteUi();
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
        status.setText("Gateway :1601 · browser starting");
        status.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(status, statusLp);

        Button reload = new Button(this);
        reload.setText("DeepSeek");
        reload.setOnClickListener(v -> webView.loadUrl("https://chat.deepseek.com/"));
        bar.addView(reload, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button refresh = new Button(this);
        refresh.setText("刷新");
        refresh.setOnClickListener(v -> webView.reload());
        bar.addView(refresh, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout remoteBar = new LinearLayout(this);
        remoteBar.setOrientation(LinearLayout.HORIZONTAL);
        remoteBar.setGravity(Gravity.CENTER_VERTICAL);
        remoteBar.setPadding(16, 0, 16, 6);

        remoteButton = new Button(this);
        remoteButton.setOnClickListener(v -> {
            boolean enabled = !GatewayApp.remoteEnabled();
            GatewayApp.setRemoteEnabled(enabled);
            startGatewayService("com.dsh.webgateway.RESTART_SERVER");
            updateRemoteUi();
            Toast.makeText(this, enabled ? "Tailscale/LAN 远程访问已开启" : "已切回仅本机访问", Toast.LENGTH_SHORT).show();
        });
        remoteBar.addView(remoteButton, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button copy = new Button(this);
        copy.setText("复制连接信息");
        copy.setOnClickListener(v -> copyConnectionInfo());
        remoteBar.addView(copy, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(remoteBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        endpointInfo = new TextView(this);
        endpointInfo.setTextColor(Color.DKGRAY);
        endpointInfo.setTextSize(12f);
        endpointInfo.setPadding(20, 0, 20, 8);
        endpointInfo.setTextIsSelectable(true);
        root.addView(endpointInfo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void updateRemoteUi() {
        boolean enabled = GatewayApp.remoteEnabled();
        remoteButton.setText(enabled ? "远程访问：开" : "远程访问：关");
        String tailscale = GatewayApp.tailscaleIpv4();
        String lan = GatewayApp.lanIpv4();

        StringBuilder s = new StringBuilder();
        s.append("本机: http://127.0.0.1:1601");
        if (enabled) {
            if (tailscale != null) s.append("\nTailscale: http://").append(tailscale).append(":1601");
            if (lan != null && !lan.equals(tailscale)) s.append("\nLAN: http://").append(lan).append(":1601");
            s.append("\nAPI Key / Bearer Token: ").append(GatewayApp.accessToken());
            if (tailscale == null) s.append("\n未检测到 100.64.0.0/10 Tailscale 地址；请先连接 Tailscale。");
        } else {
            s.append("\n远程访问关闭；不会监听 Wi‑Fi/Tailscale 接口。");
        }
        endpointInfo.setText(s.toString());
    }

    private void copyConnectionInfo() {
        boolean enabled = GatewayApp.remoteEnabled();
        String tailscale = GatewayApp.tailscaleIpv4();
        String endpoint = enabled && tailscale != null
                ? "http://" + tailscale + ":1601"
                : "http://127.0.0.1:1601";
        String text = "Base URL: " + endpoint + "\n" +
                "OpenAI endpoint: " + endpoint + "/v1\n" +
                "Model: deepseek-chat\n" +
                (enabled ? "API Key: " + GatewayApp.accessToken() : "API Key: not required for localhost");
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("DSH Web Gateway", text));
        Toast.makeText(this, "连接信息已复制", Toast.LENGTH_SHORT).show();
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
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                status.setText("Gateway :1601 · " + (url.contains("deepseek") ? "DeepSeek ready" : url));
                updateRemoteUi();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());

        relay = new WebRelay(webView);
        GatewayApp.setRelay(relay);
    }

    private void startGatewayService(String action) {
        Intent intent = new Intent(this, GatewayService.class);
        if (action != null) intent.setAction(action);
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
        else startService(intent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
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
