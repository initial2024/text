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

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    private WebView webView;
    private WebRelay relay;
    private TextView status;
    private TextView endpointInfo;
    private Button tunnelButton;
    private EditText customUrlInput;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final Runnable statusTicker = new Runnable() {
        @Override public void run() {
            updateTunnelUi();
            uiHandler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        configureWebView();
        startGatewayService(null);
        updateTunnelUi();
        uiHandler.post(statusTicker);

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
        bar.addView(reload, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button refresh = new Button(this);
        refresh.setText("刷新");
        refresh.setOnClickListener(v -> webView.reload());
        bar.addView(refresh, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout tunnelBar = new LinearLayout(this);
        tunnelBar.setOrientation(LinearLayout.HORIZONTAL);
        tunnelBar.setGravity(Gravity.CENTER_VERTICAL);
        tunnelBar.setPadding(16, 0, 16, 4);

        tunnelButton = new Button(this);
        tunnelButton.setOnClickListener(v -> {
            boolean enabled = !GatewayApp.tunnelEnabled();
            GatewayApp.setTunnelEnabled(enabled);
            startGatewayService(GatewayService.ACTION_RESTART_TUNNEL);
            updateTunnelUi();
            Toast.makeText(
                    this,
                    enabled ? "公网穿透正在连接" : "公网穿透已关闭",
                    Toast.LENGTH_SHORT).show();
        });
        tunnelBar.addView(tunnelButton, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button copy = new Button(this);
        copy.setText("复制连接信息");
        copy.setOnClickListener(v -> copyConnectionInfo());
        tunnelBar.addView(copy, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(tunnelBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        endpointInfo = new TextView(this);
        endpointInfo.setTextColor(Color.DKGRAY);
        endpointInfo.setTextSize(12f);
        endpointInfo.setPadding(20, 0, 20, 6);
        endpointInfo.setTextIsSelectable(true);
        root.addView(endpointInfo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout customBar = new LinearLayout(this);
        customBar.setOrientation(LinearLayout.HORIZONTAL);
        customBar.setPadding(16, 0, 16, 4);

        customUrlInput = new EditText(this);
        customUrlInput.setSingleLine(true);
        customUrlInput.setHint("自定义公网网址，例如 https://ai.example.com");
        customUrlInput.setText(GatewayApp.customPublicUrl());
        customUrlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        customBar.addView(customUrlInput, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button verifyCustom = new Button(this);
        verifyCustom.setText("验证");
        verifyCustom.setOnClickListener(v -> verifyCustomUrl());
        customBar.addView(verifyCustom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(customBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout advancedBar = new LinearLayout(this);
        advancedBar.setOrientation(LinearLayout.HORIZONTAL);
        advancedBar.setPadding(16, 0, 16, 5);

        Button importOld = new Button(this);
        importOld.setText("导入旧穿透参数");
        importOld.setOnClickListener(v -> showImportTunnelDialog());
        advancedBar.addView(importOld, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button useGenerated = new Button(this);
        useGenerated.setText("使用默认网址");
        useGenerated.setOnClickListener(v -> {
            GatewayApp.setCustomPublicUrl("", false);
            customUrlInput.setText("");
            updateTunnelUi();
            Toast.makeText(this, "已恢复默认 deepseeks.dpdns.org 地址", Toast.LENGTH_SHORT).show();
        });
        advancedBar.addView(useGenerated, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(advancedBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    private void updateTunnelUi() {
        boolean enabled = GatewayApp.tunnelEnabled();
        tunnelButton.setText(enabled ? "公网穿透：开" : "公网穿透：关");

        StringBuilder s = new StringBuilder();
        s.append("本机 API: http://127.0.0.1:")
                .append(GatewayApp.PORT)
                .append("/v1");
        s.append("\n模型: deepseek-chat");
        s.append("\nAPI Key: ").append(GatewayApp.apiKey());

        if (enabled) {
            s.append("\n穿透状态: ").append(GatewayApp.tunnelStatus());
            s.append("\n默认公网: ").append(GatewayApp.generatedPublicUrl()).append("/v1");
            if (GatewayApp.customPublicUrlVerified()) {
                s.append("\n当前公网: ").append(GatewayApp.effectivePublicUrl()).append("/v1");
            }
        } else {
            s.append("\n公网穿透未开启");
        }
        endpointInfo.setText(s.toString());
    }

    private void copyConnectionInfo() {
        StringBuilder text = new StringBuilder();
        text.append("Local Base URL: http://127.0.0.1:")
                .append(GatewayApp.PORT)
                .append("/v1\n");
        if (GatewayApp.tunnelEnabled()) {
            text.append("Public Base URL: ")
                    .append(GatewayApp.effectivePublicUrl())
                    .append("/v1\n");
        }
        text.append("Model: deepseek-chat\n");
        text.append("API Key: ").append(GatewayApp.apiKey());

        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(
                ClipData.newPlainText("DSH Web Gateway", text.toString()));
        Toast.makeText(this, "连接信息已复制", Toast.LENGTH_SHORT).show();
    }

    private void verifyCustomUrl() {
        String candidate = normalizeBaseUrl(customUrlInput.getText().toString());
        if (candidate == null) {
            Toast.makeText(this, "请输入有效的 https:// 网址", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!GatewayApp.tunnelEnabled()) {
            Toast.makeText(this, "请先开启公网穿透，再验证自定义网址", Toast.LENGTH_SHORT).show();
            return;
        }

        GatewayApp.setCustomPublicUrl(candidate, false);
        Toast.makeText(this, "正在验证公网探针", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            boolean ok = probe(candidate);
            runOnUiThread(() -> {
                GatewayApp.setCustomPublicUrl(candidate, ok);
                updateTunnelUi();
                Toast.makeText(
                        this,
                        ok ? "自定义网址验证通过" :
                                "验证失败：该网址尚未转发到本机 Gateway",
                        Toast.LENGTH_LONG).show();
            });
        }, "custom-url-probe").start();
    }

    private boolean probe(String baseUrl) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(baseUrl + "/__tunnel_probe").openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) return false;

            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[2048];
                int n;
                while ((n = in.read(buffer)) >= 0 && out.size() < 16 * 1024) {
                    out.write(buffer, 0, n);
                }
                JSONObject body = new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
                return LocalHttpServer.PROBE_VALUE.equals(body.optString("probe"));
            }
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String normalizeBaseUrl(String input) {
        if (input == null) return null;
        String value = input.trim();
        if (value.isEmpty()) return null;
        if (!value.startsWith("https://")) return null;
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.endsWith("/v1")) value = value.substring(0, value.length() - 3);
        try {
            URL url = new URL(value);
            if (url.getHost() == null || url.getHost().isEmpty()) return null;
            if (!"https".equalsIgnoreCase(url.getProtocol())) return null;
            return value;
        } catch (Exception e) {
            return null;
        }
    }

    private void showImportTunnelDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = 32;
        box.setPadding(pad, 12, pad, 0);

        EditText prefix = new EditText(this);
        prefix.setHint("旧 prefix，例如 gw-33e850180248");
        prefix.setSingleLine(true);
        box.addView(prefix);

        EditText token = new EditText(this);
        token.setHint("旧 tunnel_token（必须与旧 prefix 配套）");
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(token);

        new AlertDialog.Builder(this)
                .setTitle("导入旧公网穿透身份")
                .setMessage("只有旧 prefix 和对应 tunnel_token 同时正确，才能尝试恢复完全相同的旧公网子域名。")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("导入并重连", (dialog, which) -> {
                    try {
                        GatewayApp.setTunnelIdentity(
                                prefix.getText().toString().trim(),
                                token.getText().toString().trim());
                        GatewayApp.setTunnelEnabled(true);
                        startGatewayService(GatewayService.ACTION_RESTART_TUNNEL);
                        updateTunnelUi();
                        Toast.makeText(this, "已导入，正在重新注册隧道", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .show();
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
        uiHandler.removeCallbacks(statusTicker);
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
