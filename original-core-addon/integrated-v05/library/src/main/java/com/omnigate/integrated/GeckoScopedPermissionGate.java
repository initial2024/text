package com.omnigate.integrated;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Auxiliary Gecko session site permission gate.
 * Android OS permissions are NEVER requested/granted by this component.
 * A website grant cannot upgrade the Android application's permission set.
 * User choices apply solely to the owned GeckoSession/contextId.
 */
final class GeckoScopedPermissionGate {
    private static final String TAG="OmniGateSitePerm";
    private GeckoScopedPermissionGate() {}

    private static Class<?> load(String name)throws Exception {
        return Class.forName(name);
    }
    private static Object newResult(Object decision)throws Exception {
        Object result=load("org.mozilla.geckoview.GeckoResult")
            .getConstructor().newInstance();
        result.getClass().getMethod("complete",Object.class).invoke(result,decision);
        return result;
    }
    private static int constant(Class<?> type,String name)throws Exception{
        return type.getField(name).getInt(null);
    }
    private static String permissionName(int kind){
        // Grantable classes are intentionally narrow. Local network, device,
        // tracking, cross-site storage, XR, persistent storage, notifications
        // and media are NOT implicitly enabled.
        try{
            Class<?> c=load("org.mozilla.geckoview.GeckoSession$PermissionDelegate");
            if(kind==constant(c,"PERMISSION_AUTOPLAY_AUDIBLE"))return "网页自动播放声音";
            if(kind==constant(c,"PERMISSION_AUTOPLAY_INAUDIBLE"))return "网页自动播放静音媒体";
            // Location is hardware-sensitive; a Gecko profile is not an
            // Android OS permission sandbox. Keep this unavailable.
        }catch(Exception ignored){}
        return null;
    }
    private static boolean osHasLocation(Context c){
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
            || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }
    private static boolean validHttpsOrigin(String url){
        try{
            Uri uri=Uri.parse(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost()!=null
                && !uri.getHost().isEmpty() && uri.getUserInfo()==null;
        }catch(RuntimeException invalid){return false;}
    }
    /**
     * Site permission contextId from Gecko must match the selected slot,
     * not a shared default/foreign profile.
     */
    static boolean install(Context c,Object ownedSession,String contextId,BooleanSupplier owner){
        if(c==null||ownedSession==null||owner==null||
            !AccountBrowserSlots.validContextId(contextId))
            throw new IllegalArgumentException("site_permission_context_invalid");
        try {
            Class<?> delegate=load("org.mozilla.geckoview.GeckoSession$PermissionDelegate");
            Class<?> content=load("org.mozilla.geckoview.GeckoSession$PermissionDelegate$ContentPermission");
            Object impl=Proxy.newProxyInstance(delegate.getClassLoader(),
                new Class<?>[]{delegate},(proxy,method,args)->{
                    String event=method.getName();
                    if("hashCode".equals(event))return System.identityHashCode(proxy);
                    if("equals".equals(event))return args!=null&&args.length>0&&proxy==args[0];
                    if("toString".equals(event))return "OmniGateScopedPermissionGate";
                    if(args==null||args.length<2)return null;
                    boolean current=args[0]==ownedSession&&owner.getAsBoolean();
                    if("onAndroidPermissionsRequest".equals(event)){
                        if(args.length<3)return null;
                        Object callback=args[2];
                        // This callback is an app-wide Android capability,
                        // NOT a contextId-scoped website permission. Even if
                        // the app has hardware grants from another workflow,
                        // a web account cannot silently inherit them.
                        callback.getClass().getMethod("reject").invoke(callback);
                        return null;
                    }
                    if("onContentPermissionRequest".equals(event)){
                        Object perm=args[1];
                        int deny=constant(content,"VALUE_DENY");
                        if(!current)return newResult(deny);
                        String actual=(String)content.getField("contextId").get(perm);
                        String site=(String)content.getField("uri").get(perm);
                        int kind=content.getField("permission").getInt(perm);
                        String label=permissionName(kind);
                        if(!contextId.equals(actual)||!validHttpsOrigin(site)
                            ||label==null||!(c instanceof Activity))
                            return newResult(deny);
                        if("读取当前位置".equals(label) && !osHasLocation(c))
                            return newResult(deny);
                        // GeckoResult intentionally remains pending until the
                        // user explicitly makes a choice in this Activity.
                        Object pending=load("org.mozilla.geckoview.GeckoResult")
                            .getConstructor().newInstance();
                        Activity host=(Activity)c;
                        host.runOnUiThread(()->{
                            try{
                                if(!owner.getAsBoolean()||host.isFinishing()||host.isDestroyed()){
                                    pending.getClass().getMethod("complete",Object.class)
                                        .invoke(pending,deny);return;
                                }
                                String hostLabel=Uri.parse(site).getHost();
                                int allow=constant(content,"VALUE_ALLOW");
                                Object pRef=perm;
                                new AlertDialog.Builder(host)
                                    .setTitle("仅允许当前账号空间？")
                                    .setMessage("网站："+hostLabel+"\n申请："+label+
                                        "\n\n此项授权只对应当前 Gecko 账号空间；Android 系统权限不会因此改变。"+
                                        "某些 Gecko 网页许可可能在此空间内保留；可通过清除此账号空间站点数据重置。")
                                    .setNegativeButton("拒绝",(d,w)->finish(pending,deny))
                                    .setPositiveButton("允许",(d,w)->finish(pending,
                                        owner.getAsBoolean()?allow:deny))
                                    .setOnCancelListener(d->finish(pending,deny))
                                    .show();
                                try{pRef.getClass().getMethod("notifyShown").invoke(pRef);}
                                catch(Exception ignored){}
                            }catch(Throwable e){finish(pending,deny);}
                        });
                        return pending;
                    }
                    if("onMediaPermissionRequest".equals(event)){
                        // Camera/microphone are physical device resources.
                        // For now reject them entirely until Android hardware
                        // lifecycle revocation passes actual device testing.
                        if(args.length>4)reject(args[4]);
                        return null;
                    }
                    return null;
                });
            ownedSession.getClass().getMethod("setPermissionDelegate",delegate)
                .invoke(ownedSession,impl);
            Object installed=ownedSession.getClass().getMethod("getPermissionDelegate")
                .invoke(ownedSession);
            if(installed!=impl)throw new IllegalStateException("gecko_permission_owner_mismatch");
            return true;
        }catch(Throwable unavailable){
            // A failed permission hook may not fall back to Gecko defaults:
            // callers abort and close the auxiliary session.
            Log.w(TAG,"site_permission_gate_unavailable",unavailable);
            throw new IllegalStateException("gecko_permission_gate_unavailable",unavailable);
        }
    }
    private static void finish(Object result,Object decision){
        try{result.getClass().getMethod("complete",Object.class).invoke(result,decision);}
        catch(Throwable ignored){}
    }
    private static void reject(Object callback){
        try{callback.getClass().getMethod("reject").invoke(callback);}
        catch(Throwable ignored){}
    }
}
