package com.omnigate.integrated;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import java.util.concurrent.atomic.AtomicBoolean;

/** Restores previously authorized 8787 / Relay on app process and UI entry.
 * Never touches the frozen original provider, WebDriver or native ApiServer.
 * Foreground notification keepalive is explicitly opt-in and Android can
 * deny it; app/process launch auto-start is not a boot-time guarantee.
 */
public final class AutoStartRuntime {
    private static final AtomicBoolean working = new AtomicBoolean(false);
    private static final AtomicBoolean fgsRequested = new AtomicBoolean(false);
    private static volatile String status = "尚未运行自动恢复";
    private AutoStartRuntime(){}
    public static String status(){return status;}

    public static void onProcessCreated(){ ensureAsync("process_start"); }
    public static void onActivityVisible(Activity activity){
        ensureAsync("activity_resumed");
        if(activity!=null && GatewayApp.autoStartEnabled() &&
                GatewayApp.backgroundKeepAliveEnabled() &&
                !SidecarService.isServiceAlive() &&
                fgsRequested.compareAndSet(false,true)){
            // Only called after a user actually opens an Activity. Do NOT
            // launch a foreground service from a background ContentProvider.
            try{
                Intent service=new Intent(activity,SidecarService.class);
                service.setAction(SidecarService.ACTION_ENSURE_RUNNING);
                if(Build.VERSION.SDK_INT>=26)
                    activity.startForegroundService(service);
                else activity.startService(service);
                status="已请求启动通知保活服务；以系统实际状态为准";
            }catch(RuntimeException e){
                fgsRequested.set(false);
                status="系统拒绝通知保活："+e.getClass().getSimpleName()+
                    "；当前进程内服务仍可使用";
            }
        }
    }
    public static void requestRefresh(){
        ensureAsync("manual_refresh");
    }

    private static void ensureAsync(String reason){
        if(!GatewayApp.autoStartEnabled())return;
        if(!working.compareAndSet(false,true))return;
        new Thread(()->{
            try{
                if(!GatewayApp.autoStartEnabled())return;
                boolean local=LocalProxyRuntime.start();
                if(!local){
                    status="本地 8787 无法绑定："+LocalProxyRuntime.status();
                    return;
                }
                if(GatewayApp.tunnelEnabled()){
                    if(GatewayApp.relayKey().length()<24){
                        status="本地 8787 已启动；Relay 配对 Key 缺失";
                    }else{
                        RelayRuntime.ensureStarted();
                        status="8787 已启动；Relay="+GatewayApp.tunnelStatus()+
                            "（"+reason+"）";
                    }
                }else{
                    status="8787 已自动启动；公网仍按用户设置关闭";
                }
            }catch(Throwable failure){
                status="自动恢复受阻："+failure.getClass().getSimpleName();
            }finally{
                working.set(false);
            }
        },"omnigate-safe-process-autostart").start();
    }
}
