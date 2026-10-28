package com.dsh.webgateway;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

public final class GatewayService extends Service {
    public static final String ACTION_RESTART_TUNNEL =
            "com.dsh.webgateway.RESTART_TUNNEL";
    public static final String ACTION_ENSURE_RUNNING =
            "com.dsh.webgateway.ENSURE_RUNNING";
    private static final String CHANNEL_ID = "dsh_web_gateway";

    private static volatile boolean serviceAlive;

    private LocalHttpServer server;
    private TunnelClient tunnelClient;
    private CloudflareRelayClient cloudflareRelay;
    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            ensureServerAlive();
            watchdogHandler.postDelayed(this, 4_000L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        serviceAlive = true;
        createChannel();
        startForeground(GatewayApp.PORT, buildNotification());
        ensureServerAlive();
        syncTunnel();
        watchdogHandler.postDelayed(watchdog, 4_000L);
    }

    public static boolean isServiceAlive() {
        return serviceAlive;
    }

    private synchronized void ensureServerAlive() {
        if (server != null && server.isListening()) {
            boolean originalReady = OriginalGatewayProxy.probe();
            GatewayApp.setLocalApiStatus(
                    "Companion 8787 运行中 · 原核心 1601 "
                            + (originalReady ? "已连接" : "未连接"));
            return;
        }

        if (server != null) {
            server.shutdown();
            server = null;
        }

        LocalHttpServer candidate = new LocalHttpServer(GatewayApp.PORT);
        if (candidate.start()) {
            server = candidate;
            boolean originalReady = OriginalGatewayProxy.probe();
            GatewayApp.setLocalApiStatus(
                    "Companion 8787 运行中 · 原核心 1601 "
                            + (originalReady ? "已连接" : "未连接"));
        } else {
            candidate.shutdown();
            GatewayApp.setLocalApiStatus("8787 启动失败 · 正在重试");
        }
        refreshNotification();
    }

    private synchronized void syncTunnel() {
        if (tunnelClient != null) {
            tunnelClient.stop();
            tunnelClient = null;
        }
        if (cloudflareRelay != null) {
            cloudflareRelay.stop();
            cloudflareRelay = null;
        }

        if (!GatewayApp.tunnelEnabled()) {
            GatewayApp.setTunnelStatus("已停止");
            refreshNotification();
            return;
        }

        if ("legacy".equals(GatewayApp.tunnelBackend())) {
            GatewayApp.setTunnelStatus("Legacy 正在连接");
            tunnelClient = new TunnelClient(
                    GatewayApp.tunnelToken(),
                    GatewayApp.tunnelPrefix(),
                    GatewayApp.PORT,
                    status -> {
                        GatewayApp.setTunnelStatus("Legacy · " + status);
                        refreshNotification();
                    });
            tunnelClient.start();
        } else {
            String relayKey = GatewayApp.relayKey();
            if (relayKey.isEmpty()) {
                GatewayApp.setTunnelStatus("需要配置 Relay Key");
                refreshNotification();
                return;
            }

            GatewayApp.setTunnelStatus("OmniGate Relay 正在连接");
            cloudflareRelay = new CloudflareRelayClient(
                    GatewayApp.OWN_RELAY_WS,
                    GatewayApp.OWN_PUBLIC_BASE,
                    relayKey,
                    GatewayApp.PORT,
                    status -> {
                        GatewayApp.setTunnelStatus(status);
                        refreshNotification();
                    });
            cloudflareRelay.start();
        }

        refreshNotification();
    }

    private void refreshNotification() {
        try {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.notify(GatewayApp.PORT, buildNotification());
        } catch (Throwable ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureServerAlive();
        if (intent != null && ACTION_RESTART_TUNNEL.equals(intent.getAction())) {
            syncTunnel();
        } else {
            refreshNotification();
        }
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        ensureServerAlive();
        refreshNotification();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        serviceAlive = false;
        watchdogHandler.removeCallbacks(watchdog);
        GatewayApp.setLocalApiStatus("服务已停止");
        if (tunnelClient != null) {
            tunnelClient.stop();
            tunnelClient = null;
        }
        if (cloudflareRelay != null) {
            cloudflareRelay.stop();
            cloudflareRelay = null;
        }
        if (server != null) {
            server.shutdown();
            server = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "OmniGate",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(
                    "Keeps the OmniGate companion proxy and optional public tunnel available");
            getSystemService(NotificationManager.class)
                    .createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        int immutable = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_IMMUTABLE
                : 0;

        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | immutable);

        Intent showOverlay = new Intent(
                "com.deepseek2.app.action.SHOW_OVERLAY");
        showOverlay.setClassName(
                "com.deepseek2.app",
                "com.deepseek2.app.ui.OverlayService");
        PendingIntent overlayPi = PendingIntent.getService(
                this,
                1,
                showOverlay,
                PendingIntent.FLAG_UPDATE_CURRENT | immutable);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        AiProvider provider = GatewayApp.selectedProvider();

        StringBuilder text = new StringBuilder();
        text.append("8787→1601")
                .append(OriginalGatewayProxy.probe() ? " 已连接" : " 未连接");
        if (GatewayApp.tunnelEnabled()) {
            text.append(" · ").append(GatewayApp.tunnelStatus());
        }

        return b.setContentTitle("OmniGate")
                .setContentText(text.toString())
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setContentIntent(openPi)
                .addAction(
                        new Notification.Action.Builder(
                                null,
                                "显示悬浮窗",
                                overlayPi).build())
                .build();
    }
}
