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
    private static final int TAB_OVERVIEW = 0;
    private static final int TAB_SESSIONS = 1;
    private static final int TAB_WEB = 2;

    private WebView webView;
    private WebRelay relay;
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
    private TextView publicUrlText;
    private TextView tunnelStateText;
    private TextView currentProviderTitle;
    private TextView currentProviderDesc;
    private TextView webProviderTitle;
    private Switch tunnelSwitch;
    private boolean updatingTunnelSwitch;
    private boolean pendingOverlayPermission;

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

        webView = new WebView(this);
        buildUi();
        configureWebView();
        startGatewayService(null);

        if (savedInstanceState == null) {
            webView.loadUrl(GatewayApp.selectedProvider().url);
        } else {
            webView.restoreState(savedInstanceState);
        }

        switchTab(TAB_OVERVIEW);
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
                "多 AI 网页网关",
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
                UiKit.dp(this, 18),
                UiKit.dp(this, 8),
                UiKit.dp(this, 18),
                UiKit.dp(this, 26));
        scroll.addView(content);

        TextView hero = UiKit.text(
                this,
                "一个入口，连接多个网页 AI",
                26f,
                text,
                true);
        content.addView(hero, UiKit.matchWrap());

        TextView heroSub = UiKit.text(
                this,
                "保留网页登录身份，把 DeepSeek、ChatGPT、Grok 与 GLM 统一成兼容接口。",
                14f,
                muted,
                false);
        heroSub.setLineSpacing(UiKit.dp(this, 3), 1f);
        LinearLayout.LayoutParams heroSubLp = UiKit.matchWrap();
        heroSubLp.topMargin = UiKit.dp(this, 8);
        heroSubLp.bottomMargin = UiKit.dp(this, 20);
        content.addView(heroSub, heroSubLp);

        content.addView(buildStatusCard(), cardLp(0));
        content.addView(buildApiCard(), cardLp(12));
        content.addView(buildTunnelCard(), cardLp(12));
        content.addView(buildProviderSummaryCard(), cardLp(12));
        content.addView(buildOverlayCard(), cardLp(12));

        return scroll;
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
                "本机 API 与网页会话已就绪",
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
                "打开网页",
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
        TextView label = UiKit.text(this, "API 接口", 14f, muted, true);
        titleRow.addView(label, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView copy = UiKit.pill(
                this,
                "复制",
                primary,
                primarySoft,
                primarySoft,
                true);
        copy.setOnClickListener(v -> copyConnectionInfo());
        titleRow.addView(copy);
        card.addView(titleRow, UiKit.matchWrap());

        endpointText = UiKit.text(
                this,
                "http://127.0.0.1:" + GatewayApp.PORT + "/v1",
                18f,
                text,
                false);
        endpointText.setTextIsSelectable(true);
        LinearLayout.LayoutParams epLp = UiKit.matchWrap();
        epLp.topMargin = UiKit.dp(this, 14);
        card.addView(endpointText, epLp);

        TextView modelLabel = UiKit.text(
                this,
                "当前模型",
                12.5f,
                muted,
                false);
        LinearLayout.LayoutParams mlLp = UiKit.matchWrap();
        mlLp.topMargin = UiKit.dp(this, 18);
        card.addView(modelLabel, mlLp);

        modelText = UiKit.text(
                this,
                GatewayApp.selectedProvider().model,
                17f,
                text,
                true);
        LinearLayout.LayoutParams mLp = UiKit.matchWrap();
        mLp.topMargin = UiKit.dp(this, 5);
        card.addView(modelText, mLp);

        return card;
    }

    private View buildTunnelCard() {
        LinearLayout card = UiKit.card(this, surface, border);

        LinearLayout head = UiKit.horizontal(this);
        LinearLayout labels = UiKit.vertical(this);

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

        head.addView(labels, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        tunnelSwitch = new Switch(this);
        tunnelSwitch.setShowText(false);
        tunnelSwitch.setOnCheckedChangeListener(this::onTunnelSwitchChanged);
        head.addView(tunnelSwitch);

        card.addView(head, UiKit.matchWrap());

        publicUrlText = UiKit.text(
                this,
                GatewayApp.generatedPublicUrl(),
                17f,
                primary2,
                false);
        publicUrlText.setTextIsSelectable(true);
        publicUrlText.setLineSpacing(UiKit.dp(this, 2), 1f);
        LinearLayout.LayoutParams urlLp = UiKit.matchWrap();
        urlLp.topMargin = UiKit.dp(this, 18);
        card.addView(publicUrlText, urlLp);

        TextView hint = UiKit.text(
                this,
                "默认兼容原 deepseeks.dpdns.org 穿透；也可以绑定你自己的 HTTPS 域名。",
                12.5f,
                muted,
                false);
        hint.setLineSpacing(UiKit.dp(this, 3), 1f);
        LinearLayout.LayoutParams hintLp = UiKit.matchWrap();
        hintLp.topMargin = UiKit.dp(this, 10);
        card.addView(hint, hintLp);

        LinearLayout actions = UiKit.horizontal(this);
        LinearLayout.LayoutParams actionsLp = UiKit.matchWrap();
        actionsLp.topMargin = UiKit.dp(this, 14);

        TextView configure = UiKit.pill(
                this,
                "配置网址",
                primary,
                primarySoft,
                primarySoft,
                true);
        configure.setOnClickListener(v -> showTunnelConfigDialog());
        actions.addView(configure, UiKit.weighted(1f));

        TextView importOld = UiKit.pill(
                this,
                "导入旧参数",
                text,
                surfaceSoft,
                border,
                false);
        LinearLayout.LayoutParams importLp = UiKit.weighted(1f);
        importLp.leftMargin = UiKit.dp(this, 10);
        importOld.setLayoutParams(importLp);
        importOld.setOnClickListener(v -> showImportTunnelDialog());
        actions.addView(importOld);

        card.addView(actions, actionsLp);
        return card;
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
                "44dp 紧凑悬浮球 · 可拖动 · 长按隐藏",
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
                "将不同网页 AI 转换为统一的 OpenAI 兼容接口",
                14f,
                muted,
                false);
        LinearLayout.LayoutParams subLp = UiKit.matchWrap();
        subLp.topMargin = UiKit.dp(this, 8);
        content.addView(subtitle, subLp);

        LinearLayout info = UiKit.card(this, surfaceSoft, border);
        TextView infoText = UiKit.text(
                this,
                "Android WebView 独立网页登录：每家服务商保留自己的 Cookie 和登录态。切换服务商不会停止 8787 API 或公网穿透。",
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
        LinearLayout card = UiKit.card(this, surface, border);
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

        TextView name = UiKit.text(
                this,
                provider.displayName,
                21f,
                text,
                true);
        labels.addView(name, UiKit.matchWrap());

        TextView desc = UiKit.text(
                this,
                provider.description,
                13.5f,
                muted,
                false);
        desc.setLineSpacing(UiKit.dp(this, 3), 1f);
        LinearLayout.LayoutParams descLp = UiKit.matchWrap();
        descLp.topMargin = UiKit.dp(this, 5);
        labels.addView(desc, descLp);

        TextView model = UiKit.text(
                this,
                provider.model,
                12f,
                primary2,
                true);
        LinearLayout.LayoutParams modelLp = UiKit.matchWrap();
        modelLp.topMargin = UiKit.dp(this, 8);
        labels.addView(model, modelLp);

        row.addView(labels, labelsLp);

        TextView use = UiKit.pill(
                this,
                provider == GatewayApp.selectedProvider() ? "当前" : "使用",
                primary,
                primarySoft,
                primarySoft,
                true);
        use.setOnClickListener(v -> selectProvider(provider, true));
        row.addView(use);

        card.addView(row, UiKit.matchWrap());
        return card;
    }

    private View buildWebPage() {
        LinearLayout page = UiKit.vertical(this);
        page.setBackgroundColor(bg);

        LinearLayout webBar = UiKit.horizontal(this);
        webBar.setPadding(
                UiKit.dp(this, 16),
                UiKit.dp(this, 8),
                UiKit.dp(this, 12),
                UiKit.dp(this, 8));

        webProviderTitle = UiKit.text(
                this,
                GatewayApp.selectedProvider().displayName,
                16f,
                text,
                true);
        webBar.addView(webProviderTitle, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView switchProvider = UiKit.pill(
                this,
                "切换服务商",
                primary,
                primarySoft,
                primarySoft,
                true);
        switchProvider.setOnClickListener(v -> switchTab(TAB_SESSIONS));
        webBar.addView(switchProvider);

        page.addView(webBar, UiKit.matchWrap());

        FrameLayout browserShell = new FrameLayout(this);
        browserShell.setBackground(UiKit.stroked(
                surface,
                border,
                UiKit.dp(this, 20),
                Math.max(1, UiKit.dp(this, 1))));
        browserShell.setPadding(
                UiKit.dp(this, 1),
                UiKit.dp(this, 1),
                UiKit.dp(this, 1),
                UiKit.dp(this, 1));

        browserShell.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams shellLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f);
        shellLp.leftMargin = UiKit.dp(this, 12);
        shellLp.rightMargin = UiKit.dp(this, 12);
        shellLp.bottomMargin = UiKit.dp(this, 10);
        page.addView(browserShell, shellLp);

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
                "网页"),
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

        item.setOnClickListener(v -> switchTab(index));

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
        if (relay != null) relay.openProvider(provider);
        else webView.loadUrl(provider.url);
        refreshOverlayLabel();
        refreshUi();
        if (openWeb) switchTab(TAB_WEB);
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
        if (publicUrlText != null) {
            publicUrlText.setText(
                    GatewayApp.tunnelEnabled()
                            ? GatewayApp.effectivePublicUrl()
                            : GatewayApp.generatedPublicUrl());
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
                "OmniGate 2.8\n\n"
                        + "多网页 AI 统一网关\n"
                        + "DeepSeek · ChatGPT · Grok · GLM\n\n"
                        + "本地 API 端口：" + GatewayApp.PORT
                        + "\n公网穿透：deepseeks.dpdns.org 兼容协议"
                        + "\n支持自定义 HTTPS 域名、附件、长输出、自动换会话与工具调用兼容。";

        new AlertDialog.Builder(this)
                .setTitle("关于 OmniGate")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show();
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
                "已显示 44dp 小悬浮球；拖动移动，长按隐藏",
                Toast.LENGTH_SHORT).show();
    }

    private void hideOverlay() {
        Intent intent = new Intent(this, FloatingOverlayService.class);
        intent.setAction(FloatingOverlayService.ACTION_HIDE);
        startService(intent);
        GatewayApp.setOverlayEnabled(false);
        Toast.makeText(
                this,
                "悬浮窗已隐藏，OmniGate 服务继续运行",
                Toast.LENGTH_SHORT).show();
    }

    private void refreshOverlayLabel() {
        Intent intent = new Intent(this, FloatingOverlayService.class);
        intent.setAction(FloatingOverlayService.ACTION_REFRESH);
        startService(intent);
    }

    private void showTunnelConfigDialog() {
        LinearLayout box = UiKit.vertical(this);
        int pad = UiKit.dp(this, 24);
        box.setPadding(pad, UiKit.dp(this, 8), pad, 0);

        TextView defaultUrl = UiKit.text(
                this,
                "默认公网：\n" + GatewayApp.generatedPublicUrl(),
                13.5f,
                muted,
                false);
        defaultUrl.setTextIsSelectable(true);
        box.addView(defaultUrl, UiKit.matchWrap());

        EditText custom = new EditText(this);
        custom.setSingleLine(true);
        custom.setHint("https://ai.example.com");
        custom.setText(GatewayApp.customPublicUrl());
        custom.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams customLp = UiKit.matchWrap();
        customLp.topMargin = UiKit.dp(this, 14);
        box.addView(custom, customLp);

        new AlertDialog.Builder(this)
                .setTitle("公网网址")
                .setMessage(
                        "自定义网址必须真正转发到本机 Gateway，并通过 /__tunnel_probe 才会启用。")
                .setView(box)
                .setNeutralButton("恢复默认", (dialog, which) -> {
                    GatewayApp.setCustomPublicUrl("", false);
                    refreshUi();
                })
                .setNegativeButton("取消", null)
                .setPositiveButton("验证并使用", (dialog, which) -> {
                    String candidate = normalizeBaseUrl(
                            custom.getText().toString());
                    if (candidate == null) {
                        Toast.makeText(
                                this,
                                "请输入有效的 https:// 网址",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (!GatewayApp.tunnelEnabled()) {
                        Toast.makeText(
                                this,
                                "请先开启公网穿透",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    GatewayApp.setCustomPublicUrl(candidate, false);
                    Toast.makeText(
                            this,
                            "正在验证公网探针",
                            Toast.LENGTH_SHORT).show();

                    new Thread(() -> {
                        boolean ok = probe(candidate);
                        runOnUiThread(() -> {
                            GatewayApp.setCustomPublicUrl(candidate, ok);
                            refreshUi();
                            Toast.makeText(
                                    this,
                                    ok
                                            ? "自定义网址验证通过"
                                            : "验证失败：网址尚未转发到当前 Gateway",
                                    Toast.LENGTH_LONG).show();
                        });
                    }, "custom-url-probe").start();
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
                    baseUrl + "/__tunnel_probe").openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) return false;

            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out =
                        new ByteArrayOutputStream();
                byte[] buffer = new byte[2048];
                int n;
                while ((n = in.read(buffer)) >= 0
                        && out.size() < 16 * 1024) {
                    out.write(buffer, 0, n);
                }

                JSONObject body = new JSONObject(
                        out.toString(
                                StandardCharsets.UTF_8.name()));
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
        if (pendingOverlayPermission
                && Settings.canDrawOverlays(this)) {
            pendingOverlayPermission = false;
            showOverlay();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
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
            if (webView != null) {
                webView.stopLoading();
                webView.destroy();
            }
        }

        super.onDestroy();
    }
}
