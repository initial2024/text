package com.omnigate.integrated;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Human-readable, single-launcher API console. Original GeckoView remains untouched.
 * Starts local 8787 inside app process explicitly; optional foreground service for
 * background lifetime. No automatic provider-level network launch.
 */
public final class CompatSettingsActivity extends Activity {
    private static final String LOCAL_URL = "http://127.0.0.1:8787/v1";
    private static final int PICK_DOCUMENT = 556;
    private static final int MAX_FILE_BYTES = 8 * 1024 * 1024;
    private final Handler main = new Handler(Looper.getMainLooper());

    private TextView serviceState, healthState, uploadState, replyState;
    private EditText upstreamKey, publicKey, relayKey, prompt;
    private Spinner models;
    private Uri selectedUri;
    private String selectedName;
    private String selectedMime;
    private boolean lastHealthOk = false;

    private int dp(int n) {
        return (int)(getResources().getDisplayMetrics().density * n + 0.5f);
    }

    private GradientDrawable background(int fill, int radius, int border) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        if (border != 0) d.setStroke(dp(1), border);
        return d;
    }

    private LinearLayout vertical() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(size);
        if (bold) v.setTypeface(null, Typeface.BOLD);
        return v;
    }

    private void margin(LinearLayout parent, View child, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                -1, -2);
        p.bottomMargin = dp(bottom);
        parent.addView(child, p);
    }

    private LinearLayout panel(LinearLayout page, String title, String subtitle) {
        LinearLayout p = vertical();
        p.setBackground(background(Color.WHITE, 20, 0xFFE2E5F1));
        p.setPadding(dp(17), dp(18), dp(17), dp(18));
        margin(page, p, 14);
        margin(p, text(title, 17, 0xFF1D2442, true), 6);
        margin(p, text(subtitle, 12, 0xFF697187, false), 14);
        return p;
    }

    private Button button(LinearLayout parent, String label, boolean primary) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(primary ? Color.WHITE : 0xFF3D49AE);
        b.setBackground(background(primary ? 0xFF4B43DA : 0xFFEBEEFF, 14, 0));
        b.setPadding(dp(8), dp(10), dp(8), dp(10));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(46));
        p.bottomMargin = dp(10);
        parent.addView(b, p);
        return b;
    }

    private EditText field(LinearLayout parent, String label, String value, boolean secret) {
        margin(parent, text(label, 13, 0xFF3A405A, true), 5);
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextSize(14);
        e.setTextColor(0xFF252A41);
        e.setBackground(background(0xFFF3F5FA, 12, 0xFFE5E9F2));
        e.setPadding(dp(14), dp(9), dp(14), dp(9));
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (value != null) e.setText(value);
        margin(parent, e, 14);
        return e;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        GatewayApp.initialize(getApplicationContext());
        getWindow().setStatusBarColor(0xFFF4F6FC);
        getWindow().setNavigationBarColor(0xFFF4F6FC);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setBackgroundColor(0xFFF4F6FC);
        LinearLayout page = vertical();
        page.setPadding(dp(18), dp(18), dp(18), dp(28));
        scroller.addView(page);
        setContentView(scroller);

        margin(page, text("OmniGate", 29, 0xFF24235E, true), 4);
        margin(page, text("API 控制台  ·  v0.5.6", 14, 0xFF65708F, false), 20);

        LinearLayout connection = panel(page, "本地 API 服务", "仅监听手机本机 127.0.0.1，默认不暴露到公网");
        serviceState = text("正在读取状态", 15, 0xFF343B67, true);
        margin(connection, serviceState, 12);
        Button start = button(connection, "启动 8787 本地服务", true);
        start.setOnClickListener(v -> startLocalServer());
        Button stop = button(connection, "停止本地服务", false);
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, SidecarService.class));
            LocalProxyRuntime.stop();
            serviceState.setText("服务已停止");
            healthState.setText("未连接");
        });
        Button health = button(connection, "实际检测 /health", false);
        health.setOnClickListener(v -> probeHealth());
        healthState = text("尚未测试 /health", 13, 0xFF59657E, false);
        margin(connection, healthState, 4);

        LinearLayout api = panel(page, "客户端连接", "Minis、DSH 等客户端填写以下信息");
        margin(api, text(LOCAL_URL, 14, 0xFF323B83, true), 10);
        Button copy = button(api, "复制 Base URL", false);
        copy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("OmniGate API", LOCAL_URL));
                toast("地址已复制");
            }
        });
        margin(api, text("本地 Key 任意非空，例如 test；不代表网页账号已登录。", 12, 0xFF68718A, false), 0);
        models = new Spinner(this);
        String[] modelNames = {"deepseek-chat", "chatgpt-web", "grok-web-fast", "glm-web-flash"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modelNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        models.setAdapter(adapter);
        margin(api, models, 2);

        LinearLayout files = panel(page, "图片与文件", "先验证原生选择器，再通过 8787 测试附件透传");
        Button pick = button(files, "选择图片 / 文件", false);
        pick.setOnClickListener(v -> openPicker());
        uploadState = text("尚未选择文件（最大 8 MiB）", 13, 0xFF65708F, false);
        margin(files, uploadState, 10);
        prompt = field(files, "附带问题", "请描述附件内容。", false);
        Button send = button(files, "测试向网页注入发送附件", true);
        send.setOnClickListener(v -> sendAttachment());
        replyState = text("尚未发送；上游接收成功需要真实回复验证", 12, 0xFF68718A, false);
        margin(files, replyState, 10);
        margin(files, text("网页上传按钮无反应，可能来自网站 DOM、GeckoView 文件选择回调或账户权限。这里先独立检查 Android 文件选择和原 1601 注入。", 12, 0xFF68718A, false), 0);

        LinearLayout settings = panel(page, "高级配置", "公网 Relay 默认关闭；原网页账号登录与 API Key 是两种机制");
        upstreamKey = field(settings, "原 1601 上游 Key（可留空）", GatewayApp.upstreamApiKey(), true);
        publicKey = field(settings, "公网 API Key", GatewayApp.apiKey(), true);
        relayKey = field(settings, "Relay 配对 Key（默认不用）", GatewayApp.relayKey(), true);
        Button save = button(settings, "保存配置", true);
        save.setOnClickListener(v -> saveConfig());

        LinearLayout note = panel(page, "连续生成说明", "避免兼容代理主动截停回答");
        margin(note, text("8787 代理的上游读取超时为无限等待，断开本地客户端时继续读取原上游直至自然完成。原 1601、网站会话上限以及 Android 后台限制仍可能中止请求；不能保证无限生成。", 12, 0xFF5C6582, false), 3);

        updateState();
    }

    private void updateState() {
        if (serviceState == null) return;
        String message = LocalProxyRuntime.isListening()
            ? "● 8787 已监听（应用进程内）" : "● 8787 未启动";
        message += SidecarProxyServer.isUpstreamReachable()
            ? "\n● 1601 上游 TCP 可连接" : "\n● 1601 上游尚不可连接";
        message += "\n" + LocalProxyRuntime.status();
        serviceState.setText(message);
    }

    private void startLocalServer() {
        if (!LocalProxyRuntime.start()) {
            updateState();
            healthState.setText("无法绑定 8787，可能被独立 Sidecar 占用");
            return;
        }
        updateState();
        // A foreground-service is a best-effort background keepalive. Local
        // connectivity must work in foreground without this permission.
        try {
            Intent service = new Intent(this, SidecarService.class);
            service.setAction(SidecarService.ACTION_ENSURE_RUNNING);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
        } catch (RuntimeException ex) {
            healthState.setText("本地监听已启动；后台服务受限: "
                + ex.getClass().getSimpleName());
        }
        probeHealth();
    }

    private void probeHealth() {
        healthState.setText("正在读取 /health …");
        new Thread(() -> {
            HttpURLConnection c = null;
            String result;
            try {
                c = (HttpURLConnection)new URL("http://127.0.0.1:8787/health").openConnection();
                c.setConnectTimeout(3000);
                c.setReadTimeout(3000);
                int code = c.getResponseCode();
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    byte[] buf = new byte[2048];
                    int n;
                    while ((n = in.read(buf)) >= 0) {
                        if (b.size() >= 8192) break;
                        b.write(buf, 0, Math.min(n, 8192 - b.size()));
                    }
                    result = "HTTP " + code + " · " + b.toString("UTF-8");
                }
            } catch (Exception e) {
                result = "不可访问: " + e.getClass().getSimpleName()
                    + "。检查是否已经启动、或端口被旧 Sidecar 占用。";
            } finally {
                if (c != null) c.disconnect();
            }
            String captured = result;
            main.post(() -> { if (!isDestroyed()) healthState.setText(captured); });
        }, "omnigate-health").start();
    }

    private void saveConfig() {
        try {
            GatewayApp.setUpstreamApiKey(upstreamKey.getText().toString());
            GatewayApp.setApiKey(publicKey.getText().toString());
            GatewayApp.setRelayKey(relayKey.getText().toString());
            toast("配置已保存");
        } catch (RuntimeException e) {
            toast("保存失败：" + e.getMessage());
        }
    }

    private void openPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, PICK_DOCUMENT);
        } catch (RuntimeException e) {
            uploadState.setText("文件选择器失败：" + e.getClass().getSimpleName());
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_DOCUMENT) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            uploadState.setText("已取消文件选择");
            return;
        }
        selectedUri = data.getData();
        selectedMime = getContentResolver().getType(selectedUri);
        if (selectedMime == null) selectedMime = "application/octet-stream";
        selectedName = "attachment";
        try (Cursor cursor = getContentResolver().query(selectedUri,
            new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
            null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && !cursor.isNull(idx)) selectedName = cursor.getString(idx);
                int size = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (size >= 0 && !cursor.isNull(size) &&
                    cursor.getLong(size) > MAX_FILE_BYTES) {
                    selectedUri = null;
                    uploadState.setText("超过 8 MiB 上限，请选择其他附件");
                    return;
                }
            }
        } catch (Exception ignored) {
        }
        uploadState.setText("已选：" + selectedName + " · " + selectedMime);
    }

    private void sendAttachment() {
        if (selectedUri == null) {
            uploadState.setText("请先选择图片或文件");
            return;
        }
        if (!LocalProxyRuntime.isListening()) {
            uploadState.setText("请先点击“启动 8787 本地服务”");
            return;
        }
        Uri uri = selectedUri;
        String name = selectedName;
        String mime = selectedMime;
        String question = prompt.getText().toString().trim();
        String model = models.getSelectedItem().toString();
        replyState.setText("发送中……等待原网页处理。");
        new Thread(() -> {
            HttpURLConnection c = null;
            String result;
            try {
                ByteArrayOutputStream file = new ByteArrayOutputStream();
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new java.io.IOException("无法读取附件");
                    byte[] b = new byte[8192]; int n;
                    while ((n = in.read(b)) != -1) {
                        if (file.size() + n > MAX_FILE_BYTES)
                            throw new java.io.IOException("附件超过 8 MiB");
                        file.write(b, 0, n);
                    }
                }
                JSONObject entry = new JSONObject()
                    .put("id", UUID.randomUUID().toString())
                    .put("name", name)
                    .put("mime", mime)
                    .put("current", true)
                    .put("data", Base64.encodeToString(file.toByteArray(), Base64.NO_WRAP));
                JSONObject envelope = new JSONObject()
                    .put("v", 2)
                    .put("prompt", question.isEmpty() ? "请查看附件" : question)
                    .put("rolloverPrompt", question)
                    .put("attachments", new JSONArray().put(entry));
                String text = "<<<DSH_WEB_RELAY_V2_ATTACHMENT_ENVELOPE>>>"
                    + Base64.encodeToString(envelope.toString()
                        .getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP)
                    + "<<<END_DSH_WEB_RELAY_V2_ATTACHMENT_ENVELOPE>>>";
                JSONObject json = new JSONObject()
                    .put("model", model)
                    .put("stream", false)
                    .put("messages", new JSONArray().put(new JSONObject()
                        .put("role", "user").put("content", text)));
                byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
                c = (HttpURLConnection)new URL(LOCAL_URL + "/chat/completions").openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(0);
                c.setRequestMethod("POST");
                c.setRequestProperty("Authorization", "Bearer test");
                c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                c.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = c.getOutputStream()) { output.write(body); }
                int code = c.getResponseCode();
                InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                if (in != null) try (InputStream source = in) {
                    byte[] chunk = new byte[4096]; int n;
                    while ((n = source.read(chunk)) >= 0)
                        if (b.size() < 10000) b.write(chunk, 0, Math.min(n, 10000-b.size()));
                }
                result = "HTTP " + code + " · " + b.toString("UTF-8")
                    + "\n仅有 HTTP 响应不等于附件已被网站上传。";
            } catch (Exception ex) {
                result = "发送失败：" + ex.getClass().getSimpleName()
                    + (ex.getMessage() == null ? "" : " · " + ex.getMessage());
            } finally {
                if (c != null) c.disconnect();
            }
            String captured = result;
            main.post(() -> {
                if (!isDestroyed()) replyState.setText(captured);
            });
        }, "omnigate-attachment").start();
    }
}
