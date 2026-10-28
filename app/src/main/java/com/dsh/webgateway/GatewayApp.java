package com.dsh.webgateway;

import android.app.Application;
import android.content.SharedPreferences;

import java.util.Locale;
import java.util.UUID;

public final class GatewayApp extends Application {
    public static final int PORT = 8787;
    public static final int COMPAT_PORT = 1601;
    public static final String TUNNEL_SUFFIX = ".deepseeks.dpdns.org";
    public static final String LEGACY_PREFIX = "gw-33e850180248";
    public static final String LEGACY_PUBLIC_URL =
            "https://gw-33e850180248.deepseeks.dpdns.org";

    private static volatile WebRelay relay;
    private static volatile GatewayApp instance;
    private static volatile String tunnelStatus = "已停止";

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

    public static boolean hasStoredTunnelIdentity() {
        String prefix = prefs().getString("tunnel_prefix", null);
        String token = prefs().getString("tunnel_token", null);
        return prefix != null && !prefix.trim().isEmpty() &&
                token != null && !token.trim().isEmpty();
    }

    public static String tunnelPrefix() {
        SharedPreferences p = prefs();
        String value = p.getString("tunnel_prefix", null);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim().toLowerCase(Locale.ROOT);
        }
        String compact = UUID.randomUUID().toString()
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
        value = "gw-" + compact.substring(0, 12);
        p.edit().putString("tunnel_prefix", value).apply();
        return value;
    }

    public static String tunnelToken() {
        SharedPreferences p = prefs();
        String value = p.getString("tunnel_token", null);
        if (value != null && !value.trim().isEmpty()) return value.trim();
        value = newTunnelToken();
        p.edit().putString("tunnel_token", value).apply();
        return value;
    }

    private static String newTunnelToken() {
        return UUID.randomUUID().toString().replace("-", "") +
                UUID.randomUUID().toString().replace("-", "");
    }

    public static void setTunnelIdentity(String prefix, String token) {
        String normalizedPrefix = prefix == null
                ? ""
                : prefix.trim().toLowerCase(Locale.ROOT);
        String normalizedToken = token == null ? "" : token.trim();

        if (!normalizedPrefix.matches("gw-[a-z0-9]{3,48}")) {
            throw new IllegalArgumentException("prefix 必须以 gw- 开头且只含小写字母/数字");
        }
        if (!normalizedToken.matches("[A-Za-z0-9_-]{32,256}")) {
            throw new IllegalArgumentException("token 格式无效");
        }

        prefs().edit()
                .putString("tunnel_prefix", normalizedPrefix)
                .putString("tunnel_token", normalizedToken)
                .apply();
    }

    public static void generateNewTunnelIdentity() {
        String compact = UUID.randomUUID().toString()
                .replace("-", "")
                .toLowerCase(Locale.ROOT);
        prefs().edit()
                .putString("tunnel_prefix", "gw-" + compact.substring(0, 12))
                .putString("tunnel_token", newTunnelToken())
                .apply();
    }

    public static String generatedPublicUrl() {
        return "https://" + tunnelPrefix() + TUNNEL_SUFFIX;
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
