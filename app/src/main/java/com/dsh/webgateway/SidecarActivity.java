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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class SidecarActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView sidecarStatus;
    private TextView upstreamStatus;
    private TextView relayStatus;
    private TextView localUrl;
    private TextView publicUrl;
    private TextView publicKey;
    private TextView upstreamKey;
    private Switch relaySwitch;
    private boolean updatingSwitch;
    private boolean showPublicKey;
    private boolean showUpstreamKey;

    private int bg;
    private int surface;
    private int surfaceSoft;
    private int text;
    private int muted;
    private int border;
    private int primary;
    private int primarySoft;
    private int success;
    private int warning;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshUi();
            handler.postDelayed(this, 1_500L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadPalette();
        setContentView(buildUi());
        startSidecar(null);
        refreshUi();
        handler.post(ticker);
    }

    private void loadPalette() {
        bg = getColor(R.color.og_bg);
        surface = getColor(R.color.og_surface);
        surfaceSoft = getColor(R.color.og_surface_soft);
        text = getColor(R.color.og_text);
        muted = getColor(R.color.og_muted);
        border = getColor(R.color.og_border);
        primary = getColor(R.color.og_primary);
        primarySoft = getColor(R.color.og_primary_soft);
        success = getColor(R.color.og_success);
        warning = getColor(R.color.og_warning);
    }

    private ScrollView buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        LinearLayout root = UiKit.vertical(this);
        root.setPadding(
                UiKit.dp(this, 18),
                UiKit.dp(this, 18),
                UiKit.dp(this, 18),
                UiKit.dp(this, 34));
        scroll.addView(root);

        TextView brand = UiKit.text(
                this,
                "OmniGate Sidecar",
                28f,
                text,
                true);
        root.addView(brand, UiKit.matchWrap());

        TextView subtitle = UiKit.text(
                this,
                "原 AI Gateway 保持原样 · Sidecar 只提供 8787 / 公网兼容层",
                13.5f,
                muted,
                false);
        subtitle.setLineSpacing(UiKit.dp(this, 3), 1f);
        LinearLayout.LayoutParams subtitleLp = UiKit.matchWrap();
        subtitleLp.topMargin = UiKit.dp(this, 7);
        root.addView(subtitle, subtitleLp);

        root.addView(buildContinuityCard(), top(18));
        root.addView(buildStatusCard(), top(12));
        root.addView(buildLocalCard(), top(12));
        root.addView(buildPublicCard(), top(12));
        root.addView(buildKeysCard(), top(12));
        root.addView(buildModelsCard(), top(12));

        return scroll;
    }

    private LinearLayout buildContinuityCard() {
        LinearLayout card = UiKit.card(this, primarySoft, primarySoft);

        TextView title = UiKit.text(
                this,
                "不截停原网页对话",
                18f,
                primary,
                true);
        card.addView(title, UiKit.matchWrap());

        TextView body = UiKit.text(
                this,
                "Sidecar 不操作 GeckoView、输入框、发送按钮或 Stop。即使 Minis / DSH 在回答中途断开，8787 也会继续读取 1601 到自然结束，避免把客户端断流传递成原核心的取消信号。",
                13f,
                text,
                false);
        body.setLineSpacing(UiKit.dp(this, 4), 1f);
        LinearLayout.LayoutParams lp = UiKit.matchWrap();
        lp.topMargin = UiKit.dp(this, 9);
        card.addView(body, lp);

        return card;
    }

    private LinearLayout buildStatusCard() {
        LinearLayout card = UiKit.card(this, surface, border);

        TextView title = UiKit.sectionTitle(
                this,
                "运行状态",
                muted);
        card.addView(title, UiKit.matchWrap());

        sidecarStatus = UiKit.text(
                this,
                "",
                15f,
                text,
                true);
        LinearLayout.LayoutParams sLp = UiKit.matchWrap();
        sLp.topMargin = UiKit.dp(this, 12);
        card.addView(sidecarStatus, sLp);

        upstreamStatus = UiKit.text(
                this,
                "",
                14f,
                muted,
                false);
        LinearLayout.LayoutParams uLp = UiKit.matchWrap();
        uLp.topMargin = UiKit.dp(this, 7);
        card.addView(upstreamStatus, uLp);

        relayStatus = UiKit.text(
                this,
                "",
                13f,
                muted,
                false);
        relayStatus.setLineSpacing(UiKit.dp(this, 3), 1f);
        LinearLayout.LayoutParams rLp = UiKit.matchWrap();
        rLp.topMargin = UiKit.dp(this, 7);
        card.addView(relayStatus, rLp);

        return card;
    }

    private LinearLayout buildLocalCard() {
        LinearLayout card = UiKit.card(this, surface, border);

        TextView title = UiKit.text(
                this,
                "本机兼容入口",
                19f,
                text,
                true);
        card.addView(title, UiKit.matchWrap());

        TextView note = UiKit.text(
                this,
                "Minis / DSH 使用 8787。所有 /v1 请求仍需填写 API Key，但本机接受任意非空值；Sidecar 再流式转发给原核心 1601。",
                12.5f,
                muted,
                false);
        note.setLineSpacing(UiKit.dp(this, 3), 1f);
        LinearLayout.LayoutParams noteLp = UiKit.matchWrap();
        noteLp.topMargin = UiKit.dp(this, 7);
        card.addView(note, noteLp);

        localUrl = UiKit.text(
                this,
                "http://127.0.0.1:8787/v1",
                15f,
                primary,
                true);
        localUrl.setTextIsSelectable(true);
        LinearLayout.LayoutParams urlLp = UiKit.matchWrap();
        urlLp.topMargin = UiKit.dp(this, 14);
        card.addView(localUrl, urlLp);

        TextView copy = action(
                "复制本机 URL",
                primary,
                primarySoft);
        copy.setOnClickListener(v -> copy(
                "OmniGate Sidecar Local URL",
                "http://127.0.0.1:8787/v1"));
        LinearLayout.LayoutParams copyLp = UiKit.matchWrap();
        copyLp.topMargin = UiKit.dp(this, 10);
        card.addView(copy, copyLp);

        return card;
    }

    private LinearLayout buildPublicCard() {
        LinearLayout card = UiKit.card(this, surface, border);

        LinearLayout head = UiKit.horizontal(this);
        TextView title = UiKit.text(
                this,
                "公网 Relay",
                19f,
                text,
                true);
        head.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        relaySwitch = new Switch(this);
        relaySwitch.setShowText(false);
        relaySwitch.setOnCheckedChangeListener((button, checked) -> {
            if (updatingSwitch) return;
            GatewayApp.setTunnelEnabled(checked);
            startSidecar(SidecarService.ACTION_RESTART_RELAY);
            refreshUi();
        });
        head.addView(relaySwitch);
        card.addView(head, UiKit.matchWrap());

        publicUrl = UiKit.text(
                this,
                GatewayApp.effectivePublicUrl() + "/v1",
                15f,
                primary,
                true);
        publicUrl.setTextIsSelectable(true);
        LinearLayout.LayoutParams urlLp = UiKit.matchWrap();
        urlLp.topMargin = UiKit.dp(this, 13);
        card.addView(publicUrl, urlLp);

        LinearLayout actions = UiKit.horizontal(this);
        LinearLayout.LayoutParams actionsLp = UiKit.matchWrap();
        actionsLp.topMargin = UiKit.dp(this, 10);

        TextView copy = action(
                "复制公网 URL",
                primary,
                primarySoft);
        copy.setOnClickListener(v -> copy(
                "OmniGate Public URL",
                GatewayApp.effectivePublicUrl() + "/v1"));
        actions.addView(copy, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView custom = action(
                "自定义域名",
                text,
                surfaceSoft);
        LinearLayout.LayoutParams customLp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        customLp.leftMargin = UiKit.dp(this, 8);
        custom.setOnClickListener(v -> editCustomPublicUrl());
        actions.addView(custom, customLp);

        card.addView(actions, actionsLp);
        return card;
    }

    private LinearLayout buildKeysCard() {
        LinearLayout card = UiKit.card(this, surface, border);

        TextView title = UiKit.text(
                this,
                "凭据",
                19f,
                text,
                true);
        card.addView(title, UiKit.matchWrap());

        TextView note = UiKit.text(
                this,
                "本机客户端可以填任意非空 Key；下面的“公网真实 Key”只用于 claua.de / 自定义公网入口。原核心 Key 是可选的，只有原 1601 自己要求鉴权时才填写。",
                12.5f,
                muted,
                false);
        note.setLineSpacing(UiKit.dp(this, 3), 1f);
        LinearLayout.LayoutParams noteLp = UiKit.matchWrap();
        noteLp.topMargin = UiKit.dp(this, 7);
        card.addView(note, noteLp);

        publicKey = UiKit.text(
                this,
                "",
                13.5f,
                text,
                false);
        card.addView(
                buildKeyRow(
                        "公网真实 API Key",
                        publicKey,
                        () -> {
                            showPublicKey = !showPublicKey;
                            refreshUi();
                        },
                        () -> editPublicKey(),
                        () -> copy("OmniGate Public API Key", GatewayApp.apiKey())),
                top(13));

        upstreamKey = UiKit.text(
                this,
                "",
                13.5f,
                text,
                false);
        card.addView(
                buildKeyRow(
                        "原核心 1601 Key（可选）",
                        upstreamKey,
                        () -> {
                            showUpstreamKey = !showUpstreamKey;
                            refreshUi();
                        },
                        () -> editUpstreamKey(),
                        () -> copy("Original Core API Key", GatewayApp.upstreamApiKey())),
                top(9));

        TextView relayKey = action(
                "设置 Relay Key",
                primary,
                primarySoft);
        relayKey.setOnClickListener(v -> editRelayKey());
        LinearLayout.LayoutParams relayLp = UiKit.matchWrap();
        relayLp.topMargin = UiKit.dp(this, 10);
        card.addView(relayKey, relayLp);

        return card;
    }

    private LinearLayout buildModelsCard() {
        LinearLayout card = UiKit.card(this, surface, border);

        TextView title = UiKit.text(
                this,
                "原核心模型 ID",
                19f,
                text,
                true);
        card.addView(title, UiKit.matchWrap());

        TextView models = UiKit.text(
                this,
                "deepseek-chat\nchatgpt-web\ngrok-web-fast\nglm-web-flash",
                14f,
                text,
                false);
        models.setTextIsSelectable(true);
        models.setLineSpacing(UiKit.dp(this, 5), 1f);
        LinearLayout.LayoutParams mLp = UiKit.matchWrap();
        mLp.topMargin = UiKit.dp(this, 11);
        card.addView(models, mLp);

        TextView note = UiKit.text(
                this,
                "Sidecar 不解释这些模型，也不重写网页发送逻辑；model 原样交给冻结的原 AI Gateway。",
                12.5f,
                muted,
                false);
        note.setLineSpacing(UiKit.dp(this, 3), 1f);
        LinearLayout.LayoutParams noteLp = UiKit.matchWrap();
        noteLp.topMargin = UiKit.dp(this, 10);
        card.addView(note, noteLp);

        return card;
    }

    private LinearLayout buildKeyRow(
            String label,
            TextView value,
            Runnable reveal,
            Runnable edit,
            Runnable copy) {
        LinearLayout row = UiKit.vertical(this);
        row.setPadding(
                UiKit.dp(this, 12),
                UiKit.dp(this, 11),
                UiKit.dp(this, 12),
                UiKit.dp(this, 11));
        row.setBackground(UiKit.rounded(
                surfaceSoft,
                UiKit.dp(this, 16)));

        TextView labelView = UiKit.text(
                this,
                label,
                11.5f,
                muted,
                true);
        row.addView(labelView, UiKit.matchWrap());

        LinearLayout valueRow = UiKit.horizontal(this);
        LinearLayout.LayoutParams valueRowLp = UiKit.matchWrap();
        valueRowLp.topMargin = UiKit.dp(this, 6);

        value.setTextIsSelectable(true);
        valueRow.addView(value, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView revealButton = tiny("显示/隐藏");
        revealButton.setOnClickListener(v -> reveal.run());
        valueRow.addView(revealButton);

        TextView copyButton = tiny("复制");
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cLp.leftMargin = UiKit.dp(this, 5);
        copyButton.setOnClickListener(v -> copy.run());
        valueRow.addView(copyButton, cLp);

        TextView editButton = tiny("编辑");
        LinearLayout.LayoutParams eLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        eLp.leftMargin = UiKit.dp(this, 5);
        editButton.setOnClickListener(v -> edit.run());
        valueRow.addView(editButton, eLp);

        row.addView(valueRow, valueRowLp);
        return row;
    }

    private TextView action(
            String value,
            int textColor,
            int fill) {
        TextView v = UiKit.text(
                this,
                value,
                13f,
                textColor,
                true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(
                UiKit.dp(this, 12),
                UiKit.dp(this, 10),
                UiKit.dp(this, 12),
                UiKit.dp(this, 10));
        v.setBackground(UiKit.rounded(
                fill,
                UiKit.dp(this, 16)));
        return v;
    }

    private TextView tiny(String value) {
        TextView v = UiKit.text(
                this,
                value,
                10.5f,
                primary,
                true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(
                UiKit.dp(this, 8),
                UiKit.dp(this, 6),
                UiKit.dp(this, 8),
                UiKit.dp(this, 6));
        v.setBackground(UiKit.rounded(
                surface,
                UiKit.dp(this, 12)));
        return v;
    }

    private LinearLayout.LayoutParams top(int dp) {
        LinearLayout.LayoutParams lp = UiKit.matchWrap();
        lp.topMargin = UiKit.dp(this, dp);
        return lp;
    }

    private void refreshUi() {
        boolean service = SidecarService.isServiceAlive();
        boolean upstream = SidecarProxyServer.isUpstreamReachable();

        if (sidecarStatus != null) {
            sidecarStatus.setText(
                    service
                            ? "● Sidecar 8787 运行中"
                            : "○ Sidecar 服务未运行");
            sidecarStatus.setTextColor(
                    service ? success : warning);
        }

        if (upstreamStatus != null) {
            upstreamStatus.setText(
                    upstream
                            ? "● 原 AI Gateway 1601 可达"
                            : "○ 原 AI Gateway 1601 未监听 · 请先启动原软件");
            upstreamStatus.setTextColor(
                    upstream ? success : warning);
        }

        if (relayStatus != null) {
            relayStatus.setText(
                    GatewayApp.tunnelEnabled()
                            ? GatewayApp.tunnelStatus()
                            : "公网 Relay 未开启");
        }

        updatingSwitch = true;
        if (relaySwitch != null) {
            relaySwitch.setChecked(GatewayApp.tunnelEnabled());
        }
        updatingSwitch = false;

        if (localUrl != null) {
            localUrl.setText("http://127.0.0.1:8787/v1");
        }

        if (publicUrl != null) {
            publicUrl.setText(
                    GatewayApp.effectivePublicUrl() + "/v1");
        }

        if (publicKey != null) {
            publicKey.setText(
                    showPublicKey
                            ? GatewayApp.apiKey()
                            : mask(GatewayApp.apiKey()));
        }

        if (upstreamKey != null) {
            String key = GatewayApp.upstreamApiKey();
            upstreamKey.setText(
                    key.isEmpty()
                            ? "未设置"
                            : (showUpstreamKey ? key : mask(key)));
        }
    }

    private String mask(String value) {
        if (value == null || value.isEmpty()) return "未设置";
        if (value.length() <= 8) return "••••••••";
        return value.substring(0, 4)
                + "••••••••"
                + value.substring(value.length() - 4);
    }

    private void editPublicKey() {
        showTextEditor(
                "公网真实 API Key",
                "公网请求必须精确匹配这个 Key。本机 8787 仍接受任意非空 Key。",
                GatewayApp.apiKey(),
                true,
                value -> GatewayApp.setApiKey(value));
    }

    private void editUpstreamKey() {
        showTextEditor(
                "原核心 1601 Key（可选）",
                "只有冻结原 AI Gateway 的本地 API 自己要求鉴权时才填写；留空时 Sidecar 不向上游附加 Authorization。",
                GatewayApp.upstreamApiKey(),
                true,
                value -> GatewayApp.setUpstreamApiKey(value));
    }

    private void editRelayKey() {
        showTextEditor(
                "Cloudflare Relay Key",
                "只用于手机 Sidecar 与 Cloudflare Relay 配对，不等于公网 API Key。",
                GatewayApp.relayKey(),
                true,
                value -> {
                    GatewayApp.setRelayKey(value);
                    if (!value.trim().isEmpty()) {
                        GatewayApp.setTunnelEnabled(true);
                    }
                    startSidecar(
                            SidecarService.ACTION_RESTART_RELAY);
                });
    }

    private void editCustomPublicUrl() {
        showTextEditor(
                "自定义公网域名",
                "留空恢复默认 gw-33e850180248.claua.de；非空地址必须通过 /__relay_health 验证。",
                GatewayApp.customPublicUrl(),
                false,
                value -> {
                    String normalized = normalizeHttps(value);
                    if (value.trim().isEmpty()) {
                        GatewayApp.setCustomPublicUrl("", false);
                        startSidecar(
                                SidecarService.ACTION_RESTART_RELAY);
                        return;
                    }
                    if (normalized == null) {
                        throw new IllegalArgumentException(
                                "请输入有效的 https:// 地址");
                    }
                    GatewayApp.setCustomPublicUrl(normalized, false);
                    verifyCustomUrl(normalized);
                });
    }

    private void showTextEditor(
            String title,
            String message,
            String current,
            boolean secret,
            ValueSaver saver) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(current == null ? "" : current);
        input.setSelectAllOnFocus(true);

        if (secret) {
            input.setInputType(
                    InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            input.setInputType(
                    InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_VARIATION_URI);
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    try {
                        saver.save(
                                input.getText().toString());
                        refreshUi();
                        Toast.makeText(
                                this,
                                "已保存",
                                Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(
                                this,
                                e.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void verifyCustomUrl(String base) {
        Toast.makeText(
                this,
                "正在验证自定义域名",
                Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            boolean ok = false;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(
                        base + "/__relay_health")
                        .openConnection();
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(10_000);
                connection.setUseCaches(false);

                if (connection.getResponseCode() == 200) {
                    try (InputStream in = connection.getInputStream()) {
                        ByteArrayOutputStream out =
                                new ByteArrayOutputStream();
                        byte[] buffer = new byte[2048];
                        int n;
                        while ((n = in.read(buffer)) >= 0
                                && out.size() < 16 * 1024) {
                            if (n > 0) out.write(buffer, 0, n);
                        }
                        JSONObject body = new JSONObject(
                                out.toString(
                                        StandardCharsets.UTF_8.name()));
                        ok = body.optBoolean("ok");
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }

            boolean result = ok;
            runOnUiThread(() -> {
                GatewayApp.setCustomPublicUrl(
                        base,
                        result);
                refreshUi();
                Toast.makeText(
                        this,
                        result
                                ? "自定义域名验证通过"
                                : "验证失败，继续使用默认公网地址",
                        Toast.LENGTH_LONG).show();
                if (result) {
                    startSidecar(
                            SidecarService.ACTION_RESTART_RELAY);
                }
            });
        }, "sidecar-custom-domain-check").start();
    }

    private String normalizeHttps(String value) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return "";
        if (!v.startsWith("https://")) return null;

        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        if (v.endsWith("/v1")) {
            v = v.substring(0, v.length() - 3);
        }

        try {
            URL url = new URL(v);
            if (!"https".equalsIgnoreCase(url.getProtocol())
                    || url.getHost() == null
                    || url.getHost().isEmpty()) {
                return null;
            }
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    private void copy(String label, String value) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(
                        Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(
                ClipData.newPlainText(label, value));
        Toast.makeText(
                this,
                label + " 已复制",
                Toast.LENGTH_SHORT).show();
    }

    private void startSidecar(String action) {
        Intent intent =
                new Intent(this, SidecarService.class);
        if (action != null) {
            intent.setAction(action);
        }

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startSidecar(
                SidecarService.ACTION_ENSURE_RUNNING);
        refreshUi();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);
        super.onDestroy();
    }

    private interface ValueSaver {
        void save(String value);
    }
}
