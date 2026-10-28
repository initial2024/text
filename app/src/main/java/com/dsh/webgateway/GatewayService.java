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
    private static final String CHANNEL_ID = "dsh_web_gateway";
    private LocalHttpServer server;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(1601, buildNotification());
        startServer();
    }

    private void startServer() {
        if (server != null) server.shutdown();
        server = new LocalHttpServer(1601, GatewayApp.remoteEnabled());
        server.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "com.dsh.webgateway.RESTART_SERVER".equals(intent.getAction())) {
            startServer();
            getSystemService(NotificationManager.class).notify(1601, buildNotification());
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (server != null) server.shutdown();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "DSH Web Gateway", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps the DSH DeepSeek Web gateway available");
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
        String endpoint = GatewayApp.remoteEnabled() ? "remote/Tailscale enabled · :1601" : "127.0.0.1:1601";
        return b.setContentTitle("DSH Web Gateway")
                .setContentText(endpoint + " · DeepSeek Web relay")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
    }
}
