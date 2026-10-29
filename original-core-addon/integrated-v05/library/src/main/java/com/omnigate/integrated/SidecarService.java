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

    // Relay connection is owned by RelayRuntime process-wide. A foreground
    // service may observe it, but must not create a duplicate WebSocket.

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
        if (LocalProxyRuntime.start()) {
            GatewayApp.setLocalApiStatus("8787 正在监听 · 原 1601 上游需单独确认");
        } else {
            GatewayApp.setLocalApiStatus(LocalProxyRuntime.status());
        }
        refreshNotification();
    }

    private synchronized void syncRelay() {
        if(GatewayApp.tunnelEnabled())RelayRuntime.ensureStarted();
        else RelayRuntime.stop();
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
            if(GatewayApp.tunnelEnabled())RelayRuntime.restart();
            else RelayRuntime.stop();
            refreshNotification();
        } else {
            syncRelay();
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

        // A service stopped by Android must not tear down a WebSocket
        // explicitly started in the still-live API console.
        // The user-facing Stop service control explicitly stops RelayRuntime.
        // A service termination must not silently close the separately
        // running in-process 8787 when user has explicitly enabled relay.
        // The explicit Stop button disables relay first and stops 8787.
        if(!GatewayApp.tunnelEnabled())LocalProxyRuntime.stop();

        GatewayApp.setLocalApiStatus(GatewayApp.tunnelEnabled()
            ? "前台服务已结束；进程内 8787 按原状态保留"
            : "Sidecar 服务已停止");
        if(!GatewayApp.tunnelEnabled())
            GatewayApp.setTunnelStatus("公网 Relay 已关闭");
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
