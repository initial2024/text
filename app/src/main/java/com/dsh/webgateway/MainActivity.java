package com.dsh.webgateway;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
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

public final class MainActivity extends Activity {
    private static final int REQUEST_WEB_FILE = 5201;
    private static final int TAB_OVERVIEW = 0;
    private static final int TAB_SESSIONS = 1;
    private static final int TAB_WEB = 2;

    private WebView webView;
    private WebRelay relay;
    private ValueCallback<Uri[]> webFilePathCallback;
    private FrameLayout pageHost;
    private View[] pages;
    private LinearLayout[] navItems;
    private ImageView[] navIcons;
    private TextView[] navLabels;
    private int activeTab = TAB_OVERVIEW;

    private TextView topSubtitle;
    private TextView statusTitle;
    private TextView statusSubtitle;
    private TextView endpointText;
    private TextView modelText;
    private TextView apiKeyText;
    private TextView publicUrlText;
    private TextView tunnelStateText;
    private TextView tunnelBackendText;
    private TextView currentProviderTitle;
    private TextView currentProviderDesc;
    private TextView webProviderTitle;
    private Switch tunnelSwitch;
    private boolean updatingTunnelSwitch;
    private boolean pendingOverlayPermission;
    private boolean apiKeyVisible;

    private int bg;
    private int surface;
    private int surfaceSoft;
    private int text;
    private int muted;
    private int border;
    private int primary;
    private int primary2;
    private int primarySoft;
    private int success;
    private int warning;
    private int danger;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusTicker = new Runnable() {
        @Override
        public void run() {
            refreshUi();
            uiHandler.postDelayed(this, 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadPalette();

        buildUi();
        startGatewayService(null);

        switchTab(
                getIntent() != null
                        && getIntent().getBooleanExtra("open_web", false)
                        ? TAB_WEB
                        : TAB_OVERVIEW);
        refreshUi();
        uiHandler.post(statusTicker);
    }

    private void loadPalette() {
        bg = getColor(R.color.og_bg);
        surface = getColor(R.color.og_surface);
        surfaceSoft = getColor(R.color.og_surface_soft);
        text = getColor(R.color.og_text);
        muted = getColor(R.color.og_muted);
        border = getColor(R.color.og_border);
        primary = getColor(R.color.og_primary);
        primary2 = getColor(R.color.og_primary_2);
        primarySoft = getColor(R.color.og_primary_soft);
        success = getColor(R.color.og_success);
        warning = getColor(R.color.og_warning);
        danger = getColor(R.color.og_danger);
    }

    private void buildUi() {
        LinearLayout root = UiKit.vertical(this);
        root.setBackgroundColor(bg);

        root.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UiKit.dp(this, 72)));

        pageHost = new FrameLayout(this);
        root.addView(pageHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        pages = new View[]{
                buildOverviewPage(),
                buildSessionsPage(),
                buildWebPage()
        };

        for (View page : pages) {
            pageHost.addView(page, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        root.addView(buildBottomNav(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UiKit.dp(this, 74)));

        setContentView(root);
    }

    private View buildTopBar() {
        LinearLayout bar = UiKit.horizontal(this);
        bar.setPadding(
                UiKit.dp(this, 18),
                UiKit.dp(this, 10),
                UiKit.dp(this, 12),
                UiKit.dp(this, 8));
        bar.setBackgroundColor(bg);

        FrameLayout logoBox = new FrameLayout(this);
        logoBox.setBackground(UiKit.gradient(
                primary,
                primary2,
                UiKit.dp(this, 12)));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_omnigate_mark);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams logoLp = new FrameLayout.LayoutParams(
                UiKit.dp(this, 27),
                UiKit.dp(this, 27),
                Gravity.CENTER);
        logoBox.addView(logo, logoLp);
        bar.addView(logoBox, new LinearLayout.LayoutParams(
                UiKit.dp(this, 40),
                UiKit.dp(this, 40)));

        LinearLayout titles = UiKit.vertical(this);
        LinearLayout.LayoutParams titlesLp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        titlesLp.leftMargin = UiKit.dp(this, 12);

        TextView brand = UiKit.text(this, "OmniGate", 22f, text, true);
        brand.setLetterSpacing(0.02f);
        titles.addView(brand, UiKit.matchWrap());

        topSubtitle = UiKit.text(
                this,
                "原 AI Gateway 增强伴侣",
                12.5f,
                muted,
                false);
        LinearLayout.LayoutParams subLp = UiKit.matchWrap();
        subLp.topMargin = UiKit.dp(this, 2);
        titles.addView(topSubtitle, subLp);

        bar.addView(titles, titlesLp);

        ImageView menu = new ImageView(this);
        menu.setImageResource(R.drawable.ic_menu);
        menu.setColorFilter(text, PorterDuff.Mode.SRC_IN);
        menu.setPadding(
                UiKit.dp(this, 9),
                UiKit.dp(this, 9),
                UiKit.dp(this, 9),
                UiKit.dp(this, 9));
        menu.setBackground(UiKit.rounded(surfaceSoft, UiKit.dp(this, 13)));
        menu.setOnClickListener(v -> showMenu());
        bar.addView(menu, new LinearLayout.LayoutParams(
                UiKit.dp(this, 42),
                UiKit.dp(this, 42)));

        return bar;
    }

    private View buildOverviewPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout content = UiKit.vertical(this);
        content.setPadding(
                UiKit.dp(this, 16),
                UiKit.dp(this, 6),
                UiKit.dp(this, 16),
                UiKit.dp(this, 30));
        scroll.addView(content);

        content.addView(buildHeroPanel(), cardLp(0));

        TextView credentials = UiKit.sectionTitle(
                this,
                "连接凭据",
                muted);
        LinearLayout.LayoutParams credentialsLp = UiKit.matchWrap();
        credentialsLp.topMargin = UiKit.dp(this, 22);
        credentialsLp.leftMargin = UiKit.dp(this, 4);
        content.addView(credentials, credentialsLp);
        content.addView(buildApiCard(), cardLp(8));

        TextView publicAccess = UiKit.sectionTitle(
                this,
                "公网访问",
                muted);
        LinearLayout.LayoutParams publicLp = UiKit.matchWrap();
        publicLp.topMargin = UiKit.dp(this, 22);
        publicLp.leftMargin = UiKit.dp(this, 4);
        content.addView(publicAccess, publicLp);
        content.addView(buildTunnelCard(), cardLp(8));

        TextView session = UiKit.sectionTitle(
                this,
                "当前会话",
                muted);
        LinearLayout.LayoutParams sessionLp = UiKit.matchWrap();
        sessionLp.topMargin = UiKit.dp(this, 22);
        sessionLp.leftMargin = UiKit.dp(this, 4);
        content.addView(session, sessionLp);
        content.addView(buildProviderSummaryCard(), cardLp(8));

        TextView controls = UiKit.sectionTitle(
                this,
                "快捷控制",
                muted);
        LinearLayout.LayoutParams controlsLp = UiKit.matchWrap();
        controlsLp.topMargin = UiKit.dp(this, 22);
        controlsLp.leftMargin = UiKit.dp(this, 4);
        content.addView(controls, controlsLp);
        content.addView(buildOverlayCard(), cardLp(8));

        return scroll;
    }

    private View buildHeroPanel() {
        LinearLayout hero = UiKit.vertical(this);
        hero.setPadding(
                UiKit.dp(this, 22),
                UiKit.dp(this, 22),
                UiKit.dp(this, 22),
                UiKit.dp(this, 22));
        hero.setBackground(UiKit.gradient(
                primary,
                primary2,
                UiKit.dp(this, 28)));
        hero.setElevation(UiKit.dp(this, 7));

        LinearLayout top = UiKit.horizontal(this);

        TextView live = UiKit.text(
                this,
                "●  ONLINE",
                12f,
                Color.WHITE,
                true);
        live.setGravity(Gravity.CENTER);
        live.setPadding(
                UiKit.dp(this, 11),
                UiKit.dp(this, 7),
                UiKit.dp(this, 11),
                UiKit.dp(this, 7));
        live.setBackground(UiKit.rounded(
                Color.argb(42, 255, 255, 255),
                UiKit.dp(this, 15)));
        top.addView(live);

        TextView port = UiKit.text(
                this,
                "PORT 8787",
                12f,
                Color.WHITE,
                true);
        port.setGravity(Gravity.CENTER);
        port.setPadding(
                UiKit.dp(this, 11),
                UiKit.dp(this, 7),
                UiKit.dp(this, 11),
                UiKit.dp(this, 7));
        port.setBackground(UiKit.rounded(
                Color.argb(32, 255, 255, 255),
                UiKit.dp(this, 15)));
        LinearLayout.LayoutParams portLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        portLp.leftMargin = UiKit.dp(this, 8);
        top.addView(port, portLp);

        hero.addView(top, UiKit.matchWrap());

        TextView title = UiKit.text(
                this,
                "OmniGate 已就绪",
                28f,
                Color.WHITE,
                true);
        title.setLetterSpacing(0.015f);
        LinearLayout.LayoutParams titleLp = UiKit.matchWrap();
        titleLp.topMargin = UiKit.dp(this, 18);
        hero.addView(title, titleLp);

        TextView subtitle = UiKit.text(
                this,
                "原软件负责网页与登录；OmniGate 只提供 8787、公网 Relay、Key 与状态诊断。",
                14f,
                Color.argb(224, 255, 255, 255),
                false);
        subtitle.setLineSpacing(UiKit.dp(this, 3), 1f);
        LinearLayout.LayoutParams subLp = UiKit.matchWrap();
        subLp.topMargin = UiKit.dp(this, 7);
        hero.addView(subtitle, subLp);

        LinearLayout footer = UiKit.horizontal(this);
        LinearLayout.LayoutParams footerLp = UiKit.matchWrap();
        footerLp.topMargin = UiKit.dp(this, 20);

        TextView provider = UiKit.text(
                this,
                "DeepSeek · ChatGPT · Grok · GLM",
                12.5f,
                Color.argb(225, 255, 255, 255),
                true);
        footer.addView(provider, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView open = UiKit.text(
                this,
                "打开原核心  ›",
                13.5f,
                Color.WHITE,
                true);
        open.setGravity(Gravity.CENTER);
        open.setPadding(
                UiKit.dp(this, 14),
                UiKit.dp(this, 9),
                UiKit.dp(this, 14),
                UiKit.dp(this, 9));
        open.setBackground(UiKit.rounded(
                Color.argb(38, 255, 255, 255),
                UiKit.dp(this, 17)));
        open.setOnClickListener(v -> openOriginalGateway());
        footer.addView(open);

        hero.addView(footer, footerLp);
        return hero;
    }

    private View buildStatusCard() {
        LinearLayout card = UiKit.card(this, surface, border);

        LinearLayout row = UiKit.horizontal(this);

        View dot = new View(this);
        dot.setBackground(UiKit.rounded(success, UiKit.dp(this, 8)));
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(
                UiKit.dp(this, 12),
                UiKit.dp(this, 12));
        dotLp.rightMargin = UiKit.dp(this, 12);
        row.addView(dot, dotLp);

        LinearLayout labels = UiKit.vertical(this);
        statusTitle = UiKit.text(
                this,
                "运行中 · 端口 " + GatewayApp.PORT,
                19f,
                success,
                true);
        labels.addView(statusTitle, UiKit.matchWrap());

        statusSubtitle = UiKit.text(
                this,
                "Companion 8787 → 原核心 1601",
                12.5f,
                muted,
                false);
        LinearLayout.LayoutParams ssLp = UiKit.matchWrap();
        ssLp.topMargin = UiKit.dp(this, 5);
        labels.addView(statusSubtitle, ssLp);

        row.addView(labels, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView open = UiKit.pill(
                this,
                "打开原核心",
                primary,
                primarySoft,
                primarySoft,
                true);
        open.setOnClickListener(v -> switchTab(TAB_WEB));
        row.addView(open);

        card.addView(row, UiKit.matchWrap());
        return card;
    }

    private View buildApiCard() {
        LinearLayout card = UiKit.card(this, surface, border);

        LinearLayout titleRow = UiKit.horizontal(this);
        TextView label = UiKit.text(
                this,
                "连接信息",
                19f,
                text,
                true);
        titleRow.addView(label, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        ImageView editKey = UiKit.iconButton(
                this,
                R.drawable.ic_edit,
                primary,
                primarySoft,
                10,
                "设置 API Key");
        editKey.setOnClickListener(v -> showApiKeyEditor());
        titleRow.addView(editKey, new LinearLayout.LayoutParams(
                UiKit.dp(this, 42),
                UiKit.dp(this, 42)));
        card.addView(titleRow, UiKit.matchWrap());

        endpointText = UiKit.text(
                this,
                "http://127.0.0.1:" + GatewayApp.PORT + "/v1",
                15.5f,
                text,
                false);
        endpointText.setTextIsSelectable(true);
        card.addView(
                buildCopyFieldRow(
                        "本机 Base URL",
                        endpointText,
                        () -> copySingleValue(
                                "本机 Base URL",
                                endpointText.getText().toString())),
                fieldLp(16));

        modelText = UiKit.text(
                this,
                GatewayApp.selectedProvider().model,
                15.5f,
                text,
                true);
        modelText.setTextIsSelectable(true);
        card.addView(
                buildCopyFieldRow(
                        "Model",
                        modelText,
                        () -> copySingleValue(
                                "Model",
                                modelText.getText().toString())),
                fieldLp(10));

        apiKeyText = UiKit.text(
                this,
                maskedApiKey(),
                15f,
                text,
                false);
        apiKeyText.setTextIsSelectable(true);

        LinearLayout apiRow = buildCopyFieldRow(
                "API Key",
                apiKeyText,
                () -> copySingleValue(
                        "API Key",
                        GatewayApp.apiKey()));

        ImageView toggle = UiKit.iconButton(
                this,
                R.drawable.ic_eye,
                muted,
                surface,
                10,
                "显示或隐藏 API Key");
        toggle.setOnClickListener(v -> {
            apiKeyVisible = !apiKeyVisible;
            apiKeyText.setText(
                    apiKeyVisible ? GatewayApp.apiKey() : maskedApiKey());
            toggle.setColorFilter(apiKeyVisible ? primary : muted);
        });
        LinearLayout.LayoutParams toggleLp = new LinearLayout.LayoutParams(
                UiKit.dp(this, 40),
                UiKit.dp(this, 40));
        toggleLp.leftMargin = UiKit.dp(this, 6);
        apiRow.addView(toggle, toggleLp);

        card.addView(apiRow, fieldLp(10));

        TextView note = UiKit.text(
                this,
                "所有 /v1 请求都需要填写 API Key。本机 127.0.0.1 接受任意非空 Key；公网必须使用 OmniGate 的真实 Key。",
                12.5f,
                muted,
                false);
        note.setLineSpacing(UiKit.dp(this, 3), 1f);
        LinearLayout.LayoutParams noteLp = UiKit.matchWrap();
        noteLp.topMargin = UiKit.dp(this, 14);
        card.addView(note, noteLp);

        return card;
    }

    private LinearLayout buildCopyFieldRow(
            String label,
            TextView valueView,
            Runnable copyAction) {
        LinearLayout row = UiKit.horizontal(this);
        row.setPadding(
                UiKit.dp(this, 12),
                UiKit.dp(this, 11),
                UiKit.dp(this, 8),
                UiKit.dp(this, 11));
        row.setBackground(UiKit.rounded(
                surfaceSoft,
                UiKit.dp(this, 16)));

        LinearLayout values = UiKit.vertical(this);
        TextView labelView = UiKit.text(
                this,
                label,
                11.5f,
                muted,
                true);
        values.addView(labelView, UiKit.matchWrap());

        LinearLayout.LayoutParams valueLp = UiKit.matchWrap();
        valueLp.topMargin = UiKit.dp(this, 4);
        values.addView(valueView, valueLp);

        row.addView(values, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        ImageView copy = UiKit.iconButton(
                this,
                R.drawable.ic_copy,
                primary,
                surface,
                10,
                "复制 " + label);
        copy.setOnClickListener(v -> copyAction.run());
        row.addView(copy, new LinearLayout.LayoutParams(
                UiKit.dp(this, 40),
                UiKit.dp(this, 40)));

        return row;
    }

    private LinearLayout.LayoutParams fieldLp(int topMarginDp) {
        LinearLayout.LayoutParams lp = UiKit.matchWrap();
        lp.topMargin = UiKit.dp(this, topMarginDp);
        return lp;
    }

    private String maskedApiKey() {
        String key = GatewayApp.apiKey();
        if (key.length() <= 8) return "••••••••";
        return key.substring(0, Math.min(4, key.length()))
                + "••••••••••••"
                + key.substring(Math.max(4, key.length() - 4));
    }

    private void copySingleValue(String label, String value) {
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

    private void showApiKeyEditor() {
        LinearLayout box = UiKit.vertical(this);
        int pad = UiKit.dp(this, 24);
        box.setPadding(pad, UiKit.dp(this, 8), pad, 0);

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("至少 12 个字符");
        input.setText(GatewayApp.apiKey());
        input.setSelectAllOnFocus(true);
        input.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(input, UiKit.matchWrap());

        new AlertDialog.Builder(this)
                .setTitle("自定义 API Key")
                .setMessage(
                        "公网必须使用这个真实 Key；本机 127.0.0.1 只要求客户端填写任意非空 Key。")
                .setView(box)
                .setNeutralButton("重新随机生成", (dialog, which) -> {
                    GatewayApp.resetApiKey();
                    apiKeyVisible = false;
                    refreshUi();
                    Toast.makeText(
                            this,
                            "已生成新的随机 API Key",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    try {
                        GatewayApp.setApiKey(
                                input.getText().toString());
                        apiKeyVisible = false;
                        refreshUi();
                        Toast.makeText(
                                this,
                                "API Key 已更新",
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

    private View buildTunnelCard() {
        LinearLayout card = UiKit.card(this, surface, border);

        LinearLayout head = UiKit.horizontal(this);

        FrameLayout cloudBox = new FrameLayout(this);
        cloudBox.setBackground(UiKit.rounded(
                primarySoft,
                UiKit.dp(this, 16)));
        ImageView cloud = new ImageView(this);
        cloud.setImageResource(R.drawable.ic_cloud);
        cloud.setColorFilter(primary);
        cloud.setPadding(
                UiKit.dp(this, 10),
                UiKit.dp(this, 10),
                UiKit.dp(this, 10),
                UiKit.dp(this, 10));
        cloudBox.addView(cloud, new FrameLayout.LayoutParams(
                UiKit.dp(this, 42),
                UiKit.dp(this, 42),
                Gravity.CENTER));
        head.addView(cloudBox, new LinearLayout.LayoutParams(
                UiKit.dp(this, 46),
                UiKit.dp(this, 46)));

        LinearLayout labels = UiKit.vertical(this);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        labelsLp.leftMargin = UiKit.dp(this, 12);

        TextView title = UiKit.text(
                this,
                "公网穿透",
                20f,
                text,
                true);
        labels.addView(title, UiKit.matchWrap());

        tunnelStateText = UiKit.text(
                this,
                "未开启",
                12.5f,
                muted,
                false);
        LinearLayout.LayoutParams tsLp = UiKit.matchWrap();
        tsLp.topMargin = UiKit.dp(this, 4);
        labels.addView(tunnelStateText, tsLp);

        head.addView(labels, labelsLp);

        tunnelSwitch = new Switch(this);
        tunnelSwitch.setShowText(false);
        tunnelSwitch.setOnCheckedChangeListener(this::onTunnelSwitchChanged);
        head.addView(tunnelSwitch);

        card.addView(head, UiKit.matchWrap());

        LinearLayout backendRow = UiKit.horizontal(this);
        LinearLayout.LayoutParams backendLp = UiKit.matchWrap();
        backendLp.topMargin = UiKit.dp(this, 16);

        tunnelBackendText = UiKit.text(
                this,
                "own".equals(GatewayApp.tunnelBackend())
                        ? "自有 Cloudflare Relay"
                        : "Legacy 兼容隧道",
                12f,
                "own".equals(GatewayApp.tunnelBackend()) ? primary : warning,
                true);
        tunnelBackendText.setGravity(Gravity.CENTER);
        tunnelBackendText.setPadding(
                UiKit.dp(this, 11),
                UiKit.dp(this, 7),
                UiKit.dp(this, 11),
                UiKit.dp(this, 7));
        tunnelBackendText.setBackground(UiKit.rounded(
                "own".equals(GatewayApp.tunnelBackend())
                        ? primarySoft
                        : UiKit.withAlpha(warning, 30),
                UiKit.dp(this, 15)));
        backendRow.addView(tunnelBackendText);

        TextView secure = UiKit.text(
                this,
                "  TLS · API Key 鉴权",
                12f,
                muted,
                false);
        backendRow.addView(secure);
        card.addView(backendRow, backendLp);

        publicUrlText = UiKit.text(
                this,
                GatewayApp.generatedPublicUrl() + "/v1",
                15f,
                primary2,
                false);
        publicUrlText.setTextIsSelectable(true);
        publicUrlText.setLineSpacing(UiKit.dp(this, 2), 1f);
        card.addView(
                buildCopyFieldRow(
                        "公网 Base URL",
                        publicUrlText,
                        () -> copySingleValue(
                                "公网 Base URL",
                                publicUrlText.getText().toString())),
                fieldLp(14));

        LinearLayout route = UiKit.vertical(this);
        route.setPadding(
                UiKit.dp(this, 12),
                UiKit.dp(this, 11),
                UiKit.dp(this, 12),
                UiKit.dp(this, 11));
        route.setBackground(UiKit.rounded(
                surfaceSoft,
                UiKit.dp(this, 16)));
        route.addView(buildRouteLine(
                "①",
                "Cloudflare Edge",
                "接收公网请求"));
        route.addView(buildRouteLine(
                "②",
                "OmniGate Relay",
                "WebSocket 回传手机"));
        route.addView(buildRouteLine(
                "③",
                "127.0.0.1:8787",
                "本机 API 与网页会话"));
        LinearLayout.LayoutParams routeLp = UiKit.matchWrap();
        routeLp.topMargin = UiKit.dp(this, 10);
        card.addView(route, routeLp);

        TextView hint = UiKit.text(
                this,
                "默认使用 gw-33e850180248.claua.de；旧 deepseeks.dpdns.org 仅保留为 Legacy 兼容模式。",
                12.5f,
                muted,
                false);
        hint.setLineSpacing(UiKit.dp(this, 3), 1f);
        LinearLayout.LayoutParams hintLp = UiKit.matchWrap();
        hintLp.topMargin = UiKit.dp(this, 11);
        card.addView(hint, hintLp);

        LinearLayout actions = UiKit.horizontal(this);
        LinearLayout.LayoutParams actionsLp = UiKit.matchWrap();
        actionsLp.topMargin = UiKit.dp(this, 14);

        TextView configure = UiKit.pill(
                this,
                "配置 Relay",
                Color.WHITE,
                primary,
                primary,
                true);
        configure.setOnClickListener(v -> showTunnelConfigDialog());
        actions.addView(configure, UiKit.weighted(1f));

        TextView importOld = UiKit.pill(
                this,
                "Legacy",
                text,
                surfaceSoft,
                border,
                false);
        LinearLayout.LayoutParams importLp = UiKit.weighted(0.72f);
        importLp.leftMargin = UiKit.dp(this, 10);
        importOld.setLayoutParams(importLp);
        importOld.setOnClickListener(v -> showImportTunnelDialog());
        actions.addView(importOld);

        card.addView(actions, actionsLp);
        return card;
    }

    private View buildRouteLine(
            String index,
            String title,
            String subtitle) {
        LinearLayout row = UiKit.horizontal(this);
        row.setPadding(
                0,
                UiKit.dp(this, 4),
                0,
                UiKit.dp(this, 4));

        TextView number = UiKit.text(
                this,
                index,
                12.5f,
                primary,
                true);
        number.setGravity(Gravity.CENTER);
        number.setBackground(UiKit.rounded(
                primarySoft,
                UiKit.dp(this, 13)));
        row.addView(number, new LinearLayout.LayoutParams(
                UiKit.dp(this, 28),
                UiKit.dp(this, 28)));

        LinearLayout labels = UiKit.vertical(this);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        labelsLp.leftMargin = UiKit.dp(this, 10);

        TextView titleView = UiKit.text(
                this,
                title,
                13.5f,
                text,
                true);
        labels.addView(titleView, UiKit.matchWrap());

        TextView sub = UiKit.text(
                this,
                subtitle,
                11.5f,
                muted,
                false);
        LinearLayout.LayoutParams subLp = UiKit.matchWrap();
        subLp.topMargin = UiKit.dp(this, 2);
        labels.addView(sub, subLp);

        row.addView(labels, labelsLp);

        ImageView check = new ImageView(this);
        check.setImageResource(R.drawable.ic_check);
        check.setColorFilter(success);
        check.setPadding(
                UiKit.dp(this, 4),
                UiKit.dp(this, 4),
                UiKit.dp(this, 4),
                UiKit.dp(this, 4));
        row.addView(check, new LinearLayout.LayoutParams(
                UiKit.dp(this, 25),
                UiKit.dp(this, 25)));
        return row;
    }

    private View buildProviderSummaryCard() {
        LinearLayout card = UiKit.card(this, surface, border);

        LinearLayout row = UiKit.horizontal(this);
        TextView badge = providerBadge(GatewayApp.selectedProvider(), 48);
        row.addView(badge, new LinearLayout.LayoutParams(
                UiKit.dp(this, 48),
                UiKit.dp(this, 48)));

        LinearLayout labels = UiKit.vertical(this);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        labelsLp.leftMargin = UiKit.dp(this, 14);

        currentProviderTitle = UiKit.text(
                this,
                GatewayApp.selectedProvider().displayName,
                18f,
                text,
                true);
        labels.addView(currentProviderTitle, UiKit.matchWrap());

        currentProviderDesc = UiKit.text(
                this,
                GatewayApp.selectedProvider().description,
                12.5f,
                muted,
                false);
        LinearLayout.LayoutParams descLp = UiKit.matchWrap();
        descLp.topMargin = UiKit.dp(this, 5);
        labels.addView(currentProviderDesc, descLp);

        row.addView(labels, labelsLp);

        TextView change = UiKit.pill(
                this,
                "切换",
                primary,
                primarySoft,
                primarySoft,
                true);
        change.setOnClickListener(v -> switchTab(TAB_SESSIONS));
        row.addView(change);

        card.addView(row, UiKit.matchWrap());

        TextView continuity = UiKit.text(
                this,
                "自动续接：默认开启 · 对话达到上限时自动新建并继续 · 最多 3 次",
                12f,
                success,
                true);
        continuity.setLineSpacing(UiKit.dp(this, 2), 1f);
        continuity.setPadding(
                UiKit.dp(this, 11),
                UiKit.dp(this, 9),
                UiKit.dp(this, 11),
                UiKit.dp(this, 9));
        continuity.setBackground(UiKit.rounded(
                UiKit.withAlpha(success, 22),
                UiKit.dp(this, 14)));
        LinearLayout.LayoutParams continuityLp = UiKit.matchWrap();
        continuityLp.topMargin = UiKit.dp(this, 14);
        card.addView(continuity, continuityLp);

        return card;
    }

    private View buildOverlayCard() {
        LinearLayout card = UiKit.card(this, surface, border);

        LinearLayout row = UiKit.horizontal(this);
        LinearLayout labels = UiKit.vertical(this);

        TextView title = UiKit.text(
                this,
                "小悬浮窗",
                18f,
                text,
                true);
        labels.addView(title, UiKit.matchWrap());

        TextView desc = UiKit.text(
                this,
                "44dp 紧凑悬浮球 · 文字/图片/文件直发 · 可拖动 · 长按隐藏",
                12.5f,
                muted,
                false);
        LinearLayout.LayoutParams descLp = UiKit.matchWrap();
        descLp.topMargin = UiKit.dp(this, 5);
        labels.addView(desc, descLp);

        row.addView(labels, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView show = UiKit.pill(
                this,
                "显示",
                primary,
                primarySoft,
                primarySoft,
                true);
        show.setOnClickListener(v -> requestOrShowOverlay());
        row.addView(show);

        TextView hide = UiKit.pill(
                this,
                "隐藏",
                text,
                surfaceSoft,
                border,
                false);
        LinearLayout.LayoutParams hideLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hideLp.leftMargin = UiKit.dp(this, 8);
        hide.setLayoutParams(hideLp);
        hide.setOnClickListener(v -> hideOverlay());
        row.addView(hide);

        card.addView(row, UiKit.matchWrap());
        return card;
    }

    private View buildSessionsPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout content = UiKit.vertical(this);
        content.setPadding(
                UiKit.dp(this, 18),
                UiKit.dp(this, 8),
                UiKit.dp(this, 18),
                UiKit.dp(this, 28));
        scroll.addView(content);

        TextView title = UiKit.text(
                this,
                "选择 AI 服务商",
                27f,
                text,
                true);
        content.addView(title, UiKit.matchWrap());

        TextView subtitle = UiKit.text(
                this,
                "模型名保持兼容；真正网页操作由原 AI Gateway 执行",
                14f,
                muted,
                false);
        LinearLayout.LayoutParams subLp = UiKit.matchWrap();
        subLp.topMargin = UiKit.dp(this, 8);
        content.addView(subtitle, subLp);

        LinearLayout info = UiKit.card(this, surfaceSoft, border);
        TextView infoText = UiKit.text(
                this,
                "OmniGate Companion 不再创建第二套 WebView。DeepSeek、ChatGPT、Grok、GLM 的登录态、输入、上传、悬浮小窗和网页行为全部由原 AI Gateway 保留。",
                13.5f,
                muted,
                false);
        infoText.setLineSpacing(UiKit.dp(this, 4), 1f);
        info.addView(infoText, UiKit.matchWrap());
        LinearLayout.LayoutParams infoLp = cardLp(18);
        infoLp.bottomMargin = UiKit.dp(this, 6);
        content.addView(info, infoLp);

        for (AiProvider provider : AiProvider.values()) {
            content.addView(buildProviderCard(provider), cardLp(12));
        }

        return scroll;
    }

    private View buildProviderCard(AiProvider provider) {
        boolean selected = provider == GatewayApp.selectedProvider();
        LinearLayout card = UiKit.card(
                this,
                selected ? primarySoft : surface,
                selected ? UiKit.withAlpha(primary, 95) : border);
        card.setOnClickListener(v -> selectProvider(provider, true));

        LinearLayout row = UiKit.horizontal(this);

        TextView badge = providerBadge(provider, 58);
        row.addView(badge, new LinearLayout.LayoutParams(
                UiKit.dp(this, 58),
                UiKit.dp(this, 58)));

        LinearLayout labels = UiKit.vertical(this);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f);
        labelsLp.leftMargin = UiKit.dp(this, 16);

        LinearLayout nameRow = UiKit.horizontal(this);
        TextView name = UiKit.text(
                this,
                provider.displayName,
                20f,
                text,
                true);
        nameRow.addView(name);

        if (selected) {
            TextView current = UiKit.text(
                    this,
                    "当前",
                    10.5f,
                    primary,
                    true);
            current.setGravity(Gravity.CENTER);
            current.setPadding(
                    UiKit.dp(this, 8),
                    UiKit.dp(this, 4),
                    UiKit.dp(this, 8),
                    UiKit.dp(this, 4));
            current.setBackground(UiKit.rounded(
                    surface,
                    UiKit.dp(this, 12)));
            LinearLayout.LayoutParams currentLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            currentLp.leftMargin = UiKit.dp(this, 8);
            nameRow.addView(current, currentLp);
        }
        labels.addView(nameRow, UiKit.matchWrap());

        TextView desc = UiKit.text(
                this,
                provider.description,
                13f,
                muted,
                false);
        desc.setLineSpacing(UiKit.dp(this, 3), 1f);
        LinearLayout.LayoutParams descLp = UiKit.matchWrap();
        descLp.topMargin = UiKit.dp(this, 6);
        labels.addView(desc, descLp);

        TextView model = UiKit.text(
                this,
                provider.model,
                12f,
                primary2,
                true);
        LinearLayout.LayoutParams modelLp = UiKit.matchWrap();
        modelLp.topMargin = UiKit.dp(this, 9);
        labels.addView(model, modelLp);

        row.addView(labels, labelsLp);

        ImageView action = UiKit.iconButton(
                this,
                selected ? R.drawable.ic_check : R.drawable.ic_nav_web,
                selected ? success : primary,
                selected ? surface : primarySoft,
                10,
                selected ? "当前服务商" : "打开服务商");
        action.setOnClickListener(v -> selectProvider(provider, true));
        row.addView(action, new LinearLayout.LayoutParams(
                UiKit.dp(this, 42),
                UiKit.dp(this, 42)));

        card.addView(row, UiKit.matchWrap());
        return card;
    }

    private View buildWebPage() {
        LinearLayout page = UiKit.vertical(this);
        page.setPadding(
                UiKit.dp(this, 18),
                UiKit.dp(this, 18),
                UiKit.dp(this, 18),
                UiKit.dp(this, 28));
        page.setBackgroundColor(bg);

        TextView title = UiKit.text(
                this,
                "原 AI Gateway",
                27f,
                text,
                true);
        page.addView(title, UiKit.matchWrap());

        TextView subtitle = UiKit.text(
                this,
                "冻结原网页核心 · com.deepseek2.app · 127.0.0.1:1601",
                13.5f,
                muted,
                false);
        LinearLayout.LayoutParams subtitleLp = UiKit.matchWrap();
        subtitleLp.topMargin = UiKit.dp(this, 7);
        page.addView(subtitle, subtitleLp);

        LinearLayout card = UiKit.card(this, surface, border);
        LinearLayout.LayoutParams cardParams = cardLp(20);
        page.addView(card, cardParams);

        TextView mode = UiKit.text(
                this,
                "OmniGate Companion 不接管网页",
                19f,
                primary,
                true);
        card.addView(mode, UiKit.matchWrap());

        TextView detail = UiKit.text(
                this,
                "原软件继续负责：登录 Cookie、GeckoView、文字输入、图片/文件上传、四服务商切换、原悬浮小窗口和网页会话。\n\n"
                        + "Companion 只负责：8787 兼容入口、任意非空本机 Key、严格公网 Key、claua.de Relay、自定义公网地址和连接诊断。",
                13.5f,
                muted,
                false);
        detail.setLineSpacing(UiKit.dp(this, 4), 1f);
        LinearLayout.LayoutParams detailLp = UiKit.matchWrap();
        detailLp.topMargin = UiKit.dp(this, 12);
        card.addView(detail, detailLp);

        TextView status = UiKit.text(
                this,
                OriginalGatewayProxy.probe()
                        ? "● 原核心 1601 已连接"
                        : "○ 原核心 1601 未连接，请先启动原 AI Gateway",
                14f,
                OriginalGatewayProxy.probe() ? success : warning,
                true);
        LinearLayout.LayoutParams statusLp = UiKit.matchWrap();
        statusLp.topMargin = UiKit.dp(this, 16);
        card.addView(status, statusLp);

        TextView open = UiKit.pill(
                this,
                "打开原 AI Gateway",
                Color.WHITE,
                primary,
                primary,
                true);
        open.setOnClickListener(v -> openOriginalGateway());
        LinearLayout.LayoutParams openLp = UiKit.matchWrap();
        openLp.topMargin = UiKit.dp(this, 16);
        card.addView(open, openLp);

        return page;
    }

    private View buildBottomNav() {
        LinearLayout nav = UiKit.horizontal(this);
        nav.setPadding(
                UiKit.dp(this, 10),
                UiKit.dp(this, 6),
                UiKit.dp(this, 10),
                UiKit.dp(this, 8));
        nav.setBackgroundColor(surface);
        nav.setElevation(UiKit.dp(this, 12));

        navItems = new LinearLayout[3];
        navIcons = new ImageView[3];
        navLabels = new TextView[3];

        nav.addView(createNavItem(
                TAB_OVERVIEW,
                R.drawable.ic_nav_overview,
                "概览"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        nav.addView(createNavItem(
                TAB_SESSIONS,
                R.drawable.ic_nav_sessions,
                "会话"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        nav.addView(createNavItem(
                TAB_WEB,
                R.drawable.ic_nav_web,
                "原核心"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        return nav;
    }

    private LinearLayout createNavItem(
            int index,
            int iconRes,
            String label) {
        LinearLayout item = UiKit.vertical(this);
        item.setGravity(Gravity.CENTER);
        item.setPadding(
                UiKit.dp(this, 8),
                UiKit.dp(this, 5),
                UiKit.dp(this, 8),
                UiKit.dp(this, 5));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(muted, PorterDuff.Mode.SRC_IN);
        item.addView(icon, new LinearLayout.LayoutParams(
                UiKit.dp(this, 24),
                UiKit.dp(this, 24)));

        TextView textView = UiKit.text(
                this,
                label,
                12.5f,
                muted,
                true);
        textView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelLp = UiKit.matchWrap();
        labelLp.topMargin = UiKit.dp(this, 3);
        item.addView(textView, labelLp);

        item.setOnClickListener(v -> {
            switchTab(index);
            if (index == TAB_WEB) openOriginalGateway();
        });

        navItems[index] = item;
        navIcons[index] = icon;
        navLabels[index] = textView;
        return item;
    }

    private void switchTab(int index) {
        activeTab = index;
        for (int i = 0; i < pages.length; i++) {
            pages[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
            boolean selected = i == index;
            navIcons[i].setColorFilter(
                    selected ? primary : muted,
                    PorterDuff.Mode.SRC_IN);
            navLabels[i].setTextColor(selected ? text : muted);
            navItems[i].setBackground(selected
                    ? UiKit.rounded(primarySoft, UiKit.dp(this, 18))
                    : null);
        }
    }

    private TextView providerBadge(AiProvider provider, int sizeDp) {
        String label;
        int color;

        switch (provider) {
            case CHATGPT:
                label = "G";
                color = Color.rgb(16, 163, 127);
                break;
            case GROK:
                label = "X";
                color = Color.rgb(28, 29, 33);
                break;
            case GLM:
                label = "智";
                color = Color.rgb(42, 99, 235);
                break;
            case DEEPSEEK:
            default:
                label = "D";
                color = Color.rgb(19, 103, 245);
                break;
        }

        TextView badge = UiKit.text(
                this,
                label,
                sizeDp >= 54 ? 24f : 20f,
                Color.WHITE,
                true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(UiKit.rounded(
                color,
                UiKit.dp(this, sizeDp / 2f)));
        return badge;
    }

    private LinearLayout.LayoutParams cardLp(int topMarginDp) {
        LinearLayout.LayoutParams lp = UiKit.matchWrap();
        lp.topMargin = UiKit.dp(this, topMarginDp);
        return lp;
    }

    private void selectProvider(AiProvider provider, boolean openWeb) {
        GatewayApp.setSelectedProvider(provider);
        refreshOverlayLabel();
        refreshUi();
        if (openWeb) openOriginalGateway();
    }

    private void refreshUi() {
        AiProvider provider = GatewayApp.selectedProvider();

        if (topSubtitle != null) {
            topSubtitle.setText(provider.displayName + " · " + provider.model);
        }
        if (statusTitle != null) {
            statusTitle.setText("运行中 · 端口 " + GatewayApp.PORT);
        }
        if (statusSubtitle != null) {
            statusSubtitle.setText(
                    "当前服务商 " + provider.displayName
                            + (GatewayApp.tunnelEnabled()
                            ? " · 公网穿透 " + GatewayApp.tunnelStatus()
                            : " · 仅本机访问"));
        }
        if (endpointText != null) {
            endpointText.setText(
                    "http://127.0.0.1:" + GatewayApp.PORT + "/v1");
        }
        if (modelText != null) {
            modelText.setText(provider.model);
        }
        if (apiKeyText != null) {
            apiKeyText.setText(
                    apiKeyVisible ? GatewayApp.apiKey() : maskedApiKey());
        }
        if (currentProviderTitle != null) {
            currentProviderTitle.setText(provider.displayName);
        }
        if (currentProviderDesc != null) {
            currentProviderDesc.setText(provider.description);
        }
        if (webProviderTitle != null) {
            webProviderTitle.setText(
                    provider.displayName + " · " + provider.model);
        }

        updatingTunnelSwitch = true;
        if (tunnelSwitch != null) {
            tunnelSwitch.setChecked(GatewayApp.tunnelEnabled());
        }
        updatingTunnelSwitch = false;

        if (tunnelStateText != null) {
            tunnelStateText.setText(
                    GatewayApp.tunnelEnabled()
                            ? GatewayApp.tunnelStatus()
                            : "未开启");
            tunnelStateText.setTextColor(
                    GatewayApp.tunnelEnabled() ? success : muted);
        }
        if (tunnelBackendText != null) {
            boolean own = "own".equals(GatewayApp.tunnelBackend());
            tunnelBackendText.setText(
                    own ? "自有 Cloudflare Relay" : "Legacy 兼容隧道");
            tunnelBackendText.setTextColor(own ? primary : warning);
            tunnelBackendText.setBackground(UiKit.rounded(
                    own ? primarySoft : UiKit.withAlpha(warning, 30),
                    UiKit.dp(this, 15)));
        }
        if (publicUrlText != null) {
            publicUrlText.setText(
                    (GatewayApp.tunnelEnabled()
                            ? GatewayApp.effectivePublicUrl()
                            : GatewayApp.generatedPublicUrl())
                            + "/v1");
        }
    }

    private void onTunnelSwitchChanged(
            CompoundButton button,
            boolean enabled) {
        if (updatingTunnelSwitch) return;
        GatewayApp.setTunnelEnabled(enabled);
        startGatewayService(GatewayService.ACTION_RESTART_TUNNEL);
        refreshUi();
        Toast.makeText(
                this,
                enabled ? "公网穿透正在连接" : "公网穿透已关闭",
                Toast.LENGTH_SHORT).show();
    }

    private void showMenu() {
        String[] items = new String[]{
                "显示小悬浮窗",
                "隐藏悬浮窗",
                "复制连接信息",
                "公网与自定义域名",
                "导入旧穿透参数",
                "关于 OmniGate"
        };

        new AlertDialog.Builder(this)
                .setTitle("OmniGate")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            requestOrShowOverlay();
                            break;
                        case 1:
                            hideOverlay();
                            break;
                        case 2:
                            copyConnectionInfo();
                            break;
                        case 3:
                            showTunnelConfigDialog();
                            break;
                        case 4:
                            showImportTunnelDialog();
                            break;
                        default:
                            showAbout();
                            break;
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAbout() {
        String message =
                "OmniGate Companion 1.0\n\n"
                        + "原 AI Gateway 的旁路增强层\n"
                        + "DeepSeek · ChatGPT · Grok · GLM 保留原实现\n\n"
                        + "Companion API：" + GatewayApp.PORT
                        + "\n原核心 API：1601"
                        + "\n默认公网：" + GatewayApp.OWN_PUBLIC_BASE
                        + "\nLegacy：deepseeks.dpdns.org 兼容模式"
                        + "\n支持自定义 HTTPS 域名、附件、长输出、自动换会话与工具调用兼容。";

        new AlertDialog.Builder(this)
                .setTitle("关于 OmniGate")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
    }

    private void openOriginalGateway() {
        try {
            Intent launch = getPackageManager()
                    .getLaunchIntentForPackage("com.deepseek2.app");
            if (launch == null) {
                Toast.makeText(
                        this,
                        "未检测到原 AI Gateway（com.deepseek2.app）",
                        Toast.LENGTH_LONG).show();
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(launch);
        } catch (Throwable e) {
            Toast.makeText(
                    this,
                    "无法打开原 AI Gateway：" + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void requestOrShowOverlay() {
        showOverlay();
    }

    private void showOverlay() {
        try {
            Intent intent = new Intent(
                    "com.deepseek2.app.action.SHOW_OVERLAY");
            intent.setClassName(
                    "com.deepseek2.app",
                    "com.deepseek2.app.ui.OverlayService");
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            Toast.makeText(
                    this,
                    "已请求显示原 AI Gateway 悬浮小窗口",
                    Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            openOriginalGateway();
            Toast.makeText(
                    this,
                    "无法直接调用原悬浮服务，请在原 AI Gateway 中开启悬浮窗",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void hideOverlay() {
        try {
            Intent intent = new Intent(
                    "com.deepseek2.app.action.RETURN_FROM_OVERLAY");
            intent.setClassName(
                    "com.deepseek2.app",
                    "com.deepseek2.app.ui.OverlayService");
            startService(intent);
        } catch (Throwable ignored) {
            openOriginalGateway();
        }
    }

    private void refreshOverlayLabel() {
        // Original AI Gateway owns its overlay label and provider UI.
    }

    private void showTunnelConfigDialog() {
        LinearLayout box = UiKit.vertical(this);
        int pad = UiKit.dp(this, 24);
        box.setPadding(pad, UiKit.dp(this, 8), pad, 0);

        TextView defaultUrl = UiKit.text(
                this,
                "OmniGate 默认公网：\n" + GatewayApp.OWN_PUBLIC_BASE + "/v1",
                13.5f,
                primary2,
                true);
        defaultUrl.setTextIsSelectable(true);
        box.addView(defaultUrl, UiKit.matchWrap());

        EditText relayKey = new EditText(this);
        relayKey.setSingleLine(true);
        relayKey.setHint("Relay Key（手机 ↔ Cloudflare 配对密钥）");
        relayKey.setText(GatewayApp.relayKey());
        relayKey.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams keyLp = UiKit.matchWrap();
        keyLp.topMargin = UiKit.dp(this, 14);
        box.addView(relayKey, keyLp);

        EditText custom = new EditText(this);
        custom.setSingleLine(true);
        custom.setHint("可选：你以后自己的其他 https:// 域名");
        custom.setText(GatewayApp.customPublicUrl());
        custom.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams customLp = UiKit.matchWrap();
        customLp.topMargin = UiKit.dp(this, 10);
        box.addView(custom, customLp);

        new AlertDialog.Builder(this)
                .setTitle("OmniGate 自有公网穿透")
                .setMessage(
                        "默认使用 gw-33e850180248.claua.de。Relay Key 只用于手机和 Cloudflare Relay 配对；API Key 仍单独控制 /v1 访问。")
                .setView(box)
                .setNeutralButton("Legacy 旧穿透", (dialog, which) ->
                        showImportTunnelDialog())
                .setNegativeButton("取消", null)
                .setPositiveButton("保存并重连", (dialog, which) -> {
                    try {
                        GatewayApp.setTunnelBackend("own");
                        GatewayApp.setRelayKey(
                                relayKey.getText().toString());

                        String rawCustom = custom.getText().toString().trim();
                        if (rawCustom.isEmpty()) {
                            GatewayApp.setCustomPublicUrl("", false);
                        } else {
                            String candidate = normalizeBaseUrl(rawCustom);
                            if (candidate == null) {
                                throw new IllegalArgumentException(
                                        "自定义网址必须是有效的 https:// 地址");
                            }
                            GatewayApp.setCustomPublicUrl(candidate, false);
                        }

                        GatewayApp.setTunnelEnabled(true);
                        startGatewayService(
                                GatewayService.ACTION_RESTART_TUNNEL);
                        refreshUi();

                        Toast.makeText(
                                this,
                                "正在连接 OmniGate Cloudflare Relay",
                                Toast.LENGTH_LONG).show();

                        if (!rawCustom.isEmpty()) {
                            String candidate = normalizeBaseUrl(rawCustom);
                            new Thread(() -> {
                                boolean ok = probe(candidate);
                                runOnUiThread(() -> {
                                    GatewayApp.setCustomPublicUrl(candidate, ok);
                                    refreshUi();
                                    Toast.makeText(
                                            this,
                                            ok
                                                    ? "自定义网址验证通过"
                                                    : "自定义网址尚未转发到当前 Relay，继续使用 claua.de 默认地址",
                                            Toast.LENGTH_LONG).show();
                                });
                            }, "custom-url-probe").start();
                        }
                    } catch (Exception ex) {
                        Toast.makeText(
                                this,
                                ex.getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void showImportTunnelDialog() {
        LinearLayout box = UiKit.vertical(this);
        int pad = UiKit.dp(this, 24);
        box.setPadding(pad, UiKit.dp(this, 8), pad, 0);

        EditText prefix = new EditText(this);
        prefix.setHint("旧 prefix，例如 gw-33e850180248");
        prefix.setSingleLine(true);
        box.addView(prefix);

        EditText token = new EditText(this);
        token.setHint("旧 tunnel_token");
        token.setSingleLine(true);
        token.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout.LayoutParams tokenLp = UiKit.matchWrap();
        tokenLp.topMargin = UiKit.dp(this, 8);
        box.addView(token, tokenLp);

        new AlertDialog.Builder(this)
                .setTitle("导入旧公网穿透身份")
                .setMessage(
                        "只有旧 prefix 与匹配的 tunnel_token 同时正确，才能尝试恢复原来的公网子域名。")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("导入并重连", (dialog, which) -> {
                    try {
                        GatewayApp.setTunnelIdentity(
                                prefix.getText().toString().trim(),
                                token.getText().toString().trim());
                        GatewayApp.setTunnelBackend("legacy");
                        GatewayApp.setTunnelEnabled(true);
                        startGatewayService(
                                GatewayService.ACTION_RESTART_TUNNEL);
                        refreshUi();
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

    private void copyConnectionInfo() {
        AiProvider provider = GatewayApp.selectedProvider();

        StringBuilder value = new StringBuilder();
        value.append("Local Base URL: http://127.0.0.1:")
                .append(GatewayApp.PORT)
                .append("/v1\n");

        if (GatewayApp.tunnelEnabled()) {
            value.append("Public Base URL: ")
                    .append(GatewayApp.effectivePublicUrl())
                    .append("/v1\n");
        }

        value.append("Provider: ")
                .append(provider.displayName)
                .append("\nModel: ")
                .append(provider.model)
                .append("\nAPI Key: ")
                .append(GatewayApp.apiKey());

        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(
                        Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(
                ClipData.newPlainText(
                        "OmniGate connection",
                        value.toString()));

        Toast.makeText(
                this,
                "连接信息已复制",
                Toast.LENGTH_SHORT).show();
    }

    private boolean probe(String baseUrl) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(
                    baseUrl + "/__relay_health").openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[2048];
                    int n;
                    while ((n = in.read(buffer)) >= 0
                            && out.size() < 16 * 1024) {
                        out.write(buffer, 0, n);
                    }
                    JSONObject body = new JSONObject(
                            out.toString(StandardCharsets.UTF_8.name()));
                    if (body.optBoolean("ok")) return true;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }

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
                while ((n = in.read(buffer)) >= 0
                        && out.size() < 16 * 1024) {
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
        if (value.isEmpty() || !value.startsWith("https://")) {
            return null;
        }

        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }

        if (value.endsWith("/v1")) {
            value = value.substring(0, value.length() - 3);
        }

        try {
            URL url = new URL(value);
            if (url.getHost() == null || url.getHost().isEmpty()) {
                return null;
            }
            if (!"https".equalsIgnoreCase(url.getProtocol())) {
                return null;
            }
            return value;
        } catch (Exception e) {
            return null;
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(
                    WebView view,
                    String url) {
                super.onPageFinished(view, url);
                if (relay != null) relay.notePageUrl(url);

                AiProvider provider = AiProvider.fromUrl(
                        url,
                        GatewayApp.selectedProvider());
                GatewayApp.setSelectedProvider(provider);
                refreshOverlayLabel();
                refreshUi();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                if (webFilePathCallback != null) {
                    webFilePathCallback.onReceiveValue(null);
                }
                webFilePathCallback = filePathCallback;

                try {
                    Intent intent = fileChooserParams.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(intent, REQUEST_WEB_FILE);
                    return true;
                } catch (Throwable e) {
                    webFilePathCallback = null;
                    Toast.makeText(
                            MainActivity.this,
                            "无法打开文件选择器：" + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });

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
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {
        if (requestCode == REQUEST_WEB_FILE) {
            ValueCallback<Uri[]> callback = webFilePathCallback;
            webFilePathCallback = null;
            if (callback != null) {
                Uri[] result = WebChromeClient.FileChooserParams.parseResult(
                        resultCode,
                        data);
                callback.onReceiveValue(result);
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("open_web", false)) {
            switchTab(TAB_WEB);
            openOriginalGateway();
        }
        startGatewayService(GatewayService.ACTION_ENSURE_RUNNING);
        refreshUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startGatewayService(GatewayService.ACTION_ENSURE_RUNNING);
        if (pendingOverlayPermission
                && Settings.canDrawOverlays(this)) {
            pendingOverlayPermission = false;
            showOverlay();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt("active_tab", activeTab);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        switchTab(state.getInt("active_tab", TAB_OVERVIEW));
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacks(statusTicker);

        if (isFinishing()) {
            GatewayApp.setRelay(null);
        }

        super.onDestroy();
    }
}
