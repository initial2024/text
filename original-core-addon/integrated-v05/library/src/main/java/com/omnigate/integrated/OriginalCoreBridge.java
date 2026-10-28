package com.omnigate.integrated;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
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

    public static boolean isListening() {
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

    public static void requestExplicitNativeStart(Activity settings) {
        if (isListening()) {
            lastStartStatus = "原 API 端口 " + configuredPort() + " 已可连接";
            return;
        }
        try {
            Intent request = new Intent();
            request.setClassName(settings.getPackageName(),
                "com.deepseek2.app.ui.MainActivity");
            request.putExtra(REQUEST_START, true);
            lastStartStatus = "正在打开原核心管理页并请求启动";
            settings.startActivity(request);
        } catch (Exception e) {
            lastStartStatus = "原核心管理页面不可用：" + e.getClass().getSimpleName();
        }
    }

    /** Callback runs only after a user-initiated Intent reached MainActivity. */
    public static void onOriginalMainResumed(Activity activity) {
        Intent intent = activity.getIntent();
        if (intent == null || !intent.getBooleanExtra(REQUEST_START, false))
            return;
        intent.removeExtra(REQUEST_START);
        activity.getWindow().getDecorView().postDelayed(() -> {
            if (activity.isDestroyed() || activity.isFinishing()) return;
            if (isListening()) {
                lastStartStatus = "原核心端口 " + configuredPort() + " 已监听";
                return;
            }
            try {
                // startServer() is private in the frozen native MainActivity.
                // Invoke only on this initialized, foreground Activity.
                Method start = activity.getClass().getDeclaredMethod("startServer");
                start.setAccessible(true);
                start.invoke(activity);
                lastStartStatus = "已调用原核心 startServer；请检查端口 "
                    + configuredPort() + " 是否真正可连接";
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getTargetException();
                lastStartStatus = "原核心启动异常：" +
                    (cause == null ? "InvocationTargetException" :
                        cause.getClass().getSimpleName());
            } catch (Throwable ex) {
                lastStartStatus = "原核心启动失败：" +
                    ex.getClass().getSimpleName() + "；请在原管理页手动启动";
            }
            GatewayApp.setLocalApiStatus(lastStartStatus);
        }, 400);
    }
}
