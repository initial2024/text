package com.omnigate.integrated;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;
import java.util.UUID;

/** Service-owned settings. Does not replace com.deepseek2.app.App. */
public final class GatewayApp {
    // No user-owned domain or credential is embedded in the public APK.
    // Explicitly supplied HTTPS relay endpoint is kept only on the device.
    private static volatile Context context;
    // Process-only cache; Keystore-backed source remains non-exportable.
    private static volatile String cachedLocalApiKey;
    private static volatile String localApiStatus = "未启动";
    private static volatile String tunnelStatus = "未启动";
    private GatewayApp() {}
    public static void initialize(Context ctx) {
        if (ctx == null) throw new IllegalArgumentException("context null");
        context = ctx.getApplicationContext();
    }
    public static Context applicationContext() {
        return context;
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
    // Once the user enabled OmniGate locally, re-establish 8787 whenever
    // this Android application process is created. No Android BOOT receiver
    // and no attempt to restart the frozen original WebDriver/ApiServer.
    public static boolean autoStartEnabled(){
        return prefs().getBoolean("auto_start_local_enabled",true);
    }
    public static void setAutoStartEnabled(boolean value){
        prefs().edit().putBoolean("auto_start_local_enabled",value).apply();
    }
    // Opt-in notification foreground service: Android may reject it. This
    // preference never overrides background-start restrictions.
    public static boolean backgroundKeepAliveEnabled(){
        return prefs().getBoolean("background_keepalive_enabled",false);
    }
    public static void setBackgroundKeepAliveEnabled(boolean value){
        prefs().edit().putBoolean("background_keepalive_enabled",value).apply();
    }
    public static boolean tunnelEnabled(){return prefs().getBoolean("relay_enabled",false);}
    public static void setTunnelEnabled(boolean enabled){prefs().edit().putBoolean("relay_enabled",enabled).apply();}
    public static String relayKey(){return prefs().getString("relay_key","");}
    public static void setRelayKey(String v) {
        String s=v==null?"":v.trim();
        if (!s.isEmpty() && (s.length()<24||s.length()>256)) throw new IllegalArgumentException("Relay key length 24..256");
        if(s.indexOf('\n')>=0||s.indexOf('\r')>=0)throw new IllegalArgumentException("Newlines forbidden");
        prefs().edit().putString("relay_key",s).apply();
    }
    /**
     * Per-Android-UID local 8787 credential. HMAC key is non-exportable in
     * AndroidKeyStore and differs across real OS users/work profiles.
     * Do not store the derived token in SharedPreferences/Android backup.
     * OEM clone wrappers that reuse a UID/keystore namespace cannot be
     * independently guaranteed as distinct instances.
     */
    public static synchronized String localApiKey(){
        if(cachedLocalApiKey!=null)return cachedLocalApiKey;
        try{
            final String alias="omnigate.local8787.hmac.v1";
            java.security.KeyStore store=java.security.KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            javax.crypto.SecretKey key=(javax.crypto.SecretKey)store.getKey(alias,null);
            if(key==null){
                javax.crypto.KeyGenerator generator=javax.crypto.KeyGenerator.getInstance(
                    android.security.keystore.KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
                    "AndroidKeyStore");
                generator.init(new android.security.keystore.KeyGenParameterSpec.Builder(
                    alias,android.security.keystore.KeyProperties.PURPOSE_SIGN).build());
                key=generator.generateKey();
            }
            javax.crypto.Mac mac=javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] digest=mac.doFinal(
                "OmniGate local API bearer key v1 / this Android keystore owner"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            cachedLocalApiKey="oglocal-"+android.util.Base64.encodeToString(digest,
                android.util.Base64.URL_SAFE|
                android.util.Base64.NO_WRAP|
                android.util.Base64.NO_PADDING);
            java.util.Arrays.fill(digest,(byte)0);
            return cachedLocalApiKey;
        }catch(java.security.GeneralSecurityException|java.io.IOException failure){
            // A missing owner key must never reopen localhost-any-nonempty.
            throw new IllegalStateException("local_auth_keystore_unavailable",failure);
        }
    }
    public static String localApiHeader(){return "Bearer "+localApiKey();}
    public static boolean localApiKeyMatches(String candidate){
        if(candidate==null||candidate.length()<20||candidate.length()>100)return false;
        String expected=localApiKey();
        return java.security.MessageDigest.isEqual(
            expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
    public static String relayBaseUrl(){
        String v=prefs().getString("relay_base_url","");
        if(!v.isEmpty())return v;
        // Upgrade old user-entered settings only; do not resurrect any
        // former compiled-in hostname or mark a new endpoint verified.
        String previous=prefs().getString("custom_public_url","");
        return prefs().getBoolean("custom_public_url_verified",false)
            ? normalizeRelayBase(previous) : "";
    }
    public static String normalizeRelayBase(String text){
        String value=text==null?"":text.trim();
        if(value.isEmpty())return "";
        if(value.length()>255)throw new IllegalArgumentException("relay_url_too_long");
        try{
            java.net.URI url=new java.net.URI(value);
            if(!"https".equalsIgnoreCase(url.getScheme()) ||
                url.getHost()==null || url.getHost().isEmpty() ||
                url.getRawUserInfo()!=null || url.getRawQuery()!=null ||
                url.getRawFragment()!=null || url.getPort()!=-1 ||
                (url.getRawPath()!=null && !url.getRawPath().isEmpty()
                && !"/".equals(url.getRawPath())))
                throw new IllegalArgumentException("relay_https_origin_required");
            String host=url.getHost().toLowerCase(java.util.Locale.ROOT);
            if("localhost".equals(host)||host.endsWith(".localhost")||
                "127.0.0.1".equals(host)||"0.0.0.0".equals(host))
                throw new IllegalArgumentException("relay_public_host_required");
            return "https://"+host;
        }catch(java.net.URISyntaxException bad){
            throw new IllegalArgumentException("relay_url_invalid");
        }
    }
    public static void setRelayBaseUrl(String text){
        String value=normalizeRelayBase(text);
        prefs().edit().putString("relay_base_url",value)
            .remove("custom_public_url").remove("custom_public_url_verified")
            .commit();
    }
    public static String effectivePublicUrl(){return relayBaseUrl();}
    public static String relayWebSocketUrl(){
        String origin=relayBaseUrl();
        return origin.isEmpty()?"":"wss://"+origin.substring("https://".length())+"/__device";
    }
    /** Legacy setter retained only for source compatibility. Not auto-verified. */
    public static String customPublicUrl(){return relayBaseUrl();}
    public static void setCustomPublicUrl(String v,boolean verified){
        if(verified)setRelayBaseUrl(v);
        else prefs().edit().putString("custom_public_url",
            normalizeRelayBase(v)).putBoolean("custom_public_url_verified",false).commit();
    }

}
