package com.dsh.webgateway;

import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class FloatingOverlayService extends Service {
    public static final String ACTION_SHOW = "com.dsh.webgateway.OVERLAY_SHOW";
    public static final String ACTION_HIDE = "com.dsh.webgateway.OVERLAY_HIDE";
    public static final String ACTION_REFRESH = "com.dsh.webgateway.OVERLAY_REFRESH";

    private WindowManager windowManager;
    private TextView bubble;
    private LinearLayout panel;
    private WindowManager.LayoutParams params;
    private WindowManager.LayoutParams panelParams;
    private TextView panelStatus;
    private TextView panelProvider;
    private TextView panelTunnel;

    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            if (GatewayApp.overlayEnabled()) {
                ensureGatewayRunning();
                if (panel != null) refreshPanel();
                watchdogHandler.postDelayed(this, 3_000L);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_SHOW : intent.getAction();

        if (ACTION_HIDE.equals(action)) {
            hidePanel();
            hideBubble();
            GatewayApp.setOverlayEnabled(false);
            watchdogHandler.removeCallbacks(watchdog);
            return START_STICKY;
        }

        if (ACTION_REFRESH.equals(action)) {
            if (bubble != null) updateBubbleLabel();
            if (panel != null) refreshPanel();
            ensureGatewayRunning();
            return START_STICKY;
        }

        if (!Settings.canDrawOverlays(this)) {
            GatewayApp.setOverlayEnabled(false);
            stopSelf();
            return START_NOT_STICKY;
        }

        GatewayApp.setOverlayEnabled(true);
        ensureGatewayRunning();
        showBubble();
        watchdogHandler.removeCallbacks(watchdog);
        watchdogHandler.postDelayed(watchdog, 2_000L);
        return START_STICKY;
    }

    private void ensureGatewayRunning() {
        if (GatewayService.isServiceAlive()) return;

        try {
            Intent keep = new Intent(this, GatewayService.class);
            keep.setAction(GatewayService.ACTION_ENSURE_RUNNING);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(keep);
            } else {
                startService(keep);
            }
        } catch (Throwable e) {
            GatewayApp.setLocalApiStatus(
                    "8787 拉起失败 · "
                            + e.getClass().getSimpleName());
        }
    }

    private void showBubble() {
        if (bubble != null) {
            updateBubbleLabel();
            return;
        }

        bubble = new TextView(this);
        bubble.setGravity(Gravity.CENTER);
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(14.5f);
        bubble.setElevation(dp(10));

        int size = dp(44);
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                size,
                size,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(8);
        params.y = dp(180);

        updateBubbleLabel();
        bubble.setOnTouchListener(new DragTouchListener());
        bubble.setOnLongClickListener(v -> {
            hidePanel();
            hideBubble();
            GatewayApp.setOverlayEnabled(false);
            watchdogHandler.removeCallbacks(watchdog);
            Toast.makeText(
                    this,
                    "悬浮窗已隐藏，OmniGate 服务继续运行",
                    Toast.LENGTH_SHORT).show();
            return true;
        });

        windowManager.addView(bubble, params);
    }

    private void updateBubbleLabel() {
        if (bubble == null) return;

        AiProvider p = GatewayApp.selectedProvider();
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(providerColor(p));
        bg.setStroke(dp(2), Color.argb(110, 255, 255, 255));
        bubble.setBackground(bg);

        switch (p) {
            case CHATGPT:
                bubble.setText("G");
                break;
            case GROK:
                bubble.setText("X");
                break;
            case GLM:
                bubble.setText("智");
                break;
            case DEEPSEEK:
            default:
                bubble.setText("D");
                break;
        }

        bubble.setContentDescription(
                "OmniGate · "
                        + p.displayName
                        + "；点击展开小窗口，长按隐藏");
    }

    private void togglePanel() {
        ensureGatewayRunning();
        if (panel == null) {
            showPanel();
        } else {
            hidePanel();
        }
    }

    private void showPanel() {
        if (panel != null || bubble == null || params == null) return;

        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        int panelBg = dark ? Color.rgb(32, 32, 36) : Color.WHITE;
        int panelSoft = dark ? Color.rgb(45, 45, 51) : Color.rgb(245, 246, 250);
        int titleColor = dark ? Color.WHITE : Color.rgb(30, 31, 36);
        int muted = dark ? Color.rgb(175, 177, 186) : Color.rgb(110, 113, 124);
        int primary = Color.rgb(77, 86, 245);
        int border = dark ? Color.rgb(65, 67, 76) : Color.rgb(224, 226, 233);

        panel = UiKit.vertical(this);
        panel.setPadding(dp(16), dp(15), dp(16), dp(15));
        panel.setElevation(dp(14));
        panel.setBackground(UiKit.stroked(
                panelBg,
                border,
                dp(22),
                Math.max(1, dp(1))));

        LinearLayout header = UiKit.horizontal(this);
        TextView title = UiKit.text(
                this,
                "OmniGate",
                18f,
                titleColor,
                true);
        header.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView close = UiKit.text(
                this,
                "×",
                23f,
                muted,
                false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(UiKit.rounded(panelSoft, dp(14)));
        close.setOnClickListener(v -> hidePanel());
        header.addView(close, new LinearLayout.LayoutParams(dp(34), dp(34)));
        panel.addView(header, UiKit.matchWrap());

        panelStatus = UiKit.text(
                this,
                GatewayApp.localApiStatus(),
                12.5f,
                titleColor,
                true);
        panelStatus.setPadding(dp(11), dp(9), dp(11), dp(9));
        panelStatus.setBackground(UiKit.rounded(panelSoft, dp(14)));
        LinearLayout.LayoutParams statusLp = UiKit.matchWrap();
        statusLp.topMargin = dp(12);
        panel.addView(panelStatus, statusLp);

        panelProvider = UiKit.text(
                this,
                "",
                14f,
                titleColor,
                true);
        LinearLayout.LayoutParams providerLp = UiKit.matchWrap();
        providerLp.topMargin = dp(13);
        panel.addView(panelProvider, providerLp);

        TextView providerHint = UiKit.text(
                this,
                "点击下面图标直接切换网页 AI",
                11.5f,
                muted,
                false);
        LinearLayout.LayoutParams hintLp = UiKit.matchWrap();
        hintLp.topMargin = dp(3);
        panel.addView(providerHint, hintLp);

        LinearLayout providers = UiKit.horizontal(this);
        LinearLayout.LayoutParams providersLp = UiKit.matchWrap();
        providersLp.topMargin = dp(10);
        panel.addView(providers, providersLp);

        addProviderButton(providers, AiProvider.DEEPSEEK, "D", Color.rgb(19, 103, 245), panelSoft);
        addProviderButton(providers, AiProvider.CHATGPT, "G", Color.rgb(16, 163, 127), panelSoft);
        addProviderButton(providers, AiProvider.GROK, "X", Color.rgb(28, 29, 33), panelSoft);
        addProviderButton(providers, AiProvider.GLM, "智", Color.rgb(42, 99, 235), panelSoft);

        LinearLayout localRow = UiKit.horizontal(this);
        LinearLayout.LayoutParams localLp = UiKit.matchWrap();
        localLp.topMargin = dp(13);

        LinearLayout localText = UiKit.vertical(this);
        TextView localLabel = UiKit.text(this, "本机 Base URL", 10.5f, muted, true);
        localText.addView(localLabel, UiKit.matchWrap());
        TextView localValue = UiKit.text(
                this,
                "http://127.0.0.1:" + GatewayApp.PORT + "/v1",
                12.5f,
                titleColor,
                false);
        LinearLayout.LayoutParams localValueLp = UiKit.matchWrap();
        localValueLp.topMargin = dp(3);
        localText.addView(localValue, localValueLp);
        localRow.addView(localText, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView copyUrl = smallAction("复制 URL", primary, panelSoft);
        copyUrl.setOnClickListener(v -> copy(
                "OmniGate Base URL",
                "http://127.0.0.1:" + GatewayApp.PORT + "/v1"));
        localRow.addView(copyUrl);
        panel.addView(localRow, localLp);

        LinearLayout keyRow = UiKit.horizontal(this);
        LinearLayout.LayoutParams keyLp = UiKit.matchWrap();
        keyLp.topMargin = dp(10);

        TextView masked = UiKit.text(
                this,
                maskedApiKey(),
                12.5f,
                muted,
                false);
        keyRow.addView(masked, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));

        TextView copyKey = smallAction("复制 API Key", primary, panelSoft);
        copyKey.setOnClickListener(v -> copy("OmniGate API Key", GatewayApp.apiKey()));
        keyRow.addView(copyKey);
        panel.addView(keyRow, keyLp);

        panelTunnel = UiKit.text(
                this,
                "",
                11.5f,
                muted,
                false);
        LinearLayout.LayoutParams tunnelLp = UiKit.matchWrap();
        tunnelLp.topMargin = dp(11);
        panel.addView(panelTunnel, tunnelLp);

        LinearLayout actions = UiKit.horizontal(this);
        LinearLayout.LayoutParams actionsLp = UiKit.matchWrap();
        actionsLp.topMargin = dp(14);

        TextView web = smallAction("打开网页", Color.WHITE, primary);
        web.setOnClickListener(v -> openMainActivity(true));
        actions.addView(web, new LinearLayout.LayoutParams(
                0,
                dp(40),
                1f));

        TextView main = smallAction("主界面", titleColor, panelSoft);
        LinearLayout.LayoutParams mainLp = new LinearLayout.LayoutParams(
                0,
                dp(40),
                1f);
        mainLp.leftMargin = dp(8);
        main.setOnClickListener(v -> openMainActivity(false));
        actions.addView(main, mainLp);

        panel.addView(actions, actionsLp);

        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        panelParams = new WindowManager.LayoutParams(
                dp(292),
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;
        positionPanel();

        refreshPanel();
        windowManager.addView(panel, panelParams);
    }

    private TextView smallAction(
            String text,
            int textColor,
            int fill) {
        TextView v = UiKit.text(this, text, 11.5f, textColor, true);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(10), dp(8), dp(10), dp(8));
        v.setBackground(UiKit.rounded(fill, dp(14)));
        return v;
    }

    private void addProviderButton(
            LinearLayout row,
            AiProvider provider,
            String label,
            int color,
            int fallbackFill) {
        TextView button = UiKit.text(
                this,
                label,
                14f,
                Color.WHITE,
                true);
        button.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        bg.setStroke(
                dp(provider == GatewayApp.selectedProvider() ? 3 : 1),
                provider == GatewayApp.selectedProvider()
                        ? Color.WHITE
                        : Color.argb(80, 255, 255, 255));
        button.setBackground(bg);
        button.setOnClickListener(v -> selectProvider(provider));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                dp(40),
                dp(40));
        if (row.getChildCount() > 0) lp.leftMargin = dp(10);
        row.addView(button, lp);
    }

    private void selectProvider(AiProvider provider) {
        GatewayApp.setSelectedProvider(provider);
        WebRelay relay = GatewayApp.relay();
        if (relay != null) {
            relay.openProvider(provider);
        }
        updateBubbleLabel();
        refreshPanel();
        Toast.makeText(
                this,
                "已切换到 " + provider.displayName,
                Toast.LENGTH_SHORT).show();
    }

    private void refreshPanel() {
        if (panel == null) return;
        AiProvider p = GatewayApp.selectedProvider();

        if (panelStatus != null) {
            String prefix = GatewayService.isServiceAlive() ? "● " : "○ ";
            panelStatus.setText(prefix + GatewayApp.localApiStatus());
            panelStatus.setTextColor(
                    GatewayService.isServiceAlive()
                            ? Color.rgb(39, 181, 102)
                            : Color.rgb(239, 86, 86));
        }

        if (panelProvider != null) {
            panelProvider.setText(
                    p.displayName + "  ·  " + p.model);
        }

        if (panelTunnel != null) {
            String relayReady = GatewayApp.relay() == null
                    ? "网页会话未载入"
                    : "网页会话已载入";
            String tunnel = GatewayApp.tunnelEnabled()
                    ? GatewayApp.tunnelStatus()
                    : "公网穿透未开启";
            panelTunnel.setText(relayReady + "  ·  " + tunnel);
        }
    }

    private String maskedApiKey() {
        String key = GatewayApp.apiKey();
        if (key.length() <= 10) return "API Key · ••••••••";
        return "API Key · "
                + key.substring(0, 4)
                + "••••••••"
                + key.substring(key.length() - 4);
    }

    private void copy(String label, String value) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(
                ClipData.newPlainText(label, value));
        Toast.makeText(
                this,
                label + " 已复制",
                Toast.LENGTH_SHORT).show();
    }

    private void positionPanel() {
        if (panelParams == null || params == null) return;

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int panelWidth = dp(292);

        if (params.x + dp(44) + dp(10) + panelWidth <= screenWidth) {
            panelParams.x = params.x + dp(54);
        } else {
            panelParams.x = Math.max(
                    dp(8),
                    params.x - panelWidth - dp(10));
        }

        panelParams.y = Math.max(
                dp(50),
                Math.min(params.y - dp(10), screenHeight - dp(420)));

        if (panel != null) {
            try {
                windowManager.updateViewLayout(panel, panelParams);
            } catch (Throwable ignored) {}
        }
    }

    private int providerColor(AiProvider provider) {
        switch (provider) {
            case CHATGPT:
                return Color.rgb(16, 163, 127);
            case GROK:
                return Color.rgb(28, 29, 33);
            case GLM:
                return Color.rgb(42, 99, 235);
            case DEEPSEEK:
            default:
                return Color.rgb(19, 103, 245);
        }
    }

    private void hidePanel() {
        if (panel != null) {
            try {
                windowManager.removeView(panel);
            } catch (Throwable ignored) {}
            panel = null;
            panelParams = null;
            panelStatus = null;
            panelProvider = null;
            panelTunnel = null;
        }
    }

    private void hideBubble() {
        if (bubble != null) {
            try {
                windowManager.removeView(bubble);
            } catch (Throwable ignored) {}
            bubble = null;
            params = null;
        }
    }

    private void openMainActivity(boolean openWeb) {
        hidePanel();
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        open.putExtra("open_web", openWeb);
        startActivity(open);
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        watchdogHandler.removeCallbacks(watchdog);
        hidePanel();
        hideBubble();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final class DragTouchListener implements View.OnTouchListener {
        private float downRawX;
        private float downRawY;
        private int startX;
        private int startY;
        private long downAt;
        private boolean moved;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (params == null) return false;

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    startX = params.x;
                    startY = params.y;
                    downAt = System.currentTimeMillis();
                    moved = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;

                    if (Math.abs(dx) > dp(5)
                            || Math.abs(dy) > dp(5)) {
                        if (!moved) hidePanel();
                        moved = true;
                    }

                    int screenWidth =
                            getResources().getDisplayMetrics().widthPixels;
                    int screenHeight =
                            getResources().getDisplayMetrics().heightPixels;

                    params.x = Math.max(
                            0,
                            Math.min(
                                    startX + Math.round(dx),
                                    screenWidth - dp(44)));
                    params.y = Math.max(
                            dp(24),
                            Math.min(
                                    startY + Math.round(dy),
                                    screenHeight - dp(72)));

                    try {
                        windowManager.updateViewLayout(bubble, params);
                    } catch (Throwable ignored) {}
                    return true;

                case MotionEvent.ACTION_UP:
                    long held = System.currentTimeMillis() - downAt;
                    if (!moved && held < 600L) {
                        togglePanel();
                    } else if (!moved && held >= 600L) {
                        v.performLongClick();
                    }
                    return true;

                default:
                    return false;
            }
        }
    }
}
