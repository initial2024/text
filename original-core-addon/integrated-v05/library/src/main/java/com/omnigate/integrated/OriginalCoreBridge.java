package com.omnigate.integrated;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

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
    public static final String REQUEST_NATIVE_OVERLAY =
        "com.omnigate.integrated.REQUEST_ORIGINAL_OVERLAY";
    private static volatile WeakReference<Activity> originalMain =
        new WeakReference<>(null);
    private static final AtomicBoolean pendingNativeStart = new AtomicBoolean(false);

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

    /** Provider selected in the original third-party application, not the UI spinner. */
    public static String activeProviderModel() {
        try {
            Context ctx = GatewayApp.applicationContext();
            if (ctx == null) return "";
            Object prefs = preferences();
            Object provider = prefs.getClass().getMethod("provider", Context.class)
                .invoke(prefs, ctx);
            if (provider == null) return "";
            Object model = provider.getClass().getMethod("getModel").invoke(provider);
            return model instanceof String ? (String)model : "";
        } catch (Throwable ex) {
            return "";
        }
    }

    public static boolean originalHostAlive() {
        Activity a = originalMain.get();
        return a != null && !a.isFinishing() && !a.isDestroyed();
    }

    public static void onOriginalMainDestroyed(Activity a) {
        if (originalMain.get() == a) originalMain.clear();
    }

    public static String requestOriginalOverlay(Activity settings) {
        try {
            Intent intent = new Intent();
            intent.setClassName(settings.getPackageName(),
                "com.deepseek2.app.ui.MainActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.putExtra(REQUEST_NATIVE_OVERLAY, true);
            settings.startActivity(intent);
            return "已打开原核心；正在请求原版悬浮窗权限或悬浮入口";
        } catch (Throwable ex) {
            return "原版悬浮窗入口不可用：" + ex.getClass().getSimpleName();
        }
    }

    /** Original frozen MainActivity.server is the actual ownership evidence. */
    public static boolean nativeOwnerRunning() {
        Activity owner=originalMain.get();
        if(owner==null || owner.isFinishing() || owner.isDestroyed()) return false;
        try {
            Field field=owner.getClass().getDeclaredField("server");
            field.setAccessible(true);
            Object server=field.get(owner);
            if(server==null) return false;
            Object running=server.getClass().getMethod("isRunning").invoke(server);
            return Boolean.TRUE.equals(running);
        } catch(Throwable ex) { return false; }
    }

    public static String nativeLifecycleState() {
        Activity owner=originalMain.get();
        if(owner==null) return "原核心 MainActivity 未建立";
        if(owner.isFinishing() || owner.isDestroyed()) return "原核心 MainActivity 已关闭";
        return "原核心 MainActivity 存活；server.isRunning=" + nativeOwnerRunning()
            + "；Prefs.port=" + configuredPort();
    }

    /** The original MainActivity keeps its own PRIVATE int port initialized onCreate.
     * Updating Prefs alone is not enough on an already-open model page.
     */
    private static boolean alignOriginalInstancePort(Activity owner) {
        try {
            Field port=owner.getClass().getDeclaredField("port");
            port.setAccessible(true);
            int actual=port.getInt(owner);
            int requested=configuredPort();
            if(requested==SidecarProxyServer.LISTEN_PORT) {
                lastStartStatus="禁止把原生 API 启动到代理自己的 8787 端口";
                return false;
            }
            if(actual!=requested) port.setInt(owner, requested);
            if(port.getInt(owner)!=requested) throw new IllegalStateException("port readback");
            return true;
        } catch(Throwable ex) {
            lastStartStatus="原版页面端口同步失败：" + ex.getClass().getSimpleName();
            return false;
        }
    }

    private static void startNativeOnResumedOwner(Activity owner) {
        if(owner.isDestroyed() || owner.isFinishing()) {
            lastStartStatus="原版页面已关闭；未重复启动";
            return;
        }
        if(!alignOriginalInstancePort(owner)) return;
        if(nativeOwnerRunning() && isListening()) {
            lastStartStatus="原版 API 已运行于 "+configuredPort()+"；不重复创建或重启";
            return;
        }
        try {
            Method start=owner.getClass().getDeclaredMethod("startServer");
            start.setAccessible(true);
            start.invoke(owner);
            lastStartStatus="已按原版 startServer() 启动；即将核对服务归属和端口";
        } catch(java.lang.reflect.InvocationTargetException ex) {
            Throwable cause=ex.getTargetException();
            lastStartStatus="原版 startServer 抛出："+
                (cause==null?"InvocationTargetException":cause.getClass().getSimpleName());
        } catch(Throwable ex) {
            lastStartStatus="原版 startServer 无法调用："+ex.getClass().getSimpleName();
        }
        owner.getWindow().getDecorView().postDelayed(()->{
            if(owner.isDestroyed() || owner.isFinishing()) return;
            boolean running=nativeOwnerRunning();
            boolean tcp=isListening();
            lastStartStatus="原版 API："+(running?"server.isRunning=true":"server 未运行")
                +"；端口 "+configuredPort()+"："+(tcp?"TCP 可连接":"TCP 不可连接")
                +"。TCP 连通不等于模型已回复。";
            GatewayApp.setLocalApiStatus(lastStartStatus);
        },700);
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
        // The original Activity owns ApiServer. NEVER create a second MainActivity
        // when the previous host still exists: its onDestroy closes the server.
        if (!repairNativePortOnExplicitClick()) return lastStartStatus;
        if (originalHostAlive() && nativeOwnerRunning() && isListening()) {
            lastStartStatus="现有原版 MainActivity 的 API 已运行，未重复创建";
            return lastStartStatus;
        }
        pendingNativeStart.set(true);
        try {
            Intent request = new Intent();
            request.setClassName(settings.getPackageName(),
                "com.deepseek2.app.ui.MainActivity");
            request.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            request.putExtra(REQUEST_START,true);
            lastStartStatus="正在恢复已有模型页并核验原版服务；若不存在才创建";
            settings.startActivity(request);
        } catch(Throwable e) {
            pendingNativeStart.set(false);
            lastStartStatus="恢复原模型页失败："+e.getClass().getSimpleName();
        }
        return lastStartStatus;
    }

    /** MainActivity already calls startServer() from onCreate; do NOT restart it. */
    public static void onOriginalMainResumed(Activity activity) {
        originalMain = new WeakReference<>(activity);
        Intent intent = activity.getIntent();
        if (intent == null) return;
        if (intent.getBooleanExtra(REQUEST_NATIVE_OVERLAY, false)) {
            intent.removeExtra(REQUEST_NATIVE_OVERLAY);
            activity.getWindow().getDecorView().post(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                try {
                    Method original = activity.getClass().getDeclaredMethod("requestOverlay");
                    original.setAccessible(true);
                    original.invoke(activity);
                    lastStartStatus = "已调用原版 requestOverlay，若需系统悬浮窗权限请在手机确认";
                } catch (Throwable ex) {
                    lastStartStatus = "原版悬浮窗无法开启：" + ex.getClass().getSimpleName();
                }
            });
        }
        boolean requested=pendingNativeStart.getAndSet(false)
            || intent.getBooleanExtra(REQUEST_START,false);
        intent.removeExtra(REQUEST_START);
        if(!requested)return;
        activity.getWindow().getDecorView().postDelayed(()->{
            if(activity.isDestroyed() || activity.isFinishing())return;
            startNativeOnResumedOwner(activity);
        },350);
    }
}
