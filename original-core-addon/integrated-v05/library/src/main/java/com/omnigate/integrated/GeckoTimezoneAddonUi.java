package com.omnigate.integrated;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OGTZ03 — opt-in, signed Firefox add-on research candidate.
 *
 * This is not a Gecko engine timezone implementation. A third-party extension
 * modifies webpage JS and therefore needs powerful permissions. Never install
 * automatically or bypass Gecko's Mozilla-signature and permission checks.
 * Never start Remote Agent/Marionette or open remote debugging ports.
 * Works only with an already-created ORIGINAL GeckoRuntime; no replacement.
 *
 * No claim of timezone success without BrowserLeaks/worker E2E on the phone.
 */
final class GeckoTimezoneAddonUi {
    private static final String ADDON_ID="timezoneguard@3radiance.dev";
    private static final String AMO_PAGE=
        "https://addons.mozilla.org/en-US/firefox/addon/timezone-guard/";
    // Pinned to the exact AMO v1.7 "Download file" link, verified 2026-11-19.
    private static final String SIGNED_XPI=
        "https://addons.mozilla.org/firefox/downloads/file/5224737/timezone_guard-1.7.xpi";
    private static final AtomicBoolean busy=new AtomicBoolean(false);
    private GeckoTimezoneAddonUi(){}

    static void show(Activity a){
        if(a==null||a.isDestroyed()||a.isFinishing())return;
        new AlertDialog.Builder(a)
            .setTitle("共享网页时区 · 扩展试验说明")
            .setMessage("候选：Mozilla 签名的 Timezone Guard v1.7（第三方 MIT 开源）。"+
                "\\n此扩展可能读取/修改所有网站内容，并影响主网页与全部辅助 Gecko 账号。"+
                "另一 Android 工作资料不会自动继承。"+
                "\\n\\n此方案不是引擎级时区修改，Date/Intl/Worker 兼容性必须真机实测。"+
                "安装只允许你主动发起，并须再次批准实际权限。"+
                "不用远程调试，不改设备时间。")
            .setNegativeButton("关闭",null)
            .setPositiveButton("查看操作选项",(d,w)->showActions(a))
            .show();
    }

    private static void showActions(Activity a){
        if(a==null||a.isDestroyed()||a.isFinishing())return;
        new AlertDialog.Builder(a)
            .setTitle("Timezone Guard · 可选操作")
            .setItems(new String[]{
                "查看 Mozilla 扩展说明与权限",
                "手动安装签名扩展（须再次批准权限）",
                "Cloudflare 验证循环 · 网站例外设置",
                "查看已安装状态 / 打开设置 / 卸载"
            },(dialog,which)->{
                if(which==0){
                    Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(AMO_PAGE));
                    try{a.startActivity(i);}catch(RuntimeException ignored){
                        message(a,"无法打开扩展介绍页面");
                    }
                }else if(which==1)offerInstall(a);
                else if(which==2)showVerificationCompatibilityGuide(a);
                else listAndManage(a);
            }).setNegativeButton("取消",null).show();
    }

    private static void message(Activity a,String m){
        if(a!=null&&!a.isDestroyed()&&!a.isFinishing())
            Toast.makeText(a,m,Toast.LENGTH_LONG).show();
    }

    /** A documented user-controlled exception, never a challenge bypass. */
    private static void showVerificationCompatibilityGuide(Activity a){
        if(a==null||a.isDestroyed()||a.isFinishing())return;
        new AlertDialog.Builder(a)
            .setTitle("Timezone Guard · Cloudflare 兼容设置")
            .setMessage("如果启用第三方时区插件后反复出现人机验证，请先在插件设置中关闭 Spoof HTTP Headers，" +
                "并将出现验证循环的网站主域名添加到 Site Exceptions（每行一个，不要填写完整 URL）。\\n\\n" +
                "例外网站及其框架会使用浏览器原生时区；其他网站仍按你选择的时区显示。" +
                "保存后完整关闭并重新打开受影响网页。请自己手动完成网站验证。" +
                "\\n\\n此操作不修改 Cookie、账号空间、登录信息，不保证任何网站一定通过验证。" +
                "如仍然循环，请关闭或卸载该扩展。")
            .setNegativeButton("返回",null)
            .setPositiveButton("打开已安装插件设置",(d,w)->listAndManage(a))
            .show();
    }

    private static Object originalRuntime() throws Exception{
        Class<?> c=Class.forName("org.mozilla.geckoview.GeckoRuntime");
        Method m=c.getDeclaredMethod("getInstance");m.setAccessible(true);
        Object rt=m.invoke(null);
        if(rt==null)throw new IllegalStateException("ORIGINAL_GECKO_RUNTIME_NOT_STARTED");
        return rt;
    }
    private static Object controller(Object rt)throws Exception{
        return rt.getClass().getMethod("getWebExtensionController").invoke(rt);
    }

    private interface Delivered{void value(Object result);void error(Throwable error);}
    private static Object asyncCallback(Class<?> consumer,java.util.function.Consumer<Object> run){
        return Proxy.newProxyInstance(consumer.getClassLoader(),new Class[]{consumer},
            (proxy,m,args)->{
                if("accept".equals(m.getName())){
                    run.accept(args==null||args.length==0?null:args[0]);return null;
                }
                if("toString".equals(m.getName()))return "OGTZ03_GeckoResult_Consumer";
                if("hashCode".equals(m.getName()))return System.identityHashCode(proxy);
                if("equals".equals(m.getName()))return proxy==args[0];
                return null;
            });
    }
    private static void observe(Object result,Delivered delivered)throws Exception{
        Class<?> consumer=Class.forName("org.mozilla.geckoview.GeckoResult$Consumer");
        Object ok=asyncCallback(consumer,delivered::value);
        Object no=asyncCallback(consumer,v->delivered.error(
            v instanceof Throwable?(Throwable)v:new Exception("UNKNOWN_GECKO_ERROR")));
        result.getClass().getMethod("accept",consumer,consumer).invoke(result,ok,no);
    }
    private static Object promptResponse(boolean allow) throws Exception{
        Class<?> response=Class.forName(
            "org.mozilla.geckoview.WebExtension$PermissionPromptResponse");
        // Private-browsing access & technical data collection remain denied.
        return response.getConstructor(Boolean.class,Boolean.class,Boolean.class)
            .newInstance(allow,Boolean.FALSE,Boolean.FALSE);
    }
    private static void complete(Object pending,Object value){
        try{pending.getClass().getMethod("complete",Object.class).invoke(pending,value);}
        catch(Exception ignored){}
    }
    private static Object permissionDelegate(Activity a,Class<?> iface)
            throws Exception{
        return Proxy.newProxyInstance(iface.getClassLoader(),new Class[]{iface},
            (proxy,m,args)->{
                if("onInstallPromptRequest".equals(m.getName())){
                    Object pending=Class.forName("org.mozilla.geckoview.GeckoResult")
                        .getConstructor().newInstance();
                    a.runOnUiThread(()->{
                        try{
                            if(a.isDestroyed()||a.isFinishing()){
                                complete(pending,promptResponse(false));return;
                            }
                            Object extension=args[0];
                            Object id=extension.getClass().getField("id").get(extension);
                            if(!ADDON_ID.equals(id)){
                                complete(pending,promptResponse(false));return;
                            }
                            String perms=java.util.Arrays.toString((String[])args[1]);
                            String origins=java.util.Arrays.toString((String[])args[2]);
                            String collection=java.util.Arrays.toString((String[])args[3]);
                            new AlertDialog.Builder(a)
                                .setTitle("确认授予 Timezone Guard 权限？")
                                .setMessage("来源：Mozilla Add-ons，扩展 ID 已核验。"+
                                    "\n\n权限："+perms+
                                    "\n网站访问范围："+origins+
                                    "\n数据收集权限："+collection+
                                    "\n\n此第三方扩展可能访问全部网页登录页面。"+
                                    "仅授权普通网页，不授权隐私浏览/技术数据收集。"+
                                    "同意后扩展将持续安装，直至主动卸载。")
                                .setPositiveButton("授予并安装",(d,w)->{
                                    try{complete(pending,promptResponse(true));}
                                    catch(Exception e){complete(pending,null);}
                                })
                                .setNegativeButton("拒绝",(d,w)->{
                                    try{complete(pending,promptResponse(false));}
                                    catch(Exception e){complete(pending,null);}
                                })
                                .setOnCancelListener(d->{
                                    try{complete(pending,promptResponse(false));}
                                    catch(Exception e){complete(pending,null);}
                                }).show();
                        }catch(Exception e){complete(pending,null);}
                    });
                    return pending;
                }
                if("onOptionalPrompt".equals(m.getName())||
                    "onUpdatePrompt".equals(m.getName())){
                    // Do not grant extra permissions silently.
                    return Class.forName("org.mozilla.geckoview.GeckoResult")
                        .getMethod("deny").invoke(null);
                }
                if("hashCode".equals(m.getName()))return System.identityHashCode(proxy);
                if("equals".equals(m.getName()))return proxy==args[0];
                if("toString".equals(m.getName()))return "OGTZ03_Consent_Delegate";
                return null;
            });
    }
    private static void offerInstall(Activity a){
        new AlertDialog.Builder(a)
            .setTitle("只进行一次手动扩展安装？")
            .setMessage("将从 Mozilla 官方站点获取固定版本 1.7 的签名 XPI。"+
                "\n可能影响主账号和全部辅助 Gecko 页面。"+
                "不会改变手机时区，不会打开远程调试，也不会自动开启其他插件。"+
                "\n\n安装完成之后仍须在扩展内选定 IANA 时区并真机检查。")
            .setNegativeButton("取消",null)
            .setPositiveButton("继续到权限确认",(d,w)->install(a))
            .show();
    }

    private static void install(Activity a){
        if(!busy.compareAndSet(false,true)){message(a,"已有扩展操作进行中");return;}
        final Object ct;final Object prior;final Object delegate;
        try{
            ct=controller(originalRuntime());
            prior=ct.getClass().getMethod("getPromptDelegate").invoke(ct);
            // Global prompt delegates can be owned by other integrations:
            // never replace them, even temporarily.
            if(prior!=null)throw new IllegalStateException("EXISTING_ADDON_PROMPT_DELEGATE");
            Class<?> iface=Class.forName(
                "org.mozilla.geckoview.WebExtensionController$PromptDelegate");
            delegate=permissionDelegate(a,iface);
            ct.getClass().getMethod("setPromptDelegate",iface).invoke(ct,delegate);
        }catch(Exception e){
            busy.set(false);
            message(a,"未开始安装：Gecko 扩展接口不可用或已有权限管理器");
            return;
        }
        HandlerThread thread=new HandlerThread("OGTZ03-gecko-addon");
        thread.start();
        new Handler(thread.getLooper()).post(()->{
            try{
                Object pending=ct.getClass().getMethod("install",String.class)
                    .invoke(ct,SIGNED_XPI);
                observe(pending,new Delivered(){
                    private void finish(Object ext,Throwable failure){
                        a.runOnUiThread(()->{
                            try{
                                Class<?> iface=Class.forName(
                                    "org.mozilla.geckoview.WebExtensionController$PromptDelegate");
                                Object current=ct.getClass().getMethod("getPromptDelegate").invoke(ct);
                                if(current==delegate)
                                    ct.getClass().getMethod("setPromptDelegate",iface)
                                        .invoke(ct,(Object)null);
                            }catch(Exception ignored){}
                            busy.set(false);
                            if(failure!=null){
                                message(a,"插件未成功安装：签名、权限或 Gecko 兼容性验证失败");
                            }else if(ext!=null){
                                message(a,"扩展已安装；尚未验证网页时区效果，请设置并检测");
                                openPopup(a,ext);
                            }
                        });
                        thread.quitSafely();
                    }
                    @Override public void value(Object ext){finish(ext,null);}
                    @Override public void error(Throwable e){finish(null,e);}
                });
            }catch(Exception e){
                a.runOnUiThread(()->{
                    try{
                        Class<?> iface=Class.forName(
                            "org.mozilla.geckoview.WebExtensionController$PromptDelegate");
                        Object current=ct.getClass().getMethod("getPromptDelegate").invoke(ct);
                        if(current==delegate)ct.getClass().getMethod("setPromptDelegate",iface)
                            .invoke(ct,(Object)null);
                    }catch(Exception ignored){}
                    busy.set(false);
                    message(a,"安装接口调用失败，未授权更改系统或账号");
                });
                thread.quitSafely();
            }
        });
    }

    private static void listAndManage(Activity a){
        if(!busy.compareAndSet(false,true)){message(a,"已有扩展操作进行中");return;}
        HandlerThread thread=new HandlerThread("OGTZ03-gecko-list");
        thread.start();
        new Handler(thread.getLooper()).post(()->{
            try{
                Object ct=controller(originalRuntime());
                Object pending=ct.getClass().getMethod("list").invoke(ct);
                observe(pending,new Delivered(){
                    @Override public void value(Object data){
                        thread.quitSafely();busy.set(false);
                        a.runOnUiThread(()->{
                            Object found=null;
                            if(data instanceof List)for(Object ext:(List<?>)data){
                                try{
                                    if(ADDON_ID.equals(ext.getClass().getField("id").get(ext))){
                                        found=ext;break;
                                    }
                                }catch(Exception ignored){}
                            }
                            if(found==null){message(a,"当前 GeckoRuntime 未安装 Timezone Guard");return;}
                            final Object addon=found;
                            new AlertDialog.Builder(a)
                                .setTitle("已安装：Timezone Guard")
                                .setItems(new String[]{
                                    "打开时区设置（手机全屏 + 可搜索选择框）",
                                    "卸载第三方扩展 · 恢复原生 JS 时区"
                                },(d,index)->{
                                    if(index==0)openPopup(a,addon);
                                    else confirmUninstall(a,addon);
                                }).setNegativeButton("关闭",null).show();
                        });
                    }
                    @Override public void error(Throwable err){
                        thread.quitSafely();busy.set(false);
                        a.runOnUiThread(()->message(a,"无法读取 Gecko 已安装扩展"));
                    }
                });
            }catch(Exception e){
                thread.quitSafely();busy.set(false);
                a.runOnUiThread(()->message(a,"原版 GeckoRuntime 尚未启动或不支持扩展查询"));
            }
        });
    }
    private static void confirmUninstall(Activity a,Object addon){
        new AlertDialog.Builder(a).setTitle("卸载 Timezone Guard？")
            .setMessage("将移除扩展及其扩展存储，原有账号 Cookie 与 GeckoSession 不清理。"+
                "\n正在打开的网页可能需要手动重新加载，才能恢复原生环境。")
            .setNegativeButton("取消",null)
            .setPositiveButton("确认卸载",(d,w)->{
                if(!busy.compareAndSet(false,true))return;
                HandlerThread thread=new HandlerThread("OGTZ03-gecko-remove");
                thread.start();
                new Handler(thread.getLooper()).post(()->{
                    try{
                        Object ct=controller(originalRuntime());
                        Class<?> wc=Class.forName("org.mozilla.geckoview.WebExtension");
                        Object p=ct.getClass().getMethod("uninstall",wc).invoke(ct,addon);
                        observe(p,new Delivered(){
                            @Override public void value(Object ignored){
                                thread.quitSafely();busy.set(false);
                                a.runOnUiThread(()->message(a,"扩展已卸载；请手动重新打开网站测试"));
                            }
                            @Override public void error(Throwable e){
                                thread.quitSafely();busy.set(false);
                                a.runOnUiThread(()->message(a,"卸载失败，扩展状态未确认"));
                            }
                        });
                    }catch(Exception e){
                        thread.quitSafely();busy.set(false);
                        a.runOnUiThread(()->message(a,"卸载接口调用失败"));
                    }
                });
            }).show();
    }
    private static void openPopup(Activity a,Object addon){
        if(a==null||a.isDestroyed()||a.isFinishing())return;
        try{
            Object meta=addon.getClass().getField("metaData").get(addon);
            String base=(String)meta.getClass().getField("baseUrl").get(meta);
            if(base==null||!base.startsWith("moz-extension://")||!base.endsWith("/"))
                throw new IllegalStateException("INVALID_EXTENSION_ORIGIN");
            // OGTZ07: use the already-declared app Activity rather than an
            // AlertDialog-hosted GeckoView. Activity-owned windows give
            // GeckoView a normal Android InputConnection/IME lifecycle on
            // Android 16 and OEM keyboards.
            Intent i=new Intent(a,CompatSettingsActivity.class);
            i.putExtra(CompatSettingsActivity.EXTRA_TIMEZONE_ADDON_URL,
                base+"frontend/popup.html");
            a.startActivity(i);
        }catch(Exception failed){
            message(a,"插件设置界面启动失败；原账号和插件存储未改变");
        }
    }
}
