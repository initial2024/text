package com.omnigate.integrated;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;
import java.util.UUID;

/** Service-owned settings. Does not replace com.deepseek2.app.App. */
public final class GatewayApp {
    public static final String OWN_PUBLIC_BASE = "https://gw-33e850180248.claua.de";
    public static final String OWN_RELAY_WS = "wss://gw-33e850180248.claua.de/__device";
    private static volatile Context context;
    private static volatile String localApiStatus = "未启动";
    private static volatile String tunnelStatus = "未启动";
    private GatewayApp() {}
    public static void initialize(Context ctx) {
        if (ctx == null) throw new IllegalArgumentException("context null");
        context = ctx.getApplicationContext();
    }
    private static SharedPreferences prefs() {
        Context c=context;
        if (c==null) throw new IllegalStateException("OmniGate integrated context not initialized");
        return c.getSharedPreferences("omnigate_integrated_compat", Context.MODE_PRIVATE);
    }
    public static String localApiStatus(){return localApiStatus;}
    public static void setLocalApiStatus(String v){localApiStatus=v==null?"":v;}
    public static String tunnelStatus(){return tunnelStatus;}
    public static void setTunnelStatus(String v){tunnelStatus=v==null?"":v;}
    public static boolean tunnelEnabled(){return prefs().getBoolean("relay_enabled",false);}
    public static void setTunnelEnabled(boolean enabled){prefs().edit().putBoolean("relay_enabled",enabled).apply();}
    public static String relayKey(){return prefs().getString("relay_key","");}
    public static void setRelayKey(String v) {
        String s=v==null?"":v.trim();
        if (!s.isEmpty() && (s.length()<24||s.length()>256)) throw new IllegalArgumentException("Relay key length 24..256");
        if(s.indexOf('\n')>=0||s.indexOf('\r')>=0)throw new IllegalArgumentException("Newlines forbidden");
        prefs().edit().putString("relay_key",s).apply();
    }
    public static String apiKey(){
        String s=prefs().getString("api_key","");
        if(s.length()>=12)return s;
        s="og-"+UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");
        prefs().edit().putString("api_key",s).apply();
        return s;
    }
    public static void setApiKey(String v){
        String s=v==null?"":v.trim();
        if(s.length()<12||s.length()>256||s.contains("\n")||s.contains("\r"))throw new IllegalArgumentException("Public API Key length 12..256, no newlines");
        prefs().edit().putString("api_key",s).apply();
    }
    public static String upstreamApiKey(){return prefs().getString("upstream_api_key","");}
    public static void setUpstreamApiKey(String v){
        String s=v==null?"":v.trim();
        if(s.length()>256||s.contains("\n")||s.contains("\r"))throw new IllegalArgumentException("Invalid upstream key");
        prefs().edit().putString("upstream_api_key",s).apply();
    }
    public static String customPublicUrl(){return prefs().getString("custom_public_url","");}
    /** Never auto-verify a custom URL. This is a local configuration candidate only. */
    public static void setCustomPublicUrl(String v,boolean verified){
        String s=v==null?"":v.trim();
        if(!s.isEmpty()&&!s.startsWith("https://"))throw new IllegalArgumentException("HTTPS required");
        prefs().edit().putString("custom_public_url",s).putBoolean("custom_public_url_verified",verified).apply();
    }
    public static String effectivePublicUrl(){
        if(prefs().getBoolean("custom_public_url_verified",false)){
            String s=customPublicUrl();if(!s.isEmpty())return s;
        }
        return OWN_PUBLIC_BASE;
    }
}
