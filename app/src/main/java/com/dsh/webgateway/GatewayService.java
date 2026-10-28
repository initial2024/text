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
    public static final String ACTION_RESTART_TUNNEL = "com.dsh.webgateway.RESTART_TUNNEL";
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
            channel.setDescription("Keeps the DeepSeek Web gateway and optional public tunnel available");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, flags);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        String text = "127.0.0.1:" + GatewayApp.PORT;
        if (GatewayApp.tunnelEnabled()) {
            text += " · " + GatewayApp.tunnelStatus();
        }

        return b.setContentTitle("DSH Web Gateway")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }
}
