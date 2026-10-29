package com.omnigate.integrated;

/**
 * One process-wide WebSocket owner for the EXISTING Cloudflare relay.
 * This does not create another HTTP listener or another browser.
 *
 * A user explicitly enabling the relay can start it while the settings
 * Activity is visible, without depending on an Android foreground service
 * whose start may be restricted. Connection is not promised after Android
 * kills the application process.
 */
public final class RelayRuntime {
    private static CloudflareRelayClient relay;
    private RelayRuntime(){}

    public static synchronized boolean isStarted(){
        return relay != null;
    }

    public static synchronized boolean isConnected(){
        return relay != null && relay.isConnected();
    }

    public static synchronized String ensureStarted(){
        if(!GatewayApp.tunnelEnabled()){
            GatewayApp.setTunnelStatus("公网 Relay 未启用");
            return GatewayApp.tunnelStatus();
        }
        String key=GatewayApp.relayKey();
        if(key==null || key.length()<24){
            GatewayApp.setTunnelStatus("Relay 配对 Key 未配置或长度无效");
            return GatewayApp.tunnelStatus();
        }
        if(!LocalProxyRuntime.isListening() && !LocalProxyRuntime.start()){
            GatewayApp.setTunnelStatus("本地 8787 未就绪；Relay 未连接");
            return GatewayApp.tunnelStatus();
        }
        if(relay!=null)return GatewayApp.tunnelStatus();
        GatewayApp.setTunnelStatus("正在连接原有 Cloudflare Relay");
        CloudflareRelayClient candidate=new CloudflareRelayClient(
            GatewayApp.OWN_RELAY_WS,
            GatewayApp.effectivePublicUrl(),
            key,
            SidecarProxyServer.LISTEN_PORT,
            GatewayApp::setTunnelStatus);
        relay=candidate;
        candidate.start();
        return GatewayApp.tunnelStatus();
    }

    public static synchronized void stop(){
        CloudflareRelayClient prior=relay;
        relay=null;
        if(prior!=null)prior.stop();
        GatewayApp.setTunnelStatus("公网 Relay 已关闭");
    }

    /** Explicit restart only, e.g. after updating the paired key.
     * Never trigger from /health polling or from the HTTP request handler.
     */
    public static synchronized String restart(){
        stop();
        return ensureStarted();
    }
}
