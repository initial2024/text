package com.omnigate.integrated;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

/** Narrow bridge to the original, untouched native API owner.
 *
 * Does not reimplement GeckoView, bypass website login, or automatically
 * start the native server at app boot. The user explicitly clicks "Start
 * native API"; only then can a resumed original MainActivity start its
 * original ApiServer via its own startServer() method.
 */
public final class OriginalCoreBridge {
    public static final int FALLBACK_PORT = 1601;
    public static final String REQUEST_START =
        "com.omnigate.integrated.REQUEST_START_NATIVE_API";
    private static volatile String lastStartStatus = "尚未请求启动原 API";

    private OriginalCoreBridge() {}

    private static Object preferences() throws ReflectiveOperationException {
        Class<?> cls = Class.forName("com.deepseek2.app.gateway.Prefs");
        Field field = cls.getField("INSTANCE");
        return field.get(null);
    }

    public static int configuredPort() {
        try {
            Context ctx = GatewayApp.applicationContext();
            if (ctx == null) return FALLBACK_PORT;
            Object value = preferences().getClass()
                .getMethod("port", Context.class).invoke(preferences(), ctx);
            int port = ((Number)value).intValue();
            if (port > 0 && port <= 65535) return port;
        } catch (Throwable ignored) {
            // Safe diagnostic fallback; original port is never modified.
        }
        return FALLBACK_PORT;
    }

    public static String originalApiKey() {
        try {
            Context ctx = GatewayApp.applicationContext();
            if (ctx == null) return "";
            Object p = preferences();
            Object value = p.getClass()
                .getMethod("key", Context.class).invoke(p, ctx);
            return value instanceof String ? (String)value : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static boolean isPortConflicted() {
        // The 8787 listener is owned by OmniGate proxy, never native ApiServer.
        return configuredPort() == SidecarProxyServer.LISTEN_PORT;
    }

    public static boolean repairNativePortOnExplicitClick() {
        if (!isPortConflicted()) return true;
        Context ctx = GatewayApp.applicationContext();
        if (ctx == null) {
            lastStartStatus = "缺少应用上下文，不能修复原生端口";
            return false;
        }
        try {
            Object p = preferences();
            p.getClass().getMethod("setPort", Context.class, int.class)
                .invoke(p, ctx, FALLBACK_PORT);
            if (configuredPort() != FALLBACK_PORT) {
                lastStartStatus = "写入原生端口后读回不一致；停止启动";
                return false;
            }
            lastStartStatus = "已由用户确认将原核心端口 8787 改为 1601";
            return true;
        } catch (Throwable ex) {
            lastStartStatus = "原核心端口修复失败：" +
                ex.getClass().getSimpleName() + "；请在原设置手动改为 1601";
            return false;
        }
    }

    public static boolean isListening() {
        if (isPortConflicted()) return false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), configuredPort()), 550);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String lastStartStatus() {
        return lastStartStatus;
    }

    public static String requestExplicitNativeStart(Activity settings) {
        // Never allow the proxy's own port to masquerade as native API health.
        if (!repairNativePortOnExplicitClick()) return lastStartStatus;
        if (isListening()) {
            lastStartStatus = "原生 API " + configuredPort() + " TCP 已连通（待模型验证）";
            return lastStartStatus;
        }
        try {
            Intent request = new Intent();
            request.setClassName(settings.getPackageName(),
                "com.deepseek2.app.ui.MainActivity");
            request.putExtra(REQUEST_START, true);
            lastStartStatus = "正在打开原管理页；原 MainActivity.onCreate 会自动 startServer";
            settings.startActivity(request);
        } catch (Throwable e) {
            lastStartStatus = "无法打开原管理页：" + e.getClass().getSimpleName();
        }
        return lastStartStatus;
    }

    /** MainActivity already calls startServer() from onCreate; do NOT restart it. */
    public static void onOriginalMainResumed(Activity activity) {
        Intent intent = activity.getIntent();
        if (intent == null || !intent.getBooleanExtra(REQUEST_START, false))
            return;
        intent.removeExtra(REQUEST_START);
        activity.getWindow().getDecorView().postDelayed(() -> {
            if (activity.isDestroyed() || activity.isFinishing()) return;
            lastStartStatus = isListening()
                ? "原生 API " + configuredPort() + " 已监听（需文本响应验收）"
                : "已打开原管理页，但原生端口 " + configuredPort() +
                  " 未监听；请检查原页启动状态和网站登录";
            GatewayApp.setLocalApiStatus(lastStartStatus);
        }, 900);
    }
}
