package com.dsh.webgateway;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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
    private TextView providerInfo;
    private TextView endpointInfo;
    private Button providerButton;
    private Button tunnelButton;
    private EditText customUrlInput;
    private boolean pendingOverlayPermission;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final Runnable statusTicker = new Runnable() {
        @Override
        public void run() {
            updateProviderUi();
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
        updateProviderUi();
        updateTunnelUi();
        uiHandler.post(statusTicker);

        if (savedInstanceState == null) {
            webView.loadUrl(GatewayApp.selectedProvider().url);
        } else {
            webView.restoreState(savedInstanceState);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(12, 8, 12, 4);

        status = new TextView(this);
        status.setTextColor(Color.DKGRAY);
        status.setText("Gateway :" + GatewayApp.PORT + " · starting");
        top.addView(status, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        providerButton = new Button(this);
        providerButton.setOnClickListener(v -> showProviderChooser());
        top.addView(providerButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button refresh = new Button(this);
        refresh.setText("刷新");
        refresh.setOnClickListener(v -> webView.reload());
        top.addView(refresh, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        providerInfo = new TextView(this);
        providerInfo.setTextColor(Color.DKGRAY);
        providerInfo.setTextSize(12f);
        providerInfo.setPadding(16, 0, 16, 4);
        root.addView(providerInfo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout overlayBar = new LinearLayout(this);
        overlayBar.setOrientation(LinearLayout.HORIZONTAL);
        overlayBar.setPadding(12, 0, 12, 4);

        Button showOverlay = new Button(this);
        showOverlay.setText("小悬浮窗");
        showOverlay.setOnClickListener(v -> requestOrShowOverlay());
        overlayBar.addView(showOverlay, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button hideOverlay = new Button(this);
        hideOverlay.setText("隐藏悬浮窗");
        hideOverlay.setOnClickListener(v -> hideOverlay());
        overlayBar.addView(hideOverlay, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(overlayBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout tunnelBar = new LinearLayout(this);
        tunnelBar.setOrientation(LinearLayout.HORIZONTAL);
        tunnelBar.setGravity(Gravity.CENTER_VERTICAL);
        tunnelBar.setPadding(12, 0, 12, 4);

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
        endpointInfo.setPadding(16, 0, 16, 4);
        endpointInfo.setTextIsSelectable(true);
        root.addView(endpointInfo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout customBar = new LinearLayout(this);
        customBar.setOrientation(LinearLayout.HORIZONTAL);
        customBar.setPadding(12, 0, 12, 4);

        customUrlInput = new EditText(this);
        customUrlInput.setSingleLine(true);
        customUrlInput.setHint("自定义公网网址，例如 https://ai.example.com");
        customUrlInput.setText(GatewayApp.customPublicUrl());
        customUrlInput.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        customBar.addView(customUrlInput, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button verifyCustom = new Button(this);
        verifyCustom.setText("验证");
        verifyCustom.setOnClickListener(v -> verifyCustomUrl());
        customBar.addView(verifyCustom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(customBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout tunnelAdvanced = new LinearLayout(this);
        tunnelAdvanced.setOrientation(LinearLayout.HORIZONTAL);
        tunnelAdvanced.setPadding(12, 0, 12, 4);

        Button importOld = new Button(this);
        importOld.setText("导入旧穿透参数");
        importOld.setOnClickListener(v -> showImportTunnelDialog());
        tunnelAdvanced.addView(importOld, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button useGenerated = new Button(this);
        useGenerated.setText("默认公网域名");
        useGenerated.setOnClickListener(v -> {
            GatewayApp.setCustomPublicUrl("", false);
            customUrlInput.setText("");
            updateTunnelUi();
            Toast.makeText(
                    this,
                    "已恢复 deepseeks.dpdns.org 默认公网地址",
                    Toast.LENGTH_SHORT).show();
        });
        tunnelAdvanced.addView(useGenerated, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        root.addView(tunnelAdvanced, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        webView = new WebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void showProviderChooser() {
        AiProvider[] providers = AiProvider.values();
        String[] items = new String[providers.length];
        int selected = 0;

        for (int i = 0; i < providers.length; i++) {
            AiProvider p = providers[i];
            items[i] = p.displayName + "\n" + p.description;
            if (p == GatewayApp.selectedProvider()) selected = i;
        }

        new AlertDialog.Builder(this)
                .setTitle("选择 AI 服务商")
                .setSingleChoiceItems(items, selected, (dialog, which) -> {
                    selectProvider(providers[which]);
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void selectProvider(AiProvider provider) {
        GatewayApp.setSelectedProvider(provider);
        if (relay != null) {
            relay.openProvider(provider);
        } else {
            webView.loadUrl(provider.url);
        }
        refreshOverlayLabel();
        updateProviderUi();
        Toast.makeText(
                this,
                "已切换到 " + provider.displayName + "，首次使用请在网页中登录",
                Toast.LENGTH_SHORT).show();
    }

    private void updateProviderUi() {
        AiProvider p = GatewayApp.selectedProvider();
        providerButton.setText("服务商：" + p.displayName);
        providerInfo.setText(
                p.description
                        + "\n模型别名: " + p.model
                        + " · 网页登录身份独立保留");
    }

    private void requestOrShowOverlay() {
        if (Settings.canDrawOverlays(this)) {
            pendingOverlayPermission = false;
            showOverlay();
            return;
        }

        pendingOverlayPermission = true;
        Intent permission = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(permission);
    }

    private void showOverlay() {
        Intent intent = new Intent(this, FloatingOverlayService.class);
        intent.setAction(FloatingOverlayService.ACTION_SHOW);
        startService(intent);
        GatewayApp.setOverlayEnabled(true);
        Toast.makeText(
                this,
                "已显示 48dp 小悬浮球；拖动移动，长按隐藏",
                Toast.LENGTH_SHORT).show();
    }

    private void hideOverlay() {
        Intent intent = new Intent(this, FloatingOverlayService.class);
        intent.setAction(FloatingOverlayService.ACTION_HIDE);
        startService(intent);
        GatewayApp.setOverlayEnabled(false);
        Toast.makeText(this, "悬浮窗已隐藏，Gateway 仍继续运行", Toast.LENGTH_SHORT).show();
    }

    private void refreshOverlayLabel() {
        Intent intent = new Intent(this, FloatingOverlayService.class);
        intent.setAction(FloatingOverlayService.ACTION_REFRESH);
        startService(intent);
    }

    private void updateTunnelUi() {
        boolean enabled = GatewayApp.tunnelEnabled();
        tunnelButton.setText(enabled ? "公网穿透：开" : "公网穿透：关");

        AiProvider provider = GatewayApp.selectedProvider();
        StringBuilder s = new StringBuilder();
        s.append("本机 API: http://127.0.0.1:")
                .append(GatewayApp.PORT)
                .append("/v1");
        s.append("\n当前模型: ").append(provider.model);
        s.append("\nAPI Key: ").append(GatewayApp.apiKey());

        if (enabled) {
            s.append("\n穿透状态: ").append(GatewayApp.tunnelStatus());
            s.append("\n默认公网: ")
                    .append(GatewayApp.generatedPublicUrl())
                    .append("/v1");
            if (GatewayApp.customPublicUrlVerified()) {
                s.append("\n当前公网: ")
                        .append(GatewayApp.effectivePublicUrl())
                        .append("/v1");
            }
        } else {
            s.append("\n公网穿透未开启");
        }

        endpointInfo.setText(s.toString());
    }

    private void copyConnectionInfo() {
        AiProvider provider = GatewayApp.selectedProvider();

        StringBuilder text = new StringBuilder();
        text.append("Local Base URL: http://127.0.0.1:")
                .append(GatewayApp.PORT)
                .append("/v1\n");

        if (GatewayApp.tunnelEnabled()) {
            text.append("Public Base URL: ")
                    .append(GatewayApp.effectivePublicUrl())
                    .append("/v1\n");
        }

        text.append("Provider: ").append(provider.displayName).append("\n");
        text.append("Model: ").append(provider.model).append("\n");
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
                        ok ? "自定义网址验证通过"
                                : "验证失败：该网址尚未转发到本机 Gateway",
                        Toast.LENGTH_LONG).show();
            });
        }, "custom-url-probe").start();
    }

    private boolean probe(String baseUrl) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(
                    baseUrl + "/__tunnel_probe").openConnection();
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

                JSONObject body = new JSONObject(
                        out.toString(StandardCharsets.UTF_8.name()));
                return LocalHttpServer.PROBE_VALUE.equals(
                        body.optString("probe"));
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

        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        if (value.endsWith("/v1")) {
            value = value.substring(0, value.length() - 3);
        }

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
        token.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(token);

        new AlertDialog.Builder(this)
                .setTitle("导入旧公网穿透身份")
                .setMessage(
                        "只有旧 prefix 和对应 tunnel_token 同时正确，"
                                + "才能尝试恢复完全相同的旧公网子域名。")
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
                        Toast.makeText(
                                this,
                                "已导入，正在重新注册隧道",
                                Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(
                                this,
                                "导入失败：" + e.getMessage(),
                                Toast.LENGTH_LONG).show();
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
                if (relay != null) relay.notePageUrl(url);
                AiProvider p = AiProvider.fromUrl(
                        url,
                        GatewayApp.selectedProvider());
                GatewayApp.setSelectedProvider(p);
                status.setText(
                        "Gateway :" + GatewayApp.PORT
                                + " · " + p.displayName + " ready");
                updateProviderUi();
                updateTunnelUi();
                refreshOverlayLabel();
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
        if (pendingOverlayPermission && Settings.canDrawOverlays(this)) {
            pendingOverlayPermission = false;
            showOverlay();
        }
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
