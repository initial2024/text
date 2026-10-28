package com.omnigate.integrated;

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

public final class SidecarService extends Service {
    public static final String ACTION_RESTART_RELAY =
            "com.omnigate.integrated.RESTART_RELAY";
    public static final String ACTION_ENSURE_RUNNING =
            "com.omnigate.integrated.ENSURE_RUNNING";
    private static final String CHANNEL_ID =
            "omnigate_original_core_sidecar";
    private static final int NOTIFICATION_ID = 8787;

    private static volatile boolean serviceAlive;

    private SidecarProxyServer proxy;
    private CloudflareRelayClient cloudflareRelay;

    private final Handler watchdogHandler =
            new Handler(Looper.getMainLooper());

    private final Runnable watchdog = new Runnable() {
        @Override
        public void run() {
            ensureProxyAlive();
            watchdogHandler.postDelayed(this, 4_000L);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        GatewayApp.initialize(getApplicationContext());
        serviceAlive = true;
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        ensureProxyAlive();
        syncRelay();
        watchdogHandler.postDelayed(watchdog, 4_000L);
    }

    public static boolean isServiceAlive() {
        return serviceAlive;
    }

    private synchronized void ensureProxyAlive() {
        if (proxy != null && proxy.isListening()) {
            GatewayApp.setLocalApiStatus(
                    "Sidecar 运行中 · 127.0.0.1:8787 → 1601");
            return;
        }

        if (proxy != null) {
            proxy.shutdown();
            proxy = null;
        }

        SidecarProxyServer candidate = new SidecarProxyServer(status -> {
            GatewayApp.setLocalApiStatus(status);
            refreshNotification();
        });

        if (candidate.start()) {
            proxy = candidate;
            GatewayApp.setLocalApiStatus(
                    "Sidecar 运行中 · 127.0.0.1:8787 → 1601");
        } else {
            candidate.shutdown();
            GatewayApp.setLocalApiStatus(
                    "8787 启动失败 · 正在重试");
        }

        refreshNotification();
    }

    private synchronized void syncRelay() {
        if (cloudflareRelay != null) {
            cloudflareRelay.stop();
            cloudflareRelay = null;
        }

        if (!GatewayApp.tunnelEnabled()) {
            GatewayApp.setTunnelStatus("公网 Relay 已关闭");
            refreshNotification();
            return;
        }

        String relayKey = GatewayApp.relayKey();
        if (relayKey.isEmpty()) {
            GatewayApp.setTunnelStatus("需要配置 Relay Key");
            refreshNotification();
            return;
        }

        GatewayApp.setTunnelStatus("OmniGate Relay 正在连接");
        cloudflareRelay = new CloudflareRelayClient(
                GatewayApp.OWN_RELAY_WS,
                GatewayApp.effectivePublicUrl(),
                relayKey,
                SidecarProxyServer.LISTEN_PORT,
                status -> {
                    GatewayApp.setTunnelStatus(status);
                    refreshNotification();
                });
        cloudflareRelay.start();
        refreshNotification();
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {
        ensureProxyAlive();

        if (intent != null
                && ACTION_RESTART_RELAY.equals(intent.getAction())) {
            syncRelay();
        } else {
            refreshNotification();
        }

        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        ensureProxyAlive();
        refreshNotification();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        serviceAlive = false;
        watchdogHandler.removeCallbacks(watchdog);

        if (cloudflareRelay != null) {
            cloudflareRelay.stop();
            cloudflareRelay = null;
        }

        if (proxy != null) {
            proxy.shutdown();
            proxy = null;
        }

        GatewayApp.setLocalApiStatus("Sidecar 服务已停止");
        GatewayApp.setTunnelStatus("公网 Relay 已停止");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "OmniGate Sidecar",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(
                "Keeps the 8787 compatibility proxy and optional public relay available.");
        getSystemService(NotificationManager.class)
                .createNotificationChannel(channel);
    }

    private void refreshNotification() {
        try {
            NotificationManager manager =
                    getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.notify(
                        NOTIFICATION_ID,
                        buildNotification());
            }
        } catch (Throwable ignored) {
        }
    }

    private Notification buildNotification() {
        int immutable = Build.VERSION.SDK_INT >= 23
                ? PendingIntent.FLAG_IMMUTABLE
                : 0;

        Intent open = new Intent(this, CompatSettingsActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                this,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | immutable);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        boolean upstream =
                SidecarProxyServer.isUpstreamReachable();

        String text =
                "8787 → 1601 "
                        + (upstream ? "· 原核心可达" : "· 等待原核心")
                        + (GatewayApp.tunnelEnabled()
                        ? " · " + GatewayApp.tunnelStatus()
                        : "");

        return builder
                .setContentTitle("OmniGate Sidecar")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setOngoing(true)
                .setContentIntent(openPending)
                .build();
    }
}
