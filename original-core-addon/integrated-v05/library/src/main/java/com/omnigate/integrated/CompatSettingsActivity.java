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

    private TextView serviceState, healthState, uploadState, replyState, publicStatus;
    private EditText upstreamKey, publicKey, relayKey, prompt;
    private Spinner models;
    private TextView localBadge, upstreamBadge, upstreamGuide;
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
        p.setBackground(background(Color.WHITE, 18, 0xFFE6E8F2));
        p.setPadding(dp(14), dp(15), dp(14), dp(14));
        margin(page, p, 12);
        margin(p, text(title, 17, 0xFF1D2442, true), 4);
        margin(p, text(subtitle, 12, 0xFF697187, false), 10);
        return p;
    }

    private Button button(LinearLayout parent, String label, boolean primary) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(primary ? Color.WHITE : 0xFF3D49AE);
        b.setBackground(background(primary ? 0xFF4B43DA : 0xFFEBEEFF, 14, 0));
        b.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(43));
        p.bottomMargin = dp(8);
        parent.addView(b, p);
        return b;
    }

    private LinearLayout rowButtons(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        margin(parent, r, 7);
        return r;
    }

    private Button rowButton(LinearLayout row, String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(primary ? Color.WHITE : 0xFF3948AB);
        b.setBackground(background(primary ? 0xFF5144E8 : 0xFFF1F2FE, 12, 0));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1);
        p.rightMargin = dp(5);
        row.addView(b, p);
        return b;
    }

    private TextView statusBadge(LinearLayout parent, String label) {
        TextView b = text(label, 12, 0xFF3D4381, true);
        b.setPadding(dp(12), dp(9), dp(12), dp(9));
        b.setBackground(background(0xFFF2F3FA, 12, 0));
        margin(parent, b, 6);
        return b;
    }

    private EditText field(LinearLayout parent, String label, String value, boolean secret) {
        margin(parent, text(label, 12, 0xFF3A405A, true), 4);
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setTextSize(14);
        e.setTextColor(0xFF252A41);
        e.setBackground(background(0xFFF3F5FA, 12, 0xFFE5E9F2));
        e.setPadding(dp(14), dp(9), dp(14), dp(9));
        if (secret) e.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (value != null) e.setText(value);
        margin(parent, e, 10);
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
        page.setPadding(dp(14), dp(12), dp(14), dp(24));
        scroller.addView(page);
        setContentView(scroller);

        LinearLayout hero = vertical();
        GradientDrawable gradient = new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[]{0xFF342B98, 0xFF5F53E7, 0xFF2E83E7});
        gradient.setCornerRadius(dp(22));
        hero.setBackground(gradient);
        hero.setPadding(dp(19), dp(17), dp(19), dp(18));
        margin(page, hero, 14);
        margin(hero, text("◈ OmniGate", 24, Color.WHITE, true), 3);
        margin(hero, text("API 控制台   /   v0.7.0", 13, 0xFFE5E7FF, false), 7);
        margin(hero, text("本地优先 · 手动启停 · 网页注入保持不变", 12, 0xFFD6E9FF, false), 0);

        LinearLayout connection = panel(page, "本地 API 服务", "仅监听手机本机 127.0.0.1，默认不暴露到公网");
        localBadge = statusBadge(connection, "○ 8787 检查中");
        upstreamBadge = statusBadge(connection, "○ 1601 检查中");
        serviceState = text("", 12, 0xFF637086, false);
        margin(connection, serviceState, 9);
        LinearLayout actions = rowButtons(connection);
        Button start = rowButton(actions, "启动 8787", true);
        start.setOnClickListener(v -> startLocalServer());
        Button stop = rowButton(actions, "停止服务", false);
        stop.setOnClickListener(v -> {
            GatewayApp.setTunnelEnabled(false);
            RelayRuntime.stop();
            stopService(new Intent(this, SidecarService.class));
            LocalProxyRuntime.stop();
            serviceState.setText("服务已停止");
            healthState.setText("未连接");
            updateState();
        });
        Button health = button(connection, "检测 /health 连接", false);
        health.setOnClickListener(v -> probeHealth());
        healthState = text("未执行 HTTP 测试", 12, 0xFF59657E, false);
        margin(connection, healthState, 5);
        upstreamGuide = text("", 12, 0xFF91601C, false);
        margin(connection, upstreamGuide, 8);
        Button originalApi = button(connection, "修复端口冲突并启动原 API", true);
        originalApi.setOnClickListener(v -> {
            String outcome = OriginalCoreBridge.requestExplicitNativeStart(this);
            if (outcome.contains("失败") || outcome.contains("停止启动") ||
                    outcome.contains("无法") || outcome.contains("不一致")) {
                upstreamGuide.setText(outcome);
                toast(outcome);
            } else {
                upstreamGuide.setText(outcome);
            }
            updateState();
        });
        // Explicit recovery only: never evict the unknown 1601 listener.
        // A port migration must not run during a model response.
        Button fallbackPort=button(connection,
            "1601 被占用：安全使用备用原生端口（需确认）",false);
        fallbackPort.setOnClickListener(v->
            new android.app.AlertDialog.Builder(this)
                .setTitle("使用原版 API 备用端口？")
                .setMessage("当前 1601 可连接，但并非由可见 MainActivity 持有。" +
                    "仅在网页空闲时尝试把原版 API 改到 1602–1610 的空闲端口。" +
                    "不杀进程、不停止原网页、不清除登录数据；" +
                    "8787 自动读取新的原生端口。" +
                    "如果原页面正在生成回答，请取消。")
                .setNegativeButton("取消",null)
                .setPositiveButton("检查并切换",(dialog,which)->{
                    fallbackPort.setEnabled(false);
                    originalApi.setEnabled(false);
                    upstreamGuide.setText(
                        "正在核对原网页、端口归属和生成状态；不会中断旧请求");
                    new Thread(()->{
                        String result=OriginalCoreBridge.prepareAlternatePortOnExplicitConsent();
                        main.post(()->{
                            if(isDestroyed() || isFinishing())return;
                            fallbackPort.setEnabled(true);
                            originalApi.setEnabled(true);
                            if(result.startsWith("ALTERNATE_PORT_PREPARED")) {
                                String started=OriginalCoreBridge
                                    .requestExplicitNativeStart(this);
                                upstreamGuide.setText(result+"。"+started+
                                    "；返回原模型页后再次核对 1601 归属/当前端口。");
                            } else upstreamGuide.setText(result);
                            updateState();
                        });
                    },"omnigate-alternate-native-port-check").start();
                }).show()
        );
        Button originalOverlay = button(connection, "打开原版悬浮窗（可能需要授权）", false);
        originalOverlay.setOnClickListener(v -> {
            String result = OriginalCoreBridge.requestOriginalOverlay(this);
            toast(result);
        });
        Button originalPage = button(connection, "选择/打开 AI 网页会话", false);
        originalPage.setOnClickListener(v -> {
            try {
                Intent i = new Intent();
                i.setClassName(getPackageName(), "com.deepseek2.app.ui.ProviderActivity");
                startActivity(i);
            } catch (RuntimeException ex) {
                toast("网页入口失败：" + ex.getClass().getSimpleName());
            }
        });

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
        margin(api, text("Local Key: 任意非空（如 test），网站仍需原账号登录", 12, 0xFF68718A, false), 8);
        margin(api, text("目标模型", 12, 0xFF3A405A, true), 6);
        models = new Spinner(this);
        String[] modelNames = {"deepseek-chat", "chatgpt-web", "grok-web-fast", "glm-web-flash"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
            this, android.R.layout.simple_spinner_dropdown_item, modelNames) {
            @Override public View getView(int position, View convertView,
                                           android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(0xFF1D2442);
                view.setTextSize(14);
                view.setPadding(dp(12), dp(12), dp(12), dp(12));
                return view;
            }
            @Override public View getDropDownView(int position, View convertView,
                                                   android.view.ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setTextColor(0xFF1D2442);
                view.setBackgroundColor(Color.WHITE);
                view.setTextSize(14);
                view.setPadding(dp(12), dp(12), dp(12), dp(12));
                return view;
            }
        };
        models.setAdapter(adapter);
        models.setBackground(background(0xFFF3F5FC, 12, 0xFFE5E9F2));
        margin(api, models, 2);

        LinearLayout files = panel(page, "图片与文件",
            "原版网页注入：打开会话 → 上传附件 → 等待网站确认 → 发送问题");
        TextView sessionHint = text(
            "注意：选择文件只在本机完成；需要先进入对应 AI 提供商网页并保持可用登录状态。",
            12, 0xFF8A5937, true);
        margin(files, sessionHint, 9);
        Button openProvider = button(files, "先打开 AI 提供商网页", false);
        openProvider.setOnClickListener(v -> {
            try {
                Intent web = new Intent();
                web.setClassName(getPackageName(),
                    "com.deepseek2.app.ui.ProviderActivity");
                startActivity(web);
            } catch (RuntimeException ex) {
                toast("打开网页选择失败：" + ex.getClass().getSimpleName());
            }
        });
        Button pick = button(files, "选择图片 / 文件", false);
        pick.setOnClickListener(v -> openPicker());
        uploadState = text("尚未选择文件（最大 8 MiB）", 13, 0xFF65708F, false);
        margin(files, uploadState, 10);
        prompt = field(files, "附带问题", "请描述附件内容。", false);
        Button send = button(files, "发送附件到已打开的模型会话（测试）", true);
        send.setOnClickListener(v -> sendAttachment());
        replyState = text("尚未发送；上游接收成功需要真实回复验证", 12, 0xFF68718A, false);
        margin(files, replyState, 10);
        margin(files, text("也可以在模型网页的悬浮小窗使用 API／文件注入双页签。原版注入依赖网页 file input 和网站上传完成迹象。只有成功回复并经网页确认，"
          + "才能标记附件送达；如果网站不支持附件或要求登录，本功能不会绕过。", 12, 0xFF68718A, false), 0);

        Button expand = button(page, "高级配置  ▾  密钥与公网选项", false);
        LinearLayout settings = panel(page, "高级配置", "本地 8787 不需要更改密钥；仅配置原上游或公网时展开");
        settings.setVisibility(View.GONE);
        expand.setOnClickListener(v -> {
            boolean show = settings.getVisibility() != View.VISIBLE;
            settings.setVisibility(show ? View.VISIBLE : View.GONE);
            expand.setText(show ? "高级配置  ▴  点击折叠" : "高级配置  ▾  密钥与公网选项");
        });
        upstreamKey = field(settings, "原 1601 上游 Key（可留空）", GatewayApp.upstreamApiKey(), true);
        publicKey = field(settings, "公网 API Key", GatewayApp.apiKey(), true);
        relayKey = field(settings, "Relay 配对 Key（默认不用）", GatewayApp.relayKey(), true);
        Button save = button(settings, "保存配置", true);
        save.setOnClickListener(v -> saveConfig());

        margin(settings,text("沿用原穿透地址："+
            GatewayApp.OWN_PUBLIC_BASE+"/v1",12,0xFF323B83,true),8);
        Button copyPublic = button(settings,"复制原公网 Base URL",false);
        copyPublic.setOnClickListener(v -> {
            ClipboardManager cb=(ClipboardManager)getSystemService(
                Context.CLIPBOARD_SERVICE);
            if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText(
                "OmniGate HTTPS API",GatewayApp.OWN_PUBLIC_BASE+"/v1"));
        });
        publicStatus=text(
            GatewayApp.tunnelEnabled()
                ? "公网穿透已配置启用；状态："+GatewayApp.tunnelStatus()
                : "公网穿透默认关闭；本机 8787 不受影响",
            12,0xFF65708F,false);
        margin(settings,publicStatus,8);
        Button connectPublic=button(settings,
            "立即连接 / 重连已配置 Relay",true);
        connectPublic.setOnClickListener(v->{
            if(!GatewayApp.tunnelEnabled()){
                publicStatus.setText("请先明确开启公网开关；尚未连接");
                return;
            }
            if(GatewayApp.relayKey().length()<24){
                publicStatus.setText("配对 Key 不完整；未连接");
                return;
            }
            if(RelayRuntime.isConnected()){
                refreshRelayStatus();
                return;
            }
            RelayRuntime.restart();
            refreshRelayStatus();
        });
        Button refreshPublic=button(settings,"刷新 Relay 实际连接状态",false);
        refreshPublic.setOnClickListener(v->refreshRelayStatus());
        Button togglePublic=button(settings,"手动开启 / 关闭原有穿透",false);
        togglePublic.setOnClickListener(v -> {
            if(GatewayApp.tunnelEnabled()){
                GatewayApp.setTunnelEnabled(false);
                RelayRuntime.stop();
                refreshRelayStatus();
                return;
            }
            if(GatewayApp.relayKey().length()<24){
                publicStatus.setText("需要先保存正确的 Relay 配对 Key；未连接");
                return;
            }
            new android.app.AlertDialog.Builder(this)
                .setTitle("开启现有 OmniGate 公网穿透？")
                .setMessage("沿用 "+GatewayApp.OWN_PUBLIC_BASE+
                    "/v1。此操作会在当前应用进程内建立唯一 Relay WebSocket，"+
                    "不依赖前台服务启动成功。公网必须使用独立 API Key，"+
                    "后台可能被 Android 回收，不保证一直在线。")
                .setNegativeButton("取消",null)
                .setPositiveButton("确认连接",(dialog,which)->{
                    GatewayApp.setTunnelEnabled(true);
                    try {
                        RelayRuntime.ensureStarted();
                        refreshRelayStatus();
                    } catch(RuntimeException e){
                        GatewayApp.setTunnelEnabled(false);
                        RelayRuntime.stop();
                        publicStatus.setText("Relay 初始化失败："+
                            e.getClass().getSimpleName()+"；开关已恢复关闭");
                    }
                }).show();
        });

        LinearLayout note = panel(page, "连续生成", "代理不按固定时间主动截停");
        margin(note, text("8787 不设读取截止时间；原 1601、网站及 Android 后台仍可能中断。", 12, 0xFF5C6582, false), 0);

        updateState();
    }

    @Override protected void onResume() {
        super.onResume();
        if (localBadge != null && upstreamBadge != null) updateState();
        refreshRelayStatus();
    }

    private void refreshRelayStatus() {
        if(publicStatus==null)return;
        boolean toggle=GatewayApp.tunnelEnabled();
        boolean started=RelayRuntime.isStarted();
        boolean connected=RelayRuntime.isConnected();
        publicStatus.setText("公网开关="+(toggle?"开启":"关闭")+
            "；Relay 客户端="+(started?"已启动":"未启动")+
            "；WebSocket="+(connected?"已连接":"未连接")+
            "；状态="+GatewayApp.tunnelStatus()+
            "。仅端口可达不代表模型 API 可用。");
    }

    private void updateState() {
        if (serviceState == null) return;
        boolean local = LocalProxyRuntime.isListening();
        boolean upstream = SidecarProxyServer.isUpstreamReachable();
        boolean owned = OriginalCoreBridge.nativeOwnerRunning();
        int nativePort = OriginalCoreBridge.configuredPort();
        localBadge.setText(local ? "● 本地 8787 · 已监听" : "○ 本地 8787 · 已停止");
        localBadge.setTextColor(local ? 0xFF136A48 : 0xFF664D87);
        localBadge.setBackground(background(local ? 0xFFE0F7ED : 0xFFF1EDF7, 12, 0));
        boolean conflicted = OriginalCoreBridge.isPortConflicted();
        upstreamBadge.setText(conflicted
            ? "● 端口冲突：原核心和代理都配置为 8787"
            : owned && upstream ? "● 原核心 " + nativePort + " · 服务运行且 TCP 可连接"
            : upstream ? "● " + nativePort + " TCP 已连通，但原版服务归属未确认"
            : "● 原核心 " + nativePort + " · 当前不可连接");
        upstreamBadge.setTextColor(owned && upstream ? 0xFF136A48 : 0xFF966317);
        upstreamBadge.setBackground(background(owned && upstream ? 0xFFE0F7ED : 0xFFFFF3E1, 12, 0));
        serviceState.setText((local ? "8787 已监听" : "8787 未启动")
            + " · " + OriginalCoreBridge.nativeLifecycleState());
        upstreamGuide.setText(conflicted
            ? "8787→8787 自循环。请点击下方修复按钮；原版页面端口字段与保存设置必须一致。"
            : owned && upstream ? "原版 MainActivity.server.isRunning=true，"
                + nativePort + " TCP 已连接。仍需要真实模型回复才能证明 API 有效。"
            : "原版网页聊天与 API 服务独立。请点击下方按钮恢复原版 MainActivity；"
                + "不要关闭该模型页，因为原版 onDestroy 会调用 stopServer。状态："
                + OriginalCoreBridge.lastStartStatus());
    }

    private void startLocalServer() {
        if (!LocalProxyRuntime.start()) {
            updateState();
            healthState.setText("无法绑定 8787，可能被独立 Sidecar 占用");
            return;
        }
        updateState();
        // Do not launch an Android 14+ foreground service in the first
        // recovery candidate. A bad service type or restriction may kill the
        // entire app process asynchronously even when Activity catches errors.
        // Foreground-only localhost listener is safer and independently testable.
        healthState.setText("本地服务已尝试启动；切换到后台后可能被系统回收");
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
                    JSONObject reply = new JSONObject(b.toString("UTF-8"));
                    boolean alive = code == 200 && reply.optBoolean("ok", false);
                    boolean upstream = reply.optBoolean("upstreamReachable", false);
                    boolean owned = reply.optBoolean("originalServerRunning", false);
                    boolean host = reply.optBoolean("originalOwnerAlive", false);
                    boolean conflict = reply.optBoolean("portConflict", false);
                    int target = OriginalCoreBridge.configuredPort();
                    result = alive
                        ? (conflict
                            ? "8787 正常；端口冲突，原服务不能使用同一个端口。"
                            : (owned && upstream)
                                ? "8787 正常 · 原版 server.isRunning=true · " + target +
                                  " TCP 可连接（模型回复尚未验收）"
                                : "8787 正常 · 原版宿主 " + (host?"存活":"不存在")
                                  + " · 原版 server.isRunning=" + owned
                                  + " · " + target + " TCP=" + upstream +
                                  "；先恢复原版模型页，不能据此宣布 API 可用。")
                        : "HTTP " + code + " · 本地健康检查未通过";
                }
            } catch (Exception e) {
                result = "不可访问: " + e.getClass().getSimpleName()
                    + "。检查是否已经启动、或端口被旧 Sidecar 占用。";
            } finally {
                if (c != null) c.disconnect();
            }
            String captured = result;
            main.post(() -> {
                if (!isDestroyed()) {
                    healthState.setText(captured);
                    healthState.setTextColor(captured.contains("server.isRunning=true")
                        ? 0xFF216B51 : 0xFFA65831);
                    updateState();
                }
            });
        }, "omnigate-health").start();
    }

    private void saveConfig() {
        try {
            GatewayApp.setUpstreamApiKey(upstreamKey.getText().toString());
            GatewayApp.setApiKey(publicKey.getText().toString());
            String previousRelayKey=GatewayApp.relayKey();
            GatewayApp.setRelayKey(relayKey.getText().toString());
            if(GatewayApp.tunnelEnabled() &&
                !previousRelayKey.equals(GatewayApp.relayKey())) {
                RelayRuntime.restart();
            }
            refreshRelayStatus();
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
            uploadState.setText("请先启动本地 8787 服务");
            return;
        }
        if (!SidecarProxyServer.isUpstreamReachable()) {
            replyState.setText("附件仅完成本机选择；原 API 不可连接。请先启动原核心。");
            updateState();
            return;
        }
        if (!OriginalCoreBridge.originalHostAlive()) {
            replyState.setText("未检测到存活的原 AI 网页宿主。先点“先打开 AI 提供商网页”，"
                + "进入对应网站、确认正常对话后，再返回此页发送。");
            return;
        }
        String active = OriginalCoreBridge.activeProviderModel();
        String selected = models.getSelectedItem() == null
            ? "" : models.getSelectedItem().toString();
        if (active.isEmpty()) {
            replyState.setText("暂无法确认原核心已激活哪个模型；请先打开网页并测试普通文本。"
                + "为避免附件投递错会话，本次没有提交。");
            return;
        }
        if (!active.equals(selected)) {
            replyState.setText("模型不匹配：原核心当前为 " + active
                + "，设置页选择了 " + selected
                + "。请先切换原网页提供商或调整模型选项。未提交附件。");
            return;
        }
        Uri uri = selectedUri;
        String name = selectedName;
        String mime = selectedMime;
        String question = prompt.getText().toString().trim();
        String model = models.getSelectedItem().toString();
        replyState.setText("已验证端口、会话宿主和模型匹配；正在按原版 V2 网页注入协议"
            + "等待附件回执。过程中不主动截停生成。");
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
