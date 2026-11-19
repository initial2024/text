package com.omnigate.integrated;

/**
 * Single owner of the local loopback 8787 listener in this Android process.
 * Allows a foreground Activity to enable local-only API even when the platform
 * rejects starting a foreground service. This is not a background keepalive.
 */
public final class LocalProxyRuntime {
    private static SidecarProxyServer proxy;
    private static volatile String lastStatus = "未启动";
    private LocalProxyRuntime() {}

    public static synchronized boolean start() {
        if (proxy != null && proxy.isListening()) {
            lastStatus = "127.0.0.1:8787 正在监听";
            return true;
        }
        if (proxy != null) {
            proxy.shutdown();
            proxy = null;
        }
        try{
            GatewayApp.localApiKey();
        }catch(RuntimeException unavailable){
            lastStatus="本地鉴权密钥无法由 Android Keystore 安全生成；拒绝启动 8787";
            GatewayApp.setLocalApiStatus(lastStatus);
            return false;
        }
        SidecarProxyServer candidate =
            new SidecarProxyServer(value -> {
                lastStatus = value;
                GatewayApp.setLocalApiStatus(value);
            });
        if (!candidate.start()) {
            candidate.shutdown();
            lastStatus = "8787 绑定失败。检查端口是否已被独立 Sidecar 占用";
            GatewayApp.setLocalApiStatus(lastStatus);
            return false;
        }
        proxy = candidate;
        lastStatus = "127.0.0.1:8787 正在监听";
        GatewayApp.setLocalApiStatus(lastStatus);
        return true;
    }

    public static synchronized boolean isListening() {
        return proxy != null && proxy.isListening();
    }

    public static synchronized void stop() {
        if (proxy != null) {
            proxy.shutdown();
            proxy = null;
        }
        lastStatus = "用户手动停止";
        GatewayApp.setLocalApiStatus(lastStatus);
    }

    public static String status() {
        return lastStatus;
    }
}
