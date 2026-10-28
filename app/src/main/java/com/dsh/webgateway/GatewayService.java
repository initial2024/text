package com.dsh.webgateway;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public final class GatewayService extends Service {
    public static final String ACTION_RESTART_TUNNEL =
            "com.dsh.webgateway.RESTART_TUNNEL";
    private static final String CHANNEL_ID = "dsh_web_gateway";

    private LocalHttpServer server;
    private TunnelClient tunnelClient;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(GatewayApp.PORT, buildNotification());
        startServer();
        syncTunnel();
    }

    private void startServer() {
        if (server != null) server.shutdown();
        server = new LocalHttpServer(GatewayApp.PORT);
        server.start();
    }

    private synchronized void syncTunnel() {
        if (tunnelClient != null) {
            tunnelClient.stop();
            tunnelClient = null;
        }

        if (!GatewayApp.tunnelEnabled()) {
            GatewayApp.setTunnelStatus("已停止");
            refreshNotification();
            return;
        }

        GatewayApp.setTunnelStatus("正在连接");
        tunnelClient = new TunnelClient(
                GatewayApp.tunnelToken(),
                GatewayApp.tunnelPrefix(),
                GatewayApp.PORT,
                status -> {
                    GatewayApp.setTunnelStatus(status);
                    refreshNotification();
                });
        tunnelClient.start();
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
        if (intent != null && ACTION_RESTART_TUNNEL.equals(intent.getAction())) {
            syncTunnel();
        } else {
            refreshNotification();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (tunnelClient != null) {
            tunnelClient.stop();
            tunnelClient = null;
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
                    "DSH Web Gateway",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(
                    "Keeps the selected web AI gateway and optional public tunnel available");
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

        Intent showOverlay = new Intent(this, FloatingOverlayService.class);
        showOverlay.setAction(FloatingOverlayService.ACTION_SHOW);
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
        text.append(provider.displayName)
                .append(" · 127.0.0.1:")
                .append(GatewayApp.PORT);
        if (GatewayApp.tunnelEnabled()) {
            text.append(" · ").append(GatewayApp.tunnelStatus());
        }

        return b.setContentTitle("DSH Web Gateway")
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
