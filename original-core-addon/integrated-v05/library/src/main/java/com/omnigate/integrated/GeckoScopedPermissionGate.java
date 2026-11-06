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
            if(kind==constant(c,"PERMISSION_GEOLOCATION"))return "读取当前位置";
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
    static void install(Context c,Object ownedSession,String contextId,BooleanSupplier owner){
        if(c==null||ownedSession==null||!AccountBrowserSlots.validContextId(contextId))return;
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
                        boolean available=current&&args[1] instanceof String[];
                        if(available){
                            String[] requested=(String[])args[1];
                            if(requested.length==0)available=false;
                            for(String p:requested){
                                if(!Manifest.permission.CAMERA.equals(p)
                                  &&!Manifest.permission.RECORD_AUDIO.equals(p)
                                  &&!Manifest.permission.ACCESS_FINE_LOCATION.equals(p)
                                  &&!Manifest.permission.ACCESS_COARSE_LOCATION.equals(p))
                                    available=false;
                                if(p==null||c.checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)
                                    available=false;
                            }
                        }
                        callback.getClass().getMethod(available?"grant":"reject").invoke(callback);
                        if(!available&&current && c instanceof Activity)
                            Toast.makeText(c,"网页无法获取新的 Android 系统权限；请在系统设置中自行决定",
                                Toast.LENGTH_SHORT).show();
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
                        Object callback=args.length>4?args[4]:null;
                        if(callback==null)return null;
                        if(!current||!(c instanceof Activity)||!validHttpsOrigin((String)args[1])){
                            reject(callback);return null;
                        }
                        Object[] videos=args[2] instanceof Object[]?(Object[])args[2]:null;
                        Object[] audios=args[3] instanceof Object[]?(Object[])args[3]:null;
                        boolean wantsVideo=videos!=null&&videos.length>0;
                        boolean wantsAudio=audios!=null&&audios.length>0;
                        if((wantsVideo&&c.checkSelfPermission(Manifest.permission.CAMERA)
                            !=PackageManager.PERMISSION_GRANTED)
                            ||(wantsAudio&&c.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                            !=PackageManager.PERMISSION_GRANTED)
                            ||(!wantsVideo&&!wantsAudio)){
                            reject(callback);return null;
                        }
                        Activity host=(Activity)c;
                        host.runOnUiThread(()->{
                            if(!owner.getAsBoolean()||host.isFinishing()||host.isDestroyed()){
                                reject(callback);return;
                            }
                            new AlertDialog.Builder(host)
                                .setTitle("当前账号空间的媒体权限")
                                .setMessage("网站："+Uri.parse((String)args[1]).getHost()+
                                    "\n申请："+(wantsVideo?"相机 ":"")+(wantsAudio?"麦克风":"")+
                                    "\n\n仅授权本次网页媒体请求；Android 权限仍由系统管理。"+
                                    "切换空间前必须关闭旧 Gecko 会话。")
                                .setNegativeButton("拒绝",(d,w)->reject(callback))
                                .setPositiveButton("允许此次请求",(d,w)->{
                                    if(!owner.getAsBoolean()){reject(callback);return;}
                                    try{
                                        Class<?> source=load(
                                            "org.mozilla.geckoview.GeckoSession$PermissionDelegate$MediaSource");
                                        callback.getClass().getMethod("grant",source,source).invoke(callback,
                                            wantsVideo?videos[0]:null,wantsAudio?audios[0]:null);
                                    }catch(Throwable failure){reject(callback);}
                                })
                                .setOnCancelListener(d->reject(callback))
                                .show();
                        });
                        return null;
                    }
                    return null;
                });
            ownedSession.getClass().getMethod("setPermissionDelegate",delegate)
                .invoke(ownedSession,impl);
        }catch(Throwable unavailable){
            // No speculative fallback to platform-wide access.
            Log.w(TAG,"site_permission_gate_unavailable",unavailable);
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
