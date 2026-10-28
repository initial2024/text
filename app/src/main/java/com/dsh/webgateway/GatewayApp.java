package com.dsh.webgateway;

import android.app.Application;
import android.content.SharedPreferences;

import java.util.Locale;
import java.util.UUID;

public final class GatewayApp extends Application {
    public static final int PORT = 8787;
    public static final String TUNNEL_SUFFIX = ".deepseeks.dpdns.org";

    private static volatile WebRelay relay;
    private static volatile GatewayApp instance;\n    private static volatile String tunnelStatus = "已停止";

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

    public static boolean tunnelEnabled() {
        return prefs().getBoolean("tunnel_enabled", false);
    }

    public static void setTunnelEnabled(boolean enabled) {
        prefs().edit().putBoolean("tunnel_enabled", enabled).apply();
    }

    public static String tunnelPrefix() {
        SharedPreferences p = prefs();
        String value = p.getString("tunnel_prefix", null);
        if (value != null && !value.trim().isEmpty()) return value.trim().toLowerCase(Locale.ROOT);
        String compact = UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
        value = "gw-" + compact.substring(0, 12);
        p.edit().putString("tunnel_prefix", value).apply();
        return value;
    }

    public static String tunnelToken() {
        SharedPreferences p = prefs();
        String value = p.getString("tunnel_token", null);
        if (value != null && !value.trim().isEmpty()) return value.trim();
        value = UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "");
        p.edit().putString("tunnel_token", value).apply();
        return value;
    }

    public static void setTunnelIdentity(String prefix, String token) {
        if (prefix == null || !prefix.matches("gw-[a-zA-Z0-9-]{3,48}")) {
            throw new IllegalArgumentException("invalid tunnel prefix");
        }
        if (token == null || token.length() < 32 || token.length() > 256) {
            throw new IllegalArgumentException("invalid tunnel token");
        }
        prefs().edit()
                .putString("tunnel_prefix", prefix.toLowerCase(Locale.ROOT))
                .putString("tunnel_token", token.trim())
                .apply();
    }

    public static String generatedPublicUrl() {
        return "https://" + tunnelPrefix() + TUNNEL_SUFFIX;
    }

    public static String customPublicUrl() {
        String value = prefs().getString("custom_public_url", "");
        return value == null ? "" : value.trim();
    }

    public static boolean customPublicUrlVerified() {
        return prefs().getBoolean("custom_public_url_verified", false);
    }

    public static void setCustomPublicUrl(String value, boolean verified) {
        prefs().edit()
                .putString("custom_public_url", value == null ? "" : value.trim())
                .putBoolean("custom_public_url_verified", verified)
                .apply();
    }

    public static String effectivePublicUrl() {
        String custom = customPublicUrl();
        if (customPublicUrlVerified() && !custom.isEmpty()) return custom;
        return generatedPublicUrl();
    }

    public static String tunnelStatus() {
        return tunnelStatus;
    }

    public static void setTunnelStatus(String value) {
        tunnelStatus = value == null ? "" : value;
    }

    public static String apiKey() {
        SharedPreferences p = prefs();
        String value = p.getString("api_key", null);
        if (value != null && value.length() >= 32) return value;
        value = "dsh-" +
                UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "");
        p.edit().putString("api_key", value).apply();
        return value;
    }
}
