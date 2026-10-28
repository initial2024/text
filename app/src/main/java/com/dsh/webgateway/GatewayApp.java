package com.dsh.webgateway;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Base64;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Collections;

public final class GatewayApp extends Application {
    private static volatile WebRelay relay;
    private static volatile GatewayApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static WebRelay relay() {
        return relay;
    }

    public static void setRelay(WebRelay value) {
        relay = value;
    }

    private static SharedPreferences prefs() {
        if (instance == null) throw new IllegalStateException("Application not initialized");
        return instance.getSharedPreferences("gateway", MODE_PRIVATE);
    }

    public static boolean remoteEnabled() {
        return prefs().getBoolean("remote_enabled", false);
    }

    public static void setRemoteEnabled(boolean enabled) {
        prefs().edit().putBoolean("remote_enabled", enabled).apply();
    }

    public static String accessToken() {
        SharedPreferences p = prefs();
        String token = p.getString("remote_token", null);
        if (token != null && token.length() >= 24) return token;
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        token = Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        p.edit().putString("remote_token", token).apply();
        return token;
    }

    public static String tailscaleIpv4() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InetAddress a : Collections.list(nif.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || a.isLoopbackAddress()) continue;
                    byte[] b = a.getAddress();
                    int first = b[0] & 0xff;
                    int second = b[1] & 0xff;
                    if (first == 100 && second >= 64 && second <= 127) return a.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static String lanIpv4() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InetAddress a : Collections.list(nif.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress() && a.isSiteLocalAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
